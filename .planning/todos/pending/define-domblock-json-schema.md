---
title: Define DomBlock JSON schema — JS↔Kotlin contract
date: 2026-05-20
priority: high
---

# Todo: Define DomBlock JSON schema

## Context
The DOM segmentation architecture requires a clean contract between the JS extraction
layer (WebView) and the Kotlin scoring layer. This schema must be stable before
implementing either side.

## Task
Define and document the `DomBlock` JSON schema as the canonical JS↔Kotlin contract:

1. Specify all fields in `DomBlock`:
   - `index`, `tag`, `id`, `classes[]`
   - `depth`, `childCount`, `siblingCount`
   - `textLength`, `directText`, `subtreeText`
   - `priceHits`, `foodHits`
   - `visible` (from computed style)
   - `rect` (x, y, width, height)
   - `semanticRole` (aria-role, data-* attributes)
   - `schemaType` (schema.org itemtype)
   - `xpath`

2. Create Kotlin data classes:
   - `DomBlock`
   - `DomSnapshot` (wrapper with `blocks[]`, `url`, `extractedAt`)
   - `DomRect`

3. Write unit test fixtures: 3 example `DomSnapshot` JSON files
   (Italian restaurant, platform site like TheFork, broken/minimal HTML)

4. Validate schema round-trips: JS serializes → Kotlin Gson deserializes → no data loss

## Definition of Done
- [ ] `DomBlock.kt` data class in `parser/dom/` package
- [ ] `DomSnapshot.kt` data class
- [ ] 3 JSON fixture files in `src/test/resources/dom_fixtures/`
- [ ] Round-trip unit test passes
