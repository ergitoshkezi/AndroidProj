---
phase: 01-deterministic-menu-parser
plan: 04a
type: execute
wave: 4
depends_on: ["00", "01", "02", "03"]
files_modified:
  - Ingredient/app/src/main/java/com/example/ingredient/parser/enrichment/LLMEnricher.kt
  - Ingredient/app/src/main/java/com/example/ingredient/parser/mapping/AstToDtoMapper.kt
  - Ingredient/app/src/main/java/com/example/ingredient/parser/source/OcrPostProcessor.kt
autonomous: true
requirements: [D-09, D-10, D-11, D-12, D-18]

must_haves:
  truths:
    - "LLMEnricher enriches only dishes with confidence >= 0.65 (D-12), using temperature=0 (D-09)"
    - "LLMEnricher validates LLM output against original AST — no hallucinated dish names (D-18)"
    - "AstToDtoMapper converts MenuAST → List<MenuCategory> without exposing internal confidence (D-04/D-05)"
    - "OcrPostProcessor corrects OCR artifacts (l→1 in price context, hyphenated line breaks)"
  artifacts:
    - path: "Ingredient/app/src/main/java/com/example/ingredient/parser/enrichment/LLMEnricher.kt"
      provides: "Anti-hallucination LLM enrichment with schema-first prompt"
      contains: "class LLMEnricher"
    - path: "Ingredient/app/src/main/java/com/example/ingredient/parser/mapping/AstToDtoMapper.kt"
      provides: "MenuAST → List<MenuCategory> mapper"
      contains: "object AstToDtoMapper"
    - path: "Ingredient/app/src/main/java/com/example/ingredient/parser/source/OcrPostProcessor.kt"
      provides: "OCR noise correction pre-pass"
      contains: "object OcrPostProcessor"
  key_links:
    - from: "LLMEnricher.kt"
      to: "LLMApiClient.kt"
      via: "Uses existing LLM client"
      pattern: "LLMApiClient"
    - from: "AstToDtoMapper.kt"
      to: "MenuCategory"
      via: "Produces List<MenuCategory>"
      pattern: "fun map.*List<MenuCategory>"
---

<objective>
Build enrichment, mapping, and OCR processing components.

Purpose: Create the first half of integration layer components:
1. LLMEnricher — anti-hallucination enrichment with strict validation (D-09, D-10, D-11, D-12, D-18)
2. AstToDtoMapper — converts internal AST to public List<MenuCategory> (D-04, D-05)
3. OcrPostProcessor — pre-processes OCR output to fix common artifacts

Output: Three files providing enrichment, mapping, and OCR processing capabilities.
</objective>

<context>
@.planning/phases/01-deterministic-menu-parser/1-CONTEXT.md
@.planning/phases/01-deterministic-menu-parser/1-RESEARCH.md
@Ingredient/app/src/main/java/com/example/ingredient/parser/ast/MenuAST.kt
@Ingredient/app/src/main/java/com/example/ingredient/LLMApiClient.kt
@Ingredient/app/src/main/java/com/example/ingredient/MenuCategory.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Create LLMEnricher with anti-hallucination safeguards</name>
  <files>Ingredient/app/src/main/java/com/example/ingredient/parser/enrichment/LLMEnricher.kt</files>
  <read_first>Ingredient/app/src/main/java/com/example/ingredient/parser/enrichment/LLMEnricher.kt</read_first>
  <action>
Created class LLMEnricher(llmClient: LLMApiClient) with enrich(ast: MenuAST): MenuAST method.

Anti-hallucination contract documented in class header:
- LLM receives: dish name + optional existing description
- LLM returns: description + allergens ONLY
- LLM CANNOT create dishes, categories, or modify names/prices
- All output validated against original AST before acceptance

Implementation:
- ENRICH_THRESHOLD = 0.65f (D-12) — only enriches dishes with confidence >= threshold
- MAX_RETRIES = 2 (D-11) — max 2 retries per batch, keeps dish without enrichment on failure
- BATCH_SIZE = 15 — groups dishes to minimize API calls
- temperature = 0.0 passed to processMenuText (D-09)

buildBatchPrompt(): schema-first prompt per D-10
- Instructs: "DO NOT create new items. DO NOT modify names or prices."
- Specifies exact JSON schema: [{"id":"...","description":"...","allergens":["G","L"]}]
- Lists valid allergen codes: G, L, U, PE, CR, MO, AR, NO, SO, SE, SN, SA, LU, SO2

parseBatchResponse(): anti-hallucination validation (D-18)
- Rejects any ID not in originalIds set
- Rejects if description equals dish name verbatim (copy-paste)
- Validates allergen codes against allowed set
- Truncates descriptions at 200 chars
  </action>
  <verify>
    <automated>grep -c "class LLMEnricher" Ingredient/app/src/main/java/com/example/ingredient/parser/enrichment/LLMEnricher.kt</automated>
  </verify>
  <acceptance_criteria>
- File exists at parser/enrichment/LLMEnricher.kt
- `grep 'class LLMEnricher' LLMEnricher.kt` returns 1 match
- `grep 'ENRICH_THRESHOLD = 0.65f' LLMEnricher.kt` returns 1 match (D-12)
- `grep 'MAX_RETRIES = 2' LLMEnricher.kt` returns 1 match (D-11)
- `grep 'temperature = 0.0' LLMEnricher.kt` returns 1 match (D-09)
- `grep 'DO NOT create new items' LLMEnricher.kt` returns 1 match (D-10 schema-first)
- `grep 'originalIds' LLMEnricher.kt` returns 1+ matches (D-18 anti-hallucination)
  </acceptance_criteria>
  <done>LLMEnricher with anti-hallucination safeguards enforcing D-09, D-10, D-11, D-12, D-18</done>
</task>

<task type="auto">
  <name>Task 2: Create AstToDtoMapper</name>
  <files>Ingredient/app/src/main/java/com/example/ingredient/parser/mapping/AstToDtoMapper.kt</files>
  <read_first>Ingredient/app/src/main/java/com/example/ingredient/parser/mapping/AstToDtoMapper.kt</read_first>
  <action>
Created object AstToDtoMapper with map(ast: MenuAST): List<MenuCategory> method.

Per D-04/D-05: discards all internal AST metadata (confidence scores, DishFlags, lineRanges, trace).

Mapping:
- ASTSection.header.text → MenuCategory.categoryName
- ASTMenuItem → MenuItem:
  - name → name
  - description?.text → description (empty string if null)
  - allergens → allergens
  - price?.amount?.toDouble() → price (0.0 if null)
  - originalPrice = null, isOffer = false
  - country/region/cucina = "" (populated by LLM enrichment)
  - calories = 0

Filters out categories with blank names or empty dishes.
Output is fully compatible with existing FirebaseMenuUploader and UI.
  </action>
  <verify>
    <automated>grep -c "object AstToDtoMapper" Ingredient/app/src/main/java/com/example/ingredient/parser/mapping/AstToDtoMapper.kt</automated>
  </verify>
  <acceptance_criteria>
- File exists at parser/mapping/AstToDtoMapper.kt
- `grep 'object AstToDtoMapper' AstToDtoMapper.kt` returns 1 match
- `grep 'fun map.*List<MenuCategory>' AstToDtoMapper.kt` returns 1 match
- `grep 'MenuCategory' AstToDtoMapper.kt` returns 2+ matches (import + usage)
- `grep 'MenuItem' AstToDtoMapper.kt` returns 2+ matches (import + usage)
  </acceptance_criteria>
  <done>AstToDtoMapper converting MenuAST → List<MenuCategory> without exposing internal data</done>
</task>

<task type="auto">
  <name>Task 3: Create OcrPostProcessor</name>
  <files>Ingredient/app/src/main/java/com/example/ingredient/parser/source/OcrPostProcessor.kt</files>
  <read_first>Ingredient/app/src/main/java/com/example/ingredient/parser/source/OcrPostProcessor.kt</read_first>
  <action>
Created object OcrPostProcessor with process(raw: String): String and assessQuality(text: String): Float methods.

PRICE_RULES: OCR character substitution in price context
- l/I → 1 after digit, before 2-digit decimal
- O → 0 after digit, before 2-digit decimal
- OO → 00 after decimal separator
- €E artifact → €

STRUCTURAL_RULES: layout normalization
- Hyphenated line breaks: "word-\nrest" → "wordrest"
- Dot-leader fragments: ". . . ." → "......"
- Large gap → tab: "text     €12" → "text\t€12"

mergeHyphenatedLines(): merges lines ending with Italian connectors (di, con, e, al, etc.) with following lowercase line.

assessQuality(): returns 0-1 score based on:
- Garbage line ratio (non-alphanumeric content)
- Short line ratio (< 3 chars)
  </action>
  <verify>
    <automated>grep -c "object OcrPostProcessor" Ingredient/app/src/main/java/com/example/ingredient/parser/source/OcrPostProcessor.kt</automated>
  </verify>
  <acceptance_criteria>
- File exists at parser/source/OcrPostProcessor.kt
- `grep 'object OcrPostProcessor' OcrPostProcessor.kt` returns 1 match
- `grep 'fun process' OcrPostProcessor.kt` returns 1 match
- `grep 'fun assessQuality' OcrPostProcessor.kt` returns 1 match
- `grep 'PRICE_RULES' OcrPostProcessor.kt` returns 1+ matches
  </acceptance_criteria>
  <done>OcrPostProcessor with character substitution rules and quality assessment</done>
</task>

</tasks>

<verification>
```bash
# Verify all 3 files exist
ls -la Ingredient/app/src/main/java/com/example/ingredient/parser/enrichment/LLMEnricher.kt
ls -la Ingredient/app/src/main/java/com/example/ingredient/parser/mapping/AstToDtoMapper.kt
ls -la Ingredient/app/src/main/java/com/example/ingredient/parser/source/OcrPostProcessor.kt

# Verify key patterns
grep "class LLMEnricher" Ingredient/app/src/main/java/com/example/ingredient/parser/enrichment/LLMEnricher.kt
grep "object AstToDtoMapper" Ingredient/app/src/main/java/com/example/ingredient/parser/mapping/AstToDtoMapper.kt
grep "object OcrPostProcessor" Ingredient/app/src/main/java/com/example/ingredient/parser/source/OcrPostProcessor.kt

# Build check
cd Ingredient && ./gradlew compileDebugKotlin --quiet
```
</verification>

<success_criteria>
- All 3 files exist at specified paths
- LLMEnricher has ENRICH_THRESHOLD = 0.65f, MAX_RETRIES = 2, temperature = 0.0
- AstToDtoMapper converts MenuAST → List<MenuCategory>
- OcrPostProcessor has process() and assessQuality() methods
- Build compiles without errors
</success_criteria>

<output>
After verification, create `.planning/phases/01-deterministic-menu-parser/01-04a-SUMMARY.md`
</output>
