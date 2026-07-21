---
phase: 01-deterministic-menu-parser
plan: 04a
subsystem: parser/enrichment, parser/mapping, parser/source
tags: [llm-enrichment, anti-hallucination, ast-mapping, ocr-preprocessing]
dependency_graph:
  requires: ["01-01", "01-02", "01-03"]
  provides: ["LLMEnricher", "AstToDtoMapper", "OcrPostProcessor"]
  affects: ["01-04b", "01-05"]
tech_stack:
  added: []
  patterns: ["anti-hallucination validation", "confidence-threshold filtering", "ocr-repair rules"]
key_files:
  created:
    - Ingredient/app/src/main/java/com/example/ingredient/parser/enrichment/LLMEnricher.kt
    - Ingredient/app/src/main/java/com/example/ingredient/parser/mapping/AstToDtoMapper.kt
    - Ingredient/app/src/main/java/com/example/ingredient/parser/source/OcrPostProcessor.kt
  modified: []
decisions:
  - "ENRICH_THRESHOLD=0.65f: only dishes with confidence>=0.65 are sent to LLM (D-12)"
  - "additionalProperties=false in schema prompt: prevents LLM from inventing extra fields"
  - "AstToDtoMapper filters by MIN_DISH_CONFIDENCE=0.35f and MIN_CATEGORY_CONFIDENCE=0.40f before mapping to DTO"
  - "OcrPostProcessor.process() applies PRICE_RULES then STRUCTURAL_RULES for idempotent correction"
metrics:
  duration: "~15 min"
  completed: "2025-01-31"
  tasks_completed: 3
  files_changed: 3
---

# Phase 01 Plan 04a: Enrichment, Mapping, and OCR Processing Summary

**One-liner:** Anti-hallucination LLM enrichment, confidence-filtered AST→DTO mapper, and regex-based OCR post-processor implementing D-09/D-10/D-11/D-12/D-18.

## What Was Built

Three integration-layer components completing the first half of the parser pipeline output stage:

### 1. `LLMEnricher.kt` — Anti-Hallucination LLM Enrichment

`class LLMEnricher(private val llmClient: LLMApiClient)` with `suspend fun enrich(ast: MenuAST): MenuAST`.

**Anti-hallucination safeguards (D-18):**
- Only enriches dishes where `confidence >= ENRICH_THRESHOLD (0.65f)` — D-12
- Batches ≤15 dishes per LLM call to stay within context limits
- LLM called with `temperature = 0.0` (deterministic output) — D-09
- Prompt uses `additionalProperties=false` schema constraint — D-10
- `parseBatchResponse()` rejects any ID not in `originalIds` (hallucination guard)
- Rejects descriptions equal to dish name verbatim (copy-paste guard)
- Validates allergen codes against the 14-code EU allowed set
- Truncates descriptions at 200 characters
- Max 2 retries per batch; returns empty map on failure (keeps original dish) — D-11

### 2. `AstToDtoMapper.kt` — MenuAST → List\<MenuCategory\>

`object AstToDtoMapper` with `fun map(ast: MenuAST): List<MenuCategory>`.

**Confidence filtering (D-04/D-05 — locked thresholds):**
- Sections filtered: `section.confidence >= MIN_CATEGORY_CONFIDENCE (0.40f)`
- Items filtered: `item.confidence >= MIN_DISH_CONFIDENCE (0.35f)`
- All internal AST metadata discarded (confidence, DishFlags, lineRanges, trace)
- Output fully compatible with existing `FirebaseMenuUploader` and UI

**Mapping:**
- `ASTSection.header.text` → `MenuCategory.categoryName`
- `ASTMenuItem` → `MenuItem` with empty `country/region/cucina` (populated later by LLM enrichment)
- Post-filter: categories with blank names or empty dish lists removed

### 3. `OcrPostProcessor.kt` — OCR Artifact Correction

`object OcrPostProcessor` with `fun process(raw: String): String` and `fun assessQuality(text: String): Float`.

**PRICE_RULES:** `l/I→1`, `O→0` in price context, `OO→00` after decimal, `€E→€` artifact fix.
**STRUCTURAL_RULES:** Hyphenated line-break merging, dot-leader normalisation, large-gap→tab conversion.
**`mergeHyphenatedLines()`:** Merges Italian connectors (`di`, `con`, `e`, `al`, …) with following lowercase line.
**`assessQuality()`:** Returns 0–1 quality score based on garbage-line ratio and short-line ratio.

## Deviations from Plan

### Auto-fixed Issues (Rule 2 — Missing Critical Functionality)

**1. [Rule 2 - Missing] Added confidence filtering to AstToDtoMapper**
- **Found during:** Task 2 verification
- **Issue:** Original implementation had no confidence-based filtering, meaning low-confidence items (noise) would flow through to the UI. Project context explicitly states `minDishConfidence=0.35f` and `minCategoryConfidence=0.40f` as locked thresholds.
- **Fix:** Added `MIN_DISH_CONFIDENCE = 0.35f` and `MIN_CATEGORY_CONFIDENCE = 0.40f` constants; filter applied in `map()` before DTO construction.
- **Files modified:** `AstToDtoMapper.kt`
- **Commit:** c3ca5d5

**2. [Rule 2 - Missing] Added `additionalProperties=false` to LLMEnricher prompt schema**
- **Found during:** Task 1 verification
- **Issue:** Project context lists `additionalProperties=false` as a locked schema constraint (anti-hallucination). Original prompt text was missing it.
- **Fix:** Updated `buildBatchPrompt()` to annotate the schema line with `(additionalProperties=false)`.
- **Files modified:** `LLMEnricher.kt`
- **Commit:** c3ca5d5

## Known Stubs

None — all three files are fully wired. `LLMEnricher` uses the real `LLMApiClient`. `AstToDtoMapper` maps real AST nodes. `OcrPostProcessor` applies real regex rules.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: hallucination-boundary | LLMEnricher.kt | LLM response validation enforces strict ID-matching and allergen allowlist; no new surface added beyond what plan specifies |

## Self-Check: PASSED

- `parser/enrichment/LLMEnricher.kt` — FOUND ✅
- `parser/mapping/AstToDtoMapper.kt` — FOUND ✅
- `parser/source/OcrPostProcessor.kt` — FOUND ✅
- Commit `c3ca5d5` — FOUND ✅ (`feat(01-04a): implement LLMEnricher, AstToDtoMapper, OcrPostProcessor`)
- All acceptance criteria: PASS ✅ (7/7 LLMEnricher, 6/6 AstToDtoMapper, 4/4 OcrPostProcessor)
