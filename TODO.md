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

## Backlog — decisions made 2026-10-30, now actionable

Each item below was a product/design call rather than an obvious fix.
Decisions recorded; unchecked = not yet done.

- [x] `LogNotTimber` ×26 — **decision: adopt Timber.** Added the
      dependency (5.0.1), planted `Timber.DebugTree()` unconditionally in
      `AdiresyApplication.onCreate()` — not just in debug builds, since
      most of the original `Log.*` calls (e.g. `TileDownloadWorker`'s
      download-failure logging) had no `BuildConfig.DEBUG` guard and were
      visible in release too; matching that meant Timber had to forward in
      release as well. The handful of call sites that *were* debug-gated
      kept their own `if (BuildConfig.DEBUG)` around the `Timber` call.
      Dropped each `TAG` constant that exactly matched its class's simple
      name (Timber auto-infers the tag from the caller) and preserved the
      two that didn't: `AdiresyRepository`'s `TAG_DIRECTORY = "DirectoryApi"`
      (a documented `-s DirectoryApi` logcat-filter workflow) and
      `MockLocationReceiver`'s `TAG = "MockLocation"`, both via
      `Timber.tag(...)`. Timber ships its own lint check
      (`BinaryOperationInTimber`, surfaced by `checkDependencies = true`)
      that flagged 4 of the 26 for using string concatenation instead of
      `%s` format args — fixed those (and simplified one redundant
      `+ e` next to an already-passed `Throwable`). Verified for real on
      the connected device: both the custom-tag (`DirectoryApi`) and
      auto-inferred-tag (`FichePinController`) cases show up correctly in
      logcat, at the right level, with the right message, from live
      network calls — not just "doesn't crash."
- [x] `UnusedResources` ×30 — **decision: audit and remove.** No
      `getIdentifier()`/reflection usage anywhere in the codebase, and each
      of the 30 had zero references outside its own declaration (checked
      across `res/` and `java/`, not just lint's own view). Removed 25
      orphaned strings (+ fr/mg translations), 3 unused colors, 2 unused
      drawables. Several turned out to be superseded duplicates (e.g.
      `disclaimer_title`/`title_data_sources` vs the still-used
      `attribution_unofficial`/`title_attribution`; `lang_english` etc.
      vs the hardcoded literals already flagged separately by
      `HardcodedText`). Verified on-device: Settings, Offline map data, and
      Attribution screens all render correctly.
- [ ] `IconLauncherShape` ×10, `IconDuplicates` ×5 — **decision: skip.**
      Needs real icon/image work; left for a deliberate design pass.
- [x] `Overdraw` ×5 — **decision: fix and visually verify** on the
      connected device before considering it done. `activity_splash.xml`
      had a genuine full-screen double-paint (root `android:background`
      duplicating the theme's `windowBackground`, both `@color/teal_600`)
      — removed the redundant one. The other 4
      (`item_admin_unit`/`item_bookmark`/`item_emoji`/`item_search_result`)
      are the standard `selectableItemBackground`-on-clickable-row ripple
      pattern, a well-known lint false positive for RecyclerView item
      roots — suppressed with `tools:ignore` and a comment rather than
      removing the ripple feedback. Verified on-device: splash screen,
      map screen, search results list all render correctly.
- [x] `PluralsCandidate` ×5, `HardcodedText` ×10 — **decision: fix with
      real fr/mg translations** (not placeholder English copies),
      following the existing `[MG-REVIEW]` convention for Malagasy.
      Converted `places_count`, `fiche_tip_likes`, `saved_days_ago`, and
      `import_success` to real `<plurals>` resources with
      `getQuantityString(...)` call sites — this is a genuine correctness
      fix, not just a lint formality: the old `places_count` logic was a
      hand-rolled `count == 1 ? ... : ...` ternary that only matches
      *English* plural rules, so French (where CLDR's "one" category
      covers both 0 *and* 1) was rendering "0 lieux" instead of the
      grammatically correct "0 lieu". Real `<plurals>` + `getQuantityString`
      resolves the right form per-locale automatically. `saved_days_ago`
      only needed `other` functionally (the call site special-cases 0/1
      days before ever reaching it) but French's plural rules formally
      require defining `one` and `many` too, which lint enforces even for
      an unreachable quantity — added them for completeness.
      `import_truncated` was *not* converted: it has two numbers (imported,
      total) with no single count to pluralize against, and "1 of 5
      places" isn't a grammar error the way "1 places" would be — suppressed
      instead, with a comment explaining why. For the 10 `HardcodedText`
      items: 3 language names (shown in their own language regardless of
      app locale, so `translatable="false"`, no fr/mg override) and 2
      reused glyphs (chevron, back arrow, bookmark emoji — also
      `translatable="false"`, pure symbols, not language content).
      Verified live on the connected device, not just build-clean:
      bookmarked a place into a new list and watched the count go
      "0 places" → "1 place"; switched the app to French and confirmed it
      read "1 lieu" (singular, the French-specific correctness fix above),
      then switched back to English.
- [x] `UselessParent` ×3, `DisableBaselineAlignment` ×3,
      `UseCompatTextViewDrawableXml` ×2, `UseCompoundDrawables` ×1 —
      **decision: fix and visually verify** each affected screen.
      `fragment_settings.xml`'s language/theme rows and
      `item_search_result.xml` each had a horizontal `LinearLayout` with
      exactly one child (an inner vertical `LinearLayout`); flattened to a
      single vertical `LinearLayout`, which resolves
      `DisableBaselineAlignment` too (baseline alignment only applies to
      horizontal orientation). `fragment_main_map.xml`'s offline banner
      swapped `android:drawableEnd`/`drawableTint` for the AppCompat
      equivalents. Two findings suppressed instead of applied — both on
      `activity_splash.xml`, the first screen users see:
      `UseCompoundDrawables` (XML `drawableTop` always uses the drawable's
      *intrinsic* size, no way to pin it to exactly 108dp the way
      `layout_width`/`height` does) and `UselessParent` (flattening would
      center the icon alone instead of the icon+slogan pair, visibly
      shifting it). Verified on-device: Settings, map banner, search
      results, and splash screen all render correctly.
- [x] `NewerVersionAvailable` ×2, `GradleDependency` ×1 — **decision:
      check each against the pinning comments in
      `gradle/libs.versions.toml`, bump what's safe.** API 37 (Android 17)
      shipped stable 2026-06-16, so the compileSdk-37 block on the okhttp
      pin no longer applies: bumped compileSdk to 37.1, okhttp/
      logging-interceptor to 5.5.1. Smoke-tested on the connected device —
      launches clean, no new logcat errors, no visual regression.
- [x] `ObsoleteSdkInt`, `ChromeOsAbiSupport`, `AppLinkWarning`,
      `PrivateResource` — **decision: investigate and fix each
      individually.** `mipmap-anydpi-v26` renamed to `mipmap-anydpi`
      (minSdk is already 26); `ChromeOsAbiSupport` suppressed in the
      `lint {}` block — deliberate per the existing abiFilters comment;
      `AppLinkWarning` suppressed on the `CodeDetailActivity` intent-filter
      — deliberate, autoVerify would need a hosted assetlinks.json that
      doesn't exist; `PrivateResource` fixed by replacing the internal
      `androidx.appcompat.R.drawable.abc_ic_ab_back_material` reference
      with a new local `ic_arrow_back.xml` vector in
      `BookmarkListDetailFragment`.
- [x] `OldTargetApi` ×1 — bumped `targetSdk` 36 → 37. Checked the app's
      own code against every Android 17 targetSdk-gated behavior change
      first: no `screenOrientation`/orientation locks, no
      audio/`MediaPlayer`/`AudioManager` usage, no `ContactsContract`, no
      SMS, no reflection on fields (`setAccessible`/`getDeclaredField`),
      no `android:configChanges` or `onConfigurationChanged` overrides —
      none of the gated changes touch this app directly, leaving only
      dependency compatibility as a risk (low, given MapLibre/Room/
      WorkManager/Retrofit are all actively maintained). Verified live on
      the connected device rather than trusting that reasoning alone:
      location permission flow + GPS acquisition, background→foreground
      via the recents switcher (task state preserved correctly), and a
      real WorkManager-driven offline tile download (start → visible
      progress → pause) all worked with zero crashes or new logcat
      exceptions.
- [ ] `CustomSplashScreen` ×1 — still open. Means migrating
      `SplashActivity` from a custom launch-screen Activity to the
      AndroidX SplashScreen API (`androidx.core:core-splashscreen`), which
      is a real UI migration (icon/branding timing, exit-animation
      hookup), not a one-line fix — left for a separate, deliberate pass.
