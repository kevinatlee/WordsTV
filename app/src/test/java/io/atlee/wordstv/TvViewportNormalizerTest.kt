package io.atlee.wordstv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvViewportNormalizerTest {
    @Test
    fun normalizesDensityScaledChromecastSurface() {
        val config = TvViewportNormalizer.calculate(viewportWidthPx = 1920, density = 2f)

        assertEquals(1728, TvViewportNormalizer.TARGET_CSS_WIDTH)
        assertEquals(1728, config.cssWidth)
        assertEquals(5.0 / 9.0, config.metaInitialScale, 0.0001)
        assertEquals(111, config.webViewInitialScalePercent)
        assertEquals(
            "width=1728, initial-scale=0.5556, minimum-scale=0.5556, " +
                "maximum-scale=0.5556, user-scalable=no",
            config.viewportMetaContent(),
        )
    }

    @Test
    fun preservesCalibratedViewportAcross1080pAnd4kSurfaces() {
        val mdpi1080p = TvViewportNormalizer.calculate(viewportWidthPx = 1920, density = 1f)
        val densityScaled4k = TvViewportNormalizer.calculate(viewportWidthPx = 3840, density = 2f)

        assertEquals(10.0 / 9.0, mdpi1080p.metaInitialScale, 0.0001)
        assertEquals(111, mdpi1080p.webViewInitialScalePercent)
        assertEquals(10.0 / 9.0, densityScaled4k.metaInitialScale, 0.0001)
        assertEquals(222, densityScaled4k.webViewInitialScalePercent)
    }

    @Test
    fun adaptsToDifferentDensityWithoutDeviceChecks() {
        val config = TvViewportNormalizer.calculate(viewportWidthPx = 1920, density = 1.5f)

        assertEquals(20.0 / 27.0, config.metaInitialScale, 0.0001)
        assertEquals(111, config.webViewInitialScalePercent)
    }

    @Test
    fun invalidAndExtremeInputsAlwaysProduceSafeValues() {
        val configs = listOf(
            TvViewportNormalizer.calculate(viewportWidthPx = 0, density = 2f),
            TvViewportNormalizer.calculate(viewportWidthPx = -1920, density = 2f),
            TvViewportNormalizer.calculate(viewportWidthPx = 1920, density = 0f),
            TvViewportNormalizer.calculate(viewportWidthPx = 1920, density = -1f),
            TvViewportNormalizer.calculate(viewportWidthPx = 1920, density = Float.NaN),
            TvViewportNormalizer.calculate(viewportWidthPx = 1920, density = Float.POSITIVE_INFINITY),
            TvViewportNormalizer.calculate(viewportWidthPx = 1, density = Float.MAX_VALUE),
            TvViewportNormalizer.calculate(viewportWidthPx = Int.MAX_VALUE, density = Float.MIN_VALUE),
        )

        configs.forEach { config ->
            assertEquals(1728, config.cssWidth)
            assertTrue(config.metaInitialScale.isFinite())
            assertTrue(config.metaInitialScale in 0.1..4.0)
            assertTrue(config.webViewInitialScalePercent in 25..400)
        }
    }
}
