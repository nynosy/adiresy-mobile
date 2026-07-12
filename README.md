# Adiresy Mobile

Native Android client for [Adiresy](https://adiresy.mg), an *unofficial* addressing service for Madagascar. Every building gets a short, permanent code anchored to GPS coordinates (e.g. `101-6EAR-50418`) — this app lets people find, share, and navigate to those codes, working on low-end phones and, progressively, fully offline.

> Codes have no legal value — Adiresy is an unofficial, community-driven addressing layer, not a government service.

## Why this app exists

Madagascar has no formal street-addressing system in most areas. Adiresy fills that gap on the web; this app brings it to the phones people actually use there. The design north-star:

> A person on a 2 GB Transsion or Xiaomi phone, on 2G or with no signal at all, on a low battery, who is more comfortable in Malagasy than French, must be able to find and reach an address.

Every design decision — offline-first data, a ≤25 MB base install, Java + default Android UI instead of heavier frameworks, trilingual support — is measured against that user.

## Features

1. **Find** the Adiresy code for a building and share it as a link.
2. **Resolve** a shared code and see the building on a map.
3. **Navigate** to that building — offline routing, turn-by-turn UI, and spoken guidance, rolling out in phases.

| Phase | Theme | Status |
|-------|-------|--------|
| 1 | Core offline addressing — map, locate-me, nearby buildings, code resolve/share, search, favourites, trilingual UI, offline map manager | In progress |
| 2 | Offline routing (on-device A→B route calculation) | Planned |
| 3 | Turn-by-turn navigation UI | Planned |
| 4 | Offline voice guidance (English, French, Malagasy) | Planned |

Each phase ships independently and leaves the app in a coherent, releasable state.

## Design constraints

These come from the realities of the Malagasy market, not preference:

- **Offline-first.** ~80% of the population is offline; core tasks work with the radio off.
- **Budget hardware.** Built and tuned for 1–2 GB Android Go devices (Xiaomi, Tecno/Infinix/itel, Samsung A/M-series).
- **Cheap on data and battery.** No background location or wake-locks outside active navigation; downloads are Wi-Fi-only by default, resumable, and interruption-safe.
- **Malagasy first.** Malagasy, French, and English are all first-class UI languages with full string parity.
- **Privacy-respecting.** No account, no analytics by default. GPS coordinates are sent to the Adiresy API only for explicit, user-initiated lookups (locate-me, building tap, search, routing fallback) — never tracked, batched, or persisted server-side by the app.

See [`docs/Adiresy-Android-Specification.md`](docs/Adiresy-Android-Specification.md) for the full technical specification, including architecture, data pipeline, API integration, and phased feature detail.

## Tech stack

| Concern | Choice |
|---------|--------|
| Language | Java 21, `minSdk` 24 (Android 7.0), `targetSdk`/`compileSdk` 36.1 |
| UI | Android Views + Material Components (no Jetpack Compose, to stay light on Android Go) |
| Architecture | MVVM (`ViewModel` + `LiveData`) |
| Map | MapLibre Native Android, offline PMTiles vector tiles |
| Networking | Retrofit + OkHttp against the Adiresy REST API |
| Local storage | Room (resolved codes, admin units, search history, favourites) |
| Background downloads | WorkManager (resumable, Wi-Fi-constrained) |
| Offline routing (Phase 2) | GraphHopper |

## Getting started

### Prerequisites

- Android Studio (or the command-line SDK) with a recent Android SDK installed
- JDK 21

No API key is needed — the app registers itself anonymously against the
Adiresy API on first use (see [the technical specification, §9](docs/Adiresy-Android-Specification.md#9-adiresy-api-integration)).

### Setup

1. Clone the repo and open it in Android Studio, or build from the command line.
2. Create/edit `local.properties` in the project root (already gitignored) with:
   ```properties
   sdk.dir=/path/to/your/Android/sdk
   ```
3. Build and run:
   ```bash
   ./gradlew assembleDebug
   ```
   The debug APKs land in `app/build/outputs/apk/debug/`.

### CI

Every push to `main`/`develop` and every PR against `main` builds debug and release APKs via GitHub Actions (see [`.github/workflows/build.yml`](.github/workflows/build.yml)).

## Project structure

```
app/src/main/java/org/github/nynosy/adiresy_mobile/
├─ ui/          Activities, Fragments, adapters
├─ map/         MapLibre setup, style loading, offline tile source
├─ data/        Retrofit API + repository, Room cache, app prefs
├─ routing/     Phase 2 — GraphHopper wrapper
├─ nav/         Phase 3 — turn-by-turn guidance
├─ voice/       Phase 4 — voice guidance
└─ i18n/        Locale management
```

## Documentation

- [Technical specification](docs/Adiresy-Android-Specification.md) — full architecture, data pipeline, API integration, design system, and phased roadmap.

## License

[GNU General Public License v3.0](LICENSE)
