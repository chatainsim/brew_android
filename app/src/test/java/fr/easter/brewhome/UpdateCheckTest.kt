package fr.easter.brewhome

import fr.easter.brewhome.data.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    @Test
    fun `plus grande version detectee comme mise a jour`() {
        assertTrue(UpdateChecker.isNewer("1.78", "1.77"))
        assertTrue(UpdateChecker.isNewer("2.0", "1.99"))
    }

    @Test
    fun `comparaison numerique et non lexicographique`() {
        // Un tri de chaînes classerait "1.10" avant "1.9" - la comparaison doit
        // rester composant par composant façon semver.
        assertTrue(UpdateChecker.isNewer("1.10", "1.9"))
        assertFalse(UpdateChecker.isNewer("1.9", "1.10"))
    }

    @Test
    fun `version identique ou plus ancienne pas signalee comme mise a jour`() {
        assertFalse(UpdateChecker.isNewer("1.78", "1.78"))
        assertFalse(UpdateChecker.isNewer("1.77", "1.78"))
    }

    @Test
    fun `prefixe v ignore`() {
        assertTrue(UpdateChecker.isNewer("v1.78", "1.77"))
        assertFalse(UpdateChecker.isNewer("1.77", "v1.78"))
    }

    @Test
    fun `parseVersion tolere une chaine invalide`() {
        assertEquals(listOf(0), UpdateChecker.parseVersion(""))
        assertEquals(listOf(0), UpdateChecker.parseVersion("abc"))
        assertEquals(listOf(1, 78), UpdateChecker.parseVersion("1.78"))
    }
}
