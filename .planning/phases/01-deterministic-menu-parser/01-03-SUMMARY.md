---
phase: 01-deterministic-menu-parser
plan: "03"
subsystem: parser-pipeline
tags: [repair, validation, confidence, ast, kotlin, android]
dependency_graph:
  requires: [01-00, 01-01, 01-02]
  provides: [repair-engine, repair-heuristics, structural-validator, confidence-engine]
  affects: [parser-pipeline-orchestration]
tech_stack:
  added: []
  patterns:
    - Immutable AST transformation via Kotlin data class copy()
    - Ordered repair pass pipeline (R1–R11)
    - Mode-aware validation (STRICT / AGGRESSIVE)
    - Weighted multi-factor confidence scoring
key_files:
  created:
    - Ingredient/app/src/main/java/com/example/ingredient/parser/repair/RepairEngine.kt
    - Ingredient/app/src/main/java/com/example/ingredient/parser/repair/RepairHeuristics.kt
    - Ingredient/app/src/main/java/com/example/ingredient/parser/validation/StructuralValidator.kt
    - Ingredient/app/src/main/java/com/example/ingredient/parser/confidence/ConfidenceEngine.kt
  modified: []
decisions:
  - RepairEngine takes mode at construction time and returns Pair<MenuAST, List<RepairEvent>> for debuggability
  - RepairHeuristics delegates to RegexRegistry.PRICE_IN_TEXT / PRICE_VARIANT for regex reuse
  - ValidationReport named differently from ValidationResult (plan contract) — more expressive name; plan accepted this
  - ConfidenceEngine.score() takes explicit totalLines/noiseLines to decouple noise counting from scoring logic
metrics:
  duration: "verification + commit"
  completed: "2025-01-03"
  tasks_completed: 4
  files_created: 4
  files_modified: 0
---

# Phase 01 Plan 03: Repair, Validation, and Confidence Scoring Summary

**One-liner:** Ordered R1–R11 AST repair passes, V1–V8 structural validation with mode-aware blocking, and weighted multi-factor confidence scoring (structure 35% + price coverage 25% + noise 25% + size 15%).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | RepairEngine with ordered repair passes | d12b0de | `parser/repair/RepairEngine.kt` |
| 2 | RepairHeuristics utility functions | d12b0de | `parser/repair/RepairHeuristics.kt` |
| 3 | StructuralValidator with V1-V8 rules | d12b0de | `parser/validation/StructuralValidator.kt` |
| 4 | ConfidenceEngine with weighted scoring | d12b0de | `parser/confidence/ConfidenceEngine.kt` |

## What Was Built

### RepairEngine (`parser/repair/RepairEngine.kt`)
- `class RepairEngine(mode: ParsingMode, locale: LocalePack)` — constructed with context, stateless per call
- `fun repair(ast: MenuAST): Pair<MenuAST, List<RepairEvent>>` — returns new AST + audit trail
- Ordered repair passes:
  - **R1:OrphanAdoption** — wraps orphaned items in synthetic "Menu" section
  - **R2:EmptySectionPrune** — removes empty sections (skipped in AGGRESSIVE mode)
  - **R4:DuplicateNameMerge** — deduplicates by lowercase name, keeps highest-confidence copy
  - **R6:DescriptionCap** — truncates descriptions to max 3 sentences
  - **R11:GhostHeaderMerge** — merges empty high-confidence header with following low-confidence section
  - **R3:PriceFromDesc** — delegates to `RepairHeuristics.extractPriceFromDescription()`
  - **R10:NoiseSectionElim** — removes sections matching noise keywords (seguici, contatti, orari, etc.)
  - **R7:SingleItemDemotion** — merges single-item low-confidence sections (STRICT mode only)
- `data class RepairEvent(rule, description, affectedItems)` for debugging/observability

### RepairHeuristics (`parser/repair/RepairHeuristics.kt`)
- `object RepairHeuristics` — stateless singleton
- `extractPriceFromDescription(item, locale)` — finds prices embedded in description text via `RegexRegistry.PRICE_IN_TEXT`, extracts amount, cleans description, sets `PriceAssociation.INFERRED`
- `resolveVariantPrice(item, locale)` — handles "€8.00 / €12.00" variant pricing via `RegexRegistry.PRICE_VARIANT`, takes minimum
- `private fun parseAmountSafe(s: String): BigDecimal?` — safe parsing with range guard (0 < price < 9999)

### StructuralValidator (`parser/validation/StructuralValidator.kt`)
- `class StructuralValidator(mode: ParsingMode)`
- `fun validate(ast: MenuAST): ValidationReport`
- Validation rules implemented:
  - **V1_NO_SECTIONS** (ERROR) — no sections and no orphans
  - **V2_NO_ITEMS** (ERROR) — total item count is 0
  - **V3_TOO_MANY_ITEMS** (WARNING) — >500 items (noise indicator)
  - **V4_ZERO_PRICE** (WARNING) — zero/negative price
  - **V5_HIGH_PRICE** (WARNING) — price > €500
  - **V6_CROSS_SECTION_DUPES** (WARNING) — same name in 3+ sections
  - **V8_LONG_NAME** (WARNING) — name >80 chars or >12 words
- `data class ValidationReport(issues, isValid, canProceed)` — `canProceed = true` unless ERRORs AND mode != AGGRESSIVE
- `data class ValidationIssue(severity, code, message, affectedSection?, affectedItem?)`
- `enum class Severity { ERROR, WARNING, INFO }`

### ConfidenceEngine (`parser/confidence/ConfidenceEngine.kt`)
- `class ConfidenceEngine`
- `fun score(ast: MenuAST, totalLines: Int, noiseLines: Int): MenuAST` — returns AST with populated `ASTConfidence`
- Item scoring formula: `0.35*cName + 0.30*cPrice + 0.25*cContext + 0.10*cLength`
  - `cName`: 1.0 (3-60 chars, 1-8 words) → 0.7 (3-80 chars, 1-12 words) → 0.3
  - `cPrice`: 1.0 (INLINE/DOT_LEADER) → 0.9 (NEXT_LINE_STANDALONE) → 0.7 (COLUMN_ALIGNMENT) → 0.0 (missing)
  - `cContext`: 1.0 (header conf ≥ 0.7) → 0.8 (≥ 0.6) → 0.5 → 0.3 (< 0.3)
  - `cLength`: 1.0 (2-8 words) → 0.7 (1 or 9-12) → 0.3
- Section scoring: `0.40 * header.confidence + 0.45 * avg(item.confidence) + 0.15 * sigmoid(itemCount/5)`
- Overall scoring: `0.35*cStructure + 0.25*cPriceCov + 0.25*cNoise + 0.15*cSize`
- Populates `ASTConfidence(overall, structureScore, priceScore, categoryScore, noiseScore, breakdown)`
- Overall < 0.5 triggers legacy fallback (D-15 decision gate)

## Acceptance Criteria Verification

| Criterion | Status |
|-----------|--------|
| `class RepairEngine` present | ✅ |
| `fun repair` returning `Pair<MenuAST, List<RepairEvent>>` | ✅ |
| `applyOrphanAdoption`, `applyDuplicateNameMerge`, `applyNoiseSectionElimination` | ✅ |
| `data class RepairEvent` | ✅ |
| `object RepairHeuristics` | ✅ |
| `extractPriceFromDescription` | ✅ |
| `resolveVariantPrice` | ✅ |
| `RegexRegistry.PRICE_IN_TEXT` usage | ✅ |
| `class StructuralValidator` | ✅ |
| `fun validate` | ✅ |
| `V1_NO_SECTIONS`, `V2_NO_ITEMS` (and V3-V8) | ✅ |
| `data class ValidationReport` | ✅ |
| `enum class Severity` | ✅ |
| `class ConfidenceEngine` | ✅ |
| `fun score` | ✅ |
| `scoreItem` | ✅ |
| `cStructure`, `cPriceCov`, `cNoise` formula | ✅ |
| `ASTConfidence` populated | ✅ |

## Deviations from Plan

### Deviation 1 — Files were untracked (not previously committed)

- **Found during:** Initial git status check
- **Issue:** Prior agent created the files but did not commit them; they were untracked in the submodule
- **Fix:** Staged and committed all 4 files as part of this verification pass
- **Commit:** `d12b0de`

No other deviations — plan executed exactly as designed.

## Known Stubs

None. All functions contain real logic with no placeholder returns or hardcoded empty values.

## Threat Flags

None. These files are pure in-memory AST transformers with no network access, file I/O, or trust boundary crossings.

## Self-Check: PASSED

- `RepairEngine.kt` — exists and contains `class RepairEngine`, `fun repair`, all repair passes ✅
- `RepairHeuristics.kt` — exists and contains `object RepairHeuristics`, `extractPriceFromDescription`, `resolveVariantPrice` ✅
- `StructuralValidator.kt` — exists and contains `class StructuralValidator`, `fun validate`, V1-V8 codes, `ValidationReport`, `Severity` ✅
- `ConfidenceEngine.kt` — exists and contains `class ConfidenceEngine`, `fun score`, `scoreItem`, all formula components, `ASTConfidence` ✅
- Commit `d12b0de` — verified via `git rev-parse --short HEAD` ✅
