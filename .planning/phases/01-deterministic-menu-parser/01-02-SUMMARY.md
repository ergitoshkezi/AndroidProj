---
phase: 01-deterministic-menu-parser
plan: "02"
subsystem: parser-lexer-grammar
tags: [kotlin, parser, lexer, state-machine, regex, confidence]
dependency_graph:
  requires:
    - TypedLine sealed class (01-01)
    - RegexRegistry singleton (01-01)
    - LocalePackRegistry (01-01)
    - MenuAST data model (01-01)
  provides:
    - LineClassifier (10-rule priority chain, TypedLine tokens from raw text)
    - WindowedContextResolver (±3-line context ambiguity resolution)
    - MenuGrammarParser (6-state machine producing raw MenuAST)
  affects:
    - MenuParserPipeline (consumes all three layers)
    - ConfidenceEngine (receives MenuAST with enrichmentAllowed flags)
    - LLMEnricher (receives enrichmentAllowed=true items from ENRICH_THRESHOLD gating)
tech_stack:
  added: []
  patterns:
    - 10-rule deterministic priority chain for text classification
    - Windowed context resolution (±3 lines) for ambiguous tokens
    - 6-state finite state machine (INIT→HEADER_FOUND→IN_SECTION→DISH_OPEN→DESC_COLLECTING→DONE)
    - ParsingMode enum-dispatched confidence floors (D-13/D-14)
    - ENRICH_THRESHOLD companion constant (0.65f) gating enrichment eligibility
key_files:
  created:
    - Ingredient/app/src/main/java/com/example/ingredient/parser/lexer/LineClassifier.kt
    - Ingredient/app/src/main/java/com/example/ingredient/parser/grammar/WindowedContextResolver.kt
    - Ingredient/app/src/main/java/com/example/ingredient/parser/grammar/MenuGrammarParser.kt
  modified: []
decisions:
  - "10-rule chain order is fixed for determinism: noise→divider→price→allergen→strong-header→dish+price→dish→desc→weak-header→ambiguous"
  - "WindowedContextResolver computes independent dishScore/headerScore from ±3 window; resolves to highest-scoring candidate ≥0.30 threshold"
  - "MenuGrammarParser ENRICH_THRESHOLD=0.65f placed in companion object constant (not ParsingMode) per D-12 spec"
  - "Orphaned items (dishes before first header) collected in orphanedItems list rather than discarded, enabling lenient downstream processing"
  - "ENRICH_THRESHOLD=0.65f locked; minDishConfidence=0.35f enforced at LineClassifier classifyDishCandidate score<0.35 guard; minCategoryConfidence=0.40f enforced via classifyWeakHeader 0.42f floor"
metrics:
  duration: "pre-implemented, verified 2025-01-01"
  completed: "2025-01-01"
  tasks_completed: 3
  files_modified: 3
---

# Phase 01 Plan 02: Lexer and Grammar Parser Pipeline Layers Summary

**One-liner:** Deterministic 10-rule LineClassifier, ±3-line windowed ambiguity resolver, and 6-state grammar parser state machine — complete Layers 1–2 of the menu parsing pipeline producing raw MenuAST with confidence-gated enrichment flags.

## Tasks Completed

| # | Task | Status | Commit | Notes |
|---|------|--------|--------|-------|
| 1 | Create LineClassifier with 10-rule priority chain | ✅ | fe53f66 | All 10 rules, uses RegexRegistry, produces TypedLine |
| 2 | Create WindowedContextResolver for ambiguity resolution | ✅ | fe53f66 | ±3 window, resolveAmbiguous + refineDish + refineHeader |
| 3 | Create MenuGrammarParser state machine | ✅ | fe53f66 | 6 states, ENRICH_THRESHOLD=0.65f, mode confidence floors |

## Verification Results

All acceptance criteria passed:

### Task 1: LineClassifier.kt
- ✅ `class LineClassifier` — 1 match
- ✅ `fun classify(raw: String, lineIndex: Int): TypedLine` — primary entrypoint
- ✅ 10-rule priority chain: `classifyNoise`, `classifyDivider`, `classifyStandalonePrice`, `classifyAllergen`, `classifyStrongHeader`, `classifyDishWithPrice`, `classifyDishCandidate`, `classifyDescription`, `classifyWeakHeader`, fallback `Ambiguous`
- ✅ `RegexRegistry` usage — 19 references (uses compiled patterns, does not define them)
- ✅ `minDishConfidence=0.35f` enforced: `if (score < 0.35f) return null` in `classifyDishCandidate`
- ✅ `minCategoryConfidence=0.40f` effectively enforced: `classifyWeakHeader` returns 0.42f floor, `classifyStrongHeader` requires score ≥ 0.45f

### Task 2: WindowedContextResolver.kt
- ✅ `class WindowedContextResolver(mode: ParsingMode)` — 1 match
- ✅ `fun resolve(tokens: List<TypedLine>): List<TypedLine>` — 1 match
- ✅ `resolveAmbiguous` — examines ±3 window (`idx-3..idx+4`), prev/next non-blank tokens
  - dishScore: prev=CategoryHeader (+0.40), next=PriceLine (+0.50), next=DescriptionLine (+0.30)
  - headerScore: prev=SectionDivider (+0.50), next=DishCandidate (+0.40), window 2+ dishes (+0.35), prevNull (+0.30)
- ✅ `refineDish` — boosts DishCandidate confidence +0.15f if followed by PriceLine within 3 lines
- ✅ `refineHeader` — boosts CategoryHeader confidence +0.20f and adds FOLLOWED_BY_DISHES if 2+ dishes follow in 5 lines

### Task 3: MenuGrammarParser.kt
- ✅ `class MenuGrammarParser(mode: ParsingMode = ParsingMode.BALANCED)` — 1 match
- ✅ `enum class State` — 1 match
- ✅ All 6 states present: `INIT` (3), `HEADER_FOUND` (8), `IN_SECTION` (3), `DISH_OPEN` (6), `DESC_COLLECTING` (3), `DONE` (2)
- ✅ `fun parse(tokens: List<TypedLine>, metadata: ParseMetadata): MenuAST`
- ✅ `ENRICH_THRESHOLD = 0.65f` in companion object
- ✅ `mode.itemConfidenceFloor` — 1 reference in `commitDish()` (D-13 enforcement)
- ✅ `mode.headerConfidenceFloor` — referenced as `minHeaderConf` (D-14 enforcement)
- ✅ Returns `MenuAST` with sections, orphanedItems, metadata, trace (events + classificationBreakdown + stateTransitions), confidence (zeros, scored later by ConfidenceEngine)

## Locked Thresholds Verified

| Constant | Value | Location | Status |
|----------|-------|----------|--------|
| `minDishConfidence` | `0.35f` | `LineClassifier.classifyDishCandidate` | ✅ Enforced |
| `minCategoryConfidence` | `0.40f` | `LineClassifier.classifyWeakHeader` (0.42f) | ✅ Enforced |
| `ENRICH_THRESHOLD` | `0.65f` | `MenuGrammarParser.companion` | ✅ Locked |
| `mode.itemConfidenceFloor` | `BALANCED=0.45f` | `MenuGrammarParser.commitDish` | ✅ D-13 |
| `mode.headerConfidenceFloor` | `BALANCED=0.50f` | `MenuGrammarParser` | ✅ D-14 |

## Deviations from Plan

None — all three files were pre-implemented and fully matched every acceptance criterion. The only action taken was committing the untracked files to git (they existed on disk but had never been staged).

**[Rule 3 - Blocking]** Files existed on disk but were untracked (git status showed `?? app/src/main/java/.../parser/`). Committed the three plan files to git as `feat(01-02): implement lexer and grammar parser pipeline layers` (commit `fe53f66`).

## Known Stubs

None. All three files implement complete logic — no hardcoded empty values, placeholder text, or unconnected data sources. `MenuGrammarParser` returns `ASTConfidence(0f, 0f, ...)` intentionally (scores populated by ConfidenceEngine in a later plan, documented in plan spec).

## Threat Flags

None. These files are internal text-processing pipeline components — no network endpoints, authentication paths, file system access, or external trust boundaries introduced.

## Self-Check: PASSED

| File | Exists | Commit | Key Contracts |
|------|--------|--------|---------------|
| `parser/lexer/LineClassifier.kt` | ✅ | fe53f66 | `class LineClassifier`, `fun classify`, 10 rules, RegexRegistry |
| `parser/grammar/WindowedContextResolver.kt` | ✅ | fe53f66 | `class WindowedContextResolver`, `fun resolve`, resolveAmbiguous/refineDish/refineHeader |
| `parser/grammar/MenuGrammarParser.kt` | ✅ | fe53f66 | `class MenuGrammarParser`, `enum class State` (6 states), ENRICH_THRESHOLD=0.65f, mode confidence floors |
