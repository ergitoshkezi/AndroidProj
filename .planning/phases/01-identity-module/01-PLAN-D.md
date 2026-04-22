---
phase: 01-identity-module
plan: D
type: execute
wave: 3
depends_on: [01-A, 01-B, 01-C]
files_modified:
  - app/src/main/java/com/example/ingredient/AuthScreens.kt
  - app/src/main/java/com/example/ingredient/MainActivity.kt
autonomous: true
requirements: [REQ-ID-002, REQ-ID-003, RNF-SEC-001, RNF-SEC-002, RNF-PERF-001]

must_haves:
  truths:
    - "LoginScreen shows 'Invalid email or password' on wrong credentials"
    - "LoginScreen shows 'Connection error. Check your internet.' on network failure"
    - "LoginScreen shows 'Please fill in all fields' on blank inputs"
    - "LoginScreen saves session on successful login"
    - "RegistrationScreen has nome and cognome fields"
    - "RegistrationScreen has AllergeneChipSelector for allergen selection"
    - "RegistrationScreen shows 'Passwords do not match' when mismatch"
    - "RegistrationScreen shows 'Email already in use' on duplicate email"
    - "RegistrationScreen saves nome, cognome, allergeni to Firebase"
    - "RegistrationScreen saves session on successful registration"
    - "No password appears in any Log.d or Log.e call (RNF-SEC-001)"
  artifacts:
    - path: "app/src/main/java/com/example/ingredient/AuthScreens.kt"
      provides: "Extended LoginScreen and RegistrationScreen"
      contains: "nome"
  key_links:
    - from: "LoginScreen"
      to: "SessionManager"
      via: "saves session on success"
      pattern: "sessionManager\\.saveSession"
    - from: "RegistrationScreen"
      to: "Firebase users/{userId}"
      via: "writes nome, cognome, allergeni"
      pattern: '"nome" to nome'
---

<objective>
Extend AuthScreens.kt: Add proper error messages to LoginScreen, add nome/cognome/allergeni fields to RegistrationScreen, save sessions on success, and ensure no password logging (RNF-SEC-001).

Purpose: REQ-ID-002 (Login) and REQ-ID-003 (Registration) require complete auth flows with proper validation, error messages, and session persistence. RNF-SEC-001 mandates no password in logs.

Output: Fully functional LoginScreen and RegistrationScreen with all required fields and validation.
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

# Read BEFORE modifying
@app/src/main/java/com/example/ingredient/AuthScreens.kt
@app/src/main/java/com/example/ingredient/MainActivity.kt

# Interfaces from Plan A
<interfaces>
From model/SessionManager.kt:
```kotlin
class SessionManager(context: Context) {
    fun saveSession(userId: String, userType: String)
}
```

From model/AllergeneType.kt:
```kotlin
enum class AllergeneType(val displayName: String) {
    // 14 values...
}
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: CRITICAL — Remove any password logging (RNF-SEC-001)</name>
  <files>app/src/main/java/com/example/ingredient/AuthScreens.kt</files>
  <read_first>
    - app/src/main/java/com/example/ingredient/AuthScreens.kt (entire file — search for Log.d and Log.e calls)
    - .planning/phases/01-identity-module/01-PATTERNS.md (lines 349-356 — security fix pattern)
    - .planning/STATE.md (line 71 — CRITICAL-SEC-002)
  </read_first>
  <action>
SECURITY FIX: Verify and fix any password logging in AuthScreens.kt.

1. Search the file for ALL `Log.d` and `Log.e` calls
2. Verify NONE of them include the `password` variable
3. Current expected safe lines:
   - Line ~104: `Log.d("LoginScreen", "Login successful for user: $userId")` — SAFE (no password)
   - Line ~113: `Log.e("LoginScreen", "Login failed - no matching credentials")` — SAFE
   - Line ~120: `Log.e("LoginScreen", "Database error: ${error.message}")` — SAFE
   - Line ~295: `Log.d("RegistrationScreen", "User registered: $userId")` — SAFE
   - Line ~302: `Log.e("RegistrationScreen", "Registration error", e)` — SAFE
   - Line ~310: `Log.e("RegistrationScreen", "Database error: ${error.message}")` — SAFE

4. If ANY Log call contains `password`, `dbPassword`, or the password variable — REMOVE IT or replace with safe message

This is a VERIFICATION task if logs are already safe. If any password logging exists, fix it.
  </action>
  <verify>
    <automated>grep -n "Log\\.d\\|Log\\.e" app/src/main/java/com/example/ingredient/AuthScreens.kt | grep -i password || echo "NO_PASSWORD_IN_LOGS"</automated>
  </verify>
  <acceptance_criteria>
    - `grep -i password` on all Log lines returns empty (no password in logs)
    - AuthScreens.kt does NOT contain any Log call that includes `password` variable
    - AuthScreens.kt does NOT contain any Log call that includes `dbPassword` variable
    - Verification command outputs "NO_PASSWORD_IN_LOGS"
  </acceptance_criteria>
  <done>Confirmed no password in any Log.d or Log.e call (RNF-SEC-001 satisfied)</done>
</task>

<task type="auto">
  <name>Task 2: Fix LoginScreen error messages and add session save</name>
  <files>app/src/main/java/com/example/ingredient/AuthScreens.kt</files>
  <read_first>
    - app/src/main/java/com/example/ingredient/AuthScreens.kt (lines 22-139 — LoginScreen)
    - .planning/phases/01-identity-module/01-PATTERNS.md (lines 321-356 — LoginScreen modifications)
    - .planning/phases/01-identity-module/01-CONTEXT.md (lines 48-52 — error message specs)
  </read_first>
  <action>
Modify LoginScreen in AuthScreens.kt:

1. Add import at top of file (if not already): `import com.example.ingredient.model.SessionManager`

2. Inside LoginScreen, after `val context = LocalContext.current`, add:
   ```kotlin
   val sessionManager = SessionManager(context)
   ```

3. In the `onCancelled` callback (around line 117-121), improve error message:
   Change from:
   ```kotlin
   errorMessage = "Database error: ${error.message}"
   ```
   To:
   ```kotlin
   errorMessage = if (error.code == DatabaseError.NETWORK_ERROR || error.code == DatabaseError.DISCONNECTED) {
       "Connection error. Check your internet."
   } else {
       "Database error: ${error.message}"
   }
   ```
   (Requires adding import: `import com.google.firebase.database.DatabaseError` — should already exist)

4. After `onLoginSuccess(userId ?: "", userType)` (around line 106), add session save:
   Insert BEFORE the onLoginSuccess call:
   ```kotlin
   sessionManager.saveSession(userId ?: "", userType)
   ```

The existing error messages are already correct:
- "Please fill in all fields" for blank inputs (line 83-85)
- "Invalid email or password" for wrong credentials (line 111)
  </action>
  <verify>
    <automated>grep -c "sessionManager.saveSession" app/src/main/java/com/example/ingredient/AuthScreens.kt</automated>
  </verify>
  <acceptance_criteria>
    - LoginScreen contains `val sessionManager = SessionManager(context)`
    - LoginScreen contains `sessionManager.saveSession(userId`
    - LoginScreen contains `"Connection error. Check your internet."`
    - LoginScreen contains `DatabaseError.NETWORK_ERROR`
    - LoginScreen still contains `"Invalid email or password"` for wrong credentials
    - LoginScreen still contains `"Please fill in all fields"` for blank inputs
  </acceptance_criteria>
  <done>LoginScreen has proper error messages and saves session on success</done>
</task>

<task type="auto">
  <name>Task 3: Add nome and cognome fields to RegistrationScreen</name>
  <files>app/src/main/java/com/example/ingredient/AuthScreens.kt</files>
  <read_first>
    - app/src/main/java/com/example/ingredient/AuthScreens.kt (lines 141-332 — RegistrationScreen)
    - .planning/phases/01-identity-module/01-PATTERNS.md (lines 358-406 — RegistrationScreen field additions)
    - .planning/phases/01-identity-module/01-CONTEXT.md (lines 54-60 — validation rules)
  </read_first>
  <action>
Modify RegistrationScreen in AuthScreens.kt to add nome and cognome fields:

1. Add state variables AFTER existing state declarations (after line ~154):
   ```kotlin
   var nome by remember { mutableStateOf("") }
   var cognome by remember { mutableStateOf("") }
   ```

2. Add OutlinedTextField for nome AFTER the "Register" title and Spacer (around line 167):
   ```kotlin
   OutlinedTextField(
       value = nome,
       onValueChange = { nome = it },
       label = { Text("Nome") },
       modifier = Modifier.fillMaxWidth(),
       enabled = !isLoading
   )
   Spacer(modifier = Modifier.height(16.dp))
   ```

3. Add OutlinedTextField for cognome AFTER nome field:
   ```kotlin
   OutlinedTextField(
       value = cognome,
       onValueChange = { cognome = it },
       label = { Text("Cognome") },
       modifier = Modifier.fillMaxWidth(),
       enabled = !isLoading
   )
   Spacer(modifier = Modifier.height(16.dp))
   ```

4. Modify the validation in Button onClick (around line 244). Change from:
   ```kotlin
   if (email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
   ```
   To:
   ```kotlin
   if (nome.isBlank() || cognome.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
   ```

The field order in the form should be: Nome, Cognome, Email, Password, Confirm Password
  </action>
  <verify>
    <automated>grep -c 'var nome by remember' app/src/main/java/com/example/ingredient/AuthScreens.kt</automated>
  </verify>
  <acceptance_criteria>
    - RegistrationScreen contains `var nome by remember { mutableStateOf("") }`
    - RegistrationScreen contains `var cognome by remember { mutableStateOf("") }`
    - RegistrationScreen contains OutlinedTextField with `label = { Text("Nome") }`
    - RegistrationScreen contains OutlinedTextField with `label = { Text("Cognome") }`
    - Validation includes `nome.isBlank() || cognome.isBlank()`
    - Form order is: Nome → Cognome → Email → Password → Confirm Password
  </acceptance_criteria>
  <done>RegistrationScreen has nome and cognome input fields with validation</done>
</task>

<task type="auto">
  <name>Task 4: Add AllergeneChipSelector to RegistrationScreen</name>
  <files>app/src/main/java/com/example/ingredient/AuthScreens.kt</files>
  <read_first>
    - app/src/main/java/com/example/ingredient/AuthScreens.kt (RegistrationScreen — find userType selector location)
    - .planning/phases/01-identity-module/01-PATTERNS.md (lines 386-396 — AllergeneChipSelector integration)
    - .planning/phases/01-identity-module/01-CONTEXT.md (lines 106-119 — AllergeneChipSelector spec)
  </read_first>
  <action>
Add allergen selection to RegistrationScreen:

1. Add state variable after nome/cognome declarations:
   ```kotlin
   var selectedAllergens by remember { mutableStateOf<List<AllergeneType>>(emptyList()) }
   ```

2. Add AllergeneChipSelector AFTER the confirmPassword field and Spacer, BEFORE the userType selection ("I am a:"):
   ```kotlin
   Spacer(modifier = Modifier.height(16.dp))
   Text("Allergens (optional):", style = MaterialTheme.typography.bodyLarge)
   Spacer(modifier = Modifier.height(8.dp))
   AllergeneChipSelector(
       selected = selectedAllergens,
       onSelectionChange = { selectedAllergens = it }
   )
   ```

Note: AllergeneChipSelector is already defined in AuthScreens.kt (from Plan C).
Note: Allergens are optional — do NOT add to blank validation.
  </action>
  <verify>
    <automated>grep -c "AllergeneChipSelector" app/src/main/java/com/example/ingredient/AuthScreens.kt</automated>
  </verify>
  <acceptance_criteria>
    - RegistrationScreen contains `var selectedAllergens by remember { mutableStateOf<List<AllergeneType>>(emptyList()) }`
    - RegistrationScreen contains `Text("Allergens (optional):"` 
    - RegistrationScreen contains `AllergeneChipSelector(selected = selectedAllergens`
    - AllergeneChipSelector appears BEFORE "I am a:" user type selector
    - Blank validation does NOT include selectedAllergens (allergens are optional)
  </acceptance_criteria>
  <done>RegistrationScreen has AllergeneChipSelector for allergen selection</done>
</task>

<task type="auto">
  <name>Task 5: Update RegistrationScreen Firebase write and session save</name>
  <files>app/src/main/java/com/example/ingredient/AuthScreens.kt</files>
  <read_first>
    - app/src/main/java/com/example/ingredient/AuthScreens.kt (lines 282-315 — userData map and Firebase write)
    - .planning/phases/01-identity-module/01-PATTERNS.md (lines 407-431 — userData map update and error message fix)
    - tasks/CONTRACT.md (lines 76-86 — Firebase schema)
  </read_first>
  <action>
Update RegistrationScreen Firebase write and add session save:

1. Add sessionManager initialization after context (similar to LoginScreen):
   ```kotlin
   val sessionManager = SessionManager(context)
   ```

2. Update the `userData` map (around line 285) to include nome, cognome, allergeni:
   Change from:
   ```kotlin
   val userData = mapOf(
       "email" to email,
       "password" to password,
       "userType" to selectedUserType,
       "createdAt" to System.currentTimeMillis()
   )
   ```
   To:
   ```kotlin
   val userData = mapOf(
       "nome" to nome,
       "cognome" to cognome,
       "email" to email,
       "password" to password,
       "userType" to selectedUserType,
       "allergeni" to selectedAllergens.map { it.name },
       "createdAt" to System.currentTimeMillis()
   )
   ```

3. Fix the duplicate email error message (around line 277):
   Change from: `errorMessage = "Email already registered"`
   To: `errorMessage = "Email already in use"`

4. Add session save BEFORE `onRegisterSuccess` call (around line 296):
   ```kotlin
   sessionManager.saveSession(userId, selectedUserType)
   ```
  </action>
  <verify>
    <automated>grep -c '"nome" to nome' app/src/main/java/com/example/ingredient/AuthScreens.kt</automated>
  </verify>
  <acceptance_criteria>
    - RegistrationScreen contains `val sessionManager = SessionManager(context)`
    - userData map contains `"nome" to nome`
    - userData map contains `"cognome" to cognome`
    - userData map contains `"allergeni" to selectedAllergens.map { it.name }`
    - Error message is `"Email already in use"` (not "Email already registered")
    - Contains `sessionManager.saveSession(userId, selectedUserType)` before onRegisterSuccess
  </acceptance_criteria>
  <done>RegistrationScreen writes nome/cognome/allergeni to Firebase and saves session</done>
</task>

<task type="auto">
  <name>Task 6: Update IngredientApp login/register callbacks to save session</name>
  <files>app/src/main/java/com/example/ingredient/MainActivity.kt</files>
  <read_first>
    - app/src/main/java/com/example/ingredient/MainActivity.kt (lines 76-95 — Login and Register screen callbacks)
    - .planning/phases/01-identity-module/01-CONTEXT.md (lines 132-135 — session save spec)
  </read_first>
  <action>
Session is now saved in LoginScreen and RegistrationScreen directly (Tasks 2 and 5), so IngredientApp callbacks don't need modification.

VERIFY that the existing callbacks in IngredientApp work correctly:
- `onLoginSuccess` in LoginScreen call (lines 79-82) receives userId and userType
- `onRegisterSuccess` in RegistrationScreen call (lines 89-92) receives userId and userType
- Both callbacks set `currentUserId`, `userType`, and `currentScreen`

No changes needed to IngredientApp callbacks — session save happens in the auth screens.
  </action>
  <verify>
    <automated>grep -c "onLoginSuccess = { userId, type ->" app/src/main/java/com/example/ingredient/MainActivity.kt</automated>
  </verify>
  <acceptance_criteria>
    - IngredientApp contains `onLoginSuccess = { userId, type ->`
    - IngredientApp contains `onRegisterSuccess = { userId, type ->`
    - Session save occurs in AuthScreens.kt (verified in Tasks 2 and 5)
  </acceptance_criteria>
  <done>Verified IngredientApp callbacks work with session-saving auth screens</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| User input → Firebase | email, password, nome, cognome, allergens written to RTDB |
| Firebase → App | User data read for login credential check |
| App → Logs | MUST NOT include password |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-01-D-01 | Information Disclosure | Password in logs | mitigate | Task 1 verifies and removes any password logging. grep verification ensures no password in Log calls. |
| T-01-D-02 | Information Disclosure | Plaintext password in Firebase | accept | v1 accepted risk per STATE.md. Password stored as plaintext in RTDB. Firebase Auth migration in v2. |
| T-01-D-03 | Spoofing | No email verification | accept | v1 accepts unverified email registration. Firebase Auth in v2 adds email verification. |
| T-01-D-04 | Information Disclosure | Login reads all users/ | accept | Required for email lookup (no Firebase Auth). RNF-SEC-002 limits this to login only. Phase 2 fixes performSearch/fetchOffers. |
</threat_model>

<verification>
After all tasks complete:
1. Build compiles: `./gradlew :app:compileDebugKotlin`
2. Verify no password in logs: `grep -rn "Log\." AuthScreens.kt | grep -i password` returns empty
3. Login with wrong password → "Invalid email or password" shown
4. Login offline → "Connection error. Check your internet." shown
5. Register with nome="Mario", cognome="Rossi", email="test@test.it", allergens=[GLUTINE] → Firebase node contains all fields
6. Session persists after registration and login
</verification>

<success_criteria>
- [ ] No password in any Log.d or Log.e call (RNF-SEC-001)
- [ ] LoginScreen shows "Connection error. Check your internet." on network failure
- [ ] LoginScreen saves session on successful login
- [ ] RegistrationScreen has nome and cognome fields
- [ ] RegistrationScreen has AllergeneChipSelector
- [ ] RegistrationScreen validation includes nome and cognome
- [ ] Firebase userData includes nome, cognome, allergeni
- [ ] allergeni stored as List of uppercase strings (e.g., ["GLUTINE"])
- [ ] Error message is "Email already in use" (not "Email already registered")
- [ ] RegistrationScreen saves session on success
- [ ] Build compiles successfully
</success_criteria>

<output>
After completion, create `.planning/phases/01-identity-module/01-D-SUMMARY.md`
</output>
