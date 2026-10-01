package fr.easter.brewhome

import fr.easter.brewhome.calc.RefermStatus
import fr.easter.brewhome.calc.refermStatus
import fr.easter.brewhome.data.Beer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Même découpage que le badge de la cave du site (script_cave). */
class RefermentationTest {
    private val today = LocalDate.of(2026, 10, 1)
    private fun beer(referm: Int = 1, bottled: String? = "2026-09-20", days: Int? = 14) =
        Beer(id = 1, name = "Saison", refermentation = referm, bottlingDate = bottled, refermentationDays = days)

    @Test
    fun `pas de refermentation`() = assertNull(refermStatus(beer(referm = 0), today))

    @Test
    fun `sans date ou sans duree - simplement en cours`() {
        assertEquals(RefermStatus.InProgress, refermStatus(beer(bottled = null), today))
        assertEquals(RefermStatus.InProgress, refermStatus(beer(days = null), today))
    }

    @Test
    fun `compte a rebours puis prete`() {
        // embouteillée le 20/09 + 14 j = 04/10
        assertEquals(RefermStatus.ReadyIn(3, LocalDate.of(2026, 10, 4)), refermStatus(beer(), today))
        assertEquals(RefermStatus.ReadyToday(LocalDate.of(2026, 10, 1)), refermStatus(beer(days = 11), today))
        assertEquals(RefermStatus.ReadySince(4, LocalDate.of(2026, 9, 27)), refermStatus(beer(days = 7), today))
    }

    @Test
    fun `date avec heure acceptee`() {
        assertEquals(RefermStatus.ReadyIn(3, LocalDate.of(2026, 10, 4)), refermStatus(beer(bottled = "2026-09-20 18:00:00"), today))
    }
}
