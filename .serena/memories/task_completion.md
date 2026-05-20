# When a Task is Completed

1. **Build**: `.\gradlew installDebug` (phone connected via USB)
   - Or `.\gradlew assembleDebug` if no device → install APK manually
   - Set `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` first

2. **Verify**: Check Android logcat for errors. Key log tags:
   - `LLMApiClient` — API calls, response codes, token counts
   - `WebViewExtractor` — extraction chars + API responses count
   - `MenuPreprocessor` — which path found / fallback triggered
   - `PdfMenuExtractor` — download size, page count, OCR chars
   - `MenuParser` — parsed category count
   - `QrMenuImport` — category names, dish counts, dish names + descriptions
   - `FirebaseMenuUploader` — upload success/failure

3. **No automated tests** — manual test on physical device (SM-S901B)

4. **Commit**: standard format `feat/fix/refactor/docs/chore`

5. **No git configured** in this project directory
