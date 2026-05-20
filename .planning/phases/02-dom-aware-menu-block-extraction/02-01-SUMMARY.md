---
phase: "02"
plan: "01"
subsystem: "parser/dom"
tags: [dom-extraction, scoring, data-classes, gson, kotlin]
dependency_graph:
  requires: []
  provides: [DomBlock, DomSnapshot, DomSnapshotParser, DomBlockScorer, ScoredBlock, ScoreBreakdown, BlockRect]
  affects: []
tech_stack:
  added: []
  patterns: [object-singleton, data-class, gson-deserialization, weighted-scoring]
key_files:
  created:
    - Ingredient/app/src/main/java/com/example/ingredient/parser/dom/DomBlock.kt
    - Ingredient/app/src/main/java/com/example/ingredient/parser/dom/DomSnapshot.kt
    - Ingredient/app/src/main/java/com/example/ingredient/parser/dom/DomSnapshotParser.kt
    - Ingredient/app/src/main/java/com/example/ingredient/parser/dom/DomBlockScorer.kt
  modified: []
decisions:
  - "DomBlock.id and semanticRole/schemaType typed as String? (nullable) to handle DOM elements without id/role"
  - "DomSnapshotParser object singleton handles evaluateJavascript escape sequences inline"
  - "ScoreBreakdown field named foodWordDensity (not foodDensity) for consistency with D-03 spec"
  - "scoreAll() added to DomBlockScorer for convenience (sorts descending by score)"
metrics:
  duration_minutes: 8
  completed_date: "2026-05-21"
  tasks_completed: 4
  tasks_total: 4
  files_created: 4
  files_modified: 0
---

# Phase 02 Plan 01: DOM Foundation Data Classes and Scoring Engine Summary

**One-liner:** 4-file `parser/dom` foundation: typed DomBlock/DomSnapshot data classes, null-safe Gson parser, and deterministic 5-signal D-03 scorer with per-signal breakdown.

## What Was Built

Created the `com.example.ingredient.parser.dom` package with the 4 foundation files required for Phase 02's DOM-aware menu block extraction pipeline:

| File | Purpose |
|------|---------|
| `DomBlock.kt` | 16-field data class per D-02 JSON contract + `BlockRect` |
| `DomSnapshot.kt` | Envelope wrapper: `blocks[]`, `url`, `extractedAt` |
| `DomSnapshotParser.kt` | `object` singleton; Gson-based; null-safe; handles JS escape sequences |
| `DomBlockScorer.kt` | D-03 locked 5-signal formula; `score()`, `scoreWithBreakdown()`, `scoreAll()` |

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1+2  | `dc1c1dd` | feat(02-01): add DomBlock and DomSnapshot data classes |
| 3    | `83bff3d` | feat(02-01): add DomSnapshotParser (Gson-based JSON deserialization) |
| 4    | `a6e59ff` | feat(02-01): add DomBlockScorer with D-03 5-signal weighted formula |

## Implementation Details

### DomBlock (Task 1)
All 16 fields from D-02 JSON contract. `id`, `semanticRole`, and `schemaType` typed as `String?` (nullable) because DOM elements may lack `id` attribute or ARIA role. `BlockRect` co-located in same file.

### DomSnapshot (Task 2)
Minimal envelope. `extractedAt` is `Long` (Unix ms timestamp from JavaScript `Date.now()`).

### DomSnapshotParser (Task 3)
- `object` singleton (Gson instance created once)
- `parse(json)` → `DomSnapshot?` — null on any failure (T-02-01 mitigation)
- `parseWithError(json)` → `Pair<DomSnapshot?, String?>` — error message for ParseLogger
- Strips outer quotes + unescapes `\"`, `\n`, `\/` from `evaluateJavascript` callback format

### DomBlockScorer (Task 4)
Exact D-03 weights (LOCKED):
```
score = 0.30 × priceDensity
      + 0.25 × foodWordDensity
      + 0.20 × semanticRoleScore
      + 0.15 × classNameScore
      + 0.10 × positionScore
```
- `priceDensity`: `(priceHits / (textLength/1000f).coerceAtLeast(1f)).coerceAtMost(1f)`
- `foodWordDensity`: `(foodHits / (textLength/500f).coerceAtLeast(1f)).coerceAtMost(1f)`
- `semanticRoleScore`: 1.0f (schemaType=Menu or semanticRole=menu), 0.7f (data-menu), 0.3f (class hint), 0.0f
- `classNameScore`: regex `menu|piatto|dish|categ|food|item|carta` (case-insensitive)
- `positionScore` (binary): 1.0f if `rect.y > 200f && depth in 3..6`, else 0.5f

## Verification

```
BUILD SUCCESSFUL in 22s
```
`./gradlew compileDebugKotlin` — all 4 files compile without errors.

## Deviations from Plan

None — plan executed exactly as written. Minor additions:
- `scoreAll()` added to `DomBlockScorer` (sorts list descending) — convenience method implied by the interface spec, not extra scope
- `ScoreBreakdown.foodWordDensity` field name matches D-03 spec exactly (plan showed `foodDensity` as internal variable name)

## Known Stubs

None. All files are pure logic/data — no UI rendering, no hardcoded placeholders, no TODO markers.

## Threat Flags

No new threat surface beyond what is documented in the plan's threat model (T-02-01, T-02-02, T-02-03). `DomSnapshotParser` null-safety satisfies T-02-01 mitigation.

## Self-Check: PASSED

- ✅ `DomBlock.kt` exists with 16 fields
- ✅ `DomSnapshot.kt` exists with `blocks`, `url`, `extractedAt`
- ✅ `DomSnapshotParser.kt` exists with `parse()` and `parseWithError()`
- ✅ `DomBlockScorer.kt` exists with `score()`, `scoreWithBreakdown()`, `scoreAll()`
- ✅ Commit `dc1c1dd` (DomBlock + DomSnapshot)
- ✅ Commit `83bff3d` (DomSnapshotParser)
- ✅ Commit `a6e59ff` (DomBlockScorer)
- ✅ BUILD SUCCESSFUL
