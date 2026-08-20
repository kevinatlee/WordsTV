package io.atlee.wordstv

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

internal data class TvViewportConfig(
    val cssWidth: Int,
    val metaInitialScale: Double,
    val webViewInitialScalePercent: Int,
) {
    fun viewportMetaContent(): String {
        val scale = BigDecimal.valueOf(metaInitialScale)
            .setScale(4, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()

        return "width=$cssWidth, initial-scale=$scale, minimum-scale=$scale, " +
            "maximum-scale=$scale, user-scalable=no"
    }
}

internal object TvViewportNormalizer {
    const val TARGET_CSS_WIDTH = 1536

    private const val DEFAULT_META_SCALE = 1.0
    private const val DEFAULT_INITIAL_SCALE_PERCENT = 100
    private const val MIN_META_SCALE = 0.1
    private const val MAX_META_SCALE = 4.0
    private const val MIN_INITIAL_SCALE_PERCENT = 25
    private const val MAX_INITIAL_SCALE_PERCENT = 400

    fun calculate(viewportWidthPx: Int, density: Float): TvViewportConfig {
        if (viewportWidthPx <= 0 || !density.isFinite() || density <= 0f) {
            return defaultConfig()
        }

        val logicalWidthAtDefaultScale = viewportWidthPx.toDouble() / density.toDouble()
        val measuredMetaScale = logicalWidthAtDefaultScale / TARGET_CSS_WIDTH.toDouble()
        val measuredInitialScalePercent =
            (viewportWidthPx.toDouble() / TARGET_CSS_WIDTH.toDouble() * 100.0)

        if (!measuredMetaScale.isFinite() || !measuredInitialScalePercent.isFinite()) {
            return defaultConfig()
        }

        return TvViewportConfig(
            cssWidth = TARGET_CSS_WIDTH,
            metaInitialScale = measuredMetaScale.coerceIn(MIN_META_SCALE, MAX_META_SCALE),
            webViewInitialScalePercent = measuredInitialScalePercent
                .roundToInt()
                .coerceIn(MIN_INITIAL_SCALE_PERCENT, MAX_INITIAL_SCALE_PERCENT),
        )
    }

    private fun defaultConfig() = TvViewportConfig(
        cssWidth = TARGET_CSS_WIDTH,
        metaInitialScale = DEFAULT_META_SCALE,
        webViewInitialScalePercent = DEFAULT_INITIAL_SCALE_PERCENT,
    )
}
