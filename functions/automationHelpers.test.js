const { describe, it } = require("node:test");
const assert = require("node:assert/strict");
const {
  DAY_MS,
  nextBillingPeriod,
  computeCycleBillingDateMs,
  computeNextBillingEpochMs,
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
} = require("./automationHelpers");

describe("automationHelpers escalation & late fee math", () => {
  it("computes NONE, PERCENT, and FIXED_KES escalations accurately", () => {
    assert.equal(
      computeEscalatedRentCents({ rentCents: 2500000, escalationType: "NONE" }),
      2500000
    );
    assert.equal(
      computeEscalatedRentCents({
        rentCents: 2500000,
        escalationType: "PERCENT",
        escalationPercent: 7.5,
      }),
      2687500
    );
    assert.equal(
      computeEscalatedRentCents({
        rentCents: 2500000,
        escalationType: "FIXED_KES",
        escalationFixedCents: 300000,
      }),
      2800000
    );
  });

  it("evaluates renewal lead window and skips already processed, pending offers, or active move-out notices", () => {
    const now = 1_700_000_000_000;
    const baseLease = {
      status: "ACTIVE",
      renewalMode: "OFFER",
      renewalLeadDays: 30,
      renewalDurationMonths: 12,
      endDateEpochMs: now + 10 * DAY_MS,
      rentCents: 3000000,
      escalationType: "PERCENT",
      escalationPercent: 5,
    };

    const candidate = evaluateLeaseRenewalCandidate(baseLease, now);
    assert.ok(candidate);
    assert.equal(candidate.actionType, "SEND_OFFER");
    assert.equal(candidate.projectedRentCents, 3150000);
    assert.equal(
      candidate.projectedEndDateEpochMs,
      computeExtendedEndDateMs(baseLease.endDateEpochMs, 12, now)
    );

    // Already processed for this end date -> skipped
    assert.equal(
      evaluateLeaseRenewalCandidate(
        { ...baseLease, lastRenewalProcessedForEndMs: baseLease.endDateEpochMs },
        now
      ),
      null
    );

    // Active move-out notice (SUBMITTED or ACKNOWLEDGED) -> skipped
    assert.equal(
      evaluateLeaseRenewalCandidate(
        { ...baseLease, moveOutNoticeStatus: "SUBMITTED" },
        now
      ),
      null
    );
    assert.equal(
      evaluateLeaseRenewalCandidate(
        { ...baseLease, moveOutNoticeStatus: "ACKNOWLEDGED" },
        now
      ),
      null
    );

    // DECLINED or WITHDRAWN move-out notice -> still eligible for renewal
    assert.ok(
      evaluateLeaseRenewalCandidate(
        { ...baseLease, moveOutNoticeStatus: "DECLINED" },
        now
      )
    );

    // Outside lead window -> skipped
    assert.equal(
      evaluateLeaseRenewalCandidate(
        { ...baseLease, endDateEpochMs: now + 60 * DAY_MS },
        now
      ),
      null
    );

    // AUTO_RENEW mode
    const autoCandidate = evaluateLeaseRenewalCandidate(
      { ...baseLease, renewalMode: "AUTO_RENEW" },
      now
    );
    assert.equal(autoCandidate.actionType, "AUTO_RENEW");
  });

  it("computes mid-month proration for ACKNOWLEDGED move-out notices and skips post-vacate months", () => {
    const moveOutMidMonth = Date.UTC(2026, 10, 15, 9, 0, 0); // 15 Nov 2026 (30-day month) -> 15/30 = 50%
    const lease = {
      rentCents: 3000000,
      moveOutNoticeStatus: "ACKNOWLEDGED",
      requestedMoveOutEpochMs: moveOutMidMonth,
    };

    const novResult = computeBillingPeriodRentCents(lease, "2026-11");
    assert.equal(novResult.prorated, true);
    assert.equal(novResult.occupiedDays, 15);
    assert.equal(novResult.daysInMonth, 30);
    assert.equal(novResult.rentCents, 1500000);
    assert.ok(novResult.prorationNote.includes("15/30 days"));

    // Month after move-out -> 0 rent
    const decResult = computeBillingPeriodRentCents(lease, "2026-12");
    assert.equal(decResult.rentCents, 0);
    assert.equal(decResult.occupiedDays, 0);

    // Non-acknowledged notice -> full rent
    const pendingLease = { ...lease, moveOutNoticeStatus: "SUBMITTED" };
    const fullResult = computeBillingPeriodRentCents(pendingLease, "2026-11");
    assert.equal(fullResult.rentCents, 3000000);
    assert.equal(fullResult.prorated, false);
  });

  it("evaluates automated monthly billing candidates within lead window, applies lease credit, and deduplicates", () => {
    // Due day 5 of 2026-11 -> dueDate is Nov 5 23:59 Nairobi (UTC+3). Lead 5 days -> window opens Oct 31 00:00 Nairobi.
    const nowWithinWindow = Date.UTC(2026, 10, 2, 5, 0, 0); // Nov 2 08:00 EAT
    const nowTooEarly = Date.UTC(2026, 9, 25, 5, 0, 0); // Oct 25 08:00 EAT
    const lease = {
      id: "lease_bill_1",
      status: "ACTIVE",
      autoBillingEnabled: true,
      billingLeadDays: 5,
      dueDayOfMonth: 5,
      gracePeriodDays: 5,
      rentCents: 3000000,
      creditBalanceCents: 500000,
      moveOutNoticeStatus: "ACKNOWLEDGED",
      requestedMoveOutEpochMs: Date.UTC(2026, 10, 15, 9, 0, 0),
    };

    assert.equal(evaluateAutoBillingCandidate(lease, [], "2026-11", nowTooEarly), null);

    const candidate = evaluateAutoBillingCandidate(
      lease,
      [{ id: "lease_bill_1_2026-10", leaseId: "lease_bill_1", billingPeriod: "2026-10", status: "PARTIALLY_PAID", outstandingCents: 200000 }],
      "2026-11",
      nowWithinWindow
    );
    assert.ok(candidate);
    assert.equal(candidate.deterministicId, "lease_bill_1_2026-11");
    assert.equal(candidate.rentCents, 1500000); // prorated 15/30
    assert.equal(candidate.previousBalanceCents, 200000);
    assert.equal(candidate.totalDueCents, 1700000);
    assert.equal(candidate.creditToApplyCents, 500000);
    assert.equal(candidate.outstandingCents, 1200000);
    assert.equal(candidate.prorated, true);

    // Already billed for 2026-11 -> null
    assert.equal(
      evaluateAutoBillingCandidate(
        lease,
        [{ id: "lease_bill_1_2026-11", leaseId: "lease_bill_1", billingPeriod: "2026-11", status: "UNPAID", outstandingCents: 1200000 }],
        "2026-11",
        nowWithinWindow
      ),
      null
    );
  });

  it("evaluates overdue late fees only after grace period and never double-charges", () => {
    const now = 1_700_000_000_000;
    const dueMs = now - 6 * DAY_MS;
    const lease = {
      gracePeriodDays: 5,
      lateFeePercent: 5,
      lateFeeEnabled: true,
    };
    const invoice = {
      status: "UNPAID",
      outstandingCents: 2000000,
      totalDueCents: 2000000,
      dueDateEpochMs: dueMs,
    };

    const candidate = evaluateOverdueLateFeeCandidate(invoice, lease, now);
    assert.ok(candidate);
    assert.equal(candidate.lateFeeCents, 100000);
    assert.equal(candidate.newTotalDueCents, 2100000);
    assert.equal(candidate.newOutstandingCents, 2100000);
    assert.equal(candidate.gracePeriodEndsEpochMs, computeGracePeriodEndsMs(dueMs, 5));

    // Within grace period -> null
    assert.equal(
      evaluateOverdueLateFeeCandidate(
        { ...invoice, dueDateEpochMs: now - 3 * DAY_MS },
        lease,
        now
      ),
      null
    );

    // Already assessed or waived -> null
    assert.equal(
      evaluateOverdueLateFeeCandidate(
        { ...invoice, lateFeeAppliedAtEpochMs: now - 1000, lateFeeCents: 100000 },
        lease,
        now
      ),
      null
    );
    assert.equal(
      evaluateOverdueLateFeeCandidate(
        { ...invoice, lateFeeWaived: true },
        lease,
        now
      ),
      null
    );
  });

  it("builds lease renewal and move-out notice FCM dispatch plans", () => {
    const lease = {
      propertyName: "Kileleshwa Heights",
      unitNumber: "B4",
      unitId: "u1",
      ownerUid: "owner_1",
      managerUids: ["mgr_1"],
      authorizedUids: ["owner_1", "mgr_1"],
      tenantUid: "ten_1",
      tenantName: "Wanjiku Mwangi",
      rentCents: 4500000,
      offeredRentCents: 4725000,
      renewalOfferStatus: "PENDING",
      renewalOfferedAtEpochMs: 123456,
    };

    const offerPlan = buildLeaseRenewalDispatch("lease_1", { renewalOfferStatus: "NONE" }, lease);
    assert.equal(offerPlan.eventType, "LEASE_RENEWAL_OFFER");
    assert.deepEqual(offerPlan.recipients, ["ten_1"]);

    const acceptPlan = buildLeaseRenewalDispatch(
      "lease_1",
      lease,
      { ...lease, renewalOfferStatus: "ACCEPTED", renewalRespondedAtEpochMs: 223456 }
    );
    assert.equal(acceptPlan.eventType, "LEASE_RENEWAL_RESPONSE");
    assert.ok(acceptPlan.recipients.includes("owner_1"));
    assert.ok(acceptPlan.recipients.includes("mgr_1"));

    const autoPlan = buildLeaseRenewalDispatch(
      "lease_1",
      { ...lease, renewalOfferStatus: "NONE" },
      { ...lease, renewalOfferStatus: "AUTO_RENEWED", renewalRespondedAtEpochMs: 323456 }
    );
    assert.equal(autoPlan.eventType, "LEASE_AUTO_RENEWED");
    assert.deepEqual(autoPlan.recipients, ["ten_1"]);

    const submitMoveOutPlan = buildMoveOutNoticeDispatch(
      "lease_1",
      { ...lease, moveOutNoticeStatus: "NONE" },
      {
        ...lease,
        moveOutNoticeStatus: "SUBMITTED",
        requestedMoveOutEpochMs: Date.UTC(2026, 11, 15, 9, 0, 0),
        moveOutNoticeSubmittedAtMs: 400000,
        moveOutNoticeReason: "Relocating for work",
      }
    );
    assert.equal(submitMoveOutPlan.eventType, "MOVE_OUT_NOTICE_SUBMITTED");
    assert.equal(submitMoveOutPlan.targetRoute, "portfolio");
    assert.equal(submitMoveOutPlan.targetTab, "3");
    assert.ok(submitMoveOutPlan.recipients.includes("owner_1"));

    const ackMoveOutPlan = buildMoveOutNoticeDispatch(
      "lease_1",
      { ...lease, moveOutNoticeStatus: "SUBMITTED", requestedMoveOutEpochMs: Date.UTC(2026, 11, 15, 9, 0, 0) },
      {
        ...lease,
        moveOutNoticeStatus: "ACKNOWLEDGED",
        requestedMoveOutEpochMs: Date.UTC(2026, 11, 15, 9, 0, 0),
        moveOutNoticeReviewedAtMs: 500000,
        moveOutNoticeResponseNote: "Inspection scheduled for Dec 14.",
      }
    );
    assert.equal(ackMoveOutPlan.eventType, "MOVE_OUT_NOTICE_REVIEWED");
    assert.deepEqual(ackMoveOutPlan.recipients, ["ten_1"]);
  });

  it("handles recurring schedule DRAFT_FOR_REVIEW vs AUTO_ISSUE, lease-referenced grace period, and draft issuance", () => {
    const nowWithinWindow = Date.UTC(2026, 10, 2, 5, 0, 0); // Nov 2 08:00 EAT
    const lease = {
      id: "lease_rec_1",
      status: "ACTIVE",
      autoBillingEnabled: true,
      billingLeadDays: 5,
      dueDayOfMonth: 5,
      gracePeriodDays: 7, // Grace period lives ONLY on lease
      rentCents: 3000000,
      creditBalanceCents: 400000,
    };
    const draftSchedule = {
      id: "lease_rec_1",
      leaseId: "lease_rec_1",
      amountCents: 3200000,
      recurringExtraCents: 150000,
      recurringExtraNote: "Garbage & water",
      billingDayOfMonth: 5,
      leadDays: 5,
      scheduleStatus: "ACTIVE",
      issueMode: "DRAFT_FOR_REVIEW",
    };

    // Existing DRAFT invoice from Oct must NOT be counted as previous balance arrears
    const priorDraft = {
      id: "lease_rec_1_2026-10",
      leaseId: "lease_rec_1",
      billingPeriod: "2026-10",
      status: "DRAFT",
      outstandingCents: 3000000,
    };

    const draftCandidate = evaluateAutoBillingCandidate(lease, [priorDraft], "2026-11", nowWithinWindow, draftSchedule);
    assert.ok(draftCandidate);
    assert.equal(draftCandidate.status, "DRAFT");
    assert.equal(draftCandidate.scheduleId, "lease_rec_1");
    assert.equal(draftCandidate.rentCents, 3200000);
    assert.equal(draftCandidate.extraChargesCents, 150000);
    assert.equal(draftCandidate.extraChargesNote, "Garbage & water");
    assert.equal(draftCandidate.previousBalanceCents, 0);
    assert.equal(draftCandidate.creditToApplyCents, 0); // Credit deferred until draft is issued
    assert.equal(draftCandidate.issuedAtEpochMs, null);
    assert.equal(draftCandidate.billingDateEpochMs, computeCycleBillingDateMs("2026-11", 5, 5));
    assert.equal(
      draftCandidate.gracePeriodEndsEpochMs,
      computeGracePeriodEndsMs(draftCandidate.dueDateEpochMs, 7)
    );

    // Paused schedule produces null even if lease.autoBillingEnabled is true
    assert.equal(
      evaluateAutoBillingCandidate(lease, [], "2026-11", nowWithinWindow, {
        ...draftSchedule,
        scheduleStatus: "PAUSED",
      }),
      null
    );

    // Late fees skip DRAFT invoices
    assert.equal(
      evaluateOverdueLateFeeCandidate(
        {
          status: "DRAFT",
          outstandingCents: 3350000,
          totalDueCents: 3350000,
          dueDateEpochMs: nowWithinWindow - 10 * DAY_MS,
        },
        lease,
        nowWithinWindow
      ),
      null
    );

    // Issuing a draft applies live lease credit, adds reviewed extra charges, and derives grace from lease
    const issuedEval = evaluateDraftIssuance(
      {
        rentCents: draftCandidate.rentCents,
        previousBalanceCents: 0,
        lateFeeCents: 0,
        paidCents: 0,
        dueDateEpochMs: draftCandidate.dueDateEpochMs,
      },
      lease,
      200000,
      nowWithinWindow
    );
    assert.equal(issuedEval.totalDueCents, 3400000);
    assert.equal(issuedEval.creditToApplyCents, 400000);
    assert.equal(issuedEval.newOutstandingCents, 3000000);
    assert.equal(issuedEval.newStatus, "PARTIALLY_PAID");
    assert.equal(
      issuedEval.gracePeriodEndsEpochMs,
      computeGracePeriodEndsMs(draftCandidate.dueDateEpochMs, 7)
    );
    assert.equal(nextBillingPeriod("2026-12"), "2027-01");
    assert.equal(
      computeNextBillingEpochMs("2026-11", 5, 5),
      computeCycleBillingDateMs("2026-12", 5, 5)
    );
  });
});
