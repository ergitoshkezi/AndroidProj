# Ingredient App — Requirements

**Version:** 1.0 | **Status:** APPROVED | **Date:** 2026-04-22
**Source:** design-brief.md + tasks/REQUIREMENTS.md (Identity Module) + approved design doc

---

## REQ-ID-001 — Disclaimer Screen (First Run)

**Priority:** P1 | **Phase:** 1
**Description:** On first launch, show full-screen disclaimer before any other screen. User must tap "I understand". No skip, back = close app.

**Acceptance Criteria:**
- Fresh install → DisclaimerScreen shown first
- Tap "I understand" → saves `disclaimer_accepted=true` in SharedPreferences `ingredient_session`, navigates to LoginScreen
- Second launch → DisclaimerScreen NOT shown
- Back on DisclaimerScreen → app closes

---

## REQ-ID-002 — Login

**Priority:** P1 | **Phase:** 1
**Description:** Email + password login via Firebase RTDB query. Save session on success.

**Acceptance Criteria:**
- Valid credentials → session saved, navigate to ClienteScreen or RistoratoreScreen
- Wrong password → "Invalid email or password"
- Firebase offline → "Connection error. Check your internet."
- Blank fields → "Please fill in all fields" (no Firebase call)

---

## REQ-ID-003 — Registration

**Priority:** P1 | **Phase:** 1
**Description:** Register with nome, cognome, email, password, confirmPassword, userType, optional allergens.

**Acceptance Criteria:**
- All valid → Firebase node created, session saved, navigate to correct screen
- password != confirmPassword → "Passwords do not match"
- email in use → "Email already in use"
- Empty allergens → allowed, written as `[]`

---

## REQ-ID-004 — Allergen Chip Selector Component

**Priority:** P1 | **Phase:** 1
**Description:** Reusable chip selector for all 14 EU allergens. Used in Registration + ProfileScreen.

**Acceptance Criteria:**
- All 14 chips visible, toggleable
- Pre-populated for edit scenarios

---

## REQ-ID-005 — Profile Screen

**Priority:** P1 | **Phase:** 1
**Description:** 3rd tab in ClienteScreen. Loads user data, shows nome/cognome/email read-only, allergens editable. Save writes to Firebase.

**Acceptance Criteria:**
- Loads within 3s on 4G
- nome/cognome/email read-only
- Allergen chips editable, save → Firebase updated + Snackbar

---

## REQ-ID-006 — Session Persistence

**Priority:** P1 | **Phase:** 1
**Description:** SharedPreferences `ingredient_session` persists userId and userType. App resumes session on relaunch.

**Acceptance Criteria:**
- Login → close → reopen → ClienteScreen shown without re-login
- Session survives OS kill

---

## REQ-ID-007 — Logout

**Priority:** P1 | **Phase:** 1
**Description:** Logout button clears session and navigates to LoginScreen with clean back stack.

**Acceptance Criteria:**
- Logout → LoginScreen shown, back → app closes
- session_user_id and session_user_type cleared

---

## Non-Functional

| ID | Requirement | Phase |
|---|---|---|
| RNF-SEC-001 | No password in Log calls | 1 |
| RNF-SEC-002 | App never reads `users/` except for login, own profile load, own profile update | 1 |
| RNF-PERF-001 | Login completes in < 3s on 4G | 1 |
| RNF-PRIVACY-001 | Allergen consent via DisclaimerScreen (GDPR Art. 9) | 1 |

---

## Future Requirements (Phase 2+)

| ID | Requirement | Phase |
|---|---|---|
| REQ-SEARCH-001 | Search dishes by ingredient list | 2 |
| REQ-SEARCH-002 | Filter by country/region | 2 |
| REQ-SEARCH-003 | Filter by rating, distance, price | 2 |
| REQ-SEARCH-004 | Fix O(n) user scan in performSearch() and fetchOffers() | 2 |
| REQ-OFFERS-001 | Daily offers page showing nearby discounted dishes | 3 |
| REQ-REST-001 | Restaurant owner: upload menu via OCR | 4 |
| REQ-REST-002 | Restaurant owner: edit dish price | 4 |
| REQ-REST-003 | Fix FirebaseMenuUploader schema mismatch | 4 |
