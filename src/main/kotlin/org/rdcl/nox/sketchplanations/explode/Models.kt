package org.rdcl.nox.sketchplanations.explode

import java.time.Instant

/** One sketchplanation: the drawing, its words, and everything the words link to. */
public data class Sketch(
    /** Prismic document id. Stable across edits. */
    val id: String,
    /** URL slug: the sketch lives at `https://sketchplanations.com/<uid>`. */
    val uid: String,
    val title: String,
    /** The date the author shows on the site and orders by. */
    val publishedAt: Instant,
    val firstPublishedAt: Instant,
    /** Changes on every edit; most posts are edited after they first go out. */
    val lastPublishedAt: Instant,
    val tags: List<TagRef>,
    val image: SketchImage,
    /** A one-line italic opener, when the post has one (only a handful do). */
    val standfirst: String?,
    val body: List<Block>,
    /** Every link in the body, in reading order. */
    val links: List<Link>,
    /** Uids from the post's "Related ideas" section and "Also see" lines, in order, without duplicates. */
    val related: List<String>,
    /** Index into [body] where the "Related ideas" section starts, so a reader can show it as cards instead. */
    val relatedSectionStart: Int?,
    /** Alternate renderings the post links to: metric, US English, the original, a static version… */
    val versions: List<ImageVersion>,
    val podcastUrl: String?,
    val printsUrl: String?,
    /**
     * The source document as JSON. Store this rather than the parsed model: [Sketches.parse] turns it
     * back into a [Sketch] offline, and a newer version of this library can re-read old data.
     */
    val raw: String,
) {
    /** The author's own sequence number, when the image filename carries one (`…_SP981-…`). Often absent. */
    val imageNumber: Int? get() = image.number
    val url: String get() = "https://sketchplanations.com/$uid"
}

public data class TagRef(val id: String, val uid: String)

/** A category. Sketches link to tag documents; the display name lives on the tag. */
public data class Topic(val id: String, val uid: String, val name: String)

public data class SketchImage(
    /** As stored in Prismic, including any crop the editor applied. */
    val url: String,
    val alt: String?,
    val width: Int?,
    val height: Int?,
) {
    /** The untouched upload, which is what the site's own high-resolution download serves. */
    val original: String get() = url.substringBefore('?')
    /** Animated GIFs must be fetched as-is: format conversion flattens them to one frame. */
    val isAnimated: Boolean get() = original.lowercase().endsWith(".gif")
    val number: Int? get() = NUMBER.find(java.net.URLDecoder.decode(original, "UTF-8"))?.groupValues?.get(1)?.toIntOrNull()

    private companion object {
        val NUMBER = Regex("_SP(\\d+)[-_.]")
    }
}

public data class ImageVersion(val label: String, val url: String)

// ---- rich text ------------------------------------------------------------------------------

/** Text with styled ranges. Offsets are UTF-16 indices into [text], as Prismic sends them. */
public data class RichText(val text: String, val spans: List<Span>) {
    public val isEmpty: Boolean get() = text.isBlank()
}

public data class Span(val start: Int, val end: Int, val style: SpanStyle)

public sealed interface SpanStyle {
    public data object Strong : SpanStyle
    public data object Em : SpanStyle
    public data class Hyperlink(val link: Link) : SpanStyle
}

public sealed interface Block {
    public data class Paragraph(val content: RichText) : Block
    public data class Heading(val level: Int, val content: RichText) : Block
    /** Consecutive list items grouped into one list. */
    public data class ListBlock(val ordered: Boolean, val items: List<RichText>) : Block
    /** Monospaced, whitespace preserved. */
    public data class Preformatted(val content: RichText) : Block
    public data class Image(val image: SketchImage) : Block
    /** A block type this library doesn't model yet. Readers should skip it. */
    public data class Unknown(val type: String, val text: String) : Block
}

// ---- links ----------------------------------------------------------------------------------

public data class Link(val label: String, val target: LinkTarget)

public sealed interface LinkTarget {
    /** Another sketchplanation. Open it in the reader. */
    public data class Sketch(val uid: String) : LinkTarget
    /** A category page on the site. */
    public data class Topic(val uid: String) : LinkTarget
    /** An episode page on the podcast site. */
    public data class Episode(val url: String) : LinkTarget
    /** A different rendering of the sketch's own image. */
    public data class ImageVersion(val url: String) : LinkTarget
    /** Anywhere else. */
    public data class External(val url: String, val kind: LinkKind) : LinkTarget
}

public enum class LinkKind {
    /** A book link. Amazon and geni.us links are affiliate links and must be badged as such. */
    Book,
    Paper,
    Video,
    /** Any other page on sketchplanations.com, such as the book's page. */
    Site,
    Web,
}

// ---- podcast --------------------------------------------------------------------------------

public data class Podcast(val title: String, val author: String?, val copyright: String?, val image: String?, val episodes: List<Episode>)

public data class Episode(
    val guid: String,
    val title: String,
    val season: Int?,
    /** Episode numbers restart each season. */
    val number: Int?,
    val publishedAt: Instant?,
    /** As the feed reports it. Ad insertion means the audio you download can differ. */
    val durationSeconds: Int?,
    /**
     * A tracking redirect: resolve it at play or download time, never store it as an identity.
     */
    val audioUrl: String?,
    val audioBytes: Long?,
    val image: String?,
    /** The sketch this episode discusses, from the feed's own `<link>`. */
    val sketchUid: String?,
    val notesHtml: String,
    val notesText: String,
    /** Parsed from the notes. Check [chaptersFor] against the real audio length before seeking. */
    val chapters: List<Chapter>,
) {
    /**
     * Chapters valid for a downloaded file of [actualSeconds]. Ads are stitched in per request, so if
     * the last chapter would start beyond the audio, the offsets don't fit and none are returned.
     */
    public fun chaptersFor(actualSeconds: Int): List<Chapter> =
        if (chapters.isEmpty() || chapters.last().atSeconds < actualSeconds) chapters else emptyList()
}

public data class Chapter(val atSeconds: Int, val title: String)
