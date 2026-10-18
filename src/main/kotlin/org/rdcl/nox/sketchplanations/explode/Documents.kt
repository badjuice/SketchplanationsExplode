package org.rdcl.nox.sketchplanations.explode

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Turns Prismic documents into domain models. Nothing Prismic-shaped leaves this file. Parsing is
 * forgiving: a malformed block is skipped, a missing optional field is null — a missing paragraph
 * beats a crash.
 */
internal object Documents {

    fun sketch(doc: JsonObject): Sketch? {
        val data = doc.obj("data") ?: return null
        val uid = doc.str("uid") ?: return null
        val image = data.obj("image")?.let(::image) ?: return null
        val raw = data.arr("body")?.mapNotNull { it as? JsonObject }.orEmpty()
        val body = blocks(raw)
        val links = body.flatMap(::linksIn)
        val (related, relatedStart) = related(body, uid)
        val first = date(doc.str("first_publication_date"))
        return Sketch(
            id = doc.str("id") ?: uid,
            uid = uid,
            title = data.str("title").orEmpty().trim(),
            publishedAt = date(data.str("published_at")) ?: first ?: Instant.EPOCH,
            firstPublishedAt = first ?: Instant.EPOCH,
            lastPublishedAt = date(doc.str("last_publication_date")) ?: first ?: Instant.EPOCH,
            tags = data.arr("tags").orEmpty().mapNotNull { g ->
                val t = (g as? JsonObject)?.obj("tag") ?: return@mapNotNull null
                val id = t.str("id") ?: return@mapNotNull null
                TagRef(id, t.str("uid") ?: t.str("slug") ?: id)
            },
            image = image,
            standfirst = standfirst(body),
            body = body,
            links = links,
            related = related,
            relatedSectionStart = relatedStart,
            versions = links.mapNotNull { l -> (l.target as? LinkTarget.ImageVersion)?.let { ImageVersion(l.label, it.url) } }
                .distinctBy { it.url },
            podcastUrl = data.str("podcast_link_url")?.takeIf { it.isNotBlank() },
            printsUrl = data.str("redbubble_link_url")?.takeIf { it.isNotBlank() },
        )
    }

    fun topic(doc: JsonObject): Topic? {
        val id = doc.str("id") ?: return null
        val uid = doc.str("uid") ?: return null
        return Topic(id, uid, doc.obj("data")?.str("identifier")?.trim()?.takeIf { it.isNotEmpty() } ?: uid)
    }

    private fun image(o: JsonObject): SketchImage? {
        val url = o.str("url") ?: return null
        val dims = o.obj("dimensions")
        return SketchImage(url, o.str("alt")?.takeIf { it.isNotBlank() }, dims?.int("width"), dims?.int("height"))
    }

    // ---- rich text --------------------------------------------------------------------------

    fun blocks(raw: List<JsonObject>): List<Block> {
        val out = mutableListOf<Block>()
        var list: Pair<Boolean, MutableList<RichText>>? = null
        fun flush() { list?.let { out += Block.ListBlock(it.first, it.second) }; list = null }
        for (b in raw) {
            val type = b.str("type") ?: continue
            val ordered = type == "o-list-item"
            if (type == "list-item" || ordered) {
                if (list?.first != ordered) flush()
                if (list == null) list = ordered to mutableListOf()
                list!!.second += rich(b)
                continue
            }
            flush()
            when {
                type == "paragraph" -> rich(b).takeUnless { it.isEmpty }?.let { out += Block.Paragraph(it) }
                type.startsWith("heading") -> out += Block.Heading(type.removePrefix("heading").toIntOrNull() ?: 2, rich(b))
                type == "preformatted" -> out += Block.Preformatted(rich(b))
                type == "image" -> image(b)?.let { out += Block.Image(it) }
                else -> out += Block.Unknown(type, b.str("text").orEmpty())
            }
        }
        flush()
        return out
    }

    private fun rich(b: JsonObject): RichText {
        val text = b.str("text").orEmpty()
        val spans = b.arr("spans").orEmpty().mapNotNull { s ->
            val o = s as? JsonObject ?: return@mapNotNull null
            val start = o.int("start") ?: return@mapNotNull null
            val end = (o.int("end") ?: return@mapNotNull null).coerceAtMost(text.length)
            if (start >= end) return@mapNotNull null
            val style = when (o.str("type")) {
                "strong" -> SpanStyle.Strong
                "em" -> SpanStyle.Em
                "hyperlink" -> link(o.obj("data"), text.substring(start, end))?.let { SpanStyle.Hyperlink(it) }
                else -> null
            } ?: return@mapNotNull null
            Span(start, end, style)
        }
        return RichText(text, spans)
    }

    private fun link(d: JsonObject?, label: String): Link? {
        d ?: return null
        val target = when (d.str("link_type")) {
            "Document" -> when (d.str("type")) {
                "sketchplanation" -> d.str("uid")?.let { LinkTarget.Sketch(it) }
                "tag" -> d.str("uid")?.let { LinkTarget.Topic(it) }
                else -> d.str("url")?.let { Links.classify(it) }
            }
            else -> d.str("url")?.let { Links.classify(it) }
        } ?: return null
        return Link(label.trim(), target)
    }

    private fun linksIn(b: Block): List<Link> = when (b) {
        is Block.Paragraph -> linksIn(b.content)
        is Block.Heading -> linksIn(b.content)
        is Block.Preformatted -> linksIn(b.content)
        is Block.ListBlock -> b.items.flatMap(::linksIn)
        else -> emptyList()
    }

    private fun linksIn(t: RichText) = t.spans.mapNotNull { (it.style as? SpanStyle.Hyperlink)?.link }

    /** A post opens with a standfirst when its first paragraph is italic from end to end. */
    private fun standfirst(body: List<Block>): String? {
        val p = body.firstOrNull() as? Block.Paragraph ?: return null
        val t = p.content
        val italic = t.spans.any { it.style == SpanStyle.Em && it.start == 0 && it.end >= t.text.trimEnd().length }
        return if (italic) t.text.trim() else null
    }

    private val relatedHeading = Regex("^(related|ideas related|other related|more related)", RegexOption.IGNORE_CASE)
    private val alsoSee = Regex("^(also see|see also)", RegexOption.IGNORE_CASE)

    /**
     * The author lists connections two ways: a "Related Ideas…" heading followed by links, and
     * "Also see …" lines. Both are collected; the heading's position is returned so a reader can
     * render that section as cards.
     */
    private fun related(body: List<Block>, self: String): Pair<List<String>, Int?> {
        val uids = LinkedHashSet<String>()
        var start: Int? = null
        var level = Int.MAX_VALUE
        var inSection = false
        body.forEachIndexed { i, b ->
            if (b is Block.Heading) {
                if (relatedHeading.containsMatchIn(b.content.text.trim())) {
                    inSection = true; level = b.level
                    if (start == null) start = i
                    return@forEachIndexed
                }
                if (inSection && b.level <= level) inSection = false
            }
            val alsoSeeLine = b is Block.Paragraph && alsoSee.containsMatchIn(b.content.text.trim())
            if (inSection || alsoSeeLine) {
                linksIn(b).forEach { l -> (l.target as? LinkTarget.Sketch)?.uid?.let { if (it != self) uids += it } }
            }
        }
        return uids.toList() to start
    }

    // ---- json helpers -----------------------------------------------------------------------

    private val prismicDate = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")

    fun date(s: String?): Instant? {
        if (s.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(s, prismicDate).toInstant() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull()
            ?: runCatching { LocalDate.parse(s).atStartOfDay().toInstant(ZoneOffset.UTC) }.getOrNull()
    }

    fun JsonObject.obj(k: String) = this[k] as? JsonObject
    fun JsonObject.arr(k: String) = this[k] as? JsonArray
    fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
    fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull
}
