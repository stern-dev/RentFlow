function formatKesWhole(cents) {
  const kes = Math.round(Number(cents || 0) / 100);
  return `KES ${kes.toLocaleString("en-US")}`;
}

/**
 * Computes authoritative KES deposit settlement numbers from stored cents and
 * itemized deductions, and reports whether client totals match server math.
 */
function computeSettlementReconciliationServer(settlement) {
  const s = settlement || {};
  const depositHeldCents = Math.max(0, Number(s.depositHeldCents || 0));
  const creditAppliedCents = Math.max(0, Number(s.creditAppliedCents || 0));
  const arrearsIncludedCents = Math.max(0, Number(s.arrearsIncludedCents || 0));
  const deductions = Array.isArray(s.deductions) ? s.deductions : [];
  const computedDeductionsTotalCents = deductions.reduce((acc, item) => {
    const amt = Math.max(0, Number((item && item.amountCents) || 0));
    return acc + amt;
  }, 0);

  const totalAvailableCents = depositHeldCents + creditAppliedCents;
  const totalChargesCents = computedDeductionsTotalCents + arrearsIncludedCents;
  const computedNetRefundCents = Math.max(0, totalAvailableCents - totalChargesCents);
  const computedBalanceOwedCents = Math.max(0, totalChargesCents - totalAvailableCents);

  const storedDeductionsTotal = Number(s.deductionsTotalCents || 0);
  const storedNetRefund = Number(s.netRefundCents || 0);
  const storedBalanceOwed = Number(s.balanceOwedCents || 0);

  const mathValid =
    storedDeductionsTotal === computedDeductionsTotalCents &&
    storedNetRefund === computedNetRefundCents &&
    storedBalanceOwed === computedBalanceOwedCents;

  return {
    mathValid,
    computedDeductionsTotalCents,
    computedNetRefundCents,
    computedBalanceOwedCents,
  };
}

/**
 * Determines FCM push dispatch metadata and recipients for a move-in checklist
 * status transition. Returns null when no notification is needed (e.g. DRAFT edits).
 */
function buildChecklistTransitionDispatch(checklistId, before, after) {
  if (!after || !after.status) return null;
  const prevStatus = before ? before.status : null;
  const nextStatus = after.status;
  if (prevStatus === nextStatus) return null;

  const unitLabel = `${after.propertyName || "Property"} • Unit ${after.unitNumber || "-"}`;
  const orgRecipients = [
    after.ownerUid,
    ...(Array.isArray(after.managerUids) ? after.managerUids : []),
    ...(Array.isArray(after.authorizedUids) ? after.authorizedUids : []),
  ].filter((uid) => uid && uid !== after.tenantUid);

  const updatedMarker = Number(after.updatedAt?.toMillis?.() || Date.now());
  const eventKey = `checklist_${checklistId}_${nextStatus}_${updatedMarker}`;

  if (nextStatus === "PENDING_TENANT_REVIEW" && after.tenantUid) {
    const title = `Move-In Checklist Ready • Unit ${after.unitNumber || "-"}`.slice(0, 140);
    const body = `${unitLabel}: Review your unit handover condition report and sign off or flag discrepancies.`.slice(0, 500);
    return {
      eventKey,
      eventType: "CHECKLIST_PENDING_REVIEW",
      recipientRole: "TENANT",
      recipients: [after.tenantUid],
      targetRoute: "tenant_home",
      checklistId: String(checklistId),
      leaseId: String(after.leaseId || ""),
      unitId: String(after.unitId || ""),
      status: nextStatus,
      title,
      body,
      auditAction: "CHECKLIST_SUBMITTED_FOR_REVIEW",
      auditActorUid: String(after.createdByUid || after.ownerUid || "system"),
      auditActorName: "Management",
    };
  }

  if (nextStatus === "ACKNOWLEDGED" && orgRecipients.length > 0) {
    const title = `Move-In Checklist Signed Off • Unit ${after.unitNumber || "-"}`.slice(0, 140);
    const body = `${after.tenantName || "Tenant"} acknowledged the move-in condition report for ${unitLabel}.`.slice(0, 500);
    return {
      eventKey,
      eventType: "CHECKLIST_ACKNOWLEDGED",
      recipientRole: "LANDLORD",
      recipients: orgRecipients,
      targetRoute: "portfolio",
      checklistId: String(checklistId),
      leaseId: String(after.leaseId || ""),
      unitId: String(after.unitId || ""),
      status: nextStatus,
      title,
      body,
      auditAction: "CHECKLIST_ACKNOWLEDGED",
      auditActorUid: String(after.acknowledgedByUid || after.tenantUid || "tenant"),
      auditActorName: String(after.tenantName || "Tenant"),
    };
  }

  if (nextStatus === "DISPUTED" && orgRecipients.length > 0) {
    const note = after.tenantResponseNote ? ` Note: ${after.tenantResponseNote}` : "";
    const title = `Move-In Discrepancy Flagged • Unit ${after.unitNumber || "-"}`.slice(0, 140);
    const body = `${after.tenantName || "Tenant"} flagged item(s) on ${unitLabel}.${note}`.slice(0, 500);
    return {
      eventKey,
      eventType: "CHECKLIST_DISPUTED",
      recipientRole: "LANDLORD",
      recipients: orgRecipients,
      targetRoute: "portfolio",
      checklistId: String(checklistId),
      leaseId: String(after.leaseId || ""),
      unitId: String(after.unitId || ""),
      status: nextStatus,
      title,
      body,
      auditAction: "CHECKLIST_DISPUTED",
      auditActorUid: String(after.tenantUid || "tenant"),
      auditActorName: String(after.tenantName || "Tenant"),
    };
  }

  return null;
}

/**
 * Determines FCM push dispatch metadata, recipients, and server audit intent for
 * a deposit settlement status transition. Returns null for DRAFT edits.
 */
function buildSettlementTransitionDispatch(settlementId, before, after) {
  if (!after || !after.status) return null;
  const prevStatus = before ? before.status : null;
  const nextStatus = after.status;
  if (prevStatus === nextStatus) return null;

  const unitLabel = `${after.propertyName || "Property"} • Unit ${after.unitNumber || "-"}`;
  const orgRecipients = [
    after.ownerUid,
    ...(Array.isArray(after.managerUids) ? after.managerUids : []),
    ...(Array.isArray(after.authorizedUids) ? after.authorizedUids : []),
  ].filter((uid) => uid && uid !== after.tenantUid);

  const reconciliation = computeSettlementReconciliationServer(after);
  const netRefundLabel = formatKesWhole(reconciliation.computedNetRefundCents);
  const balanceOwedLabel = formatKesWhole(reconciliation.computedBalanceOwedCents);
  const updatedMarker = Number(after.updatedAt?.toMillis?.() || Date.now());
  const eventKey = `settlement_${settlementId}_${nextStatus}_${updatedMarker}`;

  if (nextStatus === "ISSUED" && after.tenantUid) {
    const summaryText =
      reconciliation.computedBalanceOwedCents > 0
        ? `Balance owed: ${balanceOwedLabel}`
        : `Net deposit refund: ${netRefundLabel}`;
    const title = `Deposit Settlement Issued • Unit ${after.unitNumber || "-"}`.slice(0, 140);
    const body = `${unitLabel}: ${summaryText} (from ${formatKesWhole(after.depositHeldCents)} deposit). Tap to review breakdown.`.slice(0, 500);
    return {
      eventKey,
      eventType: "SETTLEMENT_ISSUED",
      recipientRole: "TENANT",
      recipients: [after.tenantUid],
      targetRoute: "tenant_home",
      settlementId: String(settlementId),
      leaseId: String(after.leaseId || ""),
      unitId: String(after.unitId || ""),
      status: nextStatus,
      title,
      body,
      reconciliation,
      auditAction: "DEPOSIT_SETTLEMENT_ISSUED",
      auditActorUid: String(after.createdByUid || after.ownerUid || "system"),
      auditActorName: "Management",
    };
  }

  if (nextStatus === "APPROVED" && orgRecipients.length > 0) {
    const title = `Deposit Statement Approved • Unit ${after.unitNumber || "-"}`.slice(0, 140);
    const body = `${after.tenantName || "Tenant"} approved the deposit settlement for ${unitLabel} (${netRefundLabel} refund).`.slice(0, 500);
    return {
      eventKey,
      eventType: "SETTLEMENT_APPROVED",
      recipientRole: "LANDLORD",
      recipients: orgRecipients,
      targetRoute: "portfolio",
      settlementId: String(settlementId),
      leaseId: String(after.leaseId || ""),
      unitId: String(after.unitId || ""),
      status: nextStatus,
      title,
      body,
      reconciliation,
      auditAction: "DEPOSIT_SETTLEMENT_APPROVED",
      auditActorUid: String(after.tenantUid || "tenant"),
      auditActorName: String(after.tenantName || "Tenant"),
    };
  }

  if (nextStatus === "DISPUTED" && orgRecipients.length > 0) {
    const note = after.tenantResponseNote ? ` Note: ${after.tenantResponseNote}` : "";
    const title = `Deposit Review Requested • Unit ${after.unitNumber || "-"}`.slice(0, 140);
    const body = `${after.tenantName || "Tenant"} requested review of deductions on ${unitLabel}.${note}`.slice(0, 500);
    return {
      eventKey,
      eventType: "SETTLEMENT_DISPUTED",
      recipientRole: "LANDLORD",
      recipients: orgRecipients,
      targetRoute: "portfolio",
      settlementId: String(settlementId),
      leaseId: String(after.leaseId || ""),
      unitId: String(after.unitId || ""),
      status: nextStatus,
      title,
      body,
      reconciliation,
      auditAction: "DEPOSIT_SETTLEMENT_DISPUTED",
      auditActorUid: String(after.tenantUid || "tenant"),
      auditActorName: String(after.tenantName || "Tenant"),
    };
  }

  if (nextStatus === "SETTLED" && after.tenantUid) {
    const refPart = after.refundReferenceCode ? ` (Ref ${after.refundReferenceCode})` : "";
    const outcome =
      reconciliation.computedNetRefundCents > 0
        ? `Refund of ${netRefundLabel} sent via ${after.refundMethod || "MPESA"}${refPart}.`
        : "Final deposit account closed with KES 0 net refund due.";
    const title = `Deposit Settlement Completed • Unit ${after.unitNumber || "-"}`.slice(0, 140);
    const body = `${unitLabel}: ${outcome}`.slice(0, 500);
    return {
      eventKey,
      eventType: "SETTLEMENT_SETTLED",
      recipientRole: "TENANT",
      recipients: [after.tenantUid],
      targetRoute: "tenant_home",
      settlementId: String(settlementId),
      leaseId: String(after.leaseId || ""),
      unitId: String(after.unitId || ""),
      status: nextStatus,
      title,
      body,
      reconciliation,
      auditAction: "DEPOSIT_SETTLEMENT_FINALIZED",
      auditActorUid: String(after.settledByUid || after.ownerUid || "system"),
      auditActorName: "Management",
    };
  }

  return null;
}

module.exports = {
  formatKesWhole,
  computeSettlementReconciliationServer,
  buildChecklistTransitionDispatch,
  buildSettlementTransitionDispatch,
};
