package org.rdcl.nox.sketchplanations.explode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant

/** Thrown when the network is unreachable; callers should fall back to what's stored locally. */
public class OfflineException(cause: Throwable) : IOException("Can't reach the network", cause)

/** Anything else that went wrong talking to the site or the feed. */
public class ExplodeException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Reads sketchplanations.com's public Prismic content API and the podcast's Acast feed.
 *
 * Polite by design: requests go out one at a time, identify themselves, back off on 429/5xx, and
 * the Prismic ref is fetched once and reused until it expires.
 */
public class SketchplanationsClient(
    private val http: OkHttpClient = OkHttpClient(),
    /** Identify the app, with a contact, e.g. `MyReader/1.0 (+https://example.org)`. */
    private val userAgent: String,
    private val prismic: HttpUrl = "https://sketchplanations.cdn.prismic.io/api/v2".toHttpUrl(),
    private val feed: HttpUrl = "https://feeds.acast.com/public/shows/63dbe0223c0b1d0011855397".toHttpUrl(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private var ref: String? = null

    // ---- sketches ---------------------------------------------------------------------------

    /**
     * Every sketch, newest first, bodies included — about ten requests for the whole archive.
     * [progress] receives (fetched, total) after each page.
     */
    public suspend fun allSketches(progress: (Int, Int) -> Unit = { _, _ -> }): List<Sketch> =
        search("""[[at(document.type,"sketchplanation")]]""", "[my.sketchplanation.published_at desc]", progress)
            .mapNotNull(Documents::sketch)

    /**
     * Sketches published *or edited* after [since] — most posts get edited after they go out, so
     * an incremental refresh must look at edit dates, not just new arrivals.
     */
    public suspend fun sketchesChangedSince(since: Instant): List<Sketch> =
        search(
            """[[at(document.type,"sketchplanation")][date.after(document.last_publication_date,${since.toEpochMilli()})]]""",
            "[document.last_publication_date desc]",
        ).mapNotNull(Documents::sketch)

    public suspend fun sketch(uid: String): Sketch? =
        search("""[[at(my.sketchplanation.uid,"${uid.replace("\"", "")}")]]""", null).firstOrNull()?.let(Documents::sketch)

    /** Ids of every sketch currently published, to notice ones that were taken down. */
    public suspend fun allSketchIds(): Set<String> =
        search("""[[at(document.type,"sketchplanation")]]""", null, fields = "sketchplanation.title")
            .mapNotNull { it["id"]?.jsonPrimitive?.content }.toSet()

    public suspend fun topics(): List<Topic> =
        search("""[[at(document.type,"tag")]]""", null).mapNotNull(Documents::topic)

    // ---- podcast ----------------------------------------------------------------------------

    public suspend fun podcast(): Podcast = Feed.parse(podcastFeed())

    /** The raw feed, for storing and re-parsing offline with [PodcastFeed.parse]. */
    public suspend fun podcastFeed(): ByteArray = get(feed, "application/rss+xml, application/xml")

    // ---- plumbing ---------------------------------------------------------------------------

    private suspend fun search(
        q: String, orderings: String?, progress: (Int, Int) -> Unit = { _, _ -> }, fields: String? = null,
    ): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        var page = 1
        while (true) {
            val body = withRef { r ->
                val url = prismic.newBuilder().addPathSegments("documents/search")
                    .addQueryParameter("ref", r).addQueryParameter("q", q)
                    .addQueryParameter("pageSize", "100").addQueryParameter("page", page.toString())
                    .apply {
                        orderings?.let { addQueryParameter("orderings", it) }
                        fields?.let { addQueryParameter("fetch", it) }
                    }.build()
                json.parseToJsonElement(String(get(url, "application/json"))).jsonObject
            }
            body["results"]?.jsonArray?.forEach { (it as? JsonObject)?.let(out::add) }
            val total = body["total_results_size"]?.jsonPrimitive?.int ?: out.size
            progress(out.size, total)
            val pages = body["total_pages"]?.jsonPrimitive?.int ?: 1
            if (page >= pages) return out
            page++
        }
    }

    /** Runs [block] with the master ref, refetching it once if Prismic says it has expired. */
    private suspend fun <T> withRef(block: suspend (String) -> T): T {
        val r = ref ?: masterRef()
        return try {
            block(r)
        } catch (e: HttpStatus) {
            if (e.code != 404 && e.code != 410) throw e.wrap()
            block(masterRef())
        }
    }

    private suspend fun masterRef(): String {
        val api = json.parseToJsonElement(String(get(prismic, "application/json"))).jsonObject
        val r = api["refs"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { it["isMasterRef"]?.jsonPrimitive?.content == "true" }
            ?.get("ref")?.jsonPrimitive?.content ?: throw ExplodeException("Prismic returned no master ref")
        ref = r
        return r
    }

    private class HttpStatus(val code: Int, val url: HttpUrl) : IOException("HTTP $code for $url") {
        fun wrap() = ExplodeException(message!!, this)
    }

    private suspend fun get(url: HttpUrl, accept: String): ByteArray = lock.withLock {
        var attempt = 0
        while (true) {
            try {
                return@withLock withContext(Dispatchers.IO) {
                    val req = Request.Builder().url(url).header("User-Agent", userAgent).header("Accept", accept).build()
                    http.newCall(req).execute().use { res ->
                        if (!res.isSuccessful) throw HttpStatus(res.code, url)
                        res.body?.bytes() ?: ByteArray(0)
                    }
                }
            } catch (e: HttpStatus) {
                if (e.code != 429 && e.code < 500 || attempt >= 4) throw e
            } catch (e: IOException) {
                if (attempt >= 2) throw OfflineException(e)
            }
            delay(500L shl attempt)
            attempt++
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }
}
