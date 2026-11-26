const { describe, it } = require("node:test");
const assert = require("node:assert/strict");
const {
  DAY_MS,
  computeEscalatedRentCents,
  computeExtendedEndDateMs,
  computeGracePeriodEndsMs,
  computeLateFeeCents,
  evaluateLeaseRenewalCandidate,
  evaluateOverdueLateFeeCandidate,
  buildLeaseRenewalDispatch,
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

  it("evaluates renewal lead window and skips already processed or pending offers", () => {
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

  it("builds lease renewal FCM dispatch plans for offer, accept, decline, and auto-renew", () => {
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
  });
});
