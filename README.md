# Toolkits — Jetpack Compose Port

New Kotlin + Jetpack Compose + Material 3 Expressive app, ported from `../Toolkits-VIEW/` (original View/ViewBinding app).

## Structure

- Single-activity Compose (`MainActivity` + `ToolkitsNavHost` string routes)
- Theme: `ui/theme/` — seed palette (green/blue/purple/red/orange) + dynamic color + system/light/dark/amoled, inspired by Zenith `Theme.kt` + original `App.SCHEME_SEEDS`
- Preferences: DataStore (`data/preferences/UserPreferencesRepository`) with same keys as original `BroadcastConstants`
- Backend 1:1 from original: `helper/`, `model/`, `constant/`, `service/` (ZIP/7z/TAR/RAR, split, multipart, fallback chain, Zip-Slip guards, atomic writes)
- Screens (all original features):
  - Home (ZIP Tools / Text Tools / Settings)
  - ZipTools: Extract (SAF 4-step resolve, listing, selective, password, dest) / Compress (ZIP/7z/TAR.*, level, AES, solid, split KB/MB/GB)
  - TextTools hub: Base64 (encode/decode/swap/copy), TextInfo (lines/words/emojis/top-words/reading-time), FileViewer (50k lines + search), Projects (tree, create/rename/delete, JSON import, traversal guard, editor), FancyText (51 styles + Discord filter + 13 symbol categories)
  - Settings (seeds, dynamic, theme, paths, hide toggles, ZIP encryption default, about)

## Build

```
cd Toolkits
./gradlew assembleDebug
```

Requires Android SDK 36, JDK 17. ARM ABIs only (`armeabi-v7a`, `arm64-v8a`).

## Notes

- Services still read `PreferenceManager` defaults for extract/archive paths; Settings UI writes DataStore — unify in next pass if needed.
- `activity.MainActivity` references patched to root `MainActivity`.
- Original kept untouched in `../Toolkits-VIEW/` for reference. Zenith kept in `../Zenith/` for UX reference only.
