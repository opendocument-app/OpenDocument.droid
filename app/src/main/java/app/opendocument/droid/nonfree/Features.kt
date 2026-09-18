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

    /**
     * Every edit the core takes: pro and foss. Lite edits the text of a document inside one
     * paragraph, and the rest is what pro is sold on.
     */
    val advancedEditing = ADVANCED_EDITING

    /**
     * Whether this build lets the user into the edit mode for [kind]. The core answers whether the
     * document can be edited at all; this is the edition's policy on top of it, and the one list of
     * editing there is.
     */
    fun offersEditing(kind: EditingKind): Boolean =
        kind == EditingKind.DOCUMENT || (kind.isEditable && advancedEditing)
}
