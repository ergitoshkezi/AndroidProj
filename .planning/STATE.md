---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: planning
last_updated: "2025-07-14T00:00:00Z"
progress:
  total_phases: 5
  completed_phases: 0
  total_plans: 0
  completed_plans: 4
---

# Ingredient App — Project State

**Project:** Ingredient
**Type:** Brownfield (Android/Kotlin/Jetpack Compose)
**Initialized:** 2026-04-22
**Status:** Active — Phase 1 in planning

---

## Project Overview

Android app connecting restaurant owners and customers. Customers search dishes by ingredients, region, or country. Restaurants upload menus via OCR + LLM pipeline. The project is split in two user-type worlds: Cliente (customer) and Ristoratore (restaurant owner).

**Current focus:** Customer side (Identity Module — Phase 1)

---

## Stack

- **Platform:** Android (minSdk 24, targetSdk 34)
- **Language:** Kotlin + Jetpack Compose
- **Backend:** Firebase Realtime Database (NOT Firestore)
- **Auth:** Manual RTDB query (no Firebase Auth — v1 plaintext passwords, accepted risk)
- **Location:** Google Play Services FusedLocationProviderClient
- **OCR:** Tesseract + LLM (restaurant side)
- **Navigation:** Manual state routing via `when(currentScreen: String)` in `IngredientApp`
- **No ViewModel** — all state in composables (known debt)

---

## Approved Schema

```
/users/{userId}:
  nome, cognome, email, password (plaintext v1), userType, allergeni: List<String>, createdAt

/restaurants/{id}:
  nome, indirizzo, lat, lon, ownerId, isPlaceholder

/dishes/{dishId}:
  nome, ingredienti: List<String>, cucina, regione, paese, prezzo: Double,
  offerta: Boolean, prezzoOfferta: Double?, restaurantId, restaurantName,
  restaurantLat, restaurantLon, descrizione?
```

---

## Architectural Decisions (Locked)

| Decision | Value | Source |
|---|---|---|
| Session routing | `IngredientApp` receives `initialScreen: String` from `MainActivity.onCreate` | Eng Review |
| ProfileScreen placement | 3rd tab in ClienteScreen bottom nav | Eng Review |
| Model package | `com.example.ingredient.model` | Eng Review |
| AllergeneType storage | `.name` uppercase e.g. `"GLUTINE"` | Eng Review |
| SharedPreferences file | `ingredient_session` | Eng Review |
| Session keys | `session_user_id`, `session_user_type`, `disclaimer_accepted` | Eng Review |
| v1 password storage | Plaintext in Firebase (accepted risk, v2 migrates to Firebase Auth) | CEO Review |
| Allergen behavior | Show ⚠️ warning, never auto-exclude dishes | CEO Review |
| Search engine | Client-side filtering (Firebase RTDB, no Algolia v1) | CEO Review |
| UI language | English | Design Doc |

---

## Known Issues / Tech Debt

See `.planning/codebase/CONCERNS.md` for full list. Critical items:

1. **CRITICAL-SEC-001**: `performSearch()` + `fetchOffers()` download ALL users data — must be fixed in Search Module (Phase 2)
2. **CRITICAL-SEC-002**: Password logged in AuthScreens.kt (RNF-SEC-001 blocks this in Phase 1)
3. **CRITICAL-SCHEMA-001**: `FirebaseMenuUploader.kt` writes to wrong path — fix in Restaurant Module (Phase 4)
4. **TYPE-001**: `MenuItem.price: String` → `Double` needed for Phase 2
5. **TYPE-002**: `MenuItem.allergens: String` → `List<String>` needed for Phase 2

---

## Phase Completion History

| Phase | Name | Status | Date |
|---|---|---|---|
| — | Codebase Map | ✅ Done | 2026-04-22 |
| 01-A | SessionManager + AllergeneType models | ✅ Done | 2025-07-14 |
| 01-B | MainActivity startup routing | ✅ Done | 2025-07-14 |
| 01-C | AllergeneChipSelector composable | ✅ Done | 2025-07-14 |
| 01-D | Auth screen extensions (session save, nome/cognome/allergens) | ✅ Done | 2025-07-14 |

---

## Open Decisions

| ID | Decision | Status |
|---|---|---|
| OI-001 | Firebase region: confirm `europe-west1` for EU data | Open |
| OI-002 | Disclaimer legal text: final product owner review | Open |
| OI-003 | v2 Firebase Auth migration | Deferred (TODOS.md) |
| OI-004 | GDPR account deletion | Deferred (TODOS.md) |
