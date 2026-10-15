package dev.omalauncher.widgets

import dev.omalauncher.ShortcutRef
import dev.omalauncher.linux.LinuxApp
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutTest {
    private val whatsapp = LayoutPin.App("com.whatsapp/com.whatsapp.Main")
    private val reddit = LayoutPin.App("com.reddit.frontpage/com.reddit.launch.main.MainActivity")
    private val signal = LayoutPin.App("org.thoughtcrime.securesms/.RoutingActivity")
    private val foot = LayoutPin.Linux(LinuxApp("Foot", "foot", terminal = false, id = "foot", wmClass = null, icon = "foot"))
    private val home = LayoutPin.AppShortcut(ShortcutRef("com.google.android.apps.maps", "home"))

    private fun read(text: String) = parseLayout(text) as LayoutRead.Read

    @Test fun readsTheDocumentedShape() {
        val text =
            """
            {"omalauncher-layout": 1,
             "pages": [
              [ {"app": "com.whatsapp/com.whatsapp.Main", "x": 0, "y": 8},
                {"folder": "Social", "x": 5, "y": 9, "apps": ["com.reddit.frontpage/com.reddit.launch.main.MainActivity"]},
                {"widget": "10-today.owl", "x": 0, "y": 0, "w": 6, "h": 2} ],
              [ {"linux": {"name": "Foot", "exec": "foot", "terminal": false, "id": "foot", "icon": "foot"}, "x": 1, "y": 2},
                {"shortcut": {"package": "com.google.android.apps.maps", "id": "home"}, "x": 2, "y": 2} ]
             ]}
            """
        assertEquals(
            LayoutRead.Read(
                HomeLayout(
                    listOf(
                        listOf(
                            LayoutEntry.Pin(whatsapp, 0, 8),
                            LayoutEntry.Folder("Social", listOf(reddit), 5, 9),
                            LayoutEntry.Widget("10-today.owl", 0, 0, 6, 2),
                        ),
                        listOf(LayoutEntry.Pin(foot, 1, 2), LayoutEntry.Pin(home, 2, 2)),
                    ),
                ),
                unknown = 0,
            ),
            parseLayout(text),
        )
    }

    @Test fun roundTrips() {
        val layout =
            HomeLayout(
                listOf(
                    listOf(
                        LayoutEntry.Widget("10-today.owl", 0, 0, 6, 2),
                        LayoutEntry.Folder("Social", listOf(reddit, foot, home), 5, 9),
                        LayoutEntry.Folder(null, listOf(whatsapp, signal), 4, 9),
                    ),
                    listOf(LayoutEntry.Pin(foot, 1, 2), LayoutEntry.Pin(home, 2, 2), LayoutEntry.Pin(whatsapp, 0, 0)),
                ),
            )
        assertEquals(LayoutRead.Read(layout, unknown = 0), parseLayout(layout.toJson()))
    }

    @Test fun readsAndWritesItsColumns() {
        val text =
            """
            {"omalauncher-layout": 1, "columns": 5, "pages": [[
              {"app": "com.whatsapp/com.whatsapp.Main", "x": 4, "y": 0},
              {"app": "com.reddit.frontpage/com.reddit.launch.main.MainActivity", "x": 5, "y": 0},
              {"widget": "a.owl", "x": 0, "y": 1, "w": 5, "h": 2}
            ]]}
            """
        val layout = HomeLayout(listOf(listOf(LayoutEntry.Pin(whatsapp, 4, 0), LayoutEntry.Widget("a.owl", 0, 1, 5, 2))), columns = 5)
        assertEquals(LayoutRead.Read(layout, unknown = 1), parseLayout(text))
        assertEquals(LayoutRead.Read(layout, unknown = 0), parseLayout(layout.toJson()))
        assertEquals(5, JSONObject(layout.toJson()).getInt("columns"))
        assertEquals(LayoutRead.Rejected("not a layout file: 0 columns"), parseLayout("""{"omalauncher-layout": 1, "columns": 0, "pages": []}"""))
    }

    @Test fun resolvesOntoTheGridsColumns() {
        val text =
            """
            {"omalauncher-layout": 1, "pages": [[
              {"widget": "a.owl", "x": 0, "y": 0, "w": 6, "h": 2},
              {"widget": "b.owl", "x": 3, "y": 2, "w": 3, "h": 2},
              {"app": "com.whatsapp/com.whatsapp.Main", "x": 0, "y": 4},
              {"app": "com.reddit.frontpage/com.reddit.launch.main.MainActivity", "x": 1, "y": 4},
              {"folder": "", "x": 5, "y": 4, "apps": ["org.thoughtcrime.securesms/.RoutingActivity", "com.reddit.frontpage/com.reddit.launch.main.MainActivity"]}
            ]]}
            """
        assertEquals(
            HomeLayout(
                listOf(
                    listOf(
                        LayoutEntry.Widget("a.owl", 0, 0, 5, 2),
                        LayoutEntry.Widget("b.owl", 2, 2, 2, 2),
                        LayoutEntry.Pin(whatsapp, 0, 4),
                        LayoutEntry.Pin(reddit, 0, 4),
                        LayoutEntry.Folder(null, listOf(signal), 4, 4),
                    ),
                ),
                columns = 5,
            ),
            resolveLayout(read(text), installed = { true }, widgets = setOf("a.owl", "b.owl"), columns = 5).layout,
        )
    }

    @Test fun rejectsOtherVersionsAndFiles() {
        assertEquals(LayoutRead.Rejected("layout version 2; this launcher reads version 1"), parseLayout("""{"omalauncher-layout": 2, "pages": []}"""))
        assertEquals(LayoutRead.Rejected("not a layout file"), parseLayout("""{"pages": []}"""))
        assertEquals(LayoutRead.Rejected("not a layout file"), parseLayout("[1, 2]"))
        assertEquals(LayoutRead.Rejected("not a layout file: no pages"), parseLayout("""{"omalauncher-layout": 1}"""))
    }

    @Test fun skipsAndCountsUnknownEntries() {
        val text =
            """
            {"omalauncher-layout": 1, "pages": [[
              {"app": "com.whatsapp/com.whatsapp.Main", "x": 0, "y": 0},
              {"clock": true, "x": 1, "y": 0},
              {"app": "com.whatsapp/com.whatsapp.Main", "x": 6, "y": 0},
              {"widget": "a.owl", "x": 4, "y": 0, "w": 3, "h": 1},
              {"app": "no-slash", "x": 2, "y": 0},
              "junk",
              {"folder": "", "x": 3, "y": 0, "apps": ["com.whatsapp/com.whatsapp.Main", 7, {"phone": "x"}]}
            ]]}
            """
        assertEquals(
            LayoutRead.Read(
                HomeLayout(listOf(listOf(LayoutEntry.Pin(whatsapp, 0, 0), LayoutEntry.Folder(null, listOf(whatsapp), 3, 0)))),
                unknown = 7,
            ),
            parseLayout(text),
        )
    }

    @Test fun resolveSkipsMissingAndRepeated() {
        val text =
            """
            {"omalauncher-layout": 1, "pages": [
              [ {"app": "com.whatsapp/com.whatsapp.Main", "x": 0, "y": 8},
                {"app": "org.thoughtcrime.securesms/.RoutingActivity", "x": 1, "y": 8},
                {"folder": "Social", "x": 5, "y": 9, "apps": [
                  "com.reddit.frontpage/com.reddit.launch.main.MainActivity",
                  "com.whatsapp/com.whatsapp.Main",
                  {"shortcut": {"package": "com.google.android.apps.maps", "id": "home"}}]},
                {"widget": "10-today.owl", "x": 0, "y": 0, "w": 6, "h": 2},
                {"widget": "gone.owl", "x": 0, "y": 2, "w": 2, "h": 2} ],
              [ {"widget": "10-today.owl", "x": 0, "y": 0, "w": 6, "h": 2},
                {"linux": {"name": "Foot", "exec": "foot", "id": "foot", "icon": "foot"}, "x": 0, "y": 1},
                {"linux": {"name": "Foot (old)", "exec": "foot", "id": "foot"}, "x": 1, "y": 1},
                {"tile": 1, "x": 0, "y": 3} ]
            ]}
            """
        val installed = setOf<LayoutPin>(whatsapp, reddit, foot, LayoutPin.Linux(LinuxApp("Foot (old)", "foot", false, "foot", null)))
        val result = resolveLayout(read(text), installed = { it in installed }, widgets = setOf("10-today.owl", "other.owl"), columns = 6)
        assertEquals(
            LayoutImport(
                HomeLayout(
                    listOf(
                        listOf(
                            LayoutEntry.Pin(whatsapp, 0, 8),
                            LayoutEntry.Folder("Social", listOf(reddit), 5, 9),
                            LayoutEntry.Widget("10-today.owl", 0, 0, 6, 2),
                        ),
                        listOf(LayoutEntry.Pin(foot, 0, 1)),
                    ),
                ),
                LayoutSkips(unknown = 1, notInstalled = 2, missingWidgets = 1, repeated = 3),
            ),
            result,
        )
        // The folder is left with one app, which the home screen shows on its own.
        assertEquals("placed 3 apps, 1 widget; skipped 2 not installed, 1 widget not found, 3 listed twice, 1 unknown", result.summary())
    }

    @Test fun summaryWithNothingSkipped() {
        val layout = HomeLayout(listOf(listOf(LayoutEntry.Folder("Social", listOf(whatsapp, reddit), 0, 0), LayoutEntry.Widget("a.owl", 1, 0, 2, 2))))
        assertEquals("placed 2 apps, 1 folder, 1 widget", resolveLayout(LayoutRead.Read(layout, 0), { true }, setOf("a.owl"), 6).summary())
    }
}
