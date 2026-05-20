# Codebase Structure

## Main source files (app/src/main/java/com/example/ingredient/)

### Screens / UI
- `MainActivity.kt` — Navigation hub, all screen routing
- `AuthScreens.kt` — Login/register screens
- `MenuEditorScreen.kt` — Ristoratore menu management: EditDishDialog (name/ingredients/allergens/calories/price/country/region), MenuItemCard
- `ClienteScreens.kt` — Customer menu browsing with allergen filter
- `VetrinaScreen.kt` — Showcase with QR code display and photos
- `QrCodeScreen.kt` — QR code generator for internal menu sharing
- `QrMenuImportScreen.kt` — QR scan → WebView/PDF → LLM → preview → Firebase import

### Core Pipeline (QR Import)
- `WebViewMenuExtractor.kt` — Off-screen WebView, JS enabled, fetch/XHR interception via JavascriptInterface, extracts __NEXT_DATA__ (Next.js) or visible text
- `MenuContentPreprocessor.kt` — Extracts clean menu text from raw __NEXT_DATA__ JSON before LLM
- `PdfMenuExtractor.kt` — PDF URL detection (HEAD request), download, PdfRenderer, Tesseract OCR
- `LLMApiClient.kt` — Groq API client, processMenuText(), processInChunks(), splitBySection(), splitTextNoOverlap()
- `MenuParser.kt` — Parses LLM JSON response into List<MenuCategory> / List<MenuItem>
- `FirebaseMenuUploader.kt` — Uploads parsed menu to Firebase Realtime Database

### AI / OCR
- `TesseractManager.kt` (class: EnhancedTesseractManager) — Tesseract OCR with column detection, uses eng/spa traineddata
- `ImageColumnSplitter.kt` — Column detection for multi-column menu images
- `LocalAiParser.kt` — Local AI (llama.cpp / MediaPipe) fallback parser
- `ManualColumnSelector.kt` — Manual column selection UI

### Models
- `model/AllergeneType.kt` — Allergen enum
- `model/Restaurant.kt` — Restaurant data class
- `model/SessionManager.kt` — Firebase Auth session
- `model/User.kt` — User data class
- `model/VetrinaPhoto.kt` — Photo data class

### Data flow
```
QR scan (ZXing)
  → URL?
    → PDF? → PdfMenuExtractor (download + PdfRenderer + Tesseract OCR)
    → Web? → WebViewMenuExtractor → MenuContentPreprocessor
    → Plain text → direct
  → LLMApiClient.processMenuText()
  → MenuParser.parseMenuText() → List<MenuCategory>
  → Preview UI (QrMenuImportScreen)
  → FirebaseMenuUploader.uploadMenuWithMetadata()
```

## Tessdata
Located in `app/src/main/assets/models/`:
- `eng.traineddata` ✓
- `spa.traineddata` ✓
- `ita.traineddata` ✗ (NOT present — use eng for Italian, Latin charset works fine)
