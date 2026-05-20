# TODOS.md — Ingredient App
Last updated: 2026-04-22

Format: [priority] [module] — description
Priority: P1=must do | P2=should do | P3=nice

---

## Identity Module

- [P1] [identity] Write unit tests for `AllergeneType.fromStringList`:
  - Valid names → correct enum list returned
  - Unknown/old free-text value → silently dropped, no crash (safety-critical)
  - null input → empty list
  Reason: This function drives ⚠️ allergen warnings. A bug here can fail to warn allergic users.
  When: Write alongside AllergeneType implementation. File: `AllergeneTypeTest.kt`

- [P1] [identity] Write unit/instrumented tests for `SessionManager`:
  - saveSession → SharedPreferences contains correct userId + userType
  - readSession → returns UserSession when values present
  - readSession → returns null when empty
  - clearSession → userId/userType cleared; disclaimer_accepted NOT cleared
  When: Write alongside SessionManager implementation.

- [P2] [identity] Firebase Auth migration (v2):
  Replace manual RTDB password query with Firebase Authentication.
  Enables: proper security rules, password hashing, reset-by-email, social login.
  Trigger: when first paying users or when Firebase rules need to be deployed.
  Context: Currently passwords are stored in plaintext in RTDB. Accepted risk for v1 prototype.

- [P2] [identity] GDPR account deletion (v2 — right to erasure, Art. 17):
  Add "Delete account" option in ProfileScreen. Must delete `users/{userId}` from RTDB
  and clear local session. v2 with Firebase Auth: also delete Firebase Auth account.
  Trigger: Before any public launch or EU user onboarding.

- [P2] [identity] GDPR privacy policy (v2):
  Add a formal privacy policy link in DisclaimerScreen or RegistrationScreen.
  Required before collecting health data (allergeni) from real users.
  Context: DisclaimerScreen serves as consent gate for v1 but is not a proper GDPR privacy policy.

---

## Search Module

- [P1] [search] Migrate performSearch() off users/ scan → query /dishes directly
  Currently: downloads ALL user data to do a search. Security + performance landmine.
  Fix: `databaseReference.child("dishes").addListenerForSingleValueEvent(...)`
  Tracked here because it's a security issue, not just perf.

- [P1] [search] Same fix for fetchOffers() — also does users/ scan

- [P2] [search] Refactor parseDish(DataSnapshot) → Dish.fromSnapshot() factory
  DRY violation: same parsing logic in performSearch() and fetchOffers().
  When: during or after Search module migration to /dishes schema.

---

## Schema Migration

- [P1] [schema] Migrate MenuItem → Dish:
  - price: String → Double
  - allergens: String → List<String> (AllergeneType enum values)
  - Add: cucina, regione, paese, offerta: Boolean, prezzoOfferta: Double?
  - Rename: country→paese, region→regione, isOffer→offerta, originalPrice→prezzoOfferta
  - Add denormalized fields: restaurantId, restaurantName, restaurantLat, restaurantLon

- [P1] [schema] Update FirebaseMenuUploader to write to /dishes/{dishId}
  Currently writes to users/{id}/menu/ (old schema).
  Dual-write during migration window if needed.

---

## Infrastructure

- [P3] [infra] Firebase Crashlytics (v2):
  When added: ensure crash reports do NOT include allergeni field values.
  Health data must not appear in telemetry payloads.

- [P2] [infra] Firebase RTDB → Firestore migration at ~500 dishes:
  RTDB has no server-side array-contains query. At 500 dishes, client-side filtering
  will download ~100KB+ per search. Firestore supports array-contains natively.
  Design: wrap all Firebase reads behind a DishRepository interface so the migration
  is a 1-file swap.
  Trigger: when dish count approaches 500 or search latency noticeably degrades.
