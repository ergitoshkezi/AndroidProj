# Research Questions


## Q: Reliable DOM signals for menu content on Italian restaurant sites
**Date:** 2026-05-20
**Context:** DOM segmentation layer — DomBlockScorer signal calibration
**Question:** What class names, aria-roles, schema.org types, and data-* attributes
are most reliably associated with menu content on Italian restaurant sites
(vs. TheFork, TripAdvisor embeds, delivery widgets)?

**Specific unknowns:**
- Which schema.org types appear on menu pages? (Menu, MenuItem, FoodEstablishment?)
- Do major platforms (TheFork/LaFourchette, Deliveroo, JustEat) use consistent class patterns?
- What percentage of Italian restaurant sites use structured data vs. raw HTML menus?
- Are there common accordion patterns (Bootstrap, custom) for menu categories in Italy?

**Impact:** Calibrates semanticRoleScore and classNameScore weights in DomBlockScorer.
High-signal class patterns could be added to RegexRegistry.MENU_BLOCK_CLASS_PATTERN.
