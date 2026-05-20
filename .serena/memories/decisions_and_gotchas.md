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

## Extraction pipeline (May 2026)

### Pass 1 — Raw extraction
1. JS_EXTRACT_TEXT detects h2/h3/h4 → "=== CATEGORIA: X ===" markers
2. LLMApiClient.processMenuText():
   - If markers → processBySections() (per-section with retry)
   - If large text → processInChunks()
   - Else → single call
3. MenuParser.parseMenuText() → List<MenuCategory>

### Pass 2 — Enrichment (deterministic)
4. LLMApiClient.enrichDishes():
   - Finds dishes with empty description OR calories==0 OR empty country
   - Batches 15 at a time (maxTokens=2048, safe for TPM)
   - LLM infers from dish name: ingredienti, calorie, paese, regione
   - Merges back — does NOT overwrite fields already filled in pass 1
   - Goal: 100% of dishes always have ingredients, calories, country

## Language rule
- LLM prompts: "mantieni la lingua originale. NON tradurre."
- Enrichment prompt is in English (LLM handles both)
