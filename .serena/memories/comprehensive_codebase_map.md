# Comprehensive Codebase Map — Ingredient (May 2026)

## Project Overview
Android app (Kotlin + Jetpack Compose) for restaurant owners to manage menus.
Core feature: QR code scanning → menu import using LLM + OCR + sophisticated deterministic parser.

## Architecture

### 1. Main Entry Points

**MainActivity.kt**
- App lifecycle: Firebase init, SessionManager state routing
- Routes to: disclaimer → login/register → Cliente/Ristoratore/Vetrina/QrMenuImport screens
- Manages TesseractManager and DatabaseReference
- Composable: IngredientApp() with navigation state

**IngredientApplication.kt**
- Initializes LocalAiParser (on-device inference) in background
- Coroutine scope: SupervisorJob + Dispatchers.IO

**QrMenuImportScreen.kt** — Main orchestration
- Flow: scan QR → content type detection → extraction → parsing → enrichment → Firebase upload
- Supports 3 input types:
  1. Plain text (direct LLM analysis)
  2. PDF URL (PdfMenuExtractor → text or OCR)
  3. HTTP URL (WebViewMenuExtractor → structured DOM or visible text)
- Routes via GroqKeyManager to appropriate clients (Gemini for large, Groq for small)
- Final Firebase upload with append mode

### 2. Content Extraction Layer

**WebViewMenuExtractor.kt**
- Off-screen WebView rendering (invisible, attached to activity.window.decorView)
- Priority 0: HTTP pre-fetch for SSR/SSG (extracts inline JS variables)
- Priority 1: WebView JS injection → network interception, accordion expansion, scroll
- Outputs: WebExtractResult (visibleText, capturedApiJson, domSnapshot)
- Three extraction modes:
  1. `__INLINE_JS_MENU__:` → InlineJsMenuParser (fast path)
  2. `__STRUCTURED_DOM__:` → JSON [{t,v}] → processStructuredDom()
  3. Plain text → visible text → processMenuText()

**PdfMenuExtractor.kt**
- Two-stage: text layer first (PdfTextLayerExtractor), fallback to OCR (Tesseract)
- isPdfUrl(): URL pattern + HEAD content-type check
- Quality routing (STRICT/BALANCED/AGGRESSIVE): depends on ParsingMode
  - STRICT: text if quality >= 0.70
  - BALANCED (default): text if quality >= 0.50
  - AGGRESSIVE: text first, OCR only on parse failure
- Max 15 pages, 1200px width rendering
- detectOcrLang(): Italian signals → "spa", else "eng"

**ImageMenuExtractor.kt**
- Google ML Kit (Latin text recognizer) for camera/album images
- OpenCV preprocessing: scale to 1400px, grayscale, deskew (Hough lines), adaptive threshold, sharpen
- Supports multiple image inputs with section markers (=== FOTO N ===)

**MenuContentPreprocessor.kt**
- Pre-processes raw content before LLM
- Extracts clean menu from Next.js __NEXT_DATA__ (recursive depth 8)
- Extracts from generic API JSON blobs
- Falls back to visible text if no structured data found

### 3. LLM Layer (Dual Provider)

**GroqKeyManager.kt** — Provider routing
```
semanticClient()  → Gemini 2.5 Flash (1M context, heavy AST, SemanticIntelligenceLayer)
enrichClient()    → Groq llama-3.1-8b-instant (fast, small batch enrichment)
domFilterClient() → Groq (DOM element classification, cheapest)
clientPool()      → [Gemini, Groq KEY_1, Groq KEY_2] for parallel distribution
```

**LLMApiClient.kt** — Dual-provider API client
- Models: Gemini 2.5 Flash, Groq llama-3.1-8b-instant
- Three dispatch modes:
  1. **processMenuText()**: plain text → full menu extraction
     - Auto-detects section markers (=== CATEGORIA: X ===)
     - If > 300k chars: chunked processing
     - Fallback cascade: Gemini quota → Groq KEY_1 → Groq KEY_2
  2. **processStructuredDom()**: DOM JSON [{t,v}] → compressed candidates → LLM extraction
  3. **processBySections()**: section-by-section parallel with per-section retry
- Chunk size: 2500 chars, temperature 0.1 (main) / 0.2 (structured)
- Per-client pool for parallel distribution

**LocalAiParser.kt** — On-device inference
- Google MediaPipe LlmInference (GGUF model)
- Model: models/menu_parser.gguf → cacheDir/menu_parser.bin
- Fallback if model unavailable
- Max 1024 tokens, temp 0.2

### 4. Parsing Pipeline — Deterministic Menu Parser

**MenuParserPipeline.kt** — 7-stage sophisticated parser
```
1. Preprocessing (MenuContentPreprocessor)
2. OCR correction (OcrPostProcessor)
3. Lexer → LineClassifier (tokenize with semantic types)
4. Grammar parser → MenuGrammarParser (state machine)
5. Structural validation & repair (StructuralValidator, RepairEngine)
6. Confidence scoring (ConfidenceEngine)
7. Multi-pass reconciliation + SemanticIntelligenceLayer
```

**Output**: MenuParseResult
- categories: List<MenuCategory>
- ast: MenuAST
- confidence: Float
- mode: ParsingMode (STRICT/BALANCED/AGGRESSIVE)
- usedLlmFallback: Boolean
- warnings: List<String>

**MenuGrammarParser.kt** — State machine
- StatefulParser with transitions for category headers, prices, descriptions, dish candidates
- Handles price association (STANDALONE, SAME_LINE, NEXT_LINE)
- Description binding (ABOVE, BELOW, SAME_LINE)

**LineClassifier.kt** — Semantic tokenization
- Classifies each line: Noise, Divider, StrongHeader, WeakHeader, DishCandidate, DishWithPrice, StandalonePrice, Allergen, Description
- Locale-aware: Italian, English, Spanish, etc. (LocalePackRegistry)
- Price detection: €, dollar, commas, decimals
- Allergen parsing: numbers + asterisks

### 5. AST & Semantic Intelligence

**MenuAST.kt** — Abstract syntax tree
- Structures: MenuAST, ASTSection, ASTCategoryHeader, ASTMenuItem, ASTPrice, ASTDescription
- Enums: PriceAssociation, DescriptionBindingMethod, SourceType, ParsingMode
- Confidence tracking: ASTConfidence (overall, headerConfidence, itemConfidence, descriptionConfidence)

**SemanticIntelligenceLayer.kt** — 4-step LLM-powered validation
1. OCR repair (Gemini 2.5 Flash → fix garbled text)
2. Plausibility check (food name validation via LLM)
3. Category validation (anomaly detection → dish in wrong category)
4. Enrichment (ingredients, calories, country, region inference)

**SemanticAnomalyDetector.kt**
- FoodOntology reference (Italian + international dishes)
- Flags dishes in wrong categories (e.g., "Cappuccino" in "Secondo")
- Suggests corrections, calculates confidence

**MultiPassReconciler.kt**
- Passes 1-4: visual promotion, ontology correction, confidence propagation
- Visual groups integration (from DOM snapshots)

### 6. DOM-Aware Extraction (Phase 01-02)

**DomSnapshot.kt**
- Captures structured DOM after JS rendering
- DomElement: {t, d, v} = tag, depth, visible text
- Used for visual block detection (Imgproc-like scoring)

**MenuBlockClassifier.kt**
- Scores DOM blocks for menu likelihood
- Considers: heading tags, list structures, price patterns, text density

**DomBlockScorer.kt**
- Heuristic scoring: block type, heading presence, menu keywords, structural signals

**BlockMerger.kt**, **BlockTextExtractor.kt**, **VisualBlockDetector.kt**
- Merge adjacent blocks, extract clean text, detect visual grouping

### 7. Data Models

**MenuItem**
```kotlin
name, description, allergens, ingredients, price, originalPrice, isOffer
country, region, cucina, calories
```

**MenuCategory**
```kotlin
categoryName, dishes: List<MenuItem>
```

**User** (Firebase schema)
```
id, nome, cognome, email, password, userType, allergeni, createdAt
```

**Restaurant** (Firebase schema)
```
restaurantId, nomeRistorante, indirizzo, telefono, tipoCucina, lat, lon, createdAt
```

### 8. Firebase Integration

**FirebaseMenuUploader.kt**
- uploadMenu(): dishes/{dishId} root level (approved schema)
- Field names: Italian (nome, ingredienti, cucina, regione, paese, prezzo, offerta, prezzoOfferta)
- Append vs replace modes
- Reads restaurantName from /restaurants/{userId} (fallback to /users)
- getMenu(): queries by restaurantId, groups by cucina

### 9. OCR & Image Processing

**EnhancedTesseractManager.kt**
- Tesseract 5 (eng.traineddata, spa.traineddata in assets)
- Column detection: ImageColumnSplitter
- processImage(): split columns → process separately → merge with markers
- Supports manual column selection: ManualColumnSelector.kt

**ImageColumnSplitter.kt**
- Vertical edge detection → column boundaries
- Adaptive threshold-based

### 10. Utility Parsers

**MenuParser.kt** — LLM JSON response parser
- Parses standard LLM JSON array → List<MenuCategory>
- Handles truncated JSON (repairTruncatedJsonArray)
- Filter: name.isNotEmpty() only (no price > 0.0 requirement)
- Category names: validates, uses "Other" for invalid

**InlineJsMenuParser.kt** — Canonical JS format parser
- Handles { "categories": [ { "name": "X", "items": [ { "name": "Y", "price": Z } ] } ] }
- JSON sanitization: remove literal newlines, trailing backslashes

### 11. Session & Auth

**SessionManager.kt**
- Shared preferences: user auth state, user ID, user type, disclaimer acceptance
- Methods: saveSession, getUserId, getUserType, isLoggedIn, logout, isDisclaimerAccepted

### 12. Config & Constants

**BuildConfig** (gradle-generated)
- GEMINI_API_KEY, GROQ_API_KEY, GROQ_API_KEY_2
- LLM fallback logic in GroqKeyManager

## Constraints & Gotchas

### Critical
1. **Tesseract language**: "ita" does NOT exist in assets → SIGSEGV. Use "eng" or "spa" only. "spa" is actually Italian.
2. **WebView**: evaluateJavascript only works when attached to activity.window.decorView (must be 1×1 invisible)
3. **Groq TPM**: 6000 tokens/min free tier. Max single call = 4096 tokens, prompt size ≤ 2000
4. **Gemini quota**: Daily limit → auto-fallback to Groq
5. **llama-3.1-8b**: DO NOT change to 70b (breaks JSON output)
6. **Price filter**: name.isNotEmpty() only — websites rarely expose prices

### Known Issues
- 413 (too large) → auto-fallback to chunking
- 429 (rate limit) → auto-retry with parsed wait time (max 3 retries)
- HTTP 403/401 → fail immediately with anti-bot warning
- OCR 1920px→3840px scale to MIN_OCR_WIDTH for ML Kit

## Parsing Modes

- **STRICT** (confidence floor 0.80): only highest-confidence results
- **BALANCED** (default, confidence floor 0.45): mixed heuristic + LLM
- **AGGRESSIVE** (confidence floor 0.15): capture everything, LLM repair later

## Performance Notes

- Pipeline: preprocess + lexer + grammar ≈ 100-500ms for typical menu
- WebView render + JS inject ≈ 5-10s (with timeouts)
- LLM extraction ≈ 2-10s (depends on size)
- Total flow: ≈ 20-40s for typical QR menu import

## Testing Entry Points

- QrMenuImportScreen (full orchestration)
- MenuParserPipeline (deterministic parsing)
- PdfMenuExtractor (PDF text/OCR)
- WebViewMenuExtractor (JS rendering)
- SemanticIntelligenceLayer (LLM validation)

## File Count

87 Kotlin source files organized in:
- Root level: orchestration, extractors, LLM, parsers
- /parser/: grammar, lexer, DOM, AST, confidence, semantic, PDF, observability
- /model/: data classes, session manager
- /ui/theme/: theme, colors, typography
- Screens: QrCode, QrMenuImport, MenuEditor, Vetrina, ClienteScreens, AuthScreens
