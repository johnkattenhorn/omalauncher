package dev.omalauncher.widgets

import dev.omalauncher.widgets.runtime.AppInProfile
import dev.omalauncher.widgets.runtime.NotificationDots
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationDotsTest {
    private val signal = AppInProfile("org.thoughtcrime.securesms", null)
    private val maps = AppInProfile("com.google.android.apps.maps", null)
    private val workMail = AppInProfile("com.google.android.gm", 10)
    private val dots = NotificationDots(setOf(signal, workMail))

    @Test fun anAppWithANotificationHasADot() {
        assertEquals(listOf(true, false), listOf(signal, maps).map { dots.on(listOf(it)) })
    }

    @Test fun onlyInTheProfileItsNotificationCameFrom() {
        assertEquals(listOf(false, true, false), listOf(workMail.copy(profile = null), workMail, workMail.copy(profile = 11)).map { dots.on(listOf(it)) })
    }

    @Test fun aFolderHasADotWhenAnyOfItsAppsHasOne() {
        assertEquals(true, dots.on(listOf(maps, signal)))
        assertEquals(false, dots.on(listOf(maps, workMail.copy(profile = null))))
    }

    @Test fun noneWithoutNotificationsOrForATileWithoutAnApp() {
        assertEquals(false, NotificationDots().on(listOf(signal)))
        assertEquals(false, dots.on(emptyList()))
    }
}
