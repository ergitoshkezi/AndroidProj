---
phase: 02-dom-aware-menu-block-extraction
plan: "04"
subsystem: parser/dom
tags: [testing, fixtures, dom-pipeline, scoring, integration]

dependency_graph:
  requires: [02-01, 02-02, 02-03]
  provides: [test-coverage-D03, test-coverage-D04, dom-fixture-suite]
  affects: [DomBlockScorer, MenuBlockSelector, BlockTextExtractor, DomSnapshotParser]

tech_stack:
  added: []
  patterns:
    - JUnit4 unit tests for object singletons (DomBlockScorer, MenuBlockSelector, BlockTextExtractor)
    - JSON fixture loading via ClassLoader.getResourceAsStream
    - Deterministic score calculation validation with delta assertions

key_files:
  created:
    - app/src/test/resources/dom_snapshot_clean_menu.json
    - app/src/test/resources/dom_snapshot_noisy_page.json
    - app/src/test/resources/dom_snapshot_minimal.json
    - app/src/test/java/com/example/ingredient/parser/dom/DomBlockScorerTest.kt
    - app/src/test/java/com/example/ingredient/parser/dom/DomPipelineIntegrationTest.kt
  modified: []

decisions:
  - "Fixtures placed at root of test/resources (not fixtures/menus/) for flat getResourceAsStream('name.json') lookup"
  - "positionScore verified to return 0.5 (not 0.1) for out-of-range blocks per D-03 LOCKED binary spec"
  - "Minimal fixture uses textLength=3000 with foodHits=2 to land scores in [0.15, 0.25) for D-04 fallback coverage"
  - "Both BlockTextExtractor overloads (extractFromBlocks / extractFromDomBlocks) tested for equivalence"

metrics:
  duration: "~12 minutes"
  completed: "2026-05-21"
  tasks_completed: 3
  files_created: 5
  tests_added: 16
  tests_passing: 16
  tests_failing: 0
---

# Phase 02 Plan 04: DOM Pipeline Test Suite Summary

**One-liner:** 3 JSON DOM fixtures + 16 JUnit4 tests validating D-03 scoring formula, D-04 fallback path, and noise rejection across the full DOM segmentation pipeline.

## What Was Built

### Task 1 — JSON Test Fixtures (3 files)

| Fixture | Blocks | Purpose |
|---------|--------|---------|
| `dom_snapshot_clean_menu.json` | 5 | 3 menu sections (Antipasti/Primi/Secondi) + nav + footer — noise rejection baseline |
| `dom_snapshot_noisy_page.json` | 5 | 1 menu block + cookie-banner + social-share + booking-widget + review-section |
| `dom_snapshot_minimal.json` | 2 | Both blocks score < 0.25 but ≥ 0.15 — triggers D-04 fallback path |

All fixtures are realistic Italian restaurant DOM snapshots with correct D-02 field contract (16 fields per block).

### Task 2 — DomBlockScorerTest.kt (8 tests)

| Test | Signal / Formula Path |
|------|-----------------------|
| `high score menu block >= 0.60` | All 5 signals at maximum |
| `low score nav block < 0.15` | Zero price/food, navigation role, y=0 |
| `positionScore binary - good range` | y=300 + depth=4 → 1.0 |
| `positionScore binary - outside range` | y=50 or depth=8 → 0.5 (not 0.1) |
| `priceDensity capped at 1f` | priceHits=1000, textLength=100 → 1.0 |
| `all 3 fixtures parseable` | DomSnapshotParser round-trip for each fixture |
| `scoreAll sorts descending` | Ordering contract for downstream consumers |
| `schemaType Menu max semanticRole` | Schema.org type takes priority per D-03 |

### Task 3 — DomPipelineIntegrationTest.kt (8 tests)

| Test | Pipeline Stage Covered |
|------|------------------------|
| `clean menu selects 3 blocks` | Parse → Score → Select (BALANCED) |
| `noisy page selects only menu block` | Filter → 4 noise blocks rejected |
| `minimal page uses fallback` | D-04: usedFallback == true |
| `noise rejection - navbar/footer below 0.25` | Score + Selection threshold |
| `extracted text non-empty` | Full pipeline: parse → score → select → extract |
| `extractFromDomBlocks == extractFromBlocks` | Both BlockTextExtractor overloads equivalent |
| `cookie banner and social share < 0.10` | Extreme noise rejection |
| `STRICT mode <= BALANCED count` | ParsingMode threshold hierarchy |

## Test Results

```
DomBlockScorerTest:       8 tests, 0 failures, 0 errors
DomPipelineIntegrationTest: 8 tests, 0 failures, 0 errors
Total: 16/16 PASS
```

Build: `BUILD SUCCESSFUL in 33s`

## Deviations from Plan

None — plan executed exactly as written.

The W1 checker warning was pre-empted: `BlockTextExtractor.extractFromDomBlocks(blocks)` is used in `DomBlockScorerTest` (fixture parsability test) and `BlockTextExtractor.extractFromBlocks(result.selectedBlocks)` is used for the primary pipeline path, with both verified equivalent in the integration test.

## Known Stubs

None — test fixtures are complete with all 16 D-02 fields and realistic values.

## Phase 02 Completion Check

All 4 plans for phase 02-dom-aware-menu-block-extraction now have SUMMARY.md files:

| Plan | Summary | Status |
|------|---------|--------|
| 02-01 | 02-01-SUMMARY.md | ✅ |
| 02-02 | 02-02-SUMMARY.md | ✅ |
| 02-03 | 02-03-SUMMARY.md | ✅ |
| 02-04 | 02-04-SUMMARY.md | ✅ (this plan) |

Phase 02 is complete. The DOM-aware menu block extraction pipeline (D-01 through D-09 decisions) is fully implemented and tested.

## Self-Check: PASSED

- [x] `dom_snapshot_clean_menu.json` — exists
- [x] `dom_snapshot_noisy_page.json` — exists
- [x] `dom_snapshot_minimal.json` — exists
- [x] `DomBlockScorerTest.kt` — exists, 8 tests pass
- [x] `DomPipelineIntegrationTest.kt` — exists, 8 tests pass
- [x] Commits: 4d5879f (fixtures), 910a993 (scorer tests), d021ff1 (integration tests)
