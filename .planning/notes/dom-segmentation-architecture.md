---
title: DOM Segmentation Architecture Decisions
date: 2026-05-20
context: gsd-explore session — menu parser DOM layer redesign
---

# DOM Segmentation Architecture Decisions

## Problem
`WebViewMenuExtractor` currently flattens DOM too early: `JS_EXTRACT_TEXT` returns a
`String` to Kotlin, losing all semantic structure (element hierarchy, class names,
computed layout, density signals). Kotlin receives dumb text and cannot distinguish
menu blocks from navbars/footers/ads.

## Core Decision: Hybrid JS + Kotlin Intelligence

### JS side responsibilities (things only possible in-browser)
- DOM traversal (every visible element in document order)
- Computed style access → filter `display:none`, `visibility:hidden`
- Bounding rect (`getBoundingClientRect`) → layout density, position signals
- Initial candidate block extraction — produce `DomBlock[]` metadata, NOT scores
- Accordion expansion + scroll (already implemented in `JS_EXPAND_AND_SCROLL`)
- API JSON interception (already implemented in `JS_NETWORK_INTERCEPTOR`)

### Kotlin side responsibilities (testable, observable, debuggable)
- Parse `DomSnapshot` JSON from JS
- Score each `DomBlock` with deterministic formulas
- Classify blocks (MENU_CATEGORY / MENU_ITEM / NOISE / AMBIGUOUS)
- Select top-K blocks (K=6-8, recall-first)
- Merge adjacent candidate blocks
- Extract ordered text from winning blocks only
- Feed cleaned text into existing `MenuParserPipeline`

## JS→Kotlin Contract: DomSnapshot

```json
{
  "blocks": [
    {
      "index": 0,
      "tag": "section",
      "id": "menu-section",
      "classes": ["menu", "tab-content", "active"],
      "depth": 3,
      "childCount": 24,
      "textLength": 1840,
      "directText": "Antipasti",
      "subtreeText": "Antipasti\nBruschetta al pomodoro...",
      "priceHits": 8,
      "foodHits": 12,
      "visible": true,
      "rect": {"x": 0, "y": 420, "width": 375, "height": 2400},
      "semanticRole": "menu",
      "schemaType": "Menu",
      "xpath": "/html/body/main/section[2]"
    }
  ],
  "url": "https://...",
  "extractedAt": 1716235200000
}
```

## Scoring Decision: Recall-First

Priority: **coverage over precision** at block selection stage.
- Block selection threshold: `score >= 0.25` (generous)
- K=6-8 candidate blocks accepted
- Merge adjacent candidates if gap < 2 DOM levels
- Downstream `MenuGrammarParser` + `ConfidenceEngine` handle noise filtering

## Key Scoring Signals (Kotlin side)

| Signal | Weight | Notes |
|--------|--------|-------|
| Price hit density | 0.30 | `priceHits / textLength * 1000` |
| Food word density | 0.25 | Italian + English food lexicon |
| Semantic role | 0.20 | `role=menu`, schema.org `Menu`, `data-menu` attrs |
| Class name match | 0.15 | regex: `menu|piatto|dish|categ|food|item` |
| Depth/position | 0.10 | Main content zone: depth 3-6, y > 200px |

## Migration Impact

- `WebViewMenuExtractor.kt`: replace `JS_EXTRACT_TEXT` with `JS_EXTRACT_DOM_SNAPSHOT`
- New `DomSnapshotParser.kt`: parse JSON → `DomSnapshot` data class
- New `DomBlockScorer.kt`: scoring engine
- New `MenuBlockSelector.kt`: top-K selection + merging
- New `BlockTextExtractor.kt`: ordered text from selected blocks
- `MenuParserPipeline.kt`: wire new extraction before `MenuContentPreprocessor`

## Preserved (No Change)
- `JS_EXPAND_AND_SCROLL` — keep as-is
- `JS_NETWORK_INTERCEPTOR` — keep as-is (API JSON path still highest priority)
- `MenuParserPipeline` — receives text as before, no interface change
- Legacy LLM fallback — still active when confidence < 0.30
