# CONTRACT.md — Identity Module
Version: 1.0 | Status: APPROVED | Date: 2026-04-22

This file defines the public contract for the Identity Module: data classes, enums,
Composable function signatures, navigation routes, Firebase paths, and SharedPreferences
keys that other modules depend on.

**Rule:** Any breaking change to this contract (renamed field, changed type, removed
route) requires updating ALL consumers listed in the Dependencies table below.

---

## Data Classes & Enums

```kotlin
// File: com/example/ingredient/model/User.kt  (create new)

data class User(
    val userId: String = "",
    val email: String = "",
    val userType: UserType = UserType.CLIENTE,
    val nome: String = "",
    val cognome: String = "",
    val allergeni: List<AllergeneType> = emptyList(),
    val createdAt: Long = 0L
)

enum class UserType(val rtdbValue: String) {
    CLIENTE("Cliente"),
    RISTORATORE("Ristoratore");

    companion object {
        fun fromString(value: String?): UserType =
            values().firstOrNull { it.rtdbValue == value } ?: CLIENTE
    }
}

// 14 EU Official Allergens — Regulation (EU) No 1169/2011 Annex II
enum class AllergeneType(val displayName: String) {
    GLUTINE("Glutine"),
    CROSTACEI("Crostacei"),
    UOVA("Uova"),
    PESCE("Pesce"),
    ARACHIDI("Arachidi"),
    SOIA("Soia"),
    LATTE("Latte"),
    FRUTTA_SECCA("Frutta a guscio"),
    SEDANO("Sedano"),
    SENAPE("Senape"),
    SESAMO("Sesamo"),
    ANIDRIDE_SOLFOROSA("Anidride solforosa e solfiti"),
    LUPINI("Lupini"),
    MOLLUSCHI("Molluschi");

    companion object {
        // Safe parse: unknown values (e.g. old free-text data) are silently dropped
        fun fromStringList(values: List<String>?): List<AllergeneType> =
            values?.mapNotNull { v -> runCatching { valueOf(v) }.getOrNull() } ?: emptyList()
    }
}

// Session stored in SharedPreferences
data class UserSession(
    val userId: String,
    val userType: UserType
)
```

---

## Firebase Schema — Identity Module Paths

The Identity Module OWNS these paths. No other module should write to them.

```
/users
  /{userId}                   ← Firebase Database push() ID, generated at registration
      email:        String    ← required, unique (enforced by app logic)
      password:     String    ← PLAINTEXT v1. Migrate to Firebase Auth in v2.
      userType:     String    ← "Cliente" | "Ristoratore"
      nome:         String    ← required
      cognome:      String    ← required
      allergeni:    Object    ← Firebase stores List<String> as {"0":"GLUTINE","1":"LATTE"}
                                 Read with: .getValue(object : GenericTypeIndicator<List<String>>() {})
      createdAt:    Long      ← System.currentTimeMillis() at registration
```

**Read access pattern:**
```kotlin
// Own profile load
databaseReference.child("users").child(userId)
    .addListenerForSingleValueEvent(...)

// Login credential check (only valid use of users/ scan)
databaseReference.child("users")
    .addListenerForSingleValueEvent(...)  // iterate children, match email+password
```

**NEVER read:**
```kotlin
// Other user's profile — forbidden in customer-side app
databaseReference.child("users").child(someOtherUserId)...
```

---

## SharedPreferences Contract

```kotlin
// File name
const val PREFS_NAME = "ingredient_session"

// Keys
const val KEY_USER_ID         = "session_user_id"      // String, empty = not logged in
const val KEY_USER_TYPE       = "session_user_type"    // String ("Cliente"|"Ristoratore"|"")
const val KEY_DISCLAIMER_SEEN = "disclaimer_accepted"  // Boolean, default false

// Read session
fun readSession(context: Context): UserSession? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val userId = prefs.getString(KEY_USER_ID, "") ?: ""
    val userType = prefs.getString(KEY_USER_TYPE, "") ?: ""
    return if (userId.isNotEmpty()) UserSession(userId, UserType.fromString(userType)) else null
}

// Write session
fun saveSession(context: Context, userId: String, userType: UserType) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString(KEY_USER_ID, userId)
        .putString(KEY_USER_TYPE, userType.rtdbValue)
        .apply()
}

// Clear session
fun clearSession(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .remove(KEY_USER_ID)
        .remove(KEY_USER_TYPE)
        .apply()
    // NOTE: disclaimer_accepted is intentionally NOT cleared on logout
}
```

---

## Navigation Routes

These string constants are used in `IngredientApp` composable (`MainActivity.kt`).

```
Route              Screen               Condition
──────────────────────────────────────────────────────────────────────
"Disclaimer"    → DisclaimerScreen     First run only (disclaimer_accepted = false)
"Login"         → LoginScreen          Default entry point (after disclaimer)
"Register"      → RegistrationScreen   From Login ("Don't have an account?")
"Cliente"       → ClienteScreen        After login/register with userType=Cliente
"Ristoratore"   → RistoratoreScreen    After login/register with userType=Ristoratore
```

**Back stack rules:**
- `Disclaimer` → no back navigation (close app)
- `Login` → no back to `Disclaimer`
- `Cliente` → no back to `Login` after successful auth (replace back stack)

---

## Composable Signatures (Public API)

### Existing — extend, do NOT rewrite

```kotlin
// MainActivity.kt — IngredientApp now takes initialScreen param
// MainActivity.onCreate reads SharedPreferences BEFORE setContent and passes result:
//
//   val prefs = getSharedPreferences("ingredient_session", MODE_PRIVATE)
//   val disclaimerSeen = prefs.getBoolean("disclaimer_accepted", false)
//   val savedUserId = prefs.getString("session_user_id", "") ?: ""
//   val savedUserType = prefs.getString("session_user_type", "") ?: ""
//   val initialScreen = when {
//       !disclaimerSeen       -> "Disclaimer"
//       savedUserId.isNotEmpty() -> if (savedUserType == "Ristoratore") "Ristoratore" else "Cliente"
//       else                  -> "Login"
//   }
//   setContent { IngredientApp(..., initialScreen = initialScreen) }

@Composable
fun IngredientApp(
    tesseractManager: EnhancedTesseractManager,
    databaseReference: DatabaseReference,
    initialScreen: String = "Login"  // add this param
)

// AuthScreens.kt — existing, extend in place
@Composable
fun LoginScreen(
    databaseReference: DatabaseReference,
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: (userId: String, userType: String) -> Unit,  // keep String for now
    modifier: Modifier = Modifier
)

@Composable
fun RegistrationScreen(
    databaseReference: DatabaseReference,
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: (userId: String, userType: String) -> Unit,  // keep String for now
    modifier: Modifier = Modifier
)
// Add to RegistrationScreen: nome, cognome fields + AllergeneChipSelector
```

### New — to be created

```kotlin
// AuthScreens.kt or new DisclaimerScreen.kt
@Composable
fun DisclaimerScreen(
    onAccepted: () -> Unit,  // called on "I understand" tap
    modifier: Modifier = Modifier
)

// ProfileScreen.kt (new file) or added to ClienteScreens.kt
@Composable
fun ProfileScreen(
    userId: String,
    databaseReference: DatabaseReference,
    modifier: Modifier = Modifier
)

// Reusable component — can live in ProfileScreen.kt or AllergeneChipSelector.kt
@Composable
fun AllergeneChipSelector(
    selected: List<AllergeneType>,
    onSelectionChange: (List<AllergeneType>) -> Unit,
    modifier: Modifier = Modifier
)
```

---

## `AllergeneType` Allergen Warning Contract (for Search Module)

The Search module uses `AllergeneType` to compute ⚠️ warnings. This is the ONLY
cross-module dependency on this contract.

```kotlin
// Search module usage pattern (ClienteScreens.kt)
fun computeAllergenWarning(
    userAllergeni: List<AllergeneType>,
    dishAllergeni: List<String>?  // raw strings from Firebase
): AllergenWarningState {
    if (dishAllergeni == null) return AllergenWarningState.UNDECLARED
    val dishEnum = AllergeneType.fromStringList(dishAllergeni)
    val overlap = userAllergeni.intersect(dishEnum.toSet())
    return if (overlap.isNotEmpty()) AllergenWarningState.WARNING(overlap.map { it.displayName })
           else AllergenWarningState.SAFE
}

sealed class AllergenWarningState {
    object SAFE : AllergenWarningState()
    object UNDECLARED : AllergenWarningState()  // dish has null/missing allergen data
    data class WARNING(val matchedAllergens: List<String>) : AllergenWarningState()
}
```

---

## Files Modified / Created by This Module

| File | Action | Reason |
|---|---|---|
| `AuthScreens.kt` | EXTEND | Add nome, cognome, AllergeneChipSelector to RegistrationScreen |
| `MainActivity.kt` | EXTEND | Add Disclaimer route, session persistence check on launch |
| `model/User.kt` | CREATE | User, UserType, AllergeneType, UserSession data classes |
| `DisclaimerScreen.kt` | CREATE | One-time legal disclaimer screen |
| `ProfileScreen.kt` | CREATE | View/edit allergen profile (or add as tab in ClienteScreens.kt) |
| `SessionManager.kt` | CREATE | SharedPreferences read/write/clear helpers |

---

## Dependencies

| Consumer | Depends On | What It Uses |
|---|---|---|
| `ClienteScreens.kt` | `AllergeneType` | Allergen warning computation on search results |
| `ClienteScreens.kt` | `UserSession` / `userId` | Passed in from IngredientApp state |
| `IngredientApp` (MainActivity.kt) | Navigation routes | Routing logic |
| `IngredientApp` (MainActivity.kt) | `SessionManager` | Reads session on launch |
| `AuthScreens.kt` | `AllergeneType`, `SessionManager` | Registration writes session |
| `FirebaseMenuUploader.kt` | NONE | Restaurant side — must NOT read user identity paths |

---

## Breaking Change Policy

These changes require coordinated update of ALL consumers listed above:

- Renaming an `AllergeneType` enum value (also changes Firebase-stored strings — MIGRATION REQUIRED)
- Renaming SharedPreferences file name or keys
- Changing `users/{userId}` Firebase path
- Removing or renaming a navigation route string
- Changing `onLoginSuccess` / `onRegisterSuccess` callback signatures

Non-breaking changes (internal only, no consumer update needed):
- Adding a new `AllergeneType` enum value (new EU regulation update)
- Adding a new optional field to `User` data class
- Internal implementation changes to `SessionManager`
