---
phase: 03-pdf-menu-extraction
plan: "01"
subsystem: parser/pdf
tags: [pdf, pdfbox, text-extraction, quality-scoring, text-cleanup]
dependency_graph:
  requires: []
  provides:
    - PdfTextLayerExtractor.extract(Context, File): List<String>
    - PdfQualityChecker.score(List<String>): Float
    - PdfQualityChecker.isUsable(Float, ParsingMode): Boolean
    - PdfTextCleaner.clean(List<String>): String
  affects:
    - parser/pdf/PdfMenuExtractor (future consumer)
tech_stack:
  added:
    - com.tom-roush:pdfbox-android:2.0.27.0 (Maven Central)
  patterns:
    - Kotlin object singletons for stateless utilities
    - PDFBoxResourceLoader.init() idempotent initialization
    - D-02 weighted quality formula (charDensity + wordRecognition + pricePresence + lineStructure)
    - D-03 mode-gated routing (STRICT/BALANCED/AGGRESSIVE thresholds)
    - D-05 Unicode ligature → ASCII normalization
key_files:
  created:
    - Ingredient/app/src/main/java/com/example/ingredient/parser/pdf/PdfTextLayerExtractor.kt
    - Ingredient/app/src/main/java/com/example/ingredient/parser/pdf/PdfQualityChecker.kt
    - Ingredient/app/src/main/java/com/example/ingredient/parser/pdf/PdfTextCleaner.kt
  modified:
    - Ingredient/app/build.gradle.kts
decisions:
  - "Use com.tom-roush:pdfbox-android (hyphen groupId) — Maven Central coordinate, maps to com.tom_roush.* package namespace"
  - "PdfTextLayerExtractor.extract() signature: (Context, File) — Context first per Android idiom"
  - "PdfQualityChecker.isUsable() default mode = BALANCED — matches most real-world usage"
  - "PdfTextCleaner.clean() joins pages with double newline, strips page-number-only lines"
metrics:
  duration: ~12 minutes
  completed: 2025-05-21
  tasks_completed: 4
  files_changed: 4
---

# Phase 03 Plan 01: PDF Text Layer Infrastructure Summary

**One-liner:** PDFBox-Android 2.0.27.0 integrated with page-by-page text extraction, D-02 weighted quality scoring, and D-05 Unicode ligature/hyphen cleanup.

## What Was Built

Three new utility objects in `com.example.ingredient.parser.pdf` package:

| File | Purpose | Key API |
|------|---------|---------|
| `PdfTextLayerExtractor.kt` | PDFBox text extraction, page-by-page | `extract(Context, File): List<String>` |
| `PdfQualityChecker.kt` | D-02 quality formula + D-03 routing | `score(pages): Float`, `isUsable(score, mode): Boolean` |
| `PdfTextCleaner.kt` | D-05 artifact cleanup (ligatures, hyphens) | `clean(pages): String` |

## Commits

| Hash | Message |
|------|---------|
| `f67fd72` | `build(03-01): add pdfbox-android 2.0.27.0 dependency` |
| `78ba444` | `feat(03-01): add PdfTextLayerExtractor using PDFBox-Android` |
| `2ceba88` | `feat(03-01): add PdfQualityChecker with D-02 formula and D-03 routing` |
| `131ef6b` | `feat(03-01): add PdfTextCleaner fixing ligatures, hyphens, page headers` |
| `8577f1d` | `fix(03-01): correct pdfbox-android groupId from tom_roush to tom-roush` |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Wrong Maven groupId in plan**
- **Found during:** Build verification after Task 1
- **Issue:** Plan specified `com.tom_roush:pdfbox-android:2.0.27.0` (underscore). The artifact on Maven Central uses `com.tom-roush:pdfbox-android:2.0.27.0` (hyphen). Both `repo.maven.apache.org` and `jitpack.io` returned 404 for the underscore variant.
- **Fix:** Changed build.gradle.kts to `com.tom-roush:pdfbox-android:2.0.27.0`. Java package imports (`com.tom_roush.pdfbox.*`) remain correct — Maven groupId hyphens map to underscore package names.
- **Files modified:** `Ingredient/app/build.gradle.kts`
- **Commit:** `8577f1d`

## Verification

```
BUILD SUCCESSFUL in 47s
Task :app:compileDebugKotlin
```

All 4 new files compile without errors. PDFBox-Android 2.0.27.0 resolved from Maven Central.

## Known Stubs

None — all three utility objects implement full logic as specified. No placeholder returns or hardcoded values.

## Threat Surface Scan

No new network endpoints introduced. The `PdfTextLayerExtractor.extract()` crosses the T-03-01 trust boundary (untrusted PDF → PDFBox), mitigated by the `try/catch` wrapper returning `emptyList()` on any exception as required. No new threat surface beyond what the plan's threat model covers.

## Self-Check: PASSED

- `PdfTextLayerExtractor.kt` ✅ exists
- `PdfQualityChecker.kt` ✅ exists
- `PdfTextCleaner.kt` ✅ exists
- `build.gradle.kts` ✅ contains `com.tom-roush:pdfbox-android:2.0.27.0`
- Commits `f67fd72`, `78ba444`, `2ceba88`, `131ef6b`, `8577f1d` ✅ in git log
- `compileDebugKotlin` → BUILD SUCCESSFUL ✅
