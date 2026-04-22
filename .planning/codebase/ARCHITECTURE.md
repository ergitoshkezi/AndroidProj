# Architecture

**Analysis Date:** 2025-01-31

## Pattern Overview

**Overall:** Flat Single-Activity Jetpack Compose app with manual `when`-based string routing

**Key Characteristics:**
- One `Activity` (`MainActivity`) owns the entire app lifecycle
- Navigation is manual: a `var currentScreen by remember { mutableStateOf("Login") }` string drives all screen switching inside the `IngredientApp` composable
- No ViewModel layer — all UI state lives as `remember`/`mutableStateOf` inside `@Composable` functions
- No Repository layer — Firebase RTDB queries are written inline inside Composables and top-level suspend functions
- All code lives in a single flat package: `com.example.ingredient`
- Two distinct user roles: **Cliente** (customer) and **Ristoratore** (restaurant owner), both served from the same nav state

## Layers

**Entry / Bootstrap:**
- Purpose: Android lifecycle entry, dependency construction
- Location: `app/src/main/java/com/example/ingredient/MainActivity.kt`
- Contains: `MainActivity` class, `IngredientApp` composable, `RistoratoreScreen` composable, and all global suspend helper functions (`uploadMenuFromFile`, `processImageWithTesseract`, `processImageWithManualColumns`, `saveResponseToFile`, `uriToBitmap`)
- Depends on: All screen composables, `EnhancedTesseractManager`, `LLMApiClient`, `MenuParser`, `FirebaseMenuUploader`
- Used by: Android OS

**Screen Layer (Composables):**
- Purpose: Full-screen UI + embedded business logic + direct Firebase calls
- Location: `app/src/main/java/com/example/ingredient/`
  - `AuthScreens.kt` — `LoginScreen`, `RegistrationScreen`
  - `ClienteScreens.kt` — `ClienteScreen`, `SearchTab`, `OffersTab`, `FilterPanel`, `DishResultItem`
  - `MenuEditorScreen.kt` — `MenuEditorScreen`, `MenuCategoryCard`, `MenuItemCard`, `EditDishDialog`
  - `ManualColumnSelector.kt` — `ManualColumnSelector` (interactive Canvas-based image tool)
- Contains: All UI rendering, all local state, all Firebase listener registrations
- Depends on: Firebase SDK, Google Location Services, data classes in `MenuParser.kt`
- Used by: `IngredientApp` composable in `MainActivity.kt`
- **Note:** No ViewModel separation; business logic and UI rendering are co-located.

**Utility / Service Layer (Plain Classes):**
- Purpose: Encapsulate OCR, LLM inference, and Firebase CRUD
- Location: `app/src/main/java/com/example/ingredient/`
  - `TesseractManager.kt` — `EnhancedTesseractManager` class (OCR engine wrapper)
  - `ImageColumnSplitter.kt` — `ImageColumnSplitter` class (bitmap column detection)
  - `LocalAiParser.kt` — `LocalAiParser` singleton object (on-device MediaPipe LLM)
  - `LLMApiClient.kt` — `LLMApiClient` class (remote Siemens LLM API via HTTP)
  - `FirebaseMenuUploader.kt` — `FirebaseMenuUploader` class (CRUD against Firebase RTDB)
  - `MenuParser.kt` — `MenuParser` class + data classes `MenuItem`, `MenuCategory`, `SearchResult`
- Depends on: Firebase SDK, Tesseract4Android, MediaPipe GenAI, OkHttp (not used — raw `HttpURLConnection` is used instead)
- Used by: Screen composables and `MainActivity.kt`

**Theme Layer:**
- Purpose: Material3 theming
- Location: `app/src/main/java/com/example/ingredient/ui/theme/`
  - `Color.kt`, `Theme.kt`, `Type.kt`
- Used by: `IngredientTheme {}` wrapper in `MainActivity.kt`

## Navigation

**Mechanism:** Manual string-based routing in `IngredientApp` composable — `when (currentScreen)` switch.

**Screen States (currentScreen values):**

| Value | Screen Shown | Condition |
|---|---|---|
| `"Login"` | `LoginScreen` | Default / after logout |
| `"Register"` | `RegistrationScreen` | From Login |
| `"Cliente"` | `ClienteScreen` | After login as Cliente |
| `"Ristoratore"` | `RistoratoreScreen` | After login as Ristoratore |
| `"MenuEditor"` | `MenuEditorScreen` | From Ristoratore → "View & Edit Menu" |

**Session State (also in `IngredientApp`):**
- `currentUserId: String?` — Firebase push key for the logged-in user
- `currentUserEmail: String?` — declared but never populated (unused)
- `userType: String` — `"Cliente"` or `"Ristoratore"`

**Screen Communication:**
- Screens receive navigation callbacks as lambda parameters (`onLogout`, `onNavigateToRegister`, `onLoginSuccess`, `onViewMenu`, `onBack`)
- Screens receive Firebase `DatabaseReference` and `userId` directly as parameters — there is no shared state object or event bus
- `ClienteScreen` has its own internal tab navigation (`selectedTab: Int`) using a `NavigationBar` with two tabs: Search (0) and Offers (1)

**No Navigation Compose library is used.** Back-stack is not managed — pressing Android Back does not pop screens.

## Data Flow

**Login Flow:**
1. User types email/password in `LoginScreen`
2. `LoginScreen` directly queries `databaseReference.child("users")` via `ValueEventListener`
3. All user nodes are loaded and iterated client-side to find a matching email+password (plaintext comparison)
4. On match: `onLoginSuccess(userId, userType)` lambda called → `IngredientApp` sets `currentScreen`, `currentUserId`, `userType`
5. Compose recomposition renders the correct screen

**Menu Upload Flow (Ristoratore):**
1. `RistoratoreScreen` collects menu image via `ActivityResultContracts.GetContent`
2. `EnhancedTesseractManager.processImage()` runs OCR on the bitmap (on IO dispatcher)
3. `LLMApiClient.processMenuText()` sends OCR text to Siemens LLM API over HTTP
4. `MenuParser.parseMenuText()` converts LLM JSON response into `List<MenuCategory>`
5. `FirebaseMenuUploader.uploadMenuWithMetadata()` writes to `users/{userId}/menu/` in Firebase RTDB
6. Status messages update `statusMessage: String` state in `RistoratoreScreen` → recomposed

**Menu Search Flow (Cliente):**
1. User types ingredients in `SearchTab`
2. `performSearch()` top-level function queries all users, filters by `userType == "Ristoratore"`, iterates all dishes
3. Ingredient matching is a client-side `String.contains()` scan over all restaurant menus
4. Results returned via callback, stored in `searchResults` state
5. `LaunchedEffect` applies local filters/sorting → `filteredResults` state → recompose

**Menu Edit Flow (Ristoratore):**
1. `MenuEditorScreen` loads menu from `FirebaseMenuUploader.getMenu(userId)` in `LaunchedEffect`
2. User edits/deletes a dish via dialog
3. Full menu list is re-uploaded via `FirebaseMenuUploader.uploadMenu()` on every single change

## State Management

**No ViewModel, no StateFlow, no LiveData.** All state is Compose local state:

```kotlin
// Pattern used everywhere
var someState by remember { mutableStateOf(initialValue) }
```

State is not shared between screens. When a screen is left (via `currentScreen` change), its entire state is destroyed and rebuilt from scratch on return.

**Global session state** is the exception — it lives in `IngredientApp`:
```kotlin
var currentScreen by remember { mutableStateOf("Login") }
var currentUserId by remember { mutableStateOf<String?>(null) }
var userType by remember { mutableStateOf("") }
```

## Entry Points

**Application class:**
- Location: Referenced as `android:name=".IngredientApplication"` in `AndroidManifest.xml`
- File: Not found in source — likely initializes Firebase or MediaPipe (`LocalAiParser`) at app start

**MainActivity:**
- Location: `app/src/main/java/com/example/ingredient/MainActivity.kt`
- Triggers: Android LAUNCHER intent
- Responsibilities: Initializes `EnhancedTesseractManager`, gets `FirebaseDatabase.getInstance().getReference()`, calls `setContent { IngredientTheme { IngredientApp(...) } }`

## Error Handling

**Strategy:** Inline, per-screen `errorMessage: String` state variables

**Patterns:**
- Firebase callbacks update `errorMessage` state string, shown in-UI as `Text(color = error)`
- Coroutine exceptions caught with `try/catch`, dispatched back to Main with `withContext(Dispatchers.Main)` for `Toast.makeText()`
- `Result<T>` wrapper used in `FirebaseMenuUploader` methods — callers check `result.isSuccess`
- No centralized error handling or crash reporting

## Authentication

**No Firebase Auth SDK is used.** Authentication is fully manual:
- Passwords stored as plaintext strings in Firebase RTDB under `users/{userId}/password`
- Login performs a full scan of all users to find matching credentials
- Session state is in-memory only (`currentUserId` in `IngredientApp`) — cleared on process kill

## Missing Architecture Patterns

The following standard Android architecture patterns are **absent**:

| Pattern | Status | Impact |
|---|---|---|
| ViewModel | ❌ Not used | State lost on screen rotation; business logic untestable |
| Repository | ❌ Not used | Firebase queries scattered across Composables and files |
| Navigation Compose | ❌ Not used | No back-stack, deep links impossible |
| StateFlow / LiveData | ❌ Not used | No reactive data layer |
| Dependency Injection (Hilt/Koin) | ❌ Not used | Dependencies constructed inline; no testability |
| Use Cases / Interactors | ❌ Not used | Business logic not isolated |
| Clean Architecture layers | ❌ Not used | Presentation and data are mixed |

---

*Architecture analysis: 2025-01-31*
