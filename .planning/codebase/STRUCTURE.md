# Codebase Structure

**Analysis Date:** 2025-01-31

## Directory Layout

```
Ingredient/                              # Repo root
├── app/
│   ├── build.gradle.kts                 # App-level Gradle (deps, SDK versions)
│   ├── google-services.json             # Firebase project config
│   ├── proguard-rules.pro               # ProGuard rules (minify disabled)
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml      # Single Activity declaration, permissions
│           ├── assets/
│           │   └── models/              # Bundled AI model files
│           │       ├── menu_parser.gguf # On-device LLM model (~hundreds of MB)
│           │       ├── eng.traineddata  # Tesseract English language data
│           │       └── spa.traineddata  # Tesseract Spanish language data
│           ├── java/com/example/ingredient/
│           │   ├── MainActivity.kt      # Activity + IngredientApp nav + RistoratoreScreen + helpers
│           │   ├── AuthScreens.kt       # LoginScreen + RegistrationScreen
│           │   ├── ClienteScreens.kt    # ClienteScreen + SearchTab + OffersTab + data helpers
│           │   ├── MenuEditorScreen.kt  # MenuEditorScreen + category/item cards + edit dialog
│           │   ├── ManualColumnSelector.kt  # Canvas-based column divider UI
│           │   ├── MenuParser.kt        # MenuParser class + MenuItem/MenuCategory data classes
│           │   ├── FirebaseMenuUploader.kt  # Firebase RTDB CRUD for menu data
│           │   ├── LLMApiClient.kt      # HTTP client for remote Siemens LLM API
│           │   ├── LocalAiParser.kt     # On-device MediaPipe LLM singleton
│           │   ├── TesseractManager.kt  # EnhancedTesseractManager (OCR engine wrapper)
│           │   ├── ImageColumnSplitter.kt   # Bitmap column detection algorithm
│           │   └── ui/theme/
│           │       ├── Color.kt         # Material3 color tokens
│           │       ├── Theme.kt         # IngredientTheme composable
│           │       └── Type.kt          # Typography definitions
│           └── res/
│               ├── drawable/            # App icons, graphics
│               ├── mipmap-*/            # Launcher icons at various densities
│               ├── values/              # strings.xml, themes.xml, colors.xml
│               └── xml/                 # backup_rules.xml, data_extraction_rules.xml
├── build.gradle.kts                     # Root-level Gradle (plugin versions)
├── settings.gradle.kts                  # Module inclusion
├── gradle.properties                    # Gradle JVM/AndroidX flags
├── gradle/
│   └── libs.versions.toml               # Version catalog (all dep versions centralized)
├── gradlew / gradlew.bat                # Gradle wrapper scripts
└── .planning/
    └── codebase/                        # GSD architecture docs (this directory)
```

## Directory Purposes

**`app/src/main/java/com/example/ingredient/` (root package):**
- Purpose: All Kotlin source — one flat package, no subdirectories except `ui/theme/`
- Contains: Activities, Composable screens, utility classes, data classes
- Key files: Every `.kt` file listed above lives here

**`app/src/main/java/com/example/ingredient/ui/theme/`:**
- Purpose: Material3 theme definitions only
- Contains: `Color.kt`, `Theme.kt`, `Type.kt`
- Key file: `Theme.kt` exports `IngredientTheme {}` used in `MainActivity.kt`

**`app/src/main/assets/models/`:**
- Purpose: Bundled binary model files copied to device storage at first run
- Contains: `menu_parser.gguf` (on-device LLM), `eng.traineddata`, `spa.traineddata` (Tesseract)
- Note: These are large binary files; model is copied to `context.cacheDir` by `LocalAiParser`; tessdata is copied to `context.getExternalFilesDir(null)` by `EnhancedTesseractManager`

**`gradle/`:**
- Purpose: Centralized dependency version catalog
- Key file: `libs.versions.toml` — all library versions declared here, referenced via `libs.*` in `build.gradle.kts`

## Key File Locations

**Entry Points:**
- `app/src/main/java/com/example/ingredient/MainActivity.kt`: Android entry, `IngredientApp` composable, nav routing
- `app/src/main/AndroidManifest.xml`: App + activity registration, permissions

**Screen Composables:**
- `app/src/main/java/com/example/ingredient/AuthScreens.kt`: `LoginScreen`, `RegistrationScreen`
- `app/src/main/java/com/example/ingredient/ClienteScreens.kt`: `ClienteScreen`, `SearchTab`, `OffersTab`, `DishResultItem`, `FilterPanel`
- `app/src/main/java/com/example/ingredient/MenuEditorScreen.kt`: `MenuEditorScreen`, `MenuCategoryCard`, `MenuItemCard`, `EditDishDialog`
- `app/src/main/java/com/example/ingredient/ManualColumnSelector.kt`: `ManualColumnSelector`
- `app/src/main/java/com/example/ingredient/MainActivity.kt` also contains `RistoratoreScreen`

**Data Models:**
- `app/src/main/java/com/example/ingredient/MenuParser.kt`: `MenuItem`, `MenuCategory` data classes
- `app/src/main/java/com/example/ingredient/ClienteScreens.kt`: `SearchResult` data class

**Service / Utility Classes:**
- `app/src/main/java/com/example/ingredient/FirebaseMenuUploader.kt`: `FirebaseMenuUploader` — Firebase CRUD
- `app/src/main/java/com/example/ingredient/LLMApiClient.kt`: `LLMApiClient` — remote LLM HTTP API
- `app/src/main/java/com/example/ingredient/LocalAiParser.kt`: `LocalAiParser` — on-device LLM (singleton object)
- `app/src/main/java/com/example/ingredient/TesseractManager.kt`: `EnhancedTesseractManager` — OCR engine
- `app/src/main/java/com/example/ingredient/ImageColumnSplitter.kt`: `ImageColumnSplitter` — bitmap analysis

**Theme:**
- `app/src/main/java/com/example/ingredient/ui/theme/Theme.kt`: `IngredientTheme` composable
- `app/src/main/java/com/example/ingredient/ui/theme/Color.kt`: color palette
- `app/src/main/java/com/example/ingredient/ui/theme/Type.kt`: typography scale

**Build Configuration:**
- `app/build.gradle.kts`: All dependencies, SDK versions, compile options
- `gradle/libs.versions.toml`: Version catalog — edit here to update library versions

## Naming Conventions

**Files:**
- Screen files group by user role or feature: `AuthScreens.kt`, `ClienteScreens.kt`, `MenuEditorScreen.kt`
- Utility classes use descriptive names: `FirebaseMenuUploader.kt`, `TesseractManager.kt`, `ImageColumnSplitter.kt`
- No `ViewModel`, `Repository`, or `UseCase` suffixes exist (those patterns are absent)

**Classes and Objects:**
- `PascalCase` for all classes: `EnhancedTesseractManager`, `FirebaseMenuUploader`, `MenuParser`
- `object` singletons: `LocalAiParser` (Kotlin object)
- `data class` for models: `MenuItem`, `MenuCategory`, `SearchResult`, `ColumnRegion`

**Composable Functions:**
- `PascalCase` matching what they render: `LoginScreen`, `ClienteScreen`, `SearchTab`, `DishResultItem`
- Sub-composables (cards, dialogs) also `PascalCase`: `MenuCategoryCard`, `MenuItemCard`, `FilterPanel`

**Variables and State:**
- `camelCase` for local state: `var currentScreen`, `var isLoading`, `var searchResults`
- State declared with `by remember { mutableStateOf(...) }` pattern throughout

**Functions:**
- `camelCase` for top-level helpers: `performSearch`, `fetchOffers`, `parseDish`, `uploadMenuFromFile`
- Firebase callbacks always use anonymous `object : ValueEventListener` pattern

**Strings as Screen Keys:**
- Navigation destinations are plain strings: `"Login"`, `"Register"`, `"Cliente"`, `"Ristoratore"`, `"MenuEditor"`
- No sealed class, enum, or type-safe route object exists

## Module Structure

The app is a **single Gradle module** — no multi-module setup:

```
:app   (only module)
```

All code compiles as one Android application module. There is no separate `:core`, `:feature`, or `:data` module.

## Where to Add New Code

**New screen for an existing user role:**
- Add `@Composable fun MyNewScreen(...)` to the relevant file:
  - Cliente screens → `app/src/main/java/com/example/ingredient/ClienteScreens.kt`
  - Ristoratore screens → `app/src/main/java/com/example/ingredient/MainActivity.kt` (where `RistoratoreScreen` is) or a new `RistoratoreScreens.kt`
  - Auth screens → `app/src/main/java/com/example/ingredient/AuthScreens.kt`
- Add a new `when` branch in `IngredientApp` in `MainActivity.kt`
- Add a new string key (e.g., `"NewScreen"`) to the `currentScreen` routing

**New data model:**
- Add `data class` to `app/src/main/java/com/example/ingredient/MenuParser.kt` if menu-related
- Or add to the relevant screen file if specific to one screen

**New Firebase operation:**
- Add a `suspend fun` method to `app/src/main/java/com/example/ingredient/FirebaseMenuUploader.kt`
- Or add an inline `ValueEventListener` callback directly in the Composable (existing pattern)

**New utility/service class:**
- Create a new `.kt` file directly in `app/src/main/java/com/example/ingredient/`
- No subdirectory needed (project uses flat package structure)

**New theme colors or typography:**
- Edit `app/src/main/java/com/example/ingredient/ui/theme/Color.kt` or `Type.kt`

**New dependency:**
1. Add version to `gradle/libs.versions.toml`
2. Add `implementation(libs.yourLib)` in `app/build.gradle.kts`

## Firebase Database Structure

Based on code analysis, the RTDB tree is:

```
/ (root)
└── users/
    └── {userId}/                        # Firebase push key
        ├── email: String
        ├── password: String             # PLAINTEXT - security concern
        ├── userType: String             # "Cliente" | "Ristoratore"
        ├── createdAt: Long
        ├── lat: Double                  # Restaurant location (Ristoratore only)
        ├── lon: Double                  # Restaurant location (Ristoratore only)
        ├── menuMetadata/
        │   ├── restaurantName: String
        │   ├── lastUpdated: Long
        │   ├── totalCategories: Int
        │   └── totalDishes: Int
        └── menu/
            └── {categoryId}/            # sanitized category name as key
                ├── categoryName: String
                └── dishes/
                    └── dish_{N}/        # dish_1, dish_2, ...
                        ├── name: String
                        ├── description: String
                        ├── allergens: String
                        ├── price: String
                        ├── originalPrice: String
                        ├── isOffer: Boolean
                        ├── country: String
                        └── region: String
```

## Special Directories

**`.planning/`:**
- Purpose: GSD architecture and planning docs
- Generated: No (human/agent created)
- Committed: Yes

**`app/build/`:**
- Purpose: Gradle build outputs, generated APKs
- Generated: Yes
- Committed: No (in `.gitignore`)

**`.gradle/`:**
- Purpose: Gradle cache
- Generated: Yes
- Committed: No

**`.idea/`:**
- Purpose: Android Studio project settings
- Generated: Yes
- Committed: Partially (project-level settings are committed, user-specific are not)

---

*Structure analysis: 2025-01-31*
