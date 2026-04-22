# Ingredient App — Roadmap

**Version:** 1.0 | **Status:** Active | **Date:** 2026-04-22

---

## Phase 1: Identity Module

**Goal:** Complete user identity for the customer side — disclaimer screen, registration with nome/cognome/allergeni, login, session persistence, profile screen, and logout. The existing AuthScreens.kt must be extended (not rewritten). MainActivity must route on startup from SharedPreferences.

**Requirements:** REQ-ID-001, REQ-ID-002, REQ-ID-003, REQ-ID-004, REQ-ID-005, REQ-ID-006, REQ-ID-007, RNF-SEC-001, RNF-SEC-002, RNF-PERF-001, RNF-PRIVACY-001

**Plans:** 5 plans in 3 waves

Plans:
- [ ] 01-PLAN-A.md — Model layer: AllergeneType enum, User data class, SessionManager (Wave 1)
- [ ] 01-PLAN-B.md — DisclaimerScreen + MainActivity startup routing (Wave 2)
- [ ] 01-PLAN-C.md — AllergeneChipSelector reusable composable (Wave 2)
- [ ] 01-PLAN-D.md — Extend AuthScreens: Login error msgs, Registration nome/cognome/allergeni, session save, password logging fix (Wave 3)
- [ ] 01-PLAN-E.md — ProfileScreen as 3rd tab + Logout (Wave 3)

**Files in scope:**
- `app/src/main/java/com/example/ingredient/MainActivity.kt`
- `app/src/main/java/com/example/ingredient/AuthScreens.kt`
- `app/src/main/java/com/example/ingredient/ClienteScreens.kt`
- `app/src/main/java/com/example/ingredient/model/` (new package)

**Delivers:**
- DisclaimerScreen (legal gate, first run)
- AllergeneType enum (14 EU allergens)
- User data class
- SessionManager (SharedPreferences wrapper)
- AllergeneChipSelector (reusable composable)
- Registration with nome, cognome, allergens
- Login with proper error messages + session save
- ProfileScreen (3rd bottom nav tab)
- Logout
- MainActivity startup routing (disclaimer → login → home)
- Remove password logging (RNF-SEC-001)

---

## Phase 2: Search & Discovery

**Goal:** Customer can search dishes by ingredients (matching score), filter by country/region, sort/filter by rating, distance, and price. Fix the O(n) user scan landmine (performSearch/fetchOffers read /dishes not /users). Add cucina/regione/paese to MenuItem.

**Requirements:** REQ-SEARCH-001, REQ-SEARCH-002, REQ-SEARCH-003, REQ-SEARCH-004

**Depends on:** Phase 1

**Files in scope:**
- `app/src/main/java/com/example/ingredient/ClienteScreens.kt`
- `app/src/main/java/com/example/ingredient/model/MenuItem.kt` (or equivalent)

**Delivers:**
- Ingredient-based search with match score
- Country / region filter
- Rating / distance / price filters
- Fix O(n) scan: query /dishes directly (no more /users in search)
- MenuItem: price → Double, allergens → List<String>, add cucina/regione/paese

---

## Phase 3: Daily Offers

**Goal:** Dedicated "Offers" tab showing today's discounted dishes sorted by proximity. Uses FusedLocationProviderClient already wired. Dish must have offerta=true and prezzoOfferta set.

**Requirements:** REQ-OFFERS-001

**Depends on:** Phase 2

**Files in scope:**
- `app/src/main/java/com/example/ingredient/ClienteScreens.kt`

**Delivers:**
- Offers tab in ClienteScreen bottom nav
- Proximity-sorted offer cards showing dish, restaurant, original price, offer price
- Distance calculation from user location

---

## Phase 4: Restaurant Module

**Goal:** Ristoratore screens — upload menu via OCR + LLM pipeline, view/edit dishes and prices. Fix FirebaseMenuUploader schema mismatch (writes to users/{id}/menu/ must write to /dishes/{dishId}).

**Requirements:** REQ-REST-001, REQ-REST-002, REQ-REST-003

**Depends on:** Phase 1

**Files in scope:**
- `app/src/main/java/com/example/ingredient/RistoratoreScreens.kt`
- `app/src/main/java/com/example/ingredient/MenuParser.kt`
- `app/src/main/java/com/example/ingredient/FirebaseMenuUploader.kt`

**Delivers:**
- Ristoratore home screen with menu list
- Add/edit dish (nome, ingredienti, cucina, regione, paese, prezzo, offerta)
- OCR menu upload flow (camera → Tesseract → LLM parse → review → save)
- Fix FirebaseMenuUploader: write to /dishes/{dishId} at root level

---

## Phase 5: Production Hardening

**Goal:** Security, performance, and stability for public release. Firebase Security Rules. Remove plaintext passwords (migrate to Firebase Auth). GDPR account deletion. End-to-end testing.

**Depends on:** Phase 4

**Delivers:**
- Firebase Auth integration (replaces plaintext RTDB passwords)
- Firebase Security Rules (read/write scoped to authenticated users)
- GDPR: account deletion flow
- Privacy policy screen with proper consent checkbox
- Performance: pagination / lazy loading for large dish lists
- Smoke tests (critical flows)
