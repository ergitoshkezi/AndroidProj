# Decisions & Gotchas

## Critical bugs / known issues

### Tesseract language=ita → SIGSEGV
- `ita` tessdata does NOT exist in assets
- Calling TessBaseAPI.init() with language="ita" → getUTF8Text() → fatal signal 11
- ALWAYS use "eng" or "spa" only
- "spa" tessdata is actually Italian-trained (named spa by mistake — DO NOT rename)

### OCR language detection (PdfMenuExtractor)
- `detectOcrLang(url)`: checks URL for Italian signals (.it/, ristorante, pizzeria, etc.)
- Italian → "spa" tessdata; English/other → "eng"

### WebView evaluateJavascript
- Only works when WebView is attached to a visible window
- Must addView to activity.window.decorView as 1×1 INVISIBLE before loading URL
- Remove after done (removeView + destroy)

### Groq 413 (Request Too Large)
- Free tier TPM = 6000. Prompt tokens + maxTokens must stay ≤ 6000
- maxTokens = 4096 (main), 2048 (enrichment)
- 8192 maxTokens causes 413
- Chunk size: 2500 chars; single long lines hard-split

### Groq 429 (Rate Limit)
- Auto-retry: parse "try again in X.Xs" → sleep + 1s, max 3 retries

### Price filter
- name.isNotEmpty() only — never price > 0.0

### llama-3.1-8b-instant
- DO NOT change to 70b — breaks JSON output format
- Only model that reliably outputs clean JSON arrays

## 7-Stage Deterministic Parser (May 2026)

The new MenuParserPipeline replaces simple LLM parsing with sophisticated local analysis:

### Stage 1: Preprocessing
- MenuContentPreprocessor extracts clean menu from Next.js/__NEXT_DATA__, API JSON
- Reduces noise, normalizes whitespace

### Stage 2: OCR Correction
- OcrPostProcessor detects and repairs common OCR artifacts
- Quality assessment: char confidence, reasonable word distributions

### Stage 3: Lexer (semantic tokenization)
- LineClassifier assigns semantic type to each line
- Types: Noise, Divider, StrongHeader, WeakHeader, DishCandidate, DishWithPrice, StandalonePrice, Allergen, Description
- Locale-aware (Italian, English, Spanish, etc.)

### Stage 4: Grammar Parser (state machine)
- MenuGrammarParser builds AST with state transitions
- Tracks price association: STANDALONE, SAME_LINE, NEXT_LINE
- Tracks description binding: ABOVE, BELOW, SAME_LINE
- Handles multi-line items and ambiguous structures

### Stage 5: Structural Validation & Repair
- StructuralValidator checks AST against parsing rules
- RepairEngine fixes issues: malformed categories, duplicate headers, missing descriptions
- Logs repair events for observability

### Stage 6: Confidence Scoring
- ConfidenceEngine scores each item: overall, headerConfidence, itemConfidence, descriptionConfidence
- Three parsing modes:
  - STRICT (confidence floor 0.80): high-confidence only
  - BALANCED (default, 0.45): mixed heuristic + LLM
  - AGGRESSIVE (0.15): capture everything, repair later

### Stage 7: Multi-Pass Reconciliation + Semantic Intelligence
- MultiPassReconciler (4 passes):
  - Pass 1: visual promotion (from DOM snapshots)
  - Pass 2: ontology correction (FoodOntology reference)
  - Pass 3: confidence propagation
  - Pass 4: final structure validation
- SemanticIntelligenceLayer (optional, runs if confidence >= 0.30):
  - OCR repair (Gemini 2.5 Flash)
  - Plausibility check (LLM validation)
  - Category anomaly detection (dish in wrong category)
  - Final enrichment (fill gaps)

### Final: LLM-Powered Enrichment (2-pass)
- **Pass 1 (raw extraction)**: LLM extracts dishes as-is from text
  - processBySections() if markers detected
  - processInChunks() if > 300k chars
  - processStructuredDom() if DOM JSON available
- **Pass 2 (enrichment)**: enrichDishes()
  - Batches 15 dishes at a time (maxTokens=2048)
  - Infers: ingredients, calories, country, region
  - Never overwrites existing fields
  - Goal: 100% non-empty critical fields

## Language rule
- LLM prompts: "mantieni la lingua originale. NON tradurre."
- Enrichment prompt is in English (LLM handles both)
