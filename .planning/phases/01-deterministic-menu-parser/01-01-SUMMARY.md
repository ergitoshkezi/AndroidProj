---
phase: 01-deterministic-menu-parser
plan: "01"
subsystem: parser-foundation
tags: [kotlin, parser, lexer, ast, regex, locale]
dependency_graph:
  requires: []
  provides:
    - TypedLine sealed class (lexer token system)
    - RegexRegistry singleton (compiled regex patterns)
    - LocalePackRegistry (IT/EN locale support)
    - MenuAST data model (full AST hierarchy)
  affects:
    - LineClassifier (consumes TypedLine + RegexRegistry)
    - GrammarParser (consumes MenuAST types)
    - LocaleDetector (uses LocalePackRegistry.detect)
tech_stack:
  added: []
  patterns:
    - Sealed class token hierarchy for lexer output
    - Singleton object for pre-compiled regex (performance)
    - Enum with computed properties for threshold dispatch (ParsingMode)
    - BigDecimal for price amounts (precision)
key_files:
  created: []
  modified:
    - Ingredient/app/src/main/java/com/example/ingredient/parser/lexer/TypedLine.kt
    - Ingredient/app/src/main/java/com/example/ingredient/parser/regex/RegexRegistry.kt
    - Ingredient/app/src/main/java/com/example/ingredient/parser/locale/LocalePackRegistry.kt
    - Ingredient/app/src/main/java/com/example/ingredient/parser/ast/MenuAST.kt
decisions:
  - "ParsingMode enum carries itemConfidenceFloor/headerConfidenceFloor as computed properties (STRICT=0.65, BALANCED=0.45, AGGRESSIVE=0.25)"
  - "PriceToken uses BigDecimal for amount to avoid floating-point precision loss"
  - "LocalePackRegistry.detect() uses keyword frequency count (Italian wins ties)"
  - "TypedLine.Ambiguous holds a candidates list enabling downstream resolution without re-parsing"
metrics:
  duration: "pre-implemented"
  completed: "2025-01-01"
  tasks_completed: 4
  files_modified: 4
---

# Phase 01 Plan 01: Foundation Types and Utilities Summary

**One-liner:** Sealed TypedLine token system, pre-compiled RegexRegistry (22 patterns), IT/EN LocalePackRegistry with locale detection, and MenuAST data model with ParsingMode threshold dispatch — full data model layer enabling deterministic menu parser pipeline.

## Tasks Completed

| # | Task | Status | Notes |
|---|------|--------|-------|
| 1 | Create TypedLine sealed class | ✅ | 8 subtypes + 5 supporting enums |
| 2 | Create RegexRegistry singleton | ✅ | 22 compiled patterns: PRICE_*(6), HEADER_*(3), NOISE_*(7), ALLERGEN_*(1), DIVIDER_*(4), OCR_*(1) |
| 3 | Create LocalePackRegistry IT/EN | ✅ | 50+ IT keywords, 40+ EN keywords, allergen maps, detect() |
| 4 | Create MenuAST data model | ✅ | Full AST hierarchy, ParsingMode enum, SourceType enum, TraceEvent sealed class |

## Verification Results

All 19 acceptance criteria passed:

### TypedLine.kt
- ✅ `sealed class TypedLine` with abstract `raw`, `lineIndex`, `confidence`
- ✅ 8 subtypes: `CategoryHeader`, `DishCandidate`, `PriceLine`, `DescriptionLine`, `AllergenLine`, `SectionDivider`, `Noise`, `Ambiguous`
- ✅ `enum class HeaderIndicator` (10 indicators: ALL_CAPS, BOLD_MARKDOWN, HASH_PREFIX, SURROUNDED_BY_DASHES, KNOWN_CATEGORY_KEYWORD, SHORT_STANDALONE_LINE, FOLLOWED_BY_DISHES, PRECEDED_BY_DIVIDER, COLON_SUFFIX, SURROUNDED_BY_WHITESPACE)
- ✅ `enum class NoiseReason` (11 reasons: COOKIE_BANNER, NAVIGATION_LINK, SOCIAL_MEDIA, PHONE_NUMBER, ADDRESS, WEBSITE_FOOTER, OPENING_HOURS, LEGAL_TEXT, LONG_PROMOTIONAL_TEXT, HTML_ARTIFACT, PAGE_NUMBER)
- ✅ `PriceToken` data class with `BigDecimal amount`, `currency`, `raw`, `PricePosition`
- ✅ `DividerStyle` enum (DASHES, EQUALS, STARS, BLANK, MIXED)

### RegexRegistry.kt
- ✅ `object RegexRegistry` — singleton for compile-once performance
- ✅ PRICE patterns: `PRICE_STANDALONE`, `PRICE_INLINE_SUFFIX`, `PRICE_TAB_SEPARATOR`, `PRICE_VARIANT`, `PRICE_IN_TEXT`, `PRICE_DASH_DECIMAL`
- ✅ HEADER patterns: `HEADER_SURROUNDED_DECORATORS`, `HEADER_MARKDOWN`, `HEADER_COLON_SUFFIX`
- ✅ NOISE patterns: `NOISE_URL`, `NOISE_PHONE`, `NOISE_EMAIL`, `NOISE_COOKIE`, `NOISE_SOCIAL`, `NOISE_OPENING_HOURS`, `NOISE_PAGE_NUMBER`
- ✅ `ALLERGEN_LABELED` pattern with case-insensitive flag
- ✅ DIVIDER patterns: `DIVIDER_DASHES`, `DIVIDER_EQUALS`, `DIVIDER_STARS`, `DIVIDER_BLANK`
- ✅ `OCR_LINE_CONTINUATION` for Italian preposition continuations

### LocalePackRegistry.kt
- ✅ `data class LocalePack` with all 8 properties
- ✅ `val ITALIAN = LocalePack(locale = "it", ...)` — 50+ Italian category keywords, EUR/comma decimal
- ✅ `val ENGLISH = LocalePack(locale = "en", ...)` — 40+ English category keywords, GBP/USD/dot decimal
- ✅ `fun detect(text: String): LocalePack` — keyword frequency heuristic (Italian wins ties)
- ✅ Allergen maps for both locales with standard code symbols (G, L, U, PE, MO, CR, AR, NO, SO, SE, SN, SA, LU, SO2)

### MenuAST.kt
- ✅ `data class MenuAST` with sections, orphanedItems, metadata, trace, confidence
- ✅ `data class ASTSection` (id, header, subsections, items, lineRange, confidence)
- ✅ `data class ASTCategoryHeader` (text, rawLine, lineIndex, indicators, confidence)
- ✅ `data class ASTMenuItem` with `enrichmentAllowed: Boolean` (per D-12)
- ✅ `data class ASTPrice` with `BigDecimal amount`, currency, raw, method
- ✅ `data class ASTDescription` with bindingMethod
- ✅ `data class ParseMetadata` (sourceType, detectedLocale, lineCount, parsingMode, timestamp)
- ✅ `data class ParseTrace` (events, classificationBreakdown, stateTransitions)
- ✅ `sealed class TraceEvent` (DishCommitted, SectionCommitted, RepairApplied, AmbiguousResolved, FallbackTriggered)
- ✅ `data class ASTConfidence` (overall, structureScore, priceScore, categoryScore, noiseScore, breakdown)
- ✅ `enum class PriceAssociation` (CONFIRMED, INFERRED, MISSING, AMBIGUOUS)
- ✅ `enum class PriceAssociationMethod` (INLINE_SAME_LINE, DOT_LEADER, TAB_SEPARATOR, NEXT_LINE_STANDALONE, COLUMN_ALIGNMENT)
- ✅ `enum class DescriptionBindingMethod` (INDENTATION, CONTINUATION_HEURISTIC, EXPLICIT_SEPARATOR)
- ✅ `enum class SourceType` (HTML, PDF, OCR, RAW_TEXT, QR_DIRECT)
- ✅ `enum class ParsingMode` with computed threshold properties:
  - `STRICT`: itemConfidenceFloor=0.65f, headerConfidenceFloor=0.65f
  - `BALANCED`: itemConfidenceFloor=0.45f, headerConfidenceFloor=0.50f
  - `AGGRESSIVE`: itemConfidenceFloor=0.25f, headerConfidenceFloor=0.30f

## Locked Thresholds Verified

| Constant | Value | Status |
|----------|-------|--------|
| `ParsingMode.BALANCED.itemConfidenceFloor` | `0.45f` | ✅ Matches plan spec |
| `minDishConfidence` | `0.35f` | Downstream (LineClassifier) — not in this plan's files |
| `minCategoryConfidence` | `0.40f` | Downstream (LineClassifier) — not in this plan's files |
| `ENRICH_THRESHOLD` | `0.65f` | Downstream (EnrichmentGate) — not in this plan's files |

## Deviations from Plan

None — plan executed exactly as written. All files were pre-implemented and verified complete against every acceptance criterion.

## Known Stubs

None. These are pure data model / utility files. No UI rendering or data source wiring involved.

## Threat Flags

None. These files define internal data structures only — no network endpoints, auth paths, file access, or external trust boundaries introduced.

## Self-Check: PASSED

| File | Exists | Key Types |
|------|--------|-----------|
| `parser/lexer/TypedLine.kt` | ✅ | sealed class TypedLine (8 subtypes) |
| `parser/regex/RegexRegistry.kt` | ✅ | object RegexRegistry (22 patterns) |
| `parser/locale/LocalePackRegistry.kt` | ✅ | object LocalePackRegistry (IT+EN+detect) |
| `parser/ast/MenuAST.kt` | ✅ | data class MenuAST + full hierarchy |
