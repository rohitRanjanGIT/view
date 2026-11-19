package com.rohit.gallery.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val Base = Typography()

/**
 * Material's sizes with size-specific tracking and leading (Apple's typography rule): letters read too far apart as
 * text grows, so large text is tightened (negative tracking) and set with tighter leading; mid sizes are neutral;
 * small text keeps a little extra spacing for legibility. Material's defaults use roughly one looseness for all
 * sizes. All sizes stay in sp, so the system font size still scales everything.
 */
val GalleryTypography = Base.copy(
    displayLarge = Base.displayLarge.tuned(tracking = -0.025f, leading = 1.08f),
    displayMedium = Base.displayMedium.tuned(tracking = -0.022f, leading = 1.1f),
    displaySmall = Base.displaySmall.tuned(tracking = -0.02f, leading = 1.12f),
    headlineLarge = Base.headlineLarge.tuned(tracking = -0.018f, leading = 1.16f),
    headlineMedium = Base.headlineMedium.tuned(tracking = -0.016f, leading = 1.18f),
    headlineSmall = Base.headlineSmall.tuned(tracking = -0.012f, leading = 1.22f),
    titleLarge = Base.titleLarge.tuned(tracking = -0.008f, leading = 1.25f),
    titleMedium = Base.titleMedium.copy(letterSpacing = 0.sp),
    titleSmall = Base.titleSmall.copy(letterSpacing = 0.05.sp),
    bodyLarge = Base.bodyLarge.copy(letterSpacing = 0.15.sp),
    bodyMedium = Base.bodyMedium.copy(letterSpacing = 0.15.sp),
    bodySmall = Base.bodySmall.copy(letterSpacing = 0.3.sp),
    // Labels (12-14 sp, often over photos or in bars) keep Material's positive tracking.
)

/** [tracking] and [leading] are relative to the font size (em), so they hold at any font scale. */
private fun TextStyle.tuned(tracking: Float, leading: Float) = copy(letterSpacing = tracking.em, lineHeight = leading.em)
