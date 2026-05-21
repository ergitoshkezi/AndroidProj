---
phase: 03-pdf-menu-extraction
plan: "02"
subsystem: pdf-extraction
tags: [pdf, text-layer, ocr, routing, kotlin, android]
dependency_graph:
  requires: [03-01]
  provides: [PdfPageRenderer, PdfMenuExtractor-text-routing]
  affects: [QrMenuImportScreen]
tech_stack:
  added: []
  patterns: [text-first-routing, OCR-fallback, backward-compatible-overload]
key_files:
  created:
    - Ingredient/app/src/main/java/com/example/ingredient/parser/pdf/PdfPageRenderer.kt
  modified:
    - Ingredient/app/src/main/java/com/example/ingredient/PdfMenuExtractor.kt
decisions:
  - "Backward-compatible 3-param overload preserves QrMenuImportScreen call site without changes (D-07)"
  - "PdfPageRenderer returns null on failure; caller skips page — avoids hard crash on bad PDFs"
  - "Text-layer routing defaults to BALANCED mode (score >= 0.50) matching D-03 spec"
metrics:
  duration: "~8 minutes"
  completed: "2025-01-31"
  tasks_completed: 3
  tasks_total: 3
  files_created: 1
  files_modified: 1
---

# Phase 03 Plan 02: PdfPageRenderer Extraction + Text-First Routing Summary

**One-liner:** Extracted PDF→Bitmap rendering into `PdfPageRenderer` and wired `PdfMenuExtractor` to attempt native PDFBox text extraction before falling back to Tesseract OCR.

## What Was Built

### PdfPageRenderer.kt (new)
- Extracted verbatim bitmap rendering logic from the original `PdfMenuExtractor` OCR loop
- `renderPage(renderer, pageIndex)` returns `Bitmap?` — null on failure, caller skips page gracefully
- Same constants: `PAGE_WIDTH_PX = 1200`, white-background fill, `RENDER_MODE_FOR_DISPLAY`
- Called by the refactored OCR loop in `PdfMenuExtractor`

### PdfMenuExtractor.kt (refactored)
Two-path routing per D-03/D-04:

```
extractText()
  ├─ PdfTextLayerExtractor.extract(context, pdfFile)   ← D-03: try text first
  │    └─ PdfQualityChecker.isUsable(score, mode)
  │         ├─ TRUE  → PdfTextCleaner.clean() → return  ← fast path
  │         └─ FALSE → fall through
  └─ PdfRenderer + Tesseract OCR loop                  ← D-04: existing fallback
         └─ PdfPageRenderer.renderPage() per page      ← extracted helper
```

**Backward compatibility (D-07):**
- 3-param `extractText(context, url, onProgress)` overload added
- Delegates to 4-param version with `ParsingMode.BALANCED`
- `QrMenuImportScreen` call site unchanged — no modifications required

## Task 3: QrMenuImportScreen Verification

Lines 50–56 of `QrMenuImportScreen.kt`:
```kotlin
} else if (PdfMenuExtractor.isPdfUrl(url)) {
    statusMessage = "PDF rilevato, download in corso…"
    val pdfText = PdfMenuExtractor.extractText(
        context = context,
        url = url,
        onProgress = { statusMessage = it }
    )
```

- `isPdfUrl(url)` — unchanged ✓
- `extractText(context, url, onProgress)` — 3-param call, served by backward-compatible overload ✓
- `pdfText` → `content` → `MenuParserPipeline` — pipeline unchanged ✓

## Build Verification

```
BUILD SUCCESSFUL in 32s
```

One pre-existing deprecation warning (unrelated): `Icons.Filled.ArrowBack` deprecated in `QrMenuImportScreen.kt` — out of scope, not introduced by this plan.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed PdfTextLayerExtractor argument order**
- **Found during:** Task 2
- **Issue:** Plan's refactored code called `PdfTextLayerExtractor.extract(pdfFile, context)` but the actual method signature (from 03-01) is `extract(context: Context, file: File)` — arguments were swapped
- **Fix:** Changed call to `PdfTextLayerExtractor.extract(context, pdfFile)` — matches actual signature
- **Files modified:** `PdfMenuExtractor.kt`
- **Commit:** 276ff82

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | 1132864 | `feat(03-02): add PdfPageRenderer (extracted bitmap renderer, D-04)` |
| 2 | 276ff82 | `feat(03-02): add text-first routing to PdfMenuExtractor (D-03/D-04)` |

## Known Stubs

None — all routing logic is fully wired. Text layer path produces real output from `PdfTextLayerExtractor` → `PdfQualityChecker` → `PdfTextCleaner`.

## Threat Flags

None — no new network endpoints, auth paths, or schema changes introduced. `PdfPageRenderer` is a pure in-process renderer; `MAX_PAGES=15` DoS cap from original retained.

## Self-Check: PASSED

- `PdfPageRenderer.kt` exists at `parser/pdf/` ✓
- `PdfMenuExtractor.kt` has text-first routing block ✓
- Commits 1132864 and 276ff82 exist ✓
- `QrMenuImportScreen.kt` unchanged ✓
- Build: `BUILD SUCCESSFUL` ✓
