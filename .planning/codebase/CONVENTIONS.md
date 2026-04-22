# Coding Conventions

**Analysis Date:** 2026-04-22

## Naming Patterns

**Files:**
- PascalCase screen files: `AuthScreens.kt`, `ClienteScreens.kt`, `MenuEditorScreen.kt`
- PascalCase class files: `FirebaseMenuUploader.kt`, `LLMApiClient.kt`, `TesseractManager.kt`
- One file can contain multiple related composables (e.g., `AuthScreens.kt` holds both `LoginScreen` and `RegistrationScreen`)
- UI theme files use PascalCase: `Color.kt`, `Theme.kt`, `Type.kt` under `ui/theme/`

**Functions:**
- Composable functions: PascalCase — `LoginScreen`, `RegistrationScreen`, `SearchTab`, `FilterPanel`, `DishResultItem`
- Regular/private functions: camelCase — `performSearch`, `fetchOffers`, `parseDish`, `sanitizeKey`, `cleanPrice`
- Suspend functions in classes: camelCase — `uploadMenu`, `uploadMenuWithMetadata`, `getMenu`, `processMenuText`

**Variables:**
- camelCase throughout: `searchQuery`, `isLoading`, `errorMessage`, `menuCategories`
- State variables use descriptive names indicating their content: `currentScreen`, `currentUserId`, `userType`
- Boolean state prefixed with `is` or `show`: `isLoading`, `isProcessing`, `isSearching`, `showFilters`, `showManualSelector`

**Types / Classes:**
- PascalCase data classes: `MenuItem`, `MenuCategory`, `SearchResult`, `EditingMenuItemState`
- PascalCase classes: `MenuParser`, `FirebaseMenuUploader`, `LLMApiClient`, `EnhancedTesseractManager`
- Singleton objects: PascalCase — `LocalAiParser`
- TAG constants: `private val TAG = "ClassName"` — defined as instance-level val (not companion object)

**Constants:**
- Not extracted to companion objects; declared inline as private vals:
  ```kotlin
  private val TAG = "FirebaseMenuUploader"
  private val apiUrl = "https://api.siemens.com/llm/v1/chat/completions"
  ```
- Hardcoded color values inline: `Color(0xFFD32F2F)`

## Code Style

**Formatting:**
- No explicit formatter config detected (no `.editorconfig`, no `detekt.yml`, no `ktlint` config)
- Indentation: 4 spaces (standard Android Studio default)
- Trailing lambdas used consistently: `Button(onClick = { ... })`

**Linting:**
- No linting configuration detected — relies on IDE defaults
- `@SuppressLint("MissingPermission")` used where needed in `ClienteScreens.kt`

## Import Organization

**Order (observed pattern):**
1. `android.*` and `androidx.*` platform imports
2. `androidx.compose.*` imports (often wildcarded: `import androidx.compose.foundation.layout.*`)
3. `com.example.ingredient.*` local imports
4. `com.google.firebase.*` Firebase imports
5. `kotlinx.coroutines.*` coroutine imports
6. `java.*` / `org.*` standard library

**Wildcard imports:** Used heavily for Compose — `import androidx.compose.material3.*`, `import androidx.compose.foundation.layout.*`, `import androidx.compose.runtime.*`

**Path Aliases:** None — standard package imports only

## Composable Conventions

**Screen-level composables:**
- Always accept `modifier: Modifier = Modifier` as last parameter
- Receive `databaseReference: DatabaseReference` directly (no ViewModel layer)
- Navigation handled via callback lambdas: `onNavigateToRegister: () -> Unit`, `onLoginSuccess: (String, String) -> Unit`
- All local state declared at top of composable body before UI layout:
  ```kotlin
  var email by remember { mutableStateOf("") }
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }
  val context = LocalContext.current
  ```

**State management:**
- All state is `mutableStateOf` wrapped in `remember` at the composable level — no ViewModel, no StateFlow
- Delegation syntax used: `var x by remember { mutableStateOf(...) }` (not `.value`)
- Complex state uses data classes: `remember { mutableStateOf<EditingMenuItemState?>(null) }`
- Nullable state for optional objects: `mutableStateOf<Bitmap?>(null)`, `mutableStateOf<Uri?>(null)`

**Navigation:**
- Screen routing via a `currentScreen: String` state variable in `IngredientApp` composable
- `when(currentScreen)` block dispatches to screens — no Jetpack Navigation component
- Screen identifiers are plain strings: `"Login"`, `"Register"`, `"Cliente"`, `"Ristoratore"`, `"MenuEditor"`

**Side effects:**
- `LaunchedEffect(Unit)` for one-time initialization (polling for model readiness)
- `LaunchedEffect(userId)` to trigger data load when key changes
- `LaunchedEffect(searchResults, ...)` for reactive filter/sort logic
- `rememberCoroutineScope()` for coroutines triggered by user actions (button clicks)

## Firebase Callback Pattern

**Read pattern — `addListenerForSingleValueEvent`:**
Used in composables directly for one-shot reads (login, registration, search):
```kotlin
databaseReference.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
    override fun onDataChange(snapshot: DataSnapshot) {
        isLoading = false
        // process snapshot.children
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
Location: `AuthScreens.kt` (lines 91–122, 263–315), `ClienteScreens.kt` (lines 449–512, 520+)

**Suspend pattern — `.await()` in `FirebaseMenuUploader`:**
The one class that uses coroutines properly wraps Firebase tasks with `kotlinx.coroutines.tasks.await`:
```kotlin
suspend fun uploadMenu(...): Result<String> {
    return try {
        menuRef.removeValue().await()
        categoryRef.child("categoryName").setValue(value).await()
        Result.success(message)
    } catch (e: Exception) {
        Log.e(TAG, "Error uploading menu to Firebase", e)
        Result.failure(e)
    }
}
```
Location: `FirebaseMenuUploader.kt`

**Write pattern — `setValue` with success/failure listeners:**
```kotlin
databaseReference.child("users").child(userId).setValue(userData)
    .addOnSuccessListener {
        isLoading = false
        Toast.makeText(context, "Registration successful!", Toast.LENGTH_SHORT).show()
        onRegisterSuccess(userId, selectedUserType)
    }
    .addOnFailureListener { e ->
        isLoading = false
        errorMessage = "Registration failed: ${e.message}"
        Log.e("RegistrationScreen", "Registration error", e)
    }
```
Location: `AuthScreens.kt` (lines 292–306)

## Error Handling

**Patterns:**
- UI errors: stored in `var errorMessage by remember { mutableStateOf("") }` and displayed inline
- Error display: `if (errorMessage.isNotEmpty()) { Text(text = errorMessage, color = MaterialTheme.colorScheme.error) }`
- Service-layer errors: `Result<T>` return type — `Result.success(value)` / `Result.failure(e)`
- Result checked at call site: `if (result.isSuccess) { ... } else { errorMessage = "..." }`
- All Firebase `onCancelled` callbacks set `errorMessage` and log via `Log.e`
- Exceptions in non-UI code: caught with `try/catch`, logged, and either `Result.failure(e)` returned or fallback value provided
- Initializer failures (Tesseract, LocalAiParser): logged and `isInitialized = false` set — no crash

**Logging:**
- `Log.d(TAG, "...")` for debug/flow messages
- `Log.e(TAG, "...")` for errors — often with exception: `Log.e(TAG, "message", e)`
- `Log.w(TAG, "...")` for warnings (used in `LLMApiClient`)
- Toast used for user-visible success/failure events at auth level

## Coroutine Usage

**Scope management:**
- `rememberCoroutineScope()` in composables for user-action-triggered coroutines
- `CoroutineScope(SupervisorJob() + Dispatchers.IO)` in `IngredientApplication` for app-level background work
- `withContext(Dispatchers.IO)` in `LLMApiClient.processMenuText` for network calls

**Pattern:**
```kotlin
val coroutineScope = rememberCoroutineScope()
// ...triggered by click:
coroutineScope.launch {
    val result = uploader.uploadMenu(userId, updatedCategories)
    if (result.isSuccess) {
        menuCategories = updatedCategories
    }
}
```
Location: `MenuEditorScreen.kt` (lines 130–145, 162–178)

**Async vs Firebase callbacks:**
- `FirebaseMenuUploader` uses coroutines with `.await()` — clean suspend functions
- All other Firebase reads use callback-style `addListenerForSingleValueEvent` — NOT wrapped in coroutines
- This creates an inconsistency: Firebase writes go through suspend functions, reads go through callbacks

## Comments

**When used:**
- KDoc-style `/** ... */` blocks on public/important methods — used in `FirebaseMenuUploader.kt`, `LLMApiClient.kt`, `MenuParser.kt`
- Inline `//` comments for non-obvious logic (e.g., `// Search through all users to find matching email`)
- Section headers as comments in composables (e.g., `// User Type Selection`, `// Load menu on first composition`, `// Edit dialog`)

**KDoc pattern:**
```kotlin
/**
 * Upload menu to Firebase under specific restaurant user
 * Structure: users/{userId}/menu/{categoryId}/{dishId}
 */
suspend fun uploadMenu(...): Result<String>
```

## Function Design

**Size:** Screen composables are large (50–200+ lines) — no sub-composable extraction enforced
**Parameters:** Screen composables typically: `(data, callbacks..., modifier: Modifier = Modifier)`
**Return Values:** Service functions return `Result<T>`; composables return `Unit` (implicit)
**Data Classes:** Used for structured state: `MenuItem`, `MenuCategory`, `SearchResult`, `EditingMenuItemState`

## Module Design

**Package:** Single flat package `com.example.ingredient` for all app code
**Exports:** No explicit visibility modifiers — all classes/functions default to `public`
**No barrel files / no module separation** — everything in one package

---

*Convention analysis: 2026-04-22*
