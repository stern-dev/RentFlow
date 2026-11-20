const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const {
  onDocumentCreated,
  onDocumentUpdated,
} = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const logger = require("firebase-functions/logger");

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
 * where maintenance push notifications remain enabled.
 */
async function fetchActiveTokensForUsers(db, uids) {
  const uniqueUids = [...new Set((uids || []).filter((id) => typeof id === "string" && id.trim().length > 0))];
  const tokenRefs = [];

  for (const uid of uniqueUids) {
    const userDoc = await db.collection("users").doc(uid).get();
    if (userDoc.exists && userDoc.get("notifyMaintenance") === false) {
      continue;
    }
    const snap = await db.collection("users").doc(uid).collection("fcm_tokens").get();
    snap.forEach((doc) => {
      const data = doc.data() || {};
      if (data.notifyMaintenance !== false && typeof data.token === "string" && data.token.length >= 10) {
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
 * Sends a Firebase Cloud Messaging multicast notification and cleans up expired
 * or unregistered device tokens from Firestore.
 */
async function dispatchMaintenancePush(db, tokenEntries, payload) {
  if (!tokenEntries || tokenEntries.length === 0) {
    logger.info("No active FCM device tokens found for recipients.", { eventType: payload.data.eventType });
    return;
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
        channelId: MAINTENANCE_CHANNEL_ID,
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
  logger.info("FCM maintenance push dispatched.", {
    eventType: payload.data.eventType,
    successCount: response.successCount,
    failureCount: response.failureCount,
  });
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

    const tokenEntries = await fetchActiveTokensForUsers(db, recipients);
    const priorityLabel = formatPriorityLabel(req.priority);
    const title = `New Maintenance Ticket (${priorityLabel})`.slice(0, 140);
    const body = `${req.propertyName || "Property"} • Unit ${req.unitNumber || "-"}: ${req.title || "Repair requested"}`.slice(0, 500);

    await dispatchMaintenancePush(db, tokenEntries, {
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

    const tokenEntries = await fetchActiveTokensForUsers(db, [tenantUid]);
    const statusLabel = formatStatusLabel(after.status);
    const noteSuffix = after.latestUpdateNote ? ` — ${after.latestUpdateNote}` : "";
    const title = `Maintenance Status: ${statusLabel}`.slice(0, 140);
    const body = `${after.propertyName || "Property"} • Unit ${after.unitNumber || "-"} (${after.title || "Repair"}) is now ${statusLabel}${noteSuffix}`.slice(0, 500);

    await dispatchMaintenancePush(db, tokenEntries, {
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

async function fetchRentReminderTokens(db, uid) {
  if (typeof uid !== "string" || uid.trim().length === 0) return [];
  const userDoc = await db.collection("users").doc(uid).get();
  if (userDoc.exists && userDoc.get("notifyRentDue") === false) {
    return [];
  }
  const snap = await db.collection("users").doc(uid).collection("fcm_tokens").get();
  const tokenRefs = [];
  snap.forEach((doc) => {
    const data = doc.data() || {};
    if (typeof data.token === "string" && data.token.length >= 10) {
      tokenRefs.push({ token: data.token, ref: doc.ref });
    }
  });
  return tokenRefs;
}

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

    const messaging = getMessaging();
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
        userId: inv.tenantUid,
        orgId: inv.orgId || "rent_reminder",
        type: "RENT_DUE",
        title: title.slice(0, 140),
        body: body.slice(0, 500),
        relatedEntityId: docSnap.id,
        read: false,
        createdAt: FieldValue.serverTimestamp(),
      });

      const tokenEntries = await fetchRentReminderTokens(db, inv.tenantUid);
      if (tokenEntries.length > 0) {
        const tokens = tokenEntries.map((entry) => entry.token);
        await messaging.sendEachForMulticast({
          tokens,
          notification: {
            title: title.slice(0, 140),
            body: body.slice(0, 500),
          },
          data: {
            eventType: "RENT_REMINDER",
            targetRoute: "tenant_pay",
            invoiceId: docSnap.id,
            unitId: String(inv.unitId || ""),
            status: String(inv.status || "UNPAID"),
            recipientRole: "TENANT",
            title: title.slice(0, 140),
            body: body.slice(0, 500),
          },
          android: {
            priority: "high",
            notification: {
              channelId: RENT_CHANNEL_ID,
              icon: "ic_notification",
              clickAction: "FLUTTER_NOTIFICATION_CLICK",
            },
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
