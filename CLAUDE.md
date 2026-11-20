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
   *Approved exception (2026-11-19):* locking into Locked deletes the original via `createDeleteRequest`,
   but only after the vault copy is verified byte for byte (see DECISIONS "Locked").
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
â”œâ”€â”€ GalleryApp.kt                 // @HiltAndroidApp, Coil ImageLoader config
â”œâ”€â”€ MainActivity.kt               // edge-to-edge, nav host, intent handling
â”œâ”€â”€ core
â”‚   â”œâ”€â”€ model/                    // MediaItem, Album, MediaType, DateGroup
â”‚   â”œâ”€â”€ data
â”‚   â”‚   â”œâ”€â”€ mediastore/           // MediaStoreDataSource, queries, ContentObserver
â”‚   â”‚   â”œâ”€â”€ repository/           // MediaRepository, AlbumRepository
â”‚   â”‚   â”œâ”€â”€ db/                   // Room database, DAOs, entities
â”‚   â”‚   â””â”€â”€ prefs/                // DataStore settings
â”‚   â”œâ”€â”€ domain/                   // use cases: TrashMedia, ToggleFavorite, Share...
â”‚   â”œâ”€â”€ permissions/              // permission state + request helpers
â”‚   â”œâ”€â”€ di/                       // Hilt modules
â”‚   â””â”€â”€ ui
â”‚       â”œâ”€â”€ theme/                // color, type, shapes, motion specs
â”‚       â””â”€â”€ components/           // shared composables (thumbnail, scrollbar...)
â”œâ”€â”€ feature
â”‚   â”œâ”€â”€ timeline/                 // Pictures tab: date-grouped grid
â”‚   â”œâ”€â”€ albums/                   // Albums tab + album detail
â”‚   â”œâ”€â”€ viewer/                   // full-screen pager, zoom, video, info sheet
â”‚   â”œâ”€â”€ selection/                // multi-select state + action bar
â”‚   â”œâ”€â”€ trash/                    // recycle bin
â”‚   â”œâ”€â”€ favorites/
â”‚   â”œâ”€â”€ search/                   // Phase 3
â”‚   â”œâ”€â”€ editor/                   // Phase 3
â”‚   â””â”€â”€ settings/
â””â”€â”€ navigation/                   // routes, nav graph
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
- Bottom navigation: **Pictures**, **Albums**. Favorites, Videos and Trash are tiles at the end of Albums;
  Settings is a swipe left from Albums (no tab). One-handed reach: key actions in the lower half.
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

### Phase 2: Core daily-driver UX (DONE, verified on device; full-day test in progress)
- Viewer (zoom, pager, shared-element transition, drag-to-dismiss, info sheet).
- Video playback with Media3 (scrubber, mute, keep-screen-on, resume position).
- Albums tab + album detail; Favorites; Trash with restore/permanent delete.
- Multi-select with drag, Share, Favorite, Trash, Move/Copy to album.
- External intents (`ACTION_VIEW`, camera review).
- Fast-scroller with date bubble.
- **Done when:** I can replace my current gallery for a full day without missing anything.

### Phase 3: Polish and power features (NEARLY DONE: only the own editor is left)
- [x] Pinch grid density + month view (Pictures, album grids, Albums tab).
- [ ] Editor: crop, rotate, flip, basic adjustments, filters; save as copy. (LATER, my call: for now the viewer's
  Edit button hands the photo to Google Photos' editor with read-only access, so it can only save a copy.)
- [x] Search: by date, album, file name, media type (in memory). ML Kit labels/text skipped for now (my call).
- [x] Pinned albums (hidden albums removed 2026-11-19: Locked replaces them), custom album order, sort options, oldest-first albums.
- [x] Settings screen (theme, wallpaper colours, viewer options, remembered grid sizes and video positions,
  skip-confirmation via MANAGE_MEDIA, About).
- [x] GIF, motion-photo and HDR/Ultra HDR display support (samples in `Pictures/Gallery test` on my phone).

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

Last updated: 2026-11-19. Read this section first in a new session, then `docs/DECISIONS.md` (newest entries at the
bottom). Repo: https://github.com/rohitRanjanGIT/view (`main` tracks `origin/main`; push only when I ask).
`README.md` describes the project for GitHub; keep its Features / Status sections in step when a phase finishes.

### Where we are
- **Phases 0-2 done and verified on my phone. Phase 3 nearly done** (only the own editor is left, deferred by me).
  66 unit tests pass. Merged manifests contain no `INTERNET` (re-check each build).
- **Phase 3 done and verified on my phone:** pinch density, album timelines, album organisation, Albums tab pinch,
  oldest-first albums, search, own back-gesture animations, Edit hand-off to Google Photos, GIF / motion photo /
  Ultra HDR, Settings, the viewer redesign, favourite hearts, tabs that slide with the finger.
- **Decisions:** no ML search for now. Trash stays a flat grid. Own editor later (Edit hands off to Google Photos).
  Hidden albums are gone (merged into Locked, my call).
- **Viewer as an overlay over the grid** and the **Apple-style pass** (springs, projection, rubber-banding, press
  feedback, haptics, frosted bars, typography): done and verified. See DECISIONS "Viewer overlay", "Fluid viewer
  gestures".
- **Polish pass, 2026-11-19 (all verified by me unless marked):**
  - Floating capsule tab bar; selection action bars are the same floating capsule (`FloatingActionPill`).
  - **Pull down to reach** (`core/ui/components/ReachDown.kt`) on every grid: pull down at the top and the grid comes
    down to status bar + 30% of the screen and stays until pushed up (also from the empty space above it). One title
    glides between the centre and the top bar (`ReachBarTitle`); the bar's icons (search, +, ⋮; not Back) ride down
    above the grid (`ReachTopIcons`). Pictures: search sits on the "Today" line.
  - **Album switcher strip** (`AlbumSwitcher.kt`): in an album (and a locked album), swipe right on the grid: a
    narrow strip of covers pushes the grid aside; tap switches album in place, strip stays open.
  - **Locked (vault):** only way in is pushing up past the last album on Albums and holding 1.2 s (buzz), or Settings > Open Locked.
    Settings > Locked folder switch: off asks for the screen lock, moves everything back, then hides all Lock actions.
    Fixed 2026-11-19, not yet re-checked by me: a stray "Locked albums" title in the status bar (moving title now
    measured live).
  - Copy to clipboard (viewer top bar; selection bar with one item; not Trash / Locked). Three-finger screenshot no
    longer zooms grids. Selection mode ends when the last item is deselected. Delete album (to Trash).
  - **New album:** Albums tab + asks a name, then a picker (tap to pick, Copy here / Move here). Verified by Claude on
    the phone (made "Test album" with one copied screenshot; still there, delete it with ⋮ > Delete album).
    **+ inside an album** (add photos to an existing album): committed, not yet tried by anyone. Album review fixes
    (2026-11-19, not yet tried on the phone): folder rules, double-tap guard, Stop, name clash, picker order; see
    DECISIONS "Album fixes after a review". Also not yet tried: dates kept through Trash (restore
    writes back the saved date) and Remove album (keep photos); see DECISIONS.
- **Git:** everything pushed up to `327dd69` (More card) on 2026-11-20. Push only when I say so.
- **Next (pick with me):**
  1. **No Menu** (built 2026-11-20, not yet tried by me): bottom bar is Pictures + Albums. Favourites, Videos, Trash are
     "Collections" tiles at the end of the Albums grid; Settings is a hidden third pager page (swipe left from Albums).
  2. My screenshot idea: a notification offers Share / Share & delete after a screenshot (answers still pending).
  3. App icon (deferred by me). Then Phase 4 (see Known gaps).

### What exists
- **Setup:** `com.rohit.gallery`, AGP 9.5.1 (built-in Kotlin), Kotlin 2.2.10, Gradle 9.6.0, Compose BOM
  2026.02.01, Hilt + KSP, Navigation Compose (type-safe routes in `navigation/Routes.kt`), release (R8 + shrink)
  and `benchmark` build types, Material 3 dynamic colour.
- **Navigation:** `HomeRoute` is one destination holding Pictures / Albums / Settings (no tab) in `HomePager`
  (`navigation/HomePager.kt`: pages follow the finger, no-bounce spring stiffness 900, 30% threshold; still while
  selecting or with two fingers; Back on Settings slides to Albums, on Albums to Pictures; dragging the bottom bar drives it too).
  The pager state lives in `GalleryNavHost`, so the tab survives opening the viewer. Other screens are normal routes.
- **Foundation:** permission flow (full / partial / denied), `MediaStoreDataSource` + in-memory `MediaRepository`
  index (`StateFlow<MediaIndexState>`) with debounced `ContentObserver`, own `ThumbnailCache` + prefetch.
- **Timeline** (`feature/timeline/`): day-grouped `LazyColumn` of rows, sticky day headers pinned below the status
  bar, video / GIF badges, red heart on favourites (`MediaThumbnail`, `FavoriteHeartRed`), fast-scroller with month
  bubble, search button on the pinned header, return-to-photo after the viewer closes (`ViewerReturnTarget`).
- **Viewer** (`feature/viewer/`):
  - Pager, Telephoto zoom (double-tap-and-drag zoom works: `DismissGestures` ignores the second press of a
    double-tap), shared-element open 180 ms / close 380 ms eased (`GalleryMotion`, chosen by direction in
    `mediaSharedElement`), drag-to-dismiss into the grid cell, own back gesture with the live grid behind.
  - **The viewer is a layer, not a screen** (see "Where we are"): every `topLevel` destination hosts a
    `ViewerHost` + `ViewerOverlay` drawn over its own grid, so the grid stays composed. `ViewerRoute` remains a nav
    destination only for external intents. `ViewerViewModel` is assisted-injected with its route
    (`rememberViewerViewModelFactory`), its store is `ViewerSessions` (entry-scoped, cleared when a visit closes).
    Grids follow the viewer's photo with `FollowViewerPhoto` (scroll it into view underneath).
  - Chrome (`ViewerChrome.kt`): two-line date top bar (Today / Yesterday / date over time) with a dark tall fade and a
    ⋮ menu (Use as, Open with: read-only hand-offs); bottom bar: Share circle, Favourite / Edit / Trash pill, Details
    circle (Trash viewer: Restore / Delete forever). Play pill above the filmstrip for motion photos (clip plays once)
    and GIFs (still first frame until Play: `BitmapFactoryDecoder` + separate memory-cache keys).
  - Filmstrip (`ViewerFilmstrip.kt`): square thumbnails; swiping it changes the photo live, snaps to centre.
  - Details panel (`InfoPanel.kt`): big date, file name, location; camera, "MP | size | bytes | type", ISO / shutter /
    aperture / focal box (`ExposureValues`); album row (opens the album) and Favourites row (opens Favourites).
  - Video (Media3: scrubber, mute, keep-screen-on, resume saved across launches), Ultra HDR window mode.
- **Selection** (`feature/selection/`): long-press + drag-select with edge auto-scroll; Share, Favourite, Trash,
  More (Move / Copy to album, Lock), Copy to clipboard with one item; ends at zero selected.
  Secondary grids (album, Favourites, Videos, Trash) use `MediaListScreen` /
  `SelectableMediaGrid`.
- **Albums, Favourites, Videos, Trash** (restore / delete forever with confirmation). UI text spells "Favourite"
  (British); code identifiers keep `favorite`.
- **External intents:** `ACTION_VIEW` + camera `REVIEW` open straight into the viewer, works without media permission.
- **Pinch density:** `GridZoom.kt` / `PinchToZoom.kt` / `GridZoomAnchor.kt`. Pictures 2/4/6 + month; album lists
  share one size; Trash a flat 2/4/6 grid. All remembered across launches (`GridSizeKey` in `AppPreferences`).
- **Albums tab:** + New album (`NewAlbumScreen`, `NewAlbumRoute(name, bucketId?)` also used by the + inside an album),
  Search, ⋮ (sort, Edit albums); selection: Pin, Lock, Delete (`AlbumDelete.kt`, to Trash). Push past the last album and hold 1.2 s to open Locked.
- **Album organisation:** Room `gallery.db` (`AlbumPrefEntity`: hidden, pinnedAt, position; schema in `app/schemas/`),
  DataStore `AppPreferences` (album sort, albums per row, oldest-first). Edit albums screen (pin, reorder).
  Hidden albums were removed (the `hidden` column stays in Room, unused).
- **Search:** `feature/search/` (`parseSearch` / `SearchIndex` / suggestions, recent searches in DataStore).
- **Settings** (`feature/settings/`): theme, wallpaper colours, start muted, motion autoplay, Ultra HDR, skip
  confirmation pop-ups (`MANAGE_MEDIA` special access), Locked on / off + Open Locked, About. `AppSettings` read before the first frame.

### Known gaps / deferred
- Camera thumbnail opens Google Photos, not Gallery (parked by me; likely the system default app setting).
- `ACCESS_MEDIA_LOCATION` first-run prompt untested on device (location shows on my phone).
- The very first "open album" tap from an externally opened photo once landed on Pictures; not reproducible.
- **Phase 4:** request 120 Hz (`preferredDisplayModeId`; app renders at 90 Hz), run `assembleRelease` / benchmark
  builds on the phone (R8 risk; release should be 2-3x smoother than debug), Baseline Profile, accessibility
  `contentDescription`s, collapsing title header, real nav icons (current ones are `material-icons-core`).
- Not added yet (add in the phase that needs them): WorkManager, Turbine.

### How I like to work
- When I report something vague ("it glitches", "it vanishes"), first say back what you understood and ask what
  you need; change code only after I confirm. Clear requests can go straight to work.
- For anything about speed or feel, measure on the phone (for motion: 10x animator scale + frame contact sheet) and
  show me what you found before changing it.
- Say what each change means for me in plain words; keep reports short. Commit after each working change; push only
  when I say so.
- I give UI references as screenshots from other phones: follow the layout, implement it our own way.

### Testing on my phone (rules)
- Only test with copies, never touch originals (no restore / delete / move of real media without my approval).
- Keep Do Not Disturb on, take a screenshot before every tap, never send blind tap chains (a stray tap once
  started a real call). If the phone is locked or dozing, stop and ask me; never unlock it yourself.
- I may be using the phone at the same time: if logs show actions you did not send, stop tapping.
- Phone settings I chose: Developer options animation scales 0.7, Stay awake on (while charging), DND on.
  If you change animation scale for a test, put it back to 0.7 afterwards.
- One tap per adb command, with a screenshot before the next (chained taps get blocked). A single drag / gesture
  can be sent as one `input motionevent DOWN / MOVE... / UP` sequence. If a call or notification shows up, stop.
- adb taps are too slow for double-tap gestures: ask me to test those (and anything with two fingers).

### Running on my phone (Motorola Edge 60 Pro, Android 17, 1220x2712, wireless debugging)
- adb: `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"`. Check with `adb devices -l`; the serial looks like
  `adb-ZA223D32VK-xxxxxx._adb-tls-connect._tcp`.
- If it is not listed: `adb mdns services` shows the connect address (`adb connect <ip:port>`). If pairing is lost,
  ask me for a fresh pairing code and run `adb pair <ip:port> <code>`.
- Install and launch: `ANDROID_SERIAL=<serial> ./gradlew.bat installDebug`, then
  `adb -s <serial> shell am start -n com.rohit.gallery/.MainActivity`.
- In Git Bash, prefix adb commands that take device paths with `MSYS_NO_PATHCONV=1`.
- **Animation analysis:** set `animator_duration_scale` to 10, loop `screencap <file>.raw` (raw, ~0.23 s/frame;
  `-p` PNG takes ~1.3 s) into `/data/local/tmp` (not `/sdcard`, which would add files to the gallery), pull, and
  stitch a contact sheet with a small PowerShell + inline C# script (`System.Drawing`). Restore the scale after.
- **Timing:** temporary `Log.d` lines read with `adb logcat -d -s <TAG>:D`; remove them before committing.
- **Frame timing (jank):** `adb shell dumpsys gfxinfo com.rohit.gallery reset`, I do the gesture, then
  `dumpsys gfxinfo com.rohit.gallery framestats`; per frame use `FrameCompleted - IntendedVsync` and the gap between
  `IntendedVsync`s (read columns by header name). A 10x recording hides stalls (0.3 s is tiny next to a 3.8 s
  animation), so check feel problems at the real 0.7 scale with framestats first.
- I can drive the viewer myself for a recording when I say so: one `input tap` to open a photo (view only), then
  one `input motionevent DOWN/MOVE.../UP` swipe while an on-device `screencap` loop runs in the background.

### Gotchas learned (do not re-learn these)
- **JAVA_HOME** may be unset in the shell: `export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`
  before `gradlew.bat`. Write long build output to a scratchpad log and grep it.
- **Pinch:** `calculateCentroidSize` is 0 on the frame the second finger lands; take the first non-zero span as the
  baseline. Never read `layoutInfo` in composition (recomposes every scroll frame); use `snapshotFlow`.
  Disable `animateItem` placement on the frame the column count changes.
- **Kotlin 2.2.10 compiler cannot read Kotlin 2.4 metadata.** Latest coroutines (1.11), kotlinx-serialization
  (1.13) and Coil (3.5+) fail. Pinned: coroutines 1.10.2, serialization 1.9.0, Coil 3.4.0, Telephoto 0.19.0
  (`me.saket.telephoto.zoomable`), Media3 1.11.1. Check a library's POM `kotlin-stdlib` before bumping.
- Library internals: no sources jars in the Gradle cache; unzip the aar's `classes.jar` and read it with `javap -c -p`.
- `hiltViewModel` lives in `androidx.hilt.lifecycle.viewmodel.compose`. Use `@param:ApplicationContext`.
- There is no root `Surface`: `MainActivity` provides `LocalContentColor = onBackground`, otherwise text and icons
  default to black in dark mode.
- Sticky headers ignore `contentPadding`: inset the list with `windowInsetsPadding` instead.
- One-shot UI commands (like a fast-scroll drag target) go through a conflated `Channel`, not state: state is
  re-applied whenever its effect restarts (that caused the "grid jumps back" glitch).
- Shared elements: only the viewer's photo cell and the viewer's current page carry the shared modifier
  (`sharedElementWithCallerManagedVisibility`, visibility = viewer showing or not). Drag-dismiss moves the flight
  frame by *layout* (`FlightPlacement`), because the flight starts from layout bounds and ignores graphics-layer
  transforms. **The grid cell must not stay matched while the viewer is idle** (`viewerSettled`): with an active
  match the library keeps the bounds from the end of the open flight and ignores the drag, so the close flight
  jumped back to full screen. Re-attaching the cell at close makes a fresh match from the release point.
- Rebuilding a grid destination costs ~170-235 ms in a debug build (all three Home pages + LazyColumn subcompose);
  any animation started in that frame loses that much of its time. Keep grids composed instead of rebuilding them.
- Coil memory-cache hits for the viewer need the same request Telephoto builds: page size, `Precision.INEXACT`,
  `maxBitmapSize(Size.ORIGINAL)`.
- Don't run `python`/`python3` in the shell (hangs). `.gitignore` must use `build/` (not `/build`).
  Heredocs with backslashes lose them: use raw strings or the Write tool. In PowerShell, commit with `-m`
  arguments, not heredocs (a heredoc commit silently did nothing).
- Git commits use `-c user.name=rohit -c user.email=rohit@localhost`.
- Some sources and CLAUDE.md have CRLF line endings: strip `\r` before multi-line perl edits
  (`perl -pi -e 's/\r$//' file`).
  In perl `s{}{}` replacements with Kotlin braces break: use the Edit tool or `s///` instead.
- Coil memory-cache keys ignore the decoder: give a request with a custom `decoderFactory` its own `memoryCacheKey`.

- `draggable(startDragImmediately = ...)` is read at composition: pass *state*, never a value computed from a Job
  (a stale `true` made every tap inside the album strip start a drag).
- Inside pagers, don't combine two `boundsInWindow` / `positionInWindow` readings taken in different callbacks: they
  go stale relative to each other while pages slide. Measure live with `LayoutCoordinates.localPositionOf`.
- Pinch detectors must ignore 3+ fingers and wait ~100 ms with two before starting (three-finger screenshot swipe).
- adb `input text` needs `%s` for spaces.
