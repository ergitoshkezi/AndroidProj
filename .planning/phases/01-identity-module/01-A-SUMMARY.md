---
phase: 01-identity-module
plan: A
subsystem: model
tags: [model, enum, data-class, session, shared-preferences]
dependency_graph:
  requires: []
  provides: [AllergeneType, User, SessionManager]
  affects: [AuthScreens.kt, MainActivity.kt, ClienteScreens.kt]
tech_stack:
  added: []
  patterns: [enum-with-property, data-class-firebase, sharedprefs-wrapper]
key_files:
  created:
    - app/src/main/java/com/example/ingredient/model/AllergeneType.kt
    - app/src/main/java/com/example/ingredient/model/User.kt
    - app/src/main/java/com/example/ingredient/model/SessionManager.kt
  modified: []
decisions:
  - "SessionManager placed in model package (not util) per STATE.md locked decision"
  - "User.id included in data class for convenience but not stored in Firebase node"
  - "logout() removes session_user_id and session_user_type but NOT disclaimer_accepted (per spec)"
metrics:
  duration: ~5 minutes
  completed: 2026-04-23
  tasks_completed: 3
  files_created: 3
  files_modified: 0
---

# Phase 1 Plan A: Model Layer Foundation Summary

**One-liner:** Three-class model foundation — 14-allergen EU enum, Firebase-compatible User data class, and SharedPreferences session wrapper.

---

## What Was Built

### AllergeneType.kt
Enum class with exactly 14 EU allergens (Regulation EU No 1169/2011). Each entry has a `displayName: String` property for UI display. Stored in Firebase as `.name` (uppercase, e.g. `"GLUTINE"`).

### User.kt
Data class matching the Firebase RTDB `/users/{userId}` schema. All 8 fields have default values enabling `getValue(User::class.java)` deserialization without @NoArg annotations. The `id` field is not stored in Firebase (it's the push key) but is included for in-memory convenience.

### SessionManager.kt
SharedPreferences wrapper for `ingredient_session` file. Provides clean API for session read/write with the 3 locked keys. `logout()` intentionally preserves `disclaimer_accepted` so the legal disclaimer is only shown on first install.

---

## Interfaces Created

```kotlin
// AllergeneType.kt
enum class AllergeneType(val displayName: String) {
    GLUTINE, CROSTACEI, UOVA, PESCE, ARACHIDI, SOIA, LATTE,
    FRUTTA_SECCA, SEDANO, SENAPE, SESAMO, ANIDRIDE_SOLFOROSA,
    LUPINI, MOLLUSCHI
}

// User.kt
data class User(
    val id: String = "",
    val nome: String = "",
    val cognome: String = "",
    val email: String = "",
    val password: String = "",
    val userType: String = "",
    val allergeni: List<String> = emptyList(),
    val createdAt: Long = 0L
)

// SessionManager.kt
class SessionManager(context: Context) {
    fun saveSession(userId: String, userType: String)
    fun getUserId(): String
    fun getUserType(): String
    fun isLoggedIn(): Boolean
    fun logout()
    fun isDisclaimerAccepted(): Boolean
    fun setDisclaimerAccepted()
}
```

---

## Commits

| Task | Commit | Message |
|------|--------|---------|
| Task 1 | 3e4cb91 | feat(01-A): create AllergeneType enum |
| Task 2 | 7436a36 | feat(01-A): create User data class |
| Task 3 | 084f679 | feat(01-A): create SessionManager |

---

## Deviations from Plan

None — plan executed exactly as written.

---

## Ready For

- **PLAN-B** (AuthScreens extension): can import `User`, `SessionManager`, `AllergeneType`
- **PLAN-C** (AllergeneChipSelector): can import `AllergeneType` directly
- **PLAN-D** (MainActivity routing): can import `SessionManager`
- **PLAN-E** (ProfileScreen): can import all three classes

---

## Self-Check: PASSED

- [x] `model/AllergeneType.kt` exists — 14 enum values confirmed
- [x] `model/User.kt` exists — all 8 fields with default values confirmed
- [x] `model/SessionManager.kt` exists — all 7 methods confirmed
- [x] All files have `package com.example.ingredient.model`
- [x] `./gradlew :app:compileDebugKotlin` — exit code 0 (SUCCESS)
