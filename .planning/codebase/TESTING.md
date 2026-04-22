# Testing Patterns

**Analysis Date:** 2026-04-22

## ⚠️ Critical Gap: No Application Tests Exist

The project contains **zero custom test files**. Both test directories contain only Android Studio scaffold placeholders that were auto-generated at project creation and have never been modified.

---

## Test Framework Setup

**Unit Test Runner:**
- JUnit 4 — via `testImplementation(libs.junit)` in `app/build.gradle.kts` (line 87)
- Config: `app/build.gradle.kts` — `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`

**Instrumented Test Runner:**
- AndroidJUnit4 — via `androidTestImplementation(libs.androidx.junit)` (line 88)
- Espresso Core — via `androidTestImplementation(libs.androidx.espresso.core)` (line 89)

**Compose UI Testing:**
- `androidTestImplementation(libs.androidx.compose.ui.test.junit4)` (line 91)
- `debugImplementation(libs.androidx.compose.ui.test.manifest)` (line 95)
- Compose BOM included for test: `androidTestImplementation(platform(libs.androidx.compose.bom))` (line 90)

**Missing test dependencies (not declared):**
- No MockK or Mockito for mocking
- No Kotlin Coroutines test library (`kotlinx-coroutines-test`)
- No Turbine (Flow testing)
- No Robolectric (local Android unit tests without device)

---

## Test Directory Structure

```
app/src/
├── test/
│   └── java/com/example/ingredient/
│       └── ExampleUnitTest.kt          ← SCAFFOLD ONLY (never modified)
└── androidTest/
    └── java/com/example/ingredient/
        └── ExampleInstrumentedTest.kt  ← SCAFFOLD ONLY (never modified)
```

### Scaffold Files (Not Real Tests)

**`app/src/test/java/com/example/ingredient/ExampleUnitTest.kt`**
```kotlin
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)  // Auto-generated placeholder only
    }
}
```

**`app/src/androidTest/java/com/example/ingredient/ExampleInstrumentedTest.kt`**
```kotlin
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.ingredient", appContext.packageName)  // Auto-generated placeholder only
    }
}
```

Both files test nothing meaningful about the application. They exist solely as Android Studio project templates.

---

## What Is NOT Tested (Complete Coverage Gap)

### Pure Logic — Highest Priority (Unit Testable)

**`MenuParser.kt` — `parseMenuText()`, `cleanPrice()`, `extractJsonFromPart()`**
- `MenuParser` has zero Firebase or Android dependencies — it's 100% pure Kotlin
- `parseMenuText()` takes a `String`, returns `List<MenuCategory>` — trivially unit testable
- `cleanPrice()` normalizes price strings (`"€12,50"` → `"12.50"`) — obvious test cases
- `extractJsonFromPart()` extracts JSON arrays from LLM responses — tests needed for malformed input
- **Risk:** Silent failures produce empty menus with no user feedback

**`FirebaseMenuUploader.sanitizeKey()`**
- Private method but its behavior affects Firebase path correctness
- Characters like `/`, `.`, `$`, `#`, `[`, `]` must be stripped from category names
- Could be tested by making it internal or via integration tests

### Authentication Logic — High Priority

**`AuthScreens.kt` — Login and Registration validation**
- Password length check (`< 6 characters`) — unit testable if extracted
- Password match validation (`password != confirmPassword`) — unit testable if extracted
- Email duplicate detection logic — currently embedded in Firebase callback, not extractable without refactor
- **Risk:** Validation bypasses are undetectable; password comparison is plain-text

### Search Logic — High Priority

**`ClienteScreens.kt` — `performSearch()`, `fetchOffers()`**
- `performSearch()` parses comma-separated queries and scores dishes against ingredients
- Match scoring logic (counting occurrences) is business-critical but untested
- Filter logic in `LaunchedEffect` (distance, country, region, price sort) is testable in isolation
- **Risk:** Search returning wrong results silently

### Firebase Data Layer — Medium Priority

**`FirebaseMenuUploader.kt` — `uploadMenu()`, `getMenu()`**
- `getMenu()` parses Firebase `DataSnapshot` tree into `List<MenuCategory>`
- Price parsing with fallback (`toDoubleOrNull()`) — edge cases untested
- `uploadMenu()` clears and rewrites entire menu — destructive operation with no test coverage
- Requires Firebase emulator or mocking to test

### UI Smoke Tests — Lower Priority (Requires Device/Emulator)

**`LoginScreen` / `RegistrationScreen`**
- Form validation messages displayed correctly
- Button disabled state during loading
- Error message cleared on retry

**`ClienteScreen` — navigation between tabs**

**`MenuEditorScreen` — edit/delete dish dialogs**

---

## How to Add Tests

### Unit Tests (no device needed)

Place new unit test files in:
```
app/src/test/java/com/example/ingredient/
```

**Run command:**
```bash
./gradlew test
```

### Instrumented / Compose UI Tests (requires device or emulator)

Place new instrumented test files in:
```
app/src/androidTest/java/com/example/ingredient/
```

**Run command:**
```bash
./gradlew connectedAndroidTest
```

### Starting point — MenuParser (no dependencies, immediate value):

```kotlin
// app/src/test/java/com/example/ingredient/MenuParserTest.kt
class MenuParserTest {
    private val parser = MenuParser()

    @Test
    fun `cleanPrice removes euro symbol and normalizes comma`() {
        // test via parseMenuText with known JSON input
    }

    @Test
    fun `parseMenuText returns empty list for blank input`() {
        val result = parser.parseMenuText("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseMenuText parses valid JSON array correctly`() {
        val input = """
            === PARTE 1 ===
            [{"Categoria":"Antipasti","Piatti":"Bruschetta","Descrizione/Ingredienti/Extra":"Tomato","Allergeni":"","Prezzo":"5.50"}]
        """.trimIndent()
        val result = parser.parseMenuText(input)
        assertEquals(1, result.size)
        assertEquals("Antipasti", result[0].categoryName)
        assertEquals("Bruschetta", result[0].dishes[0].name)
    }
}
```

### Required additional dependencies for proper testing:

Add to `app/build.gradle.kts`:
```kotlin
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
testImplementation("io.mockk:mockk:1.13.8")
testImplementation("com.google.firebase:firebase-database-ktx:20.3.0")  // Firebase test utils if needed
```

---

## Test Coverage Summary

| Area | Coverage | Priority |
|------|----------|----------|
| `MenuParser` — parsing logic | ❌ None | 🔴 High — pure Kotlin, no excuses |
| `MenuParser.cleanPrice` | ❌ None | 🔴 High — price corruption silently shows €0.00 |
| Auth validation logic | ❌ None | 🔴 High — security-adjacent |
| `FirebaseMenuUploader.getMenu` | ❌ None | 🟠 Medium — needs emulator |
| `FirebaseMenuUploader.uploadMenu` | ❌ None | 🟠 Medium — destructive, needs emulator |
| Search scoring / `performSearch` | ❌ None | 🟠 Medium — core feature |
| Filter logic in `SearchTab` | ❌ None | 🟠 Medium — extractable |
| `sanitizeKey` | ❌ None | 🟡 Low — private method |
| Compose UI screens | ❌ None | 🟡 Low — requires device |
| `LLMApiClient` network calls | ❌ None | 🟡 Low — external API |

**Overall coverage: 0%**

---

*Testing analysis: 2026-04-22*
