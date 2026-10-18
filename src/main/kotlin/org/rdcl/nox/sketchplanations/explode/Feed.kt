package org.rdcl.nox.sketchplanations.explode

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.net.URI
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses the podcast's Acast RSS feed. DOM rather than StAX so the same code runs on the JVM and
 * on Android.
 */
internal object Feed {
    private const val ITUNES = "http://www.itunes.com/dtds/podcast-1.0.dtd"
    private const val CONTENT = "http://purl.org/rss/1.0/modules/content/"

    fun parse(xml: ByteArray): Podcast {
        val f = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // A feed has no business declaring entities; refuse them outright.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            isExpandEntityReferences = false
        }
        val doc = f.newDocumentBuilder().parse(ByteArrayInputStream(xml))
        val channel = doc.getElementsByTagName("channel").item(0) as? Element
            ?: throw ExplodeException("The podcast feed has no <channel>")
        val episodes = channel.children("item").map(::episode)
        return Podcast(
            title = channel.text("title").orEmpty(),
            author = channel.textNs(ITUNES, "author"),
            copyright = channel.text("copyright"),
            image = channel.childNs(ITUNES, "image")?.getAttribute("href")?.takeIf { it.isNotBlank() },
            episodes = episodes,
        )
    }

    private fun episode(it: Element): Episode {
        val notes = it.textNs(CONTENT, "encoded") ?: it.text("description").orEmpty()
        val enc = it.child("enclosure")
        val link = it.text("link")
        val notesText = Html.toText(notes)
        return Episode(
            guid = it.text("guid") ?: enc?.getAttribute("url").orEmpty(),
            title = it.text("title").orEmpty().trim(),
            season = it.textNs(ITUNES, "season")?.trim()?.toIntOrNull(),
            number = it.textNs(ITUNES, "episode")?.trim()?.toIntOrNull(),
            publishedAt = it.text("pubDate")?.let { d -> runCatching { ZonedDateTime.parse(d.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull() },
            durationSeconds = it.textNs(ITUNES, "duration")?.let(::seconds),
            audioUrl = enc?.getAttribute("url")?.takeIf { u -> u.isNotBlank() },
            audioBytes = enc?.getAttribute("length")?.toLongOrNull()?.takeIf { n -> n > 0 },
            image = it.childNs(ITUNES, "image")?.getAttribute("href")?.takeIf { h -> h.isNotBlank() },
            sketchUid = link?.let(::sketchUid),
            notesHtml = notes,
            notesText = notesText,
            chapters = chapters(notesText),
        )
    }

    /** The feed's `<link>` points at the sketch the episode is about: `sketchplanations.com/<uid>`. */
    private fun sketchUid(link: String): String? = (Links.classify(link) as? LinkTarget.Sketch)?.uid
        ?.takeIf { runCatching { URI(link).host }.getOrNull()?.removePrefix("www.") == "sketchplanations.com" }

    private val chapterLine = Regex("""^\s*[(\[]?(?:(\d{1,2}):)?(\d{1,2}):(\d{2})[)\]]?\s*[-–—:]?\s*(.+)$""")

    /**
     * Timestamps written into the show notes, one per line. Fewer than two hits isn't a chapter
     * list, so nothing is returned rather than something half-right.
     */
    fun chapters(text: String): List<Chapter> {
        val found = text.lines().mapNotNull { line ->
            val m = chapterLine.find(line) ?: return@mapNotNull null
            val (h, mm, ss, title) = m.destructured
            Chapter((h.toIntOrNull() ?: 0) * 3600 + mm.toInt() * 60 + ss.toInt(), title.trim())
        }
        return if (found.size >= 2) found.sortedBy { it.atSeconds } else emptyList()
    }

    fun seconds(d: String): Int? {
        val parts = d.trim().split(':').map { it.toIntOrNull() ?: return null }
        return parts.fold(0) { acc, p -> acc * 60 + p }
    }

    private fun Element.children(name: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.localName == name && it.namespaceURI.isNullOrEmpty() }

    private fun Element.child(name: String) = children(name).firstOrNull()
    private fun Element.text(name: String): String? = child(name)?.textContent

    private fun Element.childNs(ns: String, name: String): Element? =
        (0 until childNodes.length).map { childNodes.item(it) }.firstOrNull { it.nodeType == Node.ELEMENT_NODE && it.namespaceURI == ns && it.localName == name } as? Element

    private fun Element.textNs(ns: String, name: String): String? = childNs(ns, name)?.textContent
}

/** Just enough HTML-to-text for show notes: line breaks kept, tags dropped, entities decoded. */
internal object Html {
    private val breaks = Regex("""<\s*(br|/p|/li|/div|/h\d)[^>]*>""", RegexOption.IGNORE_CASE)
    private val tags = Regex("<[^>]+>")
    private val entities = mapOf("&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'", "&nbsp;" to " ")

    fun toText(html: String): String {
        var t = tags.replace(breaks.replace(html, "\n"), "")
        entities.forEach { (k, v) -> t = t.replace(k, v) }
        t = Regex("&#(\\d+);").replace(t) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
        return t.lines().joinToString("\n") { it.trimEnd() }.replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
