package dev.omalauncher.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class NewAppPageTest {
    @Test fun goesOnTheLastPageWithApps() {
        assertEquals(2, newAppPage(listOf(1, 2, 3), withApps = { it <= 2 }, fits = { true }))
    }

    @Test fun skipsFullPagesButNeverGoesBack() {
        assertEquals(3, newAppPage(listOf(1, 2, 3), withApps = { it == 2 }, fits = { it != 2 }))
        assertEquals(4, newAppPage(listOf(1, 2, 3), withApps = { it == 3 }, fits = { it == 1 }))
    }

    @Test fun startsFromTheFirstPageWhileNoneHasApps() {
        assertEquals(2, newAppPage(listOf(1, 2), withApps = { false }, fits = { it == 2 }))
    }

    @Test fun triesNoPageAfterTheOneItFits() {
        val tried = ArrayList<Int>()
        newAppPage(listOf(1, 2, 3, 4), withApps = { it == 1 }, fits = { tried += it; it == 2 })
        assertEquals(listOf(1, 2), tried)
    }
}
