package app.opendocument.droid.nonfree

import app.opendocument.droid.background.EditingKind

/**
 * What this build links and what it sells, asked by name rather than by flavor.
 *
 * The constants come from `Linked.kt` and `LinkedReview.kt`, which the `ads`/`noAds` and
 * `review`/`noReview` source sets define next to the classes they describe, so a flag and the code
 * it stands for cannot disagree.
 */
object Features {

    /** The ad banner, the consent form and the yearly pass: lite, and neither of the rest. */
    val withAds = LINKS_ADS

    /** Sold through play, with its review sheet: lite and pro, not foss. */
    val onPlay = ON_PLAY

    /**
     * Formatting, new and joined paragraphs, and pdf marks: pro and foss, and lite once it holds
     * the pass or an old purchase for life.
     */
    val advancedEditing: Boolean
        get() = ADVANCED_EDITING || unlocked

    /** Set by [BillingManager]. The loader's thread reads it through [advancedEditing] too. */
    @Volatile internal var unlocked = false

    /**
     * Whether asking for support stands out, in the reader and high on the landing screen: lite
     * sells the pass, and foss has nothing but the sponsors. Pro was bought already.
     */
    val promotesSupport = withAds || !onPlay

    /**
     * Whether this build opens the edit mode for [kind], which the core decided. Every edition
     * opens every kind it can: what lite does not sell is the individual tool, which carries pro's
     * badge in the strip, and one of each kind is free so that the mode is worth opening.
     */
    fun offersEditing(kind: EditingKind): Boolean = kind.isEditable
}
