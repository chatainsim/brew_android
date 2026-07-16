package fr.easter.brewhome

import fr.easter.brewhome.calc.StockCheck
import fr.easter.brewhome.data.AppSettings
import fr.easter.brewhome.data.Beer
import fr.easter.brewhome.data.Brew
import fr.easter.brewhome.data.BrewApi
import fr.easter.brewhome.data.BrewLogEntry
import fr.easter.brewhome.data.BulkCheckPut
import fr.easter.brewhome.data.CatalogItem
import fr.easter.brewhome.data.Consumption
import fr.easter.brewhome.data.Draft
import fr.easter.brewhome.data.DraftPut
import fr.easter.brewhome.data.FermReading
import fr.easter.brewhome.data.InventoryItem
import fr.easter.brewhome.data.QtyPatch
import fr.easter.brewhome.data.Recipe
import fr.easter.brewhome.data.ShoppingItem
import fr.easter.brewhome.data.ShoppingPost
import fr.easter.brewhome.data.Snapshot
import fr.easter.brewhome.data.SnapshotCache
import fr.easter.brewhome.data.StockPatch
import fr.easter.brewhome.data.TastingPut
import fr.easter.brewhome.data.VpnController
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Orchestration de refreshAll : affichage du cache au lancement, bascule
 * hors ligne, nouvelle tentative après montée du VPN, messages d'erreur.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrewViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Doublures ─────────────────────────────────────────────────────────

    private class FakeSettings : AppSettings {
        override val serverUrl = MutableStateFlow("http://test/")
        override val themeMode = MutableStateFlow("system")
        override val dynamicColor = MutableStateFlow(false)
        override val wgAuto = MutableStateFlow(false)
        override val wgTunnel = MutableStateFlow("")
        override suspend fun setServerUrl(url: String) { serverUrl.value = url }
        override suspend fun setThemeMode(mode: String) { themeMode.value = mode }
        override suspend fun setDynamicColor(enabled: Boolean) { dynamicColor.value = enabled }
        override suspend fun setWgAuto(enabled: Boolean) { wgAuto.value = enabled }
        override suspend fun setWgTunnel(name: String) { wgTunnel.value = name }
    }

    /** Serveur en mémoire : bascule [down] pour simuler la perte de réseau. */
    private class FakeApi : BrewApi {
        var down = false
        var beers = emptyList<Beer>()
        val shopping = mutableListOf<ShoppingItem>()
        var failCreateFromCall = Int.MAX_VALUE
        private var createCalls = 0
        private var nextId = 100

        private fun gate() {
            if (down) throw IOException()
        }

        override suspend fun getBeers(): List<Beer> { gate(); return beers }
        override suspend fun getRecipes(): List<Recipe> { gate(); return emptyList() }
        override suspend fun getInventory(): List<InventoryItem> { gate(); return emptyList() }
        override suspend fun getBrews(): List<Brew> { gate(); return emptyList() }
        override suspend fun getDrafts(): List<Draft> { gate(); return emptyList() }
        override suspend fun getShoppingList(): List<ShoppingItem> { gate(); return shopping.toList() }
        override suspend fun getAppSettings(): JsonObject { gate(); return JsonObject(emptyMap()) }

        override suspend fun createShoppingItem(body: ShoppingPost): ShoppingItem {
            gate()
            createCalls++
            if (createCalls >= failCreateFromCall) throw IOException("refusé")
            val item = ShoppingItem(nextId++, body.name, body.category, body.quantity, body.unit)
            shopping += item
            return item
        }

        // Non exercés par ces tests
        override suspend fun patchBeerStock(id: Int, body: StockPatch): Beer = throw NotImplementedError()
        override suspend fun putBeerTasting(id: Int, body: TastingPut): Beer = throw NotImplementedError()
        override suspend fun getRecipe(id: Int): Recipe = throw NotImplementedError()
        override suspend fun patchInventoryQty(id: Int, body: QtyPatch): InventoryItem = throw NotImplementedError()
        override suspend fun getBrewFermentation(id: Int): List<FermReading> = throw NotImplementedError()
        override suspend fun getBrewLog(id: Int): List<BrewLogEntry> = throw NotImplementedError()
        override suspend fun getCatalog(): List<CatalogItem> = throw NotImplementedError()
        override suspend fun bulkCheckShopping(body: BulkCheckPut): JsonObject = throw NotImplementedError()
        override suspend fun buyShoppingItems(): JsonObject = throw NotImplementedError()
        override suspend fun deleteShoppingItem(id: Int): JsonObject = throw NotImplementedError()
        override suspend fun createDraft(body: DraftPut): Draft = throw NotImplementedError()
        override suspend fun updateDraft(id: Int, body: DraftPut): Draft = throw NotImplementedError()
        override suspend fun getConsumption(): Consumption = throw NotImplementedError()
    }

    private val api = FakeApi()
    private val settings = FakeSettings()
    private val tunnelsUp = mutableListOf<String>()
    private lateinit var cache: SnapshotCache

    private fun vm(onTunnelUp: (String) -> Unit = { tunnelsUp += it }): BrewViewModel {
        cache = SnapshotCache(tmp.root)
        return BrewViewModel(
            settings = settings,
            apiProvider = { api },
            vpn = VpnController(settings, onTunnelUp),
            cache = cache,
            strings = { "s$it" },
            io = dispatcher,
        )
    }

    private fun snapshotOf(beerName: String) = Snapshot(
        beers = listOf(Beer(id = 1, name = beerName)),
        recipes = emptyList(), inventory = emptyList(),
        brews = emptyList(), drafts = emptyList(), shopping = emptyList(),
    )

    // ── refreshAll ────────────────────────────────────────────────────────

    @Test
    fun `succes - donnees du serveur et cache mis a jour`() = runTest {
        api.beers = listOf(Beer(id = 2, name = "Fraîche"))
        val vm = vm()
        cache.save(snapshotOf("Périmée"))

        vm.refreshAll()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(listOf(Beer(id = 2, name = "Fraîche")), s.beers)
        assertTrue(s.loaded)
        assertFalse(s.offline)
        assertNotNull(s.dataAt)
        assertNull(s.error)
        assertEquals(api.beers, cache.load()!!.snapshot.beers)
    }

    @Test
    fun `serveur injoignable - cache affiche avec bandeau hors ligne`() = runTest {
        api.down = true
        val vm = vm()
        cache.save(snapshotOf("En cache"), savedAt = 42L)

        vm.refreshAll()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("En cache", s.beers.single().name)
        assertTrue(s.loaded)
        assertTrue(s.offline)
        assertEquals(42L, s.dataAt)
        // Repli sur le nom de l'exception quand elle n'a pas de message
        assertEquals("s${R.string.error_connect} : IOException", s.error)
    }

    @Test
    fun `serveur injoignable sans cache - erreur sans bandeau`() = runTest {
        api.down = true
        val vm = vm()

        vm.refreshAll()
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.loaded)
        assertFalse(s.offline)
        assertNotNull(s.error)
    }

    @Test
    fun `vpn configure - tunnel monte puis nouvelle tentative reussie`() = runTest {
        settings.wgAuto.value = true
        settings.wgTunnel.value = "brewhome"
        api.down = true
        api.beers = listOf(Beer(id = 3, name = "Via VPN"))
        // La montée du tunnel rétablit le réseau
        val vm = vm(onTunnelUp = { tunnelsUp += it; api.down = false })

        vm.refreshAll()
        advanceUntilIdle()

        assertEquals(listOf("brewhome"), tunnelsUp)
        val s = vm.state.value
        assertEquals("Via VPN", s.beers.single().name)
        assertFalse(s.offline)
        assertNull(s.error)
    }

    @Test
    fun `changement d'URL serveur - cache vide et etat remis a zero`() = runTest {
        val vm = vm()
        cache.save(snapshotOf("Ancien serveur"))
        api.down = true

        vm.saveServerUrl("nouveau:5000")
        advanceUntilIdle()

        assertEquals("http://nouveau:5000/", settings.serverUrl.value)
        assertNull(cache.load())
        assertFalse(vm.state.value.loaded)
    }

    // ── Ajout des manquants aux courses ───────────────────────────────────

    @Test
    fun `ajout partiel aux courses - liste rechargee malgre l'echec`() = runTest {
        api.failCreateFromCall = 2
        val vm = vm()

        vm.addNeedsToShopping(
            listOf(
                StockCheck.Need("Pilsner", "malt", 2.5, "kg"),
                StockCheck.Need("Munich", "malt", 1.0, "kg"),
            ),
        )

        val s = vm.state.value
        // Le 1er article est passé : l'état doit reproduire la vérité serveur
        assertEquals(listOf("Pilsner"), s.shopping.map { it.name })
        assertEquals("s${R.string.error_shopping_add} : refusé", s.error)
    }

    @Test
    fun `ajout complet aux courses - liste rechargee sans erreur`() = runTest {
        val vm = vm()

        vm.addNeedsToShopping(listOf(StockCheck.Need("Citra", "houblon", 30.0, "g")))

        val s = vm.state.value
        assertEquals(listOf("Citra"), s.shopping.map { it.name })
        assertNull(s.error)
    }
}
