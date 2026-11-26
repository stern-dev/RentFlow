const DAY_MS = 86_400_000;
const MONTH_DAYS = 30;

function formatKesWhole(cents) {
  const kes = Math.round(Number(cents || 0) / 100);
  return `KES ${kes.toLocaleString("en-US")}`;
}

function clamp(val, min, max) {
  return Math.min(max, Math.max(min, val));
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

function evaluateLeaseRenewalCandidate(lease, nowMs = Date.now()) {
  if (!lease || lease.status !== "ACTIVE") return null;
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
  if (invoice.status === "PAID" || invoice.status === "CANCELLED") return null;
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

module.exports = {
  DAY_MS,
  MONTH_DAYS,
  formatKesWhole,
  computeEscalatedRentCents,
  computeExtendedEndDateMs,
  computeGracePeriodEndsMs,
  computeLateFeeCents,
  evaluateLeaseRenewalCandidate,
  evaluateOverdueLateFeeCandidate,
  buildLeaseRenewalDispatch,
};
