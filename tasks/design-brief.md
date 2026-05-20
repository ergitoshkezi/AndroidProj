# Design Brief — Identity Module
Version: 1.0 | Status: APPROVED | Date: 2026-04-22
Source: office-hours (design doc approved) + /plan-ceo-review (HOLD SCOPE mode)

---

## Scope approvato

**In scope (v1):**
- `DisclaimerScreen`: one-time mandatory legal screen on first launch; requires tap "I understand" before any navigation
- `LoginScreen`: manual RTDB auth (email + password query, plaintext compare)
- `RegistrationScreen`: nome, cognome, email, password, confirmPassword, userType (Cliente/Ristoratore), allergeni (multi-select chip grid, 14 EU allergens)
- `ProfileScreen`: view/edit allergeni for Cliente; nome, cognome, email are read-only
- Session persistence: `userId` + `userType` survive process kill via SharedPreferences
- Logout: clears session, navigates to LoginScreen with clean back stack

**Explicitly out of scope (v2):**
- Firebase Authentication (password hashing, secure tokens)
- Password change or reset via email
- Social login (Google, Apple)
- Account deletion (GDPR right to erasure — deferred)
- Session expiry / token refresh

---

## Vincoli architetturali

From CEO Review and codebase analysis:

- Auth is manual (RTDB child("users") scan, plaintext password compare) — Firebase Auth is DEFERRED to v2
- Security rules that depend on Firebase Auth MUST NOT be deployed in v1
- `MenuItem.allergens: String` must become `List<String>` with values from the 14-EU enum
- `MenuItem.price: String` must become `Double`
- Session persisted in SharedPreferences (not ViewModel only) — survives OS process kill
- Minimal diff policy: extend `AuthScreens.kt`, do NOT rewrite it; do NOT introduce a repository layer in v1
- `parseDish(DataSnapshot)` must be refactored into `Dish.fromSnapshot()` factory in a future phase — not in Identity scope

---

## Requisiti non negoziabili

1. DisclaimerScreen is shown before LoginScreen on first install; no skip or back button available
2. Allergens are a closed list of exactly 14 EU official values (enum, not free text)
3. Allergen profile persists on Firebase under `users/{userId}/allergeni: List<String>`
4. Login failure shows a user-visible error message (not a silent spinner stop)
5. Registration validates `password == confirmPassword` client-side BEFORE any Firebase call
6. `nome` and `cognome` are mandatory registration fields (currently absent from `RegistrationScreen`)

---

## Scenari esclusi esplicitamente

- Auto-exclusion of dishes based on allergens: show ⚠️ warning only, never hide dishes (design decision, final)
- Login without nome/cognome: those fields must be in the registration form
- Profile editing of email or password: read-only in v1 (Firebase Auth required for safe mutation)
- Rating features: deferred to v2 (noted in CEO review TODOS)
- Any query that reads `users/` for a purpose other than own-profile load or login credential check

---

## Criteri di verifica

- [ ] C1: Register with nome="Mario", cognome="Rossi", email="mario@test.it", password="Test123!", allergeni=[Glutine, Latte] → Firebase node `users/{newId}` contains all fields with correct types
- [ ] C2: Login as that user → reopen app → ClienteScreen shown directly (no re-login)
- [ ] C3: Login with wrong password → message "Invalid email or password" visible on screen
- [ ] C4: Login with Firebase offline → message "Connection error. Check your internet." visible; spinner stops
- [ ] C5: First install → DisclaimerScreen appears; Search tab is NOT reachable before "I understand" tap
- [ ] C6: Second launch after accepting disclaimer → DisclaimerScreen does NOT appear
- [ ] C7: ProfileScreen shows nome/cognome as non-editable, allergeni as chip selector
- [ ] C8: Select Arachidi + Soia in profile → Save → reopen app → both chips are selected
- [ ] C9: Registration with password != confirmPassword → error shown, Firebase NOT called
- [ ] C10: Registration with already-used email → message "Email already in use"
- [ ] C11: Logout → tap Back → app closes (ClienteScreen NOT in back stack)
- [ ] C12: SharedPreferences cleared after logout (`session_user_id` is empty)

---

## Vincoli per dimensione

_Required: allergeni = health data under GDPR Art. 9 (special category personal data)._

### WHO — autorizzazione

- Profile data (`nome`, `cognome`, `email`, `allergeni`) is readable and writable only by the owning user
- No other user (Cliente or Ristoratore) should be able to read `users/{otherUserId}` via app logic
- **v1 ACCEPTED RISK:** Firebase RTDB has no security rules active (Firebase Auth is deferred). Anyone with Firebase Console access can read all user data. This is explicitly accepted and documented. Must be remediated in v2.
- Restaurant-side code (FirebaseMenuUploader) must NEVER read or write `users/{customerId}`
- No admin interface exists in v1 — no role can modify another user's allergeni via the app
- Audit trail of profile changes: NOT implemented in v1 (deferred)

### WHERE — residenza dati

- All identity data (`nome`, `cognome`, `email`, `password`, `allergeni`) resides in Firebase RTDB (Google infrastructure)
- **Verify Firebase project region in Firebase Console** — set to `europe-west1` if EU data residency is required
- `password` is stored in plaintext in RTDB v1 — this is accepted risk for a prototype. ZERO payment or sensitive documents stored alongside it. Must be migrated in v2.
- `userId` and `userType` are persisted in Android SharedPreferences (local device, not synced to cloud)
- `allergeni` = health data per GDPR Art. 9. Consent is obtained via DisclaimerScreen. v2 must add a proper privacy policy and explicit consent checkbox.
- No allergen data is transmitted to third-party analytics services in v1 (Firebase Crashlytics is in TODOS for v2 — when added, ensure it does NOT include allergen values in crash reports)

### WHEN — temporalità

- Local session (SharedPreferences) persists until explicit logout — no expiry in v1
- Firebase data: no retention policy in v1; accounts persist until manual deletion via Firebase Console
- DisclaimerScreen: shown once per app installation (`disclaimer_accepted` flag in SharedPreferences)
- v2: implement account deletion (GDPR right to erasure, Art. 17)
- v2: implement session expiry with token refresh

### EXCLUDED — cosa non deve esistere

- Password value must NEVER appear in `Log.d`, `Log.e`, or any logging statement
- Email must NOT be sent to analytics services in v1
- `userId` must NEVER be displayed in the UI to the end user
- No code path in the customer-side app may read `users/` for purposes other than: (a) login credential check, (b) own-profile load, (c) own-profile update
- No "list all users" API or screen must exist in the customer-side app
- Allergen values must NOT be included in crash report payloads when Crashlytics is added in v2
