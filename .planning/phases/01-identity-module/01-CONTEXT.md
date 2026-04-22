# Phase 1: Identity Module - Context

**Gathered:** 2026-04-22
**Status:** Ready for planning
**Source:** PRD Express Path (tasks/REQUIREMENTS.md + tasks/design-brief.md)

<domain>
## Phase Boundary

Phase 1 delivers the complete customer identity layer:
- DisclaimerScreen (mandatory legal gate, first run only)
- Login (RTDB-based, manual credential check)
- Registration (extended: nome, cognome, allergen chip selector)
- ProfileScreen (3rd tab in ClienteScreen bottom nav)
- Session persistence (SharedPreferences, survives process kill)
- Logout (clears session, clean back stack)
- AllergeneType enum + AllergeneChipSelector composable (reusable)
- SessionManager helper (SharedPreferences wrapper)
- MainActivity startup routing

**What this phase does NOT cover:**
- Firebase Auth (deferred v2)
- Password change/reset
- Account deletion (GDPR Art. 17, deferred v2)
- O(n) search/offers fix (Phase 2)
- Restaurant side (Phase 4)
- Firebase Security Rules (Phase 5)

</domain>

<decisions>
## Implementation Decisions

### Navigation & Startup Routing
- `MainActivity.onCreate` reads SharedPreferences BEFORE `setContent`
- Reads `disclaimer_accepted`, `session_user_id`, `session_user_type`
- Passes `initialScreen: String` to `IngredientApp` composable
- Routing logic: if `disclaimer_accepted == false` → "disclaimer"; else if `session_user_id` non-empty → route to "cliente" or "ristoratore"; else → "login"
- `IngredientApp` composable receives `initialScreen: String` parameter (currently it doesn't — must add)

### DisclaimerScreen
- Shown ONLY when `disclaimer_accepted` is absent or false in SharedPreferences
- Full-screen, no bottom nav bar
- Single button "I understand" → saves `disclaimer_accepted = true`, navigates to LoginScreen
- Back button → `BackHandler { finish() }` (closes app, no navigate-back)
- Disclaimer text exact: "This app is a support tool only. Always verify ingredients and allergen information directly with the restaurant before ordering. We do not guarantee the accuracy of the data shown in this app."

### AuthScreens.kt — Extend, Do NOT Rewrite
- `LoginScreen`: ADD proper error messages for all failure cases; ensure no password in logs
- `RegistrationScreen`: ADD `nome`, `cognome` fields and `AllergeneChipSelector` component
- Minimal diff policy: add fields and components, do NOT refactor or restructure existing code
- Password field must use PasswordVisualTransformation (already implemented, verify)

### Registration Validation (client-side, before Firebase)
- nome: non-blank
- cognome: non-blank
- email: non-blank (basic format acceptable v1)
- password: min 6 chars
- confirmPassword: must equal password
- Error messages: "Please fill in all fields" / "Passwords do not match" / "Email already in use"

### Firebase Schema for User Node
```
users/{pushId}/
  nome:       String
  cognome:    String
  email:      String
  password:   String  ← plaintext v1, accepted risk
  userType:   String  ("Cliente" | "Ristoratore")
  allergeni:  List<String>  (e.g. ["GLUTINE", "LATTE"])
  createdAt:  Long    (System.currentTimeMillis())
```
- `databaseRef.child("users").push()` for new nodes
- `userId` = the key returned by push()

### AllergeneType Enum
- Package: `com.example.ingredient.model`
- File: `AllergeneType.kt`
- 14 values (exact): GLUTINE, CROSTACEI, UOVA, PESCE, ARACHIDI, SOIA, LATTE, FRUTTA_SECCA, SEDANO, SENAPE, SESAMO, ANIDRIDE_SOLFOROSA, LUPINI, MOLLUSCHI
- Display names map: Glutine, Crostacei, Uova, Pesce, Arachidi, Soia, Latte, Frutta a guscio, Sedano, Senape, Sesamo, Anidride solforosa e solfiti, Lupini, Molluschi
- Stored in Firebase as `.name` (uppercase string)

### User Data Class
- Package: `com.example.ingredient.model`
- File: `User.kt`
```kotlin
data class User(
    val id: String = "",
    val nome: String = "",
    val cognome: String = "",
    val email: String = "",
    val password: String = "",
    val userType: String = "",
    val allergeni: List<String> = emptyList(),
    val createdAt: Long = 0L
)
```

### SessionManager
- Package: `com.example.ingredient.model` (or `util`)
- File: `SessionManager.kt`
- SharedPreferences file name: `ingredient_session`
- Keys: `session_user_id` (String), `session_user_type` (String), `disclaimer_accepted` (Boolean)
- Functions: `saveSession(userId, userType)`, `getUserId()`, `getUserType()`, `isLoggedIn()`, `logout()`, `isDisclaimerAccepted()`, `setDisclaimerAccepted()`

### AllergeneChipSelector
- Reusable composable, used in RegistrationScreen AND ProfileScreen
- Signature:
```kotlin
@Composable
fun AllergeneChipSelector(
    selected: List<AllergeneType>,
    onSelectionChange: (List<AllergeneType>) -> Unit,
    modifier: Modifier = Modifier
)
```
- Uses FlowRow (or LazyVerticalGrid) to lay out all 14 chips
- Selected chips: `FilterChip` with `selected = true`, visually distinct (filled)
- Must be scrollable if exceeds screen height

### ProfileScreen
- Third tab in `ClienteScreen` bottom nav (alongside Search and Offers tabs)
- Tab icon: person icon, label "Profile"
- Accessible only to userType="Cliente" logged-in users
- Load: `databaseRef.child("users").child(userId).addListenerForSingleValueEvent`
- Shows nome/cognome/email as non-editable `Text` (not OutlinedTextField)
- Shows allergen chips via `AllergeneChipSelector`
- "Save" button: updates `users/{userId}/allergeni` only (not full node)
- Snackbar: "Saved!" on success / "Could not save. Try again." on failure
- Loading state: show CircularProgressIndicator while Firebase loads

### Session Save/Clear
- After successful login: `sessionManager.saveSession(userId, userType)`
- After successful registration: `sessionManager.saveSession(userId, userType)`
- After logout: `sessionManager.logout()`, then navigate to "login" with cleared back stack

### Security Constraints
- Password must NEVER appear in any Log.d / Log.e call (RNF-SEC-001)
- App must NEVER read `users/` node except for: (a) login check, (b) own-profile load, (c) own-profile update
- No `userId` displayed in UI

### Claude's Discretion
- Exact Compose theme/color for chip selector (use existing app theme)
- Column scroll wrapping in ProfileScreen (use `Column(modifier = Modifier.verticalScroll(...))`)
- Error state representation (use `mutableStateOf<String?>(null)` as in existing screens)
- Loading state in ProfileScreen (use `mutableStateOf(true)`)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Architecture & Decisions
- `tasks/design-brief.md` — Full SINTIA design brief with WHO/WHERE/WHEN/EXCLUDED privacy constraints (locked decisions)
- `tasks/REQUIREMENTS.md` — Full requirements spec with acceptance criteria (REQ-ID-001 through REQ-ID-007 + non-functional)
- `tasks/CONTRACT.md` — Public contract: data classes, Firebase schema, SharedPreferences keys, Composable signatures

### Existing Code to Extend
- `app/src/main/java/com/example/ingredient/AuthScreens.kt` — Existing LoginScreen + RegistrationScreen (EXTEND, do not rewrite)
- `app/src/main/java/com/example/ingredient/ClienteScreens.kt` — Existing customer screens (add ProfileScreen tab)
- `app/src/main/java/com/example/ingredient/MainActivity.kt` — App entry point (add initialScreen routing)

### Project State
- `.planning/STATE.md` — Architectural decisions, known tech debt, stack
- `.planning/codebase/ARCHITECTURE.md` — How navigation/screens currently work
- `.planning/codebase/CONCERNS.md` — Known issues with file+line references

</canonical_refs>

<specifics>
## Specific Ideas

### 14 EU Allergens (exact names and enum values)
| Display Name | AllergeneType.name |
|---|---|
| Glutine | GLUTINE |
| Crostacei | CROSTACEI |
| Uova | UOVA |
| Pesce | PESCE |
| Arachidi | ARACHIDI |
| Soia | SOIA |
| Latte | LATTE |
| Frutta a guscio | FRUTTA_SECCA |
| Sedano | SEDANO |
| Senape | SENAPE |
| Sesamo | SESAMO |
| Anidride solforosa e solfiti | ANIDRIDE_SOLFOROSA |
| Lupini | LUPINI |
| Molluschi | MOLLUSCHI |

### Verification Criteria (from design-brief.md)
- C1: Register with nome/cognome/allergeni → Firebase node contains all fields with correct types
- C2: Login → close → reopen → ClienteScreen shown (no re-login)
- C3: Wrong password → "Invalid email or password" on screen
- C4: Offline login → "Connection error. Check your internet."
- C5: First install → DisclaimerScreen first; Search tab NOT reachable before "I understand"
- C6: Second launch → DisclaimerScreen does NOT appear
- C7: ProfileScreen: nome/cognome non-editable, allergens in chip selector
- C8: Select allergens → Save → reopen → chips still selected
- C9: password != confirmPassword → error, Firebase NOT called
- C10: Email already used → "Email already in use"
- C11: Logout → Back → app closes
- C12: SharedPreferences cleared after logout

</specifics>

<deferred>
## Deferred Ideas

- Firebase Authentication (password hashing, token-based) — v2, tracked in tasks/TODOS.md
- Account deletion (GDPR Art. 17) — v2
- Session expiry / token refresh — v2
- Privacy policy screen with formal consent checkbox — v2
- Allergen crash report exclusion (Crashlytics) — v2
- Profile editing of email/password — v2 (requires Firebase Auth)
- Admin interface — not in scope

</deferred>

---

*Phase: 01-identity-module*
*Context gathered: 2026-04-22 via PRD Express Path (tasks/REQUIREMENTS.md + tasks/design-brief.md)*
