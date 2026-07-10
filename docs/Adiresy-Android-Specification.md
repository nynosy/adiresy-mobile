# Adiresy Android — Technical Specification

**Document status:** Draft v0.1 · for review
**Date:** 4 July 2026
**Author:** (to be assigned)
**Language of this document:** English
**Product languages:** English (en), French (fr), Malagasy (mg)
**Implementation language:** Java (Android)
**Related services:** Adiresy public REST API (`https://adiresy.mg`)

> Adiresy is an *unofficial* addressing service: each building in Madagascar receives a short, permanent code anchored to GPS coordinates (e.g. `101-6EAR-50418`). This document specifies a native Android client that works on low-end phones and, progressively, fully offline — from map display through routing, turn-by-turn navigation, and trilingual voice guidance.

---

## 1. Purpose and goals

The app lets a person in Madagascar:

1. Find the Adiresy code for a building and share it as a link.
2. Resolve a shared code and see the building on a map.
3. Navigate to that building — eventually with offline routing, turn-by-turn UI, and spoken guidance in their own language.

Design north-star: **a person on a 2 GB Transsion or Xiaomi phone, on 2G or with no signal at all, on a low battery, who is more comfortable in Malagasy than French, must be able to find and reach an address.** Every decision in this document is measured against that user.

Non-goals: account systems, social features, server-side user data, ad monetisation, or any feature that assumes a constant data connection.

---

## 2. Target context (Madagascar)

These constraints are first-class requirements, not caveats. Sources are listed in §22.

**Connectivity.** Around 20% of the population used the internet at the start of 2025; roughly 80% were offline. Mobile data is expensive and rising (a basic monthly telecom basket averaged ~US$8 in 2025, up ~55% year on year), and 4G coverage is roughly a third of the country, concentrated in towns. **Implication:** the app must be usable with the radio off, must never require a connection for core tasks, and must treat every byte of mobile data as costly.

**Devices.** The market is dominated by budget Android: Xiaomi (Redmi/Poco entry tier) is the most-used brand in Madagascar, alongside Transsion (Tecno, Infinix, itel) and Samsung's A/M budget lines. Many of these ship **Android Go edition** with 1–2 GB RAM, modest storage, and older or low-power SoCs. **Implication:** tight memory and storage budgets, small APK, minimal animation, no assumption of a fast CPU/GPU.

**Power.** Electricity is unreliable in much of the country. **Implication:** interrupted-download resume, low-battery-friendly behaviour, and no dependence on frequent recharging or re-downloading.

**Language.** Malagasy is spoken by effectively everyone; French is comfortable mainly for the urban, educated minority; English is a minority third language. **Implication:** Malagasy is a first-class UI language (selectable, and offered prominently on first run), not an afterthought.

**Trust.** Adiresy is explicitly unofficial with no legal standing. **Implication:** the app must carry the same honest disclaimer and clear data attributions the website does.

---

## 3. Scope and phasing

| Phase | Theme | Ships |
|-------|-------|-------|
| **1** | Core offline addressing | Offline map, locate-me, nearby buildings, code resolve, share, admin exploration, search, trilingual UI, settings, offline data manager (national Z12 overview + regional Z14 detail), favourites (named bookmark lists with user name, notes, and emoji symbol) |
| **2** | Offline routing | On-device A→B route calculation from user location to an Adiresy code; route drawn on map; textual step list |
| **3** | Turn-by-turn UI | Guided navigation view: maneuver banner, distance-to-next, off-route re-routing, arrival — built with default Android UI |
| **4** | Voice guidance | Spoken turn-by-turn in English, French, and Malagasy, fully offline. Tech stack TBD. |

Phase 3 delivers visual navigation only. Voice guidance is a separate Phase 4, allowing the navigation UI to stabilise before adding audio. The voice tech stack is open — see §14.4.

Each phase is independently shippable and leaves the app in a coherent, releasable state.

---

## 4. Non-functional requirements

| # | Requirement | Target |
|---|-------------|--------|
| NFR-1 | Core tasks work fully offline | 100% of Phase 1 + 2 features function with no network |
| NFR-2 | Cold start on low-end device | ≤ 2.5 s to interactive on a 2 GB Android Go phone |
| NFR-3 | Map pan/zoom | Visually smooth (≥ 30 fps) on 2 GB devices; no ANRs |
| NFR-4 | Install size (APK/AAB delivered) | ≤ 25 MB before map/graph data |
| NFR-5 | First-run data download | Over Wi-Fi by default; resumable; national or per-region choice |
| NFR-6 | Peak RAM | Comfortable within a 2 GB device; functional on 1 GB |
| NFR-7 | Mobile-data use in normal operation | Zero required; any network use is explicit and opt-in |
| NFR-8 | Battery | No background location or wake-locks outside active navigation |
| NFR-9 | Accessibility | Large touch targets, high-contrast text, TalkBack labels, scalable fonts |
| NFR-10 | Languages | Full parity across en / fr / mg for all user-facing strings |
| NFR-11 | Privacy | No account, no analytics by default; coordinates are sent to the Adiresy API only for explicit, user-initiated lookups (locate-me, building tap, search, routing fallback), never tracked or persisted server-side by the app |

---

## 5. Platform baseline

| Item | Value | Rationale |
|------|-------|-----------|
| Language | **Java 21** | Entire codebase and samples in Java; Java 21 is the current LTS and is fully supported by AGP 9.x + D8/R8 desugaring down to `minSdk 24` |
| Core library desugaring | **Enabled** (`desugar_jdk_libs 2.1.x`) | Backports Java 9–21 standard-library APIs (streams, new collection methods, etc.) to API 24+; small runtime cost (~few hundred KB) |
| `minSdkVersion` | **24 (Android 7.0)** | Inclusive floor that still captures older budget devices while remaining supported by MapLibre and GraphHopper. Can drop to 21 if field data shows a meaningful older tail. |
| `targetSdkVersion` | **API 36.1 (Android 16.1)** | Current stable; required by Play; keep current as new versions release |
| `compileSdkVersion` | **API 36.1** (matches target) | AGP 9.x DSL: `release(36) { minorApiLevel = 1 }` |
| Architecture ABIs | `armeabi-v7a` + `arm64-v8a` | Covers budget (32-bit) and modern devices; split per-ABI in the AAB to shrink native payload |
| UI toolkit | Android Views + **Material Components for Android** (default widgets) | Per requirement to use default Android UI elements |
| Min screen width | 320 dp | Small budget displays |
| Orientation | Portrait primary; navigation view supports landscape |

No Jetpack Compose (keeps the app light and matches the "default Android UI" and Java requirements; Compose adds runtime weight undesirable on Android Go).

---

## 6. Architecture

Single-activity is avoided in favour of a small set of activities plus fragments, to keep memory graphs simple on low-end hardware. Pattern: **MVVM with AndroidX** (`ViewModel` + `LiveData`), all in Java.

```
app/
├─ ui/            Activities, Fragments, adapters (Material widgets)
│   ├─ home/      Locate, nearby buildings, code card, share
│   ├─ map/       MapLibre MapView host + explore
│   ├─ search/    Unified search
│   ├─ code/      Resolved-code detail screen
│   ├─ saved/     SavedFragment, BookmarkListDetailFragment, SavedViewModel, BookmarkAdapter
│   ├─ nav/       Phase 3: turn-by-turn UI
│   └─ settings/  Language, theme, offline data, about/attribution
├─ map/           MapLibre setup, style loading, offline tile source, markers
├─ data/
│   ├─ api/       AdiresyApi (Retrofit) + AdiresyRepository + adapter/DTOs
│   ├─ cache/     Room DB: resolved codes, admin units, search history, bookmark lists, bookmarks
│   └─ prefs/     App settings (language, theme, downloaded regions)
├─ routing/       Phase 2: GraphHopper wrapper (RouteEngine interface)
├─ nav/           Phase 3: guidance state machine, off-route detection
├─ voice/         Phase 3b: VoiceGuide (TTS + Malagasy audio clips)
├─ i18n/          Locale management, string resolution helpers
└─ download/      WorkManager jobs for map/graph data (resumable)
```

Key interfaces (kept abstract so implementations can be swapped):

- `AddressResolver` — code → coordinates + admin hierarchy; reverse (coords → nearby buildings); search. Backed **online** by the Adiresy API and **offline** by the Room cache and any bundled admin data.
- `RouteEngine` (Phase 2) — `route(from, to) → Route`. Backed by GraphHopper offline; could later wrap Valhalla without touching callers.
- `MapController` — thin wrapper over MapLibre for markers, camera, route layers.
- `VoiceGuide` (Phase 3b) — `speak(Maneuver, Locale)`.

---

## 7. Technology stack

| Concern | Choice | Notes |
|---------|--------|-------|
| Map rendering | **MapLibre Native Android** (`org.maplibre.gl:android-sdk`, 11.11.x) | Free, open-source, GPU vector tiles, no API key, no billing. PMTiles supported since 11.7.0. Verify latest version at build time. |
| Offline tiles | **PMTiles** (single file) or MBTiles | Bundled/downloaded; loaded via `pmtiles://` + `file://`. No MapTiler dependency in-app. |
| Tile generation | **Planetiler** (or Tilemaker) | One-time build step, off-device |
| OSM data source | **Geofabrik** Madagascar extract (`.osm.pbf`) | National extract is small |
| HTTP / API | **Retrofit + OkHttp** | OkHttp disk cache; short timeouts; offline-first |
| Local cache | **Room** (SQLite) | Resolved codes, admin units, search history |
| Background downloads | **WorkManager** | Resumable, Wi-Fi-constrained, survives power loss |
| Offline routing (Ph.2) | **GraphHopper** (Java, Apache-2.0) | Runs on-device; prebuilt graph shipped/downloaded |
| Navigation UI (Ph.3a) | **Custom Java UI** over GraphHopper instructions + MapLibre | Chosen over Compose-based nav SDKs to satisfy Java + default-UI constraints and keep weight down |
| Voice (Ph.3b) | Android **TextToSpeech** (en/fr) + **bundled Malagasy audio clips** | See §14.3b — Malagasy is not supported by mainstream TTS |
| DI | Manual / lightweight (no Dagger/Hilt) | Keeps build and runtime lean |

Everything above is Java-compatible and free of per-request billing.

---

## 8. Data pipeline (off-device build)

Three artifact types are produced from the tile pipeline, each downloadable independently:

**A. Base map tiles** — vector tiles for roads, landcover, waterways, place names (OSM via Planetiler). OSM's `building` source layer is intentionally excluded — buildings come from a higher-quality overlay (see C).

**B. Routing graph (Phase 2)** — GraphHopper's preprocessed graph folder.

```bash
# Build once with a GraphHopper config tuned for car+foot profiles.
java -Ddw.graphhopper.datareader.file=madagascar-latest.osm.pbf \
  -jar graphhopper-web.jar import config-mg.yml
# Ship the resulting graph-cache/ directory (zipped) as the routing asset.
```

**C. Buildings overlay** — Google Open Buildings v3 + Microsoft GlobalMLBuildingFootprints + OSM, merged by VIDA (ODbL). A separate `.pmtiles` file per region/tier, rendered as a second MapLibre vector source. Roughly **triples** download size at each tier. Optional — user must explicitly opt in.

**D. Boundaries overlay** — BNGRC/OCHA administrative boundaries (CC BY-IGO) for all four Malagasy admin levels (region / district / commune / fokontany). A single `boundaries.pmtiles` file (~13 MB) covering the whole country; not split by tier or province. Rendered as a third MapLibre vector source with zoom-stop filters per `admLevel`.

**Base map style.** Two variants: `style-light.json` and `style-dark.json`. Glyphs currently fetched from `https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf` (MapLibre public CDN, cached by MapLibre after first fetch). Long-term target: self-host glyphs in `assets/` for guaranteed offline rendering.

**Manifest schema.** `manifest.json` is nested by quality tier. `files.national` and `provinces.{name}` are each an object keyed by tier:

```json
{
  "files": {
    "national": {
      "z12": { "filename": "madagascar-z12.pmtiles", "url": "…", "size_bytes": 43073104, "sha256": "…" },
      "z13": { "filename": "madagascar-z13.pmtiles", "url": "…", "size_bytes": 102044208, "sha256": "…" },
      "z14": { "filename": "madagascar-z14.pmtiles", "url": "…", "size_bytes": 301805319, "sha256": "…" }
    }
  },
  "provinces": {
    "antananarivo": { "z12": {…}, "z13": {…}, "z14": {…} }
  },
  "buildings": {
    "national": { "z12": {…}, "z13": {…}, "z14": {…} },
    "provinces": { "antananarivo": { "z12": {…}, … } }
  },
  "boundaries": { "filename": "boundaries.pmtiles", "url": "…", "size_bytes": 13278117, "sha256": "…" }
}
```

`buildings` and `boundaries` keys may be absent from a given release — treat as fully optional, not a manifest error.

**Hosting & updates.** Artifacts are published as GitHub Release assets in `adiresy-tiles`. The app fetches `manifest.json` from the fixed `releases/latest/download/` URL. `Range`-header support on GitHub's CDN makes resumable downloads work out of the box.

**Download URLs** (GitHub account: `nynosy`):

- Manifest: `https://github.com/nynosy/adiresy-tiles/releases/latest/download/manifest.json`
- Base map: `https://github.com/nynosy/adiresy-tiles/releases/latest/download/madagascar-{tier}.pmtiles`
- Buildings: `https://github.com/nynosy/adiresy-tiles/releases/latest/download/madagascar-buildings-{tier}.pmtiles`
- Boundaries: `https://github.com/nynosy/adiresy-tiles/releases/latest/download/boundaries.pmtiles`

**Tile quality tiers** (national base map, measured from 2026-07-05 build):

| Tier key | User-facing label | National size | Province size | Detail |
|---|---|---|---|---|
| `z12` | Overview | ~43 MB | varies | Main roads and towns — coarse detail |
| `z13` | Standard _(recommended)_ | ~102 MB | varies | Block-level detail |
| `z14` | Detailed | ~302 MB | varies | Street-level — best for walking navigation |

Province sizes vary significantly (e.g. Fianarantsoa Detailed ≈ 137 MB, Mahajanga Detailed ≈ 22 MB) — always show the actual `size_bytes` from the manifest, not a flat estimate. Buildings overlay roughly triples each of these figures.

**Data freshness — three independent cycles:**
- **Base map** (roads, labels, places): quarterly refresh from live OSM. `manifest.json`'s `osm_extract_date` reflects this.
- **Buildings overlay**: static snapshot (last updated 2024-08-27 upstream); not on the quarterly cadence.
- **Boundaries overlay**: effectively static (BNGRC/OCHA source dated 2018, reviewed 2024).

Do not surface a single "data as of" date that implies freshness for all three.

**App-side rendering — three MapLibre sources:**

| Source id | File | Source layer(s) | Notes |
|---|---|---|---|
| `omtiles` | base map `.pmtiles` | `landcover`, `water`, `waterway`, `transportation`, `place`, etc. | Always present when any tile downloaded |
| `buildings` | buildings overlay `.pmtiles` | `buildings` | Add only when buildings file is present; opt-in download |
| `boundaries` | `boundaries.pmtiles` | `boundaries` | Add when present; `admLevel` property: 1=region, 2=district, 3=commune, 4=fokontany, 99=coastline |

Boundaries `admLevel` zoom-stop filter convention: region ≥ Z4, district ≥ Z7, commune ≥ Z10, fokontany ≥ Z12.

**Routing graph (Phase 2)** — unchanged, see above.

---

## 9. Adiresy API integration

API spec confirmed against the live OpenAPI schema (`api-1.json`, retrieved July 2026). Base URL: `https://adiresy.mg`. Version prefix: `/api/v1/`.

### 9.1 Authentication

Every endpoint requires an API key passed as:
- **Header (preferred):** `X-Adiresy-Key: {key}`, or
- **Query param:** `?api_key={key}`

Keys are generated at `adiresy.mg/dashboard` after sign-in. **Rate limit:** 100 requests/hour per key, 200/hour per account across all keys.

> **OQ-7 (API key strategy):** ✓ Interim decision — option (a) bundled developer key for development and early testing. Long-term recommended approach is option (c): server-issued anonymous per-install tokens (zero user friction, per-install rate limiting, revocable). Discussion document for the adiresy.mg owner: `docs/API-Key-Strategy-Discussion.md`. Migration from (a) to (c) only requires changing `ApiKeyInterceptor` — no other app code changes.

The key is stored in `AppPrefs` (encrypted SharedPreferences) and injected into every OkHttp request via an interceptor.

### 9.2 Endpoints

**Addresses**

| Endpoint | Method | Key params | Returns | Used by |
|----------|--------|-----------|---------|---------|
| `/api/v1/addresses/{canonical_code}/` | GET | path: `canonical_code` | `AddressCode` | Code detail, deep link resolve |
| `/api/v1/addresses/reverse/` | GET | `?lat=&lng=` | `AddressCode` (best match) | Locate-me (single result) |
| `/api/v1/addresses/reverse/` | GET | `?lat=&lng=&limit=N&radius=R` | `AddressCode` + `candidates[]` | **Nearby buildings** |
| `/api/v1/addresses/` | GET | `?fokontany={pcode}&page=` | `PaginatedAddressCodeList` | Admin-level listing |

**Nearby buildings via `/reverse/` with `limit` + `radius`:**

When `limit > 1`, the response adds a `candidates` array of up to `limit` results ranked by containment first, then distance. Each candidate carries `match` (`"inside"` or `"nearest"`) and `distance_m`. App uses `limit=20&radius=200`.

```
GET /api/v1/addresses/reverse/?lat=-18.9080&lng=47.5260&limit=20&radius=200
→ { "status":"ok", "data": { …best match…, "candidates": [ { …match, distance_m… }, … ] } }
```

This is the **preferred approach** for nearby buildings — one call, distance-sorted, no fokontany lookup required.

> **Known bug (fix in progress):** `?fokontany={uuid}` on `/api/v1/addresses/` returns 0 results with a UUID; the pcode form (`MG11101001001`) works correctly. API developer is fixing this alongside formally documenting `limit`/`radius`. See `docs/bug-fokontany-filter.md`.

**Geo — admin hierarchy**

All geo list endpoints are paginated (`page` param) and searchable (`search` param). Each level supports three sub-endpoints:

| Endpoint pattern | Returns |
|-----------------|---------|
| `/api/v1/geo/regions/` | Paginated region list (with `bbox`, `centroid`) |
| `/api/v1/geo/regions/{pcode}/` | Single `Region` |
| `/api/v1/geo/regions/{pcode}/geometry/` | Simplified GeoJSON boundary |
| `/api/v1/geo/regions/{pcode}/stats/` | Region statistics |
| `/api/v1/geo/districts/?region={uuid}` | Districts filtered by region |
| `/api/v1/geo/districts/{pcode}/geometry/` | District boundary GeoJSON |
| `/api/v1/geo/communes/?district={uuid}` | Communes filtered by district |
| `/api/v1/geo/communes/{pcode}/geometry/` | Commune boundary GeoJSON |
| `/api/v1/geo/fokontany/?commune={uuid}` | Fokontany filtered by commune |
| `/api/v1/geo/fokontany/{pcode}/geometry/` | Fokontany boundary GeoJSON |
| `/api/v1/geo/stats/` | National summary statistics |

> **Implication for IQ-6 (admin bundling):** the `/geometry/` endpoints return boundaries on demand. There is no need to bundle a `regions.geojson` in `assets/` — boundaries are fetched on first explore interaction and cached in Room. The response from list endpoints already includes `bbox` and `centroid`, so the map can zoom to the right area without a geometry call first.

**Search**

| Endpoint | Params | Returns |
|----------|--------|---------|
| `/api/v1/search/autocomplete/` | `?q={min 2 chars}&limit={max 10}` | Categorised results: codes, fokontany, communes, districts, regions, OSM places |

**Health**

| Endpoint | Auth | Returns |
|----------|------|---------|
| `/api/v1/health/` | Optional | 200 healthy / 503 degraded |

Use this for a lightweight online-connectivity probe before any API call.

### 9.3 Key response schemas

**`AddressCode`**

```
canonical_code   string        e.g. "101-6EAR-50418"
district_code    string        e.g. "101"
commune_short    string        e.g. "6EAR"
serial           integer
fokontany        uuid          FK — use for nearby-buildings query
fokontany_name   string
commune_name     string
district_name    string
region_name      string
latitude         double?
longitude        double?
```

**`Region` / `District` / `Commune` / `Fokontany`** all carry:
```
id        uuid
pcode     string    used as the path param in all geo endpoints
name      string
bbox      [lon_min, lat_min, lon_max, lat_max]?
centroid  [lon, lat]?
```
Plus upward hierarchy fields (e.g. `District` carries `region_name`, `region_pcode`).

### 9.4 Offline-first behaviour

Every successful API response is written to Room, keyed by `canonical_code` (addresses) or `pcode` (admin units). Cache staleness threshold: 7 days for addresses, 30 days for admin units and boundaries.

Reverse geocode (locate-me → nearest address) is **online-only** — there is no bundled coordinate index. Phase 1 offline degradation: GPS fix centres the map; nearby-buildings list shows an "online-only" empty state with a "Connect to find nearby buildings" prompt. Pin-drop fallback is the right approach and should be implemented in Phase 1 (see IQ-9 in the plan).

**Attribution carried in-app** (Attributions screen + map long-press credit): © OpenStreetMap contributors (ODbL), Google Open Buildings, BNGRC / OCHA administrative boundaries, and an "unofficial service — codes have no legal value" disclaimer mirroring the website.

**Coverage figures** (for the About screen): 24 regions, 114 districts, 1 707 communes, 17 465 fokontany.

---

## 10. Internationalization (en / fr / mg)

- Standard Android resource qualifiers: `values/` (English default), `values-fr/`, `values-mg/`. Every string localised; **no hard-coded user-facing text**.
- **First-run language chooser** appears before anything else, with the three languages shown in their own names (English · Français · Malagasy). Default preselection follows the system locale, falling back to Malagasy for unknown/`mg`.
- Language is user-overridable at any time in Settings and applied without reinstall via a per-app locale (AndroidX `AppCompatDelegate` locale APIs).
- **Malagasy specifics:** Latin script with diacritics (ô, à, é, etc.) — covered by the default Roboto font, so no custom font is needed. Number, distance, and time formatting use locale-aware formatters; distances shown in metric (m / km).
- Right-to-left: not required (all three languages are LTR), but layouts use `start`/`end` rather than `left`/`right` for cleanliness.
- Navigation instruction phrases (Phase 3) are templated per language (§14.3) so distances and street names slot into localised sentence frames.

---

## 11. Design system

**Principle:** default Android Material components, re-skinned only through the theme's colour tokens to match the Adiresy brand (teal `#0D9488`). No custom-drawn widgets except the map surface and the navigation maneuver banner.

### 11.1 Colour tokens

Brand primary is teal `#0D9488` (the site's `theme-color`; Tailwind *teal-600*). The site supports light and dark; the app mirrors both, following the system setting with a manual override (Auto / Light / Dark), matching the website's Auto/Clair/Sombre control.

**Light theme**

| Token (Material) | Hex | Role |
|------------------|-----|------|
| `colorPrimary` | `#0D9488` | Brand, app bar, primary buttons, active states |
| `colorPrimaryDark` (status bar) | `#0F766E` | Darker teal (teal-700) |
| `colorOnPrimary` | `#FFFFFF` | Text/icons on primary |
| `colorSecondary` | `#14B8A6` | Secondary accents (teal-500) |
| `colorOnSecondary` | `#062E2A` | — |
| `android:colorBackground` | `#FFFFFF` | Screen background |
| `colorSurface` | `#FFFFFF` | Cards, sheets |
| `colorSurfaceVariant` | `#F1F5F9` | Subtle fills (slate-100) |
| `colorOnSurface` | `#0F172A` | Primary text (slate-900) |
| `textSecondary` | `#475569` | Secondary text (slate-600) |
| `colorOutline` / divider | `#E2E8F0` | Borders (slate-200) |
| `colorError` | `#DC2626` | Errors (red-600) |
| success | `#059669` | Confirmations (emerald-600) |
| Route line (active) | `#0D9488` | On map |
| Route line (alternate) | `#94A3B8` | Slate-400 |
| User location dot | `#2563EB` | Conventional blue for "you" |
| Adiresy building pin | `#0D9488` | Brand teal |

**Dark theme**

| Token | Hex | Role |
|-------|-----|------|
| `colorPrimary` | `#14B8A6` | Lighter teal for contrast on dark (teal-500) |
| `colorOnPrimary` | `#04312C` | — |
| `android:colorBackground` | `#0B1220` | Near-black slate |
| `colorSurface` | `#1E293B` | Cards/sheets (slate-800) |
| `colorSurfaceVariant` | `#334155` | Slate-700 |
| `colorOnSurface` | `#E2E8F0` | Primary text (slate-200) |
| `textSecondary` | `#94A3B8` | Slate-400 |
| `colorOutline` / divider | `#334155` | Slate-700 |
| `colorError` | `#F87171` | Red-400 |
| Route line (active) | `#2DD4BF` | Teal-400 |

Two `style.json` variants (light/dark) keep the *map* legible in each theme and switch with the app theme.

**Map surface palette** (inline style generated by `StyleLoader` when offline tiles are loaded — matches the adiresy.mg website):

| Element | Light | Dark |
|---------|-------|------|
| Land / background | `#E8DDD3` (warm sandy beige) | `#13151a` |
| Building fill | `#BEBBB5` (warm grey) | `#3a3a4a` |
| Building extrusion outline | `#9E9B95` | `#252530` |
| Water | `#a8d4f5` | `#1a3a5c` |
| Road (minor/residential) | `#F8F5EE` | `#3a3a4a` |
| Road (primary) | `#ffd27f` | `#4a4a6a` |
| Road (trunk/national) | `#f6a623` | `#5a4a2a` |
| Admin boundary | `#c8a870` | `#4a3a2a` |

**3D buildings**: the buildings overlay (`buildings-fill`) uses `fill-extrusion` at a fixed height of **5 m** (VIDA footprint tiles carry no height data). A directional `light` at azimuth 210° / polar 35° (viewport-anchored) creates shadow faces on the right and bottom sides of each building, matching the website's rendering. Opacity 0.95; visible at zoom ≥ 14.

### 11.2 Typography, iconography, spacing

- **Type:** system default (Roboto) via Material text appearances. No bundled fonts.
- **Icons:** Material Symbols / default vector icons; no licensed third-party icon packs.
- **Touch targets:** ≥ 48 dp. **Body text:** ≥ 16 sp, respecting the user's font-scale.
- **Components used:** `BottomNavigationView` (Map · Favourites · Settings — no toolbar), `SearchBar` + `SearchView` (Material3 search overlay), `FloatingActionButton` (locate me; new list in Favourites tab), `BottomSheetBehavior` (nearby results, add-to-favourites picker), `MaterialButton`, `BottomSheetDialogFragment`, `MaterialCardView`, `RecyclerView`, `Snackbar` (with Undo action for bookmark removal), `MaterialAlertDialog` (list create/edit, delete confirmation, emoji picker grid), `CircularProgressIndicator`/`LinearProgressIndicator`, `GridView` (emoji picker).

---

## 12. Permissions & privacy

| Permission | When | Why |
|-----------|------|-----|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Requested contextually on first "Locate me" / navigation | Centre map, find nearby buildings, navigate |
| `INTERNET` / `ACCESS_NETWORK_STATE` | — | API calls (when online) and data downloads |
| `FOREGROUND_SERVICE` (+ `FOREGROUND_SERVICE_LOCATION`) | Phase 3 only | Active turn-by-turn navigation |
| `POST_NOTIFICATIONS` | Phase 3 (API 33+) | Navigation notification |

No background location outside active navigation. No account, no analytics by default. GPS coordinates are used on-device and are sent to the Adiresy API only for explicit, user-initiated online lookups (locate-me, building-tap resolve, nearby buildings, search, routing fallback) — never tracked, batched, or persisted server-side by the app beyond that single request. Not persisted on-device beyond the current session/cache the user controls. The app states this plainly in an in-app privacy note, consistent with Adiresy's privacy-first posture.

---

## 13. Offline data management

A dedicated **Offline data** screen manages up to three independent downloadable tile files per selected region/tier.

### 13.1 Base map download

The user selects a **scope** (National or one of six provinces) and a **quality tier**:

| Tier key | Label | National size | Detail |
|---|---|---|---|
| `z12` | Overview | ~43 MB | Main roads and towns |
| `z13` | Standard _(recommended default)_ | ~102 MB | Block-level detail |
| `z14` | Detailed | ~302 MB | Street-level |

Province options: Antananarivo, Fianarantsoa, Toamasina, Mahajanga, Toliara, Antsiranana. Province sizes vary — always show the actual `size_bytes` from the manifest for the selected region/tier, not a flat estimate.

The national base map and one regional base map can be held simultaneously. The map renderer prefers the regional file when the viewport is within that province; the national file is the country-wide fallback.

### 13.2 Buildings overlay (opt-in)

A separate **Buildings** download card, clearly labelled with its own size, appears below the base map card. It is an explicit secondary opt-in — never bundled silently with the base map download — because it roughly triples the download size at every tier. The card must show the overlay's individual `size_bytes` from the manifest.

If the `buildings` key is absent from the current manifest (possible when the upstream VIDA extraction failed), hide the buildings card entirely — do not treat it as an error.

Attribution required when buildings overlay is loaded: _"Building data: Google Open Buildings, Microsoft, OpenStreetMap (via VIDA)"_

### 13.3 Boundaries overlay

A single `boundaries.pmtiles` (~13 MB, whole country) covering all four Malagasy admin levels. Small enough to offer without size-conscious opt-in UX — can be treated as a one-time download bundled with any base map download, or offered as a simple toggle. Show its `size_bytes` from the manifest.

If the `boundaries` key is absent from the manifest, omit the download silently.

Attribution pending: BNGRC/OCHA source is CC BY-IGO — a legally distinct license from ODbL/CC-BY; a precise credit line has not yet been drafted (tracked as TG-15).

### 13.4 Shared download behaviours

- Downloads run through **WorkManager**, **Wi-Fi-only by default** (with an explicit "use mobile data" override that warns about cost), and are **resumable** after interruption or power loss.
- Checksum (SHA-256) is verified per-file after download; retry on mismatch. Each file (base map, buildings, boundaries) has its own checksum entry in the manifest — verify independently.
- Each download has its own progress bar and delete button; managed independently.
- Files are stored in app-specific external storage (`getExternalFilesDir("map")`).
- A subtle prompt offers updates when a newer data version exists; never auto-downloads over mobile data.
- Data freshness varies by layer (see §8) — do not display a single "data as of" date that implies freshness for all three.

Cold behaviour on a fresh install with no download yet: the app is fully usable for *resolving codes online* and shows a clear call-to-action to download offline maps over Wi-Fi.

---

## 14. Feature specifications

### 14.1 Phase 1 — Core offline addressing

**Map (combined Home + Map + Search — full screen, no toolbar)**

The primary screen is a full-screen `CoordinatorLayout` with floating overlays. Layout (bottom-up):

- **MapLibre `MapView`** fills the entire screen, behind all overlays. Starts at Madagascar overview (Z5); jump-to on locate.
- **Floating `SearchBar`** (Material3) anchored top, 8 dp inset. Tapping expands a `SearchView` overlay with recent searches and live autocomplete (code / place / admin unit). Results: code → `CodeDetailActivity`; place → map pan. Recent searches cached locally.
- **Locate Me FAB** (bottom-end corner, teal): requests location permission contextually, acquires a fix, centres map at Z15, loads nearby buildings. FAB translates up as the results sheet rises.
- **Nearby results bottom sheet** (hidden until locate-me completes; peekHeight 180 dp; hideable): drag handle, "Nearby buildings" header, list of `AddressEntity` cards. Tap a card → code card bottom sheet with the code in large type, fokontany + hierarchy, **Share**, **Copy**, **Open in Maps** (geo intent), and a greyed-out **Navigate** (Phase 2).
- **Building tap → code card**: tapping a rendered building polygon (`queryRenderedFeatures` on the `buildings-fill` layer) triggers a reverse-geocode call (`/api/v1/addresses/reverse/?lat=…&lng=…&limit=1&radius=100`). While the call is in flight a semi-transparent scrim with an indeterminate `CircularProgressIndicator` is shown and the map is non-interactive. On success the **code card bottom sheet** slides up (code in large type, fokontany + commune/district, **Share**, **Copy**, **Open in Maps**). On failure, two distinct Snackbar messages: "No connection — connect to look up this building" (network error) or "No address found for this building" (API 404 — building exists in tiles but not yet coded). **Offline cache**: successful tap results are written to Room DB by canonical code and spatially indexed; on a subsequent tap of the same building without network the cached result is served (`Result.stale`). No address labels are rendered on the map — building tile geometry carries no code data; identity is resolved on demand. **Open in Maps** fires an `Intent(ACTION_VIEW, "geo:LAT,LNG?q=LAT,LNG(CODE)")` — routes to any installed maps app (Google Maps, OsmAnd, etc.) and drops a pin labelled with the address code.
- **No-tiles banner** (below SearchBar, `errorContainer` colour, hidden by default): shown when no offline PMTiles are loaded.
- Long-press on map → attribution bottom sheet.
- Honest banner on first launch: "Unofficial service — codes have no legal value," localised.

**Resolve a shared code**
- Deep link `https://adiresy.mg/{code}` (and a custom scheme) opens the app to the **Code detail** screen: marker on map, admin info, **"Navigate"** (enabled in Phase 2; before that, hands off to an external maps app via intent, like the website's *Itinéraire*).

**Settings**
- Language (en/fr/mg), Theme (Auto/Light/Dark), Offline data, About & attributions, Privacy note.

**Favourites (Bookmarks)**

Users can save any building that has a resolved Adiresy code to a named personal list, fully offline, with no account required. All data is stored locally in Room — nothing leaves the device.

_Data model (DB version 3):_

- `BookmarkListEntity` — `id` (autoincrement), `name` (required, ≤ 40 chars), `description` (optional, ≤ 120 chars), `emoji` (single Unicode scalar chosen from a curated picker; default `📍`), `created_at`.
- `BookmarkEntity` — `id` (autoincrement), `canonical_code` (**unique** — a building can belong to at most one list at a time), `latitude`, `longitude`, admin hierarchy snapshot (`fokontany_name`, `commune_name`, `district_name`, `region_name`), `name` (optional user label ≤ 60 chars, e.g. "Home", "Clinic"), `user_description` (optional notes ≤ 120 chars), `list_id` (FK → `BookmarkListEntity`), `saved_at`. The hierarchy snapshot is captured at save time so the bookmark row is meaningful offline even if the Room address cache is later cleared.

_Save flow:_

The code card action row is **icon-only** — four `ImageButton`s at 48 dp each in a single horizontal row, no text labels. Left to right: **Bookmark** · **Share** · **Copy** · **Navigate**. This fits comfortably on the 320 dp minimum screen width without scrolling or stacking. Content descriptions are set on each button for TalkBack.

The bookmark icon is outline (`ic_bookmark_border`) when unsaved and filled (`ic_bookmark`, teal tint) when saved. Tapping when unsaved opens an **"Add to favourites"** `BottomSheetDialogFragment` listing the user's named lists (each row: emoji · name · bookmark count). A **"+ New list"** row at the bottom opens the create-list dialog without closing the sheet — the RecyclerView refreshes via LiveData once the list is created. After selecting a list, two optional inline fields appear: **Name** (e.g. "Home", "Office") and **Notes** (longer description); confirming saves immediately. A building can belong to **at most one list at a time**. If the building is already saved in a different list, saving it to the new list silently moves it (`list_id` updated in place, no delete + re-insert) and a "Moved to [list name]" Snackbar is shown. Tapping the bookmark icon when the building is already saved removes it immediately and shows a Snackbar — "Removed from [list name]" — with a timed **Undo** action.

_Create / edit list dialog:_

`MaterialAlertDialog` with three inputs:
1. **Emoji** — tapping the large emoji display opens a secondary dialog containing a `GridView` of ~40 curated place/category emojis (e.g. `📍 🏠 🏥 🏫 🏪 🏢 🏭 🏨 🏦 🏛️ ⭐ ❤️ 🔑 🛒 🎯 🏕️ 🏗️ …`). Each cell is a `TextView` rendering the emoji at 28 sp in a 48 dp square. This approach is compatible with API 24+ without relying on system emoji picker APIs introduced in Android 12.
2. **Name** — `TextInputLayout` (outlined), required, 40-char limit with counter.
3. **Description** — `TextInputLayout` (outlined), optional, 120-char limit with counter.

Default emoji `📍`. Edit re-opens the same dialog pre-filled.

_Favourites tab:_

The third item in `BottomNavigationView` (bookmark icon), between Map and Settings, labelled **Favourites**. `SavedFragment` is the root.

- **Empty state** (no lists): centred icon, "No favourites yet", body text "Open any building's code card and tap the bookmark icon to add it to your favourites."
- **Lists view**: `RecyclerView` of `MaterialCardView` items. Each card: emoji (~28 sp), list name (bold title), description (secondary, one line, ellipsised), bookmark count ("3 places"). Cards are vertically scrollable; no horizontal swipe on the list-level view. FAB (teal, `+` icon) always visible — creates a new list.
- Tapping a card → `BookmarkListDetailFragment` (pushed onto the back stack within the fragment container; system Back returns to `SavedFragment`).

_Bookmark list detail:_

Shows all bookmarks within one list in a `RecyclerView`. Toolbar displays the list emoji + name; overflow menu offers **Edit list** and **Delete list**. Each bookmark row: user **name** as primary line (falls back to `canonical_code` if not set); fokontany · commune · code on the secondary line; **notes** in italic below that (hidden if empty); saved date ("Today", "Yesterday", "N days ago"). Tapping a row switches to the Map tab, clears the back stack, and animates the map to the bookmark's coordinates at zoom 15. Row overflow → **Edit description**, **Move to another list** (re-opens the add-to-favourites sheet), **Delete** with Undo Snackbar.

Delete list: `MaterialAlertDialog` — "Delete [name]? All [n] saved places will also be removed." Confirmed deletion is permanent (CASCADE in Room).

_Map integration:_

Favourite buildings are shown on the map as teal bookmark-pin icons (`ic_bookmark_pin_24`) in a MapLibre `SymbolLayer` backed by a `GeoJsonSource`. On every `onCameraIdle` event, the fragment queries `BookmarkEntity` for rows whose coordinates intersect the visible bounding box and updates the source. The layer is **only populated at zoom ≥ 11** (city level); below that threshold the source is cleared without a DB query, keeping performance acceptable on low-end devices. Tapping a bookmark row in the detail screen switches to the Map tab and pans/zooms to the saved coordinates at zoom 15; if the map style has not yet loaded the focus is buffered and applied in the `setStyle` callback.

_Search integration:_

When the user types in the search bar, a "Favourites" chip section appears at the top of the `RecyclerView` (above live API suggestions) for any bookmarks whose `canonical_code`, `name`, or `user_description` contains the query string (case-insensitive, local Room query). Each result row carries the bookmark icon, the user name/description, and the list name. Tapping navigates identically to a list-detail row tap.

_Export and import:_ accessible from **Settings → Export / Import bookmarks**. Export serialises all lists and bookmarks to a JSON file and shares it via `Intent.ACTION_SEND` (the standard Android share sheet — the user can save to Files, send via WhatsApp, email, etc.). Import reads a previously exported file via `Intent.ACTION_GET_CONTENT` (`application/json`); the app merges the imported lists into the existing database (lists with the same name are kept side by side, not merged). Both operations are fully offline. The JSON schema is versioned (`"schema_version": 1`) so future releases can migrate it.

_Constraints and limits:_ all data is stored locally; no server sync. Maximum 500 bookmarks across all lists — enforced with a Snackbar warning at 490 and a hard block at 500. The 500-bookmark ceiling keeps Room queries fast on low-end devices.

**Acceptance (Phase 1):** with airplane mode on and offline maps downloaded, a user can open a shared code, see the building on the map, explore admin units, read the code aloud, and find previously saved buildings in their named lists — with zero network.

### 14.2 Phase 2 — Offline routing

- `RouteEngine` backed by **GraphHopper** loads the prebuilt graph from local storage.
- From the Code detail "Navigate", compute a route from the current GPS fix to the code's coordinates, **fully offline**.
- Draw the route polyline as a MapLibre line layer (brand teal); show summary (distance, estimated time) and a scrollable **step list** with maneuver text and per-step distance.
- Profiles: **car** and **foot** at minimum (selectable). Bicycle optional.
- Graceful handling when the destination or origin lies outside the road graph's knowledge (common in deep-rural OSM gaps): show a straight-line bearing + distance fallback and a clear message.

**Acceptance (Phase 2):** offline, the app returns a sensible route and step list between two in-network points, and degrades clearly where OSM road data is missing.

### 14.3 Phase 3a — Turn-by-turn navigation UI

Built with **default Android UI**, in Java, over GraphHopper's instruction stream:

- **Maneuver banner** (top): next-turn icon, instruction text, distance-to-maneuver, and street/target name — the one bespoke composite view, assembled from standard `TextView`/`ImageView`.
- **Map** follows the user (camera tracks location, tilts optionally), route highlighted, traversed portion dimmed.
- **Bottom bar:** remaining distance, ETA, and a large **Stop** button.
- **Off-route detection & re-routing:** a guidance state machine watches the location stream; when the user deviates beyond a threshold, it recomputes offline via `RouteEngine`.
- **Arrival** state with confirmation.
- Runs as a **foreground service** with a persistent notification so guidance survives screen-off. Landscape supported.

Instruction phrasing is templated per language, e.g.:
- en: `"In {distance}, turn {direction} onto {street}."`
- fr: `"Dans {distance}, tournez à {direction} sur {street}."`
- mg: `"Rehefa {distance}, mivilia {direction} mankany {street}."`
(Malagasy phrasings to be finalised with a native reviewer — OQ-4.)

**Acceptance (Phase 3a):** offline guided navigation with live maneuver updates, off-route re-routing, and arrival — no network.

### 14.4 Phase 4 — Voice guidance (en / fr / mg)

Voice guidance is a separate phase after Phase 3 visual navigation is stable. The tech stack is **open** and will be decided during Phase 3 based on what is available at that time.

Known constraints that the chosen stack must satisfy:
- Fully offline on a low-end device (no streaming TTS).
- Malagasy is not supported by mainstream TTS engines (including Google TTS). A native Malagasy reviewer is identified. Delivery options to be decided at Phase 4 planning: (a) AI-generated Malagasy voice bundled as audio clips, or (b) clips recorded by the reviewer. Both approaches result in a finite bundled clip library stitched at runtime by `VoiceGuide`.
- English and French offline TTS voices are available via Android `TextToSpeech` with downloadable voice packs.
- Audio ducking during prompts; a mute toggle; prompts at standard distance thresholds.
- `VoiceGuide` interface (`speak(Maneuver, Locale)`) is defined in Phase 3 as a stub so Phase 4 can drop in an implementation without touching callers.

**Acceptance (Phase 4):** clear spoken guidance in the selected language, offline, on a low-end device.

---

## 15. Performance & low-end strategy

- **Memory:** rely on MapLibre memory-mapping PMTiles rather than loading tiles into heap; cap in-memory caches; avoid large bitmaps; recycle `RecyclerView` rows; release the routing graph when not navigating.
- **Startup:** defer heavy init (graph load) until first use; lazy-load map style; keep the first frame cheap.
- **APK/AAB:** per-ABI splits, resource shrinking, `minifyShrinkResources`, WebP assets; no bundled map/graph data in the base install.
- **Animations:** minimal; honour system "reduce animations"; avoid continuous repaint.
- **Battery:** no background location outside navigation; batch location updates; stop the foreground service promptly on arrival/stop.
- **Storage:** per-region downloads; show sizes before download; make everything deletable.
- **Data:** Wi-Fi-only downloads by default; OkHttp caching; never poll.

---

## 16. Testing strategy

**Device matrix (physical where possible):**

| Tier | Example | Purpose |
|------|---------|---------|
| Entry Android Go, 1–2 GB | itel / Tecno / Infinix budget | Worst-case memory, CPU, storage |
| Budget mainstream | Xiaomi Redmi A-series | Most common real device in Madagascar |
| Mid Samsung | Galaxy A-series | OEM skin (One UI) behaviours |
| Modern reference | Pixel / recent arm64 | Baseline correctness |

**Scenarios:** airplane-mode end-to-end (Phase 1 & 2), interrupted download + resume, power-loss mid-download, language switch mid-session, deep-link resolve, off-route re-route (Phase 3), voice in each language, rural coordinate with missing road data.

**Automation:** JUnit for `AddressResolver`/`RouteEngine`/template logic; Espresso for critical flows; a monkey/stress pass on the low-end tier for ANR/OOM.

---

## 17. Build & release

- **Gradle**, Java source/target **21** (toolchain), core library desugaring enabled (`desugar_jdk_libs`), AndroidX, Material Components. Java 21 virtual threads are a JVM feature and are not available on ART; do not use them.
- **App Bundle (AAB)** for Play Store + **per-ABI APKs** (`arm64-v8a`, `armeabi-v7a`, universal) for direct download — all built and signed by the same GitHub Actions workflow from the same keystore.
- **Distribution:** Google Play (AAB) + direct APK download via **GitHub Releases**. See `docs/Release-Pipeline-Plan.md` for the full CI/CD pipeline.
- **Versioning:** `versionCode` derived from total Git commit count (monotonically increasing); `versionName` from the Git tag (e.g. `v1.2.0`). Release triggered by pushing a version tag.
- Signing keystore stored as an encrypted GitHub Secret; never committed to the repository.
- A data-version constant surfaced in Settings → About.

---

## 18. Analytics & telemetry

Off by default. If any crash reporting is added later, it must be privacy-preserving, opt-in, and never transmit coordinates or codes. No advertising SDKs.

---

## 19. Risks & mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| OSM road/building gaps in rural areas | Routing/nearby fails where data thin | Straight-line fallback; crowd-fix path to OSM; set expectations in UI |
| Adiresy API schema differs from assumptions | Integration rework | All wire format behind `AdiresyApi` adapter (§9) |
| Malagasy TTS unavailable | No native voice | Pre-recorded clip library (§14.3b) |
| Data/graph size vs tiny storage | Users can't download | Per-region downloads; size shown up front; deletable |
| Android Go / OEM skin quirks | Crashes, ANRs | Low-end device matrix; conservative memory budget |
| Offline reverse-geocode not bundleable | Locate-me weaker offline | Pin-drop fallback; online resolve when available |
| Battery drain in navigation | User distrust | Foreground service discipline; prompt stop on arrival |

---

## 20. Assumptions

- The Adiresy API is publicly reachable, permits client use, and offers resolve / reverse / search / hierarchy capabilities (exact shapes TBC against the live docs).
- National OSM and admin-boundary data may be redistributed within the app under their licences (OSM ODbL; BNGRC/OCHA per source terms), with attribution.
- Voice guidance is Phase 4 (separate from Phase 3 visual navigation); tech stack TBD at Phase 4 planning.
- "Default Android UI" means Material Components with the brand theme, not a bespoke design system.

## 21. Open questions

- **OQ-4 (Malagasy phrasing & voice):** ✓ Partially resolved — a native Malagasy reviewer is identified. A phrase review document covering all navigation vocabulary (maneuver types, distance phrases, status cues) in en/fr/mg must be generated and sent to the reviewer before Phase 3 begins. Voice delivery for Phase 4 will be either AI-generated or recorded by the reviewer — decision deferred to Phase 4 planning. Standard (Merina-based) Malagasy assumed. TODO: generate `docs/Malagasy-Phrase-Review.md` before Phase 3.

---

## 22. References

- Adiresy — homepage, About, and API docs, `https://adiresy.mg` (brand colour `#0D9488`; coverage 24/114/1707/17465; data credits: OSM, Google Open Buildings, BNGRC/OCHA).
- DataReportal, *Digital 2025: Madagascar* (internet penetration ≈ 20%; ~80% offline; rural ≈ 58%).
- Worlddata.info / ITU, *Telecommunication in Madagascar* (monthly basket ≈ US$8, +55% YoY; ~35% 4G).
- Intelpoint / Statcounter / Start.io, African & Madagascar device market share 2024–2025 (Xiaomi, Transsion (Tecno/Infinix/itel), Samsung budget; Android Go prevalence).
- MapLibre Native Android (PMTiles support from 11.7.0).
- Geofabrik (Madagascar OSM extract); Planetiler; GraphHopper (Apache-2.0, on-device routing).

---

## Appendix A — Colour tokens (quick copy)

```
# Brand
teal-600  #0D9488   (primary)
teal-700  #0F766E   (primary dark / status bar)
teal-500  #14B8A6   (secondary / dark primary)
teal-400  #2DD4BF   (dark route line)

# Neutrals (light)
white     #FFFFFF
slate-100 #F1F5F9
slate-200 #E2E8F0
slate-600 #475569
slate-900 #0F172A

# Neutrals (dark)
#0B1220  background
slate-800 #1E293B  surface
slate-700 #334155  variant/outline
slate-400 #94A3B8  secondary text
slate-200 #E2E8F0  on-surface

# Semantic
red-600     #DC2626  error (light)
red-400     #F87171  error (dark)
emerald-600 #059669  success
blue-600    #2563EB  user location dot
```

## Appendix B — Glossary

- **Fokontany** — smallest administrative unit in Madagascar (below commune).
- **Faritra** — region (24 nationally).
- **PMTiles** — single-file archive of vector map tiles, readable offline via range access.
- **Graph (routing)** — GraphHopper's preprocessed road-network structure used to compute routes offline.
- **Android Go** — lightweight Android edition for low-RAM budget devices.
