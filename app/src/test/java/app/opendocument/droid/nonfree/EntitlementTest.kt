package app.opendocument.droid.nonfree

import org.junit.Assert.assertEquals
import org.junit.Test

class EntitlementTest {

    @Test
    fun nothingOwnedGivesNothing() {
        assertEquals(Entitlement(false, false, false), Entitlement.of(emptyList(), emptyList()))
    }

    @Test
    fun passGivesProUntilPlayStopsReportingIt() {
        assertEquals(
            Entitlement(adFree = false, lifetimePro = false, pass = true),
            Entitlement.of(emptyList(), listOf(Entitlement.PASS_PRODUCT)),
        )
    }

    @Test
    fun lifetimeAdRemovalGivesPro() {
        for (product in listOf("remove_ads_for_eva", "love_and_everything")) {
            assertEquals(
                Entitlement(adFree = true, lifetimePro = true, pass = false),
                Entitlement.of(listOf(product), emptyList()),
            )
        }
    }

    @Test
    fun oneYearAdRemovalKeepsOnlyTheAdRemoval() {
        assertEquals(
            Entitlement(adFree = true, lifetimePro = false, pass = false),
            Entitlement.of(listOf("remove_ads_for_1y"), emptyList()),
        )
    }

    @Test
    fun oldSubscriptionKeepsOnlyTheAdRemoval() {
        assertEquals(
            Entitlement(adFree = true, lifetimePro = false, pass = false),
            Entitlement.of(emptyList(), listOf("remove_ads_subscription")),
        )
    }
}
