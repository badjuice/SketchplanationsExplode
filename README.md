<p align="center"><img src="docs/logo.png" width="320" alt="SketchplanationsExplode logo"></p>

# SketchplanationsExplode

Unofficial Kotlin client for [sketchplanations.com](https://sketchplanations.com) and its podcast feed, used by
the [Sketchplanations reader](https://github.com/badjuice/sketchplanations-reader).

```kotlin
val client = SketchplanationsClient(userAgent = "MyReader/1.0 (+https://example.org)")
val sketches = client.allSketches()
val edited = client.sketchesChangedSince(lastSync)
val podcast = client.podcast()
```

```
./gradlew test
EXPLODE_LIVE=1 ./gradlew test --tests '*LiveTest*'
```

Code GPL-3.0, © 2026 [BadJuice](https://github.com/badjuice). Sketches © Jono Hey,
[CC BY-NC 4.0](https://sketchplanations.com/licence); podcast © Sketchplanations - The Podcast.
