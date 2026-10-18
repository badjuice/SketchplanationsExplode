package org.rdcl.nox.sketchplanations.explode

import java.net.URI

/** Sorts an outbound URL into what a reader should do with it. */
public object Links {
    private val books = listOf("geni.us", "amazon.", "amzn.", "bookshop.org", "audible.")
    private val papers = listOf("doi.org", "arxiv", ".edu", "pubmed", "ncbi.nlm", "springer", "jstor", "nature.com", "sciencedirect", "wiley.com", "ssrn")
    private val videos = listOf("youtube.", "youtu.be", "vimeo.", "ted.com")

    public fun classify(url: String): LinkTarget {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return LinkTarget.External(url, LinkKind.Web)
        val host = (uri.host ?: "").lowercase().removePrefix("www.")
        val path = (uri.rawPath ?: "").trim('/')
        return when {
            host == "podcast.sketchplanations.com" -> LinkTarget.Episode(url)
            host == "images.prismic.io" -> LinkTarget.ImageVersion(url)
            host == "sketchplanations.com" -> site(url, path)
            books.any { it in host } -> LinkTarget.External(url, LinkKind.Book)
            papers.any { it in host } -> LinkTarget.External(url, LinkKind.Paper)
            videos.any { it in host } -> LinkTarget.External(url, LinkKind.Video)
            else -> LinkTarget.External(url, LinkKind.Web)
        }
    }

    /** True for links that earn the site a commission and must be badged as affiliate. */
    public fun isAffiliate(url: String): Boolean {
        val host = runCatching { URI(url.trim()).host }.getOrNull()?.lowercase() ?: return false
        return host.endsWith("geni.us") || host.contains("amazon.") || host.contains("amzn.")
    }

    // A single path segment is a sketch, unless it's one of the site's own pages.
    private val sitePages = setOf(
        "", "about", "archive", "list", "search", "categories", "licence", "license", "podcast", "privacy",
        "thanks", "wisdom", "subscribe", "big-ideas-little-pictures", "prints", "random", "explore",
    )

    private fun site(url: String, path: String): LinkTarget {
        val parts = path.split('/').filter { it.isNotEmpty() }
        return when {
            parts.size == 2 && parts[0] == "categories" -> LinkTarget.Topic(parts[1])
            parts.size == 1 && parts[0] !in sitePages && !parts[0].startsWith("api") -> LinkTarget.Sketch(parts[0])
            else -> LinkTarget.External(url, LinkKind.Site)
        }
    }
}
