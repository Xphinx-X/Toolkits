# AGENT.md — Toolkits Project Handoff

> Read this first if you are picking up work on Toolkits. It contains everything
> learned so far: layout, stack, CI, hard-won build fixes, and what's left.

## 1. Workspace layout (device paths)

```
/root/Main2/OpenCode/Bot-Runner/
├── Toolkits/        ← ACTIVE project (Jetpack Compose port). Git repo → github.com/Xphinx-X/Toolkits (main)
├── Toolkits-VIEW/   ← ORIGINAL app (View/ViewBinding). READ-ONLY reference. Never edit.
│                      Has its own CLAUDE.md (fragile theme rules, ZIP fallback chain, bug log) — read relevant parts before touching backend logic.
└── Zenith/          ← UX reference only (M3 Expressive patterns). Not built here.
```

Local shell runs on-device (Termux-like): **no JDK, no Android SDK locally**.
`adb` exists at `/data/data/com.termux/files/usr/bin/adb`, device `emulator-5554`
is normally attached. `gh` is authenticated as `Xphinx-X` (repo+workflow scopes).

## 2. What Toolkits is

Kotlin + Jetpack Compose + Material 3 Expressive port of `Toolkits-VIEW`, keeping
**100% of its features, settings and backend**, rebuilt to match its **exact
design/layout** (user explicitly demanded visual parity, not a redesign).

Features: ZIP Tools (extract/list/selective-extract, create ZIP/7z/TAR.*, split ZIP,
AES, levels, solid), Text Tools (Base64, Text Info, File Viewer+search, Projects
file-tree + editor, Fancy Text 51 styles + symbols), Settings (5 seeds, dynamic
color, system/light/dark/AMOLED, paths, hide toggles, ZIP encryption default).

## 3. Stack (exact versions — do not bump casually)

- Kotlin `2.2.10`, AGP `9.2.1`, Gradle wrapper `9.4.1`, compile/target `36`, min `24`, Java `17`
- Compose BOM `2025.02.00`, Material3 `1.5.0-alpha17` (expressive), navigation-compose `2.8.9`
- DataStore prefs, Room NOT used (backend uses `FileDbHelper` SQLite directly)
- Archive backends (same as original): commons-compress `1.28.0`, xz, zip4j `2.11.5`,
  7-Zip-JBinding-4Android `16.02-2.03.1`, libarchive `1.1.6`, zstd-jni `1.5.7-6`, junrar `7.5.5`
- Version catalog: `Toolkits/gradle/libs.versions.toml`

## 4. Build & install pipeline (use this, local build is impossible)

- CI: `Toolkits/.github/workflows/build-apk.yml` — jobs `build-debug` then
  `build-release` (`needs: build-debug`). Both assemble + upload artifacts.
- Check: `gh run list --repo Xphinx-X/Toolkits --limit 3`
- Failed logs: `gh run view <ID> --repo Xphinx-X/Toolkits --log-failed`
- Download: `gh run download <ID> --repo Xphinx-X/Toolkits -n Toolkits-release-apk -D /tmp/apks`
- Install: `adb install -r /tmp/apks/app-release.apk` (release is R8-minified,
  ~15MB). Debug APK gets `applicationIdSuffix .debug` (`com.toolkits.app.debug`).
- Release is signed with the **debug key** (`signingConfig = debug` in
  `app/build.gradle.kts`) so CI APKs are adb-installable. **Replace with a real
  keystore before any public/store release.**

## 5. Current state (2026-10-08/09)

- ✅ Debug + release CI **green** (run `37856375126`, commit `ccdda12`).
- ✅ Release v1.0 installed on `emulator-5554` (`com.toolkits.app`), launched clean, no crashes in logcat.
- ✅ Design parity pass merged: exact light/dark palettes, start-aligned toolbar,
  welcome Filled card, 16dp tool-row cards with original vector icons
  (`painterResource(R.drawable.*)`), outlined section cards, 56dp Extract button,
  Base64 toggle layout, Settings sections.
- ⚠️ Release key = debug key (see §4). Deprecation warnings only
  (e.g. `InsertDriveFile` fixed; a couple AutoMirrored warnings may remain — harmless).

## 6. Key files

```
Toolkits/
├── .github/workflows/build-apk.yml
├── AGENT.md (this file)
├── README.md
├── app/build.gradle.kts                 # release=R8+shrink+debug-signing; ARM ABIs only
├── app/proguard-rules.pro               # keep 7z/libarchive/zip4j/compress/zstd + dontwarn slf4j/logging
├── app/src/main/AndroidManifest.xml     # single MainActivity + 8 archive FGS services + FileProvider
├── app/src/main/java/com/toolkits/app/
│   ├── MainActivity.kt / ToolkitsApp.kt
│   ├── constant/{BroadcastConstants,ServiceConstants}.kt   # EXTRA_SELECTED_PATHS="extra_selected_paths" lives here
│   ├── data/preferences/UserPreferencesRepository.kt       # DataStore, same keys as original
│   ├── helper/ / model/ / service/      # COPIED 1:1 from VIEW (backend). Services read file lists from FileOperationsDao via EXTRA_JOB_ID — screens MUST stage with FileOperationsDao.addFilesForJob() first.
│   ├── ui/theme/{Color,Theme,Type}.kt   # exact VIEW palettes; dynamic on S+, AMOLED blacks
│   ├── ui/components/ToolkitsBars.kt    # ToolkitsTopBar, ToolRowCard (circled/plain), OutlinedSectionCard, CardHeaderRow, SectionLabel
│   ├── ui/navigation/{Routes,ToolkitsNavHost}.kt           # string routes (NOT type-safe; avoids serialization plugin)
│   └── ui/screens/                      # Home, ZipTools, Extract, Compress, TextToolsHub, Base64, TextInfo, FileViewer, FileEditor, Projects, FancyText, Settings
└── app/src/main/res/                    # copied from VIEW: drawable*, mipmap*, values/*, values-night/*, color/, xml/
```

## 7. Build lessons (don't re-learn these)

1. **No local builds** — no JDK/SDK on device. Always push → CI → download → `adb install`.
2. **Workflow must NOT use `android-actions/setup-android@v3`** (license prompt hangs). Use preinstalled SDK at `/usr/local/lib/android/sdk` + `sdkmanager --install "platforms;android-36" "build-tools;36.0.0"`. `actions/checkout@v5`, `actions/setup-java@v5` (v4 deprecated).
3. **Gradle plugins = Zenith recipe exactly**: app module applies ONLY `android.application` + `kotlin.compose`. Adding `kotlin.android` fatals with "Cannot add extension 'kotlin'". No `kotlinOptions {}` block (same cause).
4. **Copy ALL of VIEW's res** (drawables, colors, night, `color/`, arrays, styles) or `R.drawable` cascades into dozens of "Unresolved reference" in services + AAPT link failures. Manifest theme MUST be `Theme.ToolKits` (capital K, matches VIEW).
5. **R8 needs** `-dontwarn org.slf4j.** / commons-logging / log4j` (junrar/compress facades).
6. **Release lint is strict**: night colors need base declarations; backup/extraction XML excludes must sit under an include of the same domain.
7. **Services contract**: stage files via `FileOperationsDao.addFilesForJob()` → `EXTRA_JOB_ID`; selective extract key = `EXTRA_SELECTED_PATHS` as StringArrayList; archive path ALSO passed via `EXTRA_ARCHIVE_PATH`.
8. Unused imports are fine; removed `@OptIn` annotations must go with their imports.

## 8. Likely next work (pick up here)

- [ ] Visual QA on device (screenshots vs VIEW) — Compress/FancyText/TextInfo/Viewer/Projects got top-bar swaps only; restyle details if user flags them.
- [ ] Wire `hide_output_path` / `hide_path_suggest` / default extract-archive paths into Extract/Compress UI (currently stored, partially consumed).
- [ ] Replace debug signing with a real release keystore + document it.
- [ ] Silence remaining deprecation warnings (AutoMirrored icons).
- [ ] Consider `lint.baseline` or per-issue suppressions if lintVital bites again instead of resource edits.
- Commit style: short imperative subjects (`Fix: …`, `Match original design: …`); git identity `Xphinx <xphinx@local>`; push to `main` triggers CI (~5 min for both APKs).
