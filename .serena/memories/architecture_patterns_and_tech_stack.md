# Architecture Patterns & Tech Stack — Ingredient

## Architecture Overview

### Layered Pipeline Architecture
```
QR Code Input
    ↓
Content Type Detection (plain text / PDF / HTTP)
    ↓
Content Extraction Layer (PdfMenuExtractor / WebViewMenuExtractor / direct)
    ↓
Preprocessing (MenuContentPreprocessor)
    ↓
Deterministic Parser Pipeline (7 stages: lexer → grammar → validation → repair → scoring)
    ↓
AST (MenuAST) with confidence scores
    ↓
LLM Extraction Layer (GroqKeyManager routing: Gemini or Groq)
    ↓
Semantic Intelligence Layer (4-stage LLM validation)
    ↓
Enrichment (2-pass: raw + fill gaps)
    ↓
MenuCategory[] with 100% non-empty critical fields
    ↓
Firebase Upload
```

### Key Design Patterns

1. **Dual-Provider LLM Routing** (GroqKeyManager.kt)
   - Semantic client: Gemini 2.5 Flash (heavy analysis, 1M context)
   - Enrichment client: Groq llama-3.1-8b (fast, small batches)
   - DOM filter client: Groq (cheapest, quick classification)
   - Client pool: parallel distribution across keys
   - Auto-fallback on quota/rate limits

2. **Multi-Stage Parsing Pipeline** (MenuParserPipeline.kt)
   - 7-stage deterministic + optional LLM validation
   - Confidence scoring at each stage
   - Mode-based thresholds: STRICT/BALANCED/AGGRESSIVE
   - Repair heuristics before LLM (reduces token cost)

3. **Stateful Grammar Parser** (MenuGrammarParser.kt)
   - State machine: INIT → HEADER → ITEMS → DESCRIPTION
   - Price association tracking: STANDALONE, SAME_LINE, NEXT_LINE
   - Description binding: ABOVE, BELOW, SAME_LINE
   - Handles ambiguous multi-line structures

4. **Content Extraction Priority Queue**
   - WebViewMenuExtractor: HTTP pre-fetch → WebView JS injection (3 routes)
   - PdfMenuExtractor: text layer first, OCR fallback
   - ImageMenuExtractor: ML Kit + OpenCV preprocessing
   - MenuContentPreprocessor: JSON navigation before plain text

5. **Semantic Validation Loop**
   - SemanticIntelligenceLayer: 4-stage LLM-powered validation
   - SemanticAnomalyDetector: FoodOntology reference
   - MultiPassReconciler: visual + ontology + confidence passes
   - Optional (only if confidence >= 0.30 to save tokens)

6. **2-Pass Enrichment**
   - Pass 1: raw extraction (dishes as found)
   - Pass 2: targeted enrichment (fill gaps only, never overwrite)
   - Batch strategy: 15 dishes at a time (2048 maxTokens)

## Tech Stack

### Core Framework
- **Kotlin 1.9+** (Android 12+)
- **Jetpack Compose** (UI, all screens)
- **Firebase Realtime Database** (menu storage)
- **Coroutines** (async orchestration)

### Extraction & Processing
- **OkHttp3** (HTTP client, network interception)
- **WebView** (JS rendering for SPA sites)
- **Android PDF Renderer** (native PDF pages → bitmap)
- **Tesseract OCR** (eng, spa tessdata in assets)
- **Google ML Kit** (ML-powered text recognition for images)
- **OpenCV Android** (image preprocessing, deskew, adaptive threshold, sharpen)

### LLM Integration
- **Groq API** (fast, cheap inference)
- **Google Gemini API** (heavy context, analysis)
- **Google MediaPipe LLMInference** (on-device GGUF models)
- **OkHttp** with custom interceptors

### Data Processing
- **JSONArray / JSONObject** (manual parsing)
- **Regex** (price, allergen, pattern matching)
- **Jsoup-like DOM navigation** (not explicitly listed but inferred from code)

### Development Tools
- **Gradle** (build system, BuildConfig.GEMINI_API_KEY, etc.)
- **Logcat** (Android logging)
- **adb** (device debugging)

## File Organization

```
Ingredient/app/src/main/java/com/example/ingredient/
├── MainActivity.kt                          # App entry, nav routing
├── IngredientApplication.kt                 # App class, init LocalAiParser
├── GroqKeyManager.kt                        # LLM provider routing (Gemini/Groq)
├── LLMApiClient.kt                          # Dual-provider API client
├── LocalAiParser.kt                         # On-device MediaPipe inference
├── QrMenuImportScreen.kt                    # Main orchestration composable
├── QrCodeScreen.kt, MenuEditorScreen.kt, VetrinaScreen.kt, AuthScreens.kt, ClienteScreens.kt
├── WebViewMenuExtractor.kt                  # JS rendering + DOM extraction
├── PdfMenuExtractor.kt                      # PDF text/OCR
├── ImageMenuExtractor.kt                    # Camera/album image OCR
├── MenuContentPreprocessor.kt               # Next.js/__NEXT_DATA__ extraction
├── MenuParser.kt                            # LLM JSON array parser
├── InlineJsMenuParser.kt                    # Canonical JS menu format parser
├── FirebaseMenuUploader.kt                  # Firebase integration
├── TesseractManager.kt / EnhancedTesseractManager.kt   # OCR manager
├── ImageColumnSplitter.kt, ManualColumnSelector.kt     # Image processing
├── model/
│   ├── User.kt, Restaurant.kt, SessionManager.kt, AllergeneType.kt, VetrinaPhoto.kt
├── ui/theme/
│   ├── Type.kt, Color.kt, Theme.kt
├── parser/
│   ├── MenuParserPipeline.kt                # Main 7-stage pipeline
│   ├── ast/
│   │   └── MenuAST.kt                       # AST data structures
│   ├── lexer/
│   │   ├── LineClassifier.kt                # Semantic tokenization
│   │   └── TypedLine.kt
│   ├── grammar/
│   │   ├── MenuGrammarParser.kt             # State machine parser
│   │   └── WindowedContextResolver.kt
│   ├── dom/
│   │   ├── DomSnapshot.kt, DomElement.kt, DomElementsSnapshot.kt
│   │   ├── MenuBlockClassifier.kt, DomBlockScorer.kt
│   │   ├── BlockMerger.kt, BlockTextExtractor.kt
│   │   ├── VisualBlockDetector.kt, VisualGroup.kt
│   │   ├── DomSnapshotParser.kt, DomLlmElementFilter.kt
│   │   ├── DomCandidateExtractor.kt, DomSemanticCompressor.kt
│   │   └── Many more: RenderAwareScorer, RepeatedPatternMiner, SemanticBlock, etc.
│   ├── semantic/
│   │   ├── SemanticIntelligenceLayer.kt     # 4-stage validation
│   │   ├── SemanticAnomalyDetector.kt       # FoodOntology reference
│   │   ├── SemanticPromptBuilder.kt
│   │   ├── ConfidenceArbiter.kt, ConfidenceVector.kt
│   │   ├── SemanticContract.kt, SemanticCache.kt
│   ├── confidence/
│   │   ├── ConfidenceEngine.kt              # Scoring
│   │   ├── ConfidenceVector.kt
│   ├── validation/
│   │   └── StructuralValidator.kt           # AST validation
│   ├── repair/
│   │   ├── RepairEngine.kt, RepairHeuristics.kt
│   ├── pipeline/
│   │   └── MultiPassReconciler.kt           # 4-pass reconciliation
│   ├── pdf/
│   │   ├── PdfTextLayerExtractor.kt         # Native text extraction
│   │   ├── PdfQualityChecker.kt             # Quality scoring
│   │   ├── PdfTextCleaner.kt, PdfPageRenderer.kt
│   ├── source/
│   │   ├── OcrPostProcessor.kt              # OCR artifact correction
│   │   └── HtmlMenuExtractor.kt
│   ├── regex/
│   │   ├── RegexRegistry.kt, RuleRegistry.kt
│   ├── locale/
│   │   └── LocalePackRegistry.kt            # Language detection
│   ├── ontology/
│   │   └── FoodOntology.kt                  # Dish validation reference
│   ├── budget/
│   │   └── AiBudgetManager.kt               # Token budgeting
│   ├── domain/
│   │   └── DomainTrustProfile.kt
│   ├── mapping/
│   │   └── AstToDtoMapper.kt                # AST → MenuCategory mapper
│   ├── observability/
│   │   ├── ParseLogger.kt, MetricsCollector.kt
│   │   └── ParseTraceEngine.kt
```

## Key Constants & Thresholds

### Tesseract
- MAX_PAGES = 15 (PDF)
- PAGE_WIDTH_PX = 1200
- MIN_OCR_WIDTH = 1400 (image)
- Column detection sensitivity = 0.3f
- Only tessdata: eng (Latin), spa (Italian)

### WebView
- PAGE_SETTLE_MS = 1500 (wait for rendering)
- BACKUP_EXTRACT_MS = 25000 (slow timeout)
- TIMEOUT_MS = 50000 (hard limit)
- Invisible size: 1×1 px
- User-Agent: Chrome 120 Mobile

### LLM
- Gemini: maxTokens 4096 (main), 2048 (enrichment)
- Groq: maxTokens 4096 (main), 2048 (enrichment)
- Temperature: 0.1 (main extraction), 0.2 (structured DOM), 0.2 (on-device inference)
- Chunk size: 2500 chars
- Text size threshold: 300_000 chars (split if > this)
- Enrichment batch: 15 dishes per call

### Parser
- Confidence thresholds:
  - STRICT: 0.80
  - BALANCED: 0.45
  - AGGRESSIVE: 0.15
- Semantic validation threshold: 0.30
- Locale recursion depth (Next.js): 8
- Max repair iterations: 3

### PDF
- Text layer quality thresholds:
  - STRICT: >= 0.70
  - BALANCED: >= 0.50
  - AGGRESSIVE: always text first

## Important Constraints

1. **Tesseract**: "ita" language = SIGSEGV. Use "eng" or "spa" only
2. **WebView**: evaluateJavascript requires window.decorView attachment
3. **Groq**: 6000 TPM free tier → bundle prompt + output ≤ 5000 tokens
4. **Gemini**: Daily quota per key → auto-cascade to Groq
5. **Price filter**: name.isNotEmpty() only (many sites don't expose prices)
6. **Firebase schema**: Italian field names (nome, ingredienti, cucina, etc.)
7. **Firebase mode**: append vs replace (atomic deletion on replace mode)

## Performance Characteristics

- **Preprocessing**: < 100ms
- **Lexer + Grammar**: 50-300ms
- **PDF text extraction**: 1-5s
- **PDF OCR (per page)**: 1-3s (15 page max)
- **WebView rendering**: 5-10s
- **LLM extraction**: 2-10s (size dependent)
- **Enrichment**: 1-5s (per batch of 15)
- **Total QR flow**: 20-40s typical

## Observability

### Logging Tags
- `LLMApiClient` — API calls, fallback cascades
- `MenuParserPipeline` — parsing stages, confidence
- `WebViewExtractor` — JS injection, DOM snapshots
- `PdfMenuExtractor` — text vs OCR routing
- `EnhancedTesseract` — column detection, OCR progress
- `SemanticIntelligenceLayer` — LLM validation stages
- `MultiPassReconciler` — reconciliation passes

### Tracing
- ParseTraceEngine: stage timing, event logging
- MetricsCollector: success/failure rates
- ParseLogger: detailed parsing events

## Browser Compatibility Notes

- URL schemes: http/https (no file://)
- SSL errors: accepted (many restaurant sites have cert issues)
- HTTP redirects: manual follow (OkHttp default)
- Pre-fetch priority: HTTP headers over WebView (faster, less overhead)
- JS version: ES5+ support (most restaurant sites)
