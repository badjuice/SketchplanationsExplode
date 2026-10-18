<p align="center"><img src="docs/logo.png" width="320" alt="SketchplanationsExplode logo"></p>

# SketchplanationsExplode

A small Kotlin library for reading [sketchplanations.com](https://sketchplanations.com) and its podcast:
every sketch with its words, tags, related ideas and image renditions, plus the podcast feed with chapters.
It powers the [Sketchplanations reader](https://github.com/badjuice/sketchplanations-reader) for Android, and
runs on any JVM.

Unofficial and not affiliated with Sketchplanations or Jono Hey.

```kotlin
val client = SketchplanationsClient(userAgent = "MyReader/1.0 (+https://example.org)")

val sketches = client.allSketches { done, total -> println("$done / $total") }  // ~10 requests
val edited   = client.sketchesChangedSince(lastSync)                           // new *and* edited posts
val topics   = client.topics()
val podcast  = client.podcast()

Images.url(sketch.image, Rendition.Read)   // imgix URL, no network
Numbering.ordinals(sketches)               // uid → #n in publication order
```

## What it gives you

| | |
| --- | --- |
| `Sketch` | title, dates (published, first and last publication), tags, image, standfirst when there is one |
| `body` | paragraphs, headings, bullet and numbered lists, preformatted text, with bold, italic and links as ranges |
| `links` | every link, sorted into another sketch, a topic, a podcast episode, an alternate image, or external (book, paper, video, site page, web) |
| `related` | the uids from the post's "Related ideas" section and "Also see" lines, plus where that section starts |
| `versions` | alternate renderings the post links to: metric, US English, the original… |
| `Episode` | season and number (numbers restart each season), audio URL and size, artwork, notes, chapters, and the sketch it discusses |

## Things the data taught us

- **Most posts are edited after they go out**, so refresh by last-publication date, not only new arrivals.
- **There is no sketch number.** About one image filename in five carries the author's `SP<n>`; the only
  complete sequence is publication order.
- **Animated GIFs** must be fetched untouched; imgix's format conversion flattens them to one frame.
  `Images.url` does this for you.
- **Podcast chapters** live in the show notes and only some episodes have them. Ads are stitched in per
  request, so check `Episode.chaptersFor(actualSeconds)` against the audio you actually downloaded.
- **Book links** to Amazon and geni.us are affiliate links; `Links.isAffiliate` tells you which to badge.

## Being polite

Requests go out one at a time, carry your User-Agent, back off on 429 and 5xx, and reuse the Prismic ref
until it expires. A full archive fetch is about ten requests. Please keep it that way: fetch once, store
locally, refresh incrementally.

## Content licence

This library contains no content. What it fetches belongs to its authors:

- Sketches and their words are © Jono Hey under [CC BY-NC 4.0](https://sketchplanations.com/licence).
  Credit "Image: Jono Hey, Sketchplanations" with a link to the sketch, never sell or monetise it, and
  don't remove the Sketchplanations mark from the images.
- The podcast is © Sketchplanations - The Podcast, with no open licence. Stream and download it from the
  public feed as any podcast player does; don't re-host it.

## Build

```
./gradlew test                                        # offline tests on made-up fixtures
EXPLODE_LIVE=1 ./gradlew test --tests '*LiveTest*'    # one pass against the real site
```

JDK 17+. Code licensed GPL-3.0, see `LICENSE`. © 2026 BadJuice ([github.com/badjuice](https://github.com/badjuice)).
