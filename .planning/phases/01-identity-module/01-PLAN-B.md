---
phase: 01-identity-module
plan: B
type: execute
wave: 2
depends_on: [01-A]
files_modified:
  - app/src/main/java/com/example/ingredient/MainActivity.kt
  - app/src/main/java/com/example/ingredient/AuthScreens.kt
autonomous: true
requirements: [REQ-ID-001, RNF-PRIVACY-001]

must_haves:
  truths:
    - "First app launch shows DisclaimerScreen before any other content"
    - "Tap 'I understand' saves disclaimer_accepted=true and navigates to Login"
    - "Second launch skips DisclaimerScreen if disclaimer already accepted"
    - "Back button on DisclaimerScreen closes app (not navigate back)"
    - "Existing session resumes to correct screen on app launch"
  artifacts:
    - path: "app/src/main/java/com/example/ingredient/AuthScreens.kt"
      provides: "DisclaimerScreen composable"
      contains: "fun DisclaimerScreen"
    - path: "app/src/main/java/com/example/ingredient/MainActivity.kt"
      provides: "Session-aware startup routing"
      contains: "initialScreen"
  key_links:
    - from: "MainActivity.kt"
      to: "SessionManager"
      via: "reads session before setContent"
      pattern: "SessionManager\\(applicationContext\\)"
    - from: "IngredientApp"
      to: "DisclaimerScreen"
      via: "when(currentScreen) case"
      pattern: '"disclaimer" -> DisclaimerScreen'
---

<objective>
Add DisclaimerScreen composable and MainActivity startup routing. First launch shows disclaimer, subsequent launches skip it. Session persistence routes logged-in users directly to their home screen.

Purpose: REQ-ID-001 (Disclaimer first run) and session resume (REQ-ID-006) require the app to read SharedPreferences BEFORE rendering, and pass the correct initialScreen to IngredientApp.

Output: DisclaimerScreen composable in AuthScreens.kt, modified MainActivity.kt with session-aware routing.
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
@app/src/main/java/com/example/ingredient/MainActivity.kt
@app/src/main/java/com/example/ingredient/AuthScreens.kt

# Interfaces from Plan A
<interfaces>
From model/SessionManager.kt (created in Plan A):
```kotlin
class SessionManager(context: Context) {
    fun isDisclaimerAccepted(): Boolean
    fun setDisclaimerAccepted()
    fun isLoggedIn(): Boolean
    fun getUserId(): String
    fun getUserType(): String
}
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add DisclaimerScreen composable to AuthScreens.kt</name>
  <files>app/src/main/java/com/example/ingredient/AuthScreens.kt</files>
  <read_first>
    - app/src/main/java/com/example/ingredient/AuthScreens.kt (entire file — understand existing structure)
    - .planning/phases/01-identity-module/01-PATTERNS.md (lines 253-320 — DisclaimerScreen pattern)
    - .planning/phases/01-identity-module/01-CONTEXT.md (lines 41-46 — exact disclaimer text)
  </read_first>
  <action>
Add DisclaimerScreen composable at the TOP of AuthScreens.kt (before LoginScreen).

1. Add import at top of file: `import androidx.activity.compose.BackHandler`
2. Add DisclaimerScreen composable with signature:
   ```kotlin
   @Composable
   fun DisclaimerScreen(
       onAccepted: () -> Unit,
       modifier: Modifier = Modifier
   )
   ```
3. Inside DisclaimerScreen:
   - Get activity reference: `val activity = LocalContext.current as? android.app.Activity`
   - Add BackHandler: `BackHandler { activity?.finish() }` — closes app on back press
   - Column layout with `modifier.fillMaxSize().padding(24.dp)`, centered vertically and horizontally
   - Title: `Text("Important Notice", style = MaterialTheme.typography.headlineMedium)`
   - Spacer(24.dp)
   - Body text (EXACT): "This app is a support tool only. Always verify ingredients and allergen information directly with the restaurant before ordering. We do not guarantee the accuracy of the data shown in this app."
   - Spacer(32.dp)
   - Button with `onClick = onAccepted`, `Modifier.fillMaxWidth()`, label "I understand"

Do NOT modify any existing composables in this task.
  </action>
  <verify>
    <automated>grep -c "fun DisclaimerScreen" app/src/main/java/com/example/ingredient/AuthScreens.kt</automated>
  </verify>
  <acceptance_criteria>
    - AuthScreens.kt contains `import androidx.activity.compose.BackHandler`
    - AuthScreens.kt contains `@Composable fun DisclaimerScreen(`
    - AuthScreens.kt contains `onAccepted: () -> Unit`
    - AuthScreens.kt contains `BackHandler { activity?.finish() }`
    - AuthScreens.kt contains text "This app is a support tool only"
    - AuthScreens.kt contains button text "I understand"
    - LoginScreen and RegistrationScreen remain unchanged
  </acceptance_criteria>
  <done>DisclaimerScreen composable added to AuthScreens.kt with back-closes-app behavior</done>
</task>

<task type="auto">
  <name>Task 2: Modify MainActivity to read session before setContent</name>
  <files>app/src/main/java/com/example/ingredient/MainActivity.kt</files>
  <read_first>
    - app/src/main/java/com/example/ingredient/MainActivity.kt (lines 35-65 — onCreate and setContent)
    - .planning/phases/01-identity-module/01-PATTERNS.md (lines 162-246 — MainActivity modification pattern)
    - tasks/CONTRACT.md (lines 172-184 — initialScreen routing logic)
  </read_first>
  <action>
Modify MainActivity.kt to read session BEFORE setContent and pass initialScreen.

1. Add import at top: `import com.example.ingredient.model.SessionManager`

2. In `onCreate`, AFTER `tesseractManager = EnhancedTesseractManager(applicationContext)` and BEFORE `setContent`:
   Add these lines:
   ```kotlin
   val sessionManager = SessionManager(applicationContext)
   val initialScreen: String = when {
       !sessionManager.isDisclaimerAccepted() -> "disclaimer"
       sessionManager.isLoggedIn() -> if (sessionManager.getUserType() == "Ristoratore") "Ristoratore" else "Cliente"
       else -> "Login"
   }
   ```

3. Change the `setContent` call to pass `initialScreen`:
   ```kotlin
   setContent {
       IngredientTheme {
           IngredientApp(tesseractManager, databaseReference, initialScreen)
       }
   }
   ```

Do NOT modify IngredientApp composable in this task (next task).
  </action>
  <verify>
    <automated>grep -c "SessionManager(applicationContext)" app/src/main/java/com/example/ingredient/MainActivity.kt</automated>
  </verify>
  <acceptance_criteria>
    - MainActivity.kt contains `import com.example.ingredient.model.SessionManager`
    - MainActivity.kt contains `val sessionManager = SessionManager(applicationContext)`
    - MainActivity.kt contains `val initialScreen: String = when {`
    - MainActivity.kt contains `!sessionManager.isDisclaimerAccepted() -> "disclaimer"`
    - MainActivity.kt contains `sessionManager.isLoggedIn()`
    - MainActivity.kt contains `IngredientApp(tesseractManager, databaseReference, initialScreen)`
  </acceptance_criteria>
  <done>MainActivity reads session before setContent and passes initialScreen to IngredientApp</done>
</task>

<task type="auto">
  <name>Task 3: Modify IngredientApp to accept initialScreen and route to DisclaimerScreen</name>
  <files>app/src/main/java/com/example/ingredient/MainActivity.kt</files>
  <read_first>
    - app/src/main/java/com/example/ingredient/MainActivity.kt (lines 67-138 — IngredientApp composable)
    - .planning/phases/01-identity-module/01-PATTERNS.md (lines 207-240 — IngredientApp signature change and disclaimer route)
    - tasks/CONTRACT.md (lines 187-192 — IngredientApp new signature)
  </read_first>
  <action>
Modify IngredientApp composable in MainActivity.kt:

1. Change function signature to add `initialScreen` parameter:
   ```kotlin
   @Composable
   fun IngredientApp(
       tesseractManager: EnhancedTesseractManager,
       databaseReference: DatabaseReference,
       initialScreen: String = "Login"
   )
   ```

2. Change the `currentScreen` state initialization from:
   `var currentScreen by remember { mutableStateOf("Login") }`
   To:
   `var currentScreen by remember { mutableStateOf(initialScreen) }`

3. Add new case in the `when(currentScreen)` block BEFORE the "Login" case:
   ```kotlin
   "disclaimer" -> DisclaimerScreen(
       onAccepted = {
           val sessionManager = SessionManager(LocalContext.current)
           sessionManager.setDisclaimerAccepted()
           currentScreen = "Login"
       },
       modifier = Modifier.padding(innerPadding)
   )
   ```

4. Add import if not present: `import com.example.ingredient.model.SessionManager`

The "disclaimer" case must:
- Call `sessionManager.setDisclaimerAccepted()` to persist the flag
- Navigate to "Login" after acceptance
  </action>
  <verify>
    <automated>grep -c '"disclaimer" -> DisclaimerScreen' app/src/main/java/com/example/ingredient/MainActivity.kt</automated>
  </verify>
  <acceptance_criteria>
    - IngredientApp signature includes `initialScreen: String = "Login"`
    - `currentScreen` initialized with `mutableStateOf(initialScreen)` not `mutableStateOf("Login")`
    - when block contains `"disclaimer" -> DisclaimerScreen(`
    - DisclaimerScreen onAccepted calls `sessionManager.setDisclaimerAccepted()`
    - DisclaimerScreen onAccepted sets `currentScreen = "Login"`
  </acceptance_criteria>
  <done>IngredientApp accepts initialScreen parameter and routes to DisclaimerScreen when needed</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| SharedPreferences read | Session data read at app startup |
| User input | None in this plan |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-01-B-01 | Tampering | disclaimer_accepted flag in SharedPreferences | accept | Rooted device could bypass disclaimer. Low risk: disclaimer is informational, not a security gate. |
| T-01-B-02 | Spoofing | Session userId could be tampered on rooted device | accept | v1 accepted risk per STATE.md. Firebase Security Rules in v2 will validate user identity server-side. |
</threat_model>

<verification>
After all tasks complete:
1. Build compiles: `./gradlew :app:compileDebugKotlin`
2. Fresh install shows DisclaimerScreen first
3. Tap "I understand" → navigates to LoginScreen
4. Kill app, reopen → LoginScreen shown (disclaimer skipped)
5. Back on DisclaimerScreen → app closes
</verification>

<success_criteria>
- [ ] DisclaimerScreen composable exists in AuthScreens.kt
- [ ] BackHandler closes app on DisclaimerScreen
- [ ] Disclaimer text matches exact spec
- [ ] MainActivity reads session before setContent
- [ ] IngredientApp accepts initialScreen parameter
- [ ] "disclaimer" route added to when block
- [ ] disclaimer_accepted saved on "I understand" tap
- [ ] Build compiles successfully
</success_criteria>

<output>
After completion, create `.planning/phases/01-identity-module/01-B-SUMMARY.md`
</output>
