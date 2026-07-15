package fr.easter.brewhome

import fr.easter.brewhome.data.Vitrine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Même logique que openVitrineTab() dans bh-ui.js du serveur. */
class VitrineTest {

    @Test
    fun `repo github classique`() {
        val targets = """[{"provider":"github","repo":"simon/ma-cave","branch":"main","pat":"ghp_x"}]"""
        assertEquals("https://simon.github.io/ma-cave/", Vitrine.pagesUrl(targets))
    }

    @Test
    fun `repo user_github_io publie a la racine`() {
        val targets = """[{"provider":"github","repo":"Simon/simon.github.io","branch":"main"}]"""
        assertEquals("https://Simon.github.io/", Vitrine.pagesUrl(targets))
    }

    @Test
    fun `provider custom ignore, github suivant retenu`() {
        val targets = """[
            {"provider":"custom","apiUrl":"https://gitea.local/api/v1","repo":"simon/cave"},
            {"provider":"github","repo":"simon/vitrine"}
        ]"""
        assertEquals("https://simon.github.io/vitrine/", Vitrine.pagesUrl(targets))
    }

    @Test
    fun `provider absent vaut github`() {
        val targets = """[{"repo":"simon/vitrine"}]"""
        assertEquals("https://simon.github.io/vitrine/", Vitrine.pagesUrl(targets))
    }

    @Test
    fun `pas de config utilisable`() {
        assertNull(Vitrine.pagesUrl(null))
        assertNull(Vitrine.pagesUrl(""))
        assertNull(Vitrine.pagesUrl("[]"))
        assertNull(Vitrine.pagesUrl("""[{"provider":"custom","repo":"simon/cave"}]"""))
        assertNull(Vitrine.pagesUrl("""[{"provider":"github","repo":"sans-slash"}]"""))
        assertNull(Vitrine.pagesUrl("pas du json"))
    }
}
