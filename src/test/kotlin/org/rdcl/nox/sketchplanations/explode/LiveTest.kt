package org.rdcl.nox.sketchplanations.explode

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Talks to the real site. Off by default; run with `EXPLODE_LIVE=1 ./gradlew test --tests '*LiveTest*' -i`.
 * About a dozen requests, one at a time.
 */
class LiveTest {
    private val live = System.getenv("EXPLODE_LIVE") == "1"
    private val client = SketchplanationsClient(userAgent = "SketchplanationsExplode-test/0.1 (+https://github.com/badjuice/SketchplanationsExplode)")

    @Test fun `the real archive parses`() = runBlocking {
        if (!live) return@runBlocking
        val all = client.allSketches()
        val topics = client.topics()
        val podcast = client.podcast()
        val unknown = all.flatMap { it.body }.filterIsInstance<Block.Unknown>().groupingBy { it.type }.eachCount()
        val known = all.map { it.uid }.toSet()
        val dangling = all.flatMap { it.related }.count { it !in known }
        println(
            """
            sketches ${all.size}, topics ${topics.size}, episodes ${podcast.episodes.size}
            with related ${all.count { it.related.isNotEmpty() }} (avg ${"%.1f".format(all.map { it.related.size }.average())}), dangling related $dangling
            with versions ${all.count { it.versions.isNotEmpty() }}, animated ${all.count { it.image.isAnimated }}, standfirst ${all.count { it.standfirst != null }}
            unknown blocks $unknown
            episodes matched to a sketch ${podcast.episodes.count { it.sketchUid in known }}, with chapters ${podcast.episodes.count { it.chapters.isNotEmpty() }}
            link kinds ${all.flatMap { it.links }.groupingBy { it.target::class.simpleName + ((it.target as? LinkTarget.External)?.kind?.let { k -> "/$k" } ?: "") }.eachCount()}
            """.trimIndent(),
        )
        assertTrue(all.size > 900)
        assertTrue(topics.size > 100)
        assertTrue(podcast.episodes.isNotEmpty())
    }
}
