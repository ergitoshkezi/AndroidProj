---
phase: "03"
title: PDF Menu Extraction
status: planned
depends_on: "01-deterministic-menu-parser"
date: 2026-05-21
---

# Phase 03: PDF Menu Extraction

## Goal
Build a deterministic PDF extraction pipeline that pulls menu text from PDF files
without hallucination, feeding clean structured text into the existing
`MenuParserPipeline`. Handles both native-text PDFs and image-only (scanned) PDFs
via ML Kit OCR fallback.

## Problem Statement
The current `processUrl()` has a PDF branch but it is ad-hoc: no structured text
extraction, no quality check, no page-level processing, no OCR fallback for
scanned PDFs. PDFs are the most common format for professional restaurant menus.

## Architecture

```
SourceType.PDF
   ↓
PdfMenuExtractor
   ├── PdfTextLayerExtractor    ← try native text first (PdfRenderer + text detection)
   │     ↓ raw page text strings
   ├── PdfQualityChecker        ← is text layer usable? (density + gibberish check)
   │     ├── GOOD  → PdfTextCleaner → clean text string
   │     └── POOR  → PdfPageRenderer → Bitmap[]
   │                       ↓
   │               ML Kit TextRecognition (on-device)
   │                       ↓
   │               OcrPostProcessor [existing Phase 01]
   │                       ↓
   │               clean text string
   ↓
MenuParserPipeline.parse(text, SourceType.PDF)  [existing Phase 01]
```

## New Files

| File | Purpose |
|------|---------|
| `parser/pdf/PdfMenuExtractor.kt` | Orchestrator — selects text vs OCR path |
| `parser/pdf/PdfTextLayerExtractor.kt` | Extract native text layer via Android PdfRenderer |
| `parser/pdf/PdfQualityChecker.kt` | Decide if text layer is usable (score 0-1) |
| `parser/pdf/PdfTextCleaner.kt` | Fix PDF extraction artifacts (ligatures, hyphenation, encoding) |
| `parser/pdf/PdfPageRenderer.kt` | Render PDF pages to Bitmap for OCR |

## Modified Files

| File | Change |
|------|--------|
| `QrMenuImportScreen.kt` | Wire SourceType.PDF through PdfMenuExtractor |

## Text Quality Scoring (PdfQualityChecker)

```
textScore = 0.40 × charDensity       // chars per page area
           + 0.30 × wordRecognition   // % of tokens that are dictionary words
           + 0.20 × pricePresence     // at least 1 price pattern found
           + 0.10 × lineStructure     // avg line length reasonable (20-100 chars)
```

Threshold: `score >= 0.50` → use text layer; else → OCR path

## Android Constraints

- **PdfRenderer**: built-in (API 21+), renders pages as Bitmap — does NOT extract text natively
- **Text extraction from PdfRenderer**: not possible directly; need PDFBox-Android OR render+OCR
- **Recommended approach**: 
  - Try **PDFBox-Android** (`com.tom_roush:pdfbox-android:2.0.27.0`) for text layer extraction
  - Fallback to PdfRenderer → Bitmap → ML Kit OCR if no text layer or low quality
- **ML Kit**: `com.google.mlkit:text-recognition:16.0.0` (on-device, no network)

## Parsing Mode Integration

- STRICT: only use text layer if textScore >= 0.70 (never OCR in strict)
- BALANCED: text layer if >= 0.50, else OCR
- AGGRESSIVE: always try OCR regardless of text layer

## Test Strategy

1. Unit tests: PdfQualityCheckerTest with known score fixtures
2. Integration: PDF fixture → List<MenuCategory> (use 3 PDF fixtures)
3. Fixtures: native-text PDF, scanned PDF, mixed PDF

## Success Criteria

- [ ] Native-text PDF menus parse with >= 85% dish recall
- [ ] Scanned PDF menus (via OCR fallback) parse with >= 70% dish recall
- [ ] No dishes invented that don't exist in the source PDF
- [ ] PdfQualityChecker correctly routes: 90%+ of native PDFs → text path, 90%+ scanned → OCR
- [ ] All existing Phase 01 regression tests still pass
- [ ] Extraction latency < 3s per page (text path), < 8s per page (OCR path)
