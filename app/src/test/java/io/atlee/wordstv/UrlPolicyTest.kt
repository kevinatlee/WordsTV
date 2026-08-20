package io.atlee.wordstv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {
    @Test
    fun acceptsWordsHttpsUrls() {
        assertTrue(UrlPolicy.isAllowed("https://words.atlee.io/display"))
        assertTrue(UrlPolicy.isAllowed("https://words.atlee.io/game/ABC#round"))
        assertTrue(UrlPolicy.isAllowed("https://WORDS.ATLEE.IO:443"))
    }

    @Test
    fun rejectsOtherDestinationsAndSchemes() {
        assertFalse(UrlPolicy.isAllowed("http://words.atlee.io"))
        assertFalse(UrlPolicy.isAllowed("https://evil.example"))
        assertFalse(UrlPolicy.isAllowed("https://words.atlee.io.evil.example"))
        assertFalse(UrlPolicy.isAllowed("https://words.atlee.io:444"))
        assertFalse(UrlPolicy.isAllowed("javascript:alert(1)"))
        assertFalse(UrlPolicy.isAllowed(null))
    }
}
