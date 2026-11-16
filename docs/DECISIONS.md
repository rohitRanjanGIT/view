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
