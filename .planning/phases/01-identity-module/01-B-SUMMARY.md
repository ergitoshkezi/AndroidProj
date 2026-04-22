---
phase: 01-identity-module
plan: B
subsystem: ui-routing
tags: [disclaimer, navigation, session, startup-routing, compose]
dependency_graph:
  requires: [01-A]
  provides: [DisclaimerScreen, session-aware-startup-routing]
  affects: [AuthScreens.kt, MainActivity.kt]
tech_stack:
  added: [androidx.activity.compose.BackHandler]
  patterns: [back-handler-close-app, initial-screen-parameter, composable-context-hoisting]
key_files:
  created: []
  modified:
    - app/src/main/java/com/example/ingredient/AuthScreens.kt
    - app/src/main/java/com/example/ingredient/MainActivity.kt
decisions:
  - "LocalContext.current hoisted to IngredientApp composable scope — not inside onAccepted lambda (Compose rule)"
  - "Screen name strings match existing when-block: 'Login', 'Cliente', 'Ristoratore'"
  - "disclaimer route added before Login in when-block to preserve correct evaluation order"
metrics:
  duration: ~15 minutes
  completed: 2026-04-23
  tasks_completed: 3
  files_created: 0
  files_modified: 2
---

# Phase 1 Plan B: Disclaimer Screen & Session Startup Routing Summary

**One-liner:** DisclaimerScreen composable with BackHandler + MainActivity session-aware startup routing that passes `initialScreen` to IngredientApp before first Compose frame.

---

## What Was Built

### DisclaimerScreen (AuthScreens.kt)
New `@Composable fun DisclaimerScreen(onAccepted: () -> Unit, modifier: Modifier = Modifier)` added at the top of AuthScreens.kt, before LoginScreen.

- `BackHandler { activity?.finish() }` — back press closes the app entirely (does not navigate back)
- Full-screen `Column` centered vertically and horizontally with 24dp padding
- Title: "Important Notice" (headlineMedium)
- Body text: exact spec text about app being a support tool
- CTA button: "I understand" (full width) → calls `onAccepted`

### MainActivity startup routing (MainActivity.kt)
`onCreate` now reads SharedPreferences BEFORE `setContent`:

```kotlin
val sessionManager = SessionManager(applicationContext)
val initialScreen: String = when {
    !sessionManager.isDisclaimerAccepted() -> "disclaimer"
    sessionManager.isLoggedIn() -> if (sessionManager.getUserType() == "Ristoratore") "Ristoratore" else "Cliente"
    else -> "Login"
}
```

This ensures the Compose tree is initialized with the correct screen — no flicker on resume.

### IngredientApp composable changes (MainActivity.kt)
- Signature extended: `fun IngredientApp(..., initialScreen: String = "Login")`
- `currentScreen` now initialized from `initialScreen` (not hardcoded "Login")
- New `when` case before "Login":
  ```kotlin
  "disclaimer" -> DisclaimerScreen(
      onAccepted = {
          val sessionManager = SessionManager(context)
          sessionManager.setDisclaimerAccepted()
          currentScreen = "Login"
      },
      modifier = Modifier.padding(innerPadding)
  )
  ```
- `context` captured at composable scope (`val context = LocalContext.current`) and referenced in the callback.

---

## Commits

| Task | Commit | Message |
|------|--------|---------|
| Task 1 | 3a954a5 | feat(01-B): add DisclaimerScreen composable |
| Task 2 | fcc3d5b | feat(01-B): MainActivity reads session before setContent |
| Task 3 | 862030b | feat(01-B): IngredientApp accepts initialScreen, adds disclaimer route |

---

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed LocalContext.current inside non-composable lambda**
- **Found during:** Task 3 — build failure after first commit
- **Issue:** Plan spec used `SessionManager(LocalContext.current)` inside the `onAccepted` callback lambda, but `LocalContext.current` is a composable invocation and cannot be called outside a composable context.
- **Fix:** Hoisted `val context = LocalContext.current` to the `IngredientApp` composable body scope (before the Scaffold), then referenced `context` inside the `onAccepted` lambda.
- **Files modified:** `MainActivity.kt`
- **Commit:** 862030b (amended)

---

## Build Status

`./gradlew :app:compileDebugKotlin` — **EXIT CODE 0 (SUCCESS)**

---

## Known Stubs

None. All wired to real SharedPreferences via SessionManager.

---

## Threat Flags

None — no new network endpoints, auth paths, or trust boundaries introduced. All concerns are covered by the plan's threat model (T-01-B-01, T-01-B-02 both accepted).

---

## Self-Check: PASSED

- [x] `AuthScreens.kt` contains `fun DisclaimerScreen(`
- [x] `AuthScreens.kt` contains `BackHandler { activity?.finish() }`
- [x] `AuthScreens.kt` contains `"I understand"` button text
- [x] `AuthScreens.kt` contains exact disclaimer body text
- [x] `MainActivity.kt` contains `SessionManager(applicationContext)`
- [x] `MainActivity.kt` contains `val initialScreen: String = when {`
- [x] `MainActivity.kt` contains `!sessionManager.isDisclaimerAccepted() -> "disclaimer"`
- [x] `MainActivity.kt` contains `IngredientApp(tesseractManager, databaseReference, initialScreen)`
- [x] `IngredientApp` signature contains `initialScreen: String = "Login"`
- [x] `currentScreen` initialized with `mutableStateOf(initialScreen)`
- [x] when block contains `"disclaimer" -> DisclaimerScreen(`
- [x] `compileDebugKotlin` exits 0
