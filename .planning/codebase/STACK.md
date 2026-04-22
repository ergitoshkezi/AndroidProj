# Technology Stack

**Analysis Date:** 2025-01-23

## Languages

**Primary:**
- Kotlin - All application source code (`app/src/main/java/com/example/ingredient/`)

**Secondary:**
- XML - Android resources (layouts, manifests, drawables) in `app/src/main/res/`

## Runtime

**Environment:**
- Android SDK — minSdk 24 (Android 7.0 Nougat), targetSdk 36, compileSdk 36
- JVM Target: Java 11 (set in `app/build.gradle.kts` via `kotlinOptions.jvmTarget = "11"`)

**Package Manager:**
- Gradle 8.13 (via `gradle/wrapper/gradle-wrapper.properties`)
- AGP (Android Gradle Plugin) 8.13.1 (defined in `gradle/libs.versions.toml`)
- Lockfile: Not present (no `gradle.lockfile`)

## Frameworks

**Core UI:**
- Jetpack Compose BOM `2024.09.00` — Declarative UI framework
  - `androidx.compose.ui` — Core Compose UI
  - `androidx.compose.material3` — Material Design 3 components
  - `androidx.compose.material3:material3-adaptive-navigation-suite` — Adaptive nav
  - `androidx.compose.material:material-icons-extended` — Extended icon set

**Android Architecture:**
- `androidx.core:core-ktx:1.17.0` — Kotlin extensions for Android core
- `androidx.lifecycle:lifecycle-runtime-ktx:2.9.4` — Lifecycle-aware coroutine scope
- `androidx.activity:activity-compose:1.11.0` — Compose integration for Activity

**Asynchronous:**
- Kotlin Coroutines `1.7.3` (`kotlinx-coroutines-android` + `kotlinx-coroutines-core`)
  - Used for all Firebase operations, OCR processing, and LLM inference
  - `Dispatchers.IO` for network/file I/O; `Dispatchers.Main` implied via Compose state

**Testing:**
- JUnit 4 `4.13.2` — Unit tests (`app/src/test/`)
- `androidx.test.ext:junit:1.3.0` — AndroidX JUnit integration
- `androidx.test.espresso:espresso-core:3.7.0` — UI testing
- Compose UI Test `junit4` — Compose-specific UI tests

**Build/Dev:**
- Kotlin Compose Compiler Plugin `2.0.21` (`kotlin.compose` plugin)
- ProGuard — configured in `app/proguard-rules.pro` (minification disabled in release currently)

## Key Dependencies

**OCR / Image Processing:**
- `cz.adaptech.tesseract4android:tesseract4android:4.9.0` — On-device OCR engine (Tesseract 4)
  - Trained data files bundled in `app/src/main/assets/models/` (`eng.traineddata`, `spa.traineddata`)
  - Custom column-detection preprocessing via `ImageColumnSplitter.kt`
- `com.google.mlkit:text-recognition:16.0.0` — Google ML Kit text recognition (declared as dependency, available as alternative OCR path)

**Local AI / LLM:**
- `com.google.mediapipe:tasks-genai:0.10.14` — MediaPipe LLM inference (on-device)
  - Model file expected at `assets/models/menu_parser.gguf` (GGUF format)
  - Initialized in `LocalAiParser.kt` via `LlmInference` API
  - Config: maxTokens=1024, temperature=0.2, topK=40
- `de.kherud:llama:3.0.0` — llama.cpp JNI binding (declared but primary path uses MediaPipe)

**Firebase:**
- `com.google.firebase:firebase-bom:34.6.0` — Firebase BOM for version management
- `com.google.firebase:firebase-database:22.0.1` — Firebase Realtime Database
- `com.google.firebase:firebase-storage` — Firebase Storage (declared for future file uploads)

**Networking:**
- `com.squareup.okhttp3:okhttp:4.12.0` — HTTP client (declared; LLM API uses `HttpURLConnection` directly in `LLMApiClient.kt`)
- `com.google.code.gson:gson:2.10.1` — JSON serialization (declared; actual parsing uses `org.json` built-in)

**Location:**
- `com.google.android.gms:play-services-location:21.0.1` — FusedLocationProviderClient for geolocation

## Configuration

**Environment:**
- No `.env` files — configuration via `local.properties` (SDK paths, not committed) and `google-services.json`
- LLM API key passed at runtime as constructor parameter to `LLMApiClient(apiKey: String)` — not hardcoded
- Firebase project configured via `app/google-services.json` (project ID: `rabiotv01`)

**Build:**
- Version catalog: `gradle/libs.versions.toml` — centralized dependency version management
- Top-level config: `build.gradle.kts` (root), `app/build.gradle.kts` (app module)
- Settings: `settings.gradle.kts` — single-module project, JitPack repo included
- JVM Gradle daemon: `-Xmx2048m` heap (from `gradle.properties`)
- `android.nonTransitiveRClass=true` — R class optimization enabled
- `kotlin.code.style=official`
- Build features: `compose = true` (enables Compose compiler)
- Release: minification disabled (`isMinifyEnabled = false`) — **not production-hardened**

## Platform Requirements

**Development:**
- Android Studio (project uses `.idea/` config)
- JDK 11+
- Android SDK with API 36 installed

**Production:**
- Android 7.0+ (API 24+)
- Internet permission required (`INTERNET`, `ACCESS_NETWORK_STATE`)
- Location permissions: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
- External storage permissions (capped at API 32 via `android:maxSdkVersion="32"`)
- Large local model file (`menu_parser.gguf`) bundled in APK assets — significant APK size impact

---

*Stack analysis: 2025-01-23*
