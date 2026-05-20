---
title: Restaurant Entity Design
date: 2026-05-17
context: Exploration session — ristoratore registration + vetrina feature
---

## Firebase Schema

### /restaurants/{userId}
```
nomeRistorante: String
indirizzo: String          // formatted address from Google Geocoding
telefono: String
tipoCucina: String         // e.g. "Italiana", "Giapponese"
lat: Double                // from geocoding result
lon: Double
createdAt: Long
```
`userId` is the same key as `/users/{userId}` (Ristoratore) — 1:1 relationship.

### /dishes/{dishId}
Already has `restaurantId`. Fix: `restaurantLat`/`restaurantLon` currently hardcoded 0.0
→ read from `/restaurants/{restaurantId}` at upload time.
`restaurantName` should come from `/restaurants/{restaurantId}/nomeRistorante` (not nome+cognome).

### /vetrina/{restaurantId}/{photoId}
```
url: String                // Firebase Storage download URL
uploadedBy: String         // userId of uploader
timestamp: Long
```

## Decisions

- **Address input**: Google Geocoding API (typed address → lat/lon resolved server-side via Places).
- **Vetrina proximity**: GPS < 500m from restaurant coords. Check client-side before showing upload button.
- **Vetrina style**: Instagram Stories-like horizontal scroll of user photos per restaurant.
- **Menu linkage**: dishes already keyed by `restaurantId = userId`. No schema change needed.
- **Photo storage**: Firebase Storage at `vetrina/{restaurantId}/{photoId}` — URL saved to RTDB.
