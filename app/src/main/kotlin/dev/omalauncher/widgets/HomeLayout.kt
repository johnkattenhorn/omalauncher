package dev.omalauncher.widgets

import dev.omalauncher.ShortcutRef
import dev.omalauncher.linux.LinuxApp
import org.json.JSONArray
import org.json.JSONObject

/** What a layout file pins: an Android app, a Linux app, or a shortcut an Android app made. */
sealed interface LayoutPin {
    /** A launcher activity, as `package/class` (ComponentName.flattenToString). */
    data class App(
        val component: String,
    ) : LayoutPin

    /** A snapshot of its desktop entry, as [Shortcut.linux]. */
    data class Linux(
        val app: LinuxApp,
    ) : LayoutPin

    data class AppShortcut(
        val ref: ShortcutRef,
    ) : LayoutPin
}

/** One thing on a home page, its top-left at cell [x], [y] of its [HomeLayout]'s grid. Apps and folders are 1x1. */
sealed interface LayoutEntry {
    val x: Int
    val y: Int

    data class Pin(
        val pin: LayoutPin,
        override val x: Int,
        override val y: Int,
    ) : LayoutEntry

    data class Folder(
        val name: String?,
        val apps: List<LayoutPin>,
        override val x: Int,
        override val y: Int,
    ) : LayoutEntry

    /** A widget file in the widgets folder, by name, [w] by [h] cells. */
    data class Widget(
        val file: String,
        override val x: Int,
        override val y: Int,
        val w: Int,
        val h: Int,
    ) : LayoutEntry
}

/**
 * A home screen as a file, to set one up in one go or carry it to another phone:
 * `{"omalauncher-layout": 1, "columns": 6, "pages": [[entry, …], …]}`, the first page being home's first,
 * on a grid [columns] wide (6 when the file doesn't say).
 * An entry is `{"app": "package/class"}`, `{"linux": {desktop entry}}`, `{"shortcut": {"package", "id"}}`,
 * `{"folder": name, "apps": [app, …]}` or `{"widget": file, "w", "h"}`, each with its cell's `"x"` and `"y"`.
 * In a folder, an Android app may be just its `"package/class"`.
 */
data class HomeLayout(
    val pages: List<List<LayoutEntry>>,
    val columns: Int = CLASSIC_COLUMNS,
)

/** This layout on a grid of [to] columns, each entry's cell moved as [Cell.rescaled] moves it. */
fun HomeLayout.rescaled(to: Int): HomeLayout {
    fun moved(e: LayoutEntry): LayoutEntry {
        val c = Cell(e.x, e.y, (e as? LayoutEntry.Widget)?.w ?: 1, 1).rescaled(columns, to)
        return when (e) {
            is LayoutEntry.Pin -> e.copy(x = c.x)
            is LayoutEntry.Folder -> e.copy(x = c.x)
            is LayoutEntry.Widget -> e.copy(x = c.x, w = c.w)
        }
    }
    return HomeLayout(pages.map { it.map(::moved) }, to)
}

/** A layout file read, or why it can't be. */
sealed interface LayoutRead {
    /** [unknown] counts the entries and folder apps left out: of a kind this launcher doesn't know, or off the grid. */
    data class Read(
        val layout: HomeLayout,
        val unknown: Int,
    ) : LayoutRead

    data class Rejected(
        val reason: String,
    ) : LayoutRead
}

private const val FORMAT = "omalauncher-layout"
private const val VERSION = 1

fun parseLayout(text: String): LayoutRead {
    val root = runCatching { JSONObject(text) }.getOrNull() ?: return LayoutRead.Rejected("not a layout file")
    val version = root.opt(FORMAT) ?: return LayoutRead.Rejected("not a layout file")
    if (version != VERSION) return LayoutRead.Rejected("layout version $version; this launcher reads version $VERSION")
    val pages = root.optJSONArray("pages") ?: return LayoutRead.Rejected("not a layout file: no pages")
    val columns = root.optInt("columns", CLASSIC_COLUMNS)
    if (columns < 1) return LayoutRead.Rejected("not a layout file: $columns columns")
    var unknown = 0

    fun <T : Any> counted(value: T?) = value.also { if (it == null) unknown++ }
    val layout =
        HomeLayout(
            (0 until pages.length()).map { p ->
                val page = pages.optJSONArray(p) ?: JSONArray()
                (0 until page.length()).mapNotNull { i ->
                    counted(runCatching { parseEntry(page.getJSONObject(i), columns) { counted(parsePin(it)) } }.getOrNull())
                }
            },
            columns,
        )
    return LayoutRead.Read(layout, unknown)
}

private fun parseEntry(
    o: JSONObject,
    columns: Int,
    folderApp: (Any) -> LayoutPin?,
): LayoutEntry? {
    val x = o.getInt("x")
    val y = o.getInt("y")
    if (x !in 0 until columns || y < 0) return null
    return when {
        o.has("widget") -> {
            val w = o.getInt("w")
            val h = o.getInt("h")
            if (w !in 1..columns - x || h < 1) return null
            LayoutEntry.Widget(o.getString("widget"), x, y, w, h)
        }
        o.has("folder") -> {
            val apps = o.getJSONArray("apps")
            LayoutEntry.Folder(
                o.getString("folder").trim().ifEmpty { null },
                (0 until apps.length()).mapNotNull { i -> folderApp(apps.get(i)) },
                x,
                y,
            )
        }
        else -> parsePin(o)?.let { LayoutEntry.Pin(it, x, y) }
    }
}

private fun parsePin(value: Any): LayoutPin? =
    runCatching {
        when {
            value is String -> value.takeIf { '/' in it }?.let(LayoutPin::App)
            value !is JSONObject -> null
            value.has("app") -> value.getString("app").takeIf { '/' in it }?.let(LayoutPin::App)
            value.has("linux") -> LayoutPin.Linux(linuxSnapshot(value.getJSONObject("linux")))
            value.has("shortcut") -> value.getJSONObject("shortcut").let { LayoutPin.AppShortcut(ShortcutRef(it.getString("package"), it.getString("id"))) }
            else -> null
        }
    }.getOrNull()

fun HomeLayout.toJson(): String =
    JSONObject()
        .put(FORMAT, VERSION)
        .put("columns", columns)
        .put("pages", JSONArray(pages.map { page -> JSONArray(page.map(::entryJson)) }))
        .toString(1)

private fun entryJson(e: LayoutEntry): JSONObject =
    when (e) {
        is LayoutEntry.Pin -> pinJson(e.pin)
        is LayoutEntry.Folder ->
            JSONObject()
                .put("folder", e.name.orEmpty())
                .put("apps", JSONArray(e.apps.map { if (it is LayoutPin.App) it.component else pinJson(it) }))
        is LayoutEntry.Widget -> JSONObject().put("widget", e.file).put("w", e.w).put("h", e.h)
    }.put("x", e.x).put("y", e.y)

private fun pinJson(pin: LayoutPin): JSONObject =
    when (pin) {
        is LayoutPin.App -> JSONObject().put("app", pin.component)
        is LayoutPin.Linux -> JSONObject().put("linux", linuxSnapshot(pin.app))
        is LayoutPin.AppShortcut -> JSONObject().put("shortcut", JSONObject().put("package", pin.ref.packageName).put("id", pin.ref.id))
    }

/** What an import leaves out, by why. */
data class LayoutSkips(
    val unknown: Int = 0,
    val notInstalled: Int = 0,
    val missingWidgets: Int = 0,
    /** An app or widget listed again after its first place. */
    val repeated: Int = 0,
)

/** A layout as it applies to this phone, and what was left out of it. */
data class LayoutImport(
    val layout: HomeLayout,
    val skips: LayoutSkips,
)

/**
 * [read] as it applies to this phone, on its grid of [columns]: apps [installed] says aren't there, widgets whose
 * file isn't among [widgets], and anything listed again after its first place are left out and counted.
 */
fun resolveLayout(
    read: LayoutRead.Read,
    installed: (LayoutPin) -> Boolean,
    widgets: Set<String>,
    columns: Int,
): LayoutImport {
    var notInstalled = 0
    var missing = 0
    var repeated = 0
    val seen = HashSet<String>()

    fun keep(pin: LayoutPin): Boolean =
        when {
            !installed(pin) -> false.also { notInstalled++ }
            !seen.add(pin.identity) -> false.also { repeated++ }
            else -> true
        }
    val layout = read.layout.rescaled(columns)
    val pages =
        layout.pages.map { page ->
            page.mapNotNull { e ->
                when (e) {
                    is LayoutEntry.Pin -> e.takeIf { keep(it.pin) }
                    is LayoutEntry.Folder -> e.copy(apps = e.apps.filter(::keep))
                    is LayoutEntry.Widget ->
                        when {
                            e.file !in widgets -> null.also { missing++ }
                            !seen.add("widget:" + e.file) -> null.also { repeated++ }
                            else -> e
                        }
                }
            }
        }
    return LayoutImport(HomeLayout(pages, columns), LayoutSkips(read.unknown, notInstalled, missing, repeated))
}

private val LayoutPin.identity
    get() =
        when (this) {
            is LayoutPin.App -> "app:$component"
            is LayoutPin.Linux -> "linux:" + app.id
            is LayoutPin.AppShortcut -> "shortcut:${ref.packageName}/${ref.id}"
        }

/** What an import placed and left out, in a line; a folder left with one app is that app, as on the home screen. */
fun LayoutImport.summary(): String {
    val entries = layout.pages.flatten()
    val folders = entries.filterIsInstance<LayoutEntry.Folder>()
    val apps = entries.count { it is LayoutEntry.Pin } + folders.sumOf { it.apps.size }
    val placed =
        listOf(apps to "app", folders.count { it.apps.size >= 2 } to "folder", entries.count { it is LayoutEntry.Widget } to "widget")
            .filter { it.first > 0 }
            .map { (n, what) -> count(n, what) }
    val skipped =
        listOfNotNull(
            skips.notInstalled.takeIf { it > 0 }?.let { "$it not installed" },
            skips.missingWidgets.takeIf { it > 0 }?.let { "${count(it, "widget")} not found" },
            skips.repeated.takeIf { it > 0 }?.let { "$it listed twice" },
            skips.unknown.takeIf { it > 0 }?.let { "$it unknown" },
        )
    return "placed " + placed.joinToString(", ").ifEmpty { "nothing" } + if (skipped.isEmpty()) "" else "; skipped " + skipped.joinToString(", ")
}

private fun count(
    n: Int,
    what: String,
) = "$n $what" + if (n == 1) "" else "s"
