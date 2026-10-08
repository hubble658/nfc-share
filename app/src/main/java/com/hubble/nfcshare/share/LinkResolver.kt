package com.hubble.nfcshare.share

import com.luigivampa92.ndefemulation.ndef.LocationNdefData
import com.luigivampa92.ndefemulation.ndef.NdefData
import com.luigivampa92.ndefemulation.ndef.TextNdefData
import com.luigivampa92.ndefemulation.ndef.UriNdefData
import com.hubble.nfcshare.R
import java.net.URI

// Single smart resolver shared by the app UI and both widgets: recognizes Maps
// coordinates/links, YouTube/wa.me/t.me/any URL, and falls back to plain text.
object LinkResolver {

    private const val MAX_URI_LENGTH = 800
    private const val MAX_TEXT_LENGTH = 800

    fun resolveLinkShare(raw: String): NdefData? {
        val plainCoords = Regex("^(-?\\d{1,3}(?:\\.\\d+)?)\\s*,\\s*(-?\\d{1,3}(?:\\.\\d+)?)$").find(raw)
        val mapsCoords = Regex("(?:@|[?&]q=|!3d)(-?\\d{1,3}\\.\\d+)[,!]+(?:.*?!4d)?(-?\\d{1,3}\\.\\d+)").find(raw)
        (plainCoords ?: mapsCoords)?.let { match ->
            val lat = match.groupValues[1].toDoubleOrNull()
            val lng = match.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                return LocationNdefData(lat, lng)
            }
        }

        normalizeLink(raw)?.let { return UriNdefData(it) }

        // The whole string might not be a link by itself (e.g. shared as "Check this
        // out: https://... via someone") — pull a URL out of it before giving up on it.
        Regex("https?://\\S+").find(raw)?.value?.trimEnd('.', ',', ')', ']', '"', '\'')?.let { extracted ->
            normalizeLink(extracted)?.let { return UriNdefData(it) }
        }

        return if (raw.isNotEmpty() && raw.length <= MAX_TEXT_LENGTH) TextNdefData(raw) else null
    }

    fun normalizeLink(raw: String): String? {
        val youtubeIdRegex = Regex("(?:youtu\\.be/|youtube\\.com/(?:watch\\?v=|shorts/|embed/))([A-Za-z0-9_-]{6,})")
        val youtubeId = youtubeIdRegex.find(raw)?.groupValues?.get(1)
            ?: raw.takeIf { it.matches(Regex("^[A-Za-z0-9_-]{11}$")) }
        if (youtubeId != null) {
            val url = "https://www.youtube.com/watch?v=$youtubeId"
            return url.takeIf { isValidShareableUrl(it) }
        }

        var value = raw
        if (!value.contains("://") && value.matches(Regex("^[\\w.-]+\\.[A-Za-z]{2,}(/.*)?$"))) {
            value = "https://$value"
        }
        return value.takeIf { isValidShareableUrl(it) }
    }

    fun isValidShareableUrl(value: String): Boolean {
        if (value.isEmpty() || value.length > MAX_URI_LENGTH) return false
        return try {
            !URI.create(value).scheme.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }

    // Picks a small icon that hints at what a saved/shared link actually is.
    fun classifyIcon(raw: String): Int = when {
        raw.contains("youtube.com") || raw.contains("youtu.be") -> R.drawable.ic_type_youtube
        raw.contains("instagram.com") -> R.drawable.ic_type_photo
        raw.contains("google.com/maps") || raw.contains("maps.app.goo.gl") || raw.contains("goo.gl/maps") ||
            Regex("(?:@|[?&]q=|!3d)-?\\d{1,3}\\.\\d+").containsMatchIn(raw) ||
            Regex("^-?\\d{1,3}(?:\\.\\d+)?\\s*,\\s*-?\\d{1,3}(?:\\.\\d+)?$").matches(raw) -> R.drawable.ic_type_maps
        normalizeLink(raw) != null -> R.drawable.ic_type_link
        else -> R.drawable.ic_type_text
    }
}
