---
phase: "02"
title: DOM-Aware Menu Block Extraction
status: planned
depends_on: "01-deterministic-menu-parser"
date: 2026-05-20
---

# Phase 02: DOM-Aware Menu Block Extraction

## Goal
Replace the flat `JS_EXTRACT_TEXT` string extraction with a hybrid JS+Kotlin
DOM segmentation pipeline that preserves semantic structure, scores candidate
blocks, and feeds only menu-relevant content to the existing `MenuParserPipeline`.

## Problem Statement
`WebViewMenuExtractor.JS_EXTRACT_TEXT` returns a `String` to Kotlin, discarding
all DOM hierarchy, element semantics, computed layout, and density signals.
Result: navbars / footers / reviews / booking widgets contaminate parser input.

## Architecture

```
WebView (JS side)
  ├── JS_EXPAND_AND_SCROLL  [existing — keep]
  ├── JS_NETWORK_INTERCEPTOR [existing — keep]
  └── JS_EXTRACT_DOM_SNAPSHOT [NEW — replaces JS_EXTRACT_TEXT]
         ↓ DomSnapshot JSON
Kotlin side
  ├── DomSnapshotParser    → DomSnapshot
  ├── DomBlockScorer       → scored DomBlock[]
  ├── MenuBlockClassifier  → classified DomBlock[]
  ├── MenuBlockSelector    → top-K blocks (K=6-8, recall-first)
  ├── BlockMerger          → merge adjacent candidates
  ├── BlockTextExtractor   → ordered text from winning blocks
  └── MenuParserPipeline   [existing — feeds from here]
```

## New Files

| File | Purpose |
|------|---------|
| `parser/dom/DomBlock.kt` | Data class for DOM block metadata |
| `parser/dom/DomSnapshot.kt` | Wrapper: blocks[] + url + extractedAt |
| `parser/dom/DomSnapshotParser.kt` | JSON → DomSnapshot (Gson) |
| `parser/dom/DomBlockScorer.kt` | Deterministic scoring (5-signal formula) |
| `parser/dom/MenuBlockClassifier.kt` | MENU / NOISE / AMBIGUOUS classification |
| `parser/dom/MenuBlockSelector.kt` | Top-K selection + threshold (0.25) |
| `parser/dom/BlockMerger.kt` | Merge adjacent candidate blocks |
| `parser/dom/BlockTextExtractor.kt` | Extract ordered text from selected blocks |

## Modified Files

| File | Change |
|------|--------|
| `WebViewMenuExtractor.kt` | Replace `JS_EXTRACT_TEXT` with `JS_EXTRACT_DOM_SNAPSHOT` |
| `MenuParserPipeline.kt` | Wire `BlockTextExtractor` output as text input |

## Scoring Formula (DomBlockScorer)

```
score = 0.30 × priceDensity
      + 0.25 × foodWordDensity
      + 0.20 × semanticRoleScore
      + 0.15 × classNameScore
      + 0.10 × positionScore
```

Where:
- `priceDensity = priceHits / (textLength / 1000).coerceAtLeast(1f)`
- `foodWordDensity = foodHits / (textLength / 500).coerceAtLeast(1f)`
- `semanticRoleScore`: 1.0 if role=menu/schemaType=Menu, 0.7 if data-menu attr, 0.3 if aria-label contains "menu"
- `classNameScore`: regex `menu|piatto|dish|categ|food|item|carta` on classes+id
- `positionScore`: 1.0 if rect.y > 200px AND depth 3-6, 0.5 otherwise

## Block Selection Strategy (Recall-First)

- Threshold: `score >= 0.25` (generous — coverage > precision)
- K max: 8 blocks
- Adjacent merge: if two candidate blocks are siblings or depth gap <= 2
- Fallback: if 0 blocks pass threshold → lower to 0.15, accept top-3

## JS DOM Snapshot Contract

`JS_EXTRACT_DOM_SNAPSHOT` returns JSON:
```json
{
  "blocks": [{
    "index": 0, "tag": "section", "id": "menu",
    "classes": ["menu-section"], "depth": 3,
    "childCount": 24, "textLength": 1840,
    "directText": "Antipasti", "subtreeText": "...",
    "priceHits": 8, "foodHits": 12, "visible": true,
    "rect": {"x": 0, "y": 420, "width": 375, "height": 2400},
    "semanticRole": "menu", "schemaType": "Menu",
    "xpath": "/html/body/main/section[2]"
  }],
  "url": "https://...",
  "extractedAt": 1716235200000
}
```

JS traversal criteria:
- Visit every visible element (skip display:none, visibility:hidden)
- Emit block for elements with: textLength > 100 OR priceHits > 0
- Pre-count priceHits via regex in subtree text
- Pre-count foodHits via food word list (50 Italian + 50 English words)
- Skip known noise: nav, header, footer, role=banner, role=navigation

## Test Strategy

1. Unit tests: DomBlockScorerTest with 3 fixture JSON snapshots
2. Integration tests: end-to-end from JSON fixture → List<MenuCategory>
3. Regression corpus: 10 real restaurant URLs, expected block selection
4. Noise rejection tests: verify navbar/footer never selected

## Success Criteria

- [ ] Zero navbars/footers in extracted text (verify with known-bad URLs)
- [ ] >= 85% dish recall on regression corpus vs current pipeline
- [ ] Block selection latency < 50ms (Kotlin scoring, not JS)
- [ ] JS snapshot extraction adds < 500ms to existing WebView settle time
- [ ] All existing MenuParserPipeline tests still pass (no regression)
- [ ] ParseLogger emits block selection trace with scores

## Parsing Mode Integration

DOM block threshold adjusts per ParsingMode:
- STRICT: score >= 0.45, K=4
- BALANCED: score >= 0.25, K=6 (default)
- AGGRESSIVE: score >= 0.15, K=8
