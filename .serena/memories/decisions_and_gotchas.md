# Key Decisions & Gotchas

## LLM / Groq API
- Model: `llama-3.1-8b-instant` — DO NOT change to 70b (breaks JSON output format)
- Max tokens: 4096 (free tier TPM=6000; prompt ~1000t + 4096 output = ~5096 safe)
- Chunk size: 2500 chars (was 4000, reduced to avoid 413 on large JSON lines)
- On 429: auto-retry with delay parsed from error message (up to 3 retries)
- Prompt: asks LLM to infer ingredients from dish name when not in text
- Price filter: `name.isNotEmpty()` only (NOT `price > 0.0` — websites rarely expose prices)

## WebView
- MUST be attached to `activity.window.decorView` as 1×1 INVISIBLE view
- `evaluateJavascript` requires window attachment — headless WebView will NEVER invoke callback
- Use Activity context (not applicationContext)
- Remove from decorView in `finally` block
- JS_EXTRACT_TEXT: first tries `window.__NEXT_DATA__` (Next.js/JustEat), then visible text
- JS_NETWORK_INTERCEPTOR: patches fetch+XHR to capture API JSON via MenuCapture.onApiJson

## PDF
- Detection: URL contains .pdf OR HEAD request returns content-type with "pdf"
- Max 15 pages, PAGE_WIDTH_PX=1200
- Use `eng` tessdata (NOT `ita` — not in assets, causes SIGSEGV crash)
- PdfRenderer requires white Canvas background before rendering (alpha channel issue)

## MenuParser
- Reads keys: "Categoria", "Piatti", "Descrizione/Ingredienti/Extra", "Allergeni", "Prezzo", "Calorie", "Paese", "Regione"
- Filter: `if (name.isNotEmpty())` — keep dishes without price
- Multi-part: splits on `=== PARTE N ===` markers

## MenuContentPreprocessor
- Handles `DATI JSON PAGINA (Next.js)` prefix → extracts from __NEXT_DATA__
- Known paths: props.pageProps.menu, props.pageProps.initialData.menuData, etc.
- If extraction fails → returns "" → caller falls back to visibleText
- TheFork/LaFourchette: different structure, often falls back to visible text

## Firebase
- Menu items stored at `/users/{userId}/dishes/{autoId}`
- Calories stored as `calorie` key (not `calories`)
- GROQ_API_KEY is hardcoded in QrMenuImportScreen.kt (not production-safe)

## Tesseract
- `EnhancedTesseractManager` — init in constructor, call recycle() when done
- processImage(bitmap, lang="eng", useColumnSplit=true)
- Always use `eng` for menu OCR (Italian text works with Latin charset)
