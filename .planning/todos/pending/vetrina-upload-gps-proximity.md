---
title: Implement Vetrina upload with GPS proximity check (500m)
date: 2026-05-17
priority: medium
---

## Goal
Users near a restaurant (within 500m) can upload photos that appear in that restaurant's
Vetrina — a horizontal scroll of user-submitted photos, Instagram Stories-style.

## Behavior
- On restaurant detail screen: show Vetrina horizontal photo strip
- If user GPS < 500m from restaurant coords → show "Aggiungi foto" button
- Photo picker: gallery or camera (ActivityResultContracts)
- Upload to Firebase Storage: `vetrina/{restaurantId}/{photoId}`
- Save metadata to RTDB: `/vetrina/{restaurantId}/{photoId}` → url, uploadedBy, timestamp
- Display photos in horizontal LazyRow, newest first

## GPS check
- Request `ACCESS_FINE_LOCATION` permission
- Use `FusedLocationProviderClient`
- Haversine distance < 500m → allow upload

## Files to create/touch
- `VetrinaScreen.kt` (or section in ClienteScreens.kt) — display + upload UI
- `model/VetrinaPhoto.kt` — data class (url, uploadedBy, restaurantId, timestamp)
- `AndroidManifest.xml` — ACCESS_FINE_LOCATION, CAMERA permissions
- `build.gradle.kts` — Firebase Storage dependency if not present
