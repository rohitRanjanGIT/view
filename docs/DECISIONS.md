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
