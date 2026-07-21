# Ingredient — Project Overview

Android app (Kotlin + Jetpack Compose) for restaurant owners to manage menus.

## Core features
- QR code scanning → import restaurant menus automatically
- Manual menu management (Gestisci screen)
- Image recognition of physical menus (Tesseract OCR)
- Firebase Realtime Database storage

## QR Import Pipeline (May 2026 — Dual Provider)

### Step 1: Content Detection
1. Scan QR → URL or plain text
2. Detect type: plain text / PDF / HTTP

### Step 2: Content Extraction
- **Plain text**: direct to LLM
- **PDF URL**: PdfMenuExtractor
  - Text layer first (PdfTextLayerExtractor)
  - Fallback: OCR (Tesseract) up to 15 pages at 1200px
  - Quality routing (STRICT/BALANCED/AGGRESSIVE)
- **HTTP URL**: WebViewMenuExtractor (priority-based)
  - Priority 0: HTTP pre-fetch (inline JS variables)
  - Priority 1: WebView JS injection → network interception, accordion expand, scroll
  - Output: visibleText, capturedApiJson, domSnapshot
  - Three routes: __INLINE_JS_MENU__, __STRUCTURED_DOM__, plain text

### Step 3: Preprocessing
- MenuContentPreprocessor: extract menu from Next.js __NEXT_DATA__ or API JSON
- Fallback: raw visible text

### Step 4: Parsing (sophisticated deterministic pipeline)
- 7-stage MenuParserPipeline:
  1. Preprocess (MenuContentPreprocessor)
  2. OCR correction (OcrPostProcessor)
  3. Lexer → LineClassifier (semantic tokenization)
  4. Grammar parser → MenuGrammarParser (state machine)
  5. Structural validation & repair
  6. Confidence scoring (ConfidenceEngine)
  7. Multi-pass reconciliation + SemanticIntelligenceLayer
- Output: MenuAST with 80+ categories, prices, descriptions

### Step 5: LLM Extraction (via GroqKeyManager routing)
- **Structured DOM**: GroqKeyManager.semanticClient().processStructuredDom()
  - Compress raw [{t,d,v}] → candidates
  - Gemini 2.5 Flash (1M context, handles full-menu extraction)
- **Plain text**: GroqKeyManager.semanticClient().processMenuText()
  - Section-by-section if === CATEGORIA: markers detected
  - Chunked if > 300k chars
  - Auto-cascade on quota/rate-limit

### Step 6: Enrichment (2-pass)
- Pass 1: Raw extraction via LLM
- Pass 2: LLMApiClient.enrichDishes()
  - Batches 15 dishes at a time (Groq llama-3.1-8b)
  - Infers: ingredients, calories, country, region
  - Merges back — never overwrites existing fields
  - Goal: 100% non-empty descriptions + calories

### Step 7: Semantic Validation (optional, confidence >= 0.30)
- SemanticIntelligenceLayer (4 stages):
  1. OCR repair (fix garbled text)
  2. Plausibility check (validate food names)
  3. Category validation (detect anomalies)
  4. Final enrichment (fill blanks)

### Step 8: Firebase Upload
- FirebaseMenuUploader.uploadMenu() or uploadMenuWithMetadata()
- Append vs replace modes
- Field names: Italian (nome, ingredienti, cucina, regione, paese, prezzo, offerta, prezzoOfferta)

## LLM (Dual Provider — May 2026)
**Router: GroqKeyManager.kt**
- semanticClient() → Gemini 2.5 Flash (1M context, heavy AST, SemanticIntelligenceLayer)
- enrichClient() → Groq llama-3.1-8b-instant (fast small-batch enrichment)
- domFilterClient() → Groq (DOM classification, cheapest)
- clientPool() → [Gemini, Groq KEY_1, Groq KEY_2] for parallel distribution

**Groq constraints**
- TPM: 6000 tokens/min (free tier)
- maxTokens: 4096 (main), 2048 (enrichment)
- Chunk size: 2500 chars
- Auto-retry on 429 with parsed wait time (max 3 retries)
- Auto-fallback on 413 (too large) to chunked processing

**Gemini constraints**
- Daily quota per key → auto-cascade to Groq clients
- 1M token context window
- Model: gemini-2.5-flash

**Models**
- llama-3.1-8b-instant (DO NOT change to 70b — breaks JSON output)
- gemini-2.5-flash (heavy analysis)

## Key constraints
- Groq free tier: max 6000 TPM → keep prompt + output ≤ 5000 tokens
- Tesseract: only `eng` and `spa` tessdata in assets/models/ — `ita` does NOT exist (causes SIGSEGV)
- WebView must be attached to activity.window.decorView (1×1 INVISIBLE) for evaluateJavascript to work
- Price filter: name.isNotEmpty() only — no price > 0.0 requirement (websites rarely expose prices)
