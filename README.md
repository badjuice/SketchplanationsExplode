<p align="center"><img src="docs/logo.png" width="320" alt="SketchplanationsExplode logo"></p>

# SketchplanationsExplode

Kotlin client for [sketchplanations.com](https://sketchplanations.com) content and its podcast feed. Used by
the [Sketchplanations reader](https://github.com/badjuice/sketchplanations-reader). Unofficial.

```kotlin
val client = SketchplanationsClient(userAgent = "MyReader/1.0 (+https://example.org)")
val sketches = client.allSketches()                 // about ten requests
val edited = client.sketchesChangedSince(lastSync)  // new and edited posts
val podcast = client.podcast()
Images.url(sketch.image, Rendition.Read)            // imgix URL, no network
```

Each `Sketch` has its rich text, tags, links (sorted by kind), related ideas, image versions and its source JSON
(`Sketches.parse(raw)` rebuilds it offline). Episodes carry season, number, chapters and the sketch they cover.

Requests run one at a time and back off on 429 and 5xx. Fetch once, store locally, refresh by edit date.

```
./gradlew test
EXPLODE_LIVE=1 ./gradlew test --tests '*LiveTest*'   # one pass against the real site
```

Code: GPL-3.0, © 2026 [BadJuice](https://github.com/badjuice). Content it fetches: sketches © Jono Hey,
[CC BY-NC 4.0](https://sketchplanations.com/licence); podcast © Sketchplanations - The Podcast.
