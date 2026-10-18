package org.rdcl.nox.sketchplanations.explode

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentsTest {
    private val sketch = Documents.sketch(Json.parseToJsonElement(resource("sketch.json")).jsonObject)!!

    @Test fun `basic fields`() {
        assertEquals("the-example-effect", sketch.uid)
        assertEquals("The Example Effect", sketch.title)
        assertEquals(Instant.parse("2026-01-04T11:00:00Z"), sketch.publishedAt)
        assertEquals(Instant.parse("2026-03-04T12:30:00Z"), sketch.lastPublishedAt)
        assertEquals(listOf("thinking", "work"), sketch.tags.map { it.uid })
        assertEquals(123, sketch.imageNumber)
        assertEquals("https://podcast.sketchplanations.com/the-example-effect/", sketch.podcastUrl)
        assertNull(sketch.printsUrl)
        assertEquals("A short italic opener.", sketch.standfirst)
    }

    @Test fun `lists are grouped and block types survive`() {
        val types = sketch.body.map { it::class.simpleName }
        assertEquals(
            listOf("Paragraph", "Paragraph", "Heading", "ListBlock", "ListBlock", "Preformatted", "Paragraph", "Heading", "ListBlock", "Unknown"),
            types,
        )
        val bullets = sketch.body[3] as Block.ListBlock
        assertEquals(false, bullets.ordered)
        assertEquals(listOf("One", "Two"), bullets.items.map { it.text })
        assertEquals(true, (sketch.body[4] as Block.ListBlock).ordered)
    }

    @Test fun `links are classified`() {
        val targets = sketch.links.map { it.target }
        assertIs<LinkTarget.ImageVersion>(targets[0])
        assertEquals(LinkKind.Book, (targets[1] as LinkTarget.External).kind)
        assertTrue(Links.isAffiliate((targets[1] as LinkTarget.External).url))
        assertEquals(LinkTarget.Sketch("other-idea"), targets[2])
        assertEquals(LinkTarget.Sketch("third-idea"), targets[3])
        assertEquals(LinkKind.Site, (targets.last() as LinkTarget.External).kind)
    }

    @Test fun `related ideas come from the section and also-see lines`() {
        assertEquals(listOf("other-idea", "third-idea"), sketch.related)
        assertEquals(7, sketch.relatedSectionStart)
    }

    @Test fun `alternate images become versions`() {
        assertEquals(1, sketch.versions.size)
        assertEquals("metric version", sketch.versions[0].label)
    }

    @Test fun `numbering follows publication order`() {
        val older = sketch.copy(uid = "older", id = "x", publishedAt = Instant.parse("2020-01-01T00:00:00Z"))
        assertEquals(mapOf("older" to 1, "the-example-effect" to 2), Numbering.ordinals(listOf(sketch, older)))
    }

    @Test fun `raw round-trips offline`() {
        assertEquals(sketch, Sketches.parse(sketch.raw))
        assertNull(Sketches.parse("not json"))
    }

    @Test fun `a document without an image is skipped`() {
        val doc = Json.parseToJsonElement("""{"id":"x","uid":"y","data":{"title":"No image"}}""").jsonObject
        assertNull(Documents.sketch(doc))
    }

    @Test fun `topic display names come from the identifier`() {
        val t = Documents.topic(Json.parseToJsonElement("""{"id":"T1","uid":"food-and-drink","data":{"identifier":"food and drink"}}""").jsonObject)
        assertNotNull(t)
        assertEquals("food and drink", t.name)
    }
}

internal fun resource(name: String): String =
    DocumentsTest::class.java.classLoader.getResource(name)!!.readText()
