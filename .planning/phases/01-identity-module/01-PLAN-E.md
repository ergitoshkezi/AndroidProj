---
phase: 01-identity-module
plan: E
type: execute
wave: 3
depends_on: [01-A, 01-C]
files_modified:
  - app/src/main/java/com/example/ingredient/ClienteScreens.kt
  - app/src/main/java/com/example/ingredient/MainActivity.kt
autonomous: true
requirements: [REQ-ID-005, REQ-ID-007]

must_haves:
  truths:
    - "ProfileScreen is the 3rd tab in ClienteScreen bottom navigation"
    - "ProfileScreen loads user data (nome, cognome, email, allergeni) from Firebase"
    - "ProfileScreen shows nome, cognome, email as non-editable Text"
    - "ProfileScreen shows allergeni in AllergeneChipSelector (editable)"
    - "Save button updates only allergeni in Firebase"
    - "Snackbar shows 'Saved!' on success, 'Could not save. Try again.' on failure"
    - "Logout button clears session and navigates to Login with clean back stack"
  artifacts:
    - path: "app/src/main/java/com/example/ingredient/ClienteScreens.kt"
      provides: "ProfileScreen composable and 3rd tab"
      contains: "fun ProfileScreen"
  key_links:
    - from: "ClienteScreen"
      to: "ProfileScreen"
      via: "NavigationBarItem selectedTab == 2"
      pattern: "selectedTab == 2"
    - from: "ProfileScreen"
      to: "Firebase users/{userId}"
      via: "addListenerForSingleValueEvent"
      pattern: 'child\\("users"\\)\\.child\\(userId\\)'
---

<objective>
Add ProfileScreen as the 3rd tab in ClienteScreen bottom navigation. ProfileScreen loads user data, shows nome/cognome/email read-only, allows editing allergeni via AllergeneChipSelector, and has logout functionality.

Purpose: REQ-ID-005 (Profile Screen) and REQ-ID-007 (Logout) complete the customer identity experience.

Output: ProfileScreen composable with Firebase load/save, integrated as 3rd tab with logout.
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
@app/src/main/java/com/example/ingredient/ClienteScreens.kt
@app/src/main/java/com/example/ingredient/MainActivity.kt

# Interfaces from Plan A and C
<interfaces>
From model/AllergeneType.kt:
```kotlin
enum class AllergeneType(val displayName: String) {
    GLUTINE("Glutine"), CROSTACEI("Crostacei"), UOVA("Uova"), PESCE("Pesce"),
    ARACHIDI("Arachidi"), SOIA("Soia"), LATTE("Latte"), FRUTTA_SECCA("Frutta a guscio"),
    SEDANO("Sedano"), SENAPE("Senape"), SESAMO("Sesamo"),
    ANIDRIDE_SOLFOROSA("Anidride solforosa e solfiti"), LUPINI("Lupini"), MOLLUSCHI("Molluschi")
}
```

From model/SessionManager.kt:
```kotlin
class SessionManager(context: Context) {
    fun logout()  // removes session_user_id and session_user_type
}
```

From AuthScreens.kt (Plan C):
```kotlin
@Composable
fun AllergeneChipSelector(
    selected: List<AllergeneType>,
    onSelectionChange: (List<AllergeneType>) -> Unit,
    modifier: Modifier = Modifier
)
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add required imports to ClienteScreens.kt</name>
  <files>app/src/main/java/com/example/ingredient/ClienteScreens.kt</files>
  <read_first>
    - app/src/main/java/com/example/ingredient/ClienteScreens.kt (lines 1-30 — existing imports)
    - .planning/phases/01-identity-module/01-PATTERNS.md (lines 700-710 — imports for ProfileScreen)
  </read_first>
  <action>
Add required imports to ClienteScreens.kt for ProfileScreen:

Add these imports at the top of the file (after existing imports):
```kotlin
import android.util.Log
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import com.example.ingredient.model.AllergeneType
import com.example.ingredient.model.SessionManager
import kotlinx.coroutines.launch
```

Note: Some may already exist. Add only what's missing.
  </action>
  <verify>
    <automated>grep -c "import com.example.ingredient.model.AllergeneType" app/src/main/java/com/example/ingredient/ClienteScreens.kt</automated>
  </verify>
  <acceptance_criteria>
    - ClienteScreens.kt contains `import com.example.ingredient.model.AllergeneType`
    - ClienteScreens.kt contains `import com.example.ingredient.model.SessionManager`
    - ClienteScreens.kt contains `import androidx.compose.material3.SnackbarHostState`
    - ClienteScreens.kt contains `import kotlinx.coroutines.launch`
  </acceptance_criteria>
  <done>Required imports added to ClienteScreens.kt</done>
</task>

<task type="auto">
  <name>Task 2: Add 3rd tab (Profile) to ClienteScreen bottom navigation</name>
  <files>app/src/main/java/com/example/ingredient/ClienteScreens.kt</files>
  <read_first>
    - app/src/main/java/com/example/ingredient/ClienteScreens.kt (lines 43-90 — ClienteScreen with NavigationBar)
    - .planning/phases/01-identity-module/01-PATTERNS.md (lines 660-698 — 3rd tab addition pattern)
  </read_first>
  <action>
Add Profile tab to ClienteScreen NavigationBar:

1. In the NavigationBar block (around lines 67-80), ADD a third NavigationBarItem after the Offers item:
   ```kotlin
   NavigationBarItem(
       selected = selectedTab == 2,
       onClick = { selectedTab = 2 },
       icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
       label = { Text("Profile") }
   )
   ```

2. In the `when(selectedTab)` block (around lines 85-88), ADD case for tab 2:
   ```kotlin
   2 -> ProfileScreen(
       userId = userId ?: "",
       databaseReference = databaseReference,
       onLogout = onLogout,
       currentModifier
   )
   ```

Note: `Icons.Default.Person` should already be available from existing imports.
  </action>
  <verify>
    <automated>grep -c "selectedTab == 2" app/src/main/java/com/example/ingredient/ClienteScreens.kt</automated>
  </verify>
  <acceptance_criteria>
    - ClienteScreen contains NavigationBarItem with `selected = selectedTab == 2`
    - NavigationBarItem has `icon = { Icon(Icons.Default.Person`
    - NavigationBarItem has `label = { Text("Profile") }`
    - when block contains `2 -> ProfileScreen(`
    - ProfileScreen receives `userId`, `databaseReference`, `onLogout`
  </acceptance_criteria>
  <done>Profile tab added as 3rd item in ClienteScreen bottom navigation</done>
</task>

<task type="auto">
  <name>Task 3: Create ProfileScreen composable</name>
  <files>app/src/main/java/com/example/ingredient/ClienteScreens.kt</files>
  <read_first>
    - app/src/main/java/com/example/ingredient/ClienteScreens.kt (end of file — where to add ProfileScreen)
    - .planning/phases/01-identity-module/01-PATTERNS.md (lines 510-614 — full ProfileScreen implementation)
    - .planning/phases/01-identity-module/01-CONTEXT.md (lines 121-131 — ProfileScreen spec)
  </read_first>
  <action>
Add ProfileScreen composable at the END of ClienteScreens.kt:

```kotlin
@Composable
fun ProfileScreen(
    userId: String,
    databaseReference: DatabaseReference,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nome by remember { mutableStateOf("") }
    var cognome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var selectedAllergens by remember { mutableStateOf<List<AllergeneType>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val sessionManager = SessionManager(context)

    // Load user data once
    LaunchedEffect(userId) {
        databaseReference.child("users").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    nome = snapshot.child("nome").getValue(String::class.java) ?: ""
                    cognome = snapshot.child("cognome").getValue(String::class.java) ?: ""
                    email = snapshot.child("email").getValue(String::class.java) ?: ""
                    val rawAllergens = snapshot.child("allergeni")
                        .children.mapNotNull { it.getValue(String::class.java) }
                    selectedAllergens = rawAllergens.mapNotNull {
                        runCatching { AllergeneType.valueOf(it) }.getOrNull()
                    }
                    isLoading = false
                }
                override fun onCancelled(error: DatabaseError) {
                    isLoading = false
                    Log.e("ProfileScreen", "Failed to load profile: ${error.message}")
                }
            })
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Profile", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(24.dp))
                
                // Non-editable profile fields
                Text("Nome: $nome", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Cognome: $cognome", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Email: $email", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(24.dp))

                // Allergen chip selector
                Text("My Allergens:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                AllergeneChipSelector(
                    selected = selectedAllergens,
                    onSelectionChange = { selectedAllergens = it }
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Save button
                Button(
                    onClick = {
                        databaseReference.child("users").child(userId)
                            .child("allergeni")
                            .setValue(selectedAllergens.map { it.name })
                            .addOnSuccessListener {
                                coroutineScope.launch { snackbarHostState.showSnackbar("Saved!") }
                            }
                            .addOnFailureListener { e ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Could not save. Try again.")
                                }
                                Log.e("ProfileScreen", "Failed to save allergens", e)
                            }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Logout button
                OutlinedButton(
                    onClick = {
                        sessionManager.logout()
                        onLogout()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Logout")
                }
            }
        }
    }
}
```

Key implementation details:
- `addListenerForSingleValueEvent` for one-time data load (brownfield pattern)
- `snapshot.child("allergeni").children` reads Firebase List format
- `AllergeneType.valueOf(it)` parses uppercase strings back to enum
- `runCatching { }.getOrNull()` safely ignores unknown allergen values
- Save only updates `users/{userId}/allergeni` (not full node)
- `sessionManager.logout()` clears SharedPreferences before onLogout callback
  </action>
  <verify>
    <automated>grep -c "fun ProfileScreen" app/src/main/java/com/example/ingredient/ClienteScreens.kt</automated>
  </verify>
  <acceptance_criteria>
    - ClienteScreens.kt contains `@Composable fun ProfileScreen(`
    - ProfileScreen has parameters `userId: String`, `databaseReference: DatabaseReference`, `onLogout: () -> Unit`
    - ProfileScreen uses `databaseReference.child("users").child(userId)`
    - ProfileScreen contains `Text("Nome: $nome"`
    - ProfileScreen contains `Text("Cognome: $cognome"`
    - ProfileScreen contains `Text("Email: $email"`
    - ProfileScreen contains `AllergeneChipSelector(`
    - Save button writes to `.child("allergeni")`
    - Snackbar shows `"Saved!"` on success
    - Snackbar shows `"Could not save. Try again."` on failure
    - Logout button calls `sessionManager.logout()` then `onLogout()`
  </acceptance_criteria>
  <done>ProfileScreen composable created with Firebase load/save, allergeni editing, and logout</done>
</task>

<task type="auto">
  <name>Task 4: Update IngredientApp logout to clear session and back stack</name>
  <files>app/src/main/java/com/example/ingredient/MainActivity.kt</files>
  <read_first>
    - app/src/main/java/com/example/ingredient/MainActivity.kt (lines 96-107 — ClienteScreen onLogout callback)
    - .planning/phases/01-identity-module/01-CONTEXT.md (lines 132-135 — session clear on logout)
    - tasks/CONTRACT.md (lines 163-165 — back stack rules)
  </read_first>
  <action>
The logout flow is already handled:
1. ProfileScreen calls `sessionManager.logout()` (clears SharedPreferences)
2. ProfileScreen calls `onLogout()` callback
3. IngredientApp `onLogout` sets `currentScreen = "Login"`

VERIFY the existing onLogout callback in IngredientApp ClienteScreen block (lines 100-105):
```kotlin
onLogout = {
    currentUserId = null
    currentUserEmail = null
    userType = ""
    currentScreen = "Login"
},
```

This clears Compose state. SharedPreferences is cleared in ProfileScreen.

VERIFY back navigation: When `currentScreen = "Login"`, there's no back stack to Cliente.
The user pressing back on LoginScreen will:
- Not show DisclaimerScreen (disclaimer_accepted is preserved)
- Close the app (default Android behavior)

No changes needed — verify existing behavior satisfies REQ-ID-007.
  </action>
  <verify>
    <automated>grep -c 'currentScreen = "Login"' app/src/main/java/com/example/ingredient/MainActivity.kt</automated>
  </verify>
  <acceptance_criteria>
    - IngredientApp ClienteScreen has `onLogout = {` callback
    - onLogout sets `currentScreen = "Login"`
    - onLogout clears `currentUserId = null`
    - Session is cleared by ProfileScreen before onLogout call
  </acceptance_criteria>
  <done>Verified logout flow clears session and navigates to Login (REQ-ID-007)</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| User → Firebase | Allergen update written to users/{userId}/allergeni |
| Firebase → User | Profile data read for display |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-01-E-01 | Information Disclosure | ProfileScreen reads own user node | accept | Reads only `users/{userId}` where userId is from session. No cross-user access. |
| T-01-E-02 | Tampering | User could modify own allergeni | accept | Expected behavior. Users control their own allergen preferences. |
| T-01-E-03 | Spoofing | Attacker could set any userId in SharedPreferences (rooted) | accept | v1 accepted risk. No Firebase Security Rules. v2 adds Firebase Auth + Rules. |
</threat_model>

<verification>
After all tasks complete:
1. Build compiles: `./gradlew :app:compileDebugKotlin`
2. ClienteScreen has 3 tabs: Search, Offers, Profile
3. Profile tab shows CircularProgressIndicator while loading
4. Profile shows nome/cognome/email as text (not editable)
5. Profile shows AllergeneChipSelector with user's saved allergens
6. Tap Save → Snackbar "Saved!" → Firebase allergeni updated
7. Tap Logout → navigates to LoginScreen
8. After logout, back button closes app
</verification>

<success_criteria>
- [ ] ProfileScreen composable exists in ClienteScreens.kt
- [ ] Profile tab is 3rd item in NavigationBar
- [ ] Profile tab icon is Person
- [ ] ProfileScreen loads nome/cognome/email/allergeni from Firebase
- [ ] nome/cognome/email displayed as Text (read-only)
- [ ] allergeni displayed in AllergeneChipSelector (editable)
- [ ] Save button updates only allergeni in Firebase
- [ ] Snackbar shows "Saved!" on success
- [ ] Snackbar shows "Could not save. Try again." on failure
- [ ] Logout clears session and navigates to Login
- [ ] Build compiles successfully
</success_criteria>

<output>
After completion, create `.planning/phases/01-identity-module/01-E-SUMMARY.md`
</output>
