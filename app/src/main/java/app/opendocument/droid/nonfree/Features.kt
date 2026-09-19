package app.opendocument.droid.nonfree

import app.opendocument.droid.background.EditingKind

/**
 * What this build links and what it sells, asked by name rather than by flavor.
 *
 * Both flags come from `Linked.kt`, which the `ads` and `noAds` source sets define next to the
 * classes [withAds] describes, so a flag and the code it stands for cannot disagree.
 */
object Features {

    /**
     * The ad banner, the consent form and the ad removal purchase: lite, and neither of the rest.
     */
    val withAds = LINKS_ADS

    /** Formatting, new and joined paragraphs, and pdf marks: pro and foss. */
    val advancedEditing = ADVANCED_EDITING

    /** Whether this build opens the edit mode for [kind], which the core decided. */
    fun offersEditing(kind: EditingKind): Boolean =
        kind.isEditable && (kind != EditingKind.ANNOTATION || advancedEditing)
}
