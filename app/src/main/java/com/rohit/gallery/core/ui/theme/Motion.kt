package com.rohit.gallery.core.ui.theme

import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI

/**
 * Shared motion specs. Springs are described the way Apple's fluid-interface guidance does: a damping ratio (1 = no
 * overshoot) and a *response*, roughly the seconds it takes to reach the target. A spring has no fixed duration and
 * starts from the current value and velocity, so it can be redirected mid-flight.
 */
object GalleryMotion {
    fun <T> standard() = spring<T>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    fun <T> snappy() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    /** Stiffness for a spring of mass 1 that has the given [response] in seconds. */
    fun stiffnessFor(response: Float): Float = ((2 * PI / response) * (2 * PI / response)).toFloat()

    fun <T> fluid(response: Float, dampingRatio: Float = 1f, visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio, stiffnessFor(response), visibilityThreshold)

    /** The details panel, opened or closed by a swipe or a button. */
    fun <T> sheet() = fluid<T>(response = 0.3f)

    /** A dragged photo springing back to rest; a little give, because a gesture with momentum preceded it. */
    fun <T> dragReturn() = fluid<T>(response = 0.35f, dampingRatio = 0.85f)

    /** Grid -> viewer: no gesture to inherit, so critically damped. */
    fun sharedBounds(): FiniteAnimationSpec<Rect> = fluid(response = OPEN_RESPONSE, visibilityThreshold = FlightThreshold)

    /**
     * Viewer -> grid. [velocity] (px/s) is how fast the finger was moving when it let go: the flight starts at that
     * speed, so there is no seam between dragging the photo and it flying into its cell. Zero for a back press.
     */
    fun sharedBoundsClosing(velocity: Offset): FiniteAnimationSpec<Rect> =
        VelocityHandoff(fluid(response = CLOSE_RESPONSE, visibilityThreshold = FlightThreshold), velocity.capped())

    /**
     * Where a fling would come to rest, past the point it was released at (Apple's projection: exponential decay,
     * like a scroll view). [decelerationRate] 0.998 is normal scrolling; lower values travel less.
     */
    fun project(velocity: Float, decelerationRate: Float = 0.998f): Float =
        velocity / 1000f * decelerationRate / (1f - decelerationRate)

    const val OPEN_RESPONSE = 0.28f
    const val CLOSE_RESPONSE = 0.4f
    private const val MAX_HANDOFF_VELOCITY = 6000f

    /**
     * A flight counts as landed within 1.5 px of its cell. The default (0.5 px) kept the spring creeping invisibly
     * for a further ~0.1 s, and the viewer layer only goes once the flight has landed.
     */
    private val FlightThreshold = Rect(1.5f, 1.5f, 1.5f, 1.5f)

    const val FADE_MS = 140

    private fun Offset.capped(): Offset {
        val speed = getDistance()
        return if (speed <= MAX_HANDOFF_VELOCITY) this else this * (MAX_HANDOFF_VELOCITY / speed)
    }
}

/**
 * A bounds spring that starts moving at [velocity] instead of from rest. The shared-element library starts its
 * bounds animation at zero velocity and has no way to pass one in, so this substitutes the finger's velocity when the
 * library's is zero (the start of the flight). If the flight is redirected mid-way the library passes the current,
 * non-zero velocity, which is kept, so the motion stays continuous.
 */
private class VelocityHandoff(private val spring: SpringSpec<Rect>, private val velocity: Offset) : FiniteAnimationSpec<Rect> {
    override fun <V : AnimationVector> vectorize(converter: TwoWayConverter<Rect, V>): VectorizedFiniteAnimationSpec<V> {
        val inner = spring.vectorize(converter)
        // A Rect animates as (left, top, right, bottom); moving the photo moves all four edges.
        val handoff = converter.convertToVector(Rect(velocity.x, velocity.y, velocity.x, velocity.y))
        fun pick(initialVelocity: V): V = if (converter.convertFromVector(initialVelocity) == Rect.Zero) handoff else initialVelocity
        return object : VectorizedFiniteAnimationSpec<V> {
            override fun getValueFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V =
                inner.getValueFromNanos(playTimeNanos, initialValue, targetValue, pick(initialVelocity))

            override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V =
                inner.getVelocityFromNanos(playTimeNanos, initialValue, targetValue, pick(initialVelocity))

            override fun getDurationNanos(initialValue: V, targetValue: V, initialVelocity: V): Long =
                inner.getDurationNanos(initialValue, targetValue, pick(initialVelocity))
        }
    }
}
