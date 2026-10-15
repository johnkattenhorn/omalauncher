package dev.omalauncher.linux

import android.content.Context
import android.util.Log
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import dev.omalauncher.theme.Style
import dev.omalauncher.theme.StyleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** What home.label-font comes to. */
sealed interface LabelFaces {
    /** Unset: the system's monospace, as the rest of the launcher. */
    data object System : LabelFaces

    /** A guest family's upright faces, by their paths in the guest; Compose draws a missing medium as regular and fakes a missing bold. */
    data class Guest(
        val regular: String,
        val medium: String?,
        val bold: String?,
    ) : LabelFaces

    /** Set but not to be had: the labels keep the system's monospace, and errors.txt says why. */
    data class Missing(
        val problem: String,
    ) : LabelFaces
}

/**
 * The font family the names under home's app and folder icons are drawn in:
 * home.label-font's faces from the files fontconfig finds for it in the
 * guest, as foot finds [TerminalFont]'s, loaded once per change rather than
 * per tile, and off the main thread.
 */
class LabelFont(
    private val context: Context,
    private val proot: Proot,
    private val style: StyleStore,
) {
    /** [family]'s faces with [linux] installed, or the system's monospace. */
    suspend fun load(
        family: String?,
        linux: Boolean,
    ): FontFamily {
        val faces = withContext(Dispatchers.IO) { choose(family, linux, ::list) }
        val problem =
            when (faces) {
                LabelFaces.System -> return FontFamily.Monospace
                is LabelFaces.Missing -> faces.problem
                is LabelFaces.Guest -> {
                    val loaded = withContext(Dispatchers.IO) { runCatching { fontFamily(faces) } }
                    loaded.getOrNull()?.let {
                        style.withdraw(KEY)
                        return it
                    }
                    "$KEY: can't read \"$family\" (${loaded.exceptionOrNull()}); $FALLBACK"
                }
            }
        style.report(problem)
        return FontFamily.Monospace
    }

    private fun list(family: String): List<String> =
        buildList {
            runCatching { proot.run(listing(family)) { add(it) } }.onFailure { Log.w(TAG, "can't list the faces of $family", it) }
        }

    /** Loads every face now, so drawing the first label doesn't. */
    private suspend fun fontFamily(faces: LabelFaces.Guest): FontFamily {
        val family =
            FontFamily(
                listOfNotNull(
                    Font(proot.hostFile(faces.regular), FontWeight.Normal),
                    faces.medium?.let { Font(proot.hostFile(it), FontWeight.Medium) },
                    faces.bold?.let { Font(proot.hostFile(it), FontWeight.Bold) },
                ),
            )
        createFontFamilyResolver(context).preload(family)
        return family
    }

    companion object {
        private val KEY = Style.LABEL_FONT.key
        private const val FALLBACK = "using the system's monospace"
        private const val TAG = "label-font"

        /** fontconfig's weights for regular, medium and bold. */
        private const val REGULAR = 80
        private const val MEDIUM = 100
        private const val BOLD = 200

        /** fc-list's faces of [family], a line each: weight, slant, index in the file, file. */
        fun listing(family: String) = "fc-list -f '%{weight}\\t%{slant}\\t%{index}\\t%{file}\\n' " + shellQuote(family.replace(Regex("""[\\:,-]"""), """\\$0"""))

        /** home.label-font's [family] with [linux] installed or not, from [list]'s fc-list lines; [list] runs only when there's a family and Linux. */
        fun choose(
            family: String?,
            linux: Boolean,
            list: (String) -> List<String>,
        ): LabelFaces {
            if (family == null) return LabelFaces.System
            if (!linux) return LabelFaces.Missing("$KEY: \"$family\" comes from Linux, which isn't installed; $FALLBACK")
            val upright = list(family).mapNotNull(::uprightFace)
            val regular = upright.minByOrNull { abs(it.weight - REGULAR) } ?: return LabelFaces.Missing("$KEY: no font \"$family\" in Linux (fc-list lists them); $FALLBACK")
            return LabelFaces.Guest(regular.file, upright.firstOrNull { it.weight == MEDIUM }?.file, upright.firstOrNull { it.weight == BOLD }?.file)
        }

        private class Face(
            val weight: Int,
            val file: String,
        )

        /** A [listing] line's face; null for italics, a variable font's weight range and a collection's later faces. */
        private fun uprightFace(line: String): Face? {
            val fields = line.split('\t', limit = 4)
            if (fields.size != 4 || fields[1] != "0" || fields[2] != "0" || fields[3].isEmpty()) return null
            return fields[0].toIntOrNull()?.let { Face(it, fields[3]) }
        }
    }
}
