const DAY_MS = 86_400_000;
const MONTH_DAYS = 30;
const NAIROBI_OFFSET_MS = 3 * 60 * 60 * 1000;

function formatKesWhole(cents) {
  const kes = Math.round(Number(cents || 0) / 100);
  return `KES ${kes.toLocaleString("en-US")}`;
}

function clamp(val, min, max) {
  return Math.min(max, Math.max(min, val));
}

function getNairobiDateParts(epochMs = Date.now()) {
  const shifted = new Date(Number(epochMs || 0) + NAIROBI_OFFSET_MS);
  return {
    year: shifted.getUTCFullYear(),
    monthIndex: shifted.getUTCMonth(), // 0-11
    dayOfMonth: shifted.getUTCDate(), // 1-31
  };
}

function nairobiBillingPeriodForEpochMs(epochMs = Date.now()) {
  const { year, monthIndex } = getNairobiDateParts(epochMs);
  const monthStr = String(monthIndex + 1).padStart(2, "0");
  return `${year}-${monthStr}`;
}

function formatNairobiDate(epochMs) {
  const value = Number(epochMs || 0);
  if (!Number.isFinite(value) || value <= 0) return "N/A";
  try {
    return new Intl.DateTimeFormat("en-KE", {
      timeZone: "Africa/Nairobi",
      day: "numeric",
      month: "short",
      year: "numeric",
    }).format(new Date(value));
  } catch {
    return "N/A";
  }
}

function billingPeriodLabel(billingPeriod) {
  const parts = String(billingPeriod || "").trim().split("-");
  if (parts.length !== 2) return String(billingPeriod || "");
  const year = Number(parts[0]);
  const monthIdx = Number(parts[1]) - 1;
  if (!Number.isFinite(year) || !Number.isFinite(monthIdx) || monthIdx < 0 || monthIdx > 11) {
    return String(billingPeriod || "");
  }
  const dt = new Date(Date.UTC(year, monthIdx, 15));
  try {
    return new Intl.DateTimeFormat("en-KE", {
      timeZone: "Africa/Nairobi",
      month: "long",
      year: "numeric",
    }).format(dt);
  } catch {
    return String(billingPeriod || "");
  }
}

function nextBillingPeriod(billingPeriod) {
  const parts = String(billingPeriod || "").trim().split("-");
  const year = Number(parts[0]);
  const monthIdx = Number(parts[1]) - 1;
  if (!Number.isFinite(year) || !Number.isFinite(monthIdx) || monthIdx < 0 || monthIdx > 11) {
    return nairobiBillingPeriodForEpochMs(Date.now() + 31 * DAY_MS);
  }
  const dt = new Date(Date.UTC(year, monthIdx + 1, 15));
  return `${dt.getUTCFullYear()}-${String(dt.getUTCMonth() + 1).padStart(2, "0")}`;
}

function computeCycleBillingDateMs(billingPeriod, dueDayOfMonth, leadDays) {
  const parts = String(billingPeriod || "").trim().split("-");
  const year = Number(parts[0]);
  const monthIdx = Number(parts[1]) - 1;
  if (!Number.isFinite(year) || !Number.isFinite(monthIdx) || monthIdx < 0 || monthIdx > 11) {
    return Date.now();
  }
  const daysInMonth = new Date(Date.UTC(year, monthIdx + 1, 0)).getUTCDate();
  const clampedDay = clamp(Math.round(Number(dueDayOfMonth ?? 5)), 1, daysInMonth);
  const clampedLead = clamp(Math.round(Number(leadDays ?? 5)), 0, 15);
  return Date.UTC(year, monthIdx, clampedDay, 0, 0, 0, 0) - NAIROBI_OFFSET_MS - clampedLead * DAY_MS;
}

function computeNextBillingEpochMs(afterPeriod, dueDayOfMonth, leadDays) {
  return computeCycleBillingDateMs(nextBillingPeriod(afterPeriod), dueDayOfMonth, leadDays);
}

function computeDueDateEpochMs(billingPeriod, dueDay) {
  const parts = String(billingPeriod || "").trim().split("-");
  if (parts.length !== 2) return Date.now() + 5 * DAY_MS;
  const year = Number(parts[0]);
  const monthIdx = Number(parts[1]) - 1;
  if (!Number.isFinite(year) || !Number.isFinite(monthIdx) || monthIdx < 0 || monthIdx > 11) {
    return Date.now() + 5 * DAY_MS;
  }
  const daysInMonth = new Date(Date.UTC(year, monthIdx + 1, 0)).getUTCDate();
  const clampedDay = clamp(Math.round(Number(dueDay || 5)), 1, daysInMonth);
  // 23:59:00 Africa/Nairobi = UTC minus 3h
  return Date.UTC(year, monthIdx, clampedDay, 23, 59, 0, 0) - NAIROBI_OFFSET_MS;
}

function computeEscalatedRentCents(lease) {
  const l = lease || {};
  const base = Math.max(1, Number(l.rentCents || 0));
  const escType = String(l.escalationType || "NONE");
  if (escType === "PERCENT") {
    const pct = clamp(Number(l.escalationPercent || 0), 0, 100);
    const inc = Math.max(0, Math.round(base * (pct / 100)));
    return Math.min(1_000_000_000, base + inc);
  }
  if (escType === "FIXED_KES") {
    const fixed = Math.max(0, Math.round(Number(l.escalationFixedCents || 0)));
    return Math.min(1_000_000_000, base + fixed);
  }
  return base;
}

function computeExtendedEndDateMs(currentEndDateEpochMs, renewalDurationMonths, nowMs = Date.now()) {
  const base = Math.max(Number(currentEndDateEpochMs || 0), Number(nowMs || Date.now()));
  const months = clamp(Math.round(Number(renewalDurationMonths || 12)), 1, 60);
  return base + months * MONTH_DAYS * DAY_MS;
}

function computeGracePeriodEndsMs(dueDateEpochMs, gracePeriodDays) {
  const days = clamp(Math.round(Number(gracePeriodDays ?? 5)), 0, 30);
  return Number(dueDateEpochMs || 0) + days * DAY_MS;
}

function computeLateFeeCents(overdueBalanceCents, lateFeePercent) {
  const balance = Number(overdueBalanceCents || 0);
  if (balance <= 0) return 0;
  const rate = clamp(Number(lateFeePercent ?? 5), 0, 50);
  if (rate <= 0) return 0;
  return clamp(Math.round(balance * (rate / 100)), 0, 500_000_000);
}

function computeBillingPeriodRentCents(lease, billingPeriod, baseRentOverrideCents = null) {
  const rawBase = baseRentOverrideCents !== null && baseRentOverrideCents !== undefined
    ? baseRentOverrideCents
    : lease?.rentCents;
  const baseRent = Math.max(0, Number(rawBase || 0));
  const parts = String(billingPeriod || "").trim().split("-");
  if (parts.length !== 2) {
    return { rentCents: baseRent, prorated: false, occupiedDays: 30, daysInMonth: 30, prorationNote: null };
  }
  const year = Number(parts[0]);
  const monthIdx = Number(parts[1]) - 1;
  if (!Number.isFinite(year) || !Number.isFinite(monthIdx) || monthIdx < 0 || monthIdx > 11) {
    return { rentCents: baseRent, prorated: false, occupiedDays: 30, daysInMonth: 30, prorationNote: null };
  }

  const daysInMonth = Math.max(28, new Date(Date.UTC(year, monthIdx + 1, 0)).getUTCDate());
  const monthStartMs = Date.UTC(year, monthIdx, 1, 0, 0, 0, 0) - NAIROBI_OFFSET_MS;
  const monthEndExclusiveMs = Date.UTC(year, monthIdx + 1, 1, 0, 0, 0, 0) - NAIROBI_OFFSET_MS;

  const moveOutStatus = String(lease?.moveOutNoticeStatus || "NONE");
  const moveOutMs = Number(lease?.requestedMoveOutEpochMs || 0);

  if (moveOutStatus === "ACKNOWLEDGED" && moveOutMs > 0) {
    if (moveOutMs < monthStartMs) {
      return {
        rentCents: 0,
        prorated: true,
        occupiedDays: 0,
        daysInMonth,
        prorationNote: `Vacated prior to ${billingPeriodLabel(billingPeriod)}`,
      };
    }
    if (moveOutMs < monthEndExclusiveMs) {
      const { dayOfMonth } = getNairobiDateParts(moveOutMs);
      const occupiedDays = clamp(dayOfMonth, 1, daysInMonth);
      if (occupiedDays < daysInMonth && baseRent > 0) {
        const proratedRent = clamp(Math.round(baseRent * (occupiedDays / daysInMonth)), 1, baseRent);
        return {
          rentCents: proratedRent,
          prorated: true,
          occupiedDays,
          daysInMonth,
          prorationNote: `Prorated ${occupiedDays}/${daysInMonth} days (move-out ${formatNairobiDate(moveOutMs)})`,
        };
      }
    }
  }

  return {
    rentCents: baseRent,
    prorated: false,
    occupiedDays: daysInMonth,
    daysInMonth,
    prorationNote: null,
  };
}

function evaluateAutoBillingCandidate(lease, leaseInvoices = [], billingPeriod, nowMs = Date.now(), schedule = null) {
  if (!lease || lease.status !== "ACTIVE") return null;
  if (schedule) {
    if (String(schedule.scheduleStatus || "ACTIVE") !== "ACTIVE") return null;
  } else if (lease.autoBillingEnabled === false) {
    return null;
  }

  const baseRentCents = Number(schedule?.amountCents || 0) > 0
    ? Number(schedule.amountCents)
    : Number(lease.rentCents || 0);
  if (baseRentCents <= 0) return null;

  const period = billingPeriod || nairobiBillingPeriodForEpochMs(nowMs);
  const leaseId = String(lease.id || "");
  const deterministicId = `${leaseId}_${period}`.replace(/[^a-zA-Z0-9_\-]/g, "_");

  const alreadyExists = (leaseInvoices || []).some((inv) => {
    if (!inv) return false;
    if (inv.id === deterministicId) return true;
    return inv.leaseId === leaseId && inv.billingPeriod === period && inv.status !== "CANCELLED";
  });
  if (alreadyExists) return null;

  const dueDay = clamp(Math.round(Number(schedule?.billingDayOfMonth ?? lease.dueDayOfMonth ?? 5)), 1, 28);
  const leadDays = clamp(Math.round(Number(schedule?.leadDays ?? lease.billingLeadDays ?? 5)), 0, 15);
  const dueDateEpochMs = computeDueDateEpochMs(period, dueDay);
  const billingDateEpochMs = computeCycleBillingDateMs(period, dueDay, leadDays);
  if (nowMs < billingDateEpochMs) return null;

  const rentResult = computeBillingPeriodRentCents(lease, period, baseRentCents);
  if (rentResult.rentCents <= 0) return null;

  const extraChargesCents = Math.max(0, Math.round(Number(schedule?.recurringExtraCents || 0)));
  const extraChargesNote =
    extraChargesCents > 0 && schedule?.recurringExtraNote
      ? String(schedule.recurringExtraNote).trim().slice(0, 300) || null
      : null;

  const previousBalanceCents = (leaseInvoices || [])
    .filter(
      (inv) =>
        inv &&
        inv.billingPeriod !== period &&
        inv.status !== "CANCELLED" &&
        inv.status !== "DRAFT" &&
        Number(inv.outstandingCents || 0) > 0
    )
    .reduce((sum, inv) => sum + Number(inv.outstandingCents || 0), 0);

  const totalDueCents = Math.min(2_000_000_000, rentResult.rentCents + extraChargesCents + previousBalanceCents);
  // Grace period settings are referenced strictly from the Lease document
  const gracePeriodEndsEpochMs = computeGracePeriodEndsMs(dueDateEpochMs, lease.gracePeriodDays ?? 5);

  const isDraft = String(schedule?.issueMode || "AUTO_ISSUE") === "DRAFT_FOR_REVIEW";
  const creditToApplyCents = isDraft
    ? 0
    : Math.min(Math.max(0, Number(lease.creditBalanceCents || 0)), totalDueCents);
  const outstandingCents = Math.max(0, totalDueCents - creditToApplyCents);
  const status = isDraft
    ? "DRAFT"
    : outstandingCents === 0
      ? "PAID"
      : creditToApplyCents > 0
        ? "PARTIALLY_PAID"
        : "ISSUED";

  return {
    deterministicId,
    scheduleId: String(schedule?.id || leaseId),
    billingPeriod: period,
    billingDateEpochMs,
    rentCents: rentResult.rentCents,
    extraChargesCents,
    extraChargesNote,
    previousBalanceCents,
    creditToApplyCents,
    totalDueCents,
    paidCents: creditToApplyCents,
    outstandingCents,
    dueDateEpochMs,
    gracePeriodEndsEpochMs,
    issuedAtEpochMs: isDraft ? null : nowMs,
    paidAtEpochMs: !isDraft && outstandingCents === 0 ? nowMs : null,
    status,
    prorated: rentResult.prorated,
    prorationNote: rentResult.prorationNote,
  };
}

function evaluateDraftIssuance(draftInvoice, lease, extraChargesCents, nowMs = Date.now()) {
  const draft = draftInvoice || {};
  const l = lease || {};
  const extras = Math.max(
    0,
    Math.round(Number(extraChargesCents !== undefined && extraChargesCents !== null ? extraChargesCents : draft.extraChargesCents || 0))
  );
  const totalDueCents = Math.min(
    2_000_000_000,
    Math.max(0, Number(draft.rentCents || 0)) +
      extras +
      Math.max(0, Number(draft.previousBalanceCents || 0)) +
      Math.max(0, Number(draft.lateFeeCents || 0))
  );
  const priorPaid = clamp(Math.max(0, Number(draft.paidCents || 0)), 0, totalDueCents);
  const remainingBeforeCredit = Math.max(0, totalDueCents - priorPaid);
  const creditToApplyCents = Math.min(Math.max(0, Number(l.creditBalanceCents || 0)), remainingBeforeCredit);
  const newPaidCents = Math.min(totalDueCents, priorPaid + creditToApplyCents);
  const newOutstandingCents = Math.max(0, totalDueCents - newPaidCents);
  const dueDateEpochMs = Number(draft.dueDateEpochMs || nowMs);
  const gracePeriodEndsEpochMs = computeGracePeriodEndsMs(dueDateEpochMs, l.gracePeriodDays ?? 5);
  const newStatus =
    newOutstandingCents === 0
      ? "PAID"
      : nowMs > dueDateEpochMs
        ? "OVERDUE"
        : newPaidCents > 0
          ? "PARTIALLY_PAID"
          : "ISSUED";

  return {
    totalDueCents,
    creditToApplyCents,
    newPaidCents,
    newOutstandingCents,
    gracePeriodEndsEpochMs,
    issuedAtEpochMs: nowMs,
    paidAtEpochMs: newOutstandingCents === 0 ? nowMs : null,
    newStatus,
  };
}

function evaluateLeaseRenewalCandidate(lease, nowMs = Date.now()) {
  if (!lease || lease.status !== "ACTIVE") return null;
  const noticeStatus = String(lease.moveOutNoticeStatus || "NONE");
  if (noticeStatus === "SUBMITTED" || noticeStatus === "ACKNOWLEDGED") return null;

  const mode = String(lease.renewalMode || "OFFER");
  if (mode === "MANUAL" || (mode !== "AUTO_RENEW" && mode !== "OFFER")) return null;

  const currentEndMs = Number(lease.endDateEpochMs || 0);
  if (currentEndMs <= 0) return null;

  if (
    lease.lastRenewalProcessedForEndMs !== undefined &&
    lease.lastRenewalProcessedForEndMs !== null &&
    Number(lease.lastRenewalProcessedForEndMs) === currentEndMs
  ) {
    return null;
  }

  const offerStatus = String(lease.renewalOfferStatus || "NONE");
  if (offerStatus === "PENDING" || offerStatus === "DECLINED") {
    return null;
  }

  const leadDays = clamp(Math.round(Number(lease.renewalLeadDays ?? 30)), 1, 180);
  const triggerAtMs = currentEndMs - leadDays * DAY_MS;
  if (nowMs < triggerAtMs) return null;

  const projectedRentCents = computeEscalatedRentCents(lease);
  const projectedEndDateEpochMs = computeExtendedEndDateMs(
    currentEndMs,
    lease.renewalDurationMonths ?? 12,
    nowMs
  );

  return {
    actionType: mode === "AUTO_RENEW" ? "AUTO_RENEW" : "SEND_OFFER",
    projectedRentCents,
    projectedEndDateEpochMs,
  };
}

function evaluateOverdueLateFeeCandidate(invoice, lease, nowMs = Date.now()) {
  if (!invoice || !lease) return null;
  if (invoice.status === "DRAFT" || invoice.status === "PAID" || invoice.status === "CANCELLED") return null;
  const outstandingCents = Number(invoice.outstandingCents || 0);
  if (outstandingCents <= 0) return null;

  if (
    (invoice.lateFeeAppliedAtEpochMs !== undefined && invoice.lateFeeAppliedAtEpochMs !== null) ||
    Number(invoice.lateFeeCents || 0) > 0 ||
    invoice.lateFeeWaived === true
  ) {
    return null;
  }

  const lateFeeEnabled = lease.lateFeeEnabled !== false;
  const ratePercent = clamp(Number(lease.lateFeePercent ?? 5), 0, 50);
  if (!lateFeeEnabled || ratePercent <= 0) return null;

  const gracePeriodEndsEpochMs =
    invoice.gracePeriodEndsEpochMs !== undefined && invoice.gracePeriodEndsEpochMs !== null
      ? Number(invoice.gracePeriodEndsEpochMs)
      : computeGracePeriodEndsMs(invoice.dueDateEpochMs, lease.gracePeriodDays ?? 5);

  if (nowMs <= gracePeriodEndsEpochMs) return null;

  const lateFeeCents = computeLateFeeCents(outstandingCents, ratePercent);
  if (lateFeeCents <= 0) return null;

  const totalDue = Number(invoice.totalDueCents || 0);
  return {
    lateFeeCents,
    lateFeeRatePercent: ratePercent,
    gracePeriodEndsEpochMs,
    newTotalDueCents: Math.min(2_000_000_000, totalDue + lateFeeCents),
    newOutstandingCents: Math.min(2_000_000_000, outstandingCents + lateFeeCents),
  };
}

function buildLeaseRenewalDispatch(leaseId, before, after) {
  if (!after) return null;
  const prevOfferStatus = before ? String(before.renewalOfferStatus || "NONE") : "NONE";
  const nextOfferStatus = String(after.renewalOfferStatus || "NONE");
  if (prevOfferStatus === nextOfferStatus) return null;
  if (!["PENDING", "ACCEPTED", "DECLINED", "AUTO_RENEWED"].includes(nextOfferStatus)) {
    return null;
  }

  const unitLabel = `${after.propertyName || "Property"} • Unit ${after.unitNumber || "-"}`;
  const updatedMarker = Number(after.updatedAt?.toMillis?.() || after.renewalOfferedAtEpochMs || after.renewalRespondedAtEpochMs || Date.now());
  const eventKey = `lease_renewal_${leaseId}_${nextOfferStatus}_${updatedMarker}`;
  const orgRecipients = [
    after.ownerUid,
    ...(Array.isArray(after.managerUids) ? after.managerUids : []),
    ...(Array.isArray(after.authorizedUids) ? after.authorizedUids : []),
  ].filter((uid) => uid && uid !== after.tenantUid);

  if (nextOfferStatus === "PENDING" && after.tenantUid) {
    const rentLabel = formatKesWhole(after.offeredRentCents || after.rentCents);
    return {
      eventKey,
      eventType: "LEASE_RENEWAL_OFFER",
      recipientRole: "TENANT",
      recipients: [after.tenantUid],
      targetRoute: "tenant_home",
      leaseId: String(leaseId),
      unitId: String(after.unitId || ""),
      status: nextOfferStatus,
      title: `Lease Renewal Offer • Unit ${after.unitNumber || "-"}`.slice(0, 140),
      body: `${unitLabel}: New monthly rent ${rentLabel}/mo available to review and accept.`.slice(0, 500),
    };
  }

  if (nextOfferStatus === "AUTO_RENEWED" && after.tenantUid) {
    const rentLabel = formatKesWhole(after.rentCents);
    return {
      eventKey,
      eventType: "LEASE_AUTO_RENEWED",
      recipientRole: "TENANT",
      recipients: [after.tenantUid],
      targetRoute: "tenant_home",
      leaseId: String(leaseId),
      unitId: String(after.unitId || ""),
      status: nextOfferStatus,
      title: `Lease Auto-Renewed • Unit ${after.unitNumber || "-"}`.slice(0, 140),
      body: `${unitLabel}: Lease extended automatically at ${rentLabel}/mo.`.slice(0, 500),
    };
  }

  if ((nextOfferStatus === "ACCEPTED" || nextOfferStatus === "DECLINED") && orgRecipients.length > 0) {
    const accepted = nextOfferStatus === "ACCEPTED";
    const noteSuffix = !accepted && after.renewalResponseNote ? ` Note: ${after.renewalResponseNote}` : "";
    return {
      eventKey,
      eventType: "LEASE_RENEWAL_RESPONSE",
      recipientRole: "LANDLORD",
      recipients: orgRecipients,
      targetRoute: "portfolio",
      targetTab: "3",
      leaseId: String(leaseId),
      unitId: String(after.unitId || ""),
      status: nextOfferStatus,
      title: `${accepted ? "Renewal Accepted" : "Renewal Declined"} • Unit ${after.unitNumber || "-"}`.slice(0, 140),
      body: `${after.tenantName || "Tenant"} ${accepted ? "accepted" : "declined"} the renewal offer for ${unitLabel}.${noteSuffix}`.slice(0, 500),
    };
  }

  return null;
}

function buildMoveOutNoticeDispatch(leaseId, before, after) {
  if (!after) return null;
  const prevStatus = before ? String(before.moveOutNoticeStatus || "NONE") : "NONE";
  const nextStatus = String(after.moveOutNoticeStatus || "NONE");
  const prevDate = Number(before?.requestedMoveOutEpochMs || 0);
  const nextDate = Number(after.requestedMoveOutEpochMs || 0);

  if (prevStatus === nextStatus && prevDate === nextDate) return null;
  if (!["SUBMITTED", "ACKNOWLEDGED", "DECLINED"].includes(nextStatus)) {
    return null;
  }

  const unitLabel = `${after.propertyName || "Property"} • Unit ${after.unitNumber || "-"}`;
  const moveOutLabel = formatNairobiDate(after.requestedMoveOutEpochMs);
  const updatedMarker = Number(
    after.updatedAt?.toMillis?.() ||
      after.moveOutNoticeReviewedAtMs ||
      after.moveOutNoticeSubmittedAtMs ||
      Date.now()
  );
  const eventKey = `lease_moveout_${leaseId}_${nextStatus}_${updatedMarker}`;
  const orgRecipients = [
    after.ownerUid,
    ...(Array.isArray(after.managerUids) ? after.managerUids : []),
    ...(Array.isArray(after.authorizedUids) ? after.authorizedUids : []),
  ].filter((uid) => uid && uid !== after.tenantUid);

  if (nextStatus === "SUBMITTED" && orgRecipients.length > 0) {
    const reasonSuffix = after.moveOutNoticeReason ? ` — ${after.moveOutNoticeReason}` : "";
    return {
      eventKey,
      eventType: "MOVE_OUT_NOTICE_SUBMITTED",
      recipientRole: "LANDLORD",
      recipients: orgRecipients,
      targetRoute: "portfolio",
      targetTab: "3",
      leaseId: String(leaseId),
      unitId: String(after.unitId || ""),
      status: nextStatus,
      title: `Move-Out Notice • Unit ${after.unitNumber || "-"}`.slice(0, 140),
      body: `${after.tenantName || "Tenant"} gave notice to vacate ${unitLabel} on ${moveOutLabel}.${reasonSuffix}`.slice(0, 500),
    };
  }

  if ((nextStatus === "ACKNOWLEDGED" || nextStatus === "DECLINED") && after.tenantUid) {
    const acked = nextStatus === "ACKNOWLEDGED";
    const noteSuffix = after.moveOutNoticeResponseNote ? ` Note: ${after.moveOutNoticeResponseNote}` : "";
    return {
      eventKey,
      eventType: "MOVE_OUT_NOTICE_REVIEWED",
      recipientRole: "TENANT",
      recipients: [after.tenantUid],
      targetRoute: "tenant_home",
      leaseId: String(leaseId),
      unitId: String(after.unitId || ""),
      status: nextStatus,
      title: `${acked ? "Move-Out Notice Acknowledged" : "Move-Out Notice Revision Needed"} • Unit ${after.unitNumber || "-"}`.slice(0, 140),
      body: acked
        ? `Your notice to vacate ${unitLabel} on ${moveOutLabel} was acknowledged.${noteSuffix}`.slice(0, 500)
        : `Update requested on your move-out notice for ${unitLabel} (${moveOutLabel}).${noteSuffix}`.slice(0, 500),
    };
  }

  return null;
}

module.exports = {
  DAY_MS,
  MONTH_DAYS,
  formatKesWhole,
  nairobiBillingPeriodForEpochMs,
  nextBillingPeriod,
  computeCycleBillingDateMs,
  computeNextBillingEpochMs,
  computeDueDateEpochMs,
  computeEscalatedRentCents,
  computeExtendedEndDateMs,
  computeGracePeriodEndsMs,
  computeLateFeeCents,
  computeBillingPeriodRentCents,
  evaluateAutoBillingCandidate,
  evaluateDraftIssuance,
  evaluateLeaseRenewalCandidate,
  evaluateOverdueLateFeeCandidate,
  buildLeaseRenewalDispatch,
  buildMoveOutNoticeDispatch,
};
