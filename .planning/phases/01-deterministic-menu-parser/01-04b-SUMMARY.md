---
phase: 01-deterministic-menu-parser
plan: 04b
subsystem: parser
tags: [html-extraction, observability, pipeline-orchestrator, regression-test, determinism]
dependency_graph:
  requires: ["01-00", "01-01", "01-02", "01-03", "01-04a"]
  provides: ["MenuParserPipeline", "HtmlMenuExtractor", "ParseLogger", "PipelineRegressionTest-D17"]
  affects: ["parser", "MenuParserPipeline", "unit-tests"]
tech_stack:
  added: []
  patterns: ["Parameterized JUnit tests", "UUID-stable structural serialization", "Pure Kotlin unit testing (no Android Context)"]
key_files:
  created: []
  modified:
    - Ingredient/app/src/test/java/com/example/ingredient/parser/PipelineRegressionTest.kt
key_decisions:
  - "Used LineClassifier+MenuGrammarParser directly in regression test (not MenuParserPipeline) because unit tests cannot access Android Context (SharedPreferences/LLM)"
  - "Serialize MenuAST by section headers + item names + prices only — UUIDs excluded for determinism"
  - "4 assertEquals assertions per fixture: section count (run1==run2, run2==run3) and structure equality (run1==run2, run2==run3)"
metrics:
  duration: "~10 minutes"
  completed: "2026-05-20"
  tasks_completed: 4
  files_changed: 1
---

# Phase 01 Plan 04b: HTML Extractor, Parse Logger, Pipeline Orchestrator & Regression Test Summary

**One-liner:** Activated D-17 determinism regression test using LineClassifier+MenuGrammarParser — 30 tests (10 fixtures × 3 methods), 100% passing.

## Tasks Completed

| Task | Name | Status | Commit |
|------|------|--------|--------|
| 1 | HtmlMenuExtractor | ✅ Already implemented (verified) | — |
| 2 | ParseLogger | ✅ Already implemented (verified) | — |
| 3 | MenuParserPipeline orchestrator | ✅ Already implemented (verified) | — |
| 4 | Activate PipelineRegressionTest (D-17) | ✅ Implemented + passing | `206832a` |

## Implementation Verification

### HtmlMenuExtractor
- `object HtmlMenuExtractor` ✅
- `fun extract(html: String, url: String): ExtractionResult` ✅
- Strategies: `JSON_LD`, `KNOWN_PLATFORM`, `STRIPPED_HTML`, `VISIBLE_TEXT_FALLBACK` ✅
- `extractJsonLd()` helper ✅

### ParseLogger
- `object ParseLogger` ✅
- `fun log(result: MenuParseResult)` ✅
- Structured output with `Log.d(TAG, ...)` ✅
- ASCII-boxed trace: mode, confidence, duration, sections, items, llm_fb, warnings, tokens, conf_breakdown ✅

### MenuParserPipeline
- `class MenuParserPipeline(llmEnricher, mode, confidenceThreshold, enrichmentThreshold)` ✅
- `suspend fun parse(rawContent: String, sourceType: SourceType): MenuParseResult` ✅
- `data class MenuParseResult` ✅
- Pipeline layers: MenuContentPreprocessor → OcrPostProcessor → LineClassifier → MenuGrammarParser → StructuralValidator → RepairEngine → ConfidenceEngine → LLMEnricher → AstToDtoMapper ✅
- `confidenceThreshold = 0.45f` (default, D-15 fallback) ✅
- `enrichmentThreshold = 0.65f` (D-12) ✅

### PipelineRegressionTest (D-17 activated)
- 3 `runParse()` invocations per fixture ✅
- 4 `assertEquals` assertions (section count × 2, structure × 2) ✅
- Parameterized over all 10 fixture files = **30 tests** ✅
- **0 failures, 100% pass rate** ✅
- Duration: 0.644s ✅

## Deviations from Plan

### [Rule 2 - Adaptation] Used pure Kotlin parser layers instead of MenuParserPipeline in unit test

**Found during:** Task 4
**Issue:** Plan asked to uncomment `MenuParserPipeline.parse()` calls, but `MenuParserPipeline` requires Android Context (SharedPreferences/LLM) — unavailable in JVM unit tests (`src/test/`). Full pipeline would crash at runtime.
**Fix:** Used `LineClassifier + MenuGrammarParser` directly, which is pure Kotlin with no Android framework dependencies. Per project context guidance.
**Impact:** Test proves determinism of the core parsing layers (classifier + grammar). Full pipeline determinism (including enrichment) is deferred to instrumented tests.
**Files modified:** `PipelineRegressionTest.kt`
**Commit:** `206832a`

### [Rule 1 - Bug] Updated serialize() to exclude UUIDs

**Found during:** Task 4
**Issue:** `MenuGrammarParser` generates `UUID.randomUUID()` for each `ASTSection` and `ASTMenuItem` ID. Using `.toString()` on `MenuAST` would produce different strings each run, making determinism assertions always fail.
**Fix:** Replaced `result.toString()` with a canonical serializer that outputs only section headers, item names, and prices — structural content that must be stable across runs.
**Files modified:** `PipelineRegressionTest.kt`
**Commit:** `206832a`

## Test Results

```
PipelineRegressionTest: 30 tests, 0 failures, 100% successful (0.644s)

pipeline produces identical output on 3 consecutive runs:
  [english_menu.txt]          ✅ passed (0.257s)
  [italian_html.html]         ✅ passed (0.063s)
  [italian_simple.txt]        ✅ passed (0.027s)
  [mixed_noise.txt]           ✅ passed (0.019s)
  [multi_column.txt]          ✅ passed (0.105s)
  [no_prices.txt]             ✅ passed (0.013s)
  [ocr_corrupted.txt]         ✅ passed (0.012s)
  [pdf_extracted.txt]         ✅ passed (0.028s)
  [prices_separate_lines.txt] ✅ passed (0.017s)
  [qr_direct.txt]             ✅ passed (0.002s)
```

## Known Stubs

- `pipeline output is stable across mode changes` test: still a placeholder (`assertTrue(true)`). Deferred — will be implemented when STRICT/BALANCED/AGGRESSIVE cross-mode structural comparison is defined.

## Self-Check: PASSED

- `PipelineRegressionTest.kt` — modified ✅
- `HtmlMenuExtractor.kt` — verified exists with `object HtmlMenuExtractor` ✅
- `ParseLogger.kt` — verified exists with `object ParseLogger` ✅
- `MenuParserPipeline.kt` — verified exists with `class MenuParserPipeline` ✅
- Commit `206832a` — verified in `git log` ✅
- 30 tests passing — verified in test report ✅
