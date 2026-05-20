# Roadmap — Ingredient App

---

## Phase 1: Deterministic Menu Parser v2

**Goal:** Replace the LLM-as-parser approach with a 6-layer deterministic pipeline.
The LLM is used exclusively for dish enrichment (ingredients, allergens, calories),
never for identifying categories or dishes.

**Motivation:** Current pipeline hallucinates categories, misassociates prices,
and produces unstable outputs between runs.

**Success criteria:**
- [ ] Same menu input produces identical output on 3 consecutive runs
- [ ] Zero invented categories or dishes (LLM enricher contract test passes)
- [ ] Price association accuracy > 95% on fixture suite
- [ ] Processing time per menu < 10 seconds (excluding LLM enrichment)
- [ ] All 6 layers have unit test coverage > 80%

**Key deliverables:**
- `MenuTokenizer` — line-level token classification with confidence
- `MenuStateMachineParser` — 5-state deterministic parser
- `MenuAST` — intermediate representation with confidence scores
- `MenuASTValidator` — validation + normalization layer
- `LLMEnricher` — schema-first enrichment with `temperature=0`
- `ConfidenceGate` — threshold-based output filtering
- `PipelineOrchestrator` — replaces `processUrl()` in `QrMenuImportScreen`

**Ref:** `.planning/notes/deterministic-menu-parser-architecture.md`
**Todo:** `.planning/todos/pending/implement-deterministic-parser-v2.md`

**Depends on:** —
**Estimated effort:** ~4–6 sessions

---

## Phase 2: DOM-Aware Menu Block Extraction

**Goal:** Replace flat JS `innerText` extraction with hybrid JS+Kotlin DOM segmentation pipeline that identifies and scores menu-relevant DOM blocks before text extraction.

**Motivation:** Current `JS_EXTRACT_TEXT` extracts all visible text indiscriminately, mixing menu content with navigation, footers, and ads. This noise degrades parser accuracy and wastes LLM tokens.

**Success criteria:**
- [ ] DOM snapshot captures ≥95% of menu blocks from 10 test URLs
- [ ] Noise blocks (nav, footer, sidebar) score below 0.25 threshold
- [ ] Menu blocks score ≥0.45 for STRICT mode selection
- [ ] ParseLogger emits block selection trace for debugging
- [ ] Zero new LLM calls introduced (D-08)
- [ ] All scoring/classification unit tests pass

**Key deliverables:**
- `DomBlock`, `DomSnapshot` — DOM block data classes (D-02 contract)
- `DomSnapshotParser` — JSON→Kotlin parser using Gson
- `DomBlockScorer` — 5-signal scoring formula (D-03 weights)
- `MenuBlockClassifier` — semantic role classification
- `MenuBlockSelector` — threshold-based selection with fallback (D-04, D-05)
- `BlockMerger` — adjacent block merging (D-07)
- `BlockTextExtractor` — selected blocks → concatenated text
- `JS_EXTRACT_DOM_SNAPSHOT` — JavaScript DOM walker (D-06 filtering)
- `ParseLogger.logBlockSelection()` — observability (D-09)

**Plans:** 4 plans

Plans:
- [ ] 02-01-PLAN.md — Foundation data classes (DomBlock, DomSnapshot, DomSnapshotParser, DomBlockScorer)
- [ ] 02-02-PLAN.md — Classification and selection (MenuBlockClassifier, MenuBlockSelector, BlockMerger, BlockTextExtractor)
- [ ] 02-03-PLAN.md — Integration (JS_EXTRACT_DOM_SNAPSHOT, WebViewMenuExtractor wiring, ParseLogger updates)
- [ ] 02-04-PLAN.md — Test suite (JSON fixtures, DomBlockScorerTest, DomPipelineIntegrationTest)

**Ref:** `.planning/phases/02-dom-aware-menu-block-extraction/PHASE.md`
**Context:** `.planning/phases/02-dom-aware-menu-block-extraction/2-CONTEXT.md`

**Depends on:** Phase 1 (MenuParserPipeline must exist to receive extracted text)
**Estimated effort:** ~3–4 sessions
