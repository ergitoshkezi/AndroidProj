# External Integrations

**Analysis Date:** 2025-01-23

## APIs & External Services

**LLM / AI (Remote):**
- Siemens LLM API — Menu text parsing and structured JSON extraction
  - Endpoint: `https://api.siemens.com/llm/v1/chat/completions`
  - SDK/Client: Raw `HttpURLConnection` — no SDK, manually constructed in `LLMApiClient.kt`
  - Auth: Bearer token — `apiKey: String` passed as constructor parameter to `LLMApiClient`
  - Model: `mistral-7b-instruct`
  - Timeouts: 90s connect / 90s read
  - Request payload built with `org.json.JSONObject/JSONArray`
  - Chunking fallback: texts > 6000 chars are split into 2500-char chunks
  - Response parsing: extracts `choices[0].message.content` from OpenAI-compatible response

**LLM / AI (Local / On-Device):**
- MediaPipe `tasks-genai` (Google) — On-device LLM inference
  - SDK: `com.google.mediapipe:tasks-genai:0.10.14`
  - Implementation: `LocalAiParser.kt` (singleton object)
  - Model format: GGUF — loaded from `assets/models/menu_parser.gguf` → copied to `context.cacheDir/menu_parser.bin` on first run
  - Config: maxTokens=1024, temperature=0.2f, topK=40
  - Initialization: kicked off in `IngredientApplication.onCreate()` on `Dispatchers.IO`
  - `llama.cpp` binding (`de.kherud:llama:3.0.0`) also declared but primary inference path uses MediaPipe

**OCR (On-Device):**
- Tesseract 4 — Menu image text extraction
  - SDK: `cz.adaptech.tesseract4android:tesseract4android:4.9.0`
  - Implementation: `TesseractManager.kt` (`EnhancedTesseractManager` class)
  - Trained data: `assets/models/eng.traineddata` and `assets/models/spa.traineddata` — copied to `context.getExternalFilesDir(null)/tessdata/` on first init
  - Languages supported: English (`eng`), Spanish (`spa`)
  - Custom preprocessing: `ImageColumnSplitter.kt` auto-detects multi-column menu layouts and splits image before OCR
  - Manual column override: `ManualColumnSelector.kt` — Compose UI for user-defined column boundaries
  
- Google ML Kit Text Recognition — Alternative OCR path
  - SDK: `com.google.mlkit:text-recognition:16.0.0`
  - Declared in `app/build.gradle.kts`; available as secondary OCR option

## Data Storage

**Databases:**
- Firebase Realtime Database (NOT Firestore)
  - Provider: Google Firebase (project ID: `rabiotv01`)
  - SDK: `com.google.firebase:firebase-database:22.0.1` (via BOM `34.6.0`)
  - Client: `FirebaseDatabase.getInstance().getReference()` — initialized in `MainActivity.onCreate()`
  - Implementation: `FirebaseMenuUploader.kt` — all read/write operations
  - Coroutine integration: `kotlinx.coroutines.tasks.await()` extension for `Task<T>`

  **Database Schema:**
  ```
  /users/{userId}/
    email:            String
    password:         String          ← plaintext, NOT hashed (security concern)
    userType:         String          ("Cliente" | "Ristoratore")
    createdAt:        Long            (System.currentTimeMillis())
    /menu/{categoryId}/
      categoryName:   String
      /dishes/{dishId}/               (dishId = "dish_1", "dish_2", ...)
        name:         String
        description:  String
        allergens:    String
        price:        String
    /menuMetadata/
      restaurantName: String
      lastUpdated:    Long
      totalCategories: Int
      totalDishes:    Int
  ```

  **Key operations in `FirebaseMenuUploader.kt`:**
  - `uploadMenu(userId, categories)` — clears existing menu then re-uploads all categories
  - `uploadMenuWithMetadata(userId, restaurantName, categories)` — upload + write metadata node
  - `getMenu(userId)` — reads full menu tree and deserializes to `List<MenuCategory>`
  - Key sanitization: category names sanitized (lowercase, spaces→underscores, special chars→underscores)

  **Auth queries in `AuthScreens.kt`:**
  - Login: `databaseReference.child("users").addListenerForSingleValueEvent(...)` — scans ALL users to match email/password
  - Registration: similar full-scan to check email uniqueness, then `push().key` to generate userId
  - No Firebase Authentication SDK used — custom credential comparison against DB

**File Storage:**
- `com.google.firebase:firebase-storage` declared in `app/build.gradle.kts` — for future file uploads
- Not yet actively used in source code
- Local on-device storage:
  - Tesseract trained data: `context.getExternalFilesDir(null)/tessdata/`
  - MediaPipe model copy: `context.cacheDir/menu_parser.bin`

**Caching:**
- None — no in-memory cache or persistent cache layer beyond the local model file copy

## Authentication & Identity

**Auth Provider:**
- Custom — no Firebase Auth, no OAuth, no external identity provider
- Implementation: `AuthScreens.kt` (`LoginScreen`, `RegistrationScreen` Composables)
- Mechanism: email + plaintext password stored and compared directly in Firebase Realtime Database
- Session: userId stored in `MainActivity` Compose `remember { mutableStateOf<String?>(null) }` — ephemeral, not persisted across app restarts
- User types: `"Cliente"` (end user / customer) or `"Ristoratore"` (restaurant owner)
- Navigation post-auth: state machine in `IngredientApp()` composable routes to `ClienteScreen` or `Ristoratore` screen based on `userType`

## Location Services

**Provider:** Google Play Services FusedLocationProviderClient
- SDK: `com.google.android.gms:play-services-location:21.0.1`
- Implementation: `ClienteScreens.kt` — `LocationServices.getFusedLocationProviderClient(context)`
- Usage: `fusedLocationClient.lastLocation` — passive last-known location, not active tracking
- Permissions: `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` declared in `AndroidManifest.xml`
- Result: `Location` object used to compute distance from restaurants for `SearchResult.distance`
- Error handling: `SecurityException` caught silently (returns null location)

## Monitoring & Observability

**Error Tracking:**
- None — no Crashlytics, Sentry, or similar

**Logs:**
- `android.util.Log` throughout all classes with per-class `TAG` constants
  - All classes use pattern: `private val TAG = "ClassName"`
  - Debug logs on happy path, `Log.e()` on exceptions

## CI/CD & Deployment

**Hosting:**
- Android APK — sideloaded or Play Store (not configured)

**CI Pipeline:**
- None detected — no `.github/workflows/`, no CI config files

## Environment Configuration

**Required configuration (non-secret):**
- `google-services.json` must be present at `app/google-services.json` — Firebase project config
- Tesseract trained data files must be present: `app/src/main/assets/models/eng.traineddata`, `app/src/main/assets/models/spa.traineddata`
- MediaPipe GGUF model must be present: `app/src/main/assets/models/menu_parser.gguf`

**Required secrets (injected at runtime, not in source):**
- Siemens LLM API key — passed as `apiKey` string parameter when constructing `LLMApiClient`
- No `BuildConfig` fields or `local.properties` key injection observed for secrets

**Secrets location:**
- Not committed to source — API key is passed programmatically at call-site

## Webhooks & Callbacks

**Incoming:**
- None — no webhook endpoints (Android client app only)

**Outgoing:**
- None — all external calls are client-initiated request/response

## Network Layer

**HTTP Client (LLM API):**
- `java.net.HttpURLConnection` — used directly in `LLMApiClient.invokeAPI()`
- `OkHttp 4.12.0` declared as dependency but **not actively used** in current source
- All network calls dispatched on `Dispatchers.IO` via `withContext`

**Firebase SDK networking:**
- Handled internally by Firebase Realtime Database SDK
- All Firebase operations wrapped with `.await()` coroutine extension

**Request/Response format:**
- LLM API: OpenAI-compatible chat completions format (JSON POST, JSON response)
- Firebase: SDK handles serialization/deserialization

---

*Integration audit: 2025-01-23*
