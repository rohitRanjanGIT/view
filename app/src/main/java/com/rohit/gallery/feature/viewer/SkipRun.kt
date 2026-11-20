package com.rohit.gallery.feature.viewer

/** Where a tap landed on a video page, by thirds of its width. */
enum class TapSide { Back, Middle, Forward }

/** Taps on the same side this soon after the last step add another step to the run. */
const val SKIP_RUN_WINDOW_MS = 700L

/** Left third skips back, right third skips forward, the middle third is left to zoom. */
fun tapSide(x: Float, width: Float): TapSide = when {
    width <= 0f -> TapSide.Middle
    x < width / 3f -> TapSide.Back
    x > width * 2f / 3f -> TapSide.Forward
    else -> TapSide.Middle
}

/** Where a skip of [deltaMs] from [positionMs] lands: never before the start or (when known) past the end. */
fun skipTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
    val target = positionMs + deltaMs
    return if (durationMs > 0) target.coerceIn(0L, durationMs) else target.coerceAtLeast(0L)
}

/**
 * A run of double-tap skips on one side (YouTube style): a double-tap starts it with one step, and every further
 * tap on the same side within [windowMs] of the last step adds one more. A tap elsewhere, or waiting, ends it.
 * Pure (times are passed in), so it is unit tested without a device.
 */
class SkipRun(private val windowMs: Long = SKIP_RUN_WINDOW_MS) {
    /** The side of the current run, or null when there is none (it may still be null-checked after expiry). */
    var side: TapSide? = null
        private set
    /** Steps in the current run (1 after the double-tap). */
    var steps: Int = 0
        private set
    private var lastStepAtMs = 0L

    /** A double-tap on [side] (Back or Forward) at [nowMs]: a new run with one step. */
    fun start(side: TapSide, nowMs: Long) {
        require(side != TapSide.Middle) { "the middle third zooms, it never skips" }
        this.side = side
        steps = 1
        lastStepAtMs = nowMs
    }

    /** True if a tap on [side] pressed at [nowMs] continues the run (and should not reach zoom or the chrome). */
    fun continuesWith(side: TapSide, nowMs: Long): Boolean =
        this.side != null && side == this.side && nowMs - lastStepAtMs in 0..windowMs

    /** One more step on the run's side at [nowMs]. Call only after [continuesWith] said yes. */
    fun step(nowMs: Long) {
        steps++
        lastStepAtMs = nowMs
    }

    fun end() {
        side = null
        steps = 0
    }
}
