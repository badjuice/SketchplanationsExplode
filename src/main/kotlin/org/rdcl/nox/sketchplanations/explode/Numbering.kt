package org.rdcl.nox.sketchplanations.explode

/**
 * The site stores no sketch number. Most image filenames don't carry one either (about one in five
 * does), so the only complete sequence is publication order: the first sketch is #1.
 */
public object Numbering {
    /** uid → position in publication order, starting at 1. */
    public fun ordinals(sketches: Collection<Sketch>): Map<String, Int> =
        sketches.sortedWith(compareBy<Sketch> { it.publishedAt }.thenBy { it.firstPublishedAt }.thenBy { it.id })
            .mapIndexed { i, s -> s.uid to i + 1 }.toMap()
}
