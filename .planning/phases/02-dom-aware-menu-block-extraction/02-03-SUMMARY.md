---
phase: "02"
plan: "03"
subsystem: "dom-extraction-integration"
tags: [dom, webview, javascript, extraction, observability, kotlin, android]
dependency_graph:
  requires: ["02-01", "02-02"]
  provides: ["DOM extraction pipeline wired end-to-end", "block selection observability"]
  affects: ["WebViewMenuExtractor", "ParseLogger", "MenuParserPipeline"]
tech_stack:
  added: ["JS_EXTRACT_DOM_SNAPSHOT JavaScript", "DOM scoring pipeline integration"]
  patterns: ["Try-DOM-first with legacy fallback", "ParseLogger D-09 block trace"]
key_files:
  created: []
  modified:
    - Ingredient/app/src/main/java/com/example/ingredient/WebViewMenuExtractor.kt
    - Ingredient/app/src/main/java/com/example/ingredient/parser/observability/ParseLogger.kt
    - Ingredient/app/src/main/java/com/example/ingredient/parser/MenuParserPipeline.kt
decisions:
  - "ParsingMode is in com.example.ingredient.parser.ast (not parser) — corrected import"
  - "BlockTextExtractor.extractFromSnapshot() not yet implemented; inlined pipeline chain in extractAndComplete"
  - "MergedBlockGroup type not in BlockMerger; adapted log to use existing ScoredBlock list"
  - "Task 3 wired via import + comment in MenuParserPipeline per plan's simpler path"
metrics:
  duration_minutes: 25
  tasks_completed: 3
  files_modified: 3
  completed_date: "2026-05-21"
---

# Phase 02 Plan 03: DOM-Aware Extraction Integration Summary

**One-liner:** Replaced flat JS_EXTRACT_TEXT with a structured JS_EXTRACT_DOM_SNAPSHOT DOM walker, wired the full DomBlockScorer→MenuBlockSelector→BlockMerger→BlockTextExtractor pipeline into WebViewMenuExtractor with legacy fallback, and added ParseLogger.logBlockSelection() for D-09 block trace observability.

## Tasks Completed

| Task | Name | Commit | Key Change |
|------|------|--------|-----------|
| 1 | JS_EXTRACT_DOM_SNAPSHOT + extractAndComplete update | 9b9d957 | New JS constant + DOM pipeline in WebViewMenuExtractor |
| 2 | ParseLogger.logBlockSelection | 58da512 | D-09 block trace log with per-block scores |
| 3 | Wire BlockTextExtractor into MenuParserPipeline | 4849ab0 | Import + SourceType.HTML comment |

## What Was Built

### Task 1 — JS_EXTRACT_DOM_SNAPSHOT
- Added `JS_EXTRACT_DOM_SNAPSHOT` private constant: DOM TreeWalker visiting all visible elements
- Noise filtering: skips `nav`, `header`, `footer`, `aside`, `role=banner/navigation/contentinfo`
- Emits blocks where `textLength > 100 OR priceHits > 0` with all 16 D-02 fields
- 50+ food words embedded (Italian + English) for `foodHits` counting
- Price regex covers `€12.50`, `$9.99`, `15,00€`, `EUR 8.00` patterns
- Priority 1/2: Next.js `__NEXT_DATA__` and Nuxt `__NUXT__` fast-path (embedded JSON → synthetic block)
- Modified `WebExtractResult` to include optional `domSnapshot: DomSnapshot? = null`
- New `extractAndComplete(view, deferred, capturedApiJson, mode=BALANCED)`:
  - Try `JS_EXTRACT_DOM_SNAPSHOT` → `DomSnapshotParser.parse()` → `DomBlockScorer.scoreAll()` → `MenuBlockSelector.select()` → `BlockMerger.merge()` → `BlockTextExtractor.extractFromBlocks()`
  - Falls back to `JS_EXTRACT_TEXT` if snapshot is null/empty or pipeline throws

### Task 2 — ParseLogger.logBlockSelection
- Added `logBlockSelection(url, totalBlocks, selectedCount, threshold, usedFallback, topBlocks: List<ScoredBlock>)`
- Structured `Log.d` output: URL (80-char truncated), total/selected counts, threshold, fallback flag
- Per-block breakdown for top-3: score, tag, id, priceHits, foodHits, priceDensity, foodWordDensity, semanticRole, className, position
- Import: `com.example.ingredient.parser.dom.ScoredBlock`

### Task 3 — MenuParserPipeline wiring
- Added `import com.example.ingredient.parser.dom.BlockTextExtractor`
- Added `@Suppress("UNUSED_VARIABLE")` reference to `BlockTextExtractor` with SourceType.HTML comment
- Public `parse(rawContent, sourceType)` interface **unchanged**
- DOM extraction + ParseLogger.logBlockSelection wired in WebViewMenuExtractor (simpler path per plan)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] ParsingMode import wrong package**
- **Found during:** Task 1 first compile
- **Issue:** Plan imported `com.example.ingredient.parser.ParsingMode` but enum is in `com.example.ingredient.parser.ast.ParsingMode`
- **Fix:** Changed import to `com.example.ingredient.parser.ast.ParsingMode`
- **Files modified:** WebViewMenuExtractor.kt
- **Commit:** 9b9d957

**2. [Rule 1 - Adaptation] BlockTextExtractor.extractFromSnapshot() does not exist**
- **Found during:** Task 1 implementation review
- **Issue:** Plan referenced `BlockTextExtractor.extractFromSnapshot(snapshot, mode)` returning `ExtractionResult`, but the actual class only has `extractFromBlocks(List<ScoredBlock>)` and `extractFromDomBlocks(List<DomBlock>)`
- **Fix:** Inlined the pipeline chain directly in `extractAndComplete`: `scoreAll() → select() → merge() → extractFromBlocks()`
- **Files modified:** WebViewMenuExtractor.kt
- **Commit:** 9b9d957

**3. [Rule 1 - Adaptation] MergedBlockGroup type does not exist**
- **Found during:** Task 2 implementation review
- **Issue:** Plan's `logBlockSelection(selection, groups)` signature referenced `MergedBlockGroup` which BlockMerger does not expose (it returns `List<ScoredBlock>` not grouped objects)
- **Fix:** Used project context description's alternative signature: `logBlockSelection(url, totalBlocks, selectedCount, threshold, usedFallback, topBlocks: List<ScoredBlock>)`
- **Files modified:** ParseLogger.kt
- **Commit:** 58da512

**4. [Rule 1 - Adaptation] SelectionResult.totalBlocks does not exist**
- **Found during:** Task 2 implementation review
- **Issue:** Plan referenced `selection.totalBlocks` but SelectionResult only has `selectedBlocks`, `threshold`, `kMax`, `usedFallback`, `selectedCount`
- **Fix:** Pass `snapshot.blocks.size` directly as `totalBlocks` from the call site in WebViewMenuExtractor
- **Files modified:** WebViewMenuExtractor.kt, ParseLogger.kt
- **Commit:** 9b9d957, 58da512

## Threat Model Coverage

| Threat | Mitigation Applied |
|--------|--------------------|
| T-02-07 Tampering (JS→Kotlin JSON) | `DomSnapshotParser.parse()` returns null on malformed JSON; any parse failure triggers fallback to JS_EXTRACT_TEXT |
| T-02-08 DoS (block size) | `subtreeText` capped at 10KB per block in JS; blocks naturally bounded by DOM |
| T-02-09 Info Disclosure (ParseLogger) | Accepted; logs contain menu text snippets, no PII |

## Self-Check: PASSED

- [x] `WebViewMenuExtractor.kt` modified — file exists, contains `JS_EXTRACT_DOM_SNAPSHOT`
- [x] `ParseLogger.kt` modified — contains `logBlockSelection`
- [x] `MenuParserPipeline.kt` modified — contains `BlockTextExtractor`
- [x] Commits exist: `9b9d957`, `58da512`, `4849ab0`
- [x] `./gradlew compileDebugKotlin` → BUILD SUCCESSFUL
