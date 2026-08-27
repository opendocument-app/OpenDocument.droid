package app.opendocument.droid.background

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stored values and the clock are passed into [ReviewInvitation.isEarned], so every branch is
 * reachable without a device or a fortnight.
 */
class ReviewInvitationTest {

    @Test
    fun theFirstAskWaitsForFiveDocuments() {
        assertFalse(earned(documentOpens = 4))
        assertTrue(earned(documentOpens = 5))
    }

    @Test
    fun documentsAreCountedFromTheAskBefore() {
        // ten more after the ask, not ten in all
        assertFalse(earned(documentOpens = 14, asks = 1, askedAfterOpens = 5))
        assertTrue(earned(documentOpens = 15, asks = 1, askedAfterOpens = 5))
    }

    @Test
    fun theLadderEscalates() {
        // 5, then 10, 20, 50, 100 more
        assertFalse(earned(documentOpens = 19, asks = 2, askedAfterOpens = 0))
        assertTrue(earned(documentOpens = 20, asks = 2, askedAfterOpens = 0))
        assertFalse(earned(documentOpens = 49, asks = 3, askedAfterOpens = 0))
        assertTrue(earned(documentOpens = 50, asks = 3, askedAfterOpens = 0))
        assertFalse(earned(documentOpens = 99, asks = 4, askedAfterOpens = 0))
        assertTrue(earned(documentOpens = 100, asks = 4, askedAfterOpens = 0))
    }

    @Test
    fun theFifthAskIsTheLast() {
        assertFalse(earned(documentOpens = 1000, asks = 5))
        assertFalse(earned(documentOpens = 1000, asks = 6))
    }

    @Test
    fun twoWeeksHaveToPassBetweenAsks() {
        assertFalse(
            earned(
                documentOpens = 15,
                asks = 1,
                askedAfterOpens = 5,
                askedAtMillis = ASKED_AT,
                nowMillis = ASKED_AT + TWO_WEEKS - 1,
            )
        )
        assertTrue(
            earned(
                documentOpens = 15,
                asks = 1,
                askedAfterOpens = 5,
                askedAtMillis = ASKED_AT,
                nowMillis = ASKED_AT + TWO_WEEKS,
            )
        )
    }

    @Test
    fun aClockMovedBackwardsOnlyDelaysTheAsk() {
        assertFalse(
            earned(
                documentOpens = 15,
                asks = 1,
                askedAfterOpens = 5,
                askedAtMillis = ASKED_AT,
                nowMillis = ASKED_AT - TWO_WEEKS,
            )
        )
    }

    @Test
    fun anInstallThatWasNeverAskedHasNoRail() {
        // zero is "never asked", not 1970
        assertTrue(earned(documentOpens = 5, askedAtMillis = 0, nowMillis = ASKED_AT))
    }

    @Test
    fun documentsReadBeforeTheCountersExistedStillCount() {
        // only the document counter set, as an upgrade leaves it
        assertTrue(earned(documentOpens = 40))
    }

    private fun earned(
        documentOpens: Int = 0,
        asks: Int = 0,
        askedAfterOpens: Int = 0,
        askedAtMillis: Long = 0,
        nowMillis: Long = 0,
    ): Boolean =
        ReviewInvitation.isEarned(documentOpens, asks, askedAfterOpens, askedAtMillis, nowMillis)

    private companion object {
        const val ASKED_AT = 1_700_000_000_000L
        const val TWO_WEEKS = 14L * 24 * 60 * 60 * 1000
    }
}
