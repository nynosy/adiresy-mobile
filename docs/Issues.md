# Known Issues

Tracked issues across both repos in the Adiresy Android project: this app
(`adiresy-mobile`) and its map-data pipeline (`adiresy-tiles`, checked out
locally at `/Users/michael/projects/adiresy-tiles`). Each entry was verified
against live data (actual GitHub Release assets, the real manifest.json, or
Planetiler's own source) before being listed here — not just suspected from
reading code.

Status is tracked per issue below; update it as each is fixed.

---

## 1. `adiresy-tiles`: POI overlay's `class`/`subclass` attributes are swapped

**Status:** ✅ Fixed in `config/poi-overlay.yml`. Not yet live: the currently
published `data-2026-Q3` release still has the old (broken) `poi-*.pmtiles`
files, since the tile data itself needs a pipeline rebuild+republish to pick
up the config change — that's a heavy, release-overwriting CI job, so it
wasn't triggered automatically. Needs a rerun of `build-tiles.yml` (or the
next quarterly build) before issue 4 below has correct data to render.
**File:** `config/poi-overlay.yml`
**Severity:** High — silently breaks POI icon rendering for any consumer

The schema declares:

```yaml
attributes:
  - key: class
    type: match_key
  - key: subclass
    type: match_value
```

Per Planetiler's own source (`Contexts.java`, `FeaturePostMatch`): `match_key`
resolves to the **OSM tag key** that satisfied `include_when` (e.g.
`amenity`, `shop`, `healthcare`, `office`, `tourism`, `leisure`, `railway` —
only 7 possible values), and `match_value` resolves to that key's **actual
tag value** (e.g. `hospital`, `restaurant`, `supermarket`).

This is backwards from OpenMapTiles' own convention, where `class` holds the
specific category and `subclass` holds finer detail — which is exactly what
`adiresy-mobile`'s `StyleLoader.java` expects when it matches
`["get","class"]` against `hospital`, `restaurant`, `supermarket`, etc. None
of those values can ever appear in this schema's `class` field, so every POI
from this overlay would render as the default icon, regardless of category.

Not currently user-visible only because the app doesn't use this overlay yet
(see issue 3) — but it would silently misrender the moment it's wired up.

**Fix:** Swap the two attribute types so `class` = `match_value` (specific
category, matches app expectations) and `subclass` = `match_key` (broad
group, for potential future filtering).

---

## 2. `adiresy-tiles`: `generate-manifest.py` nests province base-map tiles at the wrong level

**Status:** ✅ Fixed in `scripts/generate-manifest.py`, verified with a
dry-run (stub files, regenerated manifest, confirmed `files.provinces` now
has all 6 provinces and no stray top-level `provinces` key). Same caveat as
issue 1: the currently published `manifest.json` was generated before this
fix and still has the old structure until the next release build.
**File:** `scripts/generate-manifest.py`
**Severity:** Medium — manifest is structurally inconsistent; not currently
relied on by the app (which hardcodes province download URLs), but breaks
any future or external consumer that reads `manifest.json` as documented

The `buildings` and `poi` sections both correctly nest their per-province
entries as `manifest["buildings"]["provinces"]` /
`manifest["poi"]["provinces"]`. The base map does not follow the same
pattern — its provinces object is assigned directly to `manifest["provinces"]`
(top-level, sibling of `"files"`) instead of `manifest["files"]["provinces"]`:

```python
provinces = provinces_tiers(lambda name, t: f"province-{name}-{t}.pmtiles", base_url)
if provinces:
    manifest["provinces"] = provinces   # should be manifest["files"]["provinces"]
```

Verified against the live `data-2026-Q3` manifest: `files` only contains
`national`, while a separate top-level `provinces` key holds the six
province entries — even though the actual `province-*.pmtiles` release
assets exist and are fine.

**Fix:** Nest it under `files`, matching `buildings`/`poi`.

---

## 3. `adiresy-mobile`: downloads never fetch `manifest.json` or verify SHA-256

**Status:** ✅ Fixed. Added `ManifestClient` (fetches + caches `manifest.json`,
resolves a `DownloadTarget(url, sha256)` per layer/scope/tier), threaded a
nullable `DownloadTarget` through `OfflineDataViewModel`'s download methods
(replacing raw URL strings), and added SHA-256 verification in
`TileDownloadWorker` after every successful transfer — deletes and retries
(bounded by the existing `MAX_ATTEMPTS`) on mismatch. Every resolver falls
back to the old hardcoded URL construction with no checksum if the manifest
fetch fails or lacks an entry, so a manifest outage degrades gracefully
instead of blocking downloads — matches the app's offline-first design.
Compiles clean; not yet tested on-device (see summary).

Also discovered while fixing this: the boundaries overlay (`boundaries.pmtiles`,
`SCOPE_BOUNDARIES` in `TileDownloadWorker`) has no download trigger anywhere
in the UI at all — not a manifest bug, a separate unbuilt feature from the
spec's §13.3. Left out of scope here; noting it for a future issue.

**Files:** `download/TileDownloadWorker.java`, `ui/offline/OfflineDataFragment.java`,
`ui/offline/OfflineDataViewModel.java`
**Severity:** High — the app's own spec requires this and silently doesn't do it

`adiresy-mobile`'s own spec (`docs/Adiresy-Android-Specification.md` §13.4):
*"Checksum (SHA-256) is verified per-file after download; retry on
mismatch. Each file... has its own checksum entry in the manifest — verify
independently."* `adiresy-tiles`' README makes the same claim about the app's
behavior. Neither is true: `OfflineDataFragment` builds every download URL by
string concatenation —

```java
String url = BASE_URL + "madagascar-z" + zoom + ".pmtiles";
...
String url = BASE_URL + "province-" + packKey + ".pmtiles";
```

— and `manifest.json` is never fetched, parsed, or referenced anywhere in
the app (confirmed: zero matches for `sha256`/`checksum`/`manifest` outside
`android.Manifest` permission strings). Consequences beyond the missing
integrity check: file sizes shown in the offline-data UI come from hardcoded
tables (`NATIONAL_SIZES_MB`, `PROVINCE_SIZES_MB` in `OfflineDataFragment`)
that can silently drift from the real files, and the spec's documented
behavior for optional layers ("if the `buildings` key is absent... hide the
card entirely") can't work since the app never checks.

**Fix:** Fetch and parse `manifest.json` once when the offline-data screen
loads; resolve every download URL, expected `size_bytes`, and `sha256` from
it instead of hardcoding; verify the downloaded file's SHA-256 in
`TileDownloadWorker` after a successful transfer, deleting and retrying
(bounded by the existing `MAX_ATTEMPTS`) on mismatch.

---

## 4. `adiresy-mobile`: POI overlay is never downloaded or rendered

**Status:** ✅ Fixed. Added `SCOPE_POI`/`SCOPE_PROVINCE_POI` to
`TileDownloadWorker`, extended the national/province download chains to a
third sequential stage (map → buildings → poi — `WorkContinuation.then()`
per stage, not `.then(List)`, which runs in parallel), added `AppPrefs`
POI path/version storage (wired into delete/discard cleanup too), and gave
`StyleLoader` a `poi` source that `poi-symbol` prefers over the base map's
z14-gated layer when downloaded (minzoom drops to 12; the base layer's
`rank<=3` filter is dropped too, since the overlay has no `rank` field).

Verified end-to-end on-device: triggered a real national download, all
three chained files completed and were saved to the correct paths with
the exact byte sizes the manifest declared (e.g.
`poi-madagascar-z12.pmtiles` = 655,034 bytes, matching `manifest.json`
exactly) — confirming issue 3's manifest/checksum resolution feeds this
correctly. Map still renders with no exceptions after adding the new
source/layer config. Icons will still render as the default marker (not
by category) until `adiresy-tiles` is rebuilt with issue 1's fix — that
part is expected and out of scope here (see issue 1's status).
**Files:** `download/TileDownloadWorker.java`, `ui/offline/OfflineDataFragment.java`,
`ui/offline/OfflineDataViewModel.java`, `map/StyleLoader.java`
**Severity:** Medium — feature gap, not a crash; degrades to "no POI markers
below zoom 14"

`adiresy-tiles` publishes a dedicated low-zoom POI overlay
(`poi-*.pmtiles`, fixed `min_zoom: 12`) for national and all six provinces
at all three quality tiers — confirmed present as real, correctly-sized
release assets. The Android app never downloads or references any of these
files:

- `TileDownloadWorker` has no POI scope (only `NATIONAL`, `BUILDINGS`,
  `BOUNDARIES`, `PROVINCE_PACK`, `PROVINCE_BUILDINGS`).
- `OfflineDataFragment`/`OfflineDataViewModel` have no POI card, toggle, or
  download trigger.
- `StyleLoader.java` renders POI icons from `"source":"omtiles"` (the base
  map tiles) with `"source-layer":"poi"` and `"minzoom":14` hardcoded — i.e.
  it uses the base map's own OpenMapTiles-schema POI layer, which the tiles
  repo's own README documents as gated to zoom 14 for most categories
  ("only 14 POI features at Z13, none of them schools or hospitals").

Net effect: on the Overview (Z12) and Standard (Z13) tiers — Z13 being the
app's own "recommended default" — users see **no POI icons at all**, even
though a purpose-built overlay solving exactly that problem ships in every
release, unused.

**Fix:** Add a `SCOPE_POI` (+ `SCOPE_PROVINCE_POI`) download path in
`TileDownloadWorker`, surface it in the offline data UI (likely bundled with
the existing buildings opt-in, given its small size — ~0.6-3 MB vs.
buildings' 150-700 MB), add a `poi` MapLibre source in `StyleLoader` pointing
at the downloaded file, and drop `poi-symbol`'s `minzoom` to 12 reading from
that source instead of `omtiles`. Depends on issue 1 being fixed first —
otherwise the newly-visible low-zoom POIs would all render as the default
icon.
