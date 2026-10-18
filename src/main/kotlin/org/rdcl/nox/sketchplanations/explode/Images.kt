package org.rdcl.nox.sketchplanations.explode

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** Sizes the reader asks imgix for. Ask for what the layout needs, never the 3,000px original. */
public enum class Rendition(internal val params: Map<String, String>) {
    /** List rows: 5:4, anchored at the top where the idea sits. */
    Thumb(mapOf("w" to "240", "ar" to "5:4", "fit" to "crop", "crop" to "top")),
    /** List rows, letterboxed: the whole sketch, logo included. */
    ThumbWhole(mapOf("w" to "240", "fit" to "max")),
    /** Today's deck cards. */
    Card(mapOf("w" to "760", "ar" to "4:3", "fit" to "crop", "crop" to "top")),
    Read(mapOf("w" to "1200")),
    /** Zoom and save-to-gallery. */
    Hd(mapOf("w" to "2400")),
}

/** Pure URL building — no network. */
public object Images {
    /**
     * The URL for [image] at [rendition]. Animated GIFs come back untouched: imgix's format
     * conversion would flatten them to a single frame.
     */
    public fun url(image: SketchImage, rendition: Rendition): String {
        if (image.isAnimated) return image.original
        val uri = URI(image.url)
        val rect = query(uri.rawQuery)["rect"]
        val params = linkedMapOf("auto" to "format,compress")
        rect?.let { params["rect"] = it }
        params += rendition.params
        val q = params.entries.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8").replace("%2C", ",").replace("%3A", ":") }
        return image.original + "?" + q
    }

    private fun query(raw: String?): Map<String, String> =
        raw.orEmpty().split('&').filter { '=' in it }.associate {
            val (k, v) = it.split('=', limit = 2)
            k to URLDecoder.decode(v, "UTF-8")
        }
}
