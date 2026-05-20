---
title: Add restaurant fields to Ristoratore registration + Google Geocoding
date: 2026-05-17
priority: high
---

## Goal
When a user registers as Ristoratore, collect restaurant details and persist a
`/restaurants/{userId}` node in Firebase RTDB alongside the `/users/{userId}` node.

## Fields to add to RegistrationScreen (only shown when selectedUserType == "Ristoratore")
- `nomeRistorante` (String) — restaurant name
- `indirizzo` (String) — address input with Google Geocoding
- `telefono` (String) — phone number
- `tipoCucina` (String) — cuisine type (dropdown or text field)

## Address handling
Use Google Places Autocomplete or Geocoding REST API to resolve typed address → lat/lon.
Store both formatted address string and coordinates.

## Firebase writes on register success
1. Write `/users/{userId}` as today (userType, nome, cognome, etc.)
2. Write `/restaurants/{userId}` with nomeRistorante, indirizzo, telefono, tipoCucina, lat, lon, createdAt

## Files to touch
- `AuthScreens.kt` — add conditional restaurant fields section
- `model/Restaurant.kt` — new data class
- `FirebaseMenuUploader.kt` — fix restaurantName (read from /restaurants) and lat/lon
