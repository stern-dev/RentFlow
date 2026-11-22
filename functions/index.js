const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentWritten,
} = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const logger = require("firebase-functions/logger");
const {
  buildChecklistTransitionDispatch,
  buildSettlementTransitionDispatch,
} = require("./handoverHelpers");

initializeApp();

const DATABASE_ID = "ai-studio-android-rentflow-b762b2c7-f1c1-460e-ab53-2e6f5d351fc8";
const MAINTENANCE_CHANNEL_ID = "rentflow_maintenance";
const RENT_CHANNEL_ID = "rentflow_rent";
const REMINDER_COOLDOWN_MS = 20 * 60 * 60 * 1000;
const DUE_SOON_WINDOW_DAYS = 3;
const DAY_MS = 24 * 60 * 60 * 1000;

const STATUS_LABELS = {
  SUBMITTED: "Submitted",
  UNDER_REVIEW: "Under Review",
  ASSIGNED: "Assigned",
  IN_PROGRESS: "In Progress",
  COMPLETED: "Completed",
  REJECTED: "Rejected",
};

const PRIORITY_LABELS = {
  LOW: "Low",
  MEDIUM: "Medium",
  HIGH: "High",
  URGENT: "Urgent",
};

function formatStatusLabel(status) {
  return STATUS_LABELS[status] || status || "Updated";
}

function formatPriorityLabel(priority) {
  return PRIORITY_LABELS[priority] || priority || "Medium";
}

/**
 * Fetches active FCM registration tokens for a list of RentFlow user UIDs
 * where the specified notification preference field remains enabled.
 */
async function fetchActiveTokensForUsers(db, uids, preferenceField = "notifyMaintenance") {
  const uniqueUids = [...new Set((uids || []).filter((id) => typeof id === "string" && id.trim().length > 0))];
  const tokenRefs = [];

  for (const uid of uniqueUids) {
    const userDoc = await db.collection("users").doc(uid).get();
    if (userDoc.exists && userDoc.get(preferenceField) === false) {
      continue;
    }
    const snap = await db.collection("users").doc(uid).collection("fcm_tokens").get();
    snap.forEach((doc) => {
      const data = doc.data() || {};
      if (data[preferenceField] !== false && typeof data.token === "string" && data.token.length >= 10) {
        tokenRefs.push({
          token: data.token,
          ref: doc.ref,
        });
      }
    });
  }
  return tokenRefs;
}

/**
 * Sends a Firebase Cloud Messaging multicast notification on the specified Android
 * channel and cleans up expired or unregistered device tokens from Firestore.
 */
async function dispatchPush(db, tokenEntries, channelId, payload) {
  if (!tokenEntries || tokenEntries.length === 0) {
    logger.info("No active FCM device tokens found for recipients.", { eventType: payload.data.eventType });
    return { successCount: 0, failureCount: 0 };
  }

  const tokens = tokenEntries.map((entry) => entry.token);
  const messaging = getMessaging();
  const response = await messaging.sendEachForMulticast({
    tokens,
    notification: {
      title: payload.title,
      body: payload.body,
    },
    data: payload.data,
    android: {
      priority: "high",
      notification: {
        channelId,
        icon: "ic_notification",
        clickAction: "FLUTTER_NOTIFICATION_CLICK",
      },
    },
  });

  const batch = db.batch();
  let staleCount = 0;
  response.responses.forEach((res, idx) => {
    if (!res.success && res.error) {
      const code = res.error.code || "";
      if (
        code === "messaging/registration-token-not-registered" ||
        code === "messaging/invalid-registration-token"
      ) {
        batch.delete(tokenEntries[idx].ref);
        staleCount += 1;
      }
    }
  });
  if (staleCount > 0) {
    await batch.commit();
    logger.info(`Pruned ${staleCount} stale FCM token(s).`);
  }
  logger.info("FCM push dispatched.", {
    eventType: payload.data.eventType,
    channelId,
    successCount: response.successCount,
    failureCount: response.failureCount,
  });
  return response;
}

/**
 * Sends real-time FCM push notifications to landlords and assigned managers
 * whenever a new maintenance request document is created.
 */
exports.onMaintenanceRequestCreated = onDocumentCreated(
  {
    document: "maintenance_requests/{requestId}",
    database: DATABASE_ID,
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    const req = snap.data() || {};
    const requestId = event.params.requestId;
    const db = getFirestore(DATABASE_ID);

    const recipients = [
      req.ownerUid,
      ...(Array.isArray(req.managerUids) ? req.managerUids : []),
      ...(Array.isArray(req.authorizedUids) ? req.authorizedUids : []),
    ].filter((uid) => uid && uid !== req.tenantUid);

    const tokenEntries = await fetchActiveTokensForUsers(db, recipients, "notifyMaintenance");
    const priorityLabel = formatPriorityLabel(req.priority);
    const title = `New Maintenance Ticket (${priorityLabel})`.slice(0, 140);
    const body = `${req.propertyName || "Property"} • Unit ${req.unitNumber || "-"}: ${req.title || "Repair requested"}`.slice(0, 500);

    await dispatchPush(db, tokenEntries, MAINTENANCE_CHANNEL_ID, {
      title,
      body,
      data: {
        eventType: "MAINTENANCE_CREATED",
        targetRoute: "operations",
        requestId: String(requestId),
        unitId: String(req.unitId || ""),
        status: String(req.status || "SUBMITTED"),
        recipientRole: "LANDLORD",
        title,
        body,
      },
    });
  }
);

/**
 * Sends real-time FCM push notifications to the tenant whenever their
 * maintenance request status changes.
 */
exports.onMaintenanceStatusChanged = onDocumentUpdated(
  {
    document: "maintenance_requests/{requestId}",
    database: DATABASE_ID,
  },
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    if (!before || !after) return;

    if (before.status === after.status) {
      return;
    }

    const requestId = event.params.requestId;
    const db = getFirestore(DATABASE_ID);
    const tenantUid = after.tenantUid;
    if (!tenantUid) return;

    const tokenEntries = await fetchActiveTokensForUsers(db, [tenantUid], "notifyMaintenance");
    const statusLabel = formatStatusLabel(after.status);
    const noteSuffix = after.latestUpdateNote ? ` — ${after.latestUpdateNote}` : "";
    const title = `Maintenance Status: ${statusLabel}`.slice(0, 140);
    const body = `${after.propertyName || "Property"} • Unit ${after.unitNumber || "-"} (${after.title || "Repair"}) is now ${statusLabel}${noteSuffix}`.slice(0, 500);

    await dispatchPush(db, tokenEntries, MAINTENANCE_CHANNEL_ID, {
      title,
      body,
      data: {
        eventType: "MAINTENANCE_STATUS_CHANGED",
        targetRoute: "tenant_repairs",
        requestId: String(requestId),
        unitId: String(after.unitId || ""),
        status: String(after.status || ""),
        recipientRole: "TENANT",
        title,
        body,
      },
    });
  }
);

function startOfNairobiDayMs(epochMs) {
  // Africa/Nairobi is UTC+3 year-round with no DST
  const nairobiOffsetMs = 3 * 60 * 60 * 1000;
  const shifted = epochMs + nairobiOffsetMs;
  const dayStartShifted = Math.floor(shifted / DAY_MS) * DAY_MS;
  return dayStartShifted - nairobiOffsetMs;
}

function formatKesWhole(cents) {
  const kes = Math.round(Number(cents || 0) / 100);
  return `KES ${kes.toLocaleString("en-US")}`;
}

function formatNairobiShortDate(epochMs) {
  const value = Number(epochMs || 0);
  if (!Number.isFinite(value) || value <= 0) return "this cycle";
  try {
    return new Intl.DateTimeFormat("en-KE", {
      timeZone: "Africa/Nairobi",
      day: "numeric",
      month: "short",
      year: "numeric",
    }).format(new Date(value));
  } catch {
    return "this cycle";
  }
}

/**
 * Sends automated FCM push notifications to a tenant whenever a new rent
 * invoice document is generated for their lease.
 */
exports.onInvoiceCreated = onDocumentCreated(
  {
    document: "invoices/{invoiceId}",
    database: DATABASE_ID,
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    const inv = snap.data() || {};
    if (!inv.tenantUid || inv.status === "CANCELLED") return;

    const invoiceId = event.params.invoiceId;
    const db = getFirestore(DATABASE_ID);
    const tokenEntries = await fetchActiveTokensForUsers(db, [inv.tenantUid], "notifyRentDue");

    const amountCents = Number(
      inv.outstandingCents !== undefined && inv.outstandingCents !== null
        ? inv.outstandingCents
        : inv.totalDueCents || 0
    );
    const amountLabel = formatKesWhole(amountCents);
    const dueLabel = formatNairobiShortDate(inv.dueDateEpochMs);
    const periodText = inv.billingPeriod ? ` (${inv.billingPeriod})` : "";
    const invNumPrefix = inv.invoiceNumber ? `${inv.invoiceNumber}: ` : "";

    const title = `New Rent Invoice • Unit ${inv.unitNumber || "-"}`.slice(0, 140);
    const body = `${invNumPrefix}${inv.propertyName || "RentFlow"}${periodText} — ${amountLabel} due ${dueLabel}. Tap to pay via M-Pesa.`.slice(0, 500);

    await dispatchPush(db, tokenEntries, RENT_CHANNEL_ID, {
      title,
      body,
      data: {
        eventType: "INVOICE_GENERATED",
        targetRoute: "tenant_pay",
        targetTab: "0",
        invoiceId: String(invoiceId),
        unitId: String(inv.unitId || ""),
        status: String(inv.status || "UNPAID"),
        recipientRole: "TENANT",
        title,
        body,
      },
    });
  }
);

async function notifyPaymentConfirmed(db, paymentId, payment) {
  if (!payment || !payment.tenantUid || payment.status !== "VERIFIED") return;

  const tokenEntries = await fetchActiveTokensForUsers(db, [payment.tenantUid], "notifyRentDue");
  const amountLabel = formatKesWhole(payment.amountCents);
  const receiptSuffix = payment.receiptNumber ? ` • ${payment.receiptNumber}` : "";
  const refLabel = payment.referenceCode ? ` (Ref ${payment.referenceCode})` : "";
  const allocSuffix = payment.allocationsSummary ? ` — ${payment.allocationsSummary}` : "";

  const title = `Rent Payment Confirmed${receiptSuffix}`.slice(0, 140);
  const body = `${amountLabel} for ${payment.propertyName || "RentFlow"} • Unit ${payment.unitNumber || "-"}${refLabel} verified${allocSuffix}`.slice(0, 500);

  await dispatchPush(db, tokenEntries, RENT_CHANNEL_ID, {
    title,
    body,
    data: {
      eventType: "PAYMENT_CONFIRMED",
      targetRoute: "tenant_pay",
      targetTab: "1",
      paymentId: String(paymentId),
      invoiceId: String(payment.invoiceId || ""),
      unitId: String(payment.unitId || ""),
      status: "VERIFIED",
      recipientRole: "TENANT",
      title,
      body,
    },
  });
}

/**
 * Sends automated FCM push notifications to a tenant if a payment document
 * is created directly in VERIFIED status.
 */
exports.onPaymentCreated = onDocumentCreated(
  {
    document: "payments/{paymentId}",
    database: DATABASE_ID,
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    const payment = snap.data() || {};
    if (payment.status !== "VERIFIED") return;

    const db = getFirestore(DATABASE_ID);
    await notifyPaymentConfirmed(db, event.params.paymentId, payment);
  }
);

/**
 * Sends automated FCM push notifications to a tenant when a pending rent
 * payment transitions to VERIFIED.
 */
exports.onPaymentUpdated = onDocumentUpdated(
  {
    document: "payments/{paymentId}",
    database: DATABASE_ID,
  },
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    if (!before || !after) return;

    if (before.status === "VERIFIED" || after.status !== "VERIFIED") {
      return;
    }

    const db = getFirestore(DATABASE_ID);
    await notifyPaymentConfirmed(db, event.params.paymentId, after);
  }
);

/**
 * Runs daily at 09:00 Africa/Nairobi to send rent due-soon, due-today, and overdue
 * FCM + in-app reminders while honoring the 20-hour cooldown per invoice.
 */
exports.scheduledRentDueReminders = onSchedule(
  {
    schedule: "every day 09:00",
    timeZone: "Africa/Nairobi",
  },
  async () => {
    const db = getFirestore(DATABASE_ID);
    const nowMs = Date.now();
    const todayStartMs = startOfNairobiDayMs(nowMs);

    const snap = await db
      .collection("invoices")
      .where("status", "in", ["UNPAID", "PARTIALLY_PAID", "OVERDUE"])
      .get();

    if (snap.empty) {
      logger.info("No open invoices eligible for rent due reminders.");
      return;
    }

    let dispatchedCount = 0;

    for (const docSnap of snap.docs) {
      const inv = docSnap.data() || {};
      const outstandingCents = Number(inv.outstandingCents || 0);
      if (outstandingCents <= 0 || !inv.tenantUid) continue;

      const lastSent = Number(inv.lastReminderSentEpochMs || 0);
      if (lastSent > 0 && nowMs - lastSent < REMINDER_COOLDOWN_MS) {
        continue;
      }

      const dueStartMs = startOfNairobiDayMs(Number(inv.dueDateEpochMs || nowMs));
      const daysUntilDue = Math.floor((dueStartMs - todayStartMs) / DAY_MS);
      const amountLabel = formatKesWhole(outstandingCents);

      let title = "";
      let body = "";
      if (daysUntilDue < 0 || inv.status === "OVERDUE") {
        const overdueDays = Math.max(1, -daysUntilDue);
        title = `Overdue Rent Notice • Unit ${inv.unitNumber || "-"}`;
        body = `${inv.propertyName || "Property"} (${inv.billingPeriod || ""}): ${amountLabel} is ${overdueDays} day(s) overdue. Tap to pay via M-Pesa.`;
      } else if (daysUntilDue === 0) {
        title = `Rent Due Today • Unit ${inv.unitNumber || "-"}`;
        body = `${inv.propertyName || "Property"} (${inv.billingPeriod || ""}): ${amountLabel} is due today.`;
      } else if (daysUntilDue <= DUE_SOON_WINDOW_DAYS) {
        title = `Rent Due in ${daysUntilDue} Day(s) • Unit ${inv.unitNumber || "-"}`;
        body = `${inv.propertyName || "Property"} (${inv.billingPeriod || ""}): ${amountLabel} is due soon.`;
      } else {
        continue;
      }

      await db.collection("notifications").add({
        recipientUid: inv.tenantUid,
        senderUid: inv.ownerUid || "system_rent_reminder",
        orgId: inv.orgId || "rent_reminder",
        type: "INVOICE",
        title: title.slice(0, 140),
        body: body.slice(0, 500),
        relatedEntityId: docSnap.id,
        read: false,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });

      const tokenEntries = await fetchActiveTokensForUsers(db, [inv.tenantUid], "notifyRentDue");
      if (tokenEntries.length > 0) {
        await dispatchPush(db, tokenEntries, RENT_CHANNEL_ID, {
          title: title.slice(0, 140),
          body: body.slice(0, 500),
          data: {
            eventType: "RENT_REMINDER",
            targetRoute: "tenant_pay",
            targetTab: "0",
            invoiceId: docSnap.id,
            unitId: String(inv.unitId || ""),
            status: String(inv.status || "UNPAID"),
            recipientRole: "TENANT",
            title: title.slice(0, 140),
            body: body.slice(0, 500),
          },
        });
      }

      await docSnap.ref.update({
        lastReminderSentEpochMs: nowMs,
        reminderCount: FieldValue.increment(1),
        updatedAt: FieldValue.serverTimestamp(),
      });
      dispatchedCount += 1;
    }

    logger.info("Scheduled rent due reminders completed.", { dispatchedCount });
  }
);

/**
 * Atomically claims a server-side dispatch lock so Cloud Functions v2
 * at-least-once retries never emit duplicate FCM pushes or server audit entries.
 */
async function claimServerEventIdempotency(db, eventKey, metadata) {
  if (!eventKey) return false;
  try {
    await db.collection("function_dispatch_logs").doc(eventKey).create({
      ...metadata,
      dispatchedAt: FieldValue.serverTimestamp(),
    });
    return true;
  } catch (err) {
    const isAlreadyExists =
      err?.code === 6 ||
      err?.code === "already-exists" ||
      String(err?.message || "").includes("ALREADY_EXISTS");
    if (isAlreadyExists) {
      logger.info("Skipping duplicate Cloud Function retry.", { eventKey });
      return false;
    }
    throw err;
  }
}

/**
 * Dispatches idempotent FCM push notifications and server-side audit logs when
 * a move-in checklist enters PENDING_TENANT_REVIEW, ACKNOWLEDGED, or DISPUTED.
 */
exports.onMoveInChecklistWritten = onDocumentWritten(
  {
    document: "move_in_checklists/{checklistId}",
    database: DATABASE_ID,
  },
  async (event) => {
    const before = event.data?.before?.data() || null;
    const after = event.data?.after?.data() || null;
    if (!after) return;

    const checklistId = event.params.checklistId;
    const plan = buildChecklistTransitionDispatch(checklistId, before, after);
    if (!plan) return;

    const db = getFirestore(DATABASE_ID);
    const claimed = await claimServerEventIdempotency(db, plan.eventKey, {
      entityType: "MoveInChecklist",
      entityId: checklistId,
      eventType: plan.eventType,
      status: plan.status,
    });
    if (!claimed) return;

    const tokenEntries = await fetchActiveTokensForUsers(db, plan.recipients, "notifyRentDue");
    await dispatchPush(db, tokenEntries, RENT_CHANNEL_ID, {
      title: plan.title,
      body: plan.body,
      data: {
        eventType: plan.eventType,
        targetRoute: plan.targetRoute,
        checklistId: plan.checklistId,
        leaseId: plan.leaseId,
        unitId: plan.unitId,
        status: plan.status,
        recipientRole: plan.recipientRole,
        title: plan.title,
        body: plan.body,
      },
    });

    await db.collection("audit_logs").doc(`srv_${plan.eventKey}`).set(
      {
        orgId: String(after.orgId || ""),
        actorUid: plan.auditActorUid,
        actorName: plan.auditActorName,
        action: plan.auditAction,
        entityType: "MoveInChecklist",
        entityId: String(checklistId),
        details: plan.body.slice(0, 500),
        createdAt: FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
  }
);

/**
 * Verifies KES deposit arithmetic, writes server-side audit records, and
 * dispatches idempotent FCM push notifications on deposit settlement transitions.
 */
exports.onDepositSettlementWritten = onDocumentWritten(
  {
    document: "deposit_settlements/{settlementId}",
    database: DATABASE_ID,
  },
  async (event) => {
    const before = event.data?.before?.data() || null;
    const after = event.data?.after?.data() || null;
    if (!after) return;

    const settlementId = event.params.settlementId;
    const plan = buildSettlementTransitionDispatch(settlementId, before, after);
    if (!plan) return;

    if (!plan.reconciliation.mathValid) {
      logger.warn("Deposit settlement arithmetic mismatch detected by server trigger.", {
        settlementId,
        reconciliation: plan.reconciliation,
      });
    }

    const db = getFirestore(DATABASE_ID);
    const claimed = await claimServerEventIdempotency(db, plan.eventKey, {
      entityType: "DepositSettlement",
      entityId: settlementId,
      eventType: plan.eventType,
      status: plan.status,
      mathValid: plan.reconciliation.mathValid,
    });
    if (!claimed) return;

    const tokenEntries = await fetchActiveTokensForUsers(db, plan.recipients, "notifyRentDue");
    await dispatchPush(db, tokenEntries, RENT_CHANNEL_ID, {
      title: plan.title,
      body: plan.body,
      data: {
        eventType: plan.eventType,
        targetRoute: plan.targetRoute,
        settlementId: plan.settlementId,
        leaseId: plan.leaseId,
        unitId: plan.unitId,
        status: plan.status,
        recipientRole: plan.recipientRole,
        title: plan.title,
        body: plan.body,
      },
    });

    await db.collection("audit_logs").doc(`srv_${plan.eventKey}`).set(
      {
        orgId: String(after.orgId || ""),
        actorUid: plan.auditActorUid,
        actorName: plan.auditActorName,
        action: plan.auditAction,
        entityType: "DepositSettlement",
        entityId: String(settlementId),
        details: plan.body.slice(0, 500),
        createdAt: FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
  }
);
