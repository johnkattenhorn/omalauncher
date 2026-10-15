package dev.omalauncher.linux

import org.junit.Assert.assertEquals
import org.junit.Test

class LabelFontTest {
    /** fc-list's lines for ttf-jetbrains-mono 2.304's JetBrains Mono, as a fresh guest has it, out of order. */
    private val jetBrainsMono =
        listOf(
            "200\t100\t0\t/usr/share/fonts/TTF/JetBrainsMono-BoldItalic.ttf",
            "205\t0\t0\t/usr/share/fonts/TTF/JetBrainsMono-ExtraBold.ttf",
            "40\t0\t0\t/usr/share/fonts/TTF/JetBrainsMono-ExtraLight.ttf",
            "80\t100\t0\t/usr/share/fonts/TTF/JetBrainsMono-Italic.ttf",
            "50\t0\t0\t/usr/share/fonts/TTF/JetBrainsMono-Light.ttf",
            "100\t100\t0\t/usr/share/fonts/TTF/JetBrainsMono-MediumItalic.ttf",
            "100\t0\t0\t/usr/share/fonts/TTF/JetBrainsMono-Medium.ttf",
            "200\t0\t0\t/usr/share/fonts/TTF/JetBrainsMono-Bold.ttf",
            "80\t0\t0\t/usr/share/fonts/TTF/JetBrainsMono-Regular.ttf",
            "180\t0\t0\t/usr/share/fonts/TTF/JetBrainsMono-SemiBold.ttf",
            "0\t0\t0\t/usr/share/fonts/TTF/JetBrainsMono-Thin.ttf",
        )

    @Test fun takesTheUprightRegularMediumAndBold() {
        assertEquals(
            LabelFaces.Guest(
                "/usr/share/fonts/TTF/JetBrainsMono-Regular.ttf",
                "/usr/share/fonts/TTF/JetBrainsMono-Medium.ttf",
                "/usr/share/fonts/TTF/JetBrainsMono-Bold.ttf",
            ),
            LabelFont.choose("JetBrains Mono", linux = true) { jetBrainsMono },
        )
    }

    /** DejaVu Sans Mono has Book and Bold, and obliques; fontconfig's warnings come on the same output. */
    @Test fun aFamilyWithoutMediumHasNone() {
        val lines =
            listOf(
                "Fontconfig warning: \"/etc/fonts/conf.d/05-reset-dirs-sample.conf\", line 6: unknown element \"reset-dirs\"",
                "200\t110\t0\t/usr/share/fonts/TTF/DejaVuSansMono-BoldOblique.ttf",
                "200\t0\t0\t/usr/share/fonts/TTF/DejaVuSansMono-Bold.ttf",
                "80\t110\t0\t/usr/share/fonts/TTF/DejaVuSansMono-Oblique.ttf",
                "80\t0\t0\t/usr/share/fonts/TTF/DejaVuSansMono.ttf",
            )
        assertEquals(
            LabelFaces.Guest("/usr/share/fonts/TTF/DejaVuSansMono.ttf", null, "/usr/share/fonts/TTF/DejaVuSansMono-Bold.ttf"),
            LabelFont.choose("DejaVu Sans Mono", linux = true) { lines },
        )
    }

    /** A variable font's default face lists a weight range, and a collection's later faces an index; neither loads from a file alone. */
    @Test fun skipsVariableRangesAndLaterCollectionFaces() {
        val lines =
            listOf(
                "[0 210]\t0\t0\t/usr/share/fonts/X/Var.ttf",
                "80\t0\t65537\t/usr/share/fonts/X/Var.ttf",
                "50\t0\t0\t/usr/share/fonts/X/Light.ttf",
            )
        assertEquals(LabelFaces.Guest("/usr/share/fonts/X/Light.ttf", null, null), LabelFont.choose("X", linux = true) { lines })
    }

    @Test fun unsetIsTheSystemMonospaceWithoutAskingLinux() {
        assertEquals(LabelFaces.System, LabelFont.choose(null, linux = true) { error("listed") })
    }

    @Test fun withoutLinuxFallsBackAndSaysWhy() {
        assertEquals(
            LabelFaces.Missing("home.label-font: \"JetBrains Mono\" comes from Linux, which isn't installed; using the system's monospace"),
            LabelFont.choose("JetBrains Mono", linux = false) { error("listed") },
        )
    }

    @Test fun anUninstalledFamilyFallsBackAndSaysWhy() {
        assertEquals(
            LabelFaces.Missing("home.label-font: no font \"Comic Mono\" in Linux (fc-list lists them); using the system's monospace"),
            LabelFont.choose("Comic Mono", linux = true) { listOf("80\t100\t0\t/usr/share/fonts/TTF/ComicMono-Italic.ttf") },
        )
    }

    @Test fun listsTheFamilyAsAFontconfigPattern() {
        assertEquals("fc-list -f '%{weight}\\t%{slant}\\t%{index}\\t%{file}\\n' 'JetBrains Mono'", LabelFont.listing("JetBrains Mono"))
        assertEquals("fc-list -f '%{weight}\\t%{slant}\\t%{index}\\t%{file}\\n' 'Foo\\-Bar\\:x\\,y'", LabelFont.listing("Foo-Bar:x,y"))
    }
}
