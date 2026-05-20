# Ingredient — Project Overview

Android app (Kotlin + Jetpack Compose) for restaurant owners to manage menus.

## Core features
- QR code scanning → import restaurant menus automatically
- Manual menu management (Gestisci screen)
- Image recognition of physical menus (Tesseract OCR)
- Firebase Realtime Database storage

## QR Import pipeline (current state - May 2026)
1. Scan QR → get URL or plain text
2. Detect content type:
   - Plain text → direct LLM analysis
   - PDF URL → PdfMenuExtractor (download + PdfRenderer + Tesseract OCR)
   - HTTP URL → WebViewMenuExtractor (JS rendering, scroll, accordion expand, section detection)
3. MenuContentPreprocessor: extract structured data from Next.js/__NEXT_DATA__ SPAs
4. LLMApiClient: section-by-section extraction with per-section retry if empty
5. MenuParser: parse LLM JSON → List<MenuCategory>
6. Firebase upload via FirebaseMenuUploader

## LLM
- Provider: Groq API (free tier)
- Model: llama-3.1-8b-instant (DO NOT change to 70b — breaks JSON output)
- TPM limit: 6000 tokens/min (free tier)
- maxTokens: 4096
- Chunk size: 2500 chars
- Auto-retry on 429 with parsed wait time

## Key constraints
- Groq free tier: max 6000 TPM → keep prompt + output ≤ 5000 tokens
- Tesseract: only `eng` and `spa` tessdata in assets/models/ — `ita` does NOT exist (causes SIGSEGV)
- WebView must be attached to activity.window.decorView (1×1 INVISIBLE) for evaluateJavascript to work
- Price filter: name.isNotEmpty() only — no price > 0.0 requirement (websites rarely expose prices)
