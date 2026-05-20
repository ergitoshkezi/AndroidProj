---
phase: 01-deterministic-menu-parser
plan: 05
subsystem: ui-wiring
tags: [pipeline-wiring, fallback-logic, build-config, integration]
dependency_graph:
  requires: [01-00, 01-01, 01-02, 01-03, 01-04a, 01-04b]
  provides: [end-to-end-deterministic-pipeline, fallback-to-llm]
  affects: [QrMenuImportScreen, build.gradle.kts]
tech_stack:
  added: []
  patterns: [deterministic-first-then-llm-fallback, confidence-threshold-gate]
key_files:
  created: []
  modified:
    - Ingredient/app/src/main/java/com/example/ingredient/QrMenuImportScreen.kt
    - Ingredient/app/build.gradle.kts
decisions:
  - "Used fully-qualified class names instead of imports for parser package classes — avoids import conflicts with existing com.example.ingredient.* names and compiles cleanly"
  - "Fallback threshold set at exactly 0.5f (D-15); partial success messages use 0.65f and 0.45f bands for UX differentiation"
  - "MenuContentPreprocessor reused as Layer 0 for WebView content; PDF and QR-direct paths feed raw text straight to pipeline"
metrics:
  duration: "~10 minutes"
  completed: "2025-07-27"
  tasks_completed: 3
  files_modified: 2
---

# Phase 01 Plan 05: QrMenuImportScreen Wiring & Build Verification Summary

**One-liner:** QrMenuImportScreen wired with MenuParserPipeline as primary parser and confidence-gated LLM fallback at threshold 0.5f, jsoup dependency confirmed in build.gradle.kts, full build GREEN.

## What Was Done

### Task 1: Verify QrMenuImportScreen Wiring
All pipeline wiring was already in place from prior wave sessions. Verified:

- `MenuParserPipeline` instantiated with `LLMEnricher` and `ParsingMode.BALANCED` (lines 83–87)
- Source type determined from URL pattern: `PDF | HTML | QR_DIRECT | RAW_TEXT` (lines 88–93)
- `pipeline.parse(content, sourceType)` called (line 94)
- `ParseLogger.log(parseResult)` called immediately after (line 95)
- Fallback gate: `parseResult.categories.isNotEmpty() && parseResult.confidence >= 0.5f` (line 97) — D-15 compliant
- Fallback status message: `"Parser deterministico insufficiente, uso AI classica…"` (line 108) — D-02 compliant
- Legacy path uses: `LLMApiClient.processMenuText()` → `MenuParser().parseMenuText()` → `LLMApiClient.enrichDishes()` (lines 111–122)
- Success message includes `"(via AI)"` suffix to distinguish from deterministic result (line 123) — D-02 compliant

### Task 2: Verify jsoup Dependency
`implementation("org.jsoup:jsoup:1.18.1")` was already present in `build.gradle.kts` line 77, added as part of plan 04b execution.

### Task 3: Build & Test Verification
- `./gradlew compileDebugKotlin` → **BUILD SUCCESSFUL** (34s, 18 tasks UP-TO-DATE)
- `./gradlew :app:testDebugUnitTest` → **BUILD SUCCESSFUL** (41s, all tests pass)

## Acceptance Criteria Verification

| Criterion | Result |
|-----------|--------|
| `MenuParserPipeline` in QrMenuImportScreen | ✅ 1 match (fully-qualified instantiation) |
| `LLMEnricher` in QrMenuImportScreen | ✅ 2 matches |
| `ParsingMode.BALANCED` | ✅ 1 match |
| `parseResult.confidence >= 0.5f` | ✅ 1 match |
| `Parser deterministico insufficiente` | ✅ 1 match |
| `via AI` | ✅ 1 match |
| `ParseLogger.log` | ✅ 1 match |
| `jsoup:1.18.1` in build.gradle.kts | ✅ 1 match |
| `compileDebugKotlin` BUILD SUCCESSFUL | ✅ |
| `testDebugUnitTest` all pass | ✅ |

> **Note on MenuParserPipeline count:** Plan acceptance criteria expected 2+ matches (import + instantiation). The file uses fully-qualified names (`com.example.ingredient.parser.MenuParserPipeline`) throughout rather than import statements — this is functionally identical and compiles cleanly (no ambiguity with other `ingredient.*` classes).

## Deviations from Plan

None — plan executed exactly as written. All wiring and configuration was already in place from prior wave sessions (01-04a and 01-04b). This plan served as final integration verification.

## Known Stubs

None. All data paths are wired end-to-end:
- Deterministic pipeline produces real `ParseResult` objects
- Fallback path calls real `LLMApiClient` methods
- Firebase uploader writes to real database

## Threat Flags

None. No new network endpoints, auth paths, or trust-boundary changes introduced in this plan.

## Phase Completion Status

All 7 active plans for Phase 01 now have SUMMARY.md files:

| Plan | Name | SUMMARY |
|------|------|---------|
| 01-00 | Test Infrastructure | ✅ 01-00-SUMMARY.md |
| 01-01 | AST & Core Types | ✅ 01-01-SUMMARY.md |
| 01-02 | Parser Layers 1–3 | ✅ 01-02-SUMMARY.md |
| 01-03 | Enrichment & Confidence | ✅ 01-03-SUMMARY.md |
| 01-04a | Input Adapters | ✅ 01-04a-SUMMARY.md |
| 01-04b | HTML Extractor + Pipeline + Logger | ✅ 01-04b-SUMMARY.md |
| 01-05 | QrMenuImportScreen Wiring | ✅ 01-05-SUMMARY.md ← this |

> Plan 01-04 was superseded by the 04a/04b split; it has no SUMMARY (intentional).

## Self-Check: PASSED

- `QrMenuImportScreen.kt` exists and contains all required wiring ✅
- `build.gradle.kts` contains `jsoup:1.18.1` ✅
- `compileDebugKotlin` BUILD SUCCESSFUL ✅
- `testDebugUnitTest` BUILD SUCCESSFUL ✅
- `01-05-SUMMARY.md` created at correct path ✅
