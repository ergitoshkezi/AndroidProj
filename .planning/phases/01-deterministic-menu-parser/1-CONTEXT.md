# Phase 1: Deterministic Menu Parser v2 - Context

**Gathered:** 2026-05-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace the current `processUrl()` implementation in `QrMenuImportScreen.kt` with a
6-layer deterministic pipeline. The LLM (Groq) is used exclusively for dish enrichment
(ingredients, allergens, calories, cuisine origin) — never for identifying categories,
dishes, or prices. Output remains fully compatible with `List<MenuCategory>` (no breaking
changes to Firebase upload, UI, or vetrina cliente).

The phase does NOT include:
- Changes to the vetrina/client UI
- New enrichment fields beyond what's already in MenuItem
- Changes to FirebaseMenuUploader
- OCR integration (deferred)

</domain>

<decisions>
## Implementation Decisions

### Migration strategy
- **D-01:** Wrapper-progressive approach. New pipeline runs first. If `avgConfidence < 0.5`,
  automatically fall back to the existing `processUrl()` pipeline as a safety net.
- **D-02:** No feature flag required — the fallback is transparent to the user. The status
  message should indicate which pipeline produced the result (for debugging).
- **D-03:** Fallback trigger: `avgConfidence < 0.5` computed over all confirmed dishes in
  the `MenuAST`. If the AST has 0 dishes, also trigger fallback.

### Data model
- **D-04:** New `MenuAST` internal representation (see architecture note for full type
  definitions). The AST exists only within the pipeline. At the end of the pipeline,
  a mapper converts `MenuAST → List<MenuCategory>` (existing type). No changes to
  `MenuItem`, `MenuCategory`, `FirebaseMenuUploader`, or any UI code.
- **D-05:** `MenuAST` carries confidence scores and `DishFlag` sets per dish. These are
  discarded after the confidence gate — they do not propagate to `MenuItem`.

### Reuse of existing code
- **D-06:** `MenuContentPreprocessor.kt` is reused as **Layer 0** for HTML/Next.js/API
  JSON structured extraction. It already handles the highest-signal paths (JSON-LD,
  Next.js `__NEXT_DATA__`, API blobs). Extend it, do not replace it.
- **D-07:** `LocalAiParser.kt` is examined and potentially reused as input to the
  **fallback strategy** inside `tryParseStrategies()`. Researcher to assess its current
  capabilities before planning decides its role.
- **D-08:** `PdfMenuExtractor.kt` and `WebViewMenuExtractor.kt` remain as input adapters.
  Layer 0 normalization wraps their output before handing it to Layer 1+.

### LLM enrichment (Layer 5)
- **D-09:** `temperature = 0.0` for all enrichment calls (determinism).
- **D-10:** Schema-first: provide strict JSON schema in system prompt with
  `additionalProperties: false`. The LLM must never add fields not in the schema.
- **D-11:** Retry max 2 times per dish enrichment. On failure, keep dish without enrichment
  (do NOT drop it). Enrichment is optional metadata, not required for a dish to be valid.
- **D-12:** LLM enrichment is called ONLY on dishes that survive the confidence gate
  (confirmed dishes). Uncertain and discarded dishes are NOT enriched.

### Confidence and fallback thresholds
- **D-13:** `minDishConfidence = 0.35f` — below this, dish is discarded.
- **D-14:** `minCategoryConfidence = 0.40f` — below this, category goes to "uncertain" bucket.
- **D-15:** Pipeline fallback to legacy: triggered when `avgConfidence < 0.5` over all
  confirmed dishes, OR when confirmed dish count is 0.

### Testing strategy
- **D-16:** Fixture-first (TDD). Before writing any parser code, create a test fixture
  directory with 10+ real menu samples: HTML pages, PDF text extracts, raw QR text.
  Fixtures should include known-bad cases (cookie banners, JS-heavy sites, menus with
  prices on separate lines, multi-column PDFs).
- **D-17:** Regression test: same fixture input must produce byte-identical output on
  3 consecutive runs (proves determinism).
- **D-18:** Contract test: LLM enricher must not add dish names to the output that were
  not present in the input AST.

### the agent's Discretion
- Exact regex patterns for each price format variant (€12, 12,00, 12.00 €, etc.)
- Choice of Builder pattern implementation for `ASTCategory` and `ASTDish`
- Internal naming of intermediate pipeline stages
- Exact look-ahead window size for price association (the 2-line window is a starting
  point — researcher may find better heuristics in existing Italian menu studies)
- Whether `LocalAiParser.kt` is integrated into the state machine or used as a separate
  strategy in `tryParseStrategies()` — researcher to recommend after assessment

</decisions>

<specifics>
## Specific Ideas

- The architecture note from the exploration session contains full Kotlin pseudocode for
  all 6 layers including state machine states, regex patterns, and confidence gate logic.
  Researcher and planner MUST read it before starting.
- "Recall-first": when in doubt, keep the dish with a low confidence flag rather than
  drop it. The user can review flagged dishes in the preview screen.
- The status message in the UI should distinguish between "new pipeline" and "fallback to
  legacy" — useful for debugging during rollout.
- Wrapper progressive means: run new pipeline → if avgConfidence < 0.5, run legacy pipeline
  → present whichever produced more/better dishes (not just any output).

</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Architecture design
- `.planning/notes/deterministic-menu-parser-architecture.md` — Full 6-layer pipeline design
  with Kotlin pseudocode, regex patterns, state machine states, confidence scoring examples,
  data structures, and recommended libraries. PRIMARY REFERENCE for this phase.

### Existing code to understand before planning
- `Ingredient/app/src/main/java/com/example/ingredient/MenuParser.kt` — Current LLM-output
  parser. Defines `MenuItem` and `MenuCategory` — these types must remain unchanged.
- `Ingredient/app/src/main/java/com/example/ingredient/MenuContentPreprocessor.kt` — Layer 0
  candidate. Already handles Next.js JSON-LD and API blobs. Extend this.
- `Ingredient/app/src/main/java/com/example/ingredient/LocalAiParser.kt` — Assess capabilities
  before deciding its role in the new pipeline.
- `Ingredient/app/src/main/java/com/example/ingredient/LLMApiClient.kt` — Existing Groq client.
  Layer 5 enricher will use this. Check `enrichDishes()` method.
- `Ingredient/app/src/main/java/com/example/ingredient/QrMenuImportScreen.kt` — The entry point.
  `processUrl()` function is the replacement target.
- `Ingredient/app/src/main/java/com/example/ingredient/WebViewMenuExtractor.kt` — Input adapter
  for web pages. Remains unchanged; new Layer 0 wraps its output.
- `Ingredient/app/src/main/java/com/example/ingredient/PdfMenuExtractor.kt` — Input adapter for
  PDF URLs. Same as above.

### No external specs
No external API specs, ADRs, or design docs beyond what's listed above. Requirements are
fully captured in the decisions above and the architecture note.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `MenuCategory` / `MenuItem` data classes: output types — must remain compatible
- `MenuContentPreprocessor.extractFromNextData()`: JSON-LD/Next.js extraction already working
- `LLMApiClient.enrichDishes()`: enrichment method already exists — verify its schema-first behavior
- `LocalAiParser.kt`: unknown capabilities — researcher to assess

### Established Patterns
- Coroutines with `Dispatchers.IO` for all network/parse work (see `LLMApiClient.kt`)
- `onProgress: (String) -> Unit` callback pattern for status updates to UI
- `runCatching {}` for safe parsing with fallback
- `org.json.JSONArray/JSONObject` used throughout (not kotlinx.serialization yet)

### Integration Points
- `QrMenuImportScreen.kt` → `processUrl()`: this is the sole entry point to replace
- Output flows to `FirebaseMenuUploader.uploadMenuWithMetadata()`: expects `List<MenuCategory>`
- Preview screen shows `parsedMenu: List<MenuCategory>` to user before upload

</code_context>

<deferred>
## Deferred Ideas

- OCR fallback architecture (ML Kit text recognition) — mentioned in exploration, out of scope
  for Phase 1. Add to backlog when PDF-with-images or photo-of-menu use cases arise.
- Multilingual normalization improvements (FR/ES specific patterns) — current known-categories
  set covers basics; deep FR/ES support is a future enhancement.
- Confidence score visibility in the preview UI (show per-dish badges) — UI change, separate phase.
- Scheduled/background menu re-import — different feature entirely.

</deferred>

---

*Phase: 01-deterministic-menu-parser*
*Context gathered: 2026-05-20*
