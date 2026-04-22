---
phase: 01-identity-module
plan: A
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/example/ingredient/model/AllergeneType.kt
  - app/src/main/java/com/example/ingredient/model/User.kt
  - app/src/main/java/com/example/ingredient/model/SessionManager.kt
autonomous: true
requirements: [REQ-ID-004, REQ-ID-006, RNF-PRIVACY-001]

must_haves:
  truths:
    - "AllergeneType enum has exactly 14 EU allergens with correct display names"
    - "User data class matches Firebase schema with all required fields"
    - "SessionManager can read/write session state and disclaimer flag from SharedPreferences"
  artifacts:
    - path: "app/src/main/java/com/example/ingredient/model/AllergeneType.kt"
      provides: "14 EU allergen enum with displayName property"
      contains: "enum class AllergeneType"
    - path: "app/src/main/java/com/example/ingredient/model/User.kt"
      provides: "User data class for Firebase serialization"
      contains: "data class User"
    - path: "app/src/main/java/com/example/ingredient/model/SessionManager.kt"
      provides: "SharedPreferences wrapper for session persistence"
      contains: "class SessionManager"
  key_links:
    - from: "SessionManager.kt"
      to: "SharedPreferences ingredient_session"
      via: "getSharedPreferences"
      pattern: 'getSharedPreferences\\("ingredient_session"'
---

<objective>
Create the model layer foundation for the Identity Module: AllergeneType enum (14 EU allergens), User data class, and SessionManager (SharedPreferences wrapper).

Purpose: These are the data contracts and session utilities that ALL other phase tasks depend on. Must be created first with zero external dependencies.

Output: Three new files in `com.example.ingredient.model` package, ready for use by AuthScreens, MainActivity, and ClienteScreens.
</objective>

<execution_context>
@~/.copilot/get-shit-done/workflows/execute-plan.md
@~/.copilot/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/ROADMAP.md
@.planning/phases/01-identity-module/01-CONTEXT.md
@.planning/phases/01-identity-module/01-PATTERNS.md
@tasks/CONTRACT.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Create AllergeneType enum</name>
  <files>app/src/main/java/com/example/ingredient/model/AllergeneType.kt</files>
  <read_first>
    - tasks/CONTRACT.md (lines 39-60 — exact enum definition with displayName property)
    - .planning/phases/01-identity-module/01-CONTEXT.md (lines 76-81 — enum values and display names)
    - .planning/phases/01-identity-module/01-PATTERNS.md (lines 28-58 — package/file pattern)
  </read_first>
  <action>
Create new file `app/src/main/java/com/example/ingredient/model/AllergeneType.kt`.

1. Create package directory if not exists: `com.example.ingredient.model`
2. Package declaration: `package com.example.ingredient.model`
3. Create enum class `AllergeneType` with a `displayName: String` constructor parameter
4. Add exactly 14 enum values with these EXACT names and display names:
   - GLUTINE("Glutine")
   - CROSTACEI("Crostacei")
   - UOVA("Uova")
   - PESCE("Pesce")
   - ARACHIDI("Arachidi")
   - SOIA("Soia")
   - LATTE("Latte")
   - FRUTTA_SECCA("Frutta a guscio")
   - SEDANO("Sedano")
   - SENAPE("Senape")
   - SESAMO("Sesamo")
   - ANIDRIDE_SOLFOROSA("Anidride solforosa e solfiti")
   - LUPINI("Lupini")
   - MOLLUSCHI("Molluschi")

No companion object needed in this task (per CONTRACT.md, safe parsing is optional for Phase 1).
  </action>
  <verify>
    <automated>grep -c "enum class AllergeneType" app/src/main/java/com/example/ingredient/model/AllergeneType.kt</automated>
  </verify>
  <acceptance_criteria>
    - File exists at `app/src/main/java/com/example/ingredient/model/AllergeneType.kt`
    - Contains `package com.example.ingredient.model`
    - Contains `enum class AllergeneType(val displayName: String)`
    - Contains exactly 14 enum values: GLUTINE, CROSTACEI, UOVA, PESCE, ARACHIDI, SOIA, LATTE, FRUTTA_SECCA, SEDANO, SENAPE, SESAMO, ANIDRIDE_SOLFOROSA, LUPINI, MOLLUSCHI
    - FRUTTA_SECCA has displayName "Frutta a guscio" (with space)
    - ANIDRIDE_SOLFOROSA has displayName "Anidride solforosa e solfiti"
  </acceptance_criteria>
  <done>AllergeneType.kt exists with all 14 EU allergens and correct display names</done>
</task>

<task type="auto">
  <name>Task 2: Create User data class</name>
  <files>app/src/main/java/com/example/ingredient/model/User.kt</files>
  <read_first>
    - tasks/CONTRACT.md (lines 16-27 — User data class with exact field names and types)
    - .planning/phases/01-identity-module/01-CONTEXT.md (lines 86-97 — User data class spec)
    - .planning/phases/01-identity-module/01-PATTERNS.md (lines 62-100 — data class pattern from MenuParser.kt)
  </read_first>
  <action>
Create new file `app/src/main/java/com/example/ingredient/model/User.kt`.

1. Package declaration: `package com.example.ingredient.model`
2. Create data class `User` with these EXACT fields (all with default values for Firebase deserialization):
   - id: String = ""
   - nome: String = ""
   - cognome: String = ""
   - email: String = ""
   - password: String = ""
   - userType: String = ""
   - allergeni: List&lt;String&gt; = emptyList()
   - createdAt: Long = 0L

All fields use default values (pattern from MenuParser.kt MenuItem class). Field names are camelCase per CONVENTIONS.md.
  </action>
  <verify>
    <automated>grep -c "data class User" app/src/main/java/com/example/ingredient/model/User.kt</automated>
  </verify>
  <acceptance_criteria>
    - File exists at `app/src/main/java/com/example/ingredient/model/User.kt`
    - Contains `package com.example.ingredient.model`
    - Contains `data class User(`
    - Contains field `val id: String = ""`
    - Contains field `val nome: String = ""`
    - Contains field `val cognome: String = ""`
    - Contains field `val email: String = ""`
    - Contains field `val password: String = ""`
    - Contains field `val userType: String = ""`
    - Contains field `val allergeni: List<String> = emptyList()`
    - Contains field `val createdAt: Long = 0L`
  </acceptance_criteria>
  <done>User.kt exists with all required fields matching Firebase schema</done>
</task>

<task type="auto">
  <name>Task 3: Create SessionManager</name>
  <files>app/src/main/java/com/example/ingredient/model/SessionManager.kt</files>
  <read_first>
    - tasks/CONTRACT.md (lines 109-142 — SharedPreferences contract with exact keys and method signatures)
    - .planning/phases/01-identity-module/01-CONTEXT.md (lines 99-104 — SessionManager spec)
    - .planning/phases/01-identity-module/01-PATTERNS.md (lines 104-160 — SessionManager implementation pattern)
  </read_first>
  <action>
Create new file `app/src/main/java/com/example/ingredient/model/SessionManager.kt`.

1. Package declaration: `package com.example.ingredient.model`
2. Import: `android.content.Context` and `android.content.SharedPreferences`
3. Create class `SessionManager(context: Context)` with:
   - Private val `prefs: SharedPreferences` initialized with `context.getSharedPreferences("ingredient_session", Context.MODE_PRIVATE)`
4. Add methods with EXACT signatures:
   - `fun saveSession(userId: String, userType: String)` — puts `session_user_id` and `session_user_type`
   - `fun getUserId(): String` — returns `prefs.getString("session_user_id", "") ?: ""`
   - `fun getUserType(): String` — returns `prefs.getString("session_user_type", "") ?: ""`
   - `fun isLoggedIn(): Boolean` — returns `getUserId().isNotEmpty()`
   - `fun logout()` — removes `session_user_id` and `session_user_type` (NOT disclaimer_accepted)
   - `fun isDisclaimerAccepted(): Boolean` — returns `prefs.getBoolean("disclaimer_accepted", false)`
   - `fun setDisclaimerAccepted()` — puts `disclaimer_accepted = true`

Use `.apply()` for async writes (project pattern).
  </action>
  <verify>
    <automated>grep -c "class SessionManager" app/src/main/java/com/example/ingredient/model/SessionManager.kt</automated>
  </verify>
  <acceptance_criteria>
    - File exists at `app/src/main/java/com/example/ingredient/model/SessionManager.kt`
    - Contains `package com.example.ingredient.model`
    - Contains `class SessionManager(context: Context)`
    - Contains `getSharedPreferences("ingredient_session", Context.MODE_PRIVATE)`
    - Contains `fun saveSession(userId: String, userType: String)`
    - Contains `fun getUserId(): String`
    - Contains `fun getUserType(): String`
    - Contains `fun isLoggedIn(): Boolean`
    - Contains `fun logout()`
    - Contains `fun isDisclaimerAccepted(): Boolean`
    - Contains `fun setDisclaimerAccepted()`
    - Uses key `"session_user_id"` (exact)
    - Uses key `"session_user_type"` (exact)
    - Uses key `"disclaimer_accepted"` (exact)
    - `logout()` does NOT remove `disclaimer_accepted`
  </acceptance_criteria>
  <done>SessionManager.kt exists with all session management methods using correct SharedPreferences keys</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| SharedPreferences | Local storage, device-only, no network |
| Model classes | Data contracts, no I/O |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-01-A-01 | Information Disclosure | SessionManager stores userId in plaintext SharedPreferences | accept | SharedPreferences is app-private storage (MODE_PRIVATE). Root access required to read. Acceptable for v1 per STATE.md accepted risks. |
| T-01-A-02 | Information Disclosure | User.password field exists in data class | accept | Data class is for Firebase deserialization. Password never logged (enforced in Plan D). v2 migrates to Firebase Auth. |
</threat_model>

<verification>
After all tasks complete:
1. Verify model package exists: `ls app/src/main/java/com/example/ingredient/model/`
2. Verify all 3 files exist: AllergeneType.kt, User.kt, SessionManager.kt
3. Verify build compiles: `./gradlew :app:compileDebugKotlin` (should pass)
</verification>

<success_criteria>
- [ ] `model/` package directory created
- [ ] AllergeneType.kt with 14 enum values and displayName property
- [ ] User.kt with all fields matching Firebase schema
- [ ] SessionManager.kt with all session methods and correct SharedPreferences keys
- [ ] All files compile without errors
</success_criteria>

<output>
After completion, create `.planning/phases/01-identity-module/01-A-SUMMARY.md`
</output>
