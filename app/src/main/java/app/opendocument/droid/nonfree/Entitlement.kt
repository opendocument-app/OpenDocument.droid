package app.opendocument.droid.nonfree

/**
 * What the products that play reports as owned give lite. Only the yearly pass is sold; the rest
 * are the ad removals of 2012 to 2023, which their buyers keep.
 */
data class Entitlement(
    /** No ads, and never taken back: any of the old purchases. */
    val adFree: Boolean,
    /** The pro features, and never taken back: an old purchase that was for life. */
    val lifetimePro: Boolean,
    /** The pro features without ads, for as long as play reports the subscription. */
    val pass: Boolean,
) {

    companion object {

        /** The subscription lite sells. Its one base plan renews every year. */
        const val PASS_PRODUCT = "pro_pass"

        // the old one-time products that were for life. remove_ads_for_1y was one-time too, but
        // bought one year, so it keeps the ad removal and gets nothing more
        private val LIFETIME_PRODUCTS = setOf("remove_ads_for_eva", "love_and_everything")

        fun of(inApp: Collection<String>, subscriptions: Collection<String>): Entitlement =
            Entitlement(
                adFree = inApp.isNotEmpty() || subscriptions.any { it != PASS_PRODUCT },
                lifetimePro = inApp.any { it in LIFETIME_PRODUCTS },
                pass = PASS_PRODUCT in subscriptions,
            )
    }
}
