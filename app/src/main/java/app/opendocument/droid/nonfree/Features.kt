package app.opendocument.droid.nonfree

import app.opendocument.droid.BuildConfig

/**
 * What this build links and what it sells, asked by name rather than by flavor.
 *
 * [withAds] comes from [LINKS_ADS], which the `ads` and `noAds` source sets define next to the
 * classes it describes, so the flag and the code it stands for cannot disagree.
 */
object Features {

    /**
     * The ad banner, the consent form and the ad removal purchase: lite, and neither of the rest.
     */
    val withAds = LINKS_ADS

    /**
     * The edits that reach past one paragraph, formatting, and marking up a pdf: pro and foss. The
     * other edits are in every build. Declared per flavor in `app/build.gradle`, because no library
     * stands behind it.
     */
    val withAdvancedEditing = BuildConfig.ADVANCED_EDITING
}
