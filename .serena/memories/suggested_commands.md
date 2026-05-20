# Suggested Commands

## Build & Run
- Open in Android Studio → Run 'app' on device/emulator
- Or: `./gradlew assembleDebug` from `Ingredient/` directory (PowerShell: `.\gradlew assembleDebug`)

## Logs to watch (adb logcat)
```
adb logcat -s LLMApiClient MenuParser QrMenuImport WebViewExtractor PdfMenuExtractor EnhancedTesseract
```

## Key log tags
- `LLMApiClient` — API calls, chunk processing, retries
- `MenuParser` — parsed categories and dish counts
- `QrMenuImport` — category/dish debug output
- `WebViewExtractor` — extracted char count, API responses captured
- `PdfMenuExtractor` — PDF download, OCR progress
- `EnhancedTesseract` — OCR column detection, initialization

## Firebase
- Database URL in google-services.json
- Structure: users/{userId}/menus/{menuId}/categories/...

## Gradle
- Project root: `Ingredient/`
- App module: `Ingredient/app/`
- Kotlin version: check build.gradle
