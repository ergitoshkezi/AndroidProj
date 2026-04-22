---
phase: 01-identity-module
plan: D
subsystem: ui/identity
tags: [auth, login, registration, session, allergens, security]
dependency_graph:
  requires: [01-A, 01-B, 01-C]
  provides: [LoginScreen with session save, RegistrationScreen with nome/cognome/allergens/session save]
  affects: [AuthScreens.kt]
tech_stack:
  added: []
  patterns: [SessionManager.saveSession on auth success, DatabaseError.NETWORK_ERROR distinction, AllergeneChipSelector wired to RegistrationScreen]
key_files:
  modified:
    - app/src/main/java/com/example/ingredient/AuthScreens.kt
decisions:
  - sessionManager instantiated inside composable (val sessionManager = SessionManager(context)) — no ViewModel needed
  - allergeni stored as List<String> via selectedAllergens.map { it.name } per schema spec
  - AllergeneChipSelector call spans two lines (separate selected= and onSelectionChange= params) — correct Kotlin style
metrics:
  duration: ~20 minutes
  completed: 2025-07-14
  tasks_completed: 6
  files_modified: 1
---

# Phase 1 Plan D: Auth Screen Extensions Summary

**One-liner:** Extended LoginScreen (session save, network error distinction) and RegistrationScreen (nome/cognome fields, AllergeneChipSelector, full Firebase schema write with session save) — RNF-SEC-001 confirmed clean.

## What Was Built

### Task 1 — Security Verification (RNF-SEC-001)
Audited all `Log.d` / `Log.e` calls in `AuthScreens.kt`. Result: **no password variable in any log call**. 6 log lines confirmed safe (userId/error.message only).

### Task 2 — LoginScreen Improvements
- Added `val sessionManager = SessionManager(context)` inside `LoginScreen`
- Added `sessionManager.saveSession(userId ?: "", userType)` **before** `onLoginSuccess(...)` call
- Improved `onCancelled` error: distinguishes `DatabaseError.NETWORK_ERROR` / `DISCONNECTED` → `"Connection error. Check your internet."` vs generic `"Database error: ${error.message}"`
- Import added: `import com.example.ingredient.model.SessionManager`
- `DatabaseError` import already present

### Task 3 — RegistrationScreen nome + cognome fields
- Added `var nome by remember { mutableStateOf("") }` and `var cognome by remember { mutableStateOf("") }` state variables
- Added `OutlinedTextField` for Nome (before Email) and Cognome (after Nome, before Email)
- Field order: **Nome → Cognome → Email → Password → Confirm Password**
- Blank validation updated: `nome.isBlank() || cognome.isBlank() || email.isBlank() || ...`

### Task 4 — AllergeneChipSelector in RegistrationScreen
- Added `var selectedAllergens by remember { mutableStateOf<List<AllergeneType>>(emptyList()) }`
- Inserted `AllergeneChipSelector(selected = selectedAllergens, onSelectionChange = { ... })` after confirmPassword, before "I am a:" user type selector
- Label: `Text("Allergens (optional):")` 
- **Allergens are optional** — not included in blank validation

### Task 5 — Firebase schema + session save in RegistrationScreen
- Added `val sessionManager = SessionManager(context)` in `RegistrationScreen`
- Updated `userData` map to include all schema fields: `nome`, `cognome`, `email`, `password`, `userType`, `allergeni` (via `.map { it.name }`), `createdAt`
- Fixed duplicate-email error message: `"Email already in use"` (was `"Email already registered"`)
- Added `sessionManager.saveSession(userId, selectedUserType)` before `onRegisterSuccess`

### Task 6 — IngredientApp callbacks verification
- `onLoginSuccess = { userId, type -> }` confirmed at MainActivity.kt line 100
- `onRegisterSuccess = { userId, type -> }` confirmed at MainActivity.kt line 110
- `compileDebugKotlin`: **BUILD SUCCESSFUL** (exit 0)

## Build Status

`./gradlew :app:compileDebugKotlin` → **BUILD SUCCESSFUL** (exit 0, 3s)

## Security Check Results (RNF-SEC-001)

```
grep "Log\." AuthScreens.kt | grep -i password → NO RESULTS
```

All 6 Log calls verified safe:
- Line 128: `Log.d("LoginScreen", "Login successful for user: $userId")` — userId only
- Line 138: `Log.e("LoginScreen", "Login failed - no matching credentials")` — no variable
- Line 150: `Log.e("LoginScreen", "Database error: ${error.message}")` — error.message only
- Line 369: `Log.d("RegistrationScreen", "User registered: $userId")` — userId only
- Line 372: `Log.e("RegistrationScreen", "Registration error", e)` — exception only
- Line 381: `Log.e("RegistrationScreen", "Database error: ${error.message}")` — error.message only

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 — Security verify | 701e63f | fix(01-D): verify no password in Log calls (RNF-SEC-001) |
| 2 — LoginScreen | afdfbe1 | feat(01-D): LoginScreen error messages + session save |
| 3 — nome/cognome | 6c89051 | feat(01-D): RegistrationScreen add nome+cognome fields |
| 4 — AllergeneChipSelector | 96ac3ba | feat(01-D): RegistrationScreen add AllergeneChipSelector |
| 5 — Firebase schema | e173d5d | feat(01-D): RegistrationScreen Firebase schema + session save |
| 6 — Callback verify | 5bdbd3c | fix(01-D): verify IngredientApp callbacks + build check |

## Deviations from Plan

### AllergeneChipSelector call format (cosmetic)
- **Found during:** Task 4 / success criteria check
- **Issue:** Plan success criterion specified `AllergeneChipSelector(selected = selectedAllergens` as a single-line grep pattern. The actual call spans two lines (Kotlin multi-line argument style).
- **Fix:** None needed — code is functionally identical and more readable. The grep criterion was for verification only; actual code at lines 262-265 is correct.
- **Impact:** Zero — cosmetic formatting difference only.

No other deviations from plan.

## Known Stubs

None — all data fields wired. Session save functional. Firebase write includes full schema.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| No new threats | AuthScreens.kt | All changes are within existing trust boundaries identified in plan threat model. Password still stored as plaintext (T-01-D-02, accepted). Login still reads all users/ (T-01-D-04, accepted). |

## Self-Check: PASSED

- `AuthScreens.kt` contains `sessionManager.saveSession(userId ?: "", userType)` ✓ (line 132)
- `AuthScreens.kt` contains `"Connection error. Check your internet."` ✓ (line 147)
- `AuthScreens.kt` contains `var nome by remember` ✓ (line 182)
- `AuthScreens.kt` contains `var cognome by remember` ✓ (line 183)
- `AuthScreens.kt` contains `AllergeneChipSelector(` ✓ (line 262)
- `AuthScreens.kt` contains `"nome" to nome` ✓ (line 350)
- `AuthScreens.kt` contains `"allergeni" to selectedAllergens.map { it.name }` ✓ (line 355)
- `AuthScreens.kt` contains `"Email already in use"` ✓ (line 341)
- `AuthScreens.kt` contains `sessionManager.saveSession(userId, selectedUserType)` ✓ (line 364)
- All 6 task commits verified in git log ✓
- Build successful ✓
