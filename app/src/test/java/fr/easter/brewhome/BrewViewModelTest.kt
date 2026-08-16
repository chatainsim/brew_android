package fr.easter.brewhome

import fr.easter.brewhome.calc.StockCheck
import fr.easter.brewhome.data.AppSettings
import fr.easter.brewhome.data.Beer
import fr.easter.brewhome.data.BjcpStyle
import fr.easter.brewhome.data.Brew
import fr.easter.brewhome.data.BrewCreatePost
import fr.easter.brewhome.data.BrewApi
import fr.easter.brewhome.data.BrewLogEntry
import fr.easter.brewhome.data.BrewLogPost
import fr.easter.brewhome.data.FermReadingPost
import fr.easter.brewhome.data.BrewStep
import fr.easter.brewhome.data.BrewStepPost
import fr.easter.brewhome.data.BrewStepPut
import fr.easter.brewhome.data.DryhopDonePost
import fr.easter.brewhome.data.BrewPhoto
import fr.easter.brewhome.data.BrewPhotoPost
import fr.easter.brewhome.data.BulkCheckPut
import fr.easter.brewhome.data.BuyResult
import fr.easter.brewhome.data.BrewPut
import fr.easter.brewhome.data.CatalogItem
import fr.easter.brewhome.data.Consumption
import fr.easter.brewhome.data.CustomEvent
import fr.easter.brewhome.data.CustomEventPost
import fr.easter.brewhome.data.Draft
import fr.easter.brewhome.data.DraftPut
import fr.easter.brewhome.data.FermReading
import fr.easter.brewhome.data.InventoryItem
import fr.easter.brewhome.data.InventoryPost
import fr.easter.brewhome.data.QtyPatch
import fr.easter.brewhome.data.Recipe
import fr.easter.brewhome.data.RecipePost
import fr.easter.brewhome.data.SodaKeg
import fr.easter.brewhome.data.BeerArchivePatch
import fr.easter.brewhome.data.BeerPut
import fr.easter.brewhome.data.AiSuggestPost
import fr.easter.brewhome.data.AiSuggestResult
import fr.easter.brewhome.data.Spindle
import fr.easter.brewhome.data.TempSensor
import fr.easter.brewhome.data.TempReading
import fr.easter.brewhome.data.SpindleReading
import fr.easter.brewhome.data.ShoppingItem
import fr.easter.brewhome.data.ShoppingPost
import fr.easter.brewhome.data.Snapshot
import fr.easter.brewhome.data.SnapshotCache
import fr.easter.brewhome.data.StockPatch
import fr.easter.brewhome.data.TastingPut
import fr.easter.brewhome.data.UndoBuyPost
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
import kotlinx.serialization.json.jsonPrimitive
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
        override val notifsEnabled = MutableStateFlow(false)
        override suspend fun setServerUrl(url: String) { serverUrl.value = url }
        override suspend fun setThemeMode(mode: String) { themeMode.value = mode }
        override suspend fun setDynamicColor(enabled: Boolean) { dynamicColor.value = enabled }
        override suspend fun setWgAuto(enabled: Boolean) { wgAuto.value = enabled }
        override suspend fun setWgTunnel(name: String) { wgTunnel.value = name }
        override suspend fun setNotifsEnabled(enabled: Boolean) { notifsEnabled.value = enabled }
    }

    /** Serveur en mémoire : bascule [down] pour simuler la perte de réseau. */
    private class FakeApi : BrewApi {
        var down = false
        var beers = emptyList<Beer>()
        val shopping = mutableListOf<ShoppingItem>()
        var failCreateFromCall = Int.MAX_VALUE
        /** Simule un échec réseau ciblé sur une seule bière (rejeu partiellement échoué). */
        var failPatchForBeerId: Int? = null
        val patchBeerStockCalls = mutableListOf<Int>()
        /** Tant que non complété, bloque getBeers() en plein vol (simule un appel réseau en cours). */
        var getBeersGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        var getBeersCallCount = 0
        private var createCalls = 0
        private var nextId = 100

        private fun gate() {
            if (down) throw IOException()
        }

        override suspend fun getBeers(): List<Beer> {
            gate()
            getBeersCallCount++
            getBeersGate?.await()
            return beers
        }
        override suspend fun getRecipes(): List<Recipe> { gate(); return emptyList() }
        override suspend fun getInventory(): List<InventoryItem> { gate(); return emptyList() }
        override suspend fun getBrews(): List<Brew> { gate(); return emptyList() }
        override suspend fun createBrew(body: BrewCreatePost): Brew = throw NotImplementedError()
        override suspend fun deleteBrew(id: Int): JsonObject = throw NotImplementedError()
        override suspend fun deleteRecipe(id: Int): JsonObject = throw NotImplementedError()
        override suspend fun getTrash(): fr.easter.brewhome.data.Trash = throw NotImplementedError()
        override suspend fun restoreRecipe(id: Int): JsonObject = throw NotImplementedError()
        override suspend fun restoreBrew(id: Int): JsonObject = throw NotImplementedError()
        override suspend fun restoreBeer(id: Int): JsonObject = throw NotImplementedError()
        override suspend fun restoreInventoryItem(id: Int): JsonObject = throw NotImplementedError()
        override suspend fun deleteDraft(id: Int): JsonObject = throw NotImplementedError()
        override suspend fun getDrafts(): List<Draft> { gate(); return emptyList() }
        override suspend fun getShoppingList(): List<ShoppingItem> { gate(); return shopping.toList() }
        override suspend fun getAppSettings(): JsonObject { gate(); return JsonObject(emptyMap()) }
        override suspend fun saveAppSettings(body: JsonObject): JsonObject = throw NotImplementedError()

        override suspend fun createShoppingItem(body: ShoppingPost): ShoppingItem {
            gate()
            createCalls++
            if (createCalls >= failCreateFromCall) throw IOException("refusé")
            val item = ShoppingItem(nextId++, body.name, body.category, body.quantity, body.unit)
            shopping += item
            return item
        }

        override suspend fun patchBeerStock(id: Int, body: StockPatch): Beer {
            gate()
            patchBeerStockCalls += id
            if (id == failPatchForBeerId) throw IOException("échec réseau ciblé")
            val updated = beers.first { it.id == id }.let {
                it.copy(
                    stock33 = body.stock33 ?: it.stock33,
                    stock75 = body.stock75 ?: it.stock75,
                    kegLiters = body.kegLiters ?: it.kegLiters,
                )
            }
            beers = beers.map { if (it.id == id) updated else it }
            return updated
        }

        override suspend fun deleteShoppingItem(id: Int): JsonObject {
            gate()
            shopping.removeAll { it.id == id }
            return JsonObject(emptyMap())
        }

        override suspend fun bulkCheckShopping(body: BulkCheckPut): JsonObject {
            gate()
            shopping.replaceAll {
                if (it.id in body.ids) it.copy(checked = if (body.checked) 1 else 0) else it
            }
            return JsonObject(emptyMap())
        }

        /** Transfert : les cochés partent dans [bought], le reçu permet de les restaurer. */
        private val bought = mutableListOf<ShoppingItem>()
        val undoBuyBodies = mutableListOf<UndoBuyPost>()

        override suspend fun buyShoppingItems(): BuyResult {
            gate()
            val checked = shopping.filter { (it.checked ?: 0) == 1 }
            shopping.removeAll(checked)
            bought += checked
            return BuyResult(count = checked.size, boughtIds = checked.map { it.id })
        }

        override suspend fun undoBuyShopping(body: UndoBuyPost): JsonObject {
            gate()
            undoBuyBodies += body
            val restored = bought.filter { it.id in body.boughtIds }
            bought.removeAll(restored)
            shopping += restored.map { it.copy(checked = 0) }
            return JsonObject(emptyMap())
        }

        // Non exercés par ces tests
        override suspend fun putBeerTasting(id: Int, body: TastingPut): Beer = throw NotImplementedError()
        override suspend fun getRecipe(id: Int): Recipe = throw NotImplementedError()
        override suspend fun patchInventoryQty(id: Int, body: QtyPatch): InventoryItem = throw NotImplementedError()
        override suspend fun createInventoryItem(body: InventoryPost): InventoryItem = throw NotImplementedError()
        override suspend fun updateInventoryItem(id: Int, body: InventoryPost): InventoryItem = throw NotImplementedError()
        override suspend fun deleteInventoryItem(id: Int): JsonObject = throw NotImplementedError()
        override suspend fun getInventoryHistory(id: Int): fr.easter.brewhome.data.InventoryHistory = throw NotImplementedError()
        override suspend fun getBrewFermentation(id: Int): List<FermReading> = throw NotImplementedError()
        override suspend fun getBrewLog(id: Int): List<BrewLogEntry> = throw NotImplementedError()
        override suspend fun getBrewPhotos(id: Int): List<BrewPhoto> = throw NotImplementedError()
        override suspend fun addBrewPhoto(id: Int, body: BrewPhotoPost): BrewPhoto = throw NotImplementedError()
        override suspend fun deleteBrewPhoto(id: Int, photoId: Int): JsonObject = throw NotImplementedError()
        override suspend fun addBrewLog(id: Int, body: BrewLogPost): JsonObject = throw NotImplementedError()
        override suspend fun addBrewFermentation(id: Int, body: FermReadingPost): JsonObject = throw NotImplementedError()
        override suspend fun deleteBrewFermentation(id: Int, readingId: Int): JsonObject = throw NotImplementedError()
        override suspend fun deleteBrewLog(id: Int, entryId: Int): JsonObject = throw NotImplementedError()
        override suspend fun patchBrewPhoto(id: Int, photoId: Int, body: JsonObject): JsonObject = throw NotImplementedError()
        override suspend fun getBrewSteps(id: Int): List<BrewStep> = throw NotImplementedError()
        override suspend fun addBrewStep(id: Int, body: BrewStepPost): BrewStep = throw NotImplementedError()
        override suspend fun updateBrewStep(stepId: Int, body: BrewStepPut): BrewStep = throw NotImplementedError()
        override suspend fun deleteBrewStep(stepId: Int): JsonObject = throw NotImplementedError()
        override suspend fun markDryhopDone(id: Int, body: DryhopDonePost): JsonObject = throw NotImplementedError()
        override suspend fun getCatalog(): List<CatalogItem> = throw NotImplementedError()
        override suspend fun createCatalogItem(body: fr.easter.brewhome.data.CatalogPost): CatalogItem = throw NotImplementedError()
        override suspend fun updateCatalogItem(id: Int, body: fr.easter.brewhome.data.CatalogPost): CatalogItem = throw NotImplementedError()
        override suspend fun deleteCatalogItem(id: Int): JsonObject = throw NotImplementedError()
        override suspend fun getChecklistTemplates(): List<fr.easter.brewhome.data.ChecklistTemplate> = throw NotImplementedError()
        /** Ids envoyés dans le champ "items" de chaque appel à createChecklistTemplate. */
        val checklistTemplateItemIds = mutableListOf<List<String>>()

        override suspend fun createChecklistTemplate(body: JsonObject): fr.easter.brewhome.data.ChecklistTemplate {
            val ids = (body["items"] as kotlinx.serialization.json.JsonArray).map {
                (it as JsonObject)["id"]!!.jsonPrimitive.content
            }
            checklistTemplateItemIds += ids
            return fr.easter.brewhome.data.ChecklistTemplate(id = checklistTemplateItemIds.size, name = "t")
        }
        override suspend fun deleteChecklistTemplate(tid: Int): JsonObject = throw NotImplementedError()
        override suspend fun getBrewChecklist(id: Int): fr.easter.brewhome.data.BrewChecklist = throw NotImplementedError()
        override suspend fun saveBrewChecklist(id: Int, body: JsonObject): fr.easter.brewhome.data.BrewChecklist = throw NotImplementedError()
        override suspend fun createDraft(body: DraftPut): Draft = throw NotImplementedError()
        override suspend fun updateDraft(id: Int, body: DraftPut): Draft = throw NotImplementedError()
        override suspend fun aiDraftSuggest(body: AiSuggestPost): AiSuggestResult = throw NotImplementedError()
        override suspend fun importBeerXml(body: okhttp3.RequestBody): JsonObject = throw NotImplementedError()
        override suspend fun getConsumption(): Consumption = throw NotImplementedError()
        override suspend fun getCustomEvents(): List<CustomEvent> = throw NotImplementedError()
        override suspend fun createCustomEvent(body: CustomEventPost): CustomEvent = throw NotImplementedError()
        override suspend fun deleteCustomEvent(id: Int): JsonObject = throw NotImplementedError()
        override suspend fun getRecipeRaw(id: Int): JsonObject = throw NotImplementedError()
        override suspend fun createRecipe(body: RecipePost): Recipe = throw NotImplementedError()
        override suspend fun createRecipeRaw(body: JsonObject): JsonObject = throw NotImplementedError()
        override suspend fun updateRecipe(id: Int, body: JsonObject): JsonObject = throw NotImplementedError()
        override suspend fun getBjcpStyles(): List<BjcpStyle> = throw NotImplementedError()
        override suspend fun reorderRecipes(body: List<fr.easter.brewhome.data.ReorderEntry>): JsonObject = throw NotImplementedError()
        override suspend fun reorderBeers(body: List<fr.easter.brewhome.data.ReorderEntry>): JsonObject = throw NotImplementedError()
        override suspend fun reorderBrews(body: List<fr.easter.brewhome.data.ReorderEntry>): JsonObject = throw NotImplementedError()
        override suspend fun reorderDrafts(body: List<fr.easter.brewhome.data.ReorderEntry>): JsonObject = throw NotImplementedError()
        override suspend fun reorderSodaKegs(body: List<fr.easter.brewhome.data.ReorderEntry>): JsonObject = throw NotImplementedError()
        override suspend fun reorderSpindles(body: List<fr.easter.brewhome.data.ReorderEntry>): JsonObject = throw NotImplementedError()
        override suspend fun reorderTempSensors(body: List<fr.easter.brewhome.data.ReorderEntry>): JsonObject = throw NotImplementedError()
        override suspend fun reorderShopping(body: List<fr.easter.brewhome.data.ReorderEntry>): JsonObject = throw NotImplementedError()
        override suspend fun getRecipeHistory(id: Int): List<fr.easter.brewhome.data.RecipeVersion> = throw NotImplementedError()
        override suspend fun restoreRecipeVersion(id: Int, versionId: Int): Recipe = throw NotImplementedError()
        override suspend fun updateBrew(id: Int, body: BrewPut): JsonObject = throw NotImplementedError()
        override suspend fun getSpindles(): List<Spindle> = throw NotImplementedError()
        override suspend fun patchSpindle(id: Int, body: JsonObject): JsonObject = throw NotImplementedError()
        override suspend fun getSpindleReadings(id: Int, hours: Int?): List<SpindleReading> = throw NotImplementedError()
        override suspend fun getTempSensors(): List<TempSensor> = throw NotImplementedError()
        override suspend fun getTempReadings(id: Int, hours: Int?): List<TempReading> = throw NotImplementedError()
        override suspend fun getSodaKegs(): List<SodaKeg> = throw NotImplementedError()
        override suspend fun getSodaKegsRaw(): List<JsonObject> = throw NotImplementedError()
        override suspend fun updateSodaKeg(id: Int, body: JsonObject): JsonObject = throw NotImplementedError()
        override suspend fun createSodaKeg(body: JsonObject): JsonObject = throw NotImplementedError()
        override suspend fun deleteSodaKeg(id: Int): JsonObject = throw NotImplementedError()
        override suspend fun patchBeerArchived(id: Int, body: BeerArchivePatch): Beer = throw NotImplementedError()
        override suspend fun updateBeer(id: Int, body: BeerPut): Beer = throw NotImplementedError()
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
            pending = fr.easter.brewhome.data.PendingQueue(tmp.root),
            guideStore = fr.easter.brewhome.data.BrewGuideStore(tmp.root),
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

    // ── Rejeu de la queue hors ligne ─────────────────────────────────────

    @Test
    fun `replayPending - un op en echec reste en file, pas de perte silencieuse`() = runTest {
        api.beers = listOf(Beer(id = 1, name = "A", stock33 = 5), Beer(id = 2, name = "B", stock33 = 5))
        api.failPatchForBeerId = 2
        val pending = fr.easter.brewhome.data.PendingQueue(tmp.root)
        pending.add(fr.easter.brewhome.data.PendingStockOp(beerId = 1, d33 = -1))
        pending.add(fr.easter.brewhome.data.PendingStockOp(beerId = 2, d33 = -1))
        val vm = vm()

        vm.refreshAll()
        advanceUntilIdle()

        // Bière 1 rejouée avec succès sur le serveur
        assertEquals(4, api.beers.first { it.id == 1 }.stock33)
        // Bière 2 a échoué : l'op reste en file au lieu d'être perdue
        val remaining = pending.load()
        assertEquals(listOf(2), remaining.map { it.beerId })
        assertEquals(-1, remaining.single().d33)
    }

    @Test
    fun `replayPending - bete pas encore synchronisee localement reste en file`() = runTest {
        api.beers = emptyList() // la bière n'est pas (encore) connue côté client
        val pending = fr.easter.brewhome.data.PendingQueue(tmp.root)
        pending.add(fr.easter.brewhome.data.PendingStockOp(beerId = 42, d33 = -1))
        val vm = vm()

        vm.refreshAll()
        advanceUntilIdle()

        assertEquals(listOf(42), pending.load().map { it.beerId })
    }

    @Test
    fun `refreshAll - un rafraichissement deja en cours n'en relance pas un second`() = runTest {
        api.beers = listOf(Beer(id = 1, name = "A", stock33 = 5))
        // Bloque le premier appel réseau en plein vol, comme un aller-retour lent
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        api.getBeersGate = gate
        val vm = vm()

        vm.refreshAll()
        // Le premier rafraîchissement est encore en cours (bloqué dans getBeers())
        assertTrue(vm.state.value.loading)
        val callsWhileFirstInFlight = api.getBeersCallCount
        // Sans le garde-fou, ce second appel relancerait un rafraîchissement en parallèle
        // et rejouerait la queue hors ligne deux fois.
        vm.refreshAll()
        assertEquals(callsWhileFirstInFlight, api.getBeersCallCount)

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.state.value.loading)
        assertEquals("A", vm.state.value.beers.single().name)
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

    // ── Checklists de brassage ───────────────────────────────────────────

    @Test
    fun `createChecklistTemplate - les ids d'items ne se chevauchent pas entre deux modeles`() = runTest {
        val vm = vm()

        vm.createChecklistTemplate("IPA", null, listOf("Empâtage", "Ébullition", "Refroidissement"))
        advanceUntilIdle()
        vm.createChecklistTemplate("Stout", null, listOf("Empâtage", "Ébullition"))
        advanceUntilIdle()

        val (idsIpa, idsStout) = api.checklistTemplateItemIds
        // Avant le fix, les ids étaient purement positionnels (item_1, item_2...) et se
        // chevauchaient entre modèles : changer de modèle sur un brassin pouvait alors
        // conserver à tort des coches d'un autre modèle (BrewChecklistScreen.onSelect).
        assertTrue(idsIpa.toSet().intersect(idsStout.toSet()).isEmpty())
        assertEquals(3, idsIpa.toSet().size)
        assertEquals(2, idsStout.toSet().size)
    }

    // ── Widget (SnapshotCache) ───────────────────────────────────────────

    @Test
    fun `adjustBeerStock - persiste le cache disque pour que le widget ne reste pas perime`() = runTest {
        api.beers = listOf(Beer(id = 1, name = "Ambrée", stock33 = 4))
        val vm = vm()
        vm.refreshAll()
        advanceUntilIdle()

        vm.adjustBeerStock(vm.state.value.beers.single(), d33 = -1)
        advanceUntilIdle()

        assertEquals(3, vm.state.value.beers.single().stock33)
        // Avant le fix, seul un refreshAll() complet touchait SnapshotCache : le widget
        // (qui lit directement ce cache) restait périmé après un simple ajustement de stock.
        assertEquals(3, cache.load()!!.snapshot.beers.single().stock33)
    }

    // ── Annulation ────────────────────────────────────────────────────────

    @Test
    fun `rafale de plus sur le stock - annuler retablit la valeur de depart`() = runTest {
        api.beers = listOf(Beer(id = 1, name = "Ambrée", stock33 = 4))
        val vm = vm()
        vm.refreshAll()
        advanceUntilIdle()
        val beer = { vm.state.value.beers.single() }

        vm.adjustBeerStock(beer(), d33 = 1)
        advanceUntilIdle()
        vm.adjustBeerStock(beer(), d33 = 1)
        advanceUntilIdle()
        assertEquals(6, beer().stock33)

        // La rafale partage la même clé : une seule annulation défait les deux crans
        vm.performUndo(vm.undo.value!!)
        advanceUntilIdle()
        assertEquals(4, beer().stock33)
        assertNull(vm.undo.value)
    }

    @Test
    fun `suppression d'un article de courses - annuler le recree`() = runTest {
        api.shopping += ShoppingItem(id = 1, name = "Citra", category = "houblon")
        val vm = vm()
        vm.refreshAll()
        advanceUntilIdle()

        vm.deleteShoppingItem(vm.state.value.shopping.single())
        advanceUntilIdle()
        assertTrue(vm.state.value.shopping.isEmpty())

        vm.performUndo(vm.undo.value!!)
        advanceUntilIdle()
        assertEquals(listOf("Citra"), vm.state.value.shopping.map { it.name })
    }

    @Test
    fun `transfert des achats - annuler repasse le recu a undo-buy`() = runTest {
        api.shopping += ShoppingItem(id = 1, name = "Pilsner", category = "malt", checked = 1)
        val vm = vm()

        vm.buyCheckedShopping()
        advanceUntilIdle()
        assertTrue(vm.state.value.shopping.isEmpty())

        val notice = vm.undo.value!!
        assertTrue(notice.long)
        vm.performUndo(notice)
        advanceUntilIdle()
        assertEquals(listOf(1), api.undoBuyBodies.single().boughtIds)
        assertEquals(listOf("Pilsner"), vm.state.value.shopping.map { it.name })
    }

    // ── Guide de brassage ────────────────────────────────────────────────

    @Test
    fun `loadBrewGuide expose un etat par defaut si rien n'est persiste`() = runTest {
        val vm = vm()
        vm.loadBrewGuide("recipe_1")
        advanceUntilIdle()
        assertEquals(fr.easter.brewhome.data.BrewGuideState(), vm.brewGuideState.value)
    }

    @Test
    fun `updateBrewGuide met a jour l'etat et le persiste via le store`() = runTest {
        val vm = vm()
        vm.loadBrewGuide("recipe_1")
        advanceUntilIdle()

        vm.updateBrewGuide("recipe_1") { it.copy(step = 2, checkedItems = setOf("prep_1")) }
        advanceUntilIdle()

        assertEquals(2, vm.brewGuideState.value?.step)
        assertEquals(setOf("prep_1"), vm.brewGuideState.value?.checkedItems)

        // Une nouvelle instance relit bien l'état persisté par le store injecté
        val reloaded = fr.easter.brewhome.data.BrewGuideStore(tmp.root).load("recipe_1")
        assertEquals(2, reloaded?.step)
        assertEquals(setOf("prep_1"), reloaded?.checkedItems)
    }
}
