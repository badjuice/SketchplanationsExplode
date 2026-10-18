package org.rdcl.nox.sketchplanations.explode

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Offline parsing of stored source data, with no network access. */
public object Sketches {
    /** Rebuilds a [Sketch] from [Sketch.raw]. Returns null if [raw] isn't a sketch document. */
    public fun parse(raw: String): Sketch? =
        runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()?.let(Documents::sketch)
}

public object PodcastFeed {
    /** Parses feed XML as returned by [SketchplanationsClient.podcastFeed]. */
    public fun parse(xml: ByteArray): Podcast = Feed.parse(xml)
}
