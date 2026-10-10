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
    fun theFirstAskWaitsForThreeDocuments() {
        assertFalse(earned(documentOpens = 2))
        assertTrue(earned(documentOpens = 3))
    }

    @Test
    fun documentsAreCountedFromTheAskBefore() {
        // three more after the ask, not three in all
        assertFalse(
            earned(
                documentOpens = 5,
                askedAfterOpens = 3,
                askedAtMillis = ASKED_AT,
                nowMillis = LATER,
            )
        )
        assertTrue(
            earned(
                documentOpens = 6,
                askedAfterOpens = 3,
                askedAtMillis = ASKED_AT,
                nowMillis = LATER,
            )
        )
    }

    @Test
    fun thereIsNoLastAsk() {
        assertTrue(
            earned(
                documentOpens = 1003,
                askedAfterOpens = 1000,
                askedAtMillis = ASKED_AT,
                nowMillis = LATER,
            )
        )
    }

    @Test
    fun twoWeeksHaveToPassBetweenAsks() {
        assertFalse(
            earned(
                documentOpens = 15,
                askedAfterOpens = 5,
                askedAtMillis = ASKED_AT,
                nowMillis = ASKED_AT + TWO_WEEKS - 1,
            )
        )
        assertTrue(
            earned(
                documentOpens = 15,
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
                askedAfterOpens = 5,
                askedAtMillis = ASKED_AT,
                nowMillis = ASKED_AT - TWO_WEEKS,
            )
        )
    }

    @Test
    fun anInstallThatWasNeverAskedHasNoRail() {
        // zero is "never asked", not 1970
        assertTrue(earned(documentOpens = 3, askedAtMillis = 0, nowMillis = ASKED_AT))
    }

    @Test
    fun documentsReadBeforeTheCountersExistedStillCount() {
        // only the document counter set, as an upgrade leaves it
        assertTrue(earned(documentOpens = 40))
    }

    private fun earned(
        documentOpens: Int = 0,
        askedAfterOpens: Int = 0,
        askedAtMillis: Long = 0,
        nowMillis: Long = 0,
    ): Boolean = ReviewInvitation.isEarned(documentOpens, askedAfterOpens, askedAtMillis, nowMillis)

    private companion object {
        const val ASKED_AT = 1_700_000_000_000L
        const val TWO_WEEKS = 14L * 24 * 60 * 60 * 1000
        const val LATER = ASKED_AT + TWO_WEEKS
    }
}
