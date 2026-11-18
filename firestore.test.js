const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const LANDLORD_UID = "landlord_001";
const MANAGER_UID = "manager_002";
const TENANT_UID = "tenant_003";
const OTHER_TENANT_UID = "tenant_999";
const STAFF_UID = "staff_004";
const ADMIN_UID = "admin_000";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 5000) {
  return new Date(Date.now() - msOffset);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated visitor: can query published units/properties only, denied on private collections", async () => {
  const now = pastDate();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("properties").doc("prop_pub").set({
      orgId: "org_1",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      name: "Kilimani Heights",
      category: "APARTMENTS",
      city: "Nairobi",
      neighborhood: "Kilimani",
      addressLine: "Argwings Kodhek Rd",
      description: "Modern apartments",
      imageRefs: [],
      archived: false,
      hasPublishedUnits: true,
      totalUnitsCount: 1,
      occupiedUnitsCount: 0,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("properties").doc("prop_priv").set({
      orgId: "org_1",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      name: "Private Villa",
      category: "RESIDENTIAL_HOUSES",
      city: "Mombasa",
      neighborhood: "Nyali",
      addressLine: "Links Rd",
      description: "Private",
      imageRefs: [],
      archived: false,
      hasPublishedUnits: false,
      totalUnitsCount: 1,
      occupiedUnitsCount: 0,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("units").doc("unit_pub").set({
      propertyId: "prop_pub",
      propertyName: "Kilimani Heights",
      propertyCity: "Nairobi",
      propertyCategory: "APARTMENTS",
      orgId: "org_1",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      unitNumber: "A-101",
      unitType: "ONE_BEDROOM",
      rentCents: 3500000,
      depositCents: 3500000,
      status: "AVAILABLE",
      amenities: ["Wi-Fi", "Parking"],
      description: "Spacious 1BR",
      imageRefs: [],
      published: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(
    unauthDb.collection("properties").where("hasPublishedUnits", "==", true).where("archived", "==", false).get()
  );
  await assertSucceeds(unauthDb.collection("units").where("published", "==", true).get());
  await assertFails(unauthDb.collection("properties").doc("prop_priv").get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("leases").get());
  await assertFails(unauthDb.collection("payments").get());
});

test("User profile: owner CRUD succeeds, privilege escalation and cross-user PII read fail", async () => {
  const now = pastDate();
  const landlordDb = testEnv.authenticatedContext(LANDLORD_UID, { email: "owner@example.com", email_verified: true }).firestore();

  await assertSucceeds(
    landlordDb.collection("users").doc(LANDLORD_UID).set({
      userId: LANDLORD_UID,
      fullName: "Wanjiku Kamau",
      email: "owner@example.com",
      phoneNumber: "+254712345678",
      roles: ["LANDLORD"],
      activeRole: "LANDLORD",
      notifyRentDue: true,
      notifyMaintenance: true,
      notifyAnnouncements: true,
      suspended: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject self-assigned ADMIN role
  await assertFails(
    landlordDb.collection("users").doc(LANDLORD_UID).update({
      roles: ["LANDLORD", "ADMIN"],
      activeRole: "ADMIN",
      updatedAt: pastDate(1000),
    })
  );

  // Reject shadow field
  await assertFails(
    landlordDb.collection("users").doc(LANDLORD_UID).update({
      fullName: "Wanjiku K.",
      ghostField: "hack",
      updatedAt: pastDate(1000),
    })
  );

  // Cross-user PII read/update/delete rejected
  const tenantDb = testEnv.authenticatedContext(TENANT_UID, { email: "tenant@example.com", email_verified: true }).firestore();
  await assertFails(tenantDb.collection("users").doc(LANDLORD_UID).get());
  await assertFails(tenantDb.collection("users").doc(LANDLORD_UID).update({ fullName: "Hijacked", updatedAt: pastDate(500) }));
  await assertFails(tenantDb.collection("users").doc(LANDLORD_UID).delete());
  await assertFails(tenantDb.collection("users").get());

  // Owner can update and delete their own User profile
  await assertSucceeds(
    landlordDb.collection("users").doc(LANDLORD_UID).update({
      fullName: "Wanjiku Kamau-Njoroge",
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(landlordDb.collection("users").doc(LANDLORD_UID).delete());
});

test("Property CRUD isolation: owner can read/update/delete, unrelated user is denied", async () => {
  const now = pastDate(10000);
  const landlordDb = testEnv.authenticatedContext(LANDLORD_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_TENANT_UID).firestore();

  await assertSucceeds(
    landlordDb.collection("organizations").doc("org_iso").set({
      name: "Kileleshwa Holdings",
      ownerUid: LANDLORD_UID,
      country: "Kenya",
      currency: "KES",
      defaultDueDay: 5,
      memberUids: [LANDLORD_UID],
      suspended: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    landlordDb.collection("properties").doc("prop_iso").set({
      orgId: "org_iso",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      name: "Lavington Villas",
      category: "RESIDENTIAL_HOUSES",
      city: "Nairobi",
      neighborhood: "Lavington",
      addressLine: "James Gichuru Rd",
      description: "Townhouses",
      imageRefs: [],
      archived: false,
      hasPublishedUnits: false,
      totalUnitsCount: 0,
      occupiedUnitsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Unrelated user cannot get, update, or delete private property
  await assertFails(otherDb.collection("properties").doc("prop_iso").get());
  await assertFails(otherDb.collection("properties").doc("prop_iso").update({ name: "Hijacked", updatedAt: pastDate(500) }));
  await assertFails(otherDb.collection("properties").doc("prop_iso").delete());

  // Owner can read, update, and delete their own property
  await assertSucceeds(landlordDb.collection("properties").where("authorizedUids", "array-contains", LANDLORD_UID).get());
  await assertSucceeds(landlordDb.collection("properties").doc("prop_iso").update({ name: "Lavington Green Villas", updatedAt: pastDate(500) }));
  await assertSucceeds(landlordDb.collection("properties").doc("prop_iso").delete());
});

test("Full RentFlow lifecycle: org -> property -> unit -> application -> lease -> invoice -> payment -> maintenance -> isolation", async () => {
  const now = pastDate(10000);
  const landlordDb = testEnv.authenticatedContext(LANDLORD_UID).firestore();
  const managerDb = testEnv.authenticatedContext(MANAGER_UID).firestore();
  const tenantDb = testEnv.authenticatedContext(TENANT_UID).firestore();
  const otherTenantDb = testEnv.authenticatedContext(OTHER_TENANT_UID).firestore();
  const staffDb = testEnv.authenticatedContext(STAFF_UID).firestore();

  // 1. Organization
  await assertSucceeds(
    landlordDb.collection("organizations").doc("org_1").set({
      name: "Savannah Homes Ltd",
      ownerUid: LANDLORD_UID,
      country: "Kenya",
      currency: "KES",
      defaultDueDay: 5,
      memberUids: [LANDLORD_UID, MANAGER_UID],
      suspended: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // 2. Property
  await assertSucceeds(
    landlordDb.collection("properties").doc("prop_1").set({
      orgId: "org_1",
      ownerUid: LANDLORD_UID,
      managerUids: [MANAGER_UID],
      authorizedUids: [LANDLORD_UID, MANAGER_UID],
      name: "Milimani Court",
      category: "APARTMENTS",
      city: "Kisumu",
      neighborhood: "Milimani",
      addressLine: "Tom Mboya Dr",
      description: "Quiet residential court",
      imageRefs: [],
      archived: false,
      hasPublishedUnits: true,
      totalUnitsCount: 1,
      occupiedUnitsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // 3. Unit
  await assertSucceeds(
    managerDb.collection("units").doc("unit_1").set({
      propertyId: "prop_1",
      propertyName: "Milimani Court",
      propertyCity: "Kisumu",
      propertyCategory: "APARTMENTS",
      orgId: "org_1",
      ownerUid: LANDLORD_UID,
      managerUids: [MANAGER_UID],
      authorizedUids: [LANDLORD_UID, MANAGER_UID],
      unitNumber: "B2",
      unitType: "TWO_BEDROOM",
      rentCents: 2800000,
      depositCents: 2800000,
      status: "AVAILABLE",
      amenities: ["Water Tank", "Security"],
      description: "2BR unit",
      imageRefs: [],
      published: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // 4. Rental Application (Tenant creates SUBMITTED; cannot self-approve)
  await assertSucceeds(
    tenantDb.collection("applications").doc("app_1").set({
      unitId: "unit_1",
      unitNumber: "B2",
      propertyId: "prop_1",
      propertyName: "Milimani Court",
      orgId: "org_1",
      ownerUid: LANDLORD_UID,
      managerUids: [MANAGER_UID],
      authorizedUids: [LANDLORD_UID, MANAGER_UID],
      applicantUid: TENANT_UID,
      applicantName: "Brian Ochieng",
      applicantPhone: "+254722111222",
      desiredMoveInDate: "2026-12-01",
      status: "SUBMITTED",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    tenantDb.collection("applications").doc("app_1").update({
      status: "APPROVED",
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    landlordDb.collection("applications").doc("app_1").update({
      status: "APPROVED",
      reviewerNotes: "Approved after ID check",
      updatedAt: pastDate(1000),
    })
  );

  // 5. Lease creation & Cross-tenant isolation
  const startMs = Date.now() - 86400000;
  const endMs = Date.now() + 365 * 86400000;
  await assertSucceeds(
    landlordDb.collection("leases").doc("lease_1").set({
      orgId: "org_1",
      propertyId: "prop_1",
      propertyName: "Milimani Court",
      unitId: "unit_1",
      unitNumber: "B2",
      ownerUid: LANDLORD_UID,
      managerUids: [MANAGER_UID],
      authorizedUids: [LANDLORD_UID, MANAGER_UID],
      tenantUid: TENANT_UID,
      tenantName: "Brian Ochieng",
      tenantPhone: "+254722111222",
      rentCents: 2800000,
      depositCents: 2800000,
      startDateEpochMs: startMs,
      endDateEpochMs: endMs,
      dueDayOfMonth: 5,
      status: "ACTIVE",
      creditBalanceCents: 0,
      termsSummary: "Standard 12-month tenancy",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(tenantDb.collection("leases").where("tenantUid", "==", TENANT_UID).get());
  await assertFails(otherTenantDb.collection("leases").doc("lease_1").get());
  await assertFails(otherTenantDb.collection("leases").get());

  // 6. Monthly Invoice
  await assertSucceeds(
    landlordDb.collection("invoices").doc("lease_1_2026-11").set({
      invoiceNumber: "INV-202611-001",
      leaseId: "lease_1",
      orgId: "org_1",
      propertyId: "prop_1",
      propertyName: "Milimani Court",
      unitId: "unit_1",
      unitNumber: "B2",
      ownerUid: LANDLORD_UID,
      managerUids: [MANAGER_UID],
      authorizedUids: [LANDLORD_UID, MANAGER_UID],
      tenantUid: TENANT_UID,
      tenantName: "Brian Ochieng",
      billingPeriod: "2026-11",
      rentCents: 2800000,
      extraChargesCents: 50000,
      extraChargesNote: "Water",
      previousBalanceCents: 0,
      totalDueCents: 2850000,
      paidCents: 0,
      outstandingCents: 2850000,
      dueDateEpochMs: endMs,
      status: "UNPAID",
      createdAt: now,
      updatedAt: now,
    })
  );

  // 7. Payment: tenant cannot self-verify, must submit PENDING_VERIFICATION
  await assertFails(
    tenantDb.collection("payments").doc("pay_bad").set({
      referenceCode: "UBK99999",
      leaseId: "lease_1",
      orgId: "org_1",
      propertyId: "prop_1",
      propertyName: "Milimani Court",
      unitId: "unit_1",
      unitNumber: "B2",
      ownerUid: LANDLORD_UID,
      managerUids: [MANAGER_UID],
      authorizedUids: [LANDLORD_UID, MANAGER_UID],
      tenantUid: TENANT_UID,
      tenantName: "Brian Ochieng",
      amountCents: 2850000,
      method: "MPESA",
      simulatedCheckout: true,
      paymentDateEpochMs: startMs,
      status: "VERIFIED",
      recordedByUid: TENANT_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    tenantDb.collection("payments").doc("pay_1").set({
      referenceCode: "SIM-UBK7721A",
      leaseId: "lease_1",
      invoiceId: "lease_1_2026-11",
      orgId: "org_1",
      propertyId: "prop_1",
      propertyName: "Milimani Court",
      unitId: "unit_1",
      unitNumber: "B2",
      ownerUid: LANDLORD_UID,
      managerUids: [MANAGER_UID],
      authorizedUids: [LANDLORD_UID, MANAGER_UID],
      tenantUid: TENANT_UID,
      tenantName: "Brian Ochieng",
      amountCents: 1500000,
      method: "MPESA",
      simulatedCheckout: true,
      paymentDateEpochMs: startMs,
      status: "PENDING_VERIFICATION",
      recordedByUid: TENANT_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Manager verifies payment
  await assertSucceeds(
    managerDb.collection("payments").doc("pay_1").update({
      status: "VERIFIED",
      verifiedByUid: MANAGER_UID,
      receiptNumber: "RCP-2026-0001",
      allocationsSummary: "INV-202611-001: KES 15,000.00",
      updatedAt: pastDate(1000),
    })
  );

  // 8. Maintenance request & staff assignment
  await assertSucceeds(
    tenantDb.collection("maintenance_requests").doc("maint_1").set({
      orgId: "org_1",
      propertyId: "prop_1",
      propertyName: "Milimani Court",
      unitId: "unit_1",
      unitNumber: "B2",
      ownerUid: LANDLORD_UID,
      managerUids: [MANAGER_UID],
      authorizedUids: [LANDLORD_UID, MANAGER_UID],
      tenantUid: TENANT_UID,
      tenantName: "Brian Ochieng",
      title: "Kitchen sink leak",
      description: "Pipe dripping under kitchen sink",
      category: "PLUMBING",
      priority: "HIGH",
      status: "SUBMITTED",
      repairCostCents: 0,
      imageRefs: [],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    managerDb.collection("maintenance_requests").doc("maint_1").update({
      assignedStaffUid: STAFF_UID,
      assignedStaffName: "John Plumber",
      status: "ASSIGNED",
      updatedAt: pastDate(1000),
    })
  );

  await assertSucceeds(
    staffDb.collection("maintenance_requests").doc("maint_1").update({
      status: "COMPLETED",
      repairCostCents: 250000,
      latestUpdateNote: "Replaced washer and elbow joint",
      updatedAt: pastDate(500),
    })
  );

  // 9. Audit Log immutability
  await assertSucceeds(
    landlordDb.collection("audit_logs").doc("log_1").set({
      orgId: "org_1",
      actorUid: LANDLORD_UID,
      actorName: "Wanjiku Kamau",
      action: "VERIFY_PAYMENT",
      entityType: "Payment",
      entityId: "pay_1",
      details: "Verified KES 15,000",
      createdAt: now,
    })
  );
  await assertFails(
    landlordDb.collection("audit_logs").doc("log_1").update({ details: "Tampered" })
  );
  await assertFails(landlordDb.collection("audit_logs").doc("log_1").delete());
});

test("Bootstrapped verified admin has read/manage access; unverified email admin spoof fails", async () => {
  const now = pastDate();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(TENANT_UID).set({
      userId: TENANT_UID,
      fullName: "Brian Ochieng",
      email: "brian@example.com",
      roles: ["TENANT"],
      activeRole: "TENANT",
      notifyRentDue: true,
      notifyMaintenance: true,
      notifyAnnouncements: true,
      suspended: false,
      createdAt: now,
      updatedAt: now,
    });
  });

  // Unverified email spoof must fail
  const spoofAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: "sternomolo2@gmail.com",
    email_verified: false,
  }).firestore();
  await assertFails(spoofAdminDb.collection("users").get());

  // Verified bootstrapped admin succeeds
  const realAdminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "sternomolo2@gmail.com",
    email_verified: true,
  }).firestore();
  await assertSucceeds(realAdminDb.collection("users").get());
});

test("Cross-organization isolation: landlords and managers are restricted to their own organization documents", async () => {
  const now = pastDate(10000);
  const ORG_A_LANDLORD = "landlord_org_a";
  const ORG_A_MANAGER = "manager_org_a";
  const ORG_B_LANDLORD = "landlord_org_b";
  const ORG_B_MANAGER = "manager_org_b";
  const ORG_B_TENANT = "tenant_org_b";

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("organizations").doc("org_a").set({
      name: "Nairobi West Holdings",
      ownerUid: ORG_A_LANDLORD,
      country: "Kenya",
      currency: "KES",
      defaultDueDay: 5,
      memberUids: [ORG_A_LANDLORD, ORG_A_MANAGER],
      suspended: false,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("organizations").doc("org_b").set({
      name: "Mombasa Coast Realty",
      ownerUid: ORG_B_LANDLORD,
      country: "Kenya",
      currency: "KES",
      defaultDueDay: 5,
      memberUids: [ORG_B_LANDLORD, ORG_B_MANAGER],
      suspended: false,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("properties").doc("prop_a").set({
      orgId: "org_a",
      ownerUid: ORG_A_LANDLORD,
      managerUids: [ORG_A_MANAGER],
      authorizedUids: [ORG_A_LANDLORD, ORG_A_MANAGER],
      name: "Westlands Suites",
      category: "APARTMENTS",
      city: "Nairobi",
      neighborhood: "Westlands",
      addressLine: "Ring Rd",
      description: "Org A private property",
      imageRefs: [],
      archived: false,
      hasPublishedUnits: false,
      totalUnitsCount: 0,
      occupiedUnitsCount: 0,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("properties").doc("prop_b").set({
      orgId: "org_b",
      ownerUid: ORG_B_LANDLORD,
      managerUids: [ORG_B_MANAGER],
      authorizedUids: [ORG_B_LANDLORD, ORG_B_MANAGER],
      name: "Nyali Breeze Villas",
      category: "RESIDENTIAL_HOUSES",
      city: "Mombasa",
      neighborhood: "Nyali",
      addressLine: "Beach Rd",
      description: "Org B private property",
      imageRefs: [],
      archived: false,
      hasPublishedUnits: false,
      totalUnitsCount: 1,
      occupiedUnitsCount: 1,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("units").doc("unit_b1").set({
      propertyId: "prop_b",
      propertyName: "Nyali Breeze Villas",
      propertyCity: "Mombasa",
      propertyCategory: "RESIDENTIAL_HOUSES",
      orgId: "org_b",
      ownerUid: ORG_B_LANDLORD,
      managerUids: [ORG_B_MANAGER],
      authorizedUids: [ORG_B_LANDLORD, ORG_B_MANAGER],
      unitNumber: "V1",
      unitType: "THREE_BEDROOM",
      rentCents: 6500000,
      depositCents: 6500000,
      status: "OCCUPIED",
      amenities: ["Pool"],
      description: "Private villa",
      imageRefs: [],
      published: false,
      currentTenantUid: ORG_B_TENANT,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("leases").doc("lease_b1").set({
      orgId: "org_b",
      propertyId: "prop_b",
      propertyName: "Nyali Breeze Villas",
      unitId: "unit_b1",
      unitNumber: "V1",
      ownerUid: ORG_B_LANDLORD,
      managerUids: [ORG_B_MANAGER],
      authorizedUids: [ORG_B_LANDLORD, ORG_B_MANAGER],
      tenantUid: ORG_B_TENANT,
      tenantName: "Hassan Ali",
      tenantPhone: "+254733000111",
      rentCents: 6500000,
      depositCents: 6500000,
      startDateEpochMs: Date.now() - 86400000,
      endDateEpochMs: Date.now() + 86400000 * 300,
      dueDayOfMonth: 5,
      status: "ACTIVE",
      creditBalanceCents: 0,
      termsSummary: "12-month lease",
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("expenses").doc("exp_b1").set({
      orgId: "org_b",
      propertyId: "prop_b",
      propertyName: "Nyali Breeze Villas",
      ownerUid: ORG_B_LANDLORD,
      managerUids: [ORG_B_MANAGER],
      authorizedUids: [ORG_B_LANDLORD, ORG_B_MANAGER],
      category: "SECURITY",
      title: "Gate guard patrol",
      description: "Monthly night watch",
      amountCents: 1800000,
      expenseDateEpochMs: Date.now() - 3600000,
      recordedByUid: ORG_B_MANAGER,
      createdAt: now,
      updatedAt: now,
    });
  });

  const orgALandlordDb = testEnv.authenticatedContext(ORG_A_LANDLORD).firestore();
  const orgAManagerDb = testEnv.authenticatedContext(ORG_A_MANAGER).firestore();
  const orgBLandlordDb = testEnv.authenticatedContext(ORG_B_LANDLORD).firestore();
  const orgBManagerDb = testEnv.authenticatedContext(ORG_B_MANAGER).firestore();

  // Org B members can access Org B records
  await assertSucceeds(orgBLandlordDb.collection("organizations").doc("org_b").get());
  await assertSucceeds(orgBManagerDb.collection("properties").doc("prop_b").get());
  await assertSucceeds(orgBManagerDb.collection("units").doc("unit_b1").get());
  await assertSucceeds(orgBManagerDb.collection("leases").doc("lease_b1").get());
  await assertSucceeds(orgBManagerDb.collection("expenses").doc("exp_b1").get());

  // Org A Landlord & Manager CANNOT read or list Org B's organization, properties, units, leases, or expenses
  await assertFails(orgALandlordDb.collection("organizations").doc("org_b").get());
  await assertFails(orgAManagerDb.collection("organizations").doc("org_b").get());
  await assertFails(orgALandlordDb.collection("properties").doc("prop_b").get());
  await assertFails(orgAManagerDb.collection("properties").doc("prop_b").get());
  await assertFails(orgALandlordDb.collection("units").doc("unit_b1").get());
  await assertFails(orgAManagerDb.collection("leases").doc("lease_b1").get());
  await assertFails(orgALandlordDb.collection("expenses").doc("exp_b1").get());
  await assertFails(orgALandlordDb.collection("expenses").where("orgId", "==", "org_b").get());
  await assertFails(orgAManagerDb.collection("leases").where("orgId", "==", "org_b").get());

  // Org A Landlord CANNOT spoof Org B's orgId when creating a unit, expense, or announcement under Org A property
  await assertFails(
    orgALandlordDb.collection("units").doc("spoof_unit").set({
      propertyId: "prop_a",
      propertyName: "Westlands Suites",
      propertyCity: "Nairobi",
      propertyCategory: "APARTMENTS",
      orgId: "org_b",
      ownerUid: ORG_A_LANDLORD,
      managerUids: [],
      authorizedUids: [ORG_A_LANDLORD],
      unitNumber: "S1",
      unitType: "BEDSITTER",
      rentCents: 1500000,
      depositCents: 1500000,
      status: "AVAILABLE",
      amenities: [],
      description: "Spoof",
      imageRefs: [],
      published: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    orgALandlordDb.collection("expenses").doc("spoof_exp").set({
      orgId: "org_b",
      propertyId: "prop_a",
      propertyName: "Westlands Suites",
      ownerUid: ORG_A_LANDLORD,
      managerUids: [],
      authorizedUids: [ORG_A_LANDLORD],
      category: "OTHER",
      title: "Spoof expense",
      description: "Cross-org injection attempt",
      amountCents: 50000,
      expenseDateEpochMs: Date.now() - 1000,
      recordedByUid: ORG_A_LANDLORD,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    orgALandlordDb.collection("announcements").doc("spoof_ann").set({
      orgId: "org_b",
      propertyId: "prop_a",
      propertyName: "Westlands Suites",
      ownerUid: ORG_A_LANDLORD,
      authorizedUids: [ORG_A_LANDLORD],
      targetUids: [],
      title: "Spoof notice",
      body: "Unauthorized cross-org notice",
      authorUid: ORG_A_LANDLORD,
      authorName: "Spoofer",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Instant revocation: removing Org B Manager from organizations/org_b.memberUids immediately blocks get/update
  await assertSucceeds(
    orgBLandlordDb.collection("organizations").doc("org_b").update({
      memberUids: [ORG_B_LANDLORD],
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(orgBManagerDb.collection("organizations").doc("org_b").get());
  await assertFails(orgBManagerDb.collection("properties").doc("prop_b").get());
  await assertFails(orgBManagerDb.collection("units").doc("unit_b1").get());
  await assertFails(orgBManagerDb.collection("leases").doc("lease_b1").get());
  await assertFails(orgBManagerDb.collection("expenses").doc("exp_b1").get());
  await assertFails(
    orgBManagerDb.collection("expenses").doc("exp_b1").update({
      title: "Tampered after revocation",
      updatedAt: pastDate(500),
    })
  );
});

test("Property extended metadata & dual-filter orgId+authorizedUids query scoping", async () => {
  const now = pastDate(10000);
  const landlordDb = testEnv.authenticatedContext(LANDLORD_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_TENANT_UID).firestore();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("organizations").doc("org_alpha").set({
      name: "Alpha Estates",
      ownerUid: LANDLORD_UID,
      country: "Kenya",
      currency: "KES",
      defaultDueDay: 5,
      memberUids: [LANDLORD_UID],
      suspended: false,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("organizations").doc("org_beta").set({
      name: "Beta Towers",
      ownerUid: LANDLORD_UID,
      country: "Kenya",
      currency: "KES",
      defaultDueDay: 5,
      memberUids: [LANDLORD_UID],
      suspended: false,
      createdAt: now,
      updatedAt: now,
    });
  });

  // Reject creating property referencing non-existent organization
  await assertFails(
    landlordDb.collection("properties").doc("prop_missing_org").set({
      orgId: "org_does_not_exist",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      name: "Orphan Court",
      category: "APARTMENTS",
      city: "Nairobi",
      county: "Nairobi",
      neighborhood: "Ruaka",
      addressLine: "Limuru Rd",
      description: "Should fail",
      imageRefs: [],
      archived: false,
      hasPublishedUnits: false,
      totalUnitsCount: 0,
      occupiedUnitsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject creating property with shadow field or overlong county (> 60 chars)
  await assertFails(
    landlordDb.collection("properties").doc("prop_bad_county").set({
      orgId: "org_alpha",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      name: "Bad County Court",
      category: "APARTMENTS",
      city: "Nakuru",
      county: "C".repeat(61),
      neighborhood: "Milimani",
      addressLine: "Main Rd",
      description: "Too long county",
      imageRefs: [],
      archived: false,
      hasPublishedUnits: false,
      totalUnitsCount: 0,
      occupiedUnitsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Create properties across two organizations with extended Kenyan metadata
  await assertSucceeds(
    landlordDb.collection("properties").doc("prop_alpha").set({
      orgId: "org_alpha",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      name: "Alpha Heights",
      category: "APARTMENTS",
      city: "Nairobi",
      county: "Nairobi",
      neighborhood: "Kilimani",
      addressLine: "Kindaruma Rd",
      description: "24/7 borehole & backup generator",
      waterMeterNumber: "NWSC-88210",
      electricityMeterNumber: "KPLC-1420998",
      notes: "Caretaker John on site daily",
      imageRefs: [],
      archived: false,
      hasPublishedUnits: false,
      totalUnitsCount: 0,
      occupiedUnitsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    landlordDb.collection("properties").doc("prop_beta").set({
      orgId: "org_beta",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      name: "Beta Arcade",
      category: "COMMERCIAL_BUILDINGS",
      city: "Eldoret",
      county: "Uasin Gishu",
      neighborhood: "CBD",
      addressLine: "Uganda Rd",
      description: "Ground floor retail",
      waterMeterNumber: "ELDOWAS-4412",
      electricityMeterNumber: "KPLC-991021",
      imageRefs: [],
      archived: false,
      hasPublishedUnits: false,
      totalUnitsCount: 0,
      occupiedUnitsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Update extended utility metadata cleanly
  await assertSucceeds(
    landlordDb.collection("properties").doc("prop_alpha").update({
      waterMeterNumber: "NWSC-88211",
      notes: "Solar water heating serviced",
      updatedAt: pastDate(500),
    })
  );

  // Dual-filter orgId + authorizedUids query returns only the active organization's property
  const alphaSnap = await assertSucceeds(
    landlordDb.collection("properties")
      .where("orgId", "==", "org_alpha")
      .where("authorizedUids", "array-contains", LANDLORD_UID)
      .get()
  );
  if (alphaSnap.size !== 1 || alphaSnap.docs[0].id !== "prop_alpha") {
    throw new Error(`Expected only prop_alpha, got ${alphaSnap.docs.map((d) => d.id).join(",")}`);
  }

  // Unrelated user attempting dual-filter query on org_alpha fails
  await assertFails(
    otherDb.collection("properties")
      .where("orgId", "==", "org_alpha")
      .where("authorizedUids", "array-contains", LANDLORD_UID)
      .get()
  );
});

test("RentalUnit CRUD scoped to parent property: create, property-scoped list, update, delete unoccupied vs occupied", async () => {
  const now = pastDate(10000);
  const landlordDb = testEnv.authenticatedContext(LANDLORD_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_TENANT_UID).firestore();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("organizations").doc("org_units").set({
      name: "Mombasa Bay Estates",
      ownerUid: LANDLORD_UID,
      country: "Kenya",
      currency: "KES",
      defaultDueDay: 5,
      memberUids: [LANDLORD_UID],
      suspended: false,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("properties").doc("prop_p1").set({
      orgId: "org_units",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      name: "Tudor Court",
      category: "APARTMENTS",
      city: "Mombasa",
      county: "Mombasa",
      neighborhood: "Tudor",
      addressLine: "Tom Mboya Ave",
      description: "Ocean breeze apartments",
      imageRefs: [],
      archived: false,
      hasPublishedUnits: false,
      totalUnitsCount: 0,
      occupiedUnitsCount: 0,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("properties").doc("prop_p2").set({
      orgId: "org_units",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      name: "Kizingo Suites",
      category: "APARTMENTS",
      city: "Mombasa",
      county: "Mombasa",
      neighborhood: "Kizingo",
      addressLine: "Mama Ngina Dr",
      description: "Residential suites",
      imageRefs: [],
      archived: false,
      hasPublishedUnits: false,
      totalUnitsCount: 0,
      occupiedUnitsCount: 0,
      createdAt: now,
      updatedAt: now,
    });
  });

  // Create vacant unit under prop_p1
  await assertSucceeds(
    landlordDb.collection("units").doc("unit_a1").set({
      propertyId: "prop_p1",
      propertyName: "Tudor Court",
      propertyCity: "Mombasa",
      propertyCategory: "APARTMENTS",
      orgId: "org_units",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      unitNumber: "A-101",
      unitType: "ONE_BEDROOM",
      rentCents: 2500000,
      depositCents: 2500000,
      status: "AVAILABLE",
      amenities: ["Wi-Fi", "Borehole Water", "CCTV"],
      description: "First floor 1BR",
      imageRefs: [],
      published: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Create occupied unit under prop_p2
  await assertSucceeds(
    landlordDb.collection("units").doc("unit_b1").set({
      propertyId: "prop_p2",
      propertyName: "Kizingo Suites",
      propertyCity: "Mombasa",
      propertyCategory: "APARTMENTS",
      orgId: "org_units",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      unitNumber: "B-201",
      unitType: "TWO_BEDROOM",
      rentCents: 4500000,
      depositCents: 4500000,
      status: "OCCUPIED",
      amenities: ["Parking", "24/7 Security"],
      description: "Occupied 2BR",
      imageRefs: [],
      published: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject creating unit pointing to non-existent parent property
  await assertFails(
    landlordDb.collection("units").doc("unit_orphan").set({
      propertyId: "prop_missing",
      propertyName: "Ghost Court",
      propertyCity: "Nairobi",
      propertyCategory: "APARTMENTS",
      orgId: "org_units",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      unitNumber: "Z-1",
      unitType: "BEDSITTER",
      rentCents: 1200000,
      depositCents: 1200000,
      status: "AVAILABLE",
      amenities: [],
      description: "Invalid parent",
      imageRefs: [],
      published: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Scoped query for prop_p1 returns only unit_a1
  const p1Units = await assertSucceeds(
    landlordDb.collection("units")
      .where("propertyId", "==", "prop_p1")
      .where("authorizedUids", "array-contains", LANDLORD_UID)
      .get()
  );
  if (p1Units.size !== 1 || p1Units.docs[0].id !== "unit_a1") {
    throw new Error(`Expected only unit_a1 for prop_p1, got ${p1Units.docs.map((d) => d.id).join(",")}`);
  }

  // Update unit rent & amenities; reject changing immutable propertyId
  await assertSucceeds(
    landlordDb.collection("units").doc("unit_a1").update({
      rentCents: 2750000,
      amenities: ["Wi-Fi", "Borehole Water", "Backup Generator"],
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    landlordDb.collection("units").doc("unit_a1").update({
      propertyId: "prop_p2",
      updatedAt: pastDate(500),
    })
  );

  // Owner cannot delete an OCCUPIED unit, nor can unrelated user delete any unit
  await assertFails(otherDb.collection("units").doc("unit_a1").delete());
  await assertFails(landlordDb.collection("units").doc("unit_b1").delete());

  // Owner can delete an unoccupied (AVAILABLE) unit
  await assertSucceeds(landlordDb.collection("units").doc("unit_a1").delete());
});

