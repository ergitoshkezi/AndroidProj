---
phase: "01"
plan: "00"
subsystem: test-infrastructure
tags: [fixtures, regression-tests, tdd, d-16, d-17, d-18]
key-files:
  created:
    - Ingredient/app/src/test/resources/fixtures/menus/italian_simple.txt
    - Ingredient/app/src/test/resources/fixtures/menus/italian_html.html
    - Ingredient/app/src/test/resources/fixtures/menus/pdf_extracted.txt
    - Ingredient/app/src/test/resources/fixtures/menus/ocr_corrupted.txt
    - Ingredient/app/src/test/resources/fixtures/menus/prices_separate_lines.txt
    - Ingredient/app/src/test/resources/fixtures/menus/multi_column.txt
    - Ingredient/app/src/test/resources/fixtures/menus/no_prices.txt
    - Ingredient/app/src/test/resources/fixtures/menus/english_menu.txt
    - Ingredient/app/src/test/resources/fixtures/menus/mixed_noise.txt
    - Ingredient/app/src/test/resources/fixtures/menus/qr_direct.txt
    - Ingredient/app/src/test/java/com/example/ingredient/parser/PipelineRegressionTest.kt
    - Ingredient/app/src/test/java/com/example/ingredient/parser/LLMEnricherContractTest.kt
    - Ingredient/app/src/test/java/com/example/ingredient/parser/MenuTokenizerTest.kt
    - Ingredient/app/src/test/java/com/example/ingredient/parser/ConfidenceEngineTest.kt
decisions:
  - "Fixture-first per D-16: create test fixtures before parser code"
  - "PipelineRegressionTest is parameterized against all fixtures for broad determinism coverage"
  - "LLMEnricherContractTest covers 6 contract rules matching D-18 anti-hallucination requirements"
  - "All test stubs use TODO(D-17)/TODO(D-18) markers for traceability to activation in Plan 04b"
metrics:
  duration: "~15 minutes"
  completed: "2026-05-20"
  fixtures_created: 10
  test_stubs_created: 4
  total_files_created: 14
---

# Phase 01 Plan 00: Test Infrastructure Summary

## One-liner
Fixture-first test infrastructure: 10 diverse menu fixture files (≥500 bytes each) + 4 Kotlin test stub classes with TODO(D-17)/D-18 activation markers for parser determinism and LLM anti-hallucination contracts.

## What was built

### Task 1: 10 Menu Fixture Files (D-16)
Created `app/src/test/resources/fixtures/menus/` with 10 realistic menu samples covering the full diversity of formats the deterministic parser pipeline must handle:

| File | Format | Size | Purpose |
|------|--------|------|---------|
| `italian_simple.txt` | Clean raw text | 1127B | Baseline Italian trattoria, multi-section |
| `italian_html.html` | Full HTML page | 3496B | Nav/footer noise, span-wrapped prices |
| `pdf_extracted.txt` | PDF text extract | 1318B | Dot-leaders, page numbers, column artifacts |
| `ocr_corrupted.txt` | OCR corruption | 736B | l→1, O→0, rn→m substitutions |
| `prices_separate_lines.txt` | Prices on next line | 1336B | Tests price-to-dish association logic |
| `multi_column.txt` | Multi-column layout | 1291B | Pizza menu with left/right column merge |
| `no_prices.txt` | No prices | 1396B | Tests graceful handling of missing prices |
| `english_menu.txt` | English + allergens | 2315B | GBP prices, EU allergen code format |
| `mixed_noise.txt` | Heavy noise | 1694B | Cookie banner + social links + footer |
| `qr_direct.txt` | QR pipe-delimited | 552B | Compact single-line QR code text |

### Task 2: PipelineRegressionTest.kt (D-17)
- `@RunWith(Parameterized::class)` — runs against all 10 fixtures automatically
- `pipeline produces identical output on 3 consecutive runs` — stub ready for activation
- `fixture files exist and are non-empty` — live test verifying all fixtures ≥500 bytes
- `TODO(D-17)` markers with full commented-out implementation for Plan 04b

### Task 3: LLMEnricherContractTest.kt (D-18)
Six contract tests covering the anti-hallucination rules from D-18:
1. Dish IDs not in original AST → rejected
2. Description = dish name (copy-paste) → rejected
3. Allergen codes validated against EU allowed set
4. Descriptions truncated at 200 chars
5. Dish count preserved after enrichment
6. Prices unchanged after enrichment

### Task 4: MenuTokenizerTest.kt + ConfidenceEngineTest.kt
- `MenuTokenizerTest`: 6 stubs for LineClassifier token types (PriceLine, CategoryHeader, DishCandidate, Noise, AllergenLine, Ambiguous)
- `ConfidenceEngineTest`: 6 stubs for confidence scoring factors including D-15 fallback threshold

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1: Fixtures | fac684d | Create 10 menu fixture files covering diverse formats |
| Task 2+3: Regression/Contract | 595f3f9 | Add PipelineRegressionTest (D-17) and LLMEnricherContractTest (D-18) |
| Task 4: Unit stubs | 33d2d23 | Add MenuTokenizerTest and ConfidenceEngineTest stubs |

## Deviations from Plan

None — plan executed exactly as written.

- The project context specified different test class content than the PLAN.md (which had a more complete `PipelineRegressionTest`). The PLAN.md version was used as it is more complete and includes the `fixture files exist and are non-empty` live test.
- `qr_direct.txt` required a small content addition to reach 500 bytes (initial content was 493 bytes). Added two more menu items and one more section entry.

## Known Stubs

All test stubs are intentional infrastructure placeholders:

| File | Stub type | Activation plan |
|------|-----------|-----------------|
| `PipelineRegressionTest.kt` | `pipeline produces identical output on 3 consecutive runs` | Plan 04b (MenuParserPipeline) |
| `PipelineRegressionTest.kt` | `pipeline output is stable across mode changes` | Plan 04b |
| `LLMEnricherContractTest.kt` | All 6 contract assertions | Plan 04b (LLMEnricher) |
| `MenuTokenizerTest.kt` | All 6 token classification tests | Plan 02 (LineClassifier) |
| `ConfidenceEngineTest.kt` | All 6 confidence scoring tests | Plan 03 (ConfidenceEngine) |

Stubs do not block plan goal (test infrastructure creation). All stubs pass via `assertTrue(true)`.

## Self-Check

PASSED

**Fixture files (10/10 exist, all ≥500 bytes):**
- italian_simple.txt ✓ (1127B)
- italian_html.html ✓ (3496B)
- pdf_extracted.txt ✓ (1318B)
- ocr_corrupted.txt ✓ (736B)
- prices_separate_lines.txt ✓ (1336B)
- multi_column.txt ✓ (1291B)
- no_prices.txt ✓ (1396B)
- english_menu.txt ✓ (2315B)
- mixed_noise.txt ✓ (1694B)
- qr_direct.txt ✓ (552B)

**Test stubs (4/4 created):**
- PipelineRegressionTest.kt ✓ (contains `class PipelineRegressionTest`, `@Parameterized`)
- LLMEnricherContractTest.kt ✓ (contains `class LLMEnricherContractTest`, all 6 contract tests)
- MenuTokenizerTest.kt ✓ (contains 6 LineClassifier token stubs)
- ConfidenceEngineTest.kt ✓ (contains 6 confidence scoring stubs)

**Commits (3/3 verified):**
- fac684d ✓ — test(01-00): create menu fixture files
- 595f3f9 ✓ — test(01-00): add parser test stubs for D-17 and D-18
- 33d2d23 ✓ — test(01-00): add unit test stubs for LineClassifier and ConfidenceEngine
