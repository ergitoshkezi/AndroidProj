---
phase: 01-identity-module
plan: C
type: execute
wave: 2
depends_on: [01-A]
files_modified:
  - app/src/main/java/com/example/ingredient/AuthScreens.kt
autonomous: true
requirements: [REQ-ID-004]

must_haves:
  truths:
    - "AllergeneChipSelector shows all 14 EU allergens as toggleable chips"
    - "Chips use displayName property for labels (e.g., 'Frutta a guscio' not 'FRUTTA_SECCA')"
    - "Selected chips are visually distinct (FilterChip selected=true)"
    - "Selection changes propagate via onSelectionChange callback"
  artifacts:
    - path: "app/src/main/java/com/example/ingredient/AuthScreens.kt"
      provides: "AllergeneChipSelector composable"
      contains: "fun AllergeneChipSelector"
  key_links:
    - from: "AllergeneChipSelector"
      to: "AllergeneType.entries"
      via: "iterates all enum values"
      pattern: "AllergeneType\\.entries\\.forEach"
---

<objective>
Create the AllergeneChipSelector reusable composable that displays all 14 EU allergens as toggleable FilterChips in a FlowRow layout.

Purpose: REQ-ID-004 requires a reusable chip selector for RegistrationScreen and ProfileScreen. Build it once, use in both places.

Output: AllergeneChipSelector composable added to AuthScreens.kt, ready for use in Plans D and E.
</objective>

<execution_context>
@~/.copilot/get-shit-done/workflows/execute-plan.md
@~/.copilot/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/ROADMAP.md
@.planning/phases/01-identity-module/01-CONTEXT.md
@.planning/phases/01-identity-module/01-PATTERNS.md
@tasks/CONTRACT.md

# Read BEFORE modifying
@app/src/main/java/com/example/ingredient/AuthScreens.kt

# Interfaces from Plan A
<interfaces>
From model/AllergeneType.kt (created in Plan A):
```kotlin
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
    MOLLUSCHI("Molluschi")
}
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add FlowRow imports and OptIn annotation</name>
  <files>app/src/main/java/com/example/ingredient/AuthScreens.kt</files>
  <read_first>
    - app/src/main/java/com/example/ingredient/AuthScreens.kt (lines 1-20 — existing imports)
    - .planning/phases/01-identity-module/01-PATTERNS.md (lines 616-658 — AllergeneChipSelector pattern with FlowRow)
  </read_first>
  <action>
Add required imports to AuthScreens.kt for FlowRow and AllergeneType.

Add these imports at the top of the file (after existing imports):
```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.example.ingredient.model.AllergeneType
```

Note: `Arrangement` may already be imported. FlowRow requires `@OptIn(ExperimentalLayoutApi::class)` annotation on the composable that uses it.
  </action>
  <verify>
    <automated>grep -c "import androidx.compose.foundation.layout.FlowRow" app/src/main/java/com/example/ingredient/AuthScreens.kt</automated>
  </verify>
  <acceptance_criteria>
    - AuthScreens.kt contains `import androidx.compose.foundation.layout.FlowRow`
    - AuthScreens.kt contains `import androidx.compose.foundation.layout.ExperimentalLayoutApi`
    - AuthScreens.kt contains `import com.example.ingredient.model.AllergeneType`
  </acceptance_criteria>
  <done>FlowRow and AllergeneType imports added to AuthScreens.kt</done>
</task>

<task type="auto">
  <name>Task 2: Create AllergeneChipSelector composable</name>
  <files>app/src/main/java/com/example/ingredient/AuthScreens.kt</files>
  <read_first>
    - app/src/main/java/com/example/ingredient/AuthScreens.kt (entire file — find best location to add)
    - .planning/phases/01-identity-module/01-PATTERNS.md (lines 616-658 — full AllergeneChipSelector implementation)
    - tasks/CONTRACT.md (lines 230-237 — AllergeneChipSelector signature)
  </read_first>
  <action>
Add AllergeneChipSelector composable to AuthScreens.kt. Place it at the END of the file (after RegistrationScreen).

Add this composable:
```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllergeneChipSelector(
    selected: List<AllergeneType>,
    onSelectionChange: (List<AllergeneType>) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AllergeneType.entries.forEach { allergen ->
            FilterChip(
                selected = allergen in selected,
                onClick = {
                    onSelectionChange(
                        if (allergen in selected) selected - allergen else selected + allergen
                    )
                },
                label = { Text(allergen.displayName) }
            )
        }
    }
}
```

Key implementation details:
- `@OptIn(ExperimentalLayoutApi::class)` required for FlowRow
- `AllergeneType.entries` iterates all 14 enum values
- `allergen.displayName` shows human-readable label (e.g., "Frutta a guscio")
- Toggle logic: if selected, remove; if not selected, add
- `modifier.fillMaxWidth()` ensures chips wrap properly
- `Arrangement.spacedBy(8.dp)` for horizontal spacing, `4.dp` for vertical
  </action>
  <verify>
    <automated>grep -c "fun AllergeneChipSelector" app/src/main/java/com/example/ingredient/AuthScreens.kt</automated>
  </verify>
  <acceptance_criteria>
    - AuthScreens.kt contains `@OptIn(ExperimentalLayoutApi::class)`
    - AuthScreens.kt contains `@Composable fun AllergeneChipSelector(`
    - AuthScreens.kt contains `selected: List<AllergeneType>`
    - AuthScreens.kt contains `onSelectionChange: (List<AllergeneType>) -> Unit`
    - AuthScreens.kt contains `AllergeneType.entries.forEach`
    - AuthScreens.kt contains `allergen.displayName`
    - AuthScreens.kt contains `FilterChip(`
    - AuthScreens.kt contains `selected = allergen in selected`
    - AuthScreens.kt contains `if (allergen in selected) selected - allergen else selected + allergen`
  </acceptance_criteria>
  <done>AllergeneChipSelector composable created with FlowRow layout and toggle logic</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| None | This is a pure UI component with no I/O |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-01-C-01 | N/A | AllergeneChipSelector | accept | Pure UI component. No sensitive data handling. Allergen selection is user-controlled. |
</threat_model>

<verification>
After all tasks complete:
1. Build compiles: `./gradlew :app:compileDebugKotlin`
2. AllergeneChipSelector function exists in AuthScreens.kt
3. All 14 allergens rendered when component used (verified in Plan D/E)
</verification>

<success_criteria>
- [ ] FlowRow import added
- [ ] AllergeneType import added
- [ ] @OptIn(ExperimentalLayoutApi::class) annotation present
- [ ] AllergeneChipSelector composable exists with correct signature
- [ ] Uses AllergeneType.entries to iterate all allergens
- [ ] Uses allergen.displayName for chip labels
- [ ] Toggle logic correctly adds/removes from selection
- [ ] Build compiles successfully
</success_criteria>

<output>
After completion, create `.planning/phases/01-identity-module/01-C-SUMMARY.md`
</output>
