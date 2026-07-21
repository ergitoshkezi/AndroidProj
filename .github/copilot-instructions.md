# Copilot Instructions — Ingredient App

## Agent Tooling Boundaries

This file is loaded by **Copilot CLI** (`gh copilot` / GitHub Copilot). It does NOT support MCP servers.

User's MCP servers (Serena, claude-mem, gitnexus) are configured in `~/.claude.json` and only work in **Claude Code** (`claude` CLI) sessions. Do not suggest using Serena or any MCP tool from Copilot CLI — they are unavailable here.

| Agent | Memory | MCP / Serena | LSP |
|---|---|---|---|
| **Claude Code** (`claude`) | `~/.claude/CLAUDE.md` | ✅ Serena + claude-mem + gitnexus | ✅ via Serena |
| **Copilot CLI** (this) | `.github/copilot-instructions.md` | ❌ | ✅ built-in `lsp` tool |

## Build & Test

All Gradle commands run from `Ingredient/Ingredient/` (the project root, where `gradlew` lives).
Set `JAVA_HOME` to Android Studio's JBR on Windows if `java` isn't on PATH:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

```bash
# Build debug APK
./gradlew assembleDebug

# Clean + rebuild (use after changing local.properties or build.gradle.kts)
./gradlew clean assembleDebug

# Run all unit tests (JVM, no device needed)
./gradlew :app:test

# Run a single test class
./gradlew :app:test --tests "com.example.ingredient.parser.MenuTokenizerTest"

# Run a single test method
./gradlew :app:test --tests "com.example.ingredient.parser.ConfidenceEngineTest.someMethodName"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest
```

No linter config — formatting follows Android Studio defaults (4-space indent). No ktlint, no detekt.

## API Keys

Keys live in `Ingredient/Ingredient/local.properties` (not committed). Three keys are active:
```
groq.api.key=<groq primary key>
groq.api.key.2=<groq secondary key>
gemini.api.key=<gemini key>
```
They are injected via `build.gradle.kts` as `BuildConfig.GROQ_API_KEY`, `BuildConfig.GROQ_API_KEY_2`, and `BuildConfig.GEMINI_API_KEY`. **Never hardcode keys.** Always obtain clients via `GroqKeyManager`.

After editing `local.properties`, run `./gradlew clean assembleDebug` — a stale `BuildConfig` will silently send an empty key.

## LLM Routing — `GroqKeyManager` + `LLMApiClient`

`LLMProvider` enum: `GEMINI`, `GROQ`. `LLMApiClient(apiKey, provider)` switches the backend accordingly.

`GroqKeyManager` purpose-based factories:

| Method | Provider | Used for |
|---|---|---|
| `semanticClient()` | Gemini 2.5 Flash | `SemanticIntelligenceLayer`, full menu extraction |
| `enrichClient()` | Groq KEY_1 | `enrichDishes()` — small batches of 15 parsed dishes |
| `domFilterClient()` | Groq KEY_2 → KEY_1 → Gemini fallback | `DomSectionAiFilter`, `DomLlmElementFilter` |
| `clientPool()` | [Gemini, Groq KEY_1, Groq KEY_2] | Parallel section/chunk/batch distribution |

**Parallel processing:** Pass `clientPool = GroqKeyManager.clientPool()` to `processMenuText()` and `enrichDishes()`. Sections/chunks/batches are distributed round-robin across the pool and processed with `async { }.awaitAll()`. This triples throughput and avoids per-key TPM limits.

**Raw LLM call** (no menu-extraction wrapper): use `llmClient.complete(prompt, temperature, maxTokens)`. Use this for classification tasks (`DomLlmElementFilter`) — **not** `processMenuText()`, which adds a large system prompt.

## Architecture Overview

Single-module Android app. Package root: `com.example.ingredient`.

### User Flows
Two user types: **Cliente** (customer browsing menus) and **Ristoratore** (restaurant owner managing menus). Navigation is a `when(currentScreen: String)` block in the top-level `IngredientApp` composable — no Jetpack Navigation component.

### Menu Ingestion Pipeline
`QrMenuImportScreen.processUrl()` is the entry point. Routes through three source types:
1. **QR direct text** → straight to parser
2. **PDF URL** → `PdfMenuExtractor` (PDFBox-Android)
3. **Web URL** → `WebViewMenuExtractor` (JS injection) → `DomLlmElementFilter` (parallel, Gemini pool) → text extraction

The extracted text then goes to `MenuParserPipeline.parse()`.

4. **Photo/image** → `ImageMenuExtractor.extractText(context, uris)` — OpenCV preprocessing (deskew, adaptive threshold, sharpen) → ML Kit OCR → text → parser. Entry point in `ClienteScreens` image picker flow.

**`MenuParserPipeline` requires Android Context** (SharedPreferences + LLM init) — cannot be unit-tested with JVM tests. Unit tests exercise `LineClassifier` + `MenuGrammarParser` directly. Regression fixtures live in `app/src/test/resources/fixtures/menus/`.

### MenuParserPipeline (deterministic, `parser/` subpackage)
Layers in execution order:
1. `MenuContentPreprocessor` — encoding cleanup
2. `OcrPostProcessor` — OCR noise repair (for OCR/PDF sources)
3. `LocalePackRegistry` — locale detection
4. `LineClassifier` → `TypedLine` tokens (lexer)
5. `MenuGrammarParser` → `MenuAST` (grammar); `WindowedContextResolver` assists with multi-line context
6. `StructuralValidator` — halt-on-error gate
7. `RepairEngine` + `RepairHeuristics` — heuristic fixes
8. `ConfidenceEngine` → `ConfidenceVector` per node
9. `MultiPassReconciler` — 4-pass visual/ontology/confidence reconciliation
10. `SemanticIntelligenceLayer` (Gemini via `semanticClient()`) — OCR repair, plausibility, category validation, enrichment
11. `AstToDtoMapper` → `List<MenuCategory>` (output DTOs)

The pipeline **never uses LLM for identifying categories or dishes** — that is purely deterministic. LLM is restricted to: (a) enrichment (ingredients, allergens, calories) via `LLMEnricher`, and (b) semantic plausibility validation via `SemanticIntelligenceLayer`.

**`AiBudgetManager`** (`parser/budget/`) gates LLM calls by token budget. Check budget before any LLM call inside a pipeline stage.

### `processMenuText` splitting strategy

Text is split at `=== CATEGORIA: X ===` / `=== SEZIONE: X ===` markers (produced by DOM segmentation). Each section is one complete category block — **a single section always goes to a single LLM call**, preventing mid-dish splits. Only texts >300k chars trigger `processInChunks`, which splits at section-header line boundaries (never mid-line). `splitTextNoOverlap` is intentionally removed — Gemini's 1M-token context handles any real menu as one call.

### DOM Pipeline (`parser/dom/`)
`WebViewMenuExtractor` feeds a DOM snapshot (via `JS_EXTRACT_DOM_SNAPSHOT` JS injection) into:
- `DomSnapshotParser` — JSON → `DomBlock`/`DomSnapshot` Kotlin objects
- `DomBlockScorer` + `DomFingerprintProfile` + `DomainTrustProfile` — 5-signal scoring
- `DomLlmElementFilter(llmClient, clientPool)` — batches of 25 elements, parallel across pool
- `MenuBlockClassifier` → `SemanticBlock`; `MenuBlockSelector` — threshold-based selection
- `BlockMerger` + `BlockTextExtractor` — merge adjacent blocks → plain text
- `DomSegmentationEngine` — orchestrates the full DOM-to-text sub-pipeline

### PDF Pipeline (`parser/pdf/`)
`PdfMenuExtractor` (PDFBox-Android) → `PdfQualityChecker` → `PdfTextLayerExtractor` (or `PdfPageRenderer` for OCR fallback) → `PdfTextCleaner` → pipeline ingestion.

### Domain Models (`model/`)
`User`, `Restaurant`, `AllergeneType`, `VetrinaPhoto` — Firebase-mapped data classes. `SessionManager` holds current `userId` and `userType` as in-memory state (no persistence layer).

### Firebase
`DatabaseReference` is passed directly into composables (no repository layer):
- **Reads**: callback-style `addListenerForSingleValueEvent` inside composables
- **Writes**: suspend functions with `.await()` in `FirebaseMenuUploader`

Data path: `users/{userId}/menu/{categoryId}/{dishId}`.

### Local AI
`LocalAiParser` (singleton object, llama.cpp + MediaPipe) is initialized at app startup in `IngredientApplication` on `Dispatchers.IO`. It may not be ready immediately — composables poll `LocalAiParser.isInitialized`.

## Key Conventions

**State management**: No ViewModel, no StateFlow. All state is `var x by remember { mutableStateOf(...) }` at the composable call site.

**Coroutine scopes**:
- User-action coroutines: `rememberCoroutineScope().launch { }` inside composables
- App-level background: `CoroutineScope(SupervisorJob() + Dispatchers.IO)` in `IngredientApplication`
- Network calls: `withContext(Dispatchers.IO)` inside service classes
- Parallel LLM calls: `coroutineScope { list.mapIndexed { i, x -> async { } }.awaitAll() }`

**Error handling**:
- UI: `var errorMessage by remember { mutableStateOf("") }` displayed inline
- Service layer: `Result<T>` return type — never throw from suspend functions
- Firebase `onCancelled`: set `errorMessage`, log with `Log.e(TAG, ..., error.toException())`

**Constants**: Declared as `private val TAG = "ClassName"` at instance level, not in companion objects.

**Parser sub-packages** (`parser/`): `ast`, `budget`, `confidence`, `dom`, `domain`, `enrichment`, `grammar`, `lexer`, `locale`, `mapping`, `observability`, `ontology`, `pdf`, `pipeline`, `regex`, `repair`, `semantic`, `source`, `validation`. New parser components go in the appropriate sub-package, not in the root `parser/` or the top-level package.

**`MenuAST` is immutable** — every pipeline stage receives an AST and returns a new one. Never mutate in place.

**`ParseLogger` / `MetricsCollector`**: Use these for observability within the parser. Don't add raw `Log.d` calls without also recording to the trace engine when inside pipeline stages.

### Navigation Screen Strings

`IngredientApp` composable routes via `when(currentScreen: String)`. Valid values:

| String | Screen |
|---|---|
| `"disclaimer"` | First-run disclaimer |
| `"Login"` | Login |
| `"Register"` | Registration |
| `"Cliente"` | Customer home |
| `"Ristoratore"` | Restaurant owner home |
| `"MenuEditor"` | Edit existing menu |
| `"QrCode"` | Generate QR code |
| `"QrMenuImport"` | Scan QR / import menu |
| `"Vetrina"` | Photo showcase (customer view) |

