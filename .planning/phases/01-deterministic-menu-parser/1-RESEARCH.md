# Phase 1: Deterministic Menu Parser v2 — Research

**Researched:** 2026-05-20
**Domain:** Android/Kotlin — deterministic text-pipeline, menu parsing, NLP heuristics
**Confidence:** HIGH (all findings are from direct source-code inspection)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01/02/03:** Wrapper-progressive migration. New pipeline runs first; if `avgConfidence < 0.5` or 0 confirmed dishes, automatically fall back to legacy `processUrl()`. Status message distinguishes which pipeline ran.
- **D-04/05:** New `MenuAST` internal representation. Confidence scores and `DishFlag` sets exist only inside the pipeline and are discarded before mapping to `List<MenuCategory>`. No changes to `MenuItem`, `MenuCategory`, `FirebaseMenuUploader`, or UI code.
- **D-06:** `MenuContentPreprocessor.kt` is **Layer 0** for HTML/Next.js/API JSON extraction. Extend it.
- **D-07:** `LocalAiParser.kt` role to be determined by researcher (see §1 below).
- **D-08:** `PdfMenuExtractor.kt` and `WebViewMenuExtractor.kt` remain as input adapters.
- **D-09:** `temperature = 0.0` for all enrichment calls.
- **D-10:** Schema-first enrichment with `additionalProperties: false`.
- **D-11:** Max 2 retries per dish. On failure, keep dish without enrichment.
- **D-12:** Enrich only dishes that survive the confidence gate.
- **D-13:** `minDishConfidence = 0.35f`
- **D-14:** `minCategoryConfidence = 0.40f`
- **D-15:** Pipeline fallback triggered when `avgConfidence < 0.5` OR confirmed dish count is 0.
- **D-16:** Fixture-first (TDD). 10+ real menu samples before writing parser code.
- **D-17:** Regression test — same input → byte-identical output on 3 consecutive runs.
- **D-18:** Contract test — LLM enricher must not add dish names not in input AST.

### the agent's Discretion
- Exact regex patterns for price format variants
- Builder pattern implementation for `ASTCategory` / `ASTDish`
- Internal naming of intermediate pipeline stages
- Exact look-ahead window size for price association
- Whether `LocalAiParser.kt` is integrated into state machine or used as separate strategy

### Deferred Ideas (OUT OF SCOPE)
- OCR fallback (ML Kit text recognition)
- Multilingual normalization improvements (FR/ES deep support)
- Confidence score badges in preview UI
- Scheduled/background menu re-import
</user_constraints>

---

## RESEARCH COMPLETE

---

### 1. LocalAiParser Assessment

**What it does:** `LocalAiParser` uses **MediaPipe `LlmInference`** (on-device LLM) to run a `.gguf` model file from `assets/models/menu_parser.bin`. It accepts raw menu text and returns a JSON array of objects with only two keys: `dish` and `ingredients`. It does NOT extract categories, prices, descriptions, allergens, or any other structure.

**Is it deterministic?** NO.
- `temperature = 0.2f`, `topK = 40` — both produce non-deterministic output across runs.
- Model load time is high (asset copy + LlmInference init), causing startup latency.
- Output schema is too narrow: `[{"dish": "...", "ingredients": [...]}]` — no categories, no prices.

**Recommendation: SCRAP LocalAiParser from the new pipeline entirely.**

Rationale:
1. Its output lacks categories and prices — the core problem the new pipeline solves.
2. Non-deterministic by design (temperature > 0).
3. On-device model loading adds large memory/latency overhead not needed now that Groq API is the enrichment channel.
4. For the "LLM structured extractor" fallback strategy in `tryParseStrategies()` (strategy 3 per architecture note), use `LLMApiClient` (Groq API) — it already exists and its output maps cleanly to `MenuAST` via a schema-first prompt.

**Verdict:** Do not wire `LocalAiParser` into any layer of the new pipeline. Leave it in the codebase as-is (do not delete) — the planner should note that it is bypassed.

---

### 2. MenuContentPreprocessor Gap Analysis

**What currently works:**
- Detects `"DATI JSON PAGINA"` prefix → delegates to `extractFromNextData()`
- Detects `"DATI API JSON DEL SITO:"` prefix → delegates to `extractFromApiJson()`
- `extractFromNextData()` tries 7 known Next.js JSON paths (JustEat IT, etc.) then recursive search
- `flattenMenuNode()` handles deeply nested category/item JSON structures
- Falls back to raw visible text if no structure found

**Gaps for Layer 0 (HTML sites not served by Next.js):**

| Gap | Impact | Fix |
|-----|--------|-----|
| **No Jsoup HTML parsing** | Plain HTML restaurant sites (80% of the long tail) get raw string with nav/footer noise | Add Jsoup; extract `<article>`, `<main>`, `<section class="menu*">`, strip `<nav>`, `<footer>`, `<aside>`, `<header>` |
| **No JSON-LD schema.org/Menu extraction** | Sites embedding `<script type="application/ld+json">` with `@type: Menu` are not specially handled | Parse `<script type="application/ld+json">` tags, detect `@type: "Menu"` or `@type: "FoodEstablishment"`, extract `hasMenuItem` |
| **No structural metadata on output** | Current output is flat text — tokenizer has no hint about heading vs. paragraph | Produce annotated output: `## Category` markers (already done for Next.js) must also be produced for Jsoup path |
| **Visible text from WebView has no HTML** | `WebExtractResult.visibleText` is already de-tagged JavaScript-extracted text; Layer 0 cannot run Jsoup on it | Jsoup path is only applicable when raw HTML is available (future direct HTTP GET) — for now, the WebView visible text path is text-only |

**Extension strategy:**
- Add `fun extractJsonLd(html: String): String` — parse `<script type="application/ld+json">` blocks
- Add `fun extractSemanticHtml(html: String): String` using Jsoup — strip noise, emit `## CategoryName` headers before item blocks
- Keep the existing `preprocess()` dispatch logic; add new branches for `"HTML:"` or `"RAW_HTML:"` prefixes if WebViewMenuExtractor is extended to pass raw HTML

---

### 3. LLMApiClient enrichDishes() Assessment

**Exists:** YES — `enrichDishes(categories: List<MenuCategory>, onProgress: ((String) -> Unit)?): List<MenuCategory>` at line 528.

**Current behavior:**
- Collects dishes where `description.isBlank() || calories == 0 || country.isBlank()`
- Batches in groups of 15
- Sends compact JSON: `[{"id": N, "nome": "...", "categoria": "..."}]`
- Gets back: `id`, `ingredienti`, `calorie`, `paese`, `regione`
- Merges back into `MenuItem` copies

**Deficiencies vs. architecture requirements:**

| Requirement | Current State | Gap |
|-------------|--------------|-----|
| `temperature = 0.0` (D-09) | Uses `temperature = 0.1` | Must change to `0.0` |
| Schema-first with `additionalProperties: false` (D-10) | Natural language prompt, no JSON Schema | Need strict JSON schema in system prompt |
| Per-dish retry max 2 (D-11) | Skips entire batch on failure, no per-dish retry | Need retry loop per dish |
| Keep dish on failure (D-11) | Already does this (skips failed batches, dishes remain) | ✅ |
| Enrich only confidence-gate survivors (D-12) | Enriches ALL dishes missing fields from `List<MenuCategory>` | Enricher must accept `List<ASTDish>` from post-gate AST |
| Allergens enrichment | NOT enriched — only description/calories/country/region | Add `allergeni` to enrichment prompt |
| Works on `ASTDish` not `MenuItem` | Works on `MenuItem` | New `LLMEnricher` wrapper class needed |

**Recommended approach:** Create a new `LLMEnricher.kt` class that wraps `LLMApiClient.invokeAPI()` directly (or calls a refactored private method). The existing `enrichDishes()` method stays for backward-compatibility with the legacy fallback path. The new class accepts `List<ASTDish>` and returns `List<DishEnrichment?>`.

---

### 4. PDF Layout Preservation

**Current approach:** `PdfMenuExtractor` uses Android's built-in `PdfRenderer` (renders page → `Bitmap`) + Tesseract OCR. It produces plain text only. **No x/y/fontSize/isBold metadata is extracted.**

**Why layout matters for the new pipeline:** Multi-column PDFs and PDFs where prices appear in a right-aligned column need coordinate data to correctly associate dish names with their prices. Without layout, the state machine treats right-column prices as separate lines, degrading price-association accuracy.

**Dependency status:**
- `com.tom-roush:pdfbox-android` — **NOT in project** [VERIFIED: build.gradle.kts inspection]
- Current PDF path: PdfRenderer → Bitmap → Tesseract → plain text

**Options:**

| Option | What it gives | Trade-off |
|--------|--------------|-----------|
| Add `com.tom-roush:pdfbox-android` | Text extraction with `TextPosition` (x, y, fontSize, fontName) | +2–5 MB APK; requires AAR compat check |
| Keep current Tesseract-OCR path | Plain text, no coordinates | Simpler, but multi-column menus will have price-association failures |
| Hybrid: try pdfbox first, fall back to OCR | Best accuracy with graceful fallback | More code, but architecturally sound |

**Recommendation:** Add `com.tom-roush:pdfbox-android` and implement a layout-aware extraction path. The architecture note explicitly lists this library. Use it for text-layer PDFs (most restaurant menus). Keep the Tesseract path as a fallback for image-only PDFs (scanned menus). The new `PdfLayoutExtractor` can be a separate class — `PdfMenuExtractor` stays unchanged per D-08.

**Caveat:** pdfbox-android version to verify before adding. Last known stable: `2.0.27.0` [ASSUMED — verify on Maven Central before adding to build.gradle.kts].

---

### 5. WebViewMenuExtractor Return Type

**Returns:** `WebExtractResult(val visibleText: String, val capturedApiJson: List<String>)`

**`bestContent()` method** on `WebExtractResult` formats it as:
- If `visibleText` starts with `__NEXT_DATA__:` → returns `"DATI JSON PAGINA (Next.js/Nuxt SSR):\n{json}"`
- If `capturedApiJson` has menu-looking JSON → returns `"DATI API JSON DEL SITO:\n{json}\n\n---\nTESTO VISIBILE PAGINA:\n{visibleText}"`
- Otherwise → returns `visibleText.take(30000)`

**How it currently integrates with Layer 0:**
```kotlin
// QrMenuImportScreen.kt line 70–72
content = extracted.bestContent().let { raw ->
    val preprocessed = MenuContentPreprocessor.preprocess(raw)
    if (preprocessed.isNotBlank()) preprocessed else extracted.visibleText.take(20000)
}
```

**Layer 0 normalizer integration strategy:**
- The current flow already passes `bestContent()` through `MenuContentPreprocessor.preprocess()` — this is the correct hook point.
- The new Layer 0 should accept the `String` output of this existing chain (or the `WebExtractResult` directly if more intelligence is needed).
- Minimum change: `MenuContentPreprocessor.preprocess()` gains new branches for JSON-LD detection and Jsoup extraction — `QrMenuImportScreen.kt` calls it exactly as today.
- The `WebExtractResult` type and `WebViewMenuExtractor` code remain unchanged (D-08).

---

### 6. Testing Infrastructure

**Framework:** JUnit 4 [VERIFIED: `testImplementation(libs.junit)` in build.gradle.kts]

**Existing tests:**
- `src/test/.../ExampleUnitTest.kt` — single stub test (`assertEquals(4, 2+2)`)
- `src/androidTest/.../ExampleInstrumentedTest.kt` — stub instrumented test

**What's absent:**
- No fixture directory
- No parser tests of any kind
- No test for any existing class (`MenuParser`, `MenuContentPreprocessor`, `LLMApiClient`, etc.)

**Implications for D-16 (fixture-first TDD):**

Wave 0 must create:
- `src/test/resources/fixtures/menus/` — raw HTML, PDF text extracts, raw QR strings (10+ files)
- `src/test/.../MenuTokenizerTest.kt`
- `src/test/.../MenuStateMachineParserTest.kt`
- `src/test/.../MenuASTValidatorTest.kt`
- `src/test/.../MenuContentPreprocessorTest.kt`
- `src/test/.../ConfidenceGateTest.kt`
- `src/test/.../PipelineRegressionTest.kt` (for D-17 determinism check)

**Android context requirement:** Most new classes (`MenuTokenizer`, `MenuStateMachineParser`, `MenuAST`, `MenuASTValidator`, `ConfidenceGate`) must be **pure Kotlin with zero Android dependencies** so they can run as local JVM unit tests without an emulator. `LLMEnricher` (needs network) should have an interface + mock for testing.

---

### 7. State Machine Patterns

**Existing patterns in codebase:**
- `when` expressions are used extensively (MenuParser, MenuContentPreprocessor)
- **No sealed classes** found in the entire codebase [VERIFIED: grep of all `.kt` files]
- **No enum classes** used for states in any existing file
- Coroutine state is managed via `CompletableDeferred` (WebViewMenuExtractor) and `var` state in composables

**What to introduce (net new pattern):**

```kotlin
// Recommended: sealed class for states (idiomatic Kotlin, exhaustive when)
sealed class ParseState {
    object SeekingCategory : ParseState()
    data class InCategory(val current: ASTCategory.Builder) : ParseState()
    data class InDish(val category: ASTCategory.Builder, val current: ASTDish.Builder) : ParseState()
    data class AwaitingPrice(val category: ASTCategory.Builder, val dish: ASTDish.Builder, val linesWaited: Int) : ParseState()
    data class InDescription(val category: ASTCategory.Builder, val dish: ASTDish.Builder) : ParseState()
}
```

This is new to the codebase but idiomatic Kotlin. All team members familiar with Kotlin will recognize the pattern. Use `when(state)` for the transition function — ensures exhaustive handling.

**Builder pattern** for `ASTCategory` and `ASTDish` is also new but straightforward. Alternative: use `copy()` on data classes with a mutable accumulator `var` — simpler for this codebase's current style.

---

### 8. PipelineOrchestrator Integration Strategy

**Exact replacement target in `QrMenuImportScreen.kt`:**

The local `fun processUrl(url: String)` is a nested function inside `@Composable QrMenuImportScreen()`. The relevant block to replace is:

```kotlin
// LINES 82–103 — REPLACE THESE:
val estChunks = (content.length / 4000) + 1
statusMessage = if (estChunks > 1) "Analisi AI (0/$estChunks sezioni)…" else "Analisi AI in corso…"
val response = LLMApiClient(GROQ_API_KEY).processMenuText(
    menuText = content,
    onProgress = { cur, tot -> statusMessage = "Analisi AI: sezione $cur/$tot…" }
)
val menuCategories = MenuParser().parseMenuText(response)
if (menuCategories.isNotEmpty()) {
    // ... enrichDishes() call
    val enriched = LLMApiClient(GROQ_API_KEY).enrichDishes(...)
    parsedMenu = enriched
}
// WITH THIS:
statusMessage = "🔍 Analisi deterministica in corso…"
val result = PipelineOrchestrator(GROQ_API_KEY).orchestrate(
    content = content,
    onProgress = { msg -> statusMessage = msg }
)
parsedMenu = result.categories
statusMessage = result.statusMessage  // includes "[new pipeline]" or "[legacy fallback]" label
```

**Surface area of change in QrMenuImportScreen.kt:** 3 lines changed, ~15 lines removed. Content extraction block (lines 41–79) is **untouched**.

**PipelineOrchestrator signature:**
```kotlin
class PipelineOrchestrator(private val apiKey: String) {
    suspend fun orchestrate(
        content: String,
        onProgress: (String) -> Unit
    ): OrchestratorResult
}

data class OrchestratorResult(
    val categories: List<MenuCategory>,
    val statusMessage: String,
    val usedLegacyFallback: Boolean
)
```

**Fallback wiring:** Inside `PipelineOrchestrator.orchestrate()`:
1. Run new 6-layer pipeline → get `MenuAST`
2. Compute `avgConfidence`
3. If `avgConfidence < 0.5` OR confirmed dishes == 0 → run legacy pipeline (`LLMApiClient.processMenuText()` + `MenuParser().parseMenuText()` + `LLMApiClient.enrichDishes()`)
4. Return whichever result has more dishes (or legacy if tie)

---

### 9. Dependency Gaps

**Currently in build.gradle.kts:**

| Library | Status | Notes |
|---------|--------|-------|
| `kotlinx-coroutines-android:1.7.3` | ✅ Present | |
| `org.json` (Android built-in) | ✅ Present | Used throughout; no import needed |
| `com.google.code.gson:gson:2.10.1` | ✅ Present | Not used in parser code |
| `com.squareup.okhttp3:okhttp:4.12.0` | ✅ Present | Not used by parsers currently |
| `com.google.mlkit:text-recognition:16.0.0` | ✅ Present | Deferred (OCR) |
| **`org.jsoup:jsoup`** | ❌ MISSING | Required for Layer 0 HTML extraction |
| **`com.tom-roush:pdfbox-android`** | ❌ MISSING | Required for PDF layout extraction |
| `kotlinx-serialization-json` | ❌ MISSING | Optional — `org.json` can substitute |

**To add to `build.gradle.kts`:**
```kotlin
implementation("org.jsoup:jsoup:1.17.2")
implementation("com.tom-roush:pdfbox-android:2.0.27.0")  // verify latest on Maven Central
```

**Note on `kotlinx-serialization`:** The architecture note recommends it, but the entire codebase uses `org.json.JSONObject/JSONArray`. Adding `kotlinx-serialization` would introduce a new pattern inconsistency. **Recommendation: stay with `org.json` for this phase.** All MenuAST serialization (for debugging/logging) can use `org.json`. The planner should make the final call.

**`pdfbox-android` version:** [ASSUMED — verify `com.tom-roush:pdfbox-android` latest stable on Maven Central before adding]. The architecture note cites `com.tom-roush:pdfbox-android` without a version.

---

### 10. Known Categories Dataset

Recommended initial `KNOWN_CATEGORIES` set for the tokenizer. Sorted by signal strength (higher = more unambiguous):

**Italian (primary target language):**
```
"ANTIPASTI", "ANTIPASTO",
"PRIMI", "PRIMI PIATTI", "PRIMO",
"SECONDI", "SECONDI PIATTI", "SECONDO",
"CONTORNI",
"DOLCI", "DESSERT",
"PIZZE", "PIZZA",
"PINSA", "PINSERIA",
"FOCACCE", "FOCACCIA",
"CALZONI",
"FRITTI", "FRITTURA",
"ZUPPE", "ZUPPA", "MINESTRE",
"INSALATE", "INSALATA",
"PANINI", "PANINO", "TRAMEZZINI",
"CARNE", "CARNI",
"PESCE", "PESCI", "FRUTTI DI MARE",
"PASTA", "PASTE",
"RISOTTI", "RISOTTO",
"VERDURE", "GRIGLIATA",
"BEVANDE", "BIBITE", "BEVANDA",
"VINI", "VINO", "BIRRE", "BIRRA",
"APERITIVI", "COCKTAIL", "DIGESTIVI",
"CAFFETTERIA", "CAFFÈ",
"BAMBINI", "MENU BAMBINI",
"MENU DEL GIORNO", "MENU FISSO",
"SPECIALITÀ", "SPECIALITA", "SPECIALS",
"STAGIONALE",
"VEGETARIANO", "VEGANO",
"SUSHI", "ROLLS"   // common in Italian fusion
```

**English variants (common on Italian menus for international tourists):**
```
"STARTERS", "APPETIZERS",
"FIRST COURSE", "FIRST COURSES",
"MAIN COURSE", "MAIN COURSES", "MAINS",
"SIDE DISHES", "SIDES",
"DESSERTS",
"DRINKS", "BEVERAGES",
"WINES", "BEERS", "SPIRITS",
"SOUPS", "SALADS",
"MEAT", "FISH", "SEAFOOD",
"PASTA", "PIZZA",
"KIDS MENU"
```

**French (occasional on upscale Italian menus):**
```
"ENTRÉES", "ENTREES",
"PLATS PRINCIPAUX", "PLATS",
"DESSERTS",
"BOISSONS",
"FROMAGES"
```

**Spanish (rarely seen but worth including):**
```
"ENTRANTES",
"PLATOS PRINCIPALES",
"POSTRES",
"BEBIDAS"
```

**Normalized matching strategy:** Normalize to uppercase, strip diacritics (`ANTIPASTÌ` → `ANTIPASTI`), and match exact token OR token-at-start-of-line. Short matches (< 3 chars after normalization) should NOT match even if they appear in the set.

---

### Implementation Recommendations

**1. Scrap LocalAiParser from the pipeline; use Groq for all LLM work.**
LocalAiParser is a non-deterministic on-device LLM that can only produce `dish + ingredients` — it cannot serve any role in the new 6-layer deterministic pipeline. The "LLM structured extractor" in `tryParseStrategies()` strategy 3 should use `LLMApiClient` (Groq) with a schema-first prompt, not LocalAiParser.

**2. Create `LLMEnricher.kt` as a thin wrapper — do not modify `enrichDishes()` directly.**
`enrichDishes()` is used in the legacy fallback path. Create a separate `LLMEnricher` class that calls `invokeAPI()` (or its own HTTP call) at `temperature=0.0`, enforces the strict JSON schema from the architecture note, adds `allergens` enrichment, implements per-dish retry (max 2), and accepts `List<ASTDish>` → returns `List<DishEnrichment?>`.

**3. Make all Layer 0–4 classes pure Kotlin (zero Android SDK imports).**
`MenuTokenizer`, `MenuStateMachineParser`, `MenuASTValidator`, `ConfidenceGate`, and `MenuAST` data classes must have no `android.*` imports. This is the only way to run them as fast local JVM unit tests (D-16/D-17). If any Android type sneaks in (e.g., `android.util.Log`), replace with `java.util.logging.Logger`. This is not optional — fixture-first TDD requires JVM-runnable tests.

**4. The minimum change surface for `QrMenuImportScreen.kt` is 3 lines.**
The content-extraction block (lines 41–79) should not be touched. `PipelineOrchestrator` receives the already-preprocessed `content` string and returns `OrchestratorResult`. This minimizes regression risk and keeps the composable UI code clean.

**5. Add jsoup immediately; defer pdfbox-android until PDF layer task.**
Jsoup is needed for Layer 0 HTML extraction and is a small, well-tested dependency. Add it in the first implementation wave. pdfbox-android is larger and only needed when implementing the PDF layout path — add it in the wave that tackles `PdfLayoutExtractor`. This keeps initial build size impact low.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `com.tom-roush:pdfbox-android` latest stable is `2.0.27.0` | §4, §9 | Wrong version → Gradle resolution failure; easy fix — verify on Maven Central |
| A2 | LocalAiParser model file (`menu_parser.gguf`) is not actively used in production builds | §1 | If model is in production, removing it from pipeline needs announcement |
| A3 | `kotlinx-serialization` plugin is NOT configured in the project | §9 | If plugin is already present, org.json preference still holds but serialization is available |

---

## Sources

### PRIMARY (VERIFIED — direct source code inspection)
- `LocalAiParser.kt` — full file read; MediaPipe LlmInference usage, temperature=0.2 confirmed
- `MenuContentPreprocessor.kt` — full file read; Next.js + API JSON handling confirmed; no Jsoup
- `LLMApiClient.kt` — full file read; `enrichDishes()` at line 528, temperature=0.1 confirmed
- `PdfMenuExtractor.kt` — full file read; PdfRenderer+Tesseract, no pdfbox, no layout metadata
- `WebViewMenuExtractor.kt` — full file read; `WebExtractResult` type confirmed
- `QrMenuImportScreen.kt` — read lines 1–200; `processUrl()` structure and replacement target confirmed
- `MenuParser.kt` — full file read; `MenuItem` and `MenuCategory` types confirmed
- `build.gradle.kts` — full file read; dependency inventory confirmed
- `ExampleUnitTest.kt` — full file read; JUnit 4, stub only
- `1-CONTEXT.md` — full file read; all locked decisions
- `deterministic-menu-parser-architecture.md` — full file read; 6-layer design
