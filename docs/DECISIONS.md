# Decisions log

Non-obvious technical choices, newest last.

## Phase 0

- **AGP 9.5.1 built-in Kotlin.** No separate `kotlin-android` plugin; only the Compose,
  serialization, KSP and Hilt plugins are applied. Gradle wrapper 9.6.0.
- **Kotlin stays on 2.2.10.** Latest kotlinx-coroutines (1.11) and kotlinx-serialization (1.13)
  ship Kotlin 2.4 metadata and fail to compile here. Pinned to coroutines 1.10.2 and
  serialization 1.9.0. Revisit when AGP/Kotlin are upgraded together.
- **In-memory media index instead of Paging 3.** Fast-scroll, date headers and pinch-to-change
  density need the whole timeline up front; a lightweight index of 100k items is a few MB.
- **Deferred dependencies.** Room, DataStore, WorkManager, Coil, Telephoto and Media3 are added
  in the phase that first uses them (rule 7).
- **`benchmark` build type** inherits from `release` (R8 + resource shrinking), is debug-signed
  and not debuggable, so Macrobenchmark can run against it in Phase 4.
- **Nav icons** use `material-icons-core` placeholders (DateRange, List, Menu); custom icons later.
- **`INTERNET` is never declared** (hard rule 2). Verify the merged manifest after adding libraries.

## Phase 1

- **Timeline is a `LazyColumn` of rows, not a `LazyVerticalGrid`.** The grid has no sticky headers,
  and the brief wants sticky day headers. Each row holds `columns` items; days are `stickyHeader` +
  `items(rows)`. Changing density later means re-chunking rows (done in `TimelineViewModel`).
- **Coil pinned to 3.4.0.** 3.5+ ships Kotlin 2.4 metadata, which the Kotlin 2.2.10 compiler rejects.
  Bump Coil together with Kotlin/AGP.
- **Grid thumbnails do NOT use Coil.** They use `ThumbnailCache` (own `LruCache<ThumbKey, Bitmap>`,
  96 MB) fed by `ContentResolver.loadThumbnail` (MediaStore's thumbnail cache). Reasons: much cheaper
  than decoding originals, and a synchronous read at composition time means a cached cell draws in
  its first frame (Coil resolves asynchronously and also rejected cached thumbnails smaller than the
  cell, so scrolling back up reloaded everything). Coil stays for the viewer (Phase 2).
- **`loadThumbnail` box is sized by aspect ratio** so the short side covers the square cell
  (capped at 2.5x) instead of leaving a letterboxed, upscaled thumbnail.
- **Two loader lanes** (visible: 4 threads, prefetch: 2) so queued prefetch can never delay on-screen
  cells. Prefetch (10 rows ahead in scroll direction) is cancelled when the visible range changes.
- **`MediaItem` stores no `Uri`.** The content URI is derived from `id` + type (`mediaContentUri`),
  keeping 50k items small and the model free of framework objects for unit tests.
- **Index is queried from `MediaStore.Files`** (images + videos in one query), sorted in memory by
  `sortDateMs` (date taken, else date modified) then id, newest first. Trashed items are excluded by
  MediaStore's default; the index has no trashed field until Phase 2 adds the Trash screen.
- **Refresh policy:** `MediaRepository.refresh()` is immediate; `ContentObserver` changes are debounced
  (500 ms). A failed first query (e.g. before permission) yields an empty index instead of loading
  forever; a failed later query keeps the last good index.
- **Permission state:** Full = images+video granted; Partial = any media permission incl.
  `READ_MEDIA_VISUAL_USER_SELECTED`; DeniedPermanently is detected after a failed request when
  `shouldShowRequestPermissionRationale` is false (kept in `rememberSaveable`, not persisted).
- **Verified:** debug, release (R8 + resource shrinking) and benchmark builds pass; no `INTERNET`
  permission in any merged manifest. Release/benchmark APKs have NOT been run on the device yet.

## Phase 2

- **Viewer is a nav destination** (`ViewerRoute(mediaId, bucketId?)`) with its own `ViewerViewModel`
  reading the in-memory index; `bucketId` will scope the pager to an album later.
- **Telephoto 0.19.0** (`zoomable-image-coil3`) compiles against Kotlin 2.1.21, so it is safe with our
  2.2.10 compiler. `androidx.exifinterface` pinned to stable 1.4.2 (1.5.0 is beta).
- **Shared element only on one grid cell.** `LocalSharedMediaId` tags the tapped cell (and, while in the
  viewer, the current page) so the other ~40 visible cells do not carry the modifier. Navigation starts
  one frame after tagging so both ends exist when the transition begins.
- **No Scaffold for the nav host.** Top-level screens get a constant bottom padding (80dp + nav inset)
  and the bottom bar is an overlay hidden on the viewer, so the grid does not resize while the viewer opens.
- **Drag gestures:** one vertical `draggable` over the pager: down = dismiss (distance or fling),
  up = info sheet. Telephoto consumes pan gestures when zoomed, so this only triggers when not zoomed.
- **Info sheet** loads details (file name, folder, EXIF, GPS) on IO via `MediaInfoLoader`. GPS needs
  `ACCESS_MEDIA_LOCATION` (requested once, the first time the sheet opens) plus `MediaStore.setRequireOriginal`.
- **Known gaps:** video pages show a still frame until Media3 lands (task 2); closing the viewer does not
  scroll the grid to the photo you ended on; shared element morphs square to fit (no crop-matching yet).

## Phase 2: video playback

- **Media3 1.11.1** (`exoplayer` + `ui-compose`): its POM uses kotlin-stdlib 2.2.10, matching our compiler.
  Media3 1.12 is still beta. No `media3-ui` (Views) and no custom-controls library; controls are our own.
- **One ExoPlayer, for the current page only.** Created when a video becomes the current page, released when
  the page changes or the viewer closes (`rememberPlayback`). Other video pages show the cached thumbnail with
  a play badge. Autoplays; pauses on `ON_STOP`; `keepScreenOn` only while playing; audio focus handled by ExoPlayer.
- **TextureView, not SurfaceView**, so the drag-to-dismiss scale / corner rounding / fade also apply to video.
- **Resume position is in memory only** (`PlaybackPositionStore`, singleton). It is dropped when the app process
  dies. Persisting it needs DataStore (Phase 3). Positions under 3 s or within 2 s of the end are not resumed.
- **Mute is per viewer session** (`rememberSaveable`), not per video.
- **Player icons** (pause, volume) are vector paths in `PlayerIcons`, to avoid `material-icons-extended`.

## Phase 2: multi-select (4a)

- **One gesture detector for the grid** (`gridGestures`): tap, long-press, drag-after-long-press, with the cell
  found by hit-testing the visible rows (`rowsByFirstId` + column from x). Before the long-press it only watches
  (Main pass, so the list can claim scrolls); after it, it takes the events on the Initial pass so the list does
  not scroll during drag-select. Cells no longer have their own `clickable`.
- **`SelectionState` holds only ids** plus the drag anchor/base. Dragging adds the range anchor..finger; if the
  drag started on an already-selected item it removes the range instead. Selection mode stays on at 0 selected.
- **Auto-scroll while dragging** near the top/bottom edge; the pointer is kept in a plain holder, not state, so
  finger movement never recomposes the grid.
- **Nav bar is replaced by the selection action bar.** Top-level screens now receive `contentBottomPadding` and
  use it as the list's content padding, so the action bar can sit at the true screen bottom.
- **Writes are system-confirmed and recoverable.** Trash = `createTrashRequest` (restorable), favorite =
  `createFavoriteRequest`. We only clear the selection and refresh when the system dialog returns RESULT_OK.
  Very large selections might exceed binder limits for the request; failure shows a toast and changes nothing.
- **Share** uses a chooser with read-only URI grants; mime is exact for one item, `image/*` / `video/*` if uniform.

## Phase 2: viewer gestures and flight (later decisions)

- **Details panel is stacked, not an overlay.** It takes `InfoPanelFraction` (46%) of the height; the pager
  shrinks upward via `layout{}`. The panel has its own scroll and gesture, as a sibling of the photo area.
- **`SwipeMode`** (None / OpenInfo / CloseInfo / Dismiss / Ignore) is fixed when a swipe starts, so one swipe
  never both closes the panel and dismisses the viewer. Swipe-up opens details directly with no image drag.
- **Photo-shaped shared element** (`SharedFlightFrame`): the flight element has the photo's aspect ratio, so the
  grid cell morphs to the true outline. Using the full-screen box stretched the cropped thumbnail across the
  screen for a moment. It is only visible during transitions; the interactive layer is hidden meanwhile.

## Phase 2: move/copy, trash, favorites, albums
- **Move** = `createWriteRequest` then `ContentResolver.update(RELATIVE_PATH)`. **Copy** = insert a new row
  (IS_PENDING=1), stream-copy, IS_PENDING=0; a failed copy deletes the half-written file. Originals are never
  modified. Videos headed for `Pictures/...` fall back to `Movies/...`. New album = `Pictures/<name>/`.
- **Trashed items** are queried with `QUERY_ARG_MATCH_TRASHED`; their `MediaItem.uri` carries `includeTrashed=1`
  (`MediaStore.setIncludeTrashed` does not resolve here), otherwise thumbnails and full-size loads fail.
- **Permanent delete** only from the Trash screen: in-app confirmation plus the system `createDeleteRequest` dialog.
- **Shared selection code:** `SelectableMediaGrid` (tap / long-press / drag, auto-scroll) and `SelectionActions` +
  `SelectionActionsBar` (owned by each screen's ViewModel) serve Pictures and album detail. Favorites and Trash
  still wire their own simple actions and could move onto them later.
- **Albums** are derived from the in-memory index (`buildAlbums`: by bucket, ordered by newest item, newest item
  is the cover); no Room needed until custom order / hidden albums (Phase 3).
- **Viewer scope** is set by route args: `bucketId`, `trashed`, `favoritesOnly`.

## Phase 2: external intents
- **Same activity, viewer as start destination.** `MainActivity` turns `ACTION_VIEW`, `com.android.camera.action.REVIEW`
  and `android.provider.action.REVIEW` (image/*, video/*, content/file) into a `ViewerRoute(externalUri, externalMime)`
  and the nav host starts there, so no permission gate or timeline is composed. Default `launchMode`: the viewer runs in
  the caller's task; Back / drag-dismiss call `finish()`. `onNewIntent` re-keys the nav host for a new file.
  `REVIEW_SECURE` (lock-screen camera) is not handled.
- **Decided once per open:** a MediaStore URI (`mediaStoreIdOf`) whose id appears in the index (refresh + wait up to 2 s)
  opens the normal timeline pager at that item; anything else is shown alone as a synthetic `MediaItem` with
  `externalUri`, using only the intent's read grant (works without media permission). Unreadable URI: toast and close.
- **Synthetic id** = negative hash of the URI, so thumbnail cache and resume positions never mix two external files.
- Info panel uses `OpenableColumns` and skips `setRequireOriginal` for non-MediaStore URIs; unknown date/size are hidden.

## Phase 2: fast-scroller
- **Exact geometry, no measuring off-screen items.** `TimelineScrollMap` (pure, unit-tested) knows each day is a header
  plus equal-height rows, so offset <-> (lazy index, offset) <-> day is computed with binary searches. Header and row
  heights are measured from visible items (font scale, cell size); the map rebuilds when they change.
- **Drags are conflated:** the handle sets a target fraction; one `snapshotFlow` collector calls `scrollToItem`, so a fast
  drag never queues scroll calls. The thumb follows the finger while dragging and the list position otherwise.
- `FastScroller` (core/ui/components) is generic: fraction in, fraction out, label lambda. Shown while scrolling, hidden
  1.5 s after; only on timelines longer than 4 screens. Bubble shows "Month Year"; haptic tick when the month changes.
- Only the Pictures timeline has it for now; album/favorites grids are flat `LazyVerticalGrid`s and could reuse it.

## Phase 2: Videos
- **Menu > Videos** = index filtered to `isVideo`; viewer scope via `ViewerRoute.videosOnly`.
- **`MediaListScreen`** (feature/selection) is the shared title bar + `SelectableMediaGrid` + `SelectionActionsBar` scaffold;
  album detail and Videos use it. Favorites can move onto it (it still wires its own actions).

## Phase 2: closing fixes
- **Timeline is inset by the status bar** (`windowInsetsPadding`) instead of top content padding: sticky headers pin
  to the viewport top and ignore content padding, so the pinned day header sat under the clock. Content no longer
  scrolls behind the status bar.
- **Return to the photo you ended on:** the viewer writes its current item into `ViewerReturnTarget` (a plain holder
  provided by the nav host); the grid that reappears takes it once in its first composition and calls
  `requestScrollToItem` if the item is not fully visible, so the first frame already has the cell for the close flight.
  Timeline uses exact geometry (`TimelineScrollMap`, `lazyRowIndexOf`); flat grids use a row estimate.
- **Zoomed close:** Back / back gesture / top-bar arrow call `resetZoom` (220 ms) then pop. Drag-dismiss cannot start
  while zoomed (Telephoto consumes the pan), so it needs no change.
- **Every close flies into the cell.** Drag-dismiss used to switch the flight off and fade (the photo just vanished).
  Now it keeps the flight: `SharedFlightFrame` takes the drag as layout size/offset (`FlightPlacement`), since the
  flight starts from layout bounds, so it leaves from where the finger let go.
- **Flight timing matched to Google Photos** by capturing both on the phone at 10x `animator_duration_scale`
  (raw `screencap` loop into `/data/local/tmp`, ~0.23 s per frame, stitched into a contact sheet). Google Photos
  flies in ~7 frames (~170 ms). Ours is now `tween(180, FastOutSlowInEasing)`, open and close alike. A stiff
  spring (1200) read as vanishing; a soft one (450) crept slowly into the cell at the end.
- **All secondary grids share `MediaListScreen`.** Favorites now has Move/Copy too; Trash passes its own Restore /
  Delete bar through a slot.
- **Viewer opens sharp sooner** (measured with temporary logs on the phone, debug build). Telephoto's Coil resolver
  copies our request and asks Coil for the image at the page size with `Precision.INEXACT` and
  `maxBitmapSize(Size.ORIGINAL)`, honouring the memory cache (checked by `javap` on the 0.19.0 aar). So
  `prefetchViewerImage` enqueues that same request (window size) at tap time in `openViewer`, while the viewer is
  built and the flight runs; the page then gets a `MEMORY_CACHE` hit. The unfiltered timeline is also handed to the
  pager as the `stateIn` initial value, so its pages exist on the first frame instead of after a background hop.
  Tap to sharp photo went from ~420-600 ms to ~220-350 ms. Remaining: viewer setup (~140-190 ms, debug build) and
  Telephoto's sub-sampling checks (~60-80 ms).
- **Fast-scroll targets go through a conflated `Channel`** (`cec7e0c`). The drag target used to be state read by a
  `LaunchedEffect(map)`; every library change rebuilt `TimelineScrollMap`, restarted the effect and re-applied the
  last target, so the grid jumped back to the old handle position and seemed frozen. A `Channel(CONFLATED)` collected
  in `LaunchedEffect(listState)` (map via `rememberUpdatedState`) delivers each target once. `gridGestures` also ends
  a drag in `finally`, so a cancelled long-press drag cannot leave edge auto-scroll running.
- **Default content colour** (`2b78276`): there is no root `Surface`, so `LocalContentColor` defaulted to black and
  plain `Text` / `Icon` (Menu screen) were black on the dark background. `MainActivity` now provides
  `LocalContentColor = colorScheme.onBackground` around the nav host.

## Phase 3: pinch grid density
- **Levels:** 2 / 4 / 6 columns and a month view (8 columns, one section per month), chosen by me over the
  brief's 3 / 4 / 7. Default stays 4. `GridLevel` enum; held in memory in `TimelineViewModel` until DataStore lands.
- **Group once, chunk per level:** the library is grouped into days and months once per index change
  (`GroupedLibrary`); switching density only re-chunks rows, so the switch does not redo date math for 50k items.
  A month section is a `TimelineDay` keyed by the month's first day, so `TimelineScrollMap`, prefetch and the
  fast-scroller work unchanged.
- **Gesture:** `pinchToZoom` sits on a Box around the list (outside the scaled layer, so finger positions don't
  move with the scale) and consumes events in the initial pass once two fingers are down, so the list neither
  scrolls nor sees a tap/long press. The grid follows the fingers through a `graphicsLayer` scale (no
  recomposition). On release, past 1.15x / 0.87x it switches level; otherwise it springs back.
- **Anchor:** the cell under the fingers (or the nearest row's) is kept where the fingers were. In the first
  composition of the new level `KeepZoomAnchor` calls `requestScrollToItem` from exact geometry, snaps the scale
  to how big the cells looked at release (`pinchScale x newColumns / oldColumns`) and springs it to 1.
- **Sharper thumbnails on zoom-in:** `ThumbnailCache` remembers the cell size each bitmap was decoded for.
  A smaller one is shown at once as a placeholder and reloaded at the new size (`getSharp`, 0.9 tolerance).
