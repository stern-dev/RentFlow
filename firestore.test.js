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
const OTHER_STAFF_UID = "staff_005";
const ADMIN_UID = "admin_000";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 5000) {
  return new Date(Date.now() - msOffset);
}

function authedDb(uid, email, emailVerified = true) {
  return testEnv
    .authenticatedContext(uid, { email, email_verified: emailVerified })
    .firestore();
}

async function seedUser(ctxDb, {
  uid,
  fullName,
  email,
  roles,
  activeRole = roles[0],
  suspended = false,
  now = pastDate(20000),
}) {
  await ctxDb.collection("users").doc(uid).set({
    userId: uid,
    fullName,
    email,
    roles,
    activeRole,
    notifyRentDue: true,
    notifyMaintenance: true,
    notifyAnnouncements: true,
    suspended,
    createdAt: now,
    updatedAt: now,
  });
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

test("User profile: owner CRUD succeeds, immutable fields, privilege escalation, and cross-user PII read fail", async () => {
  const now = pastDate();
  const landlordDb = authedDb(LANDLORD_UID, "owner@example.com", true);
  const unverifiedDb = authedDb("unverified_uid", "unverified@example.com", false);

  // Unverified email cannot create a profile
  await assertFails(
    unverifiedDb.collection("users").doc("unverified_uid").set({
      userId: "unverified_uid",
      fullName: "Unverified User",
      email: "unverified@example.com",
      roles: ["TENANT"],
      activeRole: "TENANT",
      notifyRentDue: true,
      notifyMaintenance: true,
      notifyAnnouncements: true,
      suspended: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Spoofed email field on profile creation fails
  await assertFails(
    landlordDb.collection("users").doc(LANDLORD_UID).set({
      userId: LANDLORD_UID,
      fullName: "Wanjiku Kamau",
      email: "spoofed@example.com",
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

  // Reject self-assigned ADMIN role or any role addition
  await assertFails(
    landlordDb.collection("users").doc(LANDLORD_UID).update({
      roles: ["LANDLORD", "ADMIN"],
      activeRole: "ADMIN",
      updatedAt: pastDate(1000),
    })
  );

  // Reject immutable field mutations on user profile (userId, email, createdAt, suspended)
  await assertFails(
    landlordDb.collection("users").doc(LANDLORD_UID).update({
      email: "newemail@example.com",
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    landlordDb.collection("users").doc(LANDLORD_UID).update({
      createdAt: pastDate(1000),
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
  const tenantDb = authedDb(TENANT_UID, "tenant@example.com", true);
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

test("Property CRUD isolation: verified landlord can read/update/delete, unrelated user is denied", async () => {
  const now = pastDate(10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await seedUser(db, { uid: LANDLORD_UID, fullName: "Wanjiku", email: "owner@example.com", roles: ["LANDLORD"], now });
    await seedUser(db, { uid: OTHER_TENANT_UID, fullName: "Intruder", email: "other@example.com", roles: ["TENANT"], now });
  });

  const landlordDb = authedDb(LANDLORD_UID, "owner@example.com");
  const otherDb = authedDb(OTHER_TENANT_UID, "other@example.com");

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

  // Owner cannot mutate immutable orgId or ownerUid on property
  await assertFails(
    landlordDb.collection("properties").doc("prop_iso").update({
      ownerUid: OTHER_TENANT_UID,
      updatedAt: pastDate(500),
    })
  );

  // Owner can read, update, and delete their own property
  await assertSucceeds(landlordDb.collection("properties").where("authorizedUids", "array-contains", LANDLORD_UID).get());
  await assertSucceeds(landlordDb.collection("properties").doc("prop_iso").update({ name: "Lavington Green Villas", updatedAt: pastDate(500) }));
  await assertSucceeds(landlordDb.collection("properties").doc("prop_iso").delete());
});

test("Full RentFlow lifecycle: org -> property -> unit -> application -> lease -> invoice -> payment -> maintenance -> isolation", async () => {
  const now = pastDate(10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await seedUser(db, { uid: LANDLORD_UID, fullName: "Wanjiku Kamau", email: "owner@example.com", roles: ["LANDLORD"], now });
    await seedUser(db, { uid: MANAGER_UID, fullName: "Grace Wambui", email: "manager@example.com", roles: ["PROPERTY_MANAGER"], now });
    await seedUser(db, { uid: TENANT_UID, fullName: "Brian Ochieng", email: "tenant@example.com", roles: ["TENANT"], now });
    await seedUser(db, { uid: OTHER_TENANT_UID, fullName: "Other Tenant", email: "other@example.com", roles: ["TENANT"], now });
    await seedUser(db, { uid: STAFF_UID, fullName: "John Plumber", email: "staff@example.com", roles: ["MAINTENANCE_STAFF"], now });
  });

  const landlordDb = authedDb(LANDLORD_UID, "owner@example.com");
  const managerDb = authedDb(MANAGER_UID, "manager@example.com");
  const tenantDb = authedDb(TENANT_UID, "tenant@example.com");
  const otherTenantDb = authedDb(OTHER_TENANT_UID, "other@example.com");
  const staffDb = authedDb(STAFF_UID, "staff@example.com");

  // 1. Organization (Landlord only)
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

  // 2. Property (Landlord only)
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

  // 3. Unit (Owner creates; Manager cannot create units)
  await assertFails(
    managerDb.collection("units").doc("unit_mgr_blocked").set({
      propertyId: "prop_1",
      propertyName: "Milimani Court",
      propertyCity: "Kisumu",
      propertyCategory: "APARTMENTS",
      orgId: "org_1",
      ownerUid: LANDLORD_UID,
      managerUids: [MANAGER_UID],
      authorizedUids: [LANDLORD_UID, MANAGER_UID],
      unitNumber: "B99",
      unitType: "TWO_BEDROOM",
      rentCents: 2800000,
      depositCents: 2800000,
      status: "AVAILABLE",
      amenities: [],
      description: "Blocked for manager",
      imageRefs: [],
      published: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    landlordDb.collection("units").doc("unit_1").set({
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
    await seedUser(ctx.firestore(), {
      uid: TENANT_UID,
      fullName: "Brian Ochieng",
      email: "brian@example.com",
      roles: ["TENANT"],
      now,
    });
  });

  // Unverified email spoof must fail
  const spoofAdminDb = authedDb("spoof_uid", "sternomolo2@gmail.com", false);
  await assertFails(spoofAdminDb.collection("users").get());

  // Verified bootstrapped admin succeeds
  const realAdminDb = authedDb(ADMIN_UID, "sternomolo2@gmail.com", true);
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
    await seedUser(db, { uid: ORG_A_LANDLORD, fullName: "Org A Owner", email: "orga_owner@example.com", roles: ["LANDLORD"], now });
    await seedUser(db, { uid: ORG_A_MANAGER, fullName: "Org A Manager", email: "orga_mgr@example.com", roles: ["PROPERTY_MANAGER"], now });
    await seedUser(db, { uid: ORG_B_LANDLORD, fullName: "Org B Owner", email: "orgb_owner@example.com", roles: ["LANDLORD"], now });
    await seedUser(db, { uid: ORG_B_MANAGER, fullName: "Org B Manager", email: "orgb_mgr@example.com", roles: ["PROPERTY_MANAGER"], now });

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

  const orgALandlordDb = authedDb(ORG_A_LANDLORD, "orga_owner@example.com");
  const orgAManagerDb = authedDb(ORG_A_MANAGER, "orga_mgr@example.com");
  const orgBLandlordDb = authedDb(ORG_B_LANDLORD, "orgb_owner@example.com");
  const orgBManagerDb = authedDb(ORG_B_MANAGER, "orgb_mgr@example.com");

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
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await seedUser(db, { uid: LANDLORD_UID, fullName: "Wanjiku", email: "owner@example.com", roles: ["LANDLORD"], now });
    await seedUser(db, { uid: OTHER_TENANT_UID, fullName: "Other", email: "other@example.com", roles: ["TENANT"], now });

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

  const landlordDb = authedDb(LANDLORD_UID, "owner@example.com");
  const otherDb = authedDb(OTHER_TENANT_UID, "other@example.com");

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
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await seedUser(db, { uid: LANDLORD_UID, fullName: "Wanjiku", email: "owner@example.com", roles: ["LANDLORD"], now });
    await seedUser(db, { uid: OTHER_TENANT_UID, fullName: "Other", email: "other@example.com", roles: ["TENANT"], now });

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

  const landlordDb = authedDb(LANDLORD_UID, "owner@example.com");
  const otherDb = authedDb(OTHER_TENANT_UID, "other@example.com");

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

test("Role immutability, RoleInvite atomic acceptance, and Admin role grants", async () => {
  const now = pastDate(10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await seedUser(db, { uid: LANDLORD_UID, fullName: "Owner", email: "owner@example.com", roles: ["LANDLORD"], now });
    await seedUser(db, { uid: MANAGER_UID, fullName: "Grace Wambui", email: "manager@example.com", roles: ["TENANT"], now });
    await seedUser(db, { uid: OTHER_TENANT_UID, fullName: "Intruder", email: "intruder@example.com", roles: ["TENANT"], now });

    await db.collection("organizations").doc("org_rbac").set({
      name: "Westlands Realty",
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

  const landlordDb = authedDb(LANDLORD_UID, "owner@example.com");
  const managerDb = authedDb(MANAGER_UID, "manager@example.com");
  const intruderDb = authedDb(OTHER_TENANT_UID, "intruder@example.com");
  const adminDb = authedDb(ADMIN_UID, "sternomolo2@gmail.com");

  // User cannot self-mutate roles or switch activeRole to an ungranted role
  await assertFails(
    managerDb.collection("users").doc(MANAGER_UID).update({
      roles: ["TENANT", "LANDLORD"],
      activeRole: "LANDLORD",
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    managerDb.collection("users").doc(MANAGER_UID).update({
      activeRole: "PROPERTY_MANAGER",
      updatedAt: pastDate(1000),
    })
  );

  // Non-owner cannot create a RoleInvite for org_rbac
  await assertFails(
    intruderDb.collection("role_invites").doc("inv_bad").set({
      orgId: "org_rbac",
      orgName: "Westlands Realty",
      propertyIds: [],
      inviterUid: OTHER_TENANT_UID,
      inviterName: "Intruder",
      inviteeEmail: "intruder@example.com",
      role: "PROPERTY_MANAGER",
      status: "PENDING",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Org owner creates a legitimate RoleInvite for manager@example.com
  await assertSucceeds(
    landlordDb.collection("role_invites").doc("inv_mgr_1").set({
      orgId: "org_rbac",
      orgName: "Westlands Realty",
      propertyIds: [],
      inviterUid: LANDLORD_UID,
      inviterName: "Owner",
      inviteeEmail: "manager@example.com",
      role: "PROPERTY_MANAGER",
      status: "PENDING",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Wrong user (intruder) cannot read or accept Grace's invite
  await assertFails(intruderDb.collection("role_invites").doc("inv_mgr_1").get());
  await assertFails(
    intruderDb.collection("role_invites").doc("inv_mgr_1").update({
      status: "ACCEPTED",
      acceptedByUid: OTHER_TENANT_UID,
      updatedAt: pastDate(500),
    })
  );

  // Grace atomically accepts the invite via WriteBatch (invite + user profile + org memberUids)
  const batch = managerDb.batch();
  const acceptTime = pastDate(500);
  batch.update(managerDb.collection("role_invites").doc("inv_mgr_1"), {
    status: "ACCEPTED",
    acceptedByUid: MANAGER_UID,
    updatedAt: acceptTime,
  });
  batch.update(managerDb.collection("users").doc(MANAGER_UID), {
    roles: ["TENANT", "PROPERTY_MANAGER"],
    activeRole: "PROPERTY_MANAGER",
    activeOrgId: "org_rbac",
    lastAcceptedInviteId: "inv_mgr_1",
    updatedAt: acceptTime,
  });
  batch.update(managerDb.collection("organizations").doc("org_rbac"), {
    memberUids: [LANDLORD_UID, MANAGER_UID],
    updatedAt: acceptTime,
  });
  await assertSucceeds(batch.commit());

  // Grace can now switch between TENANT and PROPERTY_MANAGER (both in roles) without mutating roles
  await assertSucceeds(
    managerDb.collection("users").doc(MANAGER_UID).update({
      activeRole: "TENANT",
      updatedAt: pastDate(200),
    })
  );

  // Grace can write a self-scoped audit log for her role switch, and cannot mutate it
  await assertSucceeds(
    managerDb.collection("audit_logs").doc("log_switch_1").set({
      orgId: `user_${MANAGER_UID}`,
      actorUid: MANAGER_UID,
      actorName: "Grace Wambui",
      action: "SWITCH_ROLE",
      entityType: "UserProfile",
      entityId: MANAGER_UID,
      details: "Switched active workspace to TENANT",
      createdAt: pastDate(100),
    })
  );
  await assertFails(managerDb.collection("audit_logs").doc("log_switch_1").update({ details: "tampered" }));

  // Verified Admin can grant MAINTENANCE_STAFF role directly
  await assertSucceeds(
    adminDb.collection("users").doc(MANAGER_UID).update({
      roles: ["TENANT", "PROPERTY_MANAGER", "MAINTENANCE_STAFF"],
      updatedAt: pastDate(100),
    })
  );
});

test("Owner vs Property Manager separation on units, leases, payments, and expenses", async () => {
  const now = pastDate(10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await seedUser(db, { uid: LANDLORD_UID, fullName: "Owner", email: "owner@example.com", roles: ["LANDLORD"], now });
    await seedUser(db, { uid: MANAGER_UID, fullName: "Manager", email: "manager@example.com", roles: ["PROPERTY_MANAGER"], now });

    await db.collection("organizations").doc("org_sep").set({
      name: "Kilimani Holdings",
      ownerUid: LANDLORD_UID,
      country: "Kenya",
      currency: "KES",
      defaultDueDay: 5,
      memberUids: [LANDLORD_UID, MANAGER_UID],
      suspended: false,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("properties").doc("prop_sep").set({
      orgId: "org_sep",
      ownerUid: LANDLORD_UID,
      managerUids: [MANAGER_UID],
      authorizedUids: [LANDLORD_UID, MANAGER_UID],
      name: "Acacia Suites",
      category: "APARTMENTS",
      city: "Nairobi",
      neighborhood: "Kilimani",
      addressLine: "Denis Pritt Rd",
      description: "Apartments",
      imageRefs: [],
      archived: false,
      hasPublishedUnits: false,
      totalUnitsCount: 1,
      occupiedUnitsCount: 0,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("units").doc("unit_sep").set({
      propertyId: "prop_sep",
      propertyName: "Acacia Suites",
      propertyCity: "Nairobi",
      propertyCategory: "APARTMENTS",
      orgId: "org_sep",
      ownerUid: LANDLORD_UID,
      managerUids: [MANAGER_UID],
      authorizedUids: [LANDLORD_UID, MANAGER_UID],
      unitNumber: "C1",
      unitType: "ONE_BEDROOM",
      rentCents: 3000000,
      depositCents: 3000000,
      status: "AVAILABLE",
      amenities: ["Parking"],
      description: "Nice unit",
      imageRefs: [],
      published: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("leases").doc("lease_sep").set({
      orgId: "org_sep",
      propertyId: "prop_sep",
      propertyName: "Acacia Suites",
      unitId: "unit_sep",
      unitNumber: "C1",
      ownerUid: LANDLORD_UID,
      managerUids: [MANAGER_UID],
      authorizedUids: [LANDLORD_UID, MANAGER_UID],
      tenantUid: TENANT_UID,
      tenantName: "Brian Ochieng",
      tenantPhone: "+254722111222",
      rentCents: 3000000,
      depositCents: 3000000,
      startDateEpochMs: Date.now() - 86400000,
      endDateEpochMs: Date.now() + 86400000 * 300,
      dueDayOfMonth: 5,
      status: "ACTIVE",
      creditBalanceCents: 0,
      termsSummary: "Standard",
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("payments").doc("pay_verified").set({
      referenceCode: "UBK11122",
      leaseId: "lease_sep",
      orgId: "org_sep",
      propertyId: "prop_sep",
      propertyName: "Acacia Suites",
      unitId: "unit_sep",
      unitNumber: "C1",
      ownerUid: LANDLORD_UID,
      managerUids: [MANAGER_UID],
      authorizedUids: [LANDLORD_UID, MANAGER_UID],
      tenantUid: TENANT_UID,
      tenantName: "Brian Ochieng",
      amountCents: 3000000,
      method: "MPESA",
      simulatedCheckout: false,
      paymentDateEpochMs: Date.now() - 10000,
      status: "VERIFIED",
      recordedByUid: TENANT_UID,
      verifiedByUid: MANAGER_UID,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("expenses").doc("exp_sep").set({
      orgId: "org_sep",
      propertyId: "prop_sep",
      propertyName: "Acacia Suites",
      ownerUid: LANDLORD_UID,
      managerUids: [MANAGER_UID],
      authorizedUids: [LANDLORD_UID, MANAGER_UID],
      category: "REPAIRS",
      title: "Gate lock replacement",
      description: "Replaced lock",
      amountCents: 450000,
      expenseDateEpochMs: Date.now() - 10000,
      recordedByUid: MANAGER_UID,
      createdAt: now,
      updatedAt: now,
    });
  });

  const landlordDb = authedDb(LANDLORD_UID, "owner@example.com");
  const managerDb = authedDb(MANAGER_UID, "manager@example.com");

  // Manager CAN update operational unit fields (status, amenities)
  await assertSucceeds(
    managerDb.collection("units").doc("unit_sep").update({
      status: "UNDER_MAINTENANCE",
      published: false,
      amenities: ["Parking", "CCTV"],
      updatedAt: pastDate(500),
    })
  );

  // Manager CANNOT alter unit rentCents/depositCents or delete unit
  await assertFails(
    managerDb.collection("units").doc("unit_sep").update({
      rentCents: 3500000,
      updatedAt: pastDate(400),
    })
  );
  await assertFails(managerDb.collection("units").doc("unit_sep").delete());

  // Manager CANNOT terminate lease or alter lease rentCents
  await assertFails(
    managerDb.collection("leases").doc("lease_sep").update({
      status: "TERMINATED",
      terminationNotice: "Manager forced out",
      updatedAt: pastDate(400),
    })
  );
  await assertFails(
    managerDb.collection("leases").doc("lease_sep").update({
      rentCents: 4000000,
      updatedAt: pastDate(400),
    })
  );

  // Manager CANNOT reverse a verified payment or delete an expense
  await assertFails(
    managerDb.collection("payments").doc("pay_verified").update({
      status: "REVERSED",
      reversalReason: "Unauthorized reversal",
      updatedAt: pastDate(400),
    })
  );
  await assertFails(managerDb.collection("expenses").doc("exp_sep").delete());

  // Owner CAN update pricing, reverse verified payment, terminate lease, and delete expense
  await assertSucceeds(
    landlordDb.collection("units").doc("unit_sep").update({
      rentCents: 3200000,
      updatedAt: pastDate(300),
    })
  );
  await assertSucceeds(
    landlordDb.collection("payments").doc("pay_verified").update({
      status: "REVERSED",
      reversalReason: "Bank bounce confirmed by owner",
      updatedAt: pastDate(300),
    })
  );
  await assertSucceeds(
    landlordDb.collection("leases").doc("lease_sep").update({
      status: "TERMINATED",
      terminationNotice: "Mutual end of term",
      updatedAt: pastDate(300),
    })
  );
  await assertSucceeds(landlordDb.collection("expenses").doc("exp_sep").delete());
});

test("Verified Role Enforcement: Tenant/Staff cannot invoke Landlord/Manager operations; Staff cannot access unassigned tickets", async () => {
  const now = pastDate(10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await seedUser(db, { uid: LANDLORD_UID, fullName: "Owner", email: "owner@example.com", roles: ["LANDLORD"], now });
    await seedUser(db, { uid: TENANT_UID, fullName: "Tenant", email: "tenant@example.com", roles: ["TENANT"], now });
    await seedUser(db, { uid: STAFF_UID, fullName: "Staff", email: "staff@example.com", roles: ["MAINTENANCE_STAFF"], now });
    await seedUser(db, { uid: OTHER_STAFF_UID, fullName: "Other Staff", email: "staff2@example.com", roles: ["MAINTENANCE_STAFF"], now });

    await db.collection("organizations").doc("org_role_gate").set({
      name: "Karen Villas",
      ownerUid: LANDLORD_UID,
      country: "Kenya",
      currency: "KES",
      defaultDueDay: 5,
      memberUids: [LANDLORD_UID, STAFF_UID],
      suspended: false,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("properties").doc("prop_gate").set({
      orgId: "org_role_gate",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID, STAFF_UID],
      name: "Karen Court",
      category: "APARTMENTS",
      city: "Nairobi",
      neighborhood: "Karen",
      addressLine: "Langata Rd",
      description: "Secure court",
      imageRefs: [],
      archived: false,
      hasPublishedUnits: true,
      totalUnitsCount: 1,
      occupiedUnitsCount: 0,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("units").doc("unit_gate").set({
      propertyId: "prop_gate",
      propertyName: "Karen Court",
      propertyCity: "Nairobi",
      propertyCategory: "APARTMENTS",
      orgId: "org_role_gate",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      unitNumber: "K1",
      unitType: "ONE_BEDROOM",
      rentCents: 2000000,
      depositCents: 2000000,
      status: "AVAILABLE",
      amenities: [],
      description: "1BR",
      imageRefs: [],
      published: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("maintenance_requests").doc("maint_gate").set({
      orgId: "org_role_gate",
      propertyId: "prop_gate",
      propertyName: "Karen Court",
      unitId: "unit_gate",
      unitNumber: "K1",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      tenantUid: TENANT_UID,
      tenantName: "Tenant",
      assignedStaffUid: STAFF_UID,
      assignedStaffName: "Staff",
      title: "Broken window latch",
      description: "Latch snapped",
      category: "DOORS_WINDOWS",
      priority: "MEDIUM",
      status: "ASSIGNED",
      repairCostCents: 0,
      imageRefs: [],
      createdAt: now,
      updatedAt: now,
    });
  });

  const tenantDb = authedDb(TENANT_UID, "tenant@example.com");
  const staffDb = authedDb(STAFF_UID, "staff@example.com");
  const otherStaffDb = authedDb(OTHER_STAFF_UID, "staff2@example.com");

  // Tenant-only user CANNOT create an organization or property even claiming own UID
  await assertFails(
    tenantDb.collection("organizations").doc("org_rogue_tenant").set({
      name: "Rogue Org",
      ownerUid: TENANT_UID,
      country: "Kenya",
      currency: "KES",
      defaultDueDay: 5,
      memberUids: [TENANT_UID],
      suspended: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Maintenance Staff CANNOT create an organization, read/update property, or publish an announcement even if listed in authorizedUids
  await assertFails(
    staffDb.collection("organizations").doc("org_rogue_staff").set({
      name: "Rogue Staff Org",
      ownerUid: STAFF_UID,
      country: "Kenya",
      currency: "KES",
      defaultDueDay: 5,
      memberUids: [STAFF_UID],
      suspended: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    staffDb.collection("properties").doc("prop_gate").update({
      totalUnitsCount: 99,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    staffDb.collection("announcements").doc("ann_rogue_staff").set({
      orgId: "org_role_gate",
      propertyId: "prop_gate",
      propertyName: "Karen Court",
      ownerUid: LANDLORD_UID,
      authorizedUids: [LANDLORD_UID, STAFF_UID],
      targetUids: [TENANT_UID],
      title: "Fake Notice",
      body: "Staff cannot broadcast announcements",
      authorUid: STAFF_UID,
      authorName: "Staff",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Staff-only user CANNOT submit a tenant rental application
  await assertFails(
    staffDb.collection("applications").doc("app_staff_bad").set({
      unitId: "unit_gate",
      unitNumber: "K1",
      propertyId: "prop_gate",
      propertyName: "Karen Court",
      orgId: "org_role_gate",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      applicantUid: STAFF_UID,
      applicantName: "Staff",
      applicantPhone: "+254711000999",
      desiredMoveInDate: "2026-12-01",
      status: "SUBMITTED",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Unassigned staff CANNOT read or update maint_gate
  await assertFails(otherStaffDb.collection("maintenance_requests").doc("maint_gate").get());
  await assertFails(
    otherStaffDb.collection("maintenance_requests").doc("maint_gate").update({
      status: "COMPLETED",
      updatedAt: pastDate(500),
    })
  );

  // Assigned staff CANNOT mutate immutable title, category, or tenantUid on maintenance ticket
  await assertFails(
    staffDb.collection("maintenance_requests").doc("maint_gate").update({
      title: "Altered Title",
      status: "IN_PROGRESS",
      updatedAt: pastDate(500),
    })
  );
});

test("Account and organization suspension locks down all member reads and writes while Admin retains access", async () => {
  const now = pastDate(10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await seedUser(db, { uid: LANDLORD_UID, fullName: "Owner", email: "owner@example.com", roles: ["LANDLORD"], suspended: false, now });
    await seedUser(db, { uid: MANAGER_UID, fullName: "Suspended Manager", email: "mgr_susp@example.com", roles: ["PROPERTY_MANAGER"], suspended: true, now });

    await db.collection("organizations").doc("org_susp").set({
      name: "Suspended Holdings",
      ownerUid: LANDLORD_UID,
      country: "Kenya",
      currency: "KES",
      defaultDueDay: 5,
      memberUids: [LANDLORD_UID, MANAGER_UID],
      suspended: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("properties").doc("prop_susp").set({
      orgId: "org_susp",
      ownerUid: LANDLORD_UID,
      managerUids: [MANAGER_UID],
      authorizedUids: [LANDLORD_UID, MANAGER_UID],
      name: "Frozen Estate",
      category: "APARTMENTS",
      city: "Nairobi",
      neighborhood: "South B",
      addressLine: "Mombasa Rd",
      description: "Under suspension",
      imageRefs: [],
      archived: false,
      hasPublishedUnits: false,
      totalUnitsCount: 0,
      occupiedUnitsCount: 0,
      createdAt: now,
      updatedAt: now,
    });
  });

  const landlordDb = authedDb(LANDLORD_UID, "owner@example.com");
  const suspendedManagerDb = authedDb(MANAGER_UID, "mgr_susp@example.com");
  const adminDb = authedDb(ADMIN_UID, "sternomolo2@gmail.com");

  // Suspended manager can read their own profile (to display suspension banner) but cannot update profile or read orgs/properties
  await assertSucceeds(suspendedManagerDb.collection("users").doc(MANAGER_UID).get());
  await assertFails(
    suspendedManagerDb.collection("users").doc(MANAGER_UID).update({
      fullName: "Unsuspend Attempt",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(suspendedManagerDb.collection("organizations").doc("org_susp").get());
  await assertFails(suspendedManagerDb.collection("properties").doc("prop_susp").get());

  // Active landlord cannot mutate or create properties under a suspended organization
  await assertFails(
    landlordDb.collection("organizations").doc("org_susp").update({
      name: "Rename While Suspended",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    landlordDb.collection("properties").doc("prop_susp").update({
      name: "Edit While Org Suspended",
      updatedAt: pastDate(500),
    })
  );

  // Admin can reinstate the organization, restoring landlord writes
  await assertSucceeds(
    adminDb.collection("organizations").doc("org_susp").update({
      suspended: false,
      updatedAt: pastDate(300),
    })
  );
  await assertSucceeds(
    landlordDb.collection("properties").doc("prop_susp").update({
      name: "Restored Estate",
      updatedAt: pastDate(200),
    })
  );
});

test("FCM device tokens: owner CRUD succeeds, cross-user access, unverified email, and tampered payloads fail", async () => {
  const now = pastDate();
  const tenantDb = authedDb(TENANT_UID, "tenant@example.com", true);
  const otherDb = authedDb(OTHER_TENANT_UID, "other@example.com", true);
  const unverifiedDb = authedDb("unverified_fcm", "unv@example.com", false);

  const tokenDocRef = tenantDb.collection("users").doc(TENANT_UID).collection("fcm_tokens").doc("tok_hash_abc123");
  const validPayload = {
    userId: TENANT_UID,
    token: "fcm_sample_token_value_1234567890",
    platform: "ANDROID",
    notifyMaintenance: true,
    activeRole: "TENANT",
    appVersion: "1.0",
    createdAt: now,
    updatedAt: now,
  };

  // Unverified email cannot write FCM token
  await assertFails(
    unverifiedDb.collection("users").doc("unverified_fcm").collection("fcm_tokens").doc("tok_hash_unv")
      .set({ ...validPayload, userId: "unverified_fcm" })
  );

  // Cross-user write rejected
  await assertFails(
    otherDb.collection("users").doc(TENANT_UID).collection("fcm_tokens").doc("tok_hash_abc123").set(validPayload)
  );

  // Invalid short token rejected
  await assertFails(tokenDocRef.set({ ...validPayload, token: "short" }));

  // Owner create succeeds
  await assertSucceeds(tokenDocRef.set(validPayload));

  // Cross-user read/list/delete rejected
  await assertFails(otherDb.collection("users").doc(TENANT_UID).collection("fcm_tokens").doc("tok_hash_abc123").get());
  await assertFails(otherDb.collection("users").doc(TENANT_UID).collection("fcm_tokens").get());
  await assertFails(otherDb.collection("users").doc(TENANT_UID).collection("fcm_tokens").doc("tok_hash_abc123").delete());

  // Owner list & partial update succeed (including notifyRentDue)
  await assertSucceeds(tenantDb.collection("users").doc(TENANT_UID).collection("fcm_tokens").get());
  await assertSucceeds(
    tokenDocRef.update({
      notifyMaintenance: false,
      notifyRentDue: true,
      activeRole: "LANDLORD",
      updatedAt: pastDate(500),
    })
  );

  // Invalid non-bool notifyRentDue rejected
  await assertFails(
    tokenDocRef.update({
      notifyRentDue: "yes",
      updatedAt: pastDate(450),
    })
  );

  // Immutable userId / createdAt tamper rejected
  await assertFails(
    tokenDocRef.update({
      userId: OTHER_TENANT_UID,
      updatedAt: pastDate(300),
    })
  );
  await assertFails(
    tokenDocRef.update({
      createdAt: pastDate(100),
      updatedAt: pastDate(100),
    })
  );

  // Owner delete succeeds on sign-out
  await assertSucceeds(tokenDocRef.delete());
});

test("Saved listings bookmarks: owner create/read/delete succeeds, update and cross-user access denied", async () => {
  const now = pastDate();
  const tenantDb = authedDb(TENANT_UID, "tenant@example.com", true);
  const otherDb = authedDb(OTHER_TENANT_UID, "other@example.com", true);

  const savedRef = tenantDb.collection("users").doc(TENANT_UID).collection("saved_units").doc("unit_saved_1");
  const validSaved = {
    unitId: "unit_saved_1",
    propertyId: "prop_saved_1",
    propertyName: "Kilimani Heights",
    unitNumber: "A4",
    rentCents: 3500000,
    savedAt: now,
  };

  // Cross-user write rejected
  await assertFails(
    otherDb.collection("users").doc(TENANT_UID).collection("saved_units").doc("unit_saved_1").set(validSaved)
  );

  // Mismatched unitId rejected
  await assertFails(
    savedRef.set({ ...validSaved, unitId: "different_unit" })
  );

  // Owner create & list succeed
  await assertSucceeds(savedRef.set(validSaved));
  await assertSucceeds(tenantDb.collection("users").doc(TENANT_UID).collection("saved_units").get());

  // Cross-user read/list/delete rejected
  await assertFails(otherDb.collection("users").doc(TENANT_UID).collection("saved_units").doc("unit_saved_1").get());
  await assertFails(otherDb.collection("users").doc(TENANT_UID).collection("saved_units").get());
  await assertFails(otherDb.collection("users").doc(TENANT_UID).collection("saved_units").doc("unit_saved_1").delete());

  // Updates are immutable (delete + recreate instead)
  await assertFails(savedRef.update({ rentCents: 4000000 }));

  // Owner delete succeeds
  await assertSucceeds(savedRef.delete());
});

test("Verified landlord anti-spoofing, M-Pesa settings, invoice reminders, and listing scam reporting", async () => {
  const now = pastDate(10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await seedUser(db, { uid: LANDLORD_UID, fullName: "Wanjiku Kamau", email: "owner@example.com", roles: ["LANDLORD"], now });
    await seedUser(db, { uid: TENANT_UID, fullName: "Brian Ochieng", email: "tenant@example.com", roles: ["TENANT"], now });
  });

  const landlordDb = authedDb(LANDLORD_UID, "owner@example.com", true);
  const tenantDb = authedDb(TENANT_UID, "tenant@example.com", true);
  const adminDb = authedDb(ADMIN_UID, "sternomolo2@gmail.com", true);

  // Landlord CANNOT self-verify on organization creation
  await assertFails(
    landlordDb.collection("organizations").doc("org_spoof").set({
      name: "Fake Verified Ltd",
      ownerUid: LANDLORD_UID,
      country: "Kenya",
      currency: "KES",
      defaultDueDay: 5,
      memberUids: [LANDLORD_UID],
      suspended: false,
      verified: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Landlord creates unverified organization with M-Pesa Paybill/Till
  await assertSucceeds(
    landlordDb.collection("organizations").doc("org_ke").set({
      name: "Nairobi Homes Ltd",
      ownerUid: LANDLORD_UID,
      country: "Kenya",
      currency: "KES",
      defaultDueDay: 5,
      memberUids: [LANDLORD_UID],
      suspended: false,
      verified: false,
      mpesaPaybill: "522522",
      mpesaTill: "814920",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Landlord CANNOT toggle verified=true on update
  await assertFails(
    landlordDb.collection("organizations").doc("org_ke").update({
      verified: true,
      updatedAt: pastDate(500),
    })
  );

  // Landlord CANNOT create a property claiming landlordVerified=true before admin verifies org
  await assertFails(
    landlordDb.collection("properties").doc("prop_spoof").set({
      orgId: "org_ke",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      name: "Spoofed Court",
      category: "APARTMENTS",
      city: "Nairobi",
      county: "Nairobi",
      neighborhood: "Kilimani",
      addressLine: "Argwings Kodhek Rd",
      description: "Test",
      imageRefs: [],
      archived: false,
      hasPublishedUnits: false,
      totalUnitsCount: 0,
      occupiedUnitsCount: 0,
      landlordVerified: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Admin verifies the organization
  await assertSucceeds(
    adminDb.collection("organizations").doc("org_ke").update({
      verified: true,
      verifiedAtEpochMs: Date.now() - 1000,
      verifiedByUid: ADMIN_UID,
      updatedAt: pastDate(500),
    })
  );

  // Now landlord CAN create verified property and unit with M-Pesa instructions
  await assertSucceeds(
    landlordDb.collection("properties").doc("prop_ke").set({
      orgId: "org_ke",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      name: "Kilimani Ridge",
      category: "APARTMENTS",
      city: "Nairobi",
      county: "Nairobi",
      neighborhood: "Kilimani",
      addressLine: "Lenana Rd",
      description: "Verified apartments",
      imageRefs: [],
      archived: false,
      hasPublishedUnits: true,
      totalUnitsCount: 1,
      occupiedUnitsCount: 0,
      landlordVerified: true,
      mpesaPaybill: "522522",
      mpesaAccountPrefix: "KR-",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    landlordDb.collection("units").doc("unit_ke_1").set({
      propertyId: "prop_ke",
      propertyName: "Kilimani Ridge",
      propertyCity: "Nairobi",
      propertyCategory: "APARTMENTS",
      orgId: "org_ke",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      unitNumber: "A1",
      unitType: "ONE_BEDROOM",
      rentCents: 3200000,
      depositCents: 3200000,
      status: "AVAILABLE",
      amenities: ["CCTV", "Borehole"],
      description: "Spacious 1BR",
      imageRefs: [],
      published: true,
      landlordVerified: true,
      mpesaPaybill: "522522",
      mpesaAccountPrefix: "KR-",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Tenant can report a suspicious listing in reported_issues
  await assertSucceeds(
    tenantDb.collection("reported_issues").doc("scam_rep_1").set({
      reporterUid: TENANT_UID,
      reporterName: "Brian Ochieng",
      orgId: "org_ke",
      category: "LISTING_SCAM",
      title: "Suspicious deposit request on Unit A1",
      description: "Asked for mobile money deposit prior to physical viewing.",
      status: "OPEN",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Tenant cannot resolve their own report; Admin can resolve
  await assertFails(
    tenantDb.collection("reported_issues").doc("scam_rep_1").update({
      status: "RESOLVED",
      updatedAt: pastDate(200),
    })
  );
  await assertSucceeds(
    adminDb.collection("reported_issues").doc("scam_rep_1").update({
      status: "RESOLVED",
      adminNotes: "Reviewed and verified owner identity.",
      updatedAt: pastDate(200),
    })
  );

  // Seed lease & invoice, verify landlord can update reminder metadata
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("leases").doc("lease_ke_1").set({
      orgId: "org_ke",
      propertyId: "prop_ke",
      propertyName: "Kilimani Ridge",
      unitId: "unit_ke_1",
      unitNumber: "A1",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      tenantUid: TENANT_UID,
      tenantName: "Brian Ochieng",
      tenantPhone: "+254712345678",
      rentCents: 3200000,
      depositCents: 3200000,
      startDateEpochMs: Date.now() - 864000000,
      endDateEpochMs: Date.now() + 864000000,
      dueDayOfMonth: 5,
      status: "ACTIVE",
      creditBalanceCents: 0,
      termsSummary: "Standard KE lease",
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("invoices").doc("inv_ke_1").set({
      invoiceNumber: "INV-202611-A1",
      leaseId: "lease_ke_1",
      orgId: "org_ke",
      propertyId: "prop_ke",
      propertyName: "Kilimani Ridge",
      unitId: "unit_ke_1",
      unitNumber: "A1",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      tenantUid: TENANT_UID,
      tenantName: "Brian Ochieng",
      billingPeriod: "2026-11",
      rentCents: 3200000,
      extraChargesCents: 0,
      previousBalanceCents: 0,
      totalDueCents: 3200000,
      paidCents: 0,
      outstandingCents: 3200000,
      dueDateEpochMs: Date.now() - 86400000,
      status: "OVERDUE",
      createdAt: now,
      updatedAt: now,
    });
  });

  await assertSucceeds(
    landlordDb.collection("invoices").doc("inv_ke_1").update({
      lastReminderSentEpochMs: Date.now() - 1000,
      reminderCount: 1,
      updatedAt: pastDate(100),
    })
  );
});

test("SavedPaymentMethod: owner CRUD with Kenya Paybill/Till validation, cross-user isolation, and Payment channel metadata", async () => {
  const now = pastDate(5000);
  const tenantDb = authedDb(TENANT_UID, "tenant@example.com", true);
  const otherTenantDb = authedDb(OTHER_TENANT_UID, "other@example.com", true);
  const landlordDb = authedDb(LANDLORD_UID, "owner@example.com", true);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await seedUser(db, { uid: TENANT_UID, fullName: "Brian Ochieng", email: "tenant@example.com", roles: ["TENANT"] });
    await seedUser(db, { uid: OTHER_TENANT_UID, fullName: "Mercy Wambui", email: "other@example.com", roles: ["TENANT"] });
    await seedUser(db, { uid: LANDLORD_UID, fullName: "Wanjiku Kamau", email: "owner@example.com", roles: ["LANDLORD"] });
  });

  // Valid Kenya M-Pesa Paybill (5-7 digits, non-zero leading + accountReference)
  await assertSucceeds(
    tenantDb.collection("users").doc(TENANT_UID).collection("payment_methods").doc("pm_paybill").set({
      userId: TENANT_UID,
      label: "KCB Rent Paybill",
      channelType: "MPESA_PAYBILL",
      roleScope: "TENANT",
      paybillNumber: "522522",
      accountReference: "KH-A101",
      defaultMethod: true,
      smsVerified: true,
      smsSampleRef: "UBK7X92M10",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid Kenya M-Pesa Till (Buy Goods)
  await assertSucceeds(
    landlordDb.collection("users").doc(LANDLORD_UID).collection("payment_methods").doc("pm_till").set({
      userId: LANDLORD_UID,
      label: "Westlands Buy Goods Till",
      channelType: "MPESA_TILL",
      roleScope: "LANDLORD",
      tillNumber: "882211",
      defaultMethod: true,
      smsVerified: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject invalid shortcodes: 4 digits, 8 digits, leading zero, or Paybill without accountReference
  await assertFails(
    tenantDb.collection("users").doc(TENANT_UID).collection("payment_methods").doc("pm_bad_short").set({
      userId: TENANT_UID,
      label: "Too Short Paybill",
      channelType: "MPESA_PAYBILL",
      roleScope: "TENANT",
      paybillNumber: "1234",
      accountReference: "A101",
      defaultMethod: false,
      smsVerified: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    tenantDb.collection("users").doc(TENANT_UID).collection("payment_methods").doc("pm_bad_zero").set({
      userId: TENANT_UID,
      label: "Leading Zero Till",
      channelType: "MPESA_TILL",
      roleScope: "TENANT",
      tillNumber: "012345",
      defaultMethod: false,
      smsVerified: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    tenantDb.collection("users").doc(TENANT_UID).collection("payment_methods").doc("pm_missing_acc").set({
      userId: TENANT_UID,
      label: "Missing Account Paybill",
      channelType: "MPESA_PAYBILL",
      roleScope: "TENANT",
      paybillNumber: "522522",
      defaultMethod: false,
      smsVerified: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Owner can list & update defaultMethod; cross-user read/write/delete is denied
  await assertSucceeds(
    tenantDb.collection("users").doc(TENANT_UID).collection("payment_methods").get()
  );
  await assertSucceeds(
    tenantDb.collection("users").doc(TENANT_UID).collection("payment_methods").doc("pm_paybill").update({
      defaultMethod: false,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    otherTenantDb.collection("users").doc(TENANT_UID).collection("payment_methods").get()
  );
  await assertFails(
    otherTenantDb.collection("users").doc(TENANT_UID).collection("payment_methods").doc("pm_paybill").delete()
  );
  await assertSucceeds(
    tenantDb.collection("users").doc(TENANT_UID).collection("payment_methods").doc("pm_paybill").delete()
  );

  // Payment creation with optional paymentMethodId / paymentChannelType / paymentShortcode succeeds
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("organizations").doc("org_pm").set({
      name: "Kilimani Homes",
      ownerUid: LANDLORD_UID,
      country: "Kenya",
      currency: "KES",
      defaultDueDay: 5,
      memberUids: [LANDLORD_UID],
      suspended: false,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("leases").doc("lease_pm").set({
      orgId: "org_pm",
      propertyId: "prop_pm",
      propertyName: "Kilimani Ridge",
      unitId: "unit_pm",
      unitNumber: "A1",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      tenantUid: TENANT_UID,
      tenantName: "Brian Ochieng",
      tenantPhone: "+254712345678",
      rentCents: 3000000,
      depositCents: 3000000,
      startDateEpochMs: Date.now() - 864000000,
      endDateEpochMs: Date.now() + 864000000,
      dueDayOfMonth: 5,
      status: "ACTIVE",
      creditBalanceCents: 0,
      termsSummary: "Standard KE lease",
      createdAt: now,
      updatedAt: now,
    });
  });

  await assertSucceeds(
    tenantDb.collection("payments").doc("pay_ke_1").set({
      referenceCode: "UBK7X92M10",
      leaseId: "lease_pm",
      orgId: "org_pm",
      propertyId: "prop_pm",
      propertyName: "Kilimani Ridge",
      unitId: "unit_pm",
      unitNumber: "A1",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      tenantUid: TENANT_UID,
      tenantName: "Brian Ochieng",
      amountCents: 3000000,
      method: "MPESA",
      paymentMethodId: "pm_paybill",
      paymentChannelType: "MPESA_PAYBILL",
      paymentShortcode: "522522",
      simulatedCheckout: false,
      paymentDateEpochMs: Date.now() - 2000,
      status: "PENDING_VERIFICATION",
      recordedByUid: TENANT_UID,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("MpesaReceipt: owner CRUD, strict tx code & status validation, cross-user isolation, and Payment SMS verification update", async () => {
  const now = pastDate(6000);
  const landlordDb = authedDb(LANDLORD_UID, "owner@example.com", true);
  const tenantDb = authedDb(TENANT_UID, "tenant@example.com", true);
  const otherTenantDb = authedDb(OTHER_TENANT_UID, "other@example.com", true);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await seedUser(db, { uid: LANDLORD_UID, fullName: "Wanjiku Kamau", email: "owner@example.com", roles: ["LANDLORD"] });
    await seedUser(db, { uid: TENANT_UID, fullName: "Brian Ochieng", email: "tenant@example.com", roles: ["TENANT"] });
    await seedUser(db, { uid: OTHER_TENANT_UID, fullName: "Mercy Wambui", email: "other@example.com", roles: ["TENANT"] });
    await db.collection("organizations").doc("org_sms").set({
      name: "Kilimani Homes",
      ownerUid: LANDLORD_UID,
      country: "Kenya",
      currency: "KES",
      defaultDueDay: 5,
      memberUids: [LANDLORD_UID],
      suspended: false,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("leases").doc("lease_sms").set({
      orgId: "org_sms",
      propertyId: "prop_sms",
      propertyName: "Kilimani Ridge",
      unitId: "unit_sms",
      unitNumber: "A1",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      tenantUid: TENANT_UID,
      tenantName: "Brian Ochieng",
      tenantPhone: "+254712345678",
      rentCents: 3000000,
      depositCents: 3000000,
      startDateEpochMs: Date.now() - 864000000,
      endDateEpochMs: Date.now() + 864000000,
      dueDayOfMonth: 5,
      status: "ACTIVE",
      creditBalanceCents: 0,
      termsSummary: "Standard KE lease",
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("payments").doc("pay_sms_pending").set({
      referenceCode: "UBK7X92M10",
      leaseId: "lease_sms",
      orgId: "org_sms",
      propertyId: "prop_sms",
      propertyName: "Kilimani Ridge",
      unitId: "unit_sms",
      unitNumber: "A1",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      tenantUid: TENANT_UID,
      tenantName: "Brian Ochieng",
      amountCents: 3000000,
      method: "MPESA",
      simulatedCheckout: false,
      paymentDateEpochMs: Date.now() - 4000,
      status: "PENDING_VERIFICATION",
      recordedByUid: TENANT_UID,
      createdAt: now,
      updatedAt: now,
    });
  });

  const landlordReceiptRef = landlordDb.collection("users").doc(LANDLORD_UID).collection("mpesa_receipts").doc("mpesa_UBK7X92M10");
  const validReceipt = {
    userId: LANDLORD_UID,
    transactionCode: "UBK7X92M10",
    amountCents: 3000000,
    direction: "INCOMING_RECEIVED",
    counterpartyName: "BRIAN OCHIENG",
    counterpartyPhone: "+254712345678",
    captureSource: "NOTIFICATION",
    status: "STAGED",
    rawSnippet: "UBK7X92M10 Confirmed. You have received Ksh30,000.00 from BRIAN OCHIENG 0712345678.",
    transactionTimestampMs: Date.now() - 3000,
    createdAt: now,
    updatedAt: now,
  };

  // Reject invalid transactionCode (not 10 uppercase alphanumeric)
  await assertFails(
    landlordReceiptRef.set({ ...validReceipt, transactionCode: "SHORT123" })
  );
  // Reject non-positive amount
  await assertFails(
    landlordReceiptRef.set({ ...validReceipt, amountCents: 0 })
  );
  // Reject cross-user write
  await assertFails(
    tenantDb.collection("users").doc(LANDLORD_UID).collection("mpesa_receipts").doc("mpesa_UBK7X92M10").set(validReceipt)
  );

  // Owner create & list succeed
  await assertSucceeds(landlordReceiptRef.set(validReceipt));
  await assertSucceeds(landlordDb.collection("users").doc(LANDLORD_UID).collection("mpesa_receipts").get());

  // Cross-user read/list/delete denied
  await assertFails(otherTenantDb.collection("users").doc(LANDLORD_UID).collection("mpesa_receipts").doc("mpesa_UBK7X92M10").get());
  await assertFails(otherTenantDb.collection("users").doc(LANDLORD_UID).collection("mpesa_receipts").get());
  await assertFails(otherTenantDb.collection("users").doc(LANDLORD_UID).collection("mpesa_receipts").doc("mpesa_UBK7X92M10").delete());

  // Reject tampering immutable transactionCode or amountCents on update
  await assertFails(
    landlordReceiptRef.update({
      amountCents: 4000000,
      updatedAt: pastDate(500),
    })
  );

  // Owner can mark receipt RECONCILED and verify matching Payment with MPESA_SMS_MATCH metadata
  await assertSucceeds(
    landlordReceiptRef.update({
      status: "RECONCILED",
      matchedPaymentId: "pay_sms_pending",
      matchedLeaseId: "lease_sms",
      updatedAt: pastDate(400),
    })
  );
  await assertSucceeds(
    landlordDb.collection("payments").doc("pay_sms_pending").update({
      status: "VERIFIED",
      verifiedByUid: LANDLORD_UID,
      verificationSource: "MPESA_SMS_MATCH",
      matchedReceiptId: "mpesa_UBK7X92M10",
      receiptNumber: "RCP-12345678",
      allocationsSummary: "Allocated KES 30,000",
      updatedAt: pastDate(300),
    })
  );

  // Tenant cannot spoof verificationSource to self-verify a Payment
  await assertFails(
    tenantDb.collection("payments").doc("pay_sms_pending").update({
      status: "VERIFIED",
      verificationSource: "MPESA_SMS_MATCH",
      updatedAt: pastDate(200),
    })
  );
});

test("Property & Unit building geolocation: paired coordinates, Kenya equator allowance, (0,0) & out-of-range rejection, and owner updates", async () => {
  const now = pastDate(5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await seedUser(db, { uid: LANDLORD_UID, fullName: "Wanjiku Kamau", email: "owner@example.com", roles: ["LANDLORD"], now });
    await seedUser(db, { uid: TENANT_UID, fullName: "Brian Ochieng", email: "tenant@example.com", roles: ["TENANT"], now });
    await db.collection("organizations").doc("org_loc").set({
      name: "Kilimani Estates",
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

  const landlordDb = authedDb(LANDLORD_UID, "owner@example.com", true);
  const tenantDb = authedDb(TENANT_UID, "tenant@example.com", true);

  const baseProp = {
    orgId: "org_loc",
    ownerUid: LANDLORD_UID,
    managerUids: [],
    authorizedUids: [LANDLORD_UID],
    name: "Argwings Court",
    category: "APARTMENTS",
    city: "Nairobi",
    county: "Nairobi",
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
  };

  // Reject unpaired coordinate (latitude without longitude)
  await assertFails(
    landlordDb.collection("properties").doc("prop_bad_pair").set({
      ...baseProp,
      latitude: -1.2896,
    })
  );

  // Reject Null Island (0.0, 0.0)
  await assertFails(
    landlordDb.collection("properties").doc("prop_null_island").set({
      ...baseProp,
      latitude: 0.0,
      longitude: 0.0,
    })
  );

  // Reject out-of-bounds latitude (> 90)
  await assertFails(
    landlordDb.collection("properties").doc("prop_oob_lat").set({
      ...baseProp,
      latitude: 95.0,
      longitude: 36.82,
    })
  );

  // Allow Kenya equator coordinates (latitude 0.0 with non-zero longitude)
  await assertSucceeds(
    landlordDb.collection("properties").doc("prop_equator").set({
      ...baseProp,
      name: "Nanyuki Equator Suites",
      city: "Nanyuki",
      latitude: 0.0,
      longitude: 37.0740,
      formattedAddress: "Nanyuki, Laikipia, Kenya",
      locationVerified: true,
    })
  );

  // Valid Nairobi pin create + unit create + coordinate update propagation
  await assertSucceeds(
    landlordDb.collection("properties").doc("prop_loc").set({
      ...baseProp,
      latitude: -1.2896,
      longitude: 36.7831,
      formattedAddress: "Argwings Kodhek Rd, Kilimani, Nairobi",
      placeId: "ChIJNairobiKilimani123",
      locationVerified: true,
    })
  );

  await assertSucceeds(
    landlordDb.collection("units").doc("unit_loc").set({
      propertyId: "prop_loc",
      propertyName: "Argwings Court",
      propertyCity: "Nairobi",
      propertyCategory: "APARTMENTS",
      orgId: "org_loc",
      ownerUid: LANDLORD_UID,
      managerUids: [],
      authorizedUids: [LANDLORD_UID],
      unitNumber: "A1",
      unitType: "ONE_BEDROOM",
      rentCents: 3500000,
      depositCents: 3500000,
      status: "AVAILABLE",
      amenities: ["Wi-Fi"],
      description: "Bright 1BR",
      imageRefs: [],
      published: true,
      latitude: -1.2896,
      longitude: 36.7831,
      formattedAddress: "Argwings Kodhek Rd, Kilimani, Nairobi",
      locationVerified: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Owner updates building pin and propagates coordinates to child unit
  await assertSucceeds(
    landlordDb.collection("properties").doc("prop_loc").update({
      latitude: -1.2901,
      longitude: 36.7842,
      formattedAddress: "Yaya Centre Area, Kilimani, Nairobi",
      placeId: "ChIJUpdatedPlace",
      locationVerified: true,
      updatedAt: pastDate(400),
    })
  );
  await assertSucceeds(
    landlordDb.collection("units").doc("unit_loc").update({
      latitude: -1.2901,
      longitude: 36.7842,
      formattedAddress: "Yaya Centre Area, Kilimani, Nairobi",
      locationVerified: true,
      updatedAt: pastDate(300),
    })
  );

  // Tenant cannot tamper with unit or property location
  await assertFails(
    tenantDb.collection("properties").doc("prop_loc").update({
      latitude: -1.3000,
      longitude: 36.8000,
      updatedAt: pastDate(200),
    })
  );
  await assertFails(
    tenantDb.collection("units").doc("unit_loc").update({
      latitude: -1.3000,
      longitude: 36.8000,
      updatedAt: pastDate(200),
    })
  );
});

test("Move-in checklists & deposit settlements: role permissions, KES math invariant, tenant review/dispute, payout rules, and immutable locks", async () => {
  const now = pastDate(8000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await seedUser(db, { uid: LANDLORD_UID, fullName: "Wanjiku Kamau", email: "owner@example.com", roles: ["LANDLORD"], now });
    await seedUser(db, { uid: MANAGER_UID, fullName: "Grace Wambui", email: "manager@example.com", roles: ["PROPERTY_MANAGER"], now });
    await seedUser(db, { uid: TENANT_UID, fullName: "Brian Ochieng", email: "tenant@example.com", roles: ["TENANT"], now });
    await seedUser(db, { uid: OTHER_TENANT_UID, fullName: "Other Tenant", email: "other@example.com", roles: ["TENANT"], now });
    await db.collection("organizations").doc("org_handover").set({
      name: "Kilimani Homes",
      ownerUid: LANDLORD_UID,
      country: "Kenya",
      currency: "KES",
      defaultDueDay: 5,
      memberUids: [LANDLORD_UID, MANAGER_UID],
      suspended: false,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("leases").doc("lease_handover").set({
      orgId: "org_handover",
      propertyId: "prop_h1",
      propertyName: "Kilimani Ridge",
      unitId: "unit_h1",
      unitNumber: "C4",
      ownerUid: LANDLORD_UID,
      managerUids: [MANAGER_UID],
      authorizedUids: [LANDLORD_UID, MANAGER_UID],
      tenantUid: TENANT_UID,
      tenantName: "Brian Ochieng",
      tenantPhone: "+254712345678",
      rentCents: 3000000,
      depositCents: 3000000,
      startDateEpochMs: Date.now() - 864000000,
      endDateEpochMs: Date.now() + 864000000,
      dueDayOfMonth: 5,
      status: "ACTIVE",
      creditBalanceCents: 200000,
      termsSummary: "Standard KE lease",
      createdAt: now,
      updatedAt: now,
    });
  });

  const landlordDb = authedDb(LANDLORD_UID, "owner@example.com", true);
  const managerDb = authedDb(MANAGER_UID, "manager@example.com", true);
  const tenantDb = authedDb(TENANT_UID, "tenant@example.com", true);
  const otherDb = authedDb(OTHER_TENANT_UID, "other@example.com", true);

  const checklistRef = landlordDb.collection("move_in_checklists").doc("cl_lease_handover");
  const baseChecklist = {
    leaseId: "lease_handover",
    orgId: "org_handover",
    propertyId: "prop_h1",
    propertyName: "Kilimani Ridge",
    unitId: "unit_h1",
    unitNumber: "C4",
    ownerUid: LANDLORD_UID,
    managerUids: [MANAGER_UID],
    authorizedUids: [LANDLORD_UID, MANAGER_UID],
    tenantUid: TENANT_UID,
    tenantName: "Brian Ochieng",
    status: "DRAFT",
    inspectionDateEpochMs: Date.now() - 1000,
    waterMeterReading: "WM-1024",
    electricityMeterReading: "KPLC-5541",
    keysHandedOverCount: 3,
    items: [
      { itemId: "living_walls", area: "Living Room", label: "Walls & Paint", condition: "GOOD", notes: "" },
    ],
    imageRefs: [],
    createdByUid: LANDLORD_UID,
    createdAt: now,
    updatedAt: now,
  };

  // Tenant cannot create a move-in checklist
  await assertFails(
    tenantDb.collection("move_in_checklists").doc("cl_lease_handover").set({
      ...baseChecklist,
      createdByUid: TENANT_UID,
    })
  );

  // Landlord creates DRAFT checklist, then assigned manager sends for tenant review
  await assertSucceeds(checklistRef.set(baseChecklist));
  await assertSucceeds(
    managerDb.collection("move_in_checklists").doc("cl_lease_handover").update({
      status: "PENDING_TENANT_REVIEW",
      landlordNotes: "Freshly painted before handover",
      updatedAt: pastDate(700),
    })
  );

  // Other unrelated user denied read & write
  await assertFails(otherDb.collection("move_in_checklists").doc("cl_lease_handover").get());
  await assertFails(otherDb.collection("move_in_checklists").doc("cl_lease_handover").update({ status: "DISPUTED", updatedAt: pastDate(600) }));

  // Tenant cannot dispute without an explanation note
  await assertFails(
    tenantDb.collection("move_in_checklists").doc("cl_lease_handover").update({
      status: "DISPUTED",
      updatedAt: pastDate(600),
    })
  );

  // Tenant disputes with valid note
  await assertSucceeds(
    tenantDb.collection("move_in_checklists").doc("cl_lease_handover").update({
      status: "DISPUTED",
      tenantResponseNote: "Bathroom mirror has a small crack on bottom corner.",
      updatedAt: pastDate(550),
    })
  );

  // Landlord updates checklist and re-submits for sign-off
  await assertSucceeds(
    checklistRef.update({
      status: "PENDING_TENANT_REVIEW",
      landlordNotes: "Mirror crack noted; tenant not liable.",
      updatedAt: pastDate(500),
    })
  );

  // Tenant cannot tamper with items/keys when acknowledging
  await assertFails(
    tenantDb.collection("move_in_checklists").doc("cl_lease_handover").update({
      status: "ACKNOWLEDGED",
      keysHandedOverCount: 1,
      acknowledgedByUid: TENANT_UID,
      acknowledgedAtEpochMs: Date.now(),
      updatedAt: pastDate(450),
    })
  );

  // Tenant signs off cleanly
  await assertSucceeds(
    tenantDb.collection("move_in_checklists").doc("cl_lease_handover").update({
      status: "ACKNOWLEDGED",
      tenantResponseNote: "Confirmed and accepted.",
      acknowledgedByUid: TENANT_UID,
      acknowledgedAtEpochMs: Date.now(),
      updatedAt: pastDate(400),
    })
  );

  // Signed-off checklist is permanently locked against landlord and tenant edits
  await assertFails(
    checklistRef.update({
      landlordNotes: "Tampering after sign-off",
      updatedAt: pastDate(350),
    })
  );
  await assertFails(
    tenantDb.collection("move_in_checklists").doc("cl_lease_handover").update({
      status: "DISPUTED",
      tenantResponseNote: "Late dispute",
      updatedAt: pastDate(350),
    })
  );

  // --- Deposit Settlement Tests ---
  const settlementRef = landlordDb.collection("deposit_settlements").doc("ds_lease_handover");
  const validSettlement = {
    leaseId: "lease_handover",
    orgId: "org_handover",
    propertyId: "prop_h1",
    propertyName: "Kilimani Ridge",
    unitId: "unit_h1",
    unitNumber: "C4",
    ownerUid: LANDLORD_UID,
    managerUids: [MANAGER_UID],
    authorizedUids: [LANDLORD_UID, MANAGER_UID],
    tenantUid: TENANT_UID,
    tenantName: "Brian Ochieng",
    depositHeldCents: 3000000,
    arrearsIncludedCents: 200000,
    creditAppliedCents: 200000,
    deductions: [
      { id: "ded_1", category: "CLEANING", description: "Deep cleaning", amountCents: 300000 },
    ],
    deductionsTotalCents: 300000,
    netRefundCents: 2700000,
    balanceOwedCents: 0,
    moveOutDateEpochMs: Date.now(),
    waterMeterFinal: "WM-1180",
    electricityMeterFinal: "KPLC-6002",
    keysReturnedCount: 3,
    imageRefs: [],
    status: "ISSUED",
    refundMethod: "MPESA",
    createdByUid: LANDLORD_UID,
    createdAt: now,
    updatedAt: now,
  };

  // Reject settlement with invalid KES arithmetic invariant
  await assertFails(
    landlordDb.collection("deposit_settlements").doc("ds_bad_math").set({
      ...validSettlement,
      netRefundCents: 2999900,
    })
  );

  // Reject creating directly in SETTLED state
  await assertFails(
    landlordDb.collection("deposit_settlements").doc("ds_premature").set({
      ...validSettlement,
      status: "SETTLED",
    })
  );

  // Landlord creates & issues valid settlement
  await assertSucceeds(settlementRef.set(validSettlement));

  // Tenant disputes without reason -> rejected
  await assertFails(
    tenantDb.collection("deposit_settlements").doc("ds_lease_handover").update({
      status: "DISPUTED",
      updatedAt: pastDate(300),
    })
  );

  // Tenant disputes with reason -> succeeds
  await assertSucceeds(
    tenantDb.collection("deposit_settlements").doc("ds_lease_handover").update({
      status: "DISPUTED",
      tenantResponseNote: "Unit was left spotless on handover.",
      updatedAt: pastDate(250),
    })
  );

  // Landlord lowers deduction & re-issues
  await assertSucceeds(
    settlementRef.update({
      deductions: [{ id: "ded_1", category: "CLEANING", description: "Window track cleaning", amountCents: 100000 }],
      deductionsTotalCents: 100000,
      netRefundCents: 2900000,
      balanceOwedCents: 0,
      status: "ISSUED",
      updatedAt: pastDate(200),
    })
  );

  // Tenant approves updated statement
  await assertSucceeds(
    tenantDb.collection("deposit_settlements").doc("ds_lease_handover").update({
      status: "APPROVED",
      tenantResponseNote: "Agreed.",
      updatedAt: pastDate(150),
    })
  );

  // Settling with netRefundCents > 0 without refundReferenceCode fails
  await assertFails(
    settlementRef.update({
      status: "SETTLED",
      refundMethod: "MPESA",
      settledByUid: LANDLORD_UID,
      settledAtEpochMs: Date.now(),
      updatedAt: pastDate(100),
    })
  );

  // Settling with valid payout method and reference code succeeds
  await assertSucceeds(
    settlementRef.update({
      status: "SETTLED",
      refundMethod: "MPESA",
      refundReferenceCode: "UBL9K22M88",
      settledByUid: LANDLORD_UID,
      settledAtEpochMs: Date.now(),
      updatedAt: pastDate(50),
    })
  );

  // Once SETTLED, document is locked and immutable
  await assertFails(
    settlementRef.update({
      deductionsTotalCents: 200000,
      netRefundCents: 2800000,
      updatedAt: pastDate(10),
    })
  );
  await assertFails(
    tenantDb.collection("deposit_settlements").doc("ds_lease_handover").update({
      status: "DISPUTED",
      tenantResponseNote: "Cannot reopen",
      updatedAt: pastDate(10),
    })
  );
});





