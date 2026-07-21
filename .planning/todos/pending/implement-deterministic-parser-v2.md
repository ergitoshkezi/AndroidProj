---
title: "Implement Deterministic Menu Parser v2"
date: 2026-05-20
priority: high
ref_note: ".planning/notes/deterministic-menu-parser-architecture.md"
---

# Todo: Implement Deterministic Menu Parser v2

Full implementation of the 6-layer deterministic parsing pipeline.
See architecture note for design details and code examples.

---

## Tasks by Layer

### Layer 0 — Input Normalizers
- [ ] `HtmlMenuNormalizer`: JSON-LD schema.org/Menu extractor (Priority 1)
- [ ] `HtmlMenuNormalizer`: Jsoup semantic extractor with noise-element removal (Priority 2)
- [ ] `PdfMenuNormalizer`: PdfBox Android layout-aware extraction (x, y, fontSize, isBold)
- [ ] Add `pdfbox-android` and `jsoup` dependencies to `build.gradle`

### Layer 1 — Noise Filter
- [ ] `MenuDenoiser`: implement regex noise patterns (hours, addresses, social, legal)
- [ ] Unit tests with real noisy HTML samples

### Layer 2 — Tokenizer
- [ ] `MenuTokenizer`: implement `TokenType` enum + `LineToken` data class
- [ ] Price regex (multi-currency: €, £, $, EUR)
- [ ] Price-at-end-of-line regex
- [ ] Known categories set (Italian, English, French, Spanish)
- [ ] `isCategoryCandidate()` heuristic function
- [ ] Unit tests for each token type

### Layer 3 — State Machine Parser
- [ ] `ParserState` sealed class (5 states)
- [ ] `MenuStateMachineParser.parse()` with price-window look-ahead
- [ ] `ASTCategory.Builder` + `ASTDish.Builder` helper classes
- [ ] `String.removePriceSuffix()` extension
- [ ] Unit tests: price on same line, price on next line, no category header

### Layer 4 — Validator + Normalizer
- [ ] `MenuASTValidator.validate()`: price range, name length, all-digit filter
- [ ] Deduplication by normalized name
- [ ] Category merging (same normalized name)
- [ ] Unit tests: duplicates, out-of-range prices, all-digit names

### Layer 5 — LLM Enricher
- [ ] `DishEnrichment` data class with `@Serializable`
- [ ] `LLMEnricher.enrich()` with schema-first system prompt
- [ ] JSON extraction regex (handle LLM surrounding text)
- [ ] Retry logic (max 2, on failure return dish unchanged)
- [ ] `temperature = 0.0` in API call
- [ ] Unit test: enricher NEVER adds new dish names

### Layer 6 — Confidence Gate
- [ ] `ConfidenceGate.filter()` with configurable thresholds
- [ ] `ParsedMenuResult` (confirmed / uncertain / discarded)
- [ ] UI: show uncertain items with warning badge

### Orchestration
- [ ] `PipelineOrchestrator` replaces current `processUrl()` in `QrMenuImportScreen`
- [ ] `tryParseStrategies()`: state machine → simple extractor → LLM fallback
- [ ] Progress callbacks wired to UI status messages

### Testing
- [ ] Create `test/fixtures/menus/` with 10+ real menu samples (HTML, PDF, text)
- [ ] Regression test: same menu → identical output on 3 consecutive runs
- [ ] Contract test: LLM enricher does not invent dishes
