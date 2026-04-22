# Phase 1: Identity Module — Pattern Map

**Mapped:** 2026-04-22
**Files analyzed:** 6 (3 new, 3 modified)
**Analogs found:** 6 / 6

---

## File Classification

| New / Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `model/AllergeneType.kt` | enum | transform | `MenuParser.kt` (data classes) | partial-match (same package style, no enum exists) |
| `model/User.kt` | data class | transform | `MenuParser.kt` (`MenuItem`, `MenuCategory`) | exact |
| `model/SessionManager.kt` | utility / SharedPreferences wrapper | request-response | `IngredientApplication.kt` (Context usage) | partial-match (no SP wrapper exists) |
| `MainActivity.kt` | entry point / bootstrap | request-response | `MainActivity.kt` itself (lines 40–65) | self-modification |
| `AuthScreens.kt` | Composable screen | CRUD + request-response | `AuthScreens.kt` itself (lines 1–332) | self-modification (extend) |
| `ClienteScreens.kt` | Composable screen | CRUD + request-response | `ClienteScreens.kt` `OffersTab` (lines 287–336) | exact |

---

## Pattern Assignments

### `model/AllergeneType.kt` (enum, transform)

**Analog:** `MenuParser.kt` — data class block (lines 7–21); no existing enum in codebase.

**Package / file header pattern** — copy from `MenuParser.kt` lines 1–6:
```kotlin
package com.example.ingredient.model

// No external imports needed for a simple enum with a display-name property
```

**Enum with display-name property** — no direct codebase analog; use standard Kotlin enum pattern:
```kotlin
enum class AllergeneType(val displayName: String) {
    GLUTINE("Glutine"),
    CROSTACEI("Crostacei"),
    UOVA("Uova"),
    PESCE("Pesce"),
    ARACHIDI("Arachidi"),
    SOIA("Soia"),
    LATTE("Latte"),
    FRUTTA_SECCA("Frutta a guscio"),
    SEDANO("Sedano"),
    SENAPE("Senape"),
    SESAMO("Sesamo"),
    ANIDRIDE_SOLFOROSA("Anidride solforosa e solfiti"),
    LUPINI("Lupini"),
    MOLLUSCHI("Molluschi")
}
```

**Convention notes:**
- Package must be `com.example.ingredient.model` (locked — STATE.md)
- Firebase storage: use `.name` (e.g., `"GLUTINE"`) — never `.displayName`
- No companion object / no constants file — inline vals follow project convention (`CONVENTIONS.md` lines 28–35)

---

### `model/User.kt` (data class, transform)

**Analog:** `MenuParser.kt` — `MenuItem` data class (lines 7–16) and `MenuCategory` (lines 18–21)

**Data class pattern** (copy from `MenuParser.kt` lines 7–16):
```kotlin
data class MenuItem(
    val name: String = "",
    val description: String = "",
    val allergens: String = "",
    val price: String = "",
    val originalPrice: String = "",
    val isOffer: Boolean = false,
    val country: String = "",
    val region: String = ""
)
```

**Apply as** (exact spec from CONTEXT.md lines 86–97):
```kotlin
package com.example.ingredient.model

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
```

**Convention notes:**
- All fields have default values (consistent with `MenuItem`) — required for Firebase `getValue(User::class.java)` deserialization
- `List<String>` for `allergeni` — consistent with Firebase RTDB array serialization in the project
- PascalCase file name, camelCase fields (`CONVENTIONS.md` lines 23–24)
- No KDoc on data class — project only uses KDoc on methods/functions (`CONVENTIONS.md` lines 195–204)

---

### `model/SessionManager.kt` (utility, request-response)

**Analog:** `IngredientApplication.kt` (lines 1–24) — closest for Context-aware class structure. No SharedPreferences wrapper exists in codebase.

**Class structure pattern** — copy from `IngredientApplication.kt` lines 10–11 (Context-holding class):
```kotlin
class IngredientApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

**Apply as** — SharedPreferences wrapper class:
```kotlin
package com.example.ingredient.model

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ingredient_session", Context.MODE_PRIVATE)

    fun saveSession(userId: String, userType: String) {
        prefs.edit()
            .putString("session_user_id", userId)
            .putString("session_user_type", userType)
            .apply()
    }

    fun getUserId(): String = prefs.getString("session_user_id", "") ?: ""

    fun getUserType(): String = prefs.getString("session_user_type", "") ?: ""

    fun isLoggedIn(): Boolean = getUserId().isNotEmpty()

    fun logout() {
        prefs.edit()
            .remove("session_user_id")
            .remove("session_user_type")
            .apply()
    }

    fun isDisclaimerAccepted(): Boolean = prefs.getBoolean("disclaimer_accepted", false)

    fun setDisclaimerAccepted() {
        prefs.edit().putBoolean("disclaimer_accepted", true).apply()
    }
}
```

**Convention notes:**
- SharedPreferences file name `"ingredient_session"` — locked (STATE.md)
- Keys `"session_user_id"`, `"session_user_type"`, `"disclaimer_accepted"` — locked (STATE.md)
- No TAG / no Log calls needed — pure data accessors
- Constructor takes `Context` directly — project never uses DI (ARCHITECTURE.md lines 161–167)
- Instantiate in `MainActivity.onCreate` before `setContent`, pass down as needed

---

### `MainActivity.kt` (entry point — modify existing)

**Analog:** `MainActivity.kt` itself — `onCreate` block (lines 40–65) + `IngredientApp` composable (lines 68–138)

**Current `onCreate` pattern** (lines 40–65) — extend, do NOT rewrite:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    try {
        databaseReference = FirebaseDatabase.getInstance().getReference()
        Log.d("MainActivity", "Firebase Database initialized successfully")
    } catch (e: Exception) {
        Log.e("MainActivity", "Error initializing Firebase Database", e)
        Toast.makeText(this, "Firebase initialization failed", Toast.LENGTH_SHORT).show()
    }

    tesseractManager = EnhancedTesseractManager(applicationContext)

    setContent {
        IngredientTheme {
            IngredientApp(tesseractManager, databaseReference)
        }
    }
}
```

**What to ADD in `onCreate`** — read SharedPreferences BEFORE `setContent`:
```kotlin
// Read session BEFORE setContent — pattern: inline val at top of onCreate block
val sessionManager = SessionManager(applicationContext)
val initialScreen: String = when {
    !sessionManager.isDisclaimerAccepted() -> "disclaimer"
    sessionManager.isLoggedIn() -> if (sessionManager.getUserType() == "Cliente") "Cliente" else "Ristoratore"
    else -> "Login"
}

setContent {
    IngredientTheme {
        IngredientApp(tesseractManager, databaseReference, initialScreen)
    }
}
```

**Current `IngredientApp` signature** (line 68) — must extend to accept `initialScreen`:
```kotlin
// BEFORE:
fun IngredientApp(tesseractManager: EnhancedTesseractManager, databaseReference: DatabaseReference)

// AFTER (add third parameter):
fun IngredientApp(
    tesseractManager: EnhancedTesseractManager,
    databaseReference: DatabaseReference,
    initialScreen: String = "Login"
)
```

**Current screen state initialization** (line 69) — change default:
```kotlin
// BEFORE:
var currentScreen by remember { mutableStateOf("Login") }

// AFTER:
var currentScreen by remember { mutableStateOf(initialScreen) }
```

**New "disclaimer" case in `when(currentScreen)` block** — copy `"Login"` case pattern (lines 76–85) as template:
```kotlin
"disclaimer" -> DisclaimerScreen(
    onAccepted = { currentScreen = "Login" },
    modifier = Modifier.padding(innerPadding)
)
```

**BackHandler import** — add to imports:
```kotlin
import androidx.activity.compose.BackHandler
```

**Convention notes:**
- Minimal diff — add fields/cases, do NOT restructure existing `when` block
- Import order: androidx first, then com.example.ingredient.model (CONVENTIONS.md lines 51–57)
- `sessionManager` is a local val inside `onCreate`, not a class field — consistent with inline construction pattern

---

### `AuthScreens.kt` (Composable screens — modify existing)

**Analog:** `AuthScreens.kt` itself — `LoginScreen` (lines 22–139) and `RegistrationScreen` (lines 142–332)

#### `DisclaimerScreen` — NEW composable to add at top of file

**Screen composable structure pattern** — copy from `LoginScreen` (lines 22–139):
```kotlin
@Composable
fun LoginScreen(
    databaseReference: DatabaseReference,
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. State declarations
    var email by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val context = LocalContext.current

    // 2. Root layout
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) { ... }
}
```

**Apply as** (DisclaimerScreen — no Firebase, no loading state):
```kotlin
@Composable
fun DisclaimerScreen(
    onAccepted: () -> Unit,
    modifier: Modifier = Modifier
) {
    // BackHandler to close app instead of navigate back
    // (add androidx.activity.compose.BackHandler import)
    val activity = LocalContext.current as? android.app.Activity
    BackHandler { activity?.finish() }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Important Notice", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "This app is a support tool only. Always verify ingredients and " +
                "allergen information directly with the restaurant before ordering. " +
                "We do not guarantee the accuracy of the data shown in this app.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onAccepted,          // caller saves disclaimer_accepted = true
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("I understand")
        }
    }
}
```

**Convention notes:**
- `onAccepted` lambda — caller (`IngredientApp`) calls `sessionManager.setDisclaimerAccepted()` then sets `currentScreen = "Login"`
- No `databaseReference` parameter — disclaimer does no Firebase work
- `modifier: Modifier = Modifier` last param — project convention (CONVENTIONS.md line 65)
- `LocalContext.current as? Activity` cast for `finish()` — same pattern used in `ClienteScreens.kt` for Toast

#### `LoginScreen` — MODIFY existing (lines 91–122)

**Error message pattern** (already implemented, lines 69–76 + lines 111–113) — copy as template for new error cases:
```kotlin
// Existing inline error display pattern — keep exactly:
if (errorMessage.isNotEmpty()) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = errorMessage,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall
    )
}
```

**What to ADD in `onCancelled`** (line 117–121) — replace generic DB error with user-friendly message:
```kotlin
// BEFORE:
errorMessage = "Database error: ${error.message}"

// AFTER (add connection-error detection):
errorMessage = if (error.code == DatabaseError.NETWORK_ERROR || error.code == DatabaseError.DISCONNECTED) {
    "Connection error. Check your internet."
} else {
    "Database error: ${error.message}"
}
```

**Security fix** — remove password from Log.d (CRITICAL-SEC-002 / RNF-SEC-001):
```kotlin
// Line 104 — KEEP (no password in log):
Log.d("LoginScreen", "Login successful for user: $userId")
// ✅ Already safe

// Ensure line 98 (dbPassword) is NEVER logged — verify no Log.d contains `password`
```

#### `RegistrationScreen` — MODIFY existing (lines 142–332)

**New state variables to ADD** at top of composable body (copy pattern from lines 148–154):
```kotlin
// ADD after existing state declarations:
var nome by remember { mutableStateOf("") }
var cognome by remember { mutableStateOf("") }
var selectedAllergens by remember { mutableStateOf<List<AllergeneType>>(emptyList()) }
```

**New fields to ADD** — copy `OutlinedTextField` pattern from lines 170–177:
```kotlin
OutlinedTextField(
    value = nome,
    onValueChange = { nome = it },
    label = { Text("Nome") },
    modifier = Modifier.fillMaxWidth(),
    enabled = !isLoading
)
Spacer(modifier = Modifier.height(16.dp))
OutlinedTextField(
    value = cognome,
    onValueChange = { cognome = it },
    label = { Text("Cognome") },
    modifier = Modifier.fillMaxWidth(),
    enabled = !isLoading
)
```

**`AllergeneChipSelector` to ADD** — placed after cognome field, before user-type selector:
```kotlin
Spacer(modifier = Modifier.height(16.dp))
Text("Allergens (optional):", style = MaterialTheme.typography.bodyLarge)
Spacer(modifier = Modifier.height(8.dp))
AllergeneChipSelector(
    selected = selectedAllergens,
    onSelectionChange = { selectedAllergens = it }
)
```

**Validation to ADD** in button `onClick` (copy pattern from lines 244–258):
```kotlin
// ADD before existing blank-check:
if (nome.isBlank() || cognome.isBlank()) {
    errorMessage = "Please fill in all fields"
    return@Button
}
```

**`userData` map to UPDATE** (lines 285–290) — add nome, cognome, allergeni:
```kotlin
// BEFORE:
val userData = mapOf(
    "email" to email,
    "password" to password,
    "userType" to selectedUserType,
    "createdAt" to System.currentTimeMillis()
)

// AFTER (add 3 fields):
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

**Error message fix** — change `"Email already registered"` (line 277) to `"Email already in use"` (matches CONTEXT.md decision + C10 criterion).

**Security fix** — `password` must NEVER appear in `Log.d`/`Log.e`. Verify line 295 (`Log.d("RegistrationScreen", "User registered: $userId")`) — already safe. Verify no `Log.d` prints `password` variable.

**New import to ADD** at top of file:
```kotlin
import com.example.ingredient.model.AllergeneType
```

---

### `ClienteScreens.kt` (Composable screen — modify existing)

**Analog:** `ClienteScreens.kt` — `OffersTab` composable (lines 287–336) and `ClienteScreen` bottom nav (lines 43–90)

#### `ProfileScreen` — NEW composable to add at bottom of file

**Firebase single-value read pattern** (copy from `AuthScreens.kt` lines 91–122):
```kotlin
databaseReference.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
    override fun onDataChange(snapshot: DataSnapshot) {
        isLoading = false
        for (userSnapshot in snapshot.children) {
            val value = userSnapshot.child("fieldName").getValue(String::class.java)
        }
    }
    override fun onCancelled(error: DatabaseError) {
        isLoading = false
        errorMessage = "Database error: ${error.message}"
        Log.e("TAG", "Database error: ${error.message}")
    }
})
```

**Loading state pattern** (copy from `OffersTab` lines 292–300):
```kotlin
var isLoading by remember { mutableStateOf(true) }

LaunchedEffect(userId) {
    isLoading = true
    // ... firebase call sets isLoading = false in callbacks
}
```

**Loading indicator pattern** (copy from `OffersTab` lines 317–320):
```kotlin
if (isLoading) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
```

**Snackbar pattern** — no existing Snackbar in codebase; use `SnackbarHost` inside `Scaffold`:
```kotlin
val snackbarHostState = remember { SnackbarHostState() }
val coroutineScope = rememberCoroutineScope()

// show on save success:
coroutineScope.launch {
    snackbarHostState.showSnackbar("Saved!")
}
// show on save failure:
coroutineScope.launch {
    snackbarHostState.showSnackbar("Could not save. Try again.")
}
```

**Firebase partial update pattern** — copy `setValue` pattern from `AuthScreens.kt` lines 292–303:
```kotlin
databaseReference.child("users").child(userId).child("allergeni")
    .setValue(selectedAllergens.map { it.name })
    .addOnSuccessListener {
        coroutineScope.launch { snackbarHostState.showSnackbar("Saved!") }
    }
    .addOnFailureListener { e ->
        coroutineScope.launch { snackbarHostState.showSnackbar("Could not save. Try again.") }
        Log.e("ProfileScreen", "Failed to save allergens", e)
    }
```

**Full `ProfileScreen` composable structure**:
```kotlin
@Composable
fun ProfileScreen(
    userId: String,
    databaseReference: DatabaseReference,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    // State
    var nome by remember { mutableStateOf("") }
    var cognome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var selectedAllergens by remember { mutableStateOf<List<AllergeneType>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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
                // Non-editable profile fields
                Text("Nome: $nome", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Cognome: $cognome", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Email: $email", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))

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
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Logout")
                }
            }
        }
    }
}
```

#### `AllergeneChipSelector` — NEW composable to add in `AuthScreens.kt` or a shared file

**`FilterChip` pattern** — already used in `RegistrationScreen` (lines 217–228):
```kotlin
FilterChip(
    selected = selectedUserType == "Cliente",
    onClick = { selectedUserType = "Cliente" },
    label = { Text("Cliente") },
    enabled = !isLoading
)
```

**`AllergeneChipSelector` composable** — uses `FlowRow` (requires `androidx.compose.foundation.layout.FlowRow` experimental or `com.google.accompanist:accompanist-flowlayout`). If `FlowRow` unavailable, use wrapping `Row` + `LazyVerticalGrid`:

```kotlin
// Preferred: place in AuthScreens.kt (used there and in ProfileScreen via import)
@Composable
fun AllergeneChipSelector(
    selected: List<AllergeneType>,
    onSelectionChange: (List<AllergeneType>) -> Unit,
    modifier: Modifier = Modifier
) {
    // FlowRow requires: import androidx.compose.foundation.layout.FlowRow
    // and @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AllergeneType.entries.forEach { allergen ->
            FilterChip(
                selected = allergen in selected,
                onClick = {
                    onSelectionChange(
                        if (allergen in selected) selected - allergen else selected + allergen
                    )
                },
                label = { Text(allergen.displayName) }
            )
        }
    }
}
```

#### `ClienteScreen` — ADD 3rd tab (Profile) to existing bottom nav

**Existing bottom nav pattern** (lines 65–89) — copy/extend exactly:
```kotlin
// BEFORE (2 items):
NavigationBar {
    NavigationBarItem(
        selected = selectedTab == 0,
        onClick = { selectedTab = 0 },
        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
        label = { Text("Search") }
    )
    NavigationBarItem(
        selected = selectedTab == 1,
        onClick = { selectedTab = 1 },
        icon = { Icon(Icons.Default.LocalOffer, contentDescription = "Offers") },
        label = { Text("Offers") }
    )
}

// AFTER — ADD third item:
NavigationBarItem(
    selected = selectedTab == 2,
    onClick = { selectedTab = 2 },
    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
    label = { Text("Profile") }
)
```

**`when(selectedTab)` block to ADD** (lines 85–88):
```kotlin
// ADD case:
2 -> ProfileScreen(
    userId = userId ?: "",
    databaseReference = databaseReference,
    onLogout = onLogout,
    currentModifier
)
```

**New import to ADD** at top of `ClienteScreens.kt`:
```kotlin
import com.example.ingredient.model.AllergeneType
```

---

## Shared Patterns

### State Declaration
**Source:** `AuthScreens.kt` lines 28–32, `ClienteScreens.kt` lines 50–52
**Apply to:** All new/modified composable screens
```kotlin
// Top of every composable body — state before layout:
var someState by remember { mutableStateOf(initialValue) }
var isLoading by remember { mutableStateOf(false) }
var errorMessage by remember { mutableStateOf("") }
val context = LocalContext.current
```

### Error Display
**Source:** `AuthScreens.kt` lines 69–76
**Apply to:** All screens that have `errorMessage` state — LoginScreen, RegistrationScreen
```kotlin
if (errorMessage.isNotEmpty()) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = errorMessage,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall
    )
}
```

### Firebase Single-Read (ValueEventListener)
**Source:** `AuthScreens.kt` lines 91–122 (LoginScreen) and lines 263–315 (RegistrationScreen)
**Apply to:** ProfileScreen (load user data), LoginScreen (connection error handling)
```kotlin
databaseReference.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
    override fun onDataChange(snapshot: DataSnapshot) {
        isLoading = false
        for (userSnapshot in snapshot.children) {
            val value = userSnapshot.child("fieldName").getValue(String::class.java)
        }
    }
    override fun onCancelled(error: DatabaseError) {
        isLoading = false
        errorMessage = "Database error: ${error.message}"
        Log.e("TAG", "Database error: ${error.message}")
    }
})
```

### Firebase Write (setValue + listeners)
**Source:** `AuthScreens.kt` lines 292–303
**Apply to:** ProfileScreen (save allergens), RegistrationScreen (save full user node)
```kotlin
databaseReference.child("users").child(userId).setValue(userData)
    .addOnSuccessListener {
        isLoading = false
        // success action
    }
    .addOnFailureListener { e ->
        isLoading = false
        errorMessage = "..."
        Log.e("TAG", "...", e)
    }
```

### Loading Indicator
**Source:** `ClienteScreens.kt` lines 317–320 (OffersTab)
**Apply to:** ProfileScreen
```kotlin
if (isLoading) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
```

### Security — No Password in Logs
**Source:** `CONTEXT.md` decision RNF-SEC-001; `CONCERNS.md` CRITICAL-SEC-002
**Apply to:** `LoginScreen`, `RegistrationScreen` (audit all `Log.d` / `Log.e` calls)
- ✅ `LoginScreen` line 104: `Log.d("LoginScreen", "Login successful for user: $userId")` — safe
- ❌ `RegistrationScreen` line 295: audit that `password` variable is not interpolated in any log string
- Rule: never interpolate `password`, `dbPassword`, or `confirmPassword` in any `Log.*` call

---

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `model/AllergeneType.kt` | enum | transform | No enum classes exist anywhere in codebase |
| `model/SessionManager.kt` | SharedPreferences utility | request-response | No SharedPreferences usage anywhere in codebase |

Both should use patterns from `RESEARCH.md` / standard Android idioms as described in the Pattern Assignments above.

---

## Metadata

**Analog search scope:** `app/src/main/java/com/example/ingredient/` (flat package, 12 Kotlin files)
**Files scanned:** 12 (`AuthScreens.kt`, `ClienteScreens.kt`, `MainActivity.kt`, `MenuParser.kt`, `MenuEditorScreen.kt`, `IngredientApplication.kt`, `FirebaseMenuUploader.kt`, `LLMApiClient.kt`, `TesseractManager.kt`, `ManualColumnSelector.kt`, `LocalAiParser.kt`, `ImageColumnSplitter.kt`)
**Pattern extraction date:** 2026-04-22

---

*Phase: 01-identity-module | Mapper: gsd-pattern-mapper*
