package fr.easter.brewhome

import fr.easter.brewhome.data.Beer
import fr.easter.brewhome.data.Brew
import fr.easter.brewhome.data.InventoryItem
import fr.easter.brewhome.data.Recipe
import fr.easter.brewhome.data.RecipeIngredient
import fr.easter.brewhome.data.ShoppingItem
import fr.easter.brewhome.data.Snapshot
import fr.easter.brewhome.data.SnapshotCache
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
}
