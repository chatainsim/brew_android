package fr.easter.brewhome

import fr.easter.brewhome.data.Beer
import fr.easter.brewhome.data.Brew
import fr.easter.brewhome.data.FermReading
import fr.easter.brewhome.data.LastReading
import fr.easter.brewhome.data.InventoryItem
import fr.easter.brewhome.data.Recipe
import fr.easter.brewhome.data.RecipeIngredient
import fr.easter.brewhome.data.ShoppingItem
import fr.easter.brewhome.data.Snapshot
import fr.easter.brewhome.data.SnapshotCache
import fr.easter.brewhome.data.lastReadingOf
import fr.easter.brewhome.widget.readingLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Cache disque du dernier instantané serveur (consultation hors ligne). */
class SnapshotCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val empty = Snapshot(
        beers = emptyList(), recipes = emptyList(), inventory = emptyList(),
        brews = emptyList(), drafts = emptyList(), shopping = emptyList(),
    )

    @Test
    fun `aller-retour complet`() {
        val cache = SnapshotCache(tmp.root)
        assertNull(cache.load())

        val snap = empty.copy(
            beers = listOf(Beer(id = 1, name = "Ambrée d'été", stock33 = 12, abv = 5.4)),
            recipes = listOf(
                Recipe(
                    id = 2, name = "NEIPA", style = "NEIPA", volume = 20.0,
                    ingredients = listOf(
                        RecipeIngredient(id = 3, name = "Citra", category = "houblon", quantity = 50.0, unit = "g"),
                    ),
                ),
            ),
            inventory = listOf(InventoryItem(id = 4, name = "Pilsner", category = "malt", quantity = 5.0)),
            brews = listOf(Brew(id = 5, name = "Brassin 12", status = "completed", brewDate = "2026-05-01")),
            shopping = listOf(ShoppingItem(id = 6, name = "US-05", category = "levure")),
        )
        cache.save(snap, savedAt = 123L)

        val loaded = cache.load()
        assertNotNull(loaded)
        assertEquals(123L, loaded!!.savedAt)
        assertEquals(snap, loaded.snapshot)
    }

    @Test
    fun `fichier corrompu ignore sans exception`() {
        val cache = SnapshotCache(tmp.root)
        tmp.root.resolve("snapshot.json").writeText("pas du json")
        assertNull(cache.load())
    }

    @Test
    fun `clear supprime le cache`() {
        val cache = SnapshotCache(tmp.root)
        cache.save(empty)
        assertNotNull(cache.load())
        cache.clear()
        assertNull(cache.load())
    }

    @Test
    fun `dernieres mesures gardees dans le cache, ancien cache sans elles toujours lisible`() {
        val cache = SnapshotCache(tmp.root)
        val snap = empty.copy(lastReadings = mapOf(5 to LastReading(1.012, 19.5, "2026-10-12 14:30:00")))
        cache.save(snap, savedAt = 1L)
        assertEquals(snap, cache.load()!!.snapshot)

        tmp.root.resolve("snapshot.json").writeText(
            """{"savedAt":2,"snapshot":{"beers":[],"recipes":[],"inventory":[],"brews":[],"drafts":[],"shopping":[]}}""",
        )
        assertEquals(emptyMap<Int, LastReading>(), cache.load()!!.snapshot.lastReadings)
    }

    @Test
    fun `derniere densite et derniere temperature, chacune la plus recente`() {
        assertNull(lastReadingOf(emptyList()))
        assertNull(lastReadingOf(listOf(FermReading(recordedAt = "2026-10-01 10:00:00", notes = "rien"))))
        val r = lastReadingOf(
            listOf(
                FermReading(recordedAt = "2026-10-12 08:00:00", gravity = 1.010, temperature = 19.0),  // densimètre
                FermReading(recordedAt = "2026-10-01 10:00:00", gravity = 1.050, temperature = 20.0),
                FermReading(recordedAt = "2026-10-12 09:00:00", gravity = 1.011),                      // manuelle
            ),
        )
        assertEquals(LastReading(1.011, 19.0, "2026-10-12 09:00:00"), r)
        // relevés en UTC côté serveur, affichés à l'heure du téléphone
        val tz = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Paris"))
        try {
            assertEquals("🧪 1.011 · 🌡️ 19 °C · 12/10 11:00", readingLabel(r!!))
            assertEquals("🌡️ 18,5 °C · 04/10 00:00", readingLabel(LastReading(temperature = 18.5, recordedAt = "2026-10-03T22:00:00")))
            assertEquals("2026-01-15 11:00", fr.easter.brewhome.ui.fmtReadingTime("2026-01-15 10:00:00"))   // hiver : UTC+1
            assertEquals("illisible", fr.easter.brewhome.ui.fmtReadingTime("illisible"))
        } finally {
            java.util.TimeZone.setDefault(tz)
        }
        assertEquals("🧪 12,5", readingLabel(LastReading(gravity = 12.5)))                              // degrés Plato
    }
}
