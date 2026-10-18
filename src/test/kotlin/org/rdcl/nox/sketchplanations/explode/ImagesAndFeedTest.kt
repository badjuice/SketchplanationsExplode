package org.rdcl.nox.sketchplanations.explode

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImagesAndFeedTest {
    private val png = SketchImage("https://images.prismic.io/s/a_SP1-x.png?auto=compress,format&rect=0,0,10,10", null, 10, 10)

    @Test fun `renditions keep the editor crop and set the size`() {
        val u = Images.url(png, Rendition.Thumb)
        assertTrue(u.startsWith("https://images.prismic.io/s/a_SP1-x.png?auto=format,compress&rect=0,0,10,10&w=240"), u)
        assertTrue("ar=5:4" in u && "crop=top" in u, u)
    }

    @Test fun `animated gifs are never converted`() {
        val gif = png.copy(url = "https://images.prismic.io/s/spin.gif?auto=compress")
        assertTrue(gif.isAnimated)
        assertEquals("https://images.prismic.io/s/spin.gif", Images.url(gif, Rendition.Read))
    }

    private val podcast = Feed.parse(resource("feed.xml").toByteArray())

    @Test fun `channel and episodes`() {
        assertEquals("Example Podcast", podcast.title)
        assertEquals("Example Author", podcast.author)
        assertEquals(2, podcast.episodes.size)
        val e = podcast.episodes[0]
        assertEquals(5, e.season)
        assertEquals(17, e.number)
        assertEquals(45 * 60 + 30, e.durationSeconds)
        assertEquals(36769500L, e.audioBytes)
        assertEquals("the-example-effect", e.sketchUid)
        assertEquals(Instant.parse("2026-06-02T04:00:00Z"), e.publishedAt)
        assertTrue(e.notesText.startsWith("Show notes & more."))
        assertNull(podcast.episodes[1].sketchUid)
        assertEquals(2400, podcast.episodes[1].durationSeconds)
    }

    @Test fun `chapters are parsed and need at least two`() {
        val c = podcast.episodes[0].chapters
        assertEquals(listOf(0, 195, 3723), c.map { it.atSeconds })
        assertEquals("The idea", c[1].title)
        assertTrue(podcast.episodes[1].chapters.isEmpty())
    }

    @Test fun `chapters are dropped when ad insertion changed the length`() {
        val e = podcast.episodes[0]
        assertEquals(3, e.chaptersFor(4000).size)
        assertTrue(e.chaptersFor(3000).isEmpty())
    }
}
