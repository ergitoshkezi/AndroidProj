# Ingredient — Project Overview

## Purpose
Android app for restaurant owners to manage menus and for customers to browse them.
- Ristoratore (owner) flow: create/edit menu, scan QR of external menus to import, manage dishes with name/ingredients/allergens/calories/price/country/region
- Cliente (customer) flow: browse menu, filter by allergens
- "Vetrina" (showcase) mode: display QR code, photos

## Tech Stack
- Language: Kotlin
- UI: Jetpack Compose + Material3
- Backend: Firebase Realtime Database + Firebase Storage
- Auth: Firebase Auth (custom session via SessionManager)
- AI/LLM: Groq API (llama-3.1-8b-instant) via LLMApiClient.kt — hardcoded key in QrMenuImportScreen.kt
- OCR: Tesseract4Android (cz.adaptech.tesseract4android:4.9.0) — eng + spa traineddata in assets/models/
- QR Scanning: ZXing (zxing-android-embedded:4.3.0)
- Image loading: Coil
- WebView: on-device JS rendering for QR menu import
- PDF: Android PdfRenderer (built-in) + Tesseract OCR
- Build: Gradle (Kotlin DSL), minSdk=24, targetSdk=36, compileSdk=36

## Package
`com.example.ingredient`

## Firebase Structure
- `/users/{userId}/dishes/{menuItemId}` — menu items
  - name, description (ingredients), allergeni (list), prezzo, calorie, paese, regione
- `/users/{userId}/restaurant/` — restaurant info
- `/users/{userId}/vetrina/` — showcase photos

## Key Decisions
- Groq model: llama-3.1-8b-instant (NOT 70b — breaks output format)
- maxTokens: 4096 (free tier TPM limit is 6000; prompt ~1000 tokens + 4096 output = ~5096)
- Chunk size: 2500 chars (avoids 413/429 rate limit errors)
- Price filter: name.isNotEmpty() (NOT price > 0.0 — many sites don't expose prices)
- WebView must be attached to activity.window.decorView as 1×1 INVISIBLE view (evaluateJavascript requires window attachment)
