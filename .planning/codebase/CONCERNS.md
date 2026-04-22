# Codebase Concerns

**Analysis Date:** 2025-01-31

---

## CRITICAL — Security

### 1. Plaintext Password Storage in Firebase RTDB

- **Severity:** CRITICAL
- **Files:** `app/src/main/java/com/example/ingredient/AuthScreens.kt`
- **Location:** `RegistrationScreen` composable, lines 285–291

Passwords are stored as raw plaintext strings directly in Firebase Realtime Database:

```kotlin
val userData = mapOf(
    "email" to email,
    "password" to password,          // ← plaintext password written to DB
    "userType" to selectedUserType,
    "createdAt" to System.currentTimeMillis()
)
databaseReference.child("users").child(userId).setValue(userData)
```

Login then performs a plaintext string comparison (line 102):
```kotlin
if (dbEmail == email && dbPassword == password) { ... }
```

**Impact:** Any Firebase database breach, misconfigured security rules, or insider access exposes all user passwords immediately. Passwords are also transmitted over the network in responses when the full `users` node is fetched.

**Fix:** Replace with Firebase Authentication (`FirebaseAuth.getInstance().createUserWithEmailAndPassword()`). Remove the `password` field from RTDB entirely. Firebase Auth handles hashing, salting, and secure credential storage automatically.

---

### 2. Full User Table Scan on Every Login, Search, and Offers Load

- **Severity:** CRITICAL
- **Files:**
  - `app/src/main/java/com/example/ingredient/AuthScreens.kt` — `LoginScreen` (line 91), `RegistrationScreen` (line 263)
  - `app/src/main/java/com/example/ingredient/ClienteScreens.kt` — `performSearch()` (line 449), `fetchOffers()` (line 520)

All four operations call:
```kotlin
databaseReference.child("users").addListenerForSingleValueEvent(...)
```

This downloads the **entire `users` node** — including every user's email, plaintext password, userType, and full menu data — on every single invocation.

**AuthScreens.kt login scan (line 91):**
```kotlin
databaseReference.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
    override fun onDataChange(snapshot: DataSnapshot) {
        for (userSnapshot in snapshot.children) {
            val dbPassword = userSnapshot.child("password").getValue(String::class.java)
            if (dbEmail == email && dbPassword == password) { ... }
```

**ClienteScreens.kt performSearch (line 449):**
```kotlin
databaseReference.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
    override fun onDataChange(snapshot: DataSnapshot) {
        for (userSnapshot in snapshot.children) {
            // reads userType, restaurantName, lat, lon, and all menu data
```

**ClienteScreens.kt fetchOffers (line 520):**
```kotlin
databaseReference.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
    override fun onDataChange(snapshot: DataSnapshot) {
        for (userSnapshot in snapshot.children) {
            // identical scan pattern — reads all user data including passwords
```

**Impact (Security):** Every search or offers load causes every registered user's credentials (email + plaintext password) to be transmitted to the client device. A malicious client or a network intercept during any search exposes all user data.

**Impact (Performance):** With N users, every search downloads O(N × menu_size) bytes. This will become unusably slow and expensive at scale.

**Fix:**
- Migrate auth to Firebase Authentication (eliminates credential exposure entirely).
- For search: create a separate top-level `/restaurants` node containing only public menu/location data. Query only that node.
- For offers: similarly query `/restaurants/{id}/menu` filtered by `isOffer == true` using Firebase queries.
- Firebase RTDB security rules must be tightened so clients can never read `users/{id}/password`.

---

### 3. LLM API Key Hardcoded in Source Code

- **Severity:** CRITICAL
- **Files:** `app/src/main/java/com/example/ingredient/MainActivity.kt`
- **Location:** Lines 397 and 420 (two occurrences inside `RistoratoreScreen` composable)

```kotlin
val apiKey = "SIEMENS_API_KEY_REMOVED"
val llmClient = LLMApiClient(apiKey)
```

The key is duplicated at both call sites (lines 397 and 420), making it harder to rotate.

**Impact:** The key is committed to version control. Anyone with repository access or who reverse-engineers the APK can extract and abuse this key, incurring costs or hitting rate limits.

**Fix:** Move the key to `local.properties` (excluded from git via `.gitignore`), read it at build time via `BuildConfig` field in `build.gradle.kts`, and inject it as a single constant. For production, proxy LLM calls through a backend instead of calling from the device directly.

---

## HIGH — Architecture

### 4. No ViewModel — All Business Logic in Composables

- **Severity:** HIGH
- **Files:**
  - `app/src/main/java/com/example/ingredient/ClienteScreens.kt` — `SearchTab`, `OffersTab` (entire file)
  - `app/src/main/java/com/example/ingredient/AuthScreens.kt` — `LoginScreen`, `RegistrationScreen` (entire file)
  - `app/src/main/java/com/example/ingredient/MainActivity.kt` — `RistoratoreScreen`, `IngredientApp`

Firebase listeners, coroutine launches, state management, and business logic are all embedded directly inside `@Composable` functions. No ViewModels, no repositories, no separation of concerns.

**Impact:**
- Business logic is completely untestable (no unit tests possible without an Android device).
- State is lost on any configuration change (screen rotation resets all search results, re-triggers Firebase calls).
- `IngredientApp` in `MainActivity.kt` (lines 68–138) holds navigation state as raw `remember` variables — this will not survive process death.

**Fix:** Introduce ViewModels per screen (`LoginViewModel`, `SearchViewModel`, `OffersViewModel`, `RistoratoreViewModel`). Move all Firebase calls and coroutine logic into ViewModels. Use `viewModel()` in Composables for state. Use `SavedStateHandle` or persistent storage for session state.

---

### 5. No Session Persistence — Session Lost on App Kill

- **Severity:** HIGH
- **Files:** `app/src/main/java/com/example/ingredient/MainActivity.kt`
- **Location:** `IngredientApp` composable, lines 68–73

Session state is held only in `remember` variables:
```kotlin
var currentScreen by remember { mutableStateOf("Login") }
var currentUserId by remember { mutableStateOf<String?>(null) }
var currentUserEmail by remember { mutableStateOf<String?>(null) }
var userType by remember { mutableStateOf("") }
```

**Impact:** Killing the app or any process death sends the user back to the login screen unconditionally and requires a full `users` table scan to re-authenticate. The `currentUserEmail` variable is set to `null` on login (line 71 stores nothing; the `onLoginSuccess` callback does not return email).

**Fix:** After migrating to Firebase Authentication, use `FirebaseAuth.getInstance().currentUser` to check for an existing session on app start. For `userType`, persist to `SharedPreferences` or `DataStore` and restore on launch.

---

### 6. DRY Violation — `performSearch()` and `fetchOffers()` Are Near-Identical

- **Severity:** HIGH
- **Files:** `app/src/main/java/com/example/ingredient/ClienteScreens.kt`
- **Location:** `performSearch()` lines 439–513, `fetchOffers()` lines 515–574

Both functions:
1. Call `databaseReference.child("users").addListenerForSingleValueEvent(...)`
2. Iterate all users, filter by `userType == "Ristoratore"`
3. Compute restaurant name, lat/lon, and distance with identical code
4. Iterate menu categories → dishes → call `parseDish(dishSnapshot)`
5. Differ only in the filtering predicate (ingredient match vs. `isOffer == true`) and result collection

Approximately 70% of the code is duplicated line-for-line.

**Impact:** Any bug fix or schema change (e.g., moving to a `/restaurants` root node) must be applied in two places. The existing security/performance issues affect both functions identically and are independently maintained.

**Fix:** Extract a shared `scanRestaurants(databaseReference, userLocation, predicate, onResult)` function. `performSearch` and `fetchOffers` become thin wrappers that supply different filter predicates. Once ViewModels are introduced, this belongs in a `RestaurantRepository`.

---

## HIGH — Schema

### 7. Schema Conflict: Menu Stored Under `users/{id}/menu/` vs. Approved Root `/dishes/{dishId}`

- **Severity:** HIGH
- **Files:** `app/src/main/java/com/example/ingredient/FirebaseMenuUploader.kt`
- **Location:** `uploadMenu()` function, lines 14–67

`FirebaseMenuUploader` writes to:
```
users/{userId}/menu/{categoryId}/dishes/{dishId}
```

The approved schema uses a root-level flat structure:
```
/dishes/{dishId}
```

The current nested structure causes the full-user-table scan problem: to read any dish, the entire `users` node must be fetched. A root `/dishes` node would allow targeted Firebase queries.

Additionally, `uploadMenuWithMetadata()` (lines 72–106) hard-codes the restaurant name as `"My Restaurant"` in both call sites in `MainActivity.kt` (lines 433 and 531) instead of reading a user-provided name.

**Impact:** Search and offers cannot use Firebase query features (`.orderByChild()`, `.equalTo()`). All filtering is done client-side after downloading all data. Schema migration will require a data migration script.

**Fix:** Restructure writes to `/dishes/{dishId}` with `restaurantId` as a field. Move restaurant metadata to `/restaurants/{restaurantId}`. Update `FirebaseMenuUploader`, `performSearch`, and `fetchOffers` to use these root nodes. Add a restaurant name input field in `RistoratoreScreen`.

---

## MEDIUM — Data Model

### 8. `MenuItem.price` Typed as `String` Instead of `Double`

- **Severity:** MEDIUM
- **Files:** `app/src/main/java/com/example/ingredient/MenuParser.kt`
- **Location:** `MenuItem` data class, line 11

```kotlin
data class MenuItem(
    val name: String = "",
    val description: String = "",
    val allergens: String = "",
    val price: String = "",         // ← should be Double
    val originalPrice: String = "", // ← should be Double
    ...
)
```

Price is parsed to `Double` for arithmetic in `cleanPrice()` (line 142) and then formatted back to `String`. It is parsed back to `Double` again at sort time in `ClienteScreens.kt` (line 130):
```kotlin
filtered.sortedBy { it.dish.price.toDoubleOrNull() ?: 0.0 }
```

And again in `FirebaseMenuUploader.getMenu()` (line 155):
```kotlin
val price = priceString.toDoubleOrNull()?.let { String.format("%.2f", it) } ?: "0.00"
```

**Impact:** Price is converted between `String` and `Double` in at least 4 places, creating multiple opportunities for `NumberFormatException` or silent `null` fallback to `0.00`. Arithmetic comparisons are unreliable on `String` types.

**Fix:** Change `price: String` and `originalPrice: String` to `price: Double` and `originalPrice: Double` in `MenuItem`. Store `Double` in Firebase. Update all serialization/display sites to format on the way out (`"%.2f".format(price)`).

---

### 9. `MenuItem.allergens` Typed as `String` Instead of `List<String>`

- **Severity:** MEDIUM
- **Files:** `app/src/main/java/com/example/ingredient/MenuParser.kt`
- **Location:** `MenuItem` data class, line 10

```kotlin
val allergens: String = "",   // ← should be List<String>
```

Allergens are currently stored and displayed as a single unstructured string (e.g., `"1, 2, 7"`). This means allergen filtering, individual-allergen display, and per-allergen search are impossible without string parsing.

**Impact:** No allergen-based filtering can be implemented. Display UI must parse the string at render time. Firebase stores an opaque blob with no queryability.

**Fix:** Change to `val allergens: List<String> = emptyList()`. Update `MenuParser.parseJsonArray()` to split the allergen string by comma. Update Firebase serialization in `FirebaseMenuUploader` to write as a JSON array.

---

### 10. `MenuItem` Missing Fields Required by Approved Schema

- **Severity:** MEDIUM
- **Files:** `app/src/main/java/com/example/ingredient/MenuParser.kt`
- **Location:** `MenuItem` data class, lines 7–16

The current `MenuItem` data class:
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

The approved schema requires `cucina`, `regione`, and `paese` fields. The existing `country` and `region` fields partially map to `paese` and `regione`, but `cucina` (cuisine type) is entirely absent. Field naming is inconsistent (`country` vs. `paese`, `region` vs. `regione`).

**Impact:** Dishes parsed from the LLM response cannot carry cuisine type. Filtering by cuisine in `SearchTab` is not possible. Field name mismatch means any schema-compliant reader will not find the expected keys.

**Fix:** Rename `country → paese`, `region → regione`, add `cucina: String = ""`. Update `MenuParser.parseJsonArray()` to extract `cucina` from the LLM JSON. Update all Firebase read/write sites and `parseDish()` in `ClienteScreens.kt` (line 576).

---

### 11. `parseDish()` Reads `isOffer` and `originalPrice` from Firebase but `uploadMenu()` Never Writes Them

- **Severity:** MEDIUM
- **Files:**
  - `app/src/main/java/com/example/ingredient/FirebaseMenuUploader.kt` — `uploadMenu()`, lines 45–49
  - `app/src/main/java/com/example/ingredient/ClienteScreens.kt` — `parseDish()`, lines 583–584

`uploadMenu()` only writes `name`, `description`, `allergens`, `price`:
```kotlin
val dishData = mapOf(
    "name" to dish.name,
    "description" to dish.description,
    "allergens" to dish.allergens,
    "price" to dish.price
    // isOffer and originalPrice are NOT written
)
```

But `parseDish()` reads `isOffer` and `originalPrice`:
```kotlin
isOffer = snapshot.child("isOffer").getValue(Boolean::class.java) ?: false,
originalPrice = snapshot.child("originalPrice").getValue(String::class.java) ?: "",
```

**Impact:** The entire Offers feature (`fetchOffers()` in `ClienteScreens.kt`) is non-functional. `dish.isOffer` will always be `false` for any dish uploaded via the app, so `fetchOffers()` will always return an empty list. The "Offers" tab is permanently empty.

**Fix:** Add `"isOffer" to dish.isOffer` and `"originalPrice" to dish.originalPrice` to the `dishData` map in `FirebaseMenuUploader.uploadMenu()`. Add UI in `MenuEditorScreen` (or `RistoratoreScreen`) to allow the restaurateur to mark dishes as offers and set an original price.

---

## MEDIUM — Code Quality

### 12. `@SuppressLint("MissingPermission")` on `ClienteScreen`

- **Severity:** MEDIUM
- **Files:** `app/src/main/java/com/example/ingredient/ClienteScreens.kt`
- **Location:** Line 3 (`import android.annotation.SuppressLint`) — annotation applied to `ClienteScreen`

Location access is attempted via `fusedLocationClient.lastLocation` (line 57) inside a `try/catch(SecurityException)`. The `@SuppressLint("MissingPermission")` annotation silences the compiler warning rather than implementing a proper runtime permission check before accessing location.

**Impact:** If the user has not granted `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`, the `SecurityException` is silently swallowed and `userLocation` stays `null`. The distance feature silently fails with no user feedback.

**Fix:** Use `ActivityResultContracts.RequestPermission` to request location permission explicitly. Show a rationale dialog if needed. Only call `fusedLocationClient.lastLocation` after permission is confirmed. Remove `@SuppressLint`.

---

## LOW — Missing Tests

### 13. Zero Application Test Coverage

- **Severity:** LOW (escalates to HIGH in production)
- **Files:**
  - `app/src/test/java/com/example/ingredient/ExampleUnitTest.kt` — contains only `assertEquals(4, 2 + 2)`
  - `app/src/androidTest/java/com/example/ingredient/ExampleInstrumentedTest.kt` — contains only package name assertion

No tests exist for:
- `MenuParser.parseMenuText()` — complex JSON parsing logic with multiple edge cases
- `MenuParser.cleanPrice()` — currency string normalization
- `FirebaseMenuUploader` — any upload/read logic
- Auth flows — login, registration, duplicate email detection
- Search matching logic — ingredient score calculation in `performSearch()`
- `sanitizeKey()` in `FirebaseMenuUploader` — Firebase key character replacement

**Impact:** `MenuParser` contains non-trivial parsing logic that is completely unverified. Regressions from any refactor (e.g., fixing the `MenuItem` type issues above) will go undetected.

**Fix:** Add JUnit tests for `MenuParser` and `sanitizeKey()` immediately — these have no Android dependencies and can run on JVM. Add Robolectric or MockK-based tests for Firebase uploader logic. Once ViewModels are introduced, add ViewModel unit tests.

---

## LOW — Minor Issues

### 14. `processImageWithManualColumns` Hardcodes Language as `"spa"` (Spanish)

- **Severity:** LOW
- **Files:** `app/src/main/java/com/example/ingredient/MainActivity.kt`
- **Location:** Line 578

```kotlin
val text = processImageWithManualColumns(
    tesseract = tesseractManager,
    bitmap = bitmap,
    columnRegions = columnRegions,
    lang = "spa"    // ← hardcoded Spanish; app targets Italian menus
)
```

The app is designed for Italian restaurant menus but OCR is configured for Spanish (`spa`). The non-column path likely uses a different language setting. This will reduce OCR accuracy for Italian text.

**Fix:** Set language to `"ita"` (Italian). Consider making it a user-configurable setting.

---

### 15. Restaurant Name Always Hardcoded as `"My Restaurant"`

- **Severity:** LOW
- **Files:** `app/src/main/java/com/example/ingredient/MainActivity.kt`
- **Location:** Lines 433 and 531

```kotlin
val result = uploader.uploadMenuWithMetadata(
    userId = userId,
    restaurantName = "My Restaurant",   // ← hardcoded at both call sites
    menuCategories = menuCategories
)
```

**Impact:** All restaurants uploaded via the app are stored with `restaurantName = "My Restaurant"` in Firebase, making `performSearch()` return `"My Restaurant"` for every result in `ClienteScreens.kt` (line 457).

**Fix:** Add a restaurant name input field to `RistoratoreScreen`. Store and retrieve it from Firebase under `users/{userId}/restaurantName`. Pass the stored value to `uploadMenuWithMetadata`.

---

*Concerns audit: 2025-01-31*
