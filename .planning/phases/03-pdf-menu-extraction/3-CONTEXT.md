# Phase 03: PDF Menu Extraction — Context

**Gathered:** 2026-05-21
**Status:** Ready for planning
**Source:** PRD Express Path (PHASE.md + existing code analysis)

<domain>
## Phase Boundary

Upgrade `PdfMenuExtractor.kt` from OCR-only to a two-path pipeline:
1. **Text path:** PDFBox-Android extracts native text layer → `PdfQualityChecker` validates → `PdfTextCleaner` fixes artifacts → `MenuParserPipeline`
2. **OCR path (existing):** `PdfRenderer` renders bitmaps → Tesseract OCR → `OcrPostProcessor` → `MenuParserPipeline`

**In scope:**
- Add PDFBox-Android dependency
- New `parser/pdf/` package: PdfTextLayerExtractor, PdfQualityChecker, PdfTextCleaner, PdfPageRenderer (thin wrapper), PdfMenuExtractor (refactored orchestrator)
- Move existing `PdfMenuExtractor` logic into the new architecture
- Wire through `MenuParserPipeline.parse(text, SourceType.PDF)` (already wired in QrMenuImportScreen)

**Out of scope:**
- Changing `QrMenuImportScreen` call site (it already calls `PdfMenuExtractor.extractText()` — keep signature)
- Local PDF files (only PDF-from-URL for now)
- PDFs with password protection

</domain>

<decisions>
## Implementation Decisions

### D-01: PDFBox-Android for native text (LOCKED)
- Dependency: `com.tom_roush:pdfbox-android:2.0.27.0`
- `PdfTextLayerExtractor` uses `PDDocument.load()` → `PDFTextStripper`
- Returns page-by-page text strings

### D-02: Quality scoring formula (LOCKED)
```
textScore = 0.40 × charDensity       // extracted chars / (pageCount × 500)
           + 0.30 × wordRecognition   // % tokens that match [\w]{3,} / totalTokens
           + 0.20 × pricePresence     // min(pricePatternCount / 3, 1.0)
           + 0.10 × lineStructure     // % lines with 10-120 chars length
```
Threshold: `textScore >= 0.50` → text path; else → OCR path

### D-03: Routing per ParsingMode (LOCKED)
- STRICT: text path only if textScore >= 0.70
- BALANCED: text path if textScore >= 0.50 (default)
- AGGRESSIVE: always try text path first, fall back to OCR only on parse failure

### D-04: Existing OCR path unchanged (LOCKED)
- Keep Tesseract `EnhancedTesseractManager` + `PdfRenderer` bitmap rendering
- Only add text-path routing BEFORE the existing OCR code
- `PdfMenuExtractor.extractText()` signature UNCHANGED (backward compatibility)

### D-05: PdfTextCleaner artifacts (LOCKED)
Fix these common PDF text layer artifacts:
- Ligatures: fi→fi, fl→fl, ffi→ffi (Unicode ligature chars → ASCII)
- Soft hyphenation: `word-\nnext` → `wordnext`
- Extra whitespace between chars (PDF char-by-char extraction)
- Page header/footer repetition (page numbers, document title)

### D-06: New package location (LOCKED)
All new files in: `parser/pdf/` package = `com.example.ingredient.parser.pdf`
The existing `PdfMenuExtractor.kt` stays at `com.example.ingredient` (not moved — backward compat)
It is REFACTORED to delegate to the new `parser/pdf/` components

### D-07: MenuParserPipeline integration (LOCKED)
- `PdfMenuExtractor.extractText()` returns plain String (unchanged)
- `QrMenuImportScreen` already passes it to `MenuParserPipeline.parse(text, SourceType.PDF)`
- `MenuParserPipeline` already has `SourceType.PDF` in `OcrPostProcessor` branch — this is correct

### Agent's Discretion
- Exact Tesseract language file mapping (italian = "spa" already in existing code — keep it)
- PDFBox rendering DPI setting
- Max pages cap (existing code uses 15 — keep)

</decisions>

<canonical_refs>
## Canonical References

### Existing code to understand before planning
- `Ingredient/app/src/main/java/com/example/ingredient/PdfMenuExtractor.kt` — existing OCR-only extractor (REFACTOR target)
- `Ingredient/app/src/main/java/com/example/ingredient/QrMenuImportScreen.kt` — call site (lines 50-62, 89) — DO NOT change signature
- `Ingredient/app/src/main/java/com/example/ingredient/parser/MenuParserPipeline.kt` — Phase 01, lines with SourceType.PDF
- `Ingredient/app/src/main/java/com/example/ingredient/parser/source/OcrPostProcessor.kt` — Phase 01, already used for PDF text
- `Ingredient/app/build.gradle.kts` — add pdfbox-android dependency here

### Phase 01 (frozen — read only)
- `Ingredient/app/src/main/java/com/example/ingredient/parser/ast/MenuAST.kt` — SourceType.PDF enum value

</canonical_refs>

<specifics>
## Specific Ideas

### New files in `parser/pdf/`
1. `PdfTextLayerExtractor.kt` — `object`, `fun extract(file: File): List<String>` (one String per page)
2. `PdfQualityChecker.kt` — `object`, `fun score(pages: List<String>): Float`, `fun isUsable(score: Float, mode: ParsingMode): Boolean`
3. `PdfTextCleaner.kt` — `object`, `fun clean(pages: List<String>): String`
4. `PdfPageRenderer.kt` — thin wrapper around existing bitmap rendering logic (extracted from PdfMenuExtractor)

### Refactored `PdfMenuExtractor.kt`
```kotlin
// New routing logic (before existing OCR code):
val pages = PdfTextLayerExtractor.extract(pdfFile)
val qualityScore = PdfQualityChecker.score(pages)
if (PdfQualityChecker.isUsable(qualityScore, mode)) {
    return PdfTextCleaner.clean(pages)
}
// else fall through to existing Tesseract OCR path
```

### Dependency to add to build.gradle.kts
```kotlin
implementation("com.tom_roush:pdfbox-android:2.0.27.0")
```

</specifics>

<deferred>
## Deferred Ideas

- Password-protected PDF support
- Local file PDF (content URI) — only URL-based for now
- Multi-column PDF layout detection (already handled downstream by MenuGrammarParser)
- PDF form fields extraction

</deferred>

---

*Phase: 03-pdf-menu-extraction*
*Context gathered: 2026-05-21 via PRD Express Path*
