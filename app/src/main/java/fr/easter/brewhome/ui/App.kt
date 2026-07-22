package fr.easter.brewhome.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.glance.appwidget.updateAll
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.share.ShareText
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class Tab(val route: String, @StringRes val labelRes: Int, val icon: ImageVector)

val tabs = listOf(
    Tab("home", R.string.tab_home, Icons.Outlined.Home),
    Tab("beers", R.string.tab_beers, Icons.Outlined.LocalDrink),
    Tab("recipes", R.string.tab_recipes, Icons.AutoMirrored.Outlined.MenuBook),
    Tab("inventory", R.string.tab_inventory, Icons.Outlined.Inventory2),
    Tab("brews", R.string.tab_brews, Icons.Outlined.Science),
    Tab("tools", R.string.tab_tools, Icons.Outlined.Calculate),
)

/** Onglet auquel appartient une route (pour la sélection de la barre du bas). */
private fun tabOf(route: String?): String? = when {
    route == null -> null
    route == "home" -> "home"
    route == "beers" || route.startsWith("beer/") || route.startsWith("beerEdit/") -> "beers"
    route == "recipes" || route.startsWith("recipe/") || route.startsWith("recipeEdit/") ||
        route.startsWith("recipeFromDraft/") ||
        route.startsWith("draft/") || route.startsWith("draftEdit/") -> "recipes"
    route == "inventory" || route == "shopping" -> "inventory"
    route == "brews" || route.startsWith("brew/") || route.startsWith("brewEdit/") -> "brews"
    route == "tools" || route.startsWith("tools/") ||
        route == "stats" || route == "calendar" ||
        route == "spindles" || route.startsWith("spindle/") ||
        route == "temps" || route.startsWith("temp/") || route == "kegs" ||
        route == "trash" -> "tools"
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewHomeApp(
    vm: BrewViewModel = viewModel(factory = BrewViewModel.Factory),
    /** Route demandée par un raccourci d'app (appui long sur l'icône). */
    shortcutRoute: String? = null,
    onShortcutHandled: () -> Unit = {},
) {
    val serverUrl by vm.serverUrl.collectAsState()
    val state by vm.state.collectAsState()
    val navController: NavHostController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    // Attente de la lecture DataStore
    if (serverUrl == null) return

    LaunchedEffect(serverUrl) {
        if (!serverUrl.isNullOrBlank() && !state.loaded && !state.loading) vm.refreshAll()
    }

    LaunchedEffect(shortcutRoute, serverUrl) {
        if (shortcutRoute != null && !serverUrl.isNullOrBlank()) {
            navController.navigate(shortcutRoute) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
            }
            onShortcutHandled()
        }
    }

    // Avant le premier chargement, l'erreur reste affichée dans l'écran
    // « Réessayer » ; ensuite elle passe en snackbar.
    LaunchedEffect(state.error, state.loaded) {
        val err = state.error
        if (err != null && state.loaded) {
            snackbar.showSnackbar(err)
            vm.clearError()
        }
    }

    // Rappels de brassage : (re)programmer les notifications locales quand les
    // données ou le réglage changent (dry hop, fin de fermentation…).
    val notifsEnabled by vm.notifsEnabled.collectAsState()
    val customEvents by vm.customEvents.collectAsState()
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(notifsEnabled) {
        if (notifsEnabled) vm.loadCustomEvents()
    }
    LaunchedEffect(notifsEnabled, state.loaded, state.brews, state.beers, state.drafts, customEvents) {
        if (!notifsEnabled) {
            fr.easter.brewhome.notif.BrewReminders.cancelAll(appContext)
            return@LaunchedEffect
        }
        if (!state.loaded || !fr.easter.brewhome.notif.BrewReminders.hasPermission(appContext)) {
            return@LaunchedEffect
        }
        val today = java.time.LocalDate.now()
        val events = fr.easter.brewhome.calc.CalendarEvents.agenda(
            from = today,
            to = today.plusDays(60),
            brews = state.brews,
            recipes = state.recipes.associateBy { it.id },
            beers = state.beers,
            drafts = state.drafts,
            customEvents = customEvents ?: emptyList(),
        )
        fr.easter.brewhome.notif.BrewReminders.schedule(
            appContext,
            fr.easter.brewhome.notif.BrewReminders.remindersFrom(events),
        )
    }

    // Rafraîchir le widget écran d'accueil quand de nouvelles données arrivent
    LaunchedEffect(state.dataAt) {
        if (state.dataAt != null) {
            runCatching {
                fr.easter.brewhome.widget.BrewWidget().updateAll(appContext)
            }
        }
    }

    // Actions annulables : snackbar avec bouton « Annuler »
    val undoNotice by vm.undo.collectAsState()
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(undoNotice) {
        val notice = undoNotice ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = notice.message,
            actionLabel = undoLabel,
            duration = if (notice.long) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) vm.performUndo(notice)
        else vm.dismissUndo(notice)
    }

    // Import BeerXML : sélecteur de fichier → lecture → envoi au serveur
    val importLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val xml = runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }.getOrNull()
            if (!xml.isNullOrBlank()) vm.importBeerXml(xml)
        }
    }

    // Cible de suppression en attente de confirmation : (kind, id)
    var deleteTarget by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<Pair<String, Int>?>(null)
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val startDestination = if (serverUrl.isNullOrBlank()) "settings" else "home"
    val currentTab = tabOf(currentRoute)
    val canGoBack = currentRoute != null && currentRoute != startDestination &&
        currentRoute !in tabs.map { it.route }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            currentRoute == "settings" -> stringResource(R.string.title_settings)
                            currentRoute == "search" -> stringResource(R.string.title_search)
                            currentRoute?.startsWith("recipe/") == true -> stringResource(R.string.title_recipe)
                            currentRoute?.startsWith("recipeEdit/") == true ->
                                if (backStack?.arguments?.getString("id") == "new")
                                    stringResource(R.string.title_recipe_new)
                                else stringResource(R.string.title_recipe_edit)
                            currentRoute?.startsWith("recipeFromDraft/") == true ->
                                stringResource(R.string.title_recipe_new)
                            currentRoute?.startsWith("beer/") == true -> stringResource(R.string.title_beer)
                            currentRoute?.startsWith("beerEdit/") == true -> stringResource(R.string.title_beer_edit)
                            currentRoute?.startsWith("brew/") == true -> stringResource(R.string.title_brew)
                            currentRoute?.startsWith("brewEdit/") == true -> stringResource(R.string.title_brew_edit)
                            currentRoute?.startsWith("draft/") == true -> stringResource(R.string.title_draft)
                            currentRoute?.startsWith("draftEdit/") == true ->
                                if (backStack?.arguments?.getString("id") == "new")
                                    stringResource(R.string.title_draft_new)
                                else stringResource(R.string.title_draft_edit)
                            currentRoute == "stats" -> stringResource(R.string.title_stats)
                            currentRoute == "calendar" -> stringResource(R.string.title_calendar)
                            currentRoute == "spindles" || currentRoute?.startsWith("spindle/") == true ->
                                stringResource(R.string.title_spindles)
                            currentRoute == "temps" || currentRoute?.startsWith("temp/") == true ->
                                stringResource(R.string.title_temps)
                            currentRoute == "kegs" -> stringResource(R.string.title_kegs)
                            currentRoute == "trash" -> stringResource(R.string.title_trash)
                            currentRoute == "shopping" -> stringResource(R.string.title_shopping)
                            currentRoute == "tools" -> stringResource(R.string.tab_tools)
                            currentRoute?.startsWith("tools/") == true ->
                                toolTitle(backStack?.arguments?.getString("id"))
                            else -> stringResource(R.string.app_name)
                        },
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    }
                },
                actions = {
                    // Partage de la recette ouverte
                    val recipeToShare = if (currentRoute?.startsWith("recipe/") == true) {
                        val id = backStack?.arguments?.getString("id")?.toIntOrNull()
                        state.recipes.find { it.id == id }
                    } else null
                    if (recipeToShare != null) {
                        val context = LocalContext.current
                        val subject = stringResource(R.string.share_subject_recipe, recipeToShare.name)
                        IconButton(onClick = {
                            navController.navigate("recipeEdit/${recipeToShare.id}")
                        }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.cd_edit_recipe),
                            )
                        }
                        IconButton(onClick = {
                            vm.duplicateRecipe(recipeToShare) { newId ->
                                navController.navigate("recipe/$newId")
                            }
                        }) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = stringResource(R.string.cd_duplicate_recipe),
                            )
                        }
                        IconButton(onClick = {
                            shareText(context, ShareText.recipe(recipeToShare), subject)
                        }) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = stringResource(R.string.cd_share_recipe),
                            )
                        }
                        IconButton(onClick = { deleteTarget = "recipe" to recipeToShare.id }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete_recipe))
                        }
                    }
                    // Partage du brouillon ouvert
                    val draftToShare = if (currentRoute?.startsWith("draft/") == true) {
                        val id = backStack?.arguments?.getString("id")?.toIntOrNull()
                        state.drafts.find { it.id == id }
                    } else null
                    if (draftToShare != null) {
                        val context = LocalContext.current
                        val subject = stringResource(R.string.share_subject_draft, draftToShare.title)
                        IconButton(onClick = {
                            navController.navigate("draftEdit/${draftToShare.id}")
                        }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.cd_edit_draft),
                            )
                        }
                        IconButton(onClick = {
                            shareText(context, ShareText.draft(draftToShare), subject)
                        }) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = stringResource(R.string.cd_share_draft),
                            )
                        }
                        IconButton(onClick = { deleteTarget = "draft" to draftToShare.id }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                    // Partage du stock complet
                    if (currentRoute == "inventory" && state.inventory.isNotEmpty()) {
                        val context = LocalContext.current
                        val subject = stringResource(R.string.share_subject_inventory)
                        IconButton(onClick = {
                            val date = LocalDate.now()
                                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                            shareText(context, ShareText.inventory(state.inventory, date), subject)
                        }) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = stringResource(R.string.cd_share_inventory),
                            )
                        }
                    }
                    // Ouvre la vitrine GitHub Pages (ou à défaut la page Cave du site)
                    if (currentRoute == "beers" && !serverUrl.isNullOrBlank()) {
                        val context = LocalContext.current
                        IconButton(onClick = {
                            vm.openCaveOnline { url ->
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.cd_open_vitrine),
                            )
                        }
                    }
                    // Édition + suppression du brassin ouvert
                    if (currentRoute?.startsWith("brew/") == true) {
                        val id = backStack?.arguments?.getString("id")?.toIntOrNull()
                        if (id != null && state.brews.any { it.id == id }) {
                            IconButton(onClick = { navController.navigate("brewEdit/$id") }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.cd_edit_brew))
                            }
                            IconButton(onClick = { deleteTarget = "brew" to id }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete_brew))
                            }
                        }
                    }
                    // Import BeerXML depuis la liste des recettes
                    if (currentRoute == "recipes") {
                        IconButton(onClick = { importLauncher.launch("*/*") }) {
                            Icon(
                                Icons.Filled.FileUpload,
                                contentDescription = stringResource(R.string.recipe_import),
                            )
                        }
                    }
                    // Édition de la bière ouverte
                    if (currentRoute?.startsWith("beer/") == true) {
                        val id = backStack?.arguments?.getString("id")?.toIntOrNull()
                        if (id != null && state.beers.any { it.id == id }) {
                            IconButton(onClick = { navController.navigate("beerEdit/$id") }) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.cd_edit_beer),
                                )
                            }
                        }
                    }
                    val dataScreen = currentTab != null && currentTab != "tools"
                    if (state.loading && dataScreen) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(24.dp),
                            strokeWidth = 2.5.dp,
                        )
                    } else if (dataScreen) {
                        IconButton(onClick = { vm.refreshAll() }) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.cd_refresh),
                            )
                        }
                    }
                    if (currentRoute != "search" && currentRoute != "settings") {
                        IconButton(onClick = { navController.navigate("search") }) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = stringResource(R.string.search_hint),
                            )
                        }
                    }
                    if (currentRoute != "settings") {
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.title_settings),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val label = stringResource(tab.labelRes)
                    NavigationBarItem(
                        selected = currentTab == tab.route,
                        onClick = {
                            // Réglages = écran transitoire : le retirer de la pile
                            // avant de changer d'onglet, sinon il est sauvegardé
                            // dans l'état de l'onglet quitté et réapparaît par-dessus
                            // le contenu quand on revient sur cet onglet.
                            if (currentRoute == "settings" && currentRoute != startDestination) {
                                navController.popBackStack()
                            }
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
            // Fondu + léger glissement vertical entre les écrans
            enterTransition = {
                fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 20 }
            },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = {
                fadeOut(tween(220)) + slideOutVertically(tween(220)) { it / 20 }
            },
        ) {
            composable("home") {
                DashboardScreen(
                    vm,
                    onOpenBrew = { navController.navigate("brew/$it") },
                    onOpen = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable("beers") { BeersScreen(vm) { navController.navigate("beer/$it") } }
            composable("beer/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toIntOrNull()
                BeerDetailScreen(vm, id)
            }
            composable("beerEdit/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toIntOrNull()
                BeerEditScreen(vm, id) { navController.navigateUp() }
            }
            composable("recipes") {
                RecipesScreen(
                    vm,
                    onOpen = { navController.navigate("recipe/$it") },
                    onOpenDraft = { navController.navigate("draft/$it") },
                    onNewDraft = { navController.navigate("draftEdit/new") },
                    onNewRecipe = { navController.navigate("recipeEdit/new") },
                )
            }
            composable("recipe/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toIntOrNull()
                RecipeDetailScreen(vm, id) { brewId -> navController.navigate("brew/$brewId") }
            }
            composable("recipeEdit/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toIntOrNull() // "new" → null
                RecipeEditScreen(vm, id) { navController.navigateUp() }
            }
            composable("recipeFromDraft/{draftId}") { entry ->
                val draftId = entry.arguments?.getString("draftId")?.toIntOrNull()
                RecipeEditScreen(vm, recipeId = null, fromDraftId = draftId) {
                    // Après le transfert : retour à la liste des recettes
                    navController.navigate("recipes") { popUpTo("recipes") { inclusive = true } }
                }
            }
            composable("draft/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toIntOrNull()
                DraftDetailScreen(vm, id) { navController.navigate("recipeFromDraft/$it") }
            }
            composable("draftEdit/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toIntOrNull() // "new" → null
                DraftEditScreen(vm, id) { saved ->
                    if (id == null) {
                        // Création : remplace l'éditeur par la fiche du nouveau brouillon
                        navController.navigate("draft/${saved.id}") {
                            popUpTo("recipes")
                        }
                    } else {
                        navController.navigateUp()
                    }
                }
            }
            composable("inventory") { InventoryScreen(vm) }
            composable("shopping") { InventoryScreen(vm, initialShopping = true) }
            composable("brews") { BrewsScreen(vm) { navController.navigate("brew/$it") } }
            composable("brew/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toIntOrNull()
                BrewDetailScreen(vm, id) { navController.navigate("recipe/$it") }
            }
            composable("brewEdit/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toIntOrNull()
                BrewEditScreen(vm, id) { navController.navigateUp() }
            }
            composable("tools") {
                ToolsScreen(
                    onOpenStats = { navController.navigate("stats") },
                    onOpenCalendar = { navController.navigate("calendar") },
                    onOpenSpindles = { navController.navigate("spindles") },
                    onOpenTemps = { navController.navigate("temps") },
                    onOpenKegs = { navController.navigate("kegs") },
                    onOpenTrash = { navController.navigate("trash") },
                    onOpen = { navController.navigate("tools/$it") },
                )
            }
            composable("kegs") { KegsScreen(vm) }
            composable("trash") { TrashScreen(vm) }
            composable("spindles") {
                SpindlesScreen(vm) { navController.navigate("spindle/$it") }
            }
            composable("spindle/{id}") { entry ->
                SpindleDetailScreen(vm, entry.arguments?.getString("id")?.toIntOrNull())
            }
            composable("temps") {
                TempScreen(vm) { navController.navigate("temp/$it") }
            }
            composable("temp/{id}") { entry ->
                TempDetailScreen(vm, entry.arguments?.getString("id")?.toIntOrNull())
            }
            composable("calendar") {
                CalendarScreen(
                    vm,
                    onOpenBrew = { navController.navigate("brew/$it") },
                    onOpenDraft = { navController.navigate("draft/$it") },
                )
            }
            composable("tools/{id}") { entry ->
                ToolScreen(entry.arguments?.getString("id"))
            }
            composable("stats") { StatsScreen(vm) }
            composable("settings") {
                SettingsScreen(vm) {
                    navController.navigate("home") { popUpTo("settings") { inclusive = true } }
                }
            }
            composable("search") {
                SearchScreen(
                    vm,
                    onOpenBeer = { navController.navigate("beer/$it") },
                    onOpenRecipe = { navController.navigate("recipe/$it") },
                    onOpenBrew = { navController.navigate("brew/$it") },
                )
            }
        }
    }

    // Confirmation de suppression (recette / brouillon / brassin)
    deleteTarget?.let { (kind, id) ->
        val msg = when (kind) {
            "recipe" -> R.string.delete_recipe_confirm
            "draft" -> R.string.delete_draft_confirm
            else -> R.string.delete_brew_confirm
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteTarget = null },
            text = { Text(stringResource(msg)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    when (kind) {
                        "recipe" -> vm.deleteRecipe(id) { navController.navigateUp() }
                        "draft" -> vm.deleteDraft(id) { navController.navigateUp() }
                        else -> vm.deleteBrew(id) { navController.navigateUp() }
                    }
                    deleteTarget = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
