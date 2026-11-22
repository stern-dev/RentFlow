const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  formatKesWhole,
  computeSettlementReconciliationServer,
  buildChecklistTransitionDispatch,
  buildSettlementTransitionDispatch,
} = require("./handoverHelpers");

test("computeSettlementReconciliationServer verifies refund, zero balance, balance owed, and detects mismatch", () => {
  const validRefund = computeSettlementReconciliationServer({
    depositHeldCents: 3000000,
    creditAppliedCents: 200000,
    arrearsIncludedCents: 500000,
    deductions: [{ amountCents: 300000 }, { amountCents: 200000 }],
    deductionsTotalCents: 500000,
    netRefundCents: 2200000,
    balanceOwedCents: 0,
  });
  assert.equal(validRefund.mathValid, true);
  assert.equal(validRefund.computedNetRefundCents, 2200000);
  assert.equal(validRefund.computedBalanceOwedCents, 0);

  const validOwed = computeSettlementReconciliationServer({
    depositHeldCents: 1000000,
    creditAppliedCents: 0,
    arrearsIncludedCents: 800000,
    deductions: [{ amountCents: 450000 }],
    deductionsTotalCents: 450000,
    netRefundCents: 0,
    balanceOwedCents: 250000,
  });
  assert.equal(validOwed.mathValid, true);
  assert.equal(validOwed.computedNetRefundCents, 0);
  assert.equal(validOwed.computedBalanceOwedCents, 250000);

  const tampered = computeSettlementReconciliationServer({
    depositHeldCents: 1000000,
    creditAppliedCents: 0,
    arrearsIncludedCents: 0,
    deductions: [{ amountCents: 400000 }],
    deductionsTotalCents: 400000,
    netRefundCents: 900000, // wrong: should be 600000
    balanceOwedCents: 0,
  });
  assert.equal(tampered.mathValid, false);
  assert.equal(tampered.computedNetRefundCents, 600000);
});

test("buildChecklistTransitionDispatch routes status transitions and ignores DRAFT or unchanged status", () => {
  const baseDoc = {
    leaseId: "lease_1",
    propertyName: "Kilimani Heights",
    unitId: "unit_1",
    unitNumber: "A2",
    ownerUid: "owner_1",
    managerUids: ["mgr_1"],
    authorizedUids: ["owner_1", "mgr_1"],
    tenantUid: "tenant_1",
    tenantName: "Brian Ochieng",
    createdByUid: "owner_1",
    updatedAt: { toMillis: () => 1763800000000 },
  };

  assert.equal(buildChecklistTransitionDispatch("cl_1", null, { ...baseDoc, status: "DRAFT" }), null);
  assert.equal(
    buildChecklistTransitionDispatch("cl_1", { ...baseDoc, status: "PENDING_TENANT_REVIEW" }, { ...baseDoc, status: "PENDING_TENANT_REVIEW" }),
    null
  );

  const pendingPlan = buildChecklistTransitionDispatch("cl_1", { ...baseDoc, status: "DRAFT" }, { ...baseDoc, status: "PENDING_TENANT_REVIEW" });
  assert.equal(pendingPlan.eventType, "CHECKLIST_PENDING_REVIEW");
  assert.equal(pendingPlan.targetRoute, "tenant_home");
  assert.deepEqual(pendingPlan.recipients, ["tenant_1"]);
  assert.equal(pendingPlan.eventKey, "checklist_cl_1_PENDING_TENANT_REVIEW_1763800000000");

  const ackPlan = buildChecklistTransitionDispatch("cl_1", { ...baseDoc, status: "PENDING_TENANT_REVIEW" }, { ...baseDoc, status: "ACKNOWLEDGED", acknowledgedByUid: "tenant_1" });
  assert.equal(ackPlan.eventType, "CHECKLIST_ACKNOWLEDGED");
  assert.equal(ackPlan.targetRoute, "portfolio");
  assert.ok(ackPlan.recipients.includes("owner_1"));
  assert.ok(ackPlan.recipients.includes("mgr_1"));
  assert.ok(!ackPlan.recipients.includes("tenant_1"));

  const disputedPlan = buildChecklistTransitionDispatch(
    "cl_1",
    { ...baseDoc, status: "PENDING_TENANT_REVIEW" },
    { ...baseDoc, status: "DISPUTED", tenantResponseNote: "Kitchen tap leaks" }
  );
  assert.equal(disputedPlan.eventType, "CHECKLIST_DISPUTED");
  assert.ok(disputedPlan.body.includes("Kitchen tap leaks"));
});

test("buildSettlementTransitionDispatch formats KES summary and routes tenant vs landlord targets", () => {
  const baseDoc = {
    leaseId: "lease_1",
    propertyName: "Milimani Court",
    unitId: "unit_b2",
    unitNumber: "B2",
    ownerUid: "owner_1",
    managerUids: [],
    authorizedUids: ["owner_1"],
    tenantUid: "tenant_1",
    tenantName: "Brian Ochieng",
    depositHeldCents: 2800000,
    creditAppliedCents: 0,
    arrearsIncludedCents: 0,
    deductions: [{ amountCents: 300000 }],
    deductionsTotalCents: 300000,
    netRefundCents: 2500000,
    balanceOwedCents: 0,
    createdByUid: "owner_1",
    updatedAt: { toMillis: () => 1763800100000 },
  };

  assert.equal(buildSettlementTransitionDispatch("ds_1", null, { ...baseDoc, status: "DRAFT" }), null);

  const issuedPlan = buildSettlementTransitionDispatch("ds_1", null, { ...baseDoc, status: "ISSUED" });
  assert.equal(issuedPlan.eventType, "SETTLEMENT_ISSUED");
  assert.equal(issuedPlan.targetRoute, "tenant_home");
  assert.deepEqual(issuedPlan.recipients, ["tenant_1"]);
  assert.ok(issuedPlan.body.includes(formatKesWhole(2500000)));

  const disputedPlan = buildSettlementTransitionDispatch(
    "ds_1",
    { ...baseDoc, status: "ISSUED" },
    { ...baseDoc, status: "DISPUTED", tenantResponseNote: "Paint wear was pre-existing" }
  );
  assert.equal(disputedPlan.eventType, "SETTLEMENT_DISPUTED");
  assert.equal(disputedPlan.targetRoute, "portfolio");
  assert.ok(disputedPlan.body.includes("Paint wear was pre-existing"));

  const approvedPlan = buildSettlementTransitionDispatch(
    "ds_1",
    { ...baseDoc, status: "ISSUED" },
    { ...baseDoc, status: "APPROVED" }
  );
  assert.equal(approvedPlan.eventType, "SETTLEMENT_APPROVED");
  assert.equal(approvedPlan.targetRoute, "portfolio");

  const settledPlan = buildSettlementTransitionDispatch(
    "ds_1",
    { ...baseDoc, status: "APPROVED" },
    { ...baseDoc, status: "SETTLED", refundMethod: "MPESA", refundReferenceCode: "UBL8X12M90", settledByUid: "owner_1" }
  );
  assert.equal(settledPlan.eventType, "SETTLEMENT_SETTLED");
  assert.equal(settledPlan.targetRoute, "tenant_home");
  assert.ok(settledPlan.body.includes("UBL8X12M90"));
});
