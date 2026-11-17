# Gallery: Master Project Brief

## 1. Mission
Build a fast, polished, offline gallery app for Android, inspired by the UX of
Samsung's One UI Gallery, to be my daily-driver gallery. Smoothness and data
safety beat feature count. Every feature must feel instant on a library of
50,000+ items.

- Owner/sole user: me. Personal app, sideloaded, not for the Play Store (for now).
- Primary device: Motorola Edge 60 Pro, Android 17 (API 37), 120 Hz display.
- Dev machine: Windows. Use `gradlew.bat` / `.\gradlew` for builds.
- The project was scaffolded as an Android Studio "Empty Activity" (Compose)
  project in this repo (`C:\gallery`), app name "Gallery".

## 2. Hard rules (never break these)
1. **Never lose user media.** Never modify original files in place. Deletes go
   through `MediaStore.createTrashRequest` (recoverable). Permanent deletion
   only from the Trash screen via `createDeleteRequest`, with user confirmation.
   Edits are always saved as new copies.
2. **Offline and private.** Do NOT declare the `INTERNET` permission. No
   analytics, no cloud, no third-party SDKs that phone home.
3. **Original work only.** Take inspiration from One UI's layout and behavior,
   but do not copy Samsung code, icons, fonts, strings or other assets.
4. **No main-thread I/O.** All MediaStore, Room, file and bitmap work runs on
   background dispatchers.
5. **Build must pass.** Run `.\gradlew assembleDebug` (and tests, if touched)
   before declaring any task done. Fix warnings you introduce.
6. **Don't guess versions or APIs.** Use the latest stable releases compatible
   with the AGP/Kotlin/Compose versions already in `gradle/libs.versions.toml`.
   If unsure whether an API exists on API 37, check the official docs.
7. **Stay in scope.** Work one phase at a time. Don't add features from later
   phases or unrequested dependencies. Ask before any large architectural change.

## 3. Tech stack
- **Language:** Kotlin, coroutines + Flow
- **UI:** Jetpack Compose, Material 3, Material You dynamic color, edge-to-edge,
  predictive back, shared-element transitions (`SharedTransitionLayout`)
- **Navigation:** Navigation Compose, type-safe routes
- **DI:** Hilt (with KSP, not kapt)
- **Media access:** `MediaStore` via `ContentResolver`, `ContentObserver` for live updates
- **Images:** Coil 3 (+ video frame and GIF decoders); fixed-size thumbnail requests
- **Zoomable viewer:** Telephoto (`me.saket.telephoto`), sub-sampling for huge images
- **Video:** Media3 ExoPlayer + Compose UI
- **Local DB:** Room, only for app-owned data (custom album order, hidden
  albums, edit history, search index). MediaStore stays the source of truth for media.
- **Preferences:** DataStore
- **Background work:** WorkManager (indexing, Phase 3+)
- **Performance:** Baseline Profiles, Macrobenchmark, R8 full mode
- **Tests:** JUnit, Turbine, Compose UI tests for critical flows
- **SDK levels:** minSdk 33, compileSdk/targetSdk 37
- All dependencies go in the version catalog (`libs.versions.toml`).

## 4. Architecture
Single `app` module, package-by-feature, unidirectional data flow (MVVM):
`Compose UI -> ViewModel (StateFlow<UiState>) -> Repository -> data source`.

**Media index (core design decision):** on start, query MediaStore once on
`Dispatchers.IO` for a lightweight row per item (id, uri, mime type, date
taken/modified, width, height, duration, bucket id/name, favorite, trashed, size)
and hold it in memory as an immutable list exposed via `StateFlow`. Re-query
(debounced) when a `ContentObserver` fires. Do not use Paging 3: the grid,
fast-scroller, date headers and density changes all need the full timeline.
Show the grid as soon as the first query returns; target under 500 ms for
50k items.

```
com.rohit.gallery
├── GalleryApp.kt                 // @HiltAndroidApp, Coil ImageLoader config
├── MainActivity.kt               // edge-to-edge, nav host, intent handling
├── core
│   ├── model/                    // MediaItem, Album, MediaType, DateGroup
│   ├── data
│   │   ├── mediastore/           // MediaStoreDataSource, queries, ContentObserver
│   │   ├── repository/           // MediaRepository, AlbumRepository
│   │   ├── db/                   // Room database, DAOs, entities
│   │   └── prefs/                // DataStore settings
│   ├── domain/                   // use cases: TrashMedia, ToggleFavorite, Share...
│   ├── permissions/              // permission state + request helpers
│   ├── di/                       // Hilt modules
│   └── ui
│       ├── theme/                // color, type, shapes, motion specs
│       └── components/           // shared composables (thumbnail, scrollbar...)
├── feature
│   ├── timeline/                 // Pictures tab: date-grouped grid
│   ├── albums/                   // Albums tab + album detail
│   ├── viewer/                   // full-screen pager, zoom, video, info sheet
│   ├── selection/                // multi-select state + action bar
│   ├── trash/                    // recycle bin
│   ├── favorites/
│   ├── search/                   // Phase 3
│   ├── editor/                   // Phase 3
│   └── settings/
└── navigation/                   // routes, nav graph
```

## 5. Permissions and system integration
- `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED`
  (handle full, partial and denied states; show a clear banner to upgrade
  from partial to full access).
- `ACCESS_MEDIA_LOCATION` for EXIF location in the info sheet.
- Write operations (trash, favorite, delete, rename) use the MediaStore
  `create*Request` APIs with `IntentSenderRequest`. Optionally support
  `MANAGE_MEDIA` special access to skip per-action confirmation dialogs,
  off by default.
- Intent filters so the app can be the default gallery: `ACTION_VIEW` for
  `image/*` and `video/*`, and `com.android.camera.action.REVIEW` so the
  camera app's thumbnail opens here. Opening a single external URI must work
  even without media permission.

## 6. UX direction (One UI-inspired, original implementation)
- Bottom navigation: **Pictures**, **Albums**, **Menu** (Favorites,
  Trash, Videos, Settings). One-handed reach: key actions in the lower half.
- Large collapsing title header that shrinks on scroll.
- Timeline grid grouped by day with sticky date headers; pinch to change
  density (e.g. 3 / 4 / 7 columns, plus a month view) with animated transitions.
- Fast-scroll handle with a date bubble.
- Long-press to select, drag to select a range; contextual action bar with
  Share, Favorite, Move/Copy to album, Trash.
- Viewer: horizontal pager, shared-element open/close from the thumbnail,
  drag-down to dismiss, pinch + double-tap zoom, tap to toggle chrome,
  bottom filmstrip, swipe-up info sheet (date, path, size, resolution, EXIF, map-free location text).
- Rounded corners, generous spacing, smooth spring-based motion, full
  dark/light + dynamic color support, 120 Hz friendly.

## 7. Performance budgets
- Cold start to visible thumbnails: under 1 s.
- Timeline scroll and fling: no dropped frames at 120 Hz in release builds
  (verify with Macrobenchmark frame timing).
- Thumbnails: request at exact cell size, hardware bitmaps, stable keys
  (`MediaStore` id), `contentType` set on lazy items, memory cache sized for
  ~3 screens, disk cache enabled. Never decode a full-size image for a grid cell.
- Keep `MediaItem` stable/immutable; avoid recomposition from unstable lambdas
  or lists. Use the Compose compiler reports if scrolling janks.
- Viewer: preload neighbours, show the cached thumbnail as placeholder so
  transitions never flash blank.

## 8. Phases and acceptance criteria

### Phase 0: Project setup (DONE, pending on-device check)
- App name "Gallery", package/namespace/applicationId `com.rohit.gallery`,
  root project name `Gallery`.
- Add dependencies via the version catalog; configure Hilt, KSP, Compose
  compiler, R8 for release, a `benchmark`-ready release-like build type.
- Create the package structure above, theme, empty nav graph with 3 tabs.
- Init git with a sensible `.gitignore`; commit after each completed task.
- **Done when:** `.\gradlew assembleDebug` passes and the app launches to an empty tabbed shell.

### Phase 1: Foundation (DONE, verified on device)
- Permission flow (full / partial / denied / permanently denied).
- MediaStore data source, in-memory index, ContentObserver refresh.
- Timeline grid with Coil thumbnails, video duration badges, day headers.
- **Done when:** my real library appears, scrolls smoothly, and new photos
  from the camera show up without restarting.

### Phase 2: Core daily-driver UX
- Viewer (zoom, pager, shared-element transition, drag-to-dismiss, info sheet).
- Video playback with Media3 (scrubber, mute, keep-screen-on, resume position).
- Albums tab + album detail; Favorites; Trash with restore/permanent delete.
- Multi-select with drag, Share, Favorite, Trash, Move/Copy to album.
- External intents (`ACTION_VIEW`, camera review).
- Fast-scroller with date bubble.
- **Done when:** I can replace my current gallery for a full day without missing anything.

### Phase 3: Polish and power features
- Pinch grid density + month/year views.
- Editor: crop, rotate, flip, basic adjustments, filters; save as copy.
- Search: by date, album, file name, media type; optional on-device ML Kit
  labels/text indexed via WorkManager (bundled models only, no network).
- Hidden albums, custom album order, sort options, Settings screen.
- GIF, motion-photo and HDR/Ultra HDR display support.

### Phase 4: Optimization and hardening
- Baseline Profile + Macrobenchmarks for startup, timeline scroll, viewer open.
- R8 full mode, shrink resources, signed release build I can sideload.
- Memory/leak check, large-library stress test, accessibility pass
  (content descriptions, touch targets, font scaling).

## 9. Working agreement
- At the start of each phase, post a short task list and wait for my OK.
- Small, focused commits with clear messages.
- Small composables, no god-ViewModels, no premature abstraction or
  multi-module split.
- Unit-test repositories and use cases with fakes; UI-test permission, select,
  and trash flows.
- When a task needs my phone (permission dialogs, scroll feel, camera intent),
  tell me exactly what to test and what to report back.
- Keep a `docs/DECISIONS.md` log of non-obvious technical choices, and update
  this file's phase checklist as items are completed.

## 10. First task
Read this brief, inspect the existing project (`settings.gradle.kts`,
`gradle/libs.versions.toml`, `app/build.gradle.kts`, manifest), then propose
the Phase 0 task list and wait for my confirmation before changing anything.

---

## 11. Status and handoff (update this at the end of every work session)

Last updated: 2026-11-17. Read this section first in a new session, then `docs/DECISIONS.md`.

### Done
- **Phase 0 (project setup):** app "Gallery", `com.rohit.gallery`, AGP 9.5.1 (built-in Kotlin),
  Kotlin 2.2.10, Gradle 9.6.0, Compose BOM 2026.02.01, Hilt + KSP, Navigation Compose
  (type-safe routes), release (R8 + shrink) and `benchmark` build types, package structure,
  Material 3 dynamic-color theme, 3-tab shell (Pictures / Albums / Menu).
- **Phase 1 (foundation), verified on my phone:** permission flow, `MediaStoreDataSource` + in-memory
  `MediaRepository` index with debounced `ContentObserver`, day-grouped timeline (sticky headers, video
  badges), own `ThumbnailCache` + prefetch.
- **Phase 2 so far (all committed on `main`; 19 unit tests pass):**
  - **Viewer** (verified by me): pager, Telephoto zoom, shared-element open/close (photo-shaped flight element),
    2D drag-to-dismiss (diagonal too), swipe-up opens details instantly, details panel stacked under the photo
    (photo shrinks upward), swipe-down with details open returns to the photo, filmstrip, info with EXIF /
    lazy `ACCESS_MEDIA_LOCATION`. Files in `feature/viewer/`.
  - **Video** (verified by me, except item 8 of the checklist): Media3 ExoPlayer, scrubber, mute, keep-screen-on,
    in-memory resume position.
  - **Multi-select** (verified by me): long-press + drag-select with auto-scroll, selection bars replacing the nav
    bar, Share, Favorite (toggles), Trash (`createTrashRequest`), Move/Copy to album under More. Shared by all
    grids via `SelectableMediaGrid` and `SelectionActions` / `SelectionActionsBar` in `feature/selection/`.
  - **Trash screen** (verified): Menu > Trash, open in viewer with Restore / Delete forever, select + drag-select.
  - **Favorites** (verified): Menu > Favorites. **Albums tab + album detail** (verified): `feature/albums/`.
  - 22 unit tests pass.
- Release and benchmark builds compile; merged manifests contain no `INTERNET` (re-check each build).

### Waiting on my device report
- Back-close fix (commit `d55ceac`): back button, back swipe and swipe-down should fly the photo into its
  grid cell with no full-screen flash. Check portrait / landscape / square / video / zoomed-in.

### Next steps (Phase 2 order I approved)
1. **External intents** (`ACTION_VIEW` for image/* and video/*, `com.android.camera.action.REVIEW`; must open a
   single external URI even without media permission).
2. **Fast-scroller** with date bubble.
Commit after each task and ask me to test on the phone. Then Phase 3 and Phase 4 as in section 8.

### Testing on my phone (rules)
- Only test with copies, never touch originals (no restore / delete / move of real media without my approval).
- Keep Do Not Disturb on, take a screenshot before every tap, never send blind tap chains (a stray tap once
  started a real call).

### Known gaps / deferred
- Closing the viewer does not scroll the grid to the photo you ended on (optional polish).
- Resume positions are lost on process death (needs DataStore, Phase 3).
- Zoomed-in close snaps to the fitted view before flying to the grid (unpolished).
- `ACCESS_MEDIA_LOCATION` runtime flow untested on device.
- **Phase 4:** request 120 Hz (`preferredDisplayModeId`; app currently renders at 90 Hz, Motorola maps "high" to 90),
  run `assembleRelease` / benchmark builds on the phone (R8 risk), Baseline Profile, accessibility
  `contentDescription`s, collapsing title header, real nav icons (current ones are `material-icons-core`).
- Not added yet (add in the phase that needs them): Room, DataStore, WorkManager, Turbine.

### Gotchas learned (do not re-learn these)
- **Kotlin 2.2.10 compiler cannot read Kotlin 2.4 metadata.** Latest coroutines (1.11), kotlinx-serialization
  (1.13) and Coil (3.5+) fail. Pinned: coroutines 1.10.2, serialization 1.9.0, Coil 3.4.0, Telephoto 0.19.0
  (`me.saket.telephoto.zoomable`), Media3 1.11.1. Check a library's POM `kotlin-stdlib` before bumping.
- `hiltViewModel` lives in `androidx.hilt.lifecycle.viewmodel.compose`.
- Use `@param:ApplicationContext` on constructor params.
- Don't run `python`/`python3` in the shell (hangs). `.gitignore` must use `build/` (not `/build`).
  Heredocs with backslashes lose them: use raw strings or the Write tool.
- Git commits use `-c user.name=rohit -c user.email=rohit@localhost`.
- Shared elements: only the tapped cell and the viewer's current page carry the shared modifier; drag-dismiss
  disables the flight and fades instead (otherwise the grid thumbnail is stretched full screen).

### Running on my phone (Motorola Edge 60 Pro, wireless debugging)
- adb: `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"`. Check with `adb devices -l`.
- The phone's IP/port changes. If it is not listed: `adb mdns services` shows the connect address
  (`adb connect <ip:port>`). If pairing is lost, ask me for a fresh pairing code and run `adb pair <ip:port> <code>`.
- Install and launch: `ANDROID_SERIAL=<serial> ./gradlew.bat installDebug`, then
  `adb -s <serial> shell am start -n com.rohit.gallery/.MainActivity`.
