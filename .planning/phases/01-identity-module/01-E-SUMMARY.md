---
phase: 01-identity-module
plan: E
subsystem: ui/identity
tags: [profile, allergens, logout, session, firebase, navigation]
dependency_graph:
  requires: [01-A, 01-C, 01-D]
  provides: [ProfileScreen composable, 3rd tab in ClienteScreen]
  affects: [ClienteScreens.kt]
tech_stack:
  added: []
  patterns: [addListenerForSingleValueEvent for one-time profile load, SessionManager.logout() before onLogout callback, AllergeneChipSelector reused cross-file in same package]
key_files:
  modified:
    - app/src/main/java/com/example/ingredient/ClienteScreens.kt
decisions:
  - SessionManager instantiated inside ProfileScreen composable via LocalContext.current (consistent with LoginScreen/RegistrationScreen pattern from Plan D)
  - AllergeneChipSelector called directly from ClienteScreens.kt — works because both files are in package com.example.ingredient (no import needed)
  - ProfileScreen receives userId and databaseReference from ClienteScreen parent (avoids re-reading session inside composable)
  - Save writes only users/{userId}/allergeni node (not full user node) to prevent overwriting other fields
metrics:
  duration: ~15 minutes
  completed: 2025-07-14
  tasks_completed: 4
  files_modified: 1
---

# Phase 1 Plan E: Profile Screen Summary

**One-liner:** ProfileScreen composable added as 3rd tab in ClienteScreen — loads user data from Firebase, shows nome/cognome/email read-only, allergens editable via AllergeneChipSelector, with Save (Snackbar feedback) and Logout functionality.

## What Was Built

### Task 1 — Required Imports
Added to `ClienteScreens.kt`:
- `import android.util.Log`
- `import androidx.compose.foundation.rememberScrollState`
- `import androidx.compose.foundation.verticalScroll`
- `import com.example.ingredient.model.AllergeneType`
- `import com.example.ingredient.model.SessionManager`
- `import kotlinx.coroutines.launch`

Note: `androidx.compose.material3.*` wildcard already covered `SnackbarHost`, `SnackbarHostState`, `OutlinedButton`, `CircularProgressIndicator`. `androidx.compose.runtime.*` wildcard already covered `rememberCoroutineScope`.

### Task 2 — Profile Tab in NavigationBar
- Added 3rd `NavigationBarItem` with `Icons.Default.Person` and label `"Profilo"`
- Added `2 -> ProfileScreen(userId ?: "", databaseReference, onLogout, currentModifier)` in `when(selectedTab)` block
- `ClienteScreen` already had `userId: String?`, `databaseReference: DatabaseReference`, and `onLogout: () -> Unit` parameters — no signature changes needed

### Task 3 — ProfileScreen Composable
Added at end of `ClienteScreens.kt`:
- `LaunchedEffect(userId)` → `addListenerForSingleValueEvent` reads `users/{userId}`
- Populates `nome`, `cognome`, `email` (read-only `Text` composables)
- `allergeni` parsed from Firebase list format → `AllergeneType.valueOf()` with `runCatching` for safe parsing
- `AllergeneChipSelector(selected = selectedAllergens, onSelectionChange = { ... })` for editable allergens
- Save button writes `users/{userId}/allergeni` with `selectedAllergens.map { it.name }`
- `Snackbar("Saved!")` on success, `Snackbar("Could not save. Try again.")` on failure
- Logout button: `sessionManager.logout()` then `onLogout()`
- `CircularProgressIndicator()` shown while `isLoading = true`

### Task 4 — Logout Flow Verification (REQ-ID-007)
Verified `IngredientApp` in `MainActivity.kt` already has correct `onLogout` callback for `ClienteScreen`:
```kotlin
onLogout = {
    currentUserId = null
    currentUserEmail = null
    userType = ""
    currentScreen = "Login"
}
```
SharedPreferences cleared by `sessionManager.logout()` inside `ProfileScreen`.
No changes to `MainActivity.kt` needed.

## Build Status

`./gradlew :app:compileDebugKotlin` → **BUILD SUCCESSFUL** (exit 0, 3s)

## Success Criteria Verification

- [x] `ProfileScreen` composable exists in `ClienteScreens.kt` (line 608)
- [x] Profile tab is 3rd item in NavigationBar (`selectedTab == 2`, line 87)
- [x] Profile tab icon is `Icons.Default.Person` (line 89)
- [x] ProfileScreen loads nome/cognome/email/allergeni from Firebase
- [x] nome/cognome/email displayed as `Text` (read-only)
- [x] allergeni displayed in `AllergeneChipSelector` (editable)
- [x] Save button updates only `allergeni` in Firebase
- [x] Snackbar shows `"Saved!"` on success (line 686)
- [x] Snackbar shows `"Could not save. Try again."` on failure (line 690)
- [x] Logout clears session via `sessionManager.logout()` (line 705) and navigates to Login via `onLogout()` (line 706)
- [x] Build compiles successfully

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 — Imports | 0180ca0 | feat(01-E): add required imports for ProfileScreen to ClienteScreens.kt |
| 2 — Profile tab | 4c291c1 | feat(01-E): add Profile tab to ClienteScreen bottom nav |
| 3 — ProfileScreen | a481ade | feat(01-E): ProfileScreen composable with allergen edit + logout |
| 4 — Build verify | f063788 | fix(01-E): build verification + end-to-end flow check |

## Deviations from Plan

None — plan executed exactly as specified.

- `AllergeneChipSelector` was accessible cross-file without any import (same package `com.example.ingredient`) — no workaround needed.
- `ClienteScreen` already had all required parameters (`userId`, `databaseReference`, `onLogout`) — no call-site changes needed at MainActivity.kt.

## Known Stubs

None — all data fields wired to Firebase. Session clear functional. Snackbar feedback implemented.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| No new threats | ClienteScreens.kt | All changes within existing trust boundaries (T-01-E-01 through T-01-E-03, all accepted per plan threat model). ProfileScreen reads only own user node via userId from session. |

## Self-Check: PASSED

- `ClienteScreens.kt` contains `fun ProfileScreen(` ✓ (line 608)
- `ClienteScreens.kt` contains `sessionManager.logout()` ✓ (line 705)
- `ClienteScreens.kt` contains `onLogout()` after logout ✓ (line 706)
- `ClienteScreens.kt` contains `selectedTab == 2` ✓ (line 87)
- `ClienteScreens.kt` contains `2 -> ProfileScreen(` ✓ (line 100)
- `ClienteScreens.kt` contains `AllergeneChipSelector(` ✓ (line 673)
- `ClienteScreens.kt` contains `"Saved!"` ✓ (line 686)
- `ClienteScreens.kt` contains `"Could not save. Try again."` ✓ (line 690)
- Build: BUILD SUCCESSFUL ✓
- All 4 task commits verified in git log ✓
