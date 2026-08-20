package io.atlee.wordstv

import java.net.URI

internal object UrlPolicy {
    const val WORDS_URL = "https://words.atlee.io/display"
    private const val WORDS_HOST = "words.atlee.io"

    fun isAllowed(url: String?): Boolean {
        if (url == null) return false

        return runCatching {
            val uri = URI(url)
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals(WORDS_HOST, ignoreCase = true) &&
                (uri.port == -1 || uri.port == 443) &&
                uri.userInfo == null
        }.getOrDefault(false)
    }
}
