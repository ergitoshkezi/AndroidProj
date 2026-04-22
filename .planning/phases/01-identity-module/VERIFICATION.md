---
phase: 01-identity-module
verified: 2025-01-31T00:00:00Z
status: gaps_found
score: 9/11 requirements verified (2 partial/fail)
overrides_applied: 0
gaps:
  - truth: "Allergens (and password) stored with privacy protection"
    status: partial
    reason: "Allergens stored as plain-text enum names in Firebase RTDB; password stored as plain text. RNF-PRIV-001 allows plain storage IF decision is documented — no such documentation exists in code or comments."
    artifacts:
      - path: "app/src/main/java/com/example/ingredient/AuthScreens.kt"
        issue: "Line 353: password written to RTDB as plain string. Line 355: allergeni stored as plain list of strings. No encryption or decision comment."
    missing:
      - "Either add encryption/hashing for password (critical) and allergens, OR add a documented comment/ADR explaining the plain-storage decision for allergens."

  - truth: "Network errors give a user-friendly, distinguishing message"
    status: partial
    reason: "LoginScreen correctly distinguishes network vs DB errors (DatabaseError.NETWORK_ERROR / DISCONNECTED → friendly message). RegistrationScreen.onCancelled does NOT — it shows generic 'Database error: <message>' for all failures."
    artifacts:
      - path: "app/src/main/java/com/example/ingredient/AuthScreens.kt"
        issue: "Line 378-381: RegistrationScreen.onCancelled returns 'Database error: ${error.message}' without checking error.code for NETWORK_ERROR / DISCONNECTED."
    missing:
      - "Apply the same error-code check already present in LoginScreen to RegistrationScreen.onCancelled (lines 378-381)."

human_verification:
  - test: "DisclaimerScreen first-launch flow"
    expected: "Fresh install (or after clearing app data) shows DisclaimerScreen before Login; subsequent launches go straight to Login (or home if session active)"
    why_human: "Cannot simulate fresh-install SharedPreferences state programmatically in a static code review"

  - test: "RegistrationScreen scrollability"
    expected: "On small screen the full registration form (nome, cognome, email, password, confirm, allergen chips, user-type row, register button) is scrollable and fully accessible"
    why_human: "UI layout/overflow is a runtime concern — the Column uses no vertical scroll modifier"
---

# Phase 1: Identity Module — Verification Report

**Phase Goal:** Implement customer Identity Module — profile (nome, cognome, email, password, allergeni), disclaimer, login/registration, session persistence, profile screen with allergen editing and logout.
**Verified:** 2025-01-31
**Status:** PHASE INCOMPLETE — 2 gaps, 2 human-verification items
**Re-verification:** No — initial verification

---

## 1. Requirements Coverage

| ID | Requirement | Status | Evidence |
|----|-------------|--------|----------|
| REQ-ID-001 | User profile: nome, cognome, email, password, allergeni (14 EU types) | ✅ PASS | `User.kt`: all 5 fields present; `AllergeneType.kt`: 14 enum entries |
| REQ-ID-002 | Registration with email/password via Firebase Auth or RTDB | ✅ PASS | `AuthScreens.kt` line 359: `setValue(userData)` writes to Firebase RTDB under `users/{userId}` |
| REQ-ID-003 | Login with email/password | ✅ PASS | `AuthScreens.kt` lines 117–153: iterates RTDB users, matches email+password |
| REQ-ID-004 | Session persistence via SharedPreferences | ✅ PASS | `SessionManager.kt` lines 10–14: `putString("session_user_id", …).apply()` |
| REQ-ID-005 | Disclaimer screen on first launch | ✅ PASS | `MainActivity.kt` line 57: `!sessionManager.isDisclaimerAccepted()` → `"disclaimer"` screen |
| REQ-ID-006 | AllergeneChipSelector: multi-select chip grid (14 allergens) | ✅ PASS | `AuthScreens.kt` lines 403–426: `AllergeneType.entries.forEach { … FilterChip … }` — all 14 rendered |
| REQ-ID-007 | ProfileScreen: read-only nome/cognome/email, editable allergens, logout | ✅ PASS | `ClienteScreens.kt` lines 663–711: Text fields (read-only) + AllergeneChipSelector + Logout button |
| RNF-SEC-001 | No password in logs | ✅ PASS | All `Log.*` calls in AuthScreens.kt log only userId or error messages — no password variable |
| RNF-PRIV-001 | Allergens encrypted/hashed or plain + documented decision | ⚠️ PARTIAL | Plain-text storage (allergen names as strings, password as string). No encryption; no decision comment. |
| RNF-UX-001 | Error messages distinguish network vs DB errors | ⚠️ PARTIAL | `LoginScreen.onCancelled` (line 146) checks `NETWORK_ERROR`/`DISCONNECTED` ✅. `RegistrationScreen.onCancelled` (line 378) does NOT ❌ |
| RNF-PERSIST-001 | Session survives app restart | ✅ PASS | `SharedPreferences.apply()` persists to disk; `MainActivity` line 56–59 restores session on startup |

**Requirements score: 9/11 (2 partial)**

---

## 2. Design-Brief Criteria (C1–C12)

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| C1 | User can register with nome, cognome, email, password, optional allergens | ✅ PASS | `RegistrationScreen` (lines 172–399): all fields present; allergens optional via `AllergeneChipSelector` |
| C2 | User can login and session is saved | ✅ PASS | `LoginScreen` lines 132–133: `sessionManager.saveSession(userId, userType)` on match |
| C3 | DisclaimerScreen shown on first install, not after | ✅ PASS | `MainActivity.kt` line 57 checks `isDisclaimerAccepted()`; `setDisclaimerAccepted()` called on accept |
| C4 | AllergeneChipSelector shows all 14 EU allergens | ✅ PASS | 14 entries in `AllergeneType.kt`; `AllergeneType.entries.forEach` in `AllergeneChipSelector` renders all |
| C5 | ProfileScreen loads user data from Firebase | ✅ PASS | `ClienteScreens.kt` lines 625–643: `LaunchedEffect(userId)` reads nome, cognome, email, allergeni from RTDB |
| C6 | Allergens can be updated in ProfileScreen and saved back | ✅ PASS | Lines 680–698: `setValue(selectedAllergens.map { it.name })` writes updated allergens to RTDB |
| C7 | Logout clears session and returns to LoginScreen | ✅ PASS | Lines 704–706: `sessionManager.logout()` + `onLogout()`; `MainActivity` sets `currentScreen = "Login"` |
| C8 | No password in any Log call | ✅ PASS | Verified all 6 `Log.*` calls in `AuthScreens.kt` — none include password value |
| C9 | Network error gives user-friendly message | ⚠️ PARTIAL | `LoginScreen` ✅ distinguishes network vs DB. `RegistrationScreen.onCancelled` ❌ shows raw `"Database error: ${error.message}"` |
| C10 | Session key `session_user_id` persists in SharedPreferences | ✅ PASS | `SessionManager.kt` line 12: `putString("session_user_id", userId)` |
| C11 | UserType stored in Firebase under `userType` field | ✅ PASS | `AuthScreens.kt` line 354: `"userType" to selectedUserType` in userData map |
| C12 | `disclaimer_accepted` preserved on logout | ✅ PASS | `SessionManager.logout()` (lines 23–27) removes only `session_user_id` and `session_user_type`; `disclaimer_accepted` untouched |

**Criteria score: 11/12 (C9 partial)**

---

## 3. Artifact Verification

| Artifact | Status | Details |
|----------|--------|---------|
| `model/AllergeneType.kt` | ✅ VERIFIED | 14 EU allergens as enum with `displayName` |
| `model/User.kt` | ✅ VERIFIED | All required fields: nome, cognome, email, password, userType, allergeni, createdAt |
| `model/SessionManager.kt` | ✅ VERIFIED | save/get/logout/disclaimer methods; uses SharedPreferences with `apply()` |
| `AuthScreens.kt` — `DisclaimerScreen` | ✅ VERIFIED | Full UI with accept button, `BackHandler` to finish activity |
| `AuthScreens.kt` — `LoginScreen` | ✅ VERIFIED | Full form, RTDB query, session save, network-aware error handling |
| `AuthScreens.kt` — `RegistrationScreen` | ⚠️ PARTIAL | Full form and RTDB write; `onCancelled` missing network/DB error distinction |
| `AuthScreens.kt` — `AllergeneChipSelector` | ✅ VERIFIED | `FlowRow` + `FilterChip` for all 14 allergens; multi-select toggle |
| `ClienteScreens.kt` — `ProfileScreen` | ✅ VERIFIED | Reads from Firebase; read-only fields; editable allergens; logout with session clear |
| `MainActivity.kt` — routing logic | ✅ VERIFIED | Disclaimer → Login → Cliente/Ristoratore routing wired; session restoration on startup |

---

## 4. Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `DisclaimerScreen` | `SessionManager.setDisclaimerAccepted()` | `onAccepted` lambda in `IngredientApp` | ✅ WIRED | `MainActivity.kt` line 92 |
| `LoginScreen` | Firebase RTDB `users/` | `ValueEventListener.onDataChange` | ✅ WIRED | `AuthScreens.kt` line 117 |
| `LoginScreen` | `SessionManager.saveSession()` | `onDataChange` on match | ✅ WIRED | `AuthScreens.kt` line 132 |
| `RegistrationScreen` | Firebase RTDB `users/{id}` | `setValue(userData)` | ✅ WIRED | `AuthScreens.kt` line 359 |
| `ProfileScreen` | Firebase RTDB `users/{userId}/allergeni` | `setValue(list)` | ✅ WIRED | `ClienteScreens.kt` line 684 |
| `ProfileScreen` → Logout | `SessionManager.logout()` + `onLogout()` | Button `onClick` | ✅ WIRED | `ClienteScreens.kt` lines 705–706 |
| `MainActivity` | session restore on restart | `SessionManager.isLoggedIn()` | ✅ WIRED | `MainActivity.kt` line 58 |
| `MainActivity` | disclaimer gate | `SessionManager.isDisclaimerAccepted()` | ✅ WIRED | `MainActivity.kt` line 57 |

---

## 5. Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `AuthScreens.kt` | 353 | `"password" to password` — plain-text write to RTDB | 🛑 Blocker (Security) | Passwords stored unencrypted in Firebase RTDB; any RTDB rules misconfiguration exposes all passwords |
| `AuthScreens.kt` | 355 | `"allergeni" to selectedAllergens.map { it.name }` — plain-text | ⚠️ Warning | Allergens stored as plain strings; RNF-PRIV-001 requires encryption or documented decision |
| `AuthScreens.kt` | 378–381 | `RegistrationScreen.onCancelled` no error-code distinction | ⚠️ Warning | Fails RNF-UX-001 / C9: user sees raw database error string on network failure during registration |
| `ClienteScreens.kt` | 191–399 | `RegistrationScreen` `Column` has no `verticalScroll` | ℹ️ Info | On small screens the registration form may be cut off / inaccessible (UI concern, human verification needed) |

---

## 6. Human Verification Required

### 1. DisclaimerScreen First-Launch Behaviour

**Test:** Clear app data (or fresh install), launch the app.
**Expected:** `DisclaimerScreen` appears. After tapping "I understand", app navigates to `LoginScreen`. Relaunch without clearing data — `DisclaimerScreen` must NOT appear again.
**Why human:** SharedPreferences state cannot be simulated in static code review.

### 2. RegistrationScreen Scrollability on Small Screens

**Test:** Open the Registration screen on a device with < 600 dp height.
**Expected:** The entire form (nome, cognome, email, password, confirm password, allergen chips, user-type row, register button) is accessible by scrolling.
**Why human:** `RegistrationScreen.Column` (line 191) uses no `verticalScroll` modifier — overflow is a runtime layout concern.

---

## 7. Gaps Summary

**2 gaps block full compliance:**

### Gap 1 — Plain-text password + allergen storage (RNF-PRIV-001) 🛑

**Root cause:** `AuthScreens.kt` writes `password` and `allergeni` directly to Firebase RTDB as plain strings (lines 353, 355). No encryption, no hashing, no documented decision.

**Remediation options (pick one):**
1. **(Recommended)** Hash passwords with BCrypt or Argon2 before storage; allergens can remain plain (low sensitivity) with a code comment documenting that decision.
2. Store passwords via Firebase Authentication instead of RTDB — eliminates plain-text password entirely.
3. At minimum: add a code comment in `RegistrationScreen` documenting the deliberate plain-storage decision so RNF-PRIV-001's "or note if plain + document decision" clause is satisfied.

### Gap 2 — RegistrationScreen missing network-error distinction (RNF-UX-001 / C9) ⚠️

**Root cause:** `RegistrationScreen.onCancelled` (line 378) returns `"Database error: ${error.message}"` unconditionally. `LoginScreen` already has the correct pattern at line 146.

**Fix (2 lines):** Replace `RegistrationScreen.onCancelled` body with:
```kotlin
errorMessage = if (error.code == DatabaseError.NETWORK_ERROR || error.code == DatabaseError.DISCONNECTED) {
    "Connection error. Check your internet."
} else {
    "Database error: ${error.message}"
}
```

---

## 8. Overall Verdict

```
┌─────────────────────────────────────────────────────────┐
│  PHASE 1: IDENTITY MODULE — PHASE INCOMPLETE            │
│                                                         │
│  Requirements:  9 / 11 PASS  (2 partial)                │
│  Criteria:     11 / 12 PASS  (C9 partial)               │
│  Blockers:      1  (plain-text password in RTDB)        │
│  Warnings:      1  (RegistrationScreen network error)   │
│  Human checks:  2  (first-launch UI, scroll overflow)   │
└─────────────────────────────────────────────────────────┘

Core identity flows (register, login, disclaimer, session,
profile editing, logout) are fully wired and functional.

Two targeted fixes close all automated gaps. Human
verification of first-launch and scroll behaviour
required before marking COMPLETE.
```

---

_Verified: 2025-01-31_
_Verifier: gsd-verifier (automated)_
