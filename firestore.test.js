const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated users cannot read or write any SpendSense collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("expenses").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("expenses").doc("exp_1").set({
      userId: ALICE_UID,
      title: "Coffee",
      merchant: "Blue Tokai",
      amount: 250.0,
      category: "Food & Dining",
      paymentMethod: "UPI",
      notes: "",
      dateEpochMillis: Date.now() - 5000,
      autoCategorized: true,
      demoRecord: false,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Authenticated owner: valid UserProfile create & partial update succeed; ghost fields & tampering fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      currencyCode: "INR",
      monthlyIncome: 85000.0,
      monthlyBudgetTarget: 40000.0,
      demoModeActive: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid partial update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      monthlyIncome: 95000.0,
      monthlyBudgetTarget: 45000.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field / shadow update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Authenticated owner: valid Expense create, category edit update, list & delete succeed; invalid category/amount/@DocumentId fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Empty subcollection list must succeed
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("expenses").get());

  // Valid create (no @DocumentId `id` field)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("expenses").doc("exp_101").set({
      userId: ALICE_UID,
      title: "Zomato Dinner",
      merchant: "Zomato",
      amount: 640.5,
      category: "Food & Dining",
      paymentMethod: "UPI",
      notes: "Team dinner",
      dateEpochMillis: Date.now() - 60000,
      autoCategorized: true,
      demoRecord: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid partial update (correcting category)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("expenses").doc("exp_101").update({
      category: "Entertainment",
      autoCategorized: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject future timestamp (request.time check)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("expenses").doc("exp_future").set({
      userId: ALICE_UID,
      title: "Future item",
      merchant: "Store",
      amount: 100.0,
      category: "Shopping",
      paymentMethod: "Card",
      notes: "",
      dateEpochMillis: Date.now(),
      autoCategorized: false,
      demoRecord: false,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Reject @DocumentId `id` key in write payload
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("expenses").doc("exp_bad_id").set({
      id: "exp_bad_id",
      userId: ALICE_UID,
      title: "Bad payload",
      merchant: "Store",
      amount: 100.0,
      category: "Shopping",
      paymentMethod: "Card",
      notes: "",
      dateEpochMillis: Date.now() - 1000,
      autoCategorized: false,
      demoRecord: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject invalid category
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("expenses").doc("exp_bad_cat").set({
      userId: ALICE_UID,
      title: "Invalid Cat",
      merchant: "Store",
      amount: 100.0,
      category: "CryptoGambling",
      paymentMethod: "Card",
      notes: "",
      dateEpochMillis: Date.now() - 1000,
      autoCategorized: false,
      demoRecord: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject negative/zero amount
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("expenses").doc("exp_zero").set({
      userId: ALICE_UID,
      title: "Zero",
      merchant: "Store",
      amount: 0.0,
      category: "Shopping",
      paymentMethod: "Card",
      notes: "",
      dateEpochMillis: Date.now() - 1000,
      autoCategorized: false,
      demoRecord: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("expenses").doc("exp_101").delete()
  );
});

test("Authenticated owner: budgets & category_rules CRUD succeed; cross-user isolation enforced", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("budgets").doc("food_dining").set({
      userId: ALICE_UID,
      category: "Food & Dining",
      monthlyLimit: 8000.0,
      alertThresholdPct: 80,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("category_rules").doc("rule_zomato").set({
      userId: ALICE_UID,
      keyword: "zomato",
      category: "Food & Dining",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Bob cannot read, list, create, update, or delete Alice's data
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("expenses").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("budgets").doc("food_dining").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("budgets").doc("food_dining").update({
      monthlyLimit: 1.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("category_rules").doc("rule_zomato").delete()
  );
});
