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
  drag started on an already-selected item it removes the range instead. Selection mode ends at 0 selected (changed
  2026-11-19 at my request; it used to stay on, One UI style), but only once a drag has ended.
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
  recomposition). It switches **while the fingers move**, each time the pinch passes 1.1x / 0.9x since the last
  switch (tuned on the phone: one big swipe should go 2 -> month). Movement during a switch still counts towards
  the next, so a fast pinch chains switches. On release without a switch it springs back.
- **Switch animation:** the grid draws through a recorded `GraphicsLayer`; at a switch it is snapshotted
  (`toImageBitmap`) and laid over the new layout. Both scale together (220 ms) so the anchor cell stays one cell,
  while the snapshot fades out. The first try (new grid only, springing from a smaller size) showed empty edges.
- **Anchor:** the cell under the fingers (or the nearest row's) is kept where the fingers were.
- **Sharper thumbnails on zoom-in:** `ThumbnailCache` remembers the cell size each bitmap was decoded for.
  A smaller one is shown at once as a placeholder and reloaded at the new size (`getSharp`, 0.9 tolerance).
- **Pinch baseline bug:** `calculateCentroidSize` is 0 on the frame the second finger lands (it only counts
  fingers already down), so the starting distance is the first non-zero span.
- **Timeline background** is painted with `colorScheme.background` (the window behind is a different shade, which
  showed in the empty end of short rows). Video badges in cells under 64 dp show only the play icon.
- **Album grids too:** the pinch engine moved to `core/ui/components` (`GridZoomState`, `pinchToZoom`, `zoomLayer`,
  `ZoomOverlay`) and only knows column counts. `SelectableMediaGrid` (album, Favorites, Videos, Trash) pinches
  through 2 / 4 / 6 columns (no month view: no date sections there); `KeepFlatGridAnchor` scrolls with exact
  row geometry. Column count is per screen (`rememberSaveable`) until DataStore lands.
- **Albums, Favorites and Videos use the timeline grid** (my request: dates and months in albums). `TimelineGrid` is
  public with `underStatusBar = false` for screens under a title bar; grouping moved to `groupLibrary` /
  `toUiState` (TimelineGrouping.kt) so the Pictures VM and `MediaListScreen` share it. `MediaListScreen` groups
  with `produceState` on `Dispatchers.Default` (blank for a few ms rather than a spinner flash). Trash keeps the
  flat `SelectableMediaGrid` (ordered by delete date), which still pinches 2 / 4 / 6.

## Phase 3: album organisation
- **Storage:** Room 2.8.5 (`gallery.db`, table `album_prefs`: hidden, pinnedAt, position per bucket id; schema
  exported to `app/schemas`) and DataStore 1.3.0 (`settings`: album sort, oldest-first). Both built with Kotlin
  <= 2.2, so readable by our 2.2.10 compiler. Only app-owned data: folders and files are never touched.
- **Hidden means hidden everywhere** (my choice): `MediaRepository.state` is the index minus hidden buckets, so
  Pictures, Favorites, Videos and the viewer all skip them with no per-screen code. `allState` keeps everything
  for album management and the Move/Copy picker. `state` waits for the hidden list before its first Ready, so
  hidden photos never flash on start. `HiddenAlbums` is an interface so the repository test needs no database.
- **Pins** (my request): any album can be pinned; pinned albums lead in pin time order, so a new pin goes after
  earlier ones. The rest follow the sort: Newest photo first (default), Name A-Z, Most items, Custom.
- **Custom order:** dragging in Edit albums saves positions for all unpinned albums and switches the sort to
  Custom. Albums never placed (new folders) lead the custom order. Pure ordering lives in `organizeAlbums`
  (unit-tested).
- **UI:** Albums tab menu (sort, Edit albums), long-press a card to pin/unpin or hide. Edit albums lists every
  album (hidden ones dimmed) with pin / eye toggles and drag handles. Icons are drawn in `OrganizeIcons`
  (material-icons-core has no pin/eye/drag icons).
- **Bulk pin / hide** (my request): long-press selects albums in the Albums tab and in Edit albums (replacing
  the earlier long-press menu). The bar's buttons reverse when they already apply to every selected album
  (Pin -> Unpin, Hide -> Show), so hidden albums can be shown in bulk from Edit albums. Albums pinned together
  get `now + index` in display order, so they keep that order after earlier pins. Writes are one `upsertAll`.
- **Pinch on the Albums tab:** 2 / 3 / 4 albums per row (my choice), saved in DataStore (`album_columns`).
  The anchor maths is now shared (`KeepGridZoomAnchor` + `GridGeometry`: padding, leading items, row pitch),
  because album rows are taller than photo rows (the name and count under each card are measured). Card
  placement animation is off for the one frame where the column count changes, so cards don't slide under
  the cross-fade.

### Album order: newest or oldest first (Phase 3)
- One setting for every album (`AppPreferences.albumOldestFirst`), changed from the ⋮ menu on an album's title
  bar. Favorites, Videos and Pictures stay newest first.
- The album's list waits for both the index and the setting before showing anything, so it never shows newest
  first and then flips.
- The viewer gets `oldestFirst` in `ViewerRoute` and reverses the same filtered list, so swiping goes in the
  order the album shows. Day and month grouping keep whatever order they are given.

### Search (Phase 3)
- **In memory, no database.** The index already holds every item, so a search is one loop over it on
  `Dispatchers.Default` (`mapLatest`: a new keystroke cancels the old search). `SearchIndex` works out each item's
  month and year once per library change, so a query never converts dates per item. No Room FTS table: it would
  have to be kept in step with MediaStore for no speed gain at this size.
- **File names:** `DISPLAY_NAME` was added to the one startup query (`MediaItem.displayName`).
- **Query words are ANDed.** Each word is read as a year ("2024"), a month name or its start ("mar"; also matches
  album names, so "may" finds "May trip"), a kind (videos, photos, screenshots, gifs, favourites), a relative date
  (today, yesterday, this/last week/month/year; the week starts on the locale's first day), or else text looked
  for in album and file names. Year-like words are never matched against file names: camera names carry the time
  (`..._2024...` at 20:24), which would bring in other years. A "quoted phrase" is searched whole; album
  suggestions use quotes so "WhatsApp Images" is not read as "WhatsApp" + photos.
- **Screenshots** = album name contains "screenshot" or file name starts with it.
- **Viewer:** `ViewerRoute.searchQuery` re-runs the same search on the viewer's side, so the pager swipes through the
  matches only and survives process death without passing a long id list through navigation.
- **Recent searches** (8, newest first) live in DataStore, saved when the user presses Search on the keyboard or
  opens a match (not on every keystroke).
- The Pictures search button sits at the right end of the pinned day header, which is otherwise empty there.

### Back gesture animations (Phase 3)
- Navigation Compose 2.10 previews the system back gesture with its own `predictivePopExitTransition`
  (a `scaleOut` "card"). It did not suit this app: screens without a background showed the screen beneath
  through them, and the viewer's photo (drawn in the shared-element overlay) did not follow the card.
- Now: every bottom-bar / secondary destination draws an opaque background (in `topLevel`), and the NavHost's
  predictive pop slides the leaving screen out towards the side the swipe started from while the screen beneath
  stays still (`predictivePopEnterTransition = None`).
- The viewer handles the gesture itself with `PredictiveBackHandler` (inner handlers win over the NavHost's): the
  photo shrinks to about 60 % (drag-down progress 0.9, so the background mostly fades and corners round) and
  leans 24 dp towards the swiped edge (mostly a centred shrink), eased out so a short swipe already shows; chrome fades, and
  releasing closes through the normal path, so the shared-element flight starts from where the photo is.
  Cancelling springs it back. Zoomed in or with details open, back keeps its old behaviour (zoom out / close
  details).
- **Grid behind the photo:** the grid is not composed while the viewer is open, so `GalleryNavHost` records its
  content into a `GraphicsLayer` and, when a photo is opened, keeps `toImageBitmap()` of it (`ViewerBackdrop`,
  cleared when the viewer is gone). The viewer draws it under its black background only while a back or
  drag-down gesture is fading the black. Grids register a locator (`RegisterViewerCells`) so the capture also
  records where each visible photo sits; the viewer draws the current photo's cell as an empty gap, which the
  close flight then fills. It is a picture from when the viewer opened, so after paging far it may not show the
  current photo's cell (then there is no gap).
- **Rounded corners while shrinking** follow the photo itself (`FittedRoundedShape`: the fitted, centred photo
  rect inside the page), not the full-screen page, so letterboxed photos visibly round.

### GIF, motion photos, Ultra HDR (Phase 3)
- **Detection** (`MediaTraitsReader`): MediaStore indexes none of this, so the viewer reads the first 256 KB of the
  current JPEG on IO and looks for `hdrgm:Version` (Ultra HDR gain map) and `MotionPhoto="1"` / `MicroVideo="1"`
  in the XMP; cached per item. Only for the photo on screen, never for grid cells (too many files), so the grid
  has no motion badge. GIFs are known from the mime type, so the grid gets a "GIF" badge.
- **Ultra HDR:** while such a photo is on screen the window is put in `COLOR_MODE_HDR` (back to default otherwise
  and when the viewer leaves). Verified on the phone: SurfaceFlinger reports a full-screen HDR layer
  (desired ratio 5.0). Motorola's `_HDR` / `_PCT` camera files are Ultra HDR JPEGs.
- **GIFs** animate through Coil's `AnimatedImageDecoder` (already registered); Telephoto shows them as is.
- **Motion photos:** a "Motion" pill plays the embedded clip once over the photo with Media3, which reads it
  straight from the JPEG (`JpegMotionPhotoExtractor`), so no extraction code or new library. The clip layer gets
  the same drag / back-gesture transform as the photo.
- Test samples live in `Pictures/Gallery test` on the phone (a generated GIF, a generated motion photo whose clip
  is a short screen recording of that GIF, and a copy of an Ultra HDR camera photo). Kept until I say otherwise.

### Settings (Phase 3)
- One DataStore file (`AppPreferences`): theme (system / light / dark), wallpaper colours, start videos muted, motion
  photo autoplay, Ultra HDR on/off, remembered grid sizes (Pictures level; one level shared by album / Favorites /
  Videos; Trash columns) and video resume positions (100 most recent, `id:ms` pairs).
- `MainActivity` draws nothing until settings are read (a few ms; cold start measured 478-491 ms), so the app never
  flashes the wrong theme; system bar icons follow the chosen theme via `enableEdgeToEdge(SystemBarStyle.auto)`.
- Grid sizes change in memory first (a pinch switches mid-gesture) and are written in the background; screens show
  nothing until the saved size is read rather than the default first.
- "Skip confirmation pop-ups" is the `MANAGE_MEDIA` special access (declared, off until the user grants it in the
  system screen). With it, MediaStore trash / favourite requests complete without the system dialog; permanent
  delete still has the app's own confirmation in Trash.
- The Settings screen UI was written by a helper agent in parallel (new files only), then reviewed and built here.

## Filmstrip scrubbing (2026-11-18)
Swiping the viewer filmstrip changes the photo live: whichever thumbnail is nearest the middle becomes the current page (`pagerState.scrollToPage`). Only user scrolls drive this (a drag, or the snap fling after it, tracked with a wrapping `FlingBehavior`), so our own animated scroll that follows the pager never selects the items it passes. `SnapPosition.Center` snapping leaves one thumbnail exactly centred.

## Viewer layout and Play pill (2026-11-18)
- Layout follows my reference screenshots (own implementation): two-line date top bar with a read-only ⋮ menu (Use as, Open with); bottom bar of Share circle, Favourite / Edit / Trash pill, Details circle. The bottom bar stays over the details panel and takes its colours, so the panel leaves `ActionBarHeight` of room.
- Details panel: big date, file name, location; camera, "MP | size | bytes | type"; ISO / shutter / aperture / focal box (`ExposureValues`); folder.
- GIFs stay still until Play: the still frame is a plain `BitmapFactoryDecoder` decode, the animation the normal animated decoder. Both get their own memory-cache keys, because Coil's key does not include the decoder and the still frame would otherwise stand in for the animation.
- Pause and Folder icons are drawn in `core/ui/icons/GalleryIcons.kt` instead of adding the large extended icon set.

## Tabs in one pager (2026-11-19)
Pictures, Albums and Menu are pages of one `HorizontalPager` inside a single `HomeRoute` (was three nav destinations), so a sideways swipe drags the next tab in with the finger. All three stay composed (`beyondViewportPageCount = 2`) so scroll positions survive; cold start was unchanged (460-470 ms). Snap: no-bounce spring, stiffness 900, 30% positional threshold; the same spec for bar taps and Back (Back from Albums / Menu slides to Pictures). The pager is still while selecting or when a second finger lands (pinch belongs to the grid). Dragging on the bottom bar drives the pager too (`dragsPager`).

## Viewer overlay (2026-11-19)
- **Problem:** swipe-down close "jumped" into the grid with a stutter; a slower glide (250 -> 380 ms) changed nothing. Frame timing (`dumpsys gfxinfo framestats`) showed why: popping the viewer route rebuilt the whole Home destination (three pager pages, the timeline's LazyColumn subcompose) in one frame, ~235 ms with only 3 frames drawn, which ate ~90% of the close flight. 10x recordings had hidden it.
- **Choice (mine, over "try a release build first" and "hold the photo through the freeze"):** keep the grid alive under the photo. Each `topLevel` destination draws its viewer as a layer (`ViewerOverlay`, state in `ViewerHost`, saved as JSON of `ViewerRoute`). Closing just removes the layer once the flight has landed. Measured after: no frame gap over ~33 ms after release.
- `ViewerRoute` stays a nav destination only for external intents (no grid there). In-app viewers no longer navigate; Back is handled by the viewer's own handlers plus a fallback `BackHandler` in the overlay.
- `ViewerViewModel` is `@AssistedInject` with its route (factory via a Hilt `EntryPoint`), stored in `ViewerSessions` (an entry-scoped ViewModel that is a `ViewModelStoreOwner`), keyed per visit and cleared when the visit closes. It survives a trip to an album from the details panel (the viewer is still open on return).
- Shared element: `sharedElementWithCallerManagedVisibility`, viewer side visible while showing, grid cell visible otherwise. The grid cell is unlinked once the open flight lands (`viewerSettled`) and re-linked at close, otherwise the close flight starts from the open flight's end bounds (full screen) instead of the release point.
- The stored "backdrop" bitmap of the grid (and recording the whole screen into a layer every frame) is gone: the live grid shows behind a drag-down, and the photo's cell is hidden (alpha 0) while the viewer shows it. `ViewerReturnTarget` is replaced by `FollowViewerPhoto`: grids scroll the viewer's photo into view underneath, 350 ms after paging settles, at once on close.
- Close flight: 380 ms, `CubicBezierEasing(0.2, 0, 0, 1)` (about 266 ms at my 0.7 animation scale).

## Fluid viewer gestures (2026-11-19, Apple "Designing Fluid Interfaces")
- **Springs in Apple's terms:** `GalleryMotion.fluid(response, dampingRatio)` with stiffness = (2π / response)².
  Open flight: critically damped, response 0.28 s. Close flight: response 0.4 s. Details panel 0.3 s; a photo
  springing back after a drag 0.35 s with damping 0.85 (a little give, since a gesture with momentum preceded it).
- **Velocity handoff into the close flight:** the shared-element library starts its bounds animation from rest and
  takes no initial velocity, so `VelocityHandoff` wraps the bounds spring and substitutes the finger's release velocity
  (`ViewerHost.closeVelocity`, set by `onSwipeClose`) when the library passes zero. A non-zero velocity (flight
  redirected mid-way) is kept. Capped at 6000 px/s.
- **Projection:** swipe-down closes when `dragY + project(v, 0.995)` passes 120 dp (0.995 rather than a scroll's
  0.998, so speed alone needs ~1700 px/s, close to the old 1800 threshold); flicking back up (> 150 dp/s) always keeps
  the viewer. The details panel opens/closes towards `progress + project(v)` (0.998) instead of a fixed velocity.
- **Interruptible:** the details panel follows the finger 1:1 (`infoDrag`) and is an `Animatable`, so it can be grabbed
  mid-animation; a photo springing back (`settle`) is cancelled when a new drag starts, continuing from where it is.
- Not done: grabbing the photo during the close flight itself (the viewer is already closing then).
- **No input lock while closing** (fix, 2026-11-19): the closing viewer layer used to swallow every touch until the
  flight had fully settled; with the spring's long, invisible tail that ate the next tap (felt like ~1 s before a new
  photo would open). Now the layer stops blocking as soon as it closes, and once its fade is done it is clipped to an
  empty shape so hit testing skips it entirely (the flying photo is drawn by the shared-transition overlay, so it is
  unaffected). A tap on the grid during a close ends that close at once (`finishClose`) and opens the new photo.
  Flights also count as landed within 1.5 px (`FlightThreshold`) instead of 0.5 px.
- **Rubber-banding** (item 4): `GalleryMotion.rubberBand` is Apple's curve (`o·d·c / (d + c·o)`, c = 0.55), capped at
  12 % of the screen height (`StretchFraction`). Used where the viewer used to stop dead: pulling the details panel
  past fully open (it grows taller, bottom fixed, then springs back; swiping up on the photo while it is open now does
  this too instead of nothing) and pulling a swipe-down photo back above its start. `rubberBandInverse` lets a drag
  resume from a stretched position without a jump. Grids, pagers and the filmstrip already had Android's stretch
  overscroll, and pinch past 2 columns / month view already resisted, so they were left as they are.
- **Press feedback on grid cells** (item 5): cells had none (one custom gesture detector, no `clickable`). Now
  `gridGestures` reports a press after the finger rests 70 ms (a scroll view's content-touch delay, so starting a
  scroll never flashes a cell); the cell then sinks towards 0.92 over the rest of the long-press wait (a hint that
  a long-press is coming) and springs back with a little give when the touch becomes a tap, scroll or long-press.
  `GridPressState` holds the pressed id and one `Animatable`; cells read it only inside `graphicsLayer`, so pressing
  never recomposes the grid. Buttons, menu rows and album cards keep Android's ripple (familiar platform feedback).
- **Haptics** (item 6; Apple's causality / harmony / utility rules): fired on the causal frame, only at meaningful
  moments, and through `LocalHapticFeedback`, so the system "touch feedback" setting still applies.
  New: swipe-down ticks (`GestureThresholdActivate`) when letting go would close the viewer, once per crossing;
  filmstrip scrubbing ticks (`SegmentFrequentTick`) per photo passed; viewer favourite gives `ToggleOn` / `ToggleOff`
  when MediaStore reports the change saved (not on the button press). Changed: pinch density switches use
  `SegmentTick` (a detent) and the fast-scroller's month change `SegmentFrequentTick`, instead of `TextHandleMove`.
  Not added: drag-select per item (too buzzy on fast drags), details panel open/close, trash.
- **Frosted bars** (item 7): Haze 1.7.2 (`dev.chrisbanes.haze:haze`), the last release built with Kotlin 2.2 and
  Compose 1.10 (1.7.3+ needs Kotlin 2.3+ metadata and Compose 1.12). It adds no permissions and leaves every
  androidx / Kotlin version unchanged (only Compose Multiplatform's redirect stubs move 1.9.3 -> 1.10.0). One material
  (`FrostedChrome.kt`): blur 28 dp, then `surfaceContainer` at 72 %, plus a hairline on the edge facing the content.
  Used by the bottom tab bar (source: the home pager) and every selection bar (Pictures top + bottom, album /
  Favourites / Videos / Trash bottom, Albums tab bottom); each screen marks its scrolling content `hazeSource` and
  provides its state as `LocalChromeHaze` only around the bars, so no bar is inside its own source. Title bars that
  sit above (not over) their content stay solid. Not done: the viewer's pills, the pinned day header.
- **Typography** (item 8): `GalleryTypography` keeps Material's sizes but sets tracking and leading per size, in em so
  they hold at any font scale: display -0.025..-0.02 em, headline -0.018..-0.012 em, titleLarge -0.008 em, with
  leading tightening as size grows (1.08x..1.25x); titleMedium 0, titleSmall 0.05 sp; body 0.15-0.3 sp (Material
  used 0.25-0.5); labels unchanged. Weights unchanged.
- **Reduced motion** (item 8): Android's form is the system "Remove animations" switch (animation scale 0), which
  Compose already applies to every animation, ours included (springs, flights, fades jump to their end). The only
  code that waits on an animation (the viewer layer waiting for its open / close flight) handles a flight that ends
  at once. No in-app "reduce motion" setting: Android has no separate reduce-motion signal, and overriding the
  system scale to keep cross-fades would go against what the user chose.
- **Floating tab bar** (2026-11-19, picked by the user over a refined flat bar and icons-only): `FloatingTabBar` is a
  full-round frosted capsule, 16 dp in from the sides and 12 dp above the bottom inset, 64 dp tall. The selected tab is
  a 48 dp pill (secondaryContainer) that opens to show its label; the others show icons only. Width changes animate
  with `animateContentSize` and the label with fade + expand. Frosting uses `frostedChrome(capsule = true)`: the
  capsule is clipped round and gets a hairline all round (a `drawRoundRect` rather than a generic shape, because
  `drawOutline` isn't available from our Compose imports). The content padding above the bar is now 92 dp (was 80).
  Icons: `GalleryIcons.PicturesTab` (frame, mountain, sun), `AlbumsTab` (stacked cards), `MenuTab` (four tiles),
  replacing the material-icons-core placeholders. Selection action bars are still full-width (not floating).
- **Tab bar, second pass** (user: the first capsule "doesn't look very good"): the glass was too thin (photos showed
  through with edges), spacing uneven, filled-block icons crude, olive wallpaper-colour pill muddy. Now: a compact
  capsule centred and sized to its tabs (3 equal slots, first 112 dp x 56 dp, then 96 dp x 64 dp at the user's request: narrower and taller), heavier glass (`floatingHazeStyle`:
  blur 48 dp, surfaceContainerHigh at 86 %), outlined 1.8-stroke line icons (`GalleryIcons.lineIcon`), and one
  neutral pill (onSurface at 12 %) that slides with the pager's live position via `graphicsLayer` (no recomposition
  while swiping). No ripple on tabs: the sliding pill is the feedback. Content padding above it 92 dp.

## Locked (vault), 2026-11-19
- **Real vault, by the user's choice:** locked photos must be invisible to every other app, so locking *moves* files
  into app-private storage (`filesDir/vault`), which other apps and file managers can't read. Not encrypted for now
  (user's call: app-private storage plus the phone's own encryption; our own key would add a "lost key = lost photos"
  risk and slow every open).
- **Hard-rule exception, approved by the user:** locking deletes the original from shared storage, not via Trash
  (Trash would leave it visible to other apps for 30 days). Only after the vault copy is verified byte for byte, and
  through Android's own `createDeleteRequest` confirmation. Deleting from Locked is the only copy, so it is confirmed.
- **Safe move:** `VaultStorage.store` writes a `.part` file, fsyncs, re-hashes it against the SHA-256 of the bytes
  read, then renames; a failed copy leaves nothing. Rows (`vault_items`, Room v2 via AutoMigration) start `pending`;
  after the delete confirmation each row is settled by whether its original still exists (gone: keep; still there:
  remove the duplicate; can't tell: wait). The same check runs on start for locks interrupted by process death.
  Unlock inserts into the original `RELATIVE_PATH` (fallback `Pictures/Restored/`, `Movies/Restored/`), re-hashes the
  written copy, and only then deletes the vault file.
- **Viewing:** vault files are served by a non-exported `FileProvider` (`${applicationId}.vault`), so Coil,
  Telephoto, Media3 and the info panel work unchanged; ids are negative (`VaultIds`, base 2^40) so they never clash.
  Grid thumbnails come from `ThumbnailUtils` on the private file (MediaStore has none for them).
- **Unlock to view:** the framework `BiometricPrompt` (BIOMETRIC_STRONG or DEVICE_CREDENTIAL), so no new library and
  no app PIN. `VaultSession` relocks on the screen's ON_STOP (leaving it, hiding the app, screen off), which also
  closes an open locked photo. `FLAG_SECURE` while Locked is shown (no screenshots, blank recents preview).
- **Where:** Menu > Locked (grid, Share / Unlock / Delete, Unlock everything); "Lock" in the selection bar's More menu
  (Pictures, albums, Favourites, Videos), in the viewer's menu, and on the Albums tab selection bar (whole albums).
  Locked photos offer no edit / favourite / "open with" (those would hand the file out); Share stays.
- **Uninstall / backup:** `android:hasFragileUserData="true"` makes Android ask "keep app data?" on uninstall;
  clearing data still wipes the vault, so "Unlock everything" is the escape hatch. `allowBackup` is now false (Google's
  cloud backup would have uploaded the vault; the offline rule forbids it).
- **Locked, second round (2026-11-19):**
  - *Uninstall:* Android runs no app code on uninstall, so the vault can't empty itself then (user chose to keep it
    private over a shared hidden folder). The Locked screen and the Unlock-everything dialog say to unlock everything
    first and to keep app data when uninstalling (`hasFragileUserData`).
  - *Pages:* Locked is laid out like the main gallery: a Photos page (timeline grid) and an Albums page (the Albums
    tab's `AlbumGrid`, now internal, fed with vault items grouped by the album they were locked from;
    `VaultIds.albumIdOf(name)`), swiped between with the floating tab bar. An album opens its own timeline, and the
    viewer then pages through that album only (`ViewerRoute.vaultAlbum`). Selected albums: Share / Unlock / Delete.
  - *No pop-ups:* with "Skip confirmation pop-ups" (`MANAGE_MEDIA`) on, Android deletes the originals of a lock
    without asking, so locking is one tap. With it off, the first lock shows a one-time hint (`lockHintShown` in
    DataStore) that links to the system switch; otherwise Android's prompt stays (it can't be avoided).
  - *Trash with Undo* (user's pick over a confirmation dialog): with that access on, trash happens at once and an
    "Moved to trash · Undo" bar (`UndoBarHost` at the app root, `LocalUndoBar`) shows for 5 s; Undo is a silent
    `createTrashRequest(..., false)` on the trashed URIs. With it off, Android's own trash dialog stays and no Undo is
    shown (it would pop a second system dialog). Delete forever keeps its in-app confirmation everywhere.

## Pull down to reach, album switcher (2026-11-19)
- **Pull down to reach** (`core/ui/components/ReachDown.kt`): on Pictures, the Albums tab, album / Favourites /
  Videos / Trash grids and both Locked pages, pulling down at the top of the grid brings the whole grid down so its top lands at
  status bar + 30% of the screen height, measured on screen so it is the same on every screen whatever bar sits
  above; a big title and item count fill the space, and the screen's top icons (search, ⋮; not Back) fade out at
  the top and ride in a row just above the grid instead (`ReachTopIcons`, still laid out but untappable when hidden). It stays down (saved per screen) until pushed
  back up. Built as a `NestedScrollConnection` around the grid, so the grid's own scrolling is untouched: only the
  drag the grid can't use (pulling past its top) moves it, and a push up first brings it back. 1:1 tracking,
  rubber-band past the open position, momentum projection on release, haptic tick at the halfway point. The grid
  moves by layout `offset` (not a graphics layer) so shared-element flights and hit tests stay correct.
- **Album switcher** (`AlbumSwitcher.kt`): inside an album (and inside a locked album), a sideways swipe on the grid
  pulls in a list of albums from the left (M3 `ModalNavigationDrawer`, whose drag covers the whole content, so it
  works from the middle; the screen edges stay the system back gesture). Off while selecting. Picking an album
  switches in place (`AlbumDetailViewModel.switchTo`, saved in `SavedStateHandle`): no new screen, so Back still
  returns to the Albums tab. The grid is keyed by album so a new album starts at the top.
- **Selection actions as a floating capsule** (2026-11-19, user's call, for consistency with the tab bar): every
  selection action bar (photos, albums, Trash, Locked) is a `FloatingActionPill`: frosted capsule, 64 dp tall,
  centred 12 dp above the bottom inset, equal 76 dp slots, pill-shaped press ripple.
- Pull-down title and icon row draw *over* the grid: on Pictures the grid's opaque background reaches up under the
  icon row (status-bar inset), which hid the search icon.
- **Copy to clipboard** (2026-11-19): a Copy icon next to the viewer's ⋮, and in the selection top bar when exactly
  one item is selected (Pictures, albums, Favourites, Videos, Search). Not in Trash, and not in Locked (the clipboard
  would hand a private photo to other apps). The clip carries the content URI (`core/domain/Clipboard.kt`), built by
  hand with the known MIME type (`ClipData.newUri` would query the provider on the main thread); the pasting app gets
  read access to that one file, and Android 13+ shows its own "Copied" preview. Copying ends the selection.
- **Album switcher, second pass** (user's reference screenshot): replaced the M3 modal drawer with a narrow strip
  (26% of the width) of album covers stacked with name and count, below the title bar (`MediaListScreen`
  `gridWrapper`). It *pushes* the grid right (layout `offset`, so photo flights stay correct) instead of covering it
  with a scrim; the grid stays usable. Picking an album keeps the strip open (to flick through albums); swipe left
  or Back closes it. Own `draggable` with 1:1 tracking, rubber band past open, projection on release. The grid's
  columns are not squeezed: grids size cells from the window width, and re-laying them out during the drag would
  cost frames.

## Hidden albums merged into Locked (2026-11-19, user's call)
- Hidden albums are gone: no Hide / Show action, nothing in Edit albums, no filtering. `MediaRepository` has one
  `state` (the old filtered `state` / unfiltered `allState` pair is merged); `HiddenAlbums` and its DI binding are
  deleted. The Room `hidden` column stays (no migration; nothing reads it). Albums the user had hidden simply show
  again (the user confirmed there were none worth keeping).
- Locked is the one private place, and its only door is a gesture: on the Albums tab, pull down (the thumb-reach
  pull) past halfway and keep holding for 2 s (`ReachDown` `onHold`); a long-press buzz, then Locked opens and asks
  for the screen lock. No Menu row, no visible hint, so nobody browsing the phone learns it exists.
- **Locked on / off in Settings** (2026-11-19): a "Locked folder" switch (DataStore `locked_enabled`, on by default)
  and, while on, an "Open Locked" row. Off hides every Lock action (viewer menu, selection More, Albums selection) and
  the 2 s hold (`VaultLocker.enabled`). Turning off asks for the screen lock first (one switch would otherwise pour
  the private photos back into the gallery), then `unlockAll` moves everything back to its albums with a progress
  dialog (Back blocked); the switch only goes off when every item made it, otherwise Locked stays on and keeps the
  rest. The count is awaited, never assumed empty.
- **One moving title** (2026-11-19, user's call, all screens): instead of a big title plus a fading bar title, the
  title is one piece of text drawn by `ReachDown`: big and centred while the grid is down, gliding and shrinking (by
  font-size ratio, graphics layer only) onto the bar's title spot as it goes up. The bar keeps an invisible
  `ReachBarTitle` that reports where to land; with no `ReachDown` on screen (empty / loading) it draws normally.
  Pictures and the selecting state have no bar title, so there it rises and fades. `ReachDown` no longer clips (the
  title leaves its area). While the album strip pushes the grid aside, the title centres and the icons sit within
  the visible part (`LocalPushedAside`; not the window position, which would re-centre during tab swipes).
- **Delete album** (2026-11-19): Albums selection bar "Delete" and an album's ⋮ "Delete album" trash everything in the
  album(s) (`rememberAlbumDeleter`, `createTrashRequest`: recoverable for 30 days, per hard rule 1); an empty bucket
  is no album, so it disappears. Same confirmation rules as trashing photos (Skip confirmation on: at once + Undo
  bar; off: Android asks). The folder itself may remain on storage, empty: MediaStore gives no way to remove
  directories, and asking for all-files access for that is not worth it.
- **Albums tab: + (new album) and Search** (2026-11-19), next to the ⋮ (also in the icon row above the pulled-down
  grid). Android only knows an album once a file is in it, so + asks for a name, then opens `NewAlbumScreen`: the
  whole library like Pictures, where a tap picks a photo (never opens the viewer), and a pill with Copy here / Move
  here (the existing `SelectionActions` copy / move into `MediaTransfer.newAlbum`; Move asks Android first). It
  closes once the photos are in (`SelectionActions.finished`).
- **Add photos to an existing album** (2026-11-19): a + in every album's top bar opens the same picker
  (`NewAlbumRoute(name, bucketId)`): the library minus what is already in that album, Copy here / Move here into the
  album's own folder (`MediaTransfer.destinationOf` its cover). Verified on the phone: + on Albums → "Test album" →
  one photo → Copy here made the album with the copy.
- **Album fixes after a review** (2026-11-19):
  - **Folders Android refuses:** MediaStore takes photos only under `DCIM/` / `Pictures/`, videos under `DCIM/` /
    `Movies/` / `Pictures/` (`folderAccepts`). Move / Copy skip what can't go into the target and say why; the
    "Add to" picker only lists what fits (a Movies album offers videos) and shows a message instead of a grid for
    Download / WhatsApp folders. Chosen over writing into Download through the Downloads collection: simpler, and
    Download isn't a real album.
  - **One transfer at a time:** `SelectionActions.working` is set on the tap itself, so a double tap can't copy
    twice; a declined Move prompt calls `cancelMove()`.
  - **Progress with Stop:** `TransferProgressDialog` shows "n of N" and Stop. A copy stops mid-file too (own
    256 KB loop checks the job) and the half-written copy is removed; a move stops before the next item.
  - **Names:** a new album can't take an existing album's name (any case: folders ignore case, so it would merge).
    The typed name survives rotation.
  - **Picker:** albums in the Albums tab's order, without the album you are in.
  - **Delete album** drops the albums' pins / custom places (`AlbumRepository.forget`), so a later album with the
    same folder starts fresh; Undo puts them back.
  - Back in the photo picker clears the picks first; + in an album waits for the album to load.
- **Dates kept through Trash** (2026-11-19, user saw restored photos jump to another day): Android re-reads a file
  when it leaves Trash, and a photo with no date inside the file (WhatsApp) can come back with another date.
  `MediaActions.trashRequest` saves each photo's shown date (date taken, else file date) and favourite mark in Room
  (`trash_snapshots`, kept 35 days). One app-wide `RestoreRepairEffect` compares restored photos with their snapshot
  (`findRepairs`) and writes the old date back as `DATE_TAKEN` (plus the favourite mark) after a write request:
  silent with Skip confirmation on, one Android prompt otherwise. Covers every restore path (Undo bars, Trash
  screen, viewer) without touching each one. Only MediaStore columns change, never the file. Snapshots of photos
  that came back unchanged are dropped after 10 minutes (not sooner: the trash prompt may still be open).
- **Remove album, keep the photos** (2026-11-19, user's call: "back to where they came from"): every Move now saves
  the folder a photo left (Room `media_origins`, `MediaTransfer.moveEach`). Remove (Albums selection bar, album ⋮)
  sends each photo back to its saved folder (`planRemoval`: not a folder being removed, and one Android accepts);
  photos without one (copies, or moved before this) go where the user picks in the album sheet. Then the folder
  is empty and the album is gone; nothing goes to Trash. If anything can't move, the album stays and says so.
  Room schema 3 (two new tables, auto-migrated).
- **More menu as a frosted card** (2026-11-20, user's pick): the selection bar's More opens a small frosted card
  (`MoreMenuCard` in `SelectionBars.kt`) instead of Material's dropdown: same material as the capsule
  (`floatingHazeStyle`, `frostedChrome(corner = 24.dp)`), right-aligned 10 dp above the capsule
  (`AboveAnchor` position provider; the More slot spans the capsule's height), springing out of the More button
  (scale 0.6 → 1, damping 0.78) with a light haptic. Rows have line icons drawn in `GalleryIcons` (FolderMove,
  FolderCopy, Lock); Lock sits under a hairline. It is a `Popup`, which works with Haze because Haze
  places its blur by screen position.
- Copy to clipboard stays in the selection top bar (one photo only); tried in More and moved back (user's call).
  The card was made more compact after the first look (196 dp wide, smaller rows).
