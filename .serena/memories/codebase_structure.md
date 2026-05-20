# Codebase Structure

All Kotlin files in:
`Ingredient/app/src/main/java/com/example/ingredient/`

## Key files

| File | Role |
|---|---|
| `QrMenuImportScreen.kt` | Main orchestrator: QR scan → pipeline → preview → Firebase upload. GROQ_API_KEY hardcoded line 22. |
| `LLMApiClient.kt` | All LLM interaction. processMenuText() → section-by-section if markers detected, else chunks. extractSection/extractSectionAggressive for retry. |
| `MenuParser.kt` | Parses LLM JSON array → List<MenuCategory>. Keys: "Categoria","Piatti","Descrizione/Ingredienti/Extra","Allergeni","Prezzo","Calorie","Paese","Regione". Filter: name.isNotEmpty() only. Splits on "=== PARTE N ===" for multi-chunk. |
| `WebViewMenuExtractor.kt` | JS renderer. Flow: load → JS_NETWORK_INTERCEPTOR → scroll+expand (JS_EXPAND_AND_SCROLL, 3s wait) → JS_EXTRACT_TEXT. Returns section markers `=== CATEGORIA: X ===` when h2/h3 headings detected. bestContent() routes __NEXT_DATA__ prefix to full JSON. |
| `MenuContentPreprocessor.kt` | Extracts clean menu from Next.js/Nuxt JSON. Falls back to "" → caller uses visibleText. searchMenuArrays() recursive depth 8. |
| `PdfMenuExtractor.kt` | isPdfUrl(): URL pattern + HEAD content-type check. extractText(): download → PdfRenderer → Tesseract OCR per page. Uses `eng` tessdata, max 15 pages, PAGE_WIDTH_PX=1200. |
| `TesseractManager.kt` (class: EnhancedTesseractManager) | OCR for camera/image import. Uses ImageColumnSplitter. ONLY eng+spa tessdata. |
| `FirebaseMenuUploader.kt` | Uploads List<MenuCategory> to Firebase Realtime DB. |

## Data model (in-memory)
```kotlin
data class MenuCategory(val categoryName: String, val dishes: List<MenuItem>)
data class MenuItem(val name: String, val description: String, val allergens: String, val price: Float, val calories: Int, val country: String, val region: String)
```

## Assets
- `assets/models/eng.traineddata` — Tesseract English (covers Italian)
- `assets/models/spa.traineddata` — Tesseract Spanish
- `assets/tessdata/` — NOT used (use getExternalFilesDir)
