---
phase: 01-deterministic-menu-parser
plan: 04b
type: execute
wave: 5
depends_on: ["00", "01", "02", "03", "04a"]
files_modified:
  - Ingredient/app/src/main/java/com/example/ingredient/parser/source/HtmlMenuExtractor.kt
  - Ingredient/app/src/main/java/com/example/ingredient/parser/observability/ParseLogger.kt
  - Ingredient/app/src/main/java/com/example/ingredient/parser/MenuParserPipeline.kt
  - Ingredient/app/src/test/java/com/example/ingredient/parser/PipelineRegressionTest.kt
autonomous: true
requirements: [D-04, D-05, D-15, D-17]

must_haves:
  truths:
    - "HtmlMenuExtractor provides JSON-LD and stripped HTML extraction strategies"
    - "MenuParserPipeline orchestrates all 8 layers with fallback when avgConfidence < 0.5f (D-15)"
    - "PipelineRegressionTest has 3 active pipeline.parse() calls proving determinism (D-17)"
    - "PipelineRegressionTest has 2 assertEquals assertions comparing consecutive runs"
  artifacts:
    - path: "Ingredient/app/src/main/java/com/example/ingredient/parser/source/HtmlMenuExtractor.kt"
      provides: "HTML extraction strategies (JSON-LD, strip)"
      contains: "object HtmlMenuExtractor"
    - path: "Ingredient/app/src/main/java/com/example/ingredient/parser/observability/ParseLogger.kt"
      provides: "Structured parse trace logging"
      contains: "object ParseLogger"
    - path: "Ingredient/app/src/main/java/com/example/ingredient/parser/MenuParserPipeline.kt"
      provides: "Main orchestrator with all pipeline layers"
      contains: "class MenuParserPipeline"
    - path: "Ingredient/app/src/test/java/com/example/ingredient/parser/PipelineRegressionTest.kt"
      provides: "Activated determinism regression test"
      contains: "pipeline.parse(fixtureContent"
  key_links:
    - from: "MenuParserPipeline.kt"
      to: "All pipeline components"
      via: "Orchestrates full pipeline"
      pattern: "class MenuParserPipeline"
    - from: "PipelineRegressionTest.kt"
      to: "MenuParserPipeline.kt"
      via: "Tests pipeline determinism"
      pattern: "pipeline.parse"
---

<objective>
Build HTML extraction, logging, main pipeline orchestrator, and activate regression test.

Purpose: Complete the pipeline with:
1. HtmlMenuExtractor — extracts menu content from HTML (JSON-LD, stripped)
2. ParseLogger — structured observability logging
3. MenuParserPipeline — main orchestrator running all 8 layers with 0.5f threshold (D-15)
4. Activate PipelineRegressionTest — uncomment the 3 parse calls and 2 assertEquals assertions (D-17)

Output: Three implementation files plus activated regression test proving determinism.
</objective>

<context>
@.planning/phases/01-deterministic-menu-parser/1-CONTEXT.md
@.planning/phases/01-deterministic-menu-parser/1-RESEARCH.md
@Ingredient/app/src/main/java/com/example/ingredient/parser/ast/MenuAST.kt
@Ingredient/app/src/main/java/com/example/ingredient/LLMApiClient.kt
@Ingredient/app/src/main/java/com/example/ingredient/MenuCategory.kt
@Ingredient/app/src/test/java/com/example/ingredient/parser/PipelineRegressionTest.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Create HtmlMenuExtractor</name>
  <files>Ingredient/app/src/main/java/com/example/ingredient/parser/source/HtmlMenuExtractor.kt</files>
  <read_first>Ingredient/app/src/main/java/com/example/ingredient/parser/source/HtmlMenuExtractor.kt</read_first>
  <action>
Created object HtmlMenuExtractor with extract(html: String, url: String): ExtractionResult method.

ExtractionResult data class: text, strategy, confidence.
Strategy enum: JSON_LD, KNOWN_PLATFORM, STRIPPED_HTML, VISIBLE_TEXT_FALLBACK.

Extraction strategies in priority order:
1. JSON-LD: extracts <script type="application/ld+json"> containing Menu/MenuItem keywords
2. Known platforms: detects justeat, thefork, deliveroo, glovo URLs and extracts by class patterns
3. Stripped HTML: removes script/style/nav/footer/aside/header/form/iframe, converts br/p/div/li/h* to newlines
4. Fallback: returns raw HTML truncated to 20000 chars

Helper methods:
- extractJsonLd(): regex-based JSON-LD extraction
- detectPlatform(): URL-based platform detection
- extractByPlatform(): class-based content extraction per platform
- stripHtml(): comprehensive HTML tag removal with entity decoding
- decodeHtmlEntities(): handles &amp;, &lt;, &gt;, &quot;, &#NNN;
  </action>
  <verify>
    <automated>grep -c "object HtmlMenuExtractor" Ingredient/app/src/main/java/com/example/ingredient/parser/source/HtmlMenuExtractor.kt</automated>
  </verify>
  <acceptance_criteria>
- File exists at parser/source/HtmlMenuExtractor.kt
- `grep 'object HtmlMenuExtractor' HtmlMenuExtractor.kt` returns 1 match
- `grep 'fun extract' HtmlMenuExtractor.kt` returns 1 match
- `grep 'JSON_LD\|KNOWN_PLATFORM\|STRIPPED_HTML' HtmlMenuExtractor.kt` returns 3+ matches
- `grep 'extractJsonLd' HtmlMenuExtractor.kt` returns 1+ matches
  </acceptance_criteria>
  <done>HtmlMenuExtractor with JSON-LD, platform-specific, and stripped HTML extraction strategies</done>
</task>

<task type="auto">
  <name>Task 2: Create ParseLogger</name>
  <files>Ingredient/app/src/main/java/com/example/ingredient/parser/observability/ParseLogger.kt</files>
  <read_first>Ingredient/app/src/main/java/com/example/ingredient/parser/observability/ParseLogger.kt</read_first>
  <action>
Created object ParseLogger with log(result: MenuParseResult) method.

Logs structured parse trace including:
- Parse mode (STRICT/BALANCED/AGGRESSIVE)
- Overall confidence (3 decimal places)
- Duration in milliseconds
- Section count
- Total item count
- LLM fallback flag
- Warnings list
- Token classification breakdown from trace
- Confidence breakdown map

Output format: ASCII-boxed log block for easy grep/filtering.
Uses Android Log.d with TAG = "MenuParser".
  </action>
  <verify>
    <automated>grep -c "object ParseLogger" Ingredient/app/src/main/java/com/example/ingredient/parser/observability/ParseLogger.kt</automated>
  </verify>
  <acceptance_criteria>
- File exists at parser/observability/ParseLogger.kt
- `grep 'object ParseLogger' ParseLogger.kt` returns 1 match
- `grep 'fun log' ParseLogger.kt` returns 1 match
- `grep 'MenuParseResult' ParseLogger.kt` returns 1+ matches
- `grep 'Log.d' ParseLogger.kt` returns 1+ matches
  </acceptance_criteria>
  <done>ParseLogger with structured trace output for debugging and monitoring</done>
</task>

<task type="auto">
  <name>Task 3: Create MenuParserPipeline orchestrator</name>
  <files>Ingredient/app/src/main/java/com/example/ingredient/parser/MenuParserPipeline.kt</files>
  <read_first>Ingredient/app/src/main/java/com/example/ingredient/parser/MenuParserPipeline.kt</read_first>
  <action>
Created class MenuParserPipeline(llmEnricher: LLMEnricher?, mode: ParsingMode, confidenceThreshold: Float, enrichmentThreshold: Float) with parse(rawContent: String, sourceType: SourceType): MenuParseResult method.

MenuParseResult data class: categories, ast, confidence, mode, usedLlmFallback, warnings, durationMs.

Pipeline layers executed in order:
1. Layer 0: MenuContentPreprocessor.preprocess() (existing, D-06)
2. OCR correction: OcrPostProcessor.process() for OCR/PDF sources
3. Line normalization: split by newlines, trim
4. Locale detection: LocalePackRegistry.detect()
5. Lexer: LineClassifier.classify() each line
6. Grammar parser: MenuGrammarParser.parse() with WindowedContextResolver
7. Validation: StructuralValidator.validate() — returns partial if canProceed=false
8. Repair: RepairEngine.repair()
9. Confidence: ConfidenceEngine.score()
10. LLM enrichment: only if overallConf >= enrichmentThreshold (0.65f) and llmEnricher != null
11. Mapping: AstToDtoMapper.map()

Error handling: returns empty result with error warning on exception.
Timing: tracks startMs through completion.
Logging: Android Log.d throughout for debugging.

confidenceThreshold = 0.5f (per D-15 — fallback when avgConfidence < 0.5)
enrichmentThreshold = 0.65f (per D-12)
  </action>
  <verify>
    <automated>grep -c "class MenuParserPipeline" Ingredient/app/src/main/java/com/example/ingredient/parser/MenuParserPipeline.kt</automated>
  </verify>
  <acceptance_criteria>
- File exists at parser/MenuParserPipeline.kt
- `grep 'class MenuParserPipeline' MenuParserPipeline.kt` returns 1 match
- `grep 'data class MenuParseResult' MenuParserPipeline.kt` returns 1 match
- `grep 'suspend fun parse' MenuParserPipeline.kt` returns 1 match
- `grep 'MenuContentPreprocessor' MenuParserPipeline.kt` returns 1+ matches (D-06)
- `grep 'LineClassifier' MenuParserPipeline.kt` returns 1+ matches
- `grep 'MenuGrammarParser' MenuParserPipeline.kt` returns 1+ matches
- `grep 'RepairEngine' MenuParserPipeline.kt` returns 1+ matches
- `grep 'ConfidenceEngine' MenuParserPipeline.kt` returns 1+ matches
- `grep 'AstToDtoMapper' MenuParserPipeline.kt` returns 1+ matches
  </acceptance_criteria>
  <done>MenuParserPipeline orchestrating all 8 pipeline layers with proper thresholds and error handling</done>
</task>

<task type="auto">
  <name>Task 4: Activate PipelineRegressionTest determinism assertions</name>
  <files>Ingredient/app/src/test/java/com/example/ingredient/parser/PipelineRegressionTest.kt</files>
  <read_first>Ingredient/app/src/test/java/com/example/ingredient/parser/PipelineRegressionTest.kt</read_first>
  <action>
Per D-17, uncomment the 3 pipeline.parse() calls and 2 assertEquals() assertions in PipelineRegressionTest.kt.

In the test function `pipeline produces identical output on 3 consecutive runs`:
1. Uncomment the pipeline instantiation:
   ```kotlin
   val pipeline = MenuParserPipeline(
       llmEnricher = null, // No LLM for determinism test
       mode = ParsingMode.BALANCED,
       confidenceThreshold = 0.5f,
       enrichmentThreshold = 0.65f
   )
   ```

2. Uncomment the 3 parse calls:
   ```kotlin
   val result1 = runBlocking { pipeline.parse(fixtureContent, SourceType.RAW_TEXT) }
   val result2 = runBlocking { pipeline.parse(fixtureContent, SourceType.RAW_TEXT) }
   val result3 = runBlocking { pipeline.parse(fixtureContent, SourceType.RAW_TEXT) }
   ```

3. Uncomment the 2 assertEquals assertions:
   ```kotlin
   assertEquals("Run 1 vs Run 2 for $fixtureName", serialize(result1), serialize(result2))
   assertEquals("Run 2 vs Run 3 for $fixtureName", serialize(result2), serialize(result3))
   ```

4. Remove the placeholder assertion:
   ```kotlin
   assertTrue("Fixture loaded: $fixtureName", fixtureContent.isNotEmpty())
   ```

5. Update the serialize() function to properly serialize MenuParseResult to canonical JSON.

Add required imports:
- import kotlinx.coroutines.runBlocking
- import com.example.ingredient.parser.MenuParserPipeline
- import com.example.ingredient.parser.ParsingMode
- import com.example.ingredient.parser.SourceType
  </action>
  <verify>
    <automated>Select-String -Path "Ingredient/app/src/test/java/com/example/ingredient/parser/PipelineRegressionTest.kt" -Pattern "pipeline\.parse\(fixtureContent" | Measure-Object | Select-Object -ExpandProperty Count</automated>
  </verify>
  <acceptance_criteria>
- `grep 'pipeline.parse(fixtureContent' PipelineRegressionTest.kt` returns 3 matches (not commented out)
- `grep 'assertEquals.*serialize' PipelineRegressionTest.kt` returns 2+ matches (not commented out)
- `grep 'runBlocking' PipelineRegressionTest.kt` returns 1+ matches
- `grep 'MenuParserPipeline' PipelineRegressionTest.kt` returns 2+ matches (import + usage)
- Test compiles: `./gradlew compileDebugUnitTestKotlin`
  </acceptance_criteria>
  <done>PipelineRegressionTest activated with 3 parse calls and 2 assertEquals assertions per D-17</done>
</task>

</tasks>

<verification>
```bash
# Verify all 3 implementation files exist
ls -la Ingredient/app/src/main/java/com/example/ingredient/parser/source/HtmlMenuExtractor.kt
ls -la Ingredient/app/src/main/java/com/example/ingredient/parser/observability/ParseLogger.kt
ls -la Ingredient/app/src/main/java/com/example/ingredient/parser/MenuParserPipeline.kt

# Verify key patterns
grep "object HtmlMenuExtractor" Ingredient/app/src/main/java/com/example/ingredient/parser/source/HtmlMenuExtractor.kt
grep "object ParseLogger" Ingredient/app/src/main/java/com/example/ingredient/parser/observability/ParseLogger.kt
grep "class MenuParserPipeline" Ingredient/app/src/main/java/com/example/ingredient/parser/MenuParserPipeline.kt

# Verify PipelineRegressionTest activation (D-17)
grep -c "pipeline.parse(fixtureContent" Ingredient/app/src/test/java/com/example/ingredient/parser/PipelineRegressionTest.kt
# Expected: 3

grep -c "assertEquals" Ingredient/app/src/test/java/com/example/ingredient/parser/PipelineRegressionTest.kt
# Expected: 2+

# Build check
cd Ingredient && ./gradlew compileDebugKotlin compileDebugUnitTestKotlin --quiet
```
</verification>

<success_criteria>
- All 3 implementation files exist at specified paths
- HtmlMenuExtractor has JSON-LD, platform, and stripped strategies
- ParseLogger outputs structured trace
- MenuParserPipeline orchestrates all layers with 0.5f threshold (D-15)
- PipelineRegressionTest has 3 uncommented pipeline.parse() calls
- PipelineRegressionTest has 2+ uncommented assertEquals assertions
- Build compiles without errors (both main and test)
</success_criteria>

<output>
After verification, create `.planning/phases/01-deterministic-menu-parser/01-04b-SUMMARY.md`
</output>
