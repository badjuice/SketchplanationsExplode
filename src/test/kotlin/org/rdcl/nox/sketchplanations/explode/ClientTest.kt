package org.rdcl.nox.sketchplanations.explode

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClientTest {
    private val server = MockWebServer()
    private val sketch = resource("sketch.json")
    private var ref = "ref1"
    private var expireOnce = false
    private val requests = mutableListOf<String>()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path!!
                requests += path
                val url = request.requestUrl!!
                return when {
                    url.encodedPath.endsWith("/api/v2") -> json("""{"refs":[{"id":"master","ref":"$ref","isMasterRef":true}]}""")
                    url.encodedPath.endsWith("/documents/search") -> {
                        if (expireOnce && url.queryParameter("ref") == "ref1") { expireOnce = false; ref = "ref2"; return MockResponse().setResponseCode(410) }
                        val page = url.queryParameter("page")!!.toInt()
                        json("""{"page":$page,"total_pages":2,"total_results_size":2,"results":[$sketch]}""")
                    }
                    url.encodedPath.endsWith("/feed") -> MockResponse().setBody(resource("feed.xml"))
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @AfterTest fun stop() = server.shutdown()

    private fun json(s: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(s)

    private fun client() = SketchplanationsClient(
        userAgent = "test/1.0", prismic = server.url("/api/v2"), feed = server.url("/feed"),
    )

    @Test fun `pages through every result and reports progress`() = runTest {
        val seen = mutableListOf<Pair<Int, Int>>()
        val all = client().allSketches { n, total -> seen += n to total }
        assertEquals(2, all.size)
        assertEquals(listOf(1 to 2, 2 to 2), seen)
        assertEquals(1, requests.count { it.startsWith("/api/v2?") || it == "/api/v2" }, "the ref is fetched once")
        assertTrue(requests.all { "ref=ref1" in it || !it.contains("search") })
    }

    @Test fun `an expired ref is refetched once`() = runTest {
        val c = client()
        c.allSketches()
        expireOnce = true
        assertEquals(2, c.allSketches().size)
        assertTrue(requests.any { "ref=ref2" in it })
    }

    @Test fun `incremental refresh asks for edits, not just new posts`() = runTest {
        client().sketchesChangedSince(Instant.ofEpochMilli(1_700_000_000_000))
        val q = requests.first { "search" in it }
        assertTrue("last_publication_date" in java.net.URLDecoder.decode(q, "UTF-8") && "1700000000000" in q, q)
    }

    @Test fun `podcast feed`() = runTest {
        assertEquals(2, client().podcast().episodes.size)
    }

    @Test fun `no network surfaces as OfflineException`() = runTest {
        val c = client()
        server.shutdown()
        assertFailsWith<OfflineException> { c.allSketches() }
    }
}
