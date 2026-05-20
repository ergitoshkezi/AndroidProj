# REQUIREMENTS.md — Identity Module
Version: 1.0 | Status: APPROVED | Date: 2026-04-22

Source: design-brief.md + approved design doc (2026-04-22) + CEO Review (HOLD SCOPE)

Priority: P1 = must ship v1 | P2 = important but deferrable | P3 = nice-to-have

---

## REQ-ID-001 — Disclaimer Screen (First Run)

**Priority:** P1
**Type:** Functional / Legal

**Description:**
On first app launch (fresh install or cleared app data), show a full-screen disclaimer
before any other screen. The user must tap "I understand" to proceed. No skip button,
no back navigation (back = close the app).

**Trigger:** `disclaimer_accepted` key absent or `false` in SharedPreferences `ingredient_session`.

**Disclaimer text (exact):**
> "This app is a support tool only. Always verify ingredients and allergen information
> directly with the restaurant before ordering. We do not guarantee the accuracy of
> the data shown in this app."

**Acceptance Criteria:**
- AC-001-1: Fresh install → DisclaimerScreen is the first screen shown
- AC-001-2: Tap "I understand" → saves `disclaimer_accepted = true` in SharedPreferences, navigates to LoginScreen
- AC-001-3: Second launch → DisclaimerScreen NOT shown; goes directly to LoginScreen (or ClienteScreen if session active)
- AC-001-4: Back button on DisclaimerScreen → app closes (does not navigate backward)
- AC-001-5: DisclaimerScreen has NO bottom navigation bar

---

## REQ-ID-002 — Login

**Priority:** P1
**Type:** Functional

**Description:**
User enters email + password. App queries `users/` node in Firebase RTDB, finds a
matching `email`+`password` pair, reads `userType`, saves session, and navigates.

**Current state:** `LoginScreen` in `AuthScreens.kt` exists and works. Needs: proper
error messages for all failure cases (currently `onCancelled` message is generic).

**Validation (client-side, before Firebase call):**
- email not blank
- password not blank

**Acceptance Criteria:**
- AC-002-1: Valid credentials → session saved, navigate to ClienteScreen (userType=Cliente) or RistoratoreScreen (userType=Ristoratore) in < 3 seconds on 4G
- AC-002-2: Wrong password → message "Invalid email or password" visible, spinner stopped
- AC-002-3: Firebase offline / timeout → message "Connection error. Check your internet." visible
- AC-002-4: Blank fields → message "Please fill in all fields" shown WITHOUT calling Firebase
- AC-002-5: Password field shows bullets (PasswordVisualTransformation) — already implemented, verify it persists

---

## REQ-ID-003 — Registration

**Priority:** P1
**Type:** Functional

**Description:**
New user provides nome, cognome, email, password, confirmPassword, userType, and
optionally selects allergens from the 14-EU chip grid. App writes a new node to
`users/{pushId}` and starts a session.

**Current gap:** `RegistrationScreen` in `AuthScreens.kt` is missing `nome`, `cognome`,
and the allergen chip selector. These must be added without rewriting the screen.

**Fields:**

| Field | Type | Required | Validation |
|---|---|---|---|
| nome | String | YES | non-blank |
| cognome | String | YES | non-blank |
| email | String | YES | non-blank, basic format check |
| password | String | YES | min 6 chars |
| confirmPassword | String | YES | must equal password |
| userType | "Cliente" / "Ristoratore" | YES | one of two values |
| allergeni | List\<String\> | NO | values from AllergeneType.name enum |

**Firebase write schema:**
```
users/{pushId}/
  nome:         String
  cognome:      String
  email:        String
  password:     String   ← PLAINTEXT v1, accepted risk
  userType:     String   ("Cliente" | "Ristoratore")
  allergeni:    List<String>  (e.g. ["GLUTINE", "LATTE"])
  createdAt:    Long     (System.currentTimeMillis())
```

**Acceptance Criteria:**
- AC-003-1: All required fields valid → Firebase node created with all fields above; session saved; navigation to correct screen
- AC-003-2: password != confirmPassword → error "Passwords do not match" shown WITHOUT calling Firebase
- AC-003-3: email already used by another account → error "Email already in use"
- AC-003-4: allergeni empty list (no selection) → allowed; `allergeni` written as empty list `[]`
- AC-003-5: allergeni selected (e.g. Glutine, Latte) → written as `["GLUTINE", "LATTE"]` in Firebase
- AC-003-6: nome or cognome blank → error "Please fill in all fields"
- AC-003-7: Firebase offline → error message shown, no crash

---

## REQ-ID-004 — Allergen Chip Selector Component

**Priority:** P1
**Type:** Functional / UI Component

**Description:**
A reusable Compose component showing all 14 EU allergens as toggleable chips. Used in
both RegistrationScreen and ProfileScreen. Selected chips are visually distinct
(filled/tinted). Returns `List<AllergeneType>` on change.

**14 EU allergens (exact display names):**
Glutine, Crostacei, Uova, Pesce, Arachidi, Soia, Latte, Frutta a guscio,
Sedano, Senape, Sesamo, Anidride solforosa e solfiti, Lupini, Molluschi

**Component signature:**
```kotlin
@Composable
fun AllergeneChipSelector(
    selected: List<AllergeneType>,
    onSelectionChange: (List<AllergeneType>) -> Unit,
    modifier: Modifier = Modifier
)
```

**Acceptance Criteria:**
- AC-004-1: All 14 allergens visible as chips in a FlowRow or LazyVerticalGrid
- AC-004-2: Tap chip → toggles selection state, calls onSelectionChange
- AC-004-3: Pre-populated selection shown correctly (for ProfileScreen use)
- AC-004-4: Component works with zero selected chips (empty state)
- AC-004-5: Component scrollable if chips exceed screen height

---

## REQ-ID-005 — Profile Screen

**Priority:** P1
**Type:** Functional

**Description:**
Third tab in ClienteScreen's bottom navigation. Loads user data from Firebase, shows
nome/cognome/email as read-only, and allergen chips as editable. "Save" writes updated
allergens back to Firebase.

**Access:** Available only to logged-in users of userType="Cliente".

**Load flow:**
1. Read `users/{userId}` from Firebase (single value event)
2. Show loading indicator while waiting
3. Populate fields from snapshot

**Save flow:**
1. Write `users/{userId}/allergeni` with updated list
2. Show "Saved!" Snackbar on success
3. Show "Could not save. Try again." on failure

**Acceptance Criteria:**
- AC-005-1: Tab loads and shows correct user data within 3 seconds on 4G
- AC-005-2: nome, cognome, email are displayed as non-editable Text (not OutlinedTextField)
- AC-005-3: Allergen chips pre-populated from Firebase data
- AC-005-4: Tap Save → Firebase `allergeni` updated; Snackbar "Saved!" shown
- AC-005-5: Reopen app → ProfileScreen shows updated allergens
- AC-005-6: Firebase load failure → error message "Could not load profile" shown
- AC-005-7: Save failure → error message shown; user can retry

---

## REQ-ID-006 — Session Persistence

**Priority:** P1
**Type:** Non-Functional / UX

**Description:**
After login or registration, save `userId` and `userType` to SharedPreferences file
`ingredient_session`. On app launch, read this file. If `session_user_id` is non-empty,
skip Login and navigate directly to the appropriate home screen.

**SharedPreferences file:** `ingredient_session`

| Key | Type | Description |
|---|---|---|
| `session_user_id` | String | userId from Firebase; empty = not logged in |
| `session_user_type` | String | "Cliente" or "Ristoratore"; empty = not logged in |
| `disclaimer_accepted` | Boolean | Whether disclaimer has been accepted |

**Acceptance Criteria:**
- AC-006-1: Login → close app → reopen → ClienteScreen shown, no re-login required
- AC-006-2: Register → close app → reopen → ClienteScreen shown
- AC-006-3: App killed by OS (low memory) → reopen → session still active
- AC-006-4: Disclaimer flag persists independently of login session

---

## REQ-ID-007 — Logout

**Priority:** P1
**Type:** Functional

**Description:**
A "Logout" button (visible somewhere accessible to the logged-in user, e.g. in
ProfileScreen or as a top bar action) clears SharedPreferences session keys and
navigates to LoginScreen with a clean back stack.

**Acceptance Criteria:**
- AC-007-1: Tap Logout → LoginScreen shown
- AC-007-2: Back button after logout → app closes (ClienteScreen NOT in stack)
- AC-007-3: `session_user_id` and `session_user_type` are empty in SharedPreferences after logout
- AC-007-4: Logging back in as a different user creates a new valid session

---

## Non-Functional Requirements

### RNF-SEC-001 — No password logging
Password value must NEVER be written to `Log.d`, `Log.e`, or any Timber/logging call.
**Acceptance Criteria:** grep `password` in `AuthScreens.kt` — no Log calls include the value variable.

### RNF-SEC-002 — Scope of RTDB reads
The customer-side app must NOT read `users/` for any purpose other than:
(a) login credential check, (b) own-profile load, (c) own-profile update.
All other `users/` reads (e.g. the ones in `performSearch()` and `fetchOffers()`)
must be migrated to query `/dishes` directly (tracked separately in Search module).

### RNF-PERF-001 — Login response time
Login must complete (success or failure message visible) within 3 seconds on 4G.
Firebase `onCancelled` must fire within 10 seconds (Firebase default timeout is acceptable).

### RNF-PRIVACY-001 — GDPR allergen consent
`allergeni` is health data under GDPR Art. 9. Collection requires explicit, informed
consent. The DisclaimerScreen (REQ-ID-001) serves as consent gate for v1.
v2 must add a formal privacy policy link and explicit consent checkbox.

---

## Open Issues

| ID | Issue | Status |
|---|---|---|
| OI-001 | Firebase project region: confirm `europe-west1` is set if EU data residency required | Open |
| OI-002 | DisclaimerScreen legal text: needs final review by product owner before shipping | Open |
| OI-003 | v2 Firebase Auth migration: password hashing + security rules | TODOS |
| OI-004 | v2 Account deletion (GDPR Art. 17) | TODOS |
| OI-005 | Email uniqueness check: current approach queries all users — O(n). Acceptable for < 500 users. | Accepted risk v1 |
