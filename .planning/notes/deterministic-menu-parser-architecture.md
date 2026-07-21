---
title: "Deterministic Menu Parser v2 — Architecture"
date: 2026-05-20
context: "Exploration session — redesign of QR menu analysis pipeline"
---

# Deterministic Menu Parser v2

## Problem Statement

The current pipeline sends raw text directly to the LLM for parsing,
causing: hallucinated categories/dishes, wrong price associations,
unstable outputs between runs, and description boundary errors.

**Root cause:** LLM used as a parser instead of an enricher.

## Design Principles

- **Deterministic-first:** parsing must be reproducible between runs
- **Recall-first:** capture uncertain items with a confidence score rather than drop them
- **LLM = enrichment only:** never ask the LLM to identify categories or dishes
- **Precision over creativity:** no invented structure

---

## New Pipeline

```
[Input: QR/PDF/HTML/Text]
         │
         ▼
┌─────────────────────┐
│  Layer 0: Normalize │  → clean text with structural metadata
└─────────────────────┘
         │
         ▼
┌─────────────────────┐
│  Layer 1: Denoise   │  → remove banners, hours, addresses, social links
└─────────────────────┘
         │
         ▼
┌─────────────────────┐
│  Layer 2: Tokenize  │  → stream of LineToken(type, confidence)
└─────────────────────┘
         │
         ▼
┌──────────────────────────┐
│  Layer 3: State Machine  │  → deterministic parsing → Menu AST
└──────────────────────────┘
         │
         ▼
┌─────────────────────┐
│  Layer 4: Validate  │  → price range, duplicates, plausible categories
│  + Normalize        │
└─────────────────────┘
         │
         ▼
┌──────────────────────────────┐
│  Layer 5: LLM Enricher ONLY  │  → ingredients, allergens, calories
│  (schema-first, temp=0)      │    NEVER categories or new dishes
└──────────────────────────────┘
         │
         ▼
┌────────────────────────────┐
│  Layer 6: Confidence Gate  │  → per-dish score filter with thresholds
└────────────────────────────┘
         │
         ▼
    [MenuAST final output]
```

---

## Data Structures

### MenuAST
```kotlin
data class MenuAST(
    val categories: List<ASTCategory>,
    val sourceType: SourceType,
    val overallConfidence: Float,
    val parserVersion: String = "2.0"
)

data class ASTCategory(
    val name: String,
    val normalizedName: String,
    val dishes: List<ASTDish>,
    val confidence: Float,
    val detectionMethod: DetectionMethod,
    val lineIndex: Int
)

data class ASTDish(
    val name: String,
    val description: String?,
    val rawPrice: String?,
    val priceValue: Double?,
    val confidence: Float,
    val detectionMethod: DetectionMethod,
    val flags: Set<DishFlag> = emptySet(),
    val enrichment: DishEnrichment? = null
)

enum class DishFlag {
    PRICE_FROM_NEXT_LINE,
    DESCRIPTION_UNCERTAIN,
    CATEGORY_INFERRED,
    DUPLICATE_SUSPECTED
}

enum class DetectionMethod { REGEX, HEURISTIC, SCHEMA_LD, LLM_FALLBACK }
enum class SourceType { HTML, PDF, RAW_TEXT, OCR }
```

---

## Layer 0: Input Normalizers

- **HTML:** Priority 1 = JSON-LD schema.org/Menu (already structured). Priority 2 = Jsoup semantic extraction (remove nav/footer/aside/cookie/social).
- **PDF:** PdfBox Android — extract with layout (x, y, fontSize, isBold) to reconstruct columns and headers.
- **Raw text:** direct to tokenizer.

## Layer 1: Noise Patterns (regex)

Remove lines matching:
- Opening hours: `(aperto|chiuso|orari?|lunedì|...)`
- Contact info: `(prenotaz|tel\.?|telefono|\+\d{8,})`
- Addresses: `(via |corso |piazza |p\.?\s*iva)`
- Social: `(instagram|facebook|@\w+)`
- Legal: `(copyright|privacy|cookie|gdpr)`

## Layer 2: Tokenizer

**Price regex:**
```
(?:€|EUR|£|\$)?\s*(\d{1,3}(?:[.,]\d{3})*[.,]\d{2}|\d{1,3})\s*(?:€|EUR)?
```

**Price at end of line:**
```
\s*\.{0,20}\s*(?:€|EUR|£|\$)?\s*(\d{1,3}[.,]\d{2})\s*(?:€|EUR)?\s*$
```

**Category detection heuristics:**
- Known category keywords (multilingual set)
- All-caps or title-case
- Short (3–40 chars), no price, ends with `:`

## Layer 3: State Machine

5 states: `SeekingCategory → InCategory → InDish → AwaitingPrice → InDescription`

Key rules:
- Price window = 2 lines (look-ahead for `PRICE_STANDALONE`)
- Dish without category → synthetic "Menu" category with `CATEGORY_INFERRED` flag
- Flush state on new category header

## Layer 4: Validation Rules

- Price range: €0.30 – €150.00 (configurable)
- Dish name: 3–100 chars
- All-digit names → noise (confidence -= 0.6)
- Deduplication by normalized name
- Merge categories with same normalized name

## Layer 5: LLM Enrichment

**System prompt enforces strict JSON schema — no extra fields:**
```json
{
  "ingredients": "string[] (max 10)",
  "allergens": "string[] (EU standard only)",
  "estimatedCalories": "number | null",
  "cuisineCountry": "string | null",
  "cuisineRegion": "string | null"
}
```

- `temperature = 0.0` for determinism
- Extract JSON with regex even if LLM adds surrounding text
- Max 2 retries per dish; on failure keep dish without enrichment

## Layer 6: Confidence Gate

- `minDishConfidence = 0.35f` → below = discarded
- `minCategoryConfidence = 0.40f` → below = shown as "uncertain"
- UI can show uncertain items with a warning badge

---

## Fallback Strategy (tryParseStrategies)

1. **State machine parser** — full deterministic
2. **Simple dish extractor** — no state, just price-proximity heuristic
3. **LLM structured extractor** — denoised text only, schema-first (last resort)

---

## Recommended Libraries

| Need | Library |
|---|---|
| HTML parsing | `org.jsoup:jsoup` |
| PDF with layout | `com.tom-roush:pdfbox-android` |
| JSON serialization | `kotlinx-serialization-json` |
| OCR fallback | `com.google.mlkit:text-recognition` |

---

## Testing Strategy

- Unit test each layer in isolation
- Fixture files: real menus from 10+ Italian restaurants (HTML, PDF, raw)
- Contract test: LLM enricher NEVER adds dish names not in input
- Fuzz test: random HTML with menu-like structure
- Regression suite: known menus must parse identically between runs
