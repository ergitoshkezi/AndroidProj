# Suggested Commands

## Build & Install
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
cd "C:\Users\ErgiToshkezi\AndroidStudioProjects\Ingredient\Ingredient"

# Build + install (phone must be connected via USB)
.\gradlew installDebug

# Build APK only (no device needed)
.\gradlew assembleDebug
# APK output: app\build\outputs\apk\debug\app-debug.apk
```

## System Commands (Windows)
- List files: `Get-ChildItem` or `dir`
- Find text: `Select-String` or use grep tool
- Path separator: `\` (backslash)
- No bash/zsh — use PowerShell only

## No linting/testing configured
- No existing test suite to run
- No lint step in CI
- Just build + install + manual test on device
