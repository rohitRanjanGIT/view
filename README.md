# Gallery

A fast, offline photo and video gallery for Android, built to be a daily-driver replacement for the stock
gallery. The layout and gestures are inspired by Samsung's One UI Gallery; the code, icons and assets are original.

Smoothness and data safety come before feature count: every screen is meant to feel instant on a library of
50,000+ items, and the app never touches the network.

## Principles

- **Never lose media.** Originals are never modified in place. Deleting moves items to the system trash
  (`MediaStore.createTrashRequest`), and permanent deletion only happens from the Trash screen after a confirmation.
- **Offline and private.** The app does not declare the `INTERNET` permission. No analytics, no cloud, no SDKs
  that phone home.
- **No main-thread I/O.** MediaStore queries, file access and image decoding all run on background dispatchers.

## Features

- **Pictures timeline**: every photo and video grouped by day, with pinned day headers, video duration badges and
  a fast-scroll handle with a month bubble. New photos from the camera appear without a restart.
- **Viewer**: horizontal pager, pinch and double-tap zoom with tiled decoding for huge images, a shared-element
  flight between the grid and the viewer, drag in any direction to dismiss, a bottom filmstrip, and a details
  panel (date, path, size, resolution, EXIF, location text). The tapped photo starts decoding on tap, so it is
  usually sharp by the time the viewer opens.
- **Video**: Media3 ExoPlayer with a scrubber, mute, keep-screen-on and resume position.
- **Albums**: album list and album detail; pin, custom order, delete (to Trash), new album and move photos into an
  album (the phone's own folders); a side strip in an album to jump between albums.
- **Locked**: a private folder only this app can open (fingerprint or screen lock), opened by a hidden gesture or
  from Settings; turning it off moves everything back.
- **One-handed reach**: pull any grid down to bring its top rows within thumb reach; the title glides with it.
- **Selection**: long-press, then drag to select a range with auto-scroll at the edges; share, favorite, move to an
  album, lock, copy to clipboard, and trash (with Undo).
- **Favorites, Videos and Trash** pages. Trash supports restore and permanent delete.
- **Opens files from other apps**: `ACTION_VIEW` and camera review intents for images and videos, including
  when the app has no media permission.
- Material 3 with Material You dynamic colour, dark and light themes, edge-to-edge.

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.2, coroutines and Flow |
| UI | Jetpack Compose (BOM 2026.02.01), Material 3, `SharedTransitionLayout` |
| Navigation | Navigation Compose with type-safe routes |
| DI | Hilt (KSP) |
| Media | `MediaStore` via `ContentResolver`, `ContentObserver` for live updates |
| Images | Coil 3, Telephoto (zoom and sub-sampling) |
| Video | Media3 ExoPlayer |
| Build | AGP 9.5, Gradle version catalog, R8 for release |

Android 13 (API 33) and newer; built against API 37.

## Architecture

A single `app` module, organised by feature, with unidirectional data flow:
Compose UI → ViewModel (`StateFlow`) → repository → data source.

The media index is the core design choice. On start the app queries MediaStore once for a lightweight row per
item and keeps the whole timeline in memory as an immutable list exposed through a `StateFlow`. A debounced
`ContentObserver` refreshes it. There is deliberately no paging: the grid, fast-scroller, date headers and viewer
all need the full timeline. MediaStore stays the source of truth for media.

```
app/src/main/java/com/rohit/gallery/
├── core/
│   ├── data/          MediaStore data source, in-memory index, media actions
│   ├── model/         MediaItem, Album, ...
│   ├── permissions/   full / partial / denied media access
│   └── ui/            theme, motion specs, shared components (thumbnails, fast-scroller)
├── feature/
│   ├── timeline/      Pictures tab
│   ├── albums/        Albums tab and album detail
│   ├── viewer/        pager, zoom, video, details panel, shared-element flight
│   ├── selection/     multi-select state, grids and action bars
│   ├── favorites/  videos/  trash/  settings/
└── navigation/        routes and nav host
```

Non-obvious technical decisions, and the reasons behind them, are logged in
[`docs/DECISIONS.md`](docs/DECISIONS.md).

## Building

Requirements: Android Studio (or the Android SDK command-line tools) with API 37 installed, and JDK 17+.

```sh
# Windows
.\gradlew assembleDebug
.\gradlew testDebugUnitTest

# macOS / Linux
./gradlew assembleDebug
```

Install on a connected device with `./gradlew installDebug`. A `release` build (R8 and resource shrinking) and a
`benchmark` build type are also configured.

## Status and roadmap

- **Phase 0, setup**: done.
- **Phase 1, foundation** (permissions, MediaStore index, timeline): done.
- **Phase 2, core daily-driver UX** (viewer, video, albums, favorites, trash, multi-select, external intents,
  fast-scroller): done; the final full-day test is in progress.
- **Phase 3, polish**: pinch to change grid density and month/year views, an editor that saves copies, search,
  hidden albums and sort options, settings, GIF / motion photo / Ultra HDR support.
- **Phase 4, hardening**: Baseline Profiles and Macrobenchmarks, signed release build, memory and accessibility
  passes.

This is a personal project, sideloaded rather than published on the Play Store.
