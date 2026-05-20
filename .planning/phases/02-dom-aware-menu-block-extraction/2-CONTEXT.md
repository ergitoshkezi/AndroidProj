# Phase 02: DOM-Aware Menu Block Extraction — Context

**Gathered:** 2026-05-20
**Status:** Ready for planning
**Source:** PRD Express Path (PHASE.md + dom-segmentation-architecture.md)

<domain>
## Phase Boundary

Replace `WebViewMenuExtractor.JS_EXTRACT_TEXT` (flat string) with a hybrid JS+Kotlin
DOM segmentation pipeline. JS extracts a structured `DomSnapshot` JSON; Kotlin scores,
classifies, selects, merges, and extracts text from only the menu-relevant DOM blocks
before feeding text into the existing `MenuParserPipeline`.

**In scope:**
- 8 new Kotlin files in `parser/dom/` package
- `WebViewMenuExtractor.kt` — replace `JS_EXTRACT_TEXT` with `JS_EXTRACT_DOM_SNAPSHOT`
- `MenuParserPipeline.kt` — wire `BlockTextExtractor` output as text input
- Unit + integration + regression tests for DOM layer

**Out of scope:**
- Changes to `MenuGrammarParser`, `ConfidenceEngine`, `RepairEngine` (Phase 01 — frozen)
- OCR pipeline (separate source type)
- New LLM calls (Phase 02 is fully deterministic, no LLM)
- PDF extraction changes

</domain>

<decisions>
## Implementation Decisions

### D-01: Hybrid JS+Kotlin split (LOCKED)
- JS side: DOM traversal, computed style access, bounding rect, pre-count priceHits/foodHits, emit DomBlock[] metadata
- Kotlin side: ALL scoring, classification, selection, merging, text extraction
- Rationale: Kotlin code is testable, observable, debuggable; JS only does what requires browser context

### D-02: DomSnapshot JSON contract (LOCKED)
Each block contains: `index`, `tag`, `id`, `classes[]`, `depth`, `childCount`, `textLength`,
`directText`, `subtreeText`, `priceHits`, `foodHits`, `visible`, `rect{x,y,width,height}`,
`semanticRole`, `schemaType`, `xpath`
Envelope: `blocks[]`, `url`, `extractedAt`

### D-03: Scoring formula (LOCKED — 5 signals, weighted)
```
score = 0.30 × priceDensity
      + 0.25 × foodWordDensity
      + 0.20 × semanticRoleScore
      + 0.15 × classNameScore
      + 0.10 × positionScore
```
- `priceDensity = priceHits / (textLength / 1000).coerceAtLeast(1f)`
- `foodWordDensity = foodHits / (textLength / 500).coerceAtLeast(1f)`
- `semanticRoleScore`: 1.0 if role=menu/schemaType=Menu, 0.7 if data-menu attr, 0.3 if aria-label contains "menu"
- `classNameScore`: regex `menu|piatto|dish|categ|food|item|carta` on classes+id
- `positionScore`: 1.0 if rect.y > 200px AND depth 3-6, 0.5 otherwise

### D-04: Recall-first block selection (LOCKED)
- Default threshold: `score >= 0.25` (generous — coverage over precision)
- K max: 8 blocks
- Fallback: if 0 blocks pass → lower to 0.15, accept top-3
- Downstream `MenuGrammarParser` + `ConfidenceEngine` handle noise filtering

### D-05: ParsingMode integration (LOCKED)
- STRICT: threshold >= 0.45, K=4
- BALANCED: threshold >= 0.25, K=6 (default)
- AGGRESSIVE: threshold >= 0.15, K=8

### D-06: JS traversal rules (LOCKED)
- Skip `display:none`, `visibility:hidden` elements
- Skip known noise elements: nav, header, footer, role=banner, role=navigation
- Emit block for: textLength > 100 OR priceHits > 0
- Pre-count foodHits via 50 Italian + 50 English food words embedded in JS

### D-07: Adjacent block merge (LOCKED)
- Merge if two candidate blocks are siblings OR depth gap <= 2
- Preserves document order in merged text

### D-08: No new LLM calls (LOCKED)
- Phase 02 is 100% deterministic
- LLM path is unchanged — still falls back from MenuParserPipeline when confidence < 0.5f

### D-09: ParseLogger integration (LOCKED)
- `ParseLogger` must emit block selection trace with per-block scores
- Required for observability (D-D-17 from Phase 01 architecture)

### Agent's Discretion
- Exact Gson deserialization strategy (data classes vs TypeToken)
- Food word list exact contents (50+50 words)
- Test fixture JSON format for DomBlockScorerTest
- Exact `BlockMerger` merging algorithm details (sibling detection method)
- Whether `DomSnapshotParser` uses `object` or `class` instantiation

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 02 spec
- `.planning/phases/02-dom-aware-menu-block-extraction/PHASE.md` — Full architecture, JS contract, scoring formula, success criteria
- `.planning/notes/dom-segmentation-architecture.md` — All architectural decisions from design session

### Phase 01 (depends on — read-only, do not modify)
- `Ingredient/app/src/main/java/com/example/ingredient/parser/MenuParserPipeline.kt` — Pipeline input interface (feeds BlockTextExtractor output here)
- `Ingredient/app/src/main/java/com/example/ingredient/parser/observability/ParseLogger.kt` — Must use for block selection trace logging

### Modified files
- `Ingredient/app/src/main/java/com/example/ingredient/WebViewMenuExtractor.kt` — Replace JS_EXTRACT_TEXT with JS_EXTRACT_DOM_SNAPSHOT
- `Ingredient/app/build.gradle.kts` — Gson already available (check before adding)

</canonical_refs>

<specifics>
## Specific Ideas

### New package: `parser/dom/`
8 files in `Ingredient/app/src/main/java/com/example/ingredient/parser/dom/`:
1. `DomBlock.kt` — data class (all fields from JSON contract)
2. `DomSnapshot.kt` — data class: blocks: List<DomBlock>, url: String, extractedAt: Long
3. `DomSnapshotParser.kt` — JSON → DomSnapshot (use Gson, already available)
4. `DomBlockScorer.kt` — `fun score(block: DomBlock): Float` (locked formula D-03)
5. `MenuBlockClassifier.kt` — MENU / NOISE / AMBIGUOUS enum + classify()
6. `MenuBlockSelector.kt` — top-K selection with ParsingMode threshold
7. `BlockMerger.kt` — merge adjacent candidates
8. `BlockTextExtractor.kt` — `fun extract(blocks: List<DomBlock>): String`

### JS_EXTRACT_DOM_SNAPSHOT
Replaces `JS_EXTRACT_TEXT` in `WebViewMenuExtractor.kt`.
Must return JSON string parseable by `DomSnapshotParser`.

### Test fixtures needed
- 3 JSON DomSnapshot fixtures (clean menu, noisy page, minimal page)
- `DomBlockScorerTest`: score known blocks, assert thresholds
- Integration test: JSON fixture → List<MenuCategory>
- Noise rejection test: navbar/footer block scores < 0.25

</specifics>

<deferred>
## Deferred Ideas

- Schema.org JSON-LD full parsing (API JSON interception via JS_NETWORK_INTERCEPTOR already handles this)
- Multi-page PDF menu DOM extraction
- Tab/accordion active-state JavaScript interaction beyond JS_EXPAND_AND_SCROLL

</deferred>

---

*Phase: 02-dom-aware-menu-block-extraction*
*Context gathered: 2026-05-20 via PRD Express Path (PHASE.md)*
