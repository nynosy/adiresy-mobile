# Code quality TODO

Generated from the Android Lint + Error Prone findings after wiring both
tools into the build (see `app/build.gradle.kts`, `app/lint-baseline.xml`).
Items are grouped by priority. Checked items have been fixed and verified
with `./gradlew clean assembleDebug lintDebug`.

## P0 — Crash / runtime-correctness bugs

- [x] `share_text` format string: fr/mg translations referenced `%2$s` but
      every one of the 9 call sites only supplies one argument →
      `MissingFormatArgumentException` on French/Malagasy devices. The
      existing code comment suggested passing the code twice instead, but
      that trades the crash for a different lint violation (`StringFormatCount`:
      a format string must reference every lower-numbered argument too, and
      none of the three locales used `%1$s`). Fixed by changing fr/mg to
      `%1$s`, matching the English default and every call site — single
      argument, consistent across all locales.
- [x] `AdiresyApplication.java:40` — dynamic `registerReceiver()` for the
      debug-only mock-location receiver has no `RECEIVER_EXPORTED` /
      `RECEIVER_NOT_EXPORTED` flag, which throws `SecurityException` on
      API 33+ (targetSdk is 36). Fix with `ContextCompat.registerReceiver`.
- [x] `QueryPermissionsNeeded` ×6 — `geo:`, `tel:`/`ACTION_DIAL`, and
      `https ACTION_VIEW` intent resolution checks silently return "no
      handler" on Android 11+ without a `<queries>` manifest declaration.
      Add the three declarations.
- [x] `WrongConstant` ×3 (`BookmarkPinController`, `FichePinController`,
      `SelectionPinController`) — raw string literals (`"center"`,
      `"bottom"`) passed where `Property.ICON_ANCHOR_*` constants are
      expected. Values match today, but not type-checked; swap to the
      named constants.
- [x] `MissingPermission` on `HomeViewModel.java:161` — false positive,
      `HomeFragment` already checks `ACCESS_FINE_LOCATION` before calling
      `startLocating()`; lint can't see cross-class. Suppress with a
      comment explaining why.
- [x] `MockLocation` on `src/debug/AndroidManifest.xml:6` — already
      correctly scoped to the debug source set; suppress with
      `tools:ignore` and a comment, since this is lint flagging its own
      recommended pattern.
- [x] `ExportedReceiver` on the debug `MockLocationReceiver` — intentional:
      it exists so `adb shell am broadcast` can reach it from outside the
      app. Debug-only, never ships to release. Suppress with a comment.

## P1 — Error Prone correctness

- [x] `FutureReturnValueIgnored` — `TileDownloadWorker.java:128` ignores the
      `Future` from `setProgressAsync(...)`, swallowing failures.
- [x] `DefaultCharset` — `BookmarkRepository.java:280` opens a `FileWriter`
      with the platform default charset; pin to UTF-8.
- [x] `StringFormatMatches` (offline download screen) — `%s` placeholders
      fed `int` args in `offline_status_progress` / `offline_national_done`.
      Not an actual crash (`%s` works on any arg), but switch the integer
      placeholders to `%d` across all three locales for correctness.

## P2 — Locale / i18n correctness

- [x] `DefaultLocale` / `StringCaseLocaleUsage` ×3 — `AdminUnitAdapter.java:55`,
      `ExploreBottomSheet.java:110`, `SearchAdapter.java:52` call
      `.toUpperCase()` with no `Locale`. Use `Locale.getDefault()` (these
      are user-facing display labels).
- [x] `SetTextI18n` ×3 — `AboutFragment.java:33` (`getString(...) + ": " +
      BuildConfig.VERSION_NAME`) and `CodeCardBottomSheet.java:83`
      (`commune + " › " + district`) concatenate literals into `setText`;
      move to format-string resources. The `AdminUnitAdapter.java:55`
      instance is resolved as a side effect of the `DefaultLocale` fix
      above (moving the concatenation out of the `setText(...)` call).

## P3 — Error Prone hygiene / dead code

- [x] `UnusedVariable` ×7 — `MainMapFragment.java:93` (`mapViewModel`
      field), `StyleLoader.java:70` (`buildingOut` local),
      `SearchController.java:30,34` (`context`, `editText` fields),
      `SearchFragment.java:24` (`searchController` field),
      `OfflineDataFragment.java:42` (`NATIONAL_SIZES_MB`),
      `PoiIconFactory.java:270` (`sz` param).
- [x] `UnusedMethod` — `OfflineDataViewModel.java:187` (`enqueue`).
- [x] `UnnecessaryLambda` — `HomeViewModel.java:61`.
- [x] `MissingSummary` — `AdiresyRepository.java:611` javadoc.
- [x] `PatternMatchingInstanceof` ×4 — `SaveToListAdapter.java:64,76`,
      `EmojiPickerDialog.java:43`, `AppPrefs.java:182`.
- [x] `StatementSwitchToExpressionSwitch` ×14 + `RefactorSwitch` ×2 —
      modernize to arrow switches: `SaveToListBottomSheet.java:146`,
      `BookmarkAdapter.java:112`, `HomeFragment.java:71`,
      `AdiresyRepository.java:779,807,834`, `MainMapFragment.java:438`,
      `MapViewModel.java:40`, `ExploreBottomSheet.java:125,147,182,192,213`,
      `OfflineDataFragment.java:299`, `TileDownloadWorker.java:146`,
      `AdiresyApplication.java:50`.

## Backlog — needs a product/design decision, not auto-fixed

These are real lint findings but fixing them blindly risks guessing wrong
about intent (translations, visual assets, a new dependency). Flagging
for a deliberate pass rather than touching them here.

- [ ] `LogNotTimber` ×26 — a dependency-bundled lint check (surfaced now
      that `checkDependencies = true` is on) suggesting the Timber logging
      library instead of `android.util.Log`. That's a new dependency +
      26 call-site migration, not a bug fix — decide first whether to adopt
      Timber or suppress this check.
- [ ] `UnusedResources` ×30 — likely safe to delete, but needs a pass to
      rule out resources referenced dynamically (e.g. via
      `getIdentifier()`) before bulk-removing.
- [ ] `IconLauncherShape` ×10, `IconDuplicates` ×5 — asset-level; needs
      actual icon/image work, not code changes.
- [ ] `Overdraw` ×5 — layout perf; needs visual verification per screen
      before changing backgrounds/elevation.
- [ ] `PluralsCandidate` ×6, `HardcodedText` ×10 — i18n cleanup that needs
      real fr/mg translation, not just resource extraction with the
      English text copied over.
- [ ] `UselessParent` ×3, `DisableBaselineAlignment` ×3,
      `UseCompatTextViewDrawableXml` ×2, `UseCompoundDrawables` ×1 —
      layout simplifications, low risk but need a visual check per screen.
- [ ] `NewerVersionAvailable` ×2, `GradleDependency` ×1 — version bumps;
      check against the pinning comments already in
      `gradle/libs.versions.toml` (e.g. okhttp is pinned to 5.4.0 because
      5.5.0+ needs compileSdk 37, which isn't published yet).
- [ ] `OldTargetApi` ×1, `ObsoleteSdkInt` ×1, `CustomSplashScreen` ×1,
      `ChromeOsAbiSupport` ×1, `AppLinkWarning` ×1, `PrivateResource` ×1 —
      one-off manifest/config findings, each needs individual investigation.
