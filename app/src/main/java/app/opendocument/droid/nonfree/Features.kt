package app.opendocument.droid.nonfree

/**
 * What this build links, asked by name rather than by flavor.
 *
 * The answer comes from [LINKS_ADS], which the `ads` and `noAds` source sets define next to the
 * classes it describes, so the flag and the code it stands for cannot disagree.
 */
object Features {

    /**
     * The ad banner, the consent form and the ad removal purchase: lite, and neither of the rest.
     */
    val withAds = LINKS_ADS
}
