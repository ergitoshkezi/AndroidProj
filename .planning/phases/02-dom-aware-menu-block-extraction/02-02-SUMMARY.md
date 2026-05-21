---
phase: "02"
plan: "02"
subsystem: "parser/dom"
tags: [dom, classification, selection, merging, text-extraction, kotlin]
dependency_graph:
  requires: ["02-01"]
  provides: ["MenuBlockClassifier", "MenuBlockSelector", "BlockMerger", "BlockTextExtractor"]
  affects: ["MenuParserPipeline"]
tech_stack:
  added: []
  patterns: ["object singleton", "data class", "sorted by index for document order"]
key_files:
  created:
    - Ingredient/app/src/main/java/com/example/ingredient/parser/dom/MenuBlockClassifier.kt
    - Ingredient/app/src/main/java/com/example/ingredient/parser/dom/MenuBlockSelector.kt
    - Ingredient/app/src/main/java/com/example/ingredient/parser/dom/BlockMerger.kt
    - Ingredient/app/src/main/java/com/example/ingredient/parser/dom/BlockTextExtractor.kt
  modified: []
decisions:
  - "BlockLabel enum uses fixed thresholds: >=0.45 MENU, <0.10 NOISE, else AMBIGUOUS (spec-driven)"
  - "MenuBlockSelector takes pre-scored List<ScoredBlock> not raw DomBlocks — caller decides when to score"
  - "BlockMerger returns List<ScoredBlock> (not a wrapper type) for simpler downstream use"
  - "BlockTextExtractor uses two named overloads (extractFromBlocks / extractFromDomBlocks) to satisfy plan spec and avoid ambiguity"
  - "ParsingMode imported from com.example.ingredient.parser.ast (not parser root — plan had wrong path)"
metrics:
  duration: "~15 min"
  completed: "2025-05-20"
  tasks_completed: 4
  files_created: 4
---

# Phase 02 Plan 02: Classification, Selection, Merging & Text Extraction Summary

**One-liner:** Four Kotlin singletons completing the DOM scoring pipeline — block labeling (MENU/NOISE/AMBIGUOUS), recall-first top-K selection with D-04/D-05 ParsingMode thresholds, D-07 adjacent sibling merging, and document-order text extraction.

## What Was Built

| File | Purpose |
|------|---------|
| `MenuBlockClassifier.kt` | Classifies `ScoredBlock` → `BlockLabel` (MENU/NOISE/AMBIGUOUS) using fixed score thresholds |
| `MenuBlockSelector.kt` | Selects top-K blocks from `List<ScoredBlock>` per `ParsingMode`; D-04 fallback if 0 pass primary |
| `BlockMerger.kt` | Merges consecutive blocks that are siblings or have depth gap ≤ 2 (D-07); collapsed block carries combined `subtreeText` |
| `BlockTextExtractor.kt` | Two overloads: `extractFromBlocks(List<ScoredBlock>)` and `extractFromDomBlocks(List<DomBlock>)`; joins `subtreeText` with `\n\n` in document order |

## Task Commits

| Task | Description | Commit |
|------|-------------|--------|
| 1 | MenuBlockClassifier (MENU/NOISE/AMBIGUOUS labeling) | `714f5d6` |
| 2 | MenuBlockSelector with D-04/D-05 recall-first thresholds | `32ea7bd` |
| 3 | BlockMerger with D-07 adjacent-block merge logic | `5a53eb6` |
| 4 | BlockTextExtractor (extractFromBlocks + extractFromDomBlocks) | `b1175ec` |

## Key API Contracts

### MenuBlockClassifier
```kotlin
object MenuBlockClassifier {
    fun classify(scored: ScoredBlock): ClassifiedBlock        // >= 0.45 → MENU, < 0.10 → NOISE
    fun classifyAll(blocks: List<ScoredBlock>): List<ClassifiedBlock>
}
enum class BlockLabel { MENU, NOISE, AMBIGUOUS }
```

### MenuBlockSelector
```kotlin
object MenuBlockSelector {
    fun select(blocks: List<ScoredBlock>, mode: ParsingMode = BALANCED): SelectionResult
}
data class SelectionResult(selectedBlocks, threshold, kMax, usedFallback)
```
D-05 thresholds: STRICT=0.45/K4 · BALANCED=0.25/K6 · AGGRESSIVE=0.15/K8

### BlockMerger
```kotlin
object BlockMerger {
    fun merge(blocks: List<ScoredBlock>): List<ScoredBlock>   // D-07: siblings OR depthGap<=2
}
```

### BlockTextExtractor
```kotlin
object BlockTextExtractor {
    fun extractFromBlocks(blocks: List<ScoredBlock>): String
    fun extractFromDomBlocks(blocks: List<DomBlock>): String
}
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Wrong ParsingMode import path in plan**
- **Found during:** Task 2
- **Issue:** Plan specified `com.example.ingredient.parser.ParsingMode`; actual location is `com.example.ingredient.parser.ast.ParsingMode`
- **Fix:** Used correct import `com.example.ingredient.parser.ast.ParsingMode`
- **Files modified:** `MenuBlockSelector.kt`
- **Commit:** `32ea7bd`

**2. [Rule 1 - Design simplification] BlockMerger returns `List<ScoredBlock>` instead of `List<MergedBlockGroup>`**
- **Found during:** Task 3
- **Issue:** Plan showed two alternative signatures; the `MergedBlockGroup` wrapper type adds indirection without benefit for downstream callers (BlockTextExtractor, MenuParserPipeline)
- **Fix:** `merge()` returns `List<ScoredBlock>` directly; merged text is stored in the `subtreeText` field of the representative block via `copy()`
- **Files modified:** `BlockMerger.kt`
- **Commit:** `5a53eb6`

## Known Stubs

None — all methods are fully implemented and wire real data.

## Self-Check: PASSED

```
FOUND: MenuBlockClassifier.kt
FOUND: MenuBlockSelector.kt
FOUND: BlockMerger.kt
FOUND: BlockTextExtractor.kt
FOUND commit: 714f5d6
FOUND commit: 32ea7bd
FOUND commit: 5a53eb6
FOUND commit: b1175ec
BUILD SUCCESSFUL
```
