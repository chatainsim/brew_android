package fr.easter.brewhome.ui

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Calculate
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    Tab("beers", R.string.tab_beers, Icons.Outlined.LocalDrink),
    Tab("recipes", R.string.tab_recipes, Icons.AutoMirrored.Outlined.MenuBook),
    Tab("inventory", R.string.tab_inventory, Icons.Outlined.Inventory2),
    Tab("brews", R.string.tab_brews, Icons.Outlined.Science),
    Tab("tools", R.string.tab_tools, Icons.Outlined.Calculate),
)

/** Onglet auquel appartient une route (pour la sélection de la barre du bas). */
private fun tabOf(route: String?): String? = when {
    route == null -> null
    route == "beers" || route.startsWith("beer/") -> "beers"
    route == "recipes" || route.startsWith("recipe/") ||
        route.startsWith("draft/") || route.startsWith("draftEdit/") -> "recipes"
    route == "inventory" -> "inventory"
    route == "brews" || route.startsWith("brew/") -> "brews"
    route == "tools" || route.startsWith("tools/") || route == "stats" -> "tools"
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewHomeApp(vm: BrewViewModel = viewModel(factory = BrewViewModel.Factory)) {
    val serverUrl by vm.serverUrl.collectAsState()
    val state by vm.state.collectAsState()
    val navController: NavHostController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    // Attente de la lecture DataStore
    if (serverUrl == null) return

    LaunchedEffect(serverUrl) {
        if (!serverUrl.isNullOrBlank() && !state.loaded && !state.loading) vm.refreshAll()
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

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val startDestination = if (serverUrl.isNullOrBlank()) "settings" else "beers"
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
                            currentRoute?.startsWith("recipe/") == true -> stringResource(R.string.title_recipe)
                            currentRoute?.startsWith("beer/") == true -> stringResource(R.string.title_beer)
                            currentRoute?.startsWith("brew/") == true -> stringResource(R.string.title_brew)
                            currentRoute?.startsWith("draft/") == true -> stringResource(R.string.title_draft)
                            currentRoute?.startsWith("draftEdit/") == true ->
                                if (backStack?.arguments?.getString("id") == "new")
                                    stringResource(R.string.title_draft_new)
                                else stringResource(R.string.title_draft_edit)
                            currentRoute == "stats" -> stringResource(R.string.title_stats)
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
                            shareText(context, ShareText.recipe(recipeToShare), subject)
                        }) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = stringResource(R.string.cd_share_recipe),
                            )
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
        ) {
            composable("beers") { BeersScreen(vm) { navController.navigate("beer/$it") } }
            composable("beer/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toIntOrNull()
                BeerDetailScreen(vm, id)
            }
            composable("recipes") {
                RecipesScreen(
                    vm,
                    onOpen = { navController.navigate("recipe/$it") },
                    onOpenDraft = { navController.navigate("draft/$it") },
                    onNewDraft = { navController.navigate("draftEdit/new") },
                )
            }
            composable("recipe/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toIntOrNull()
                RecipeDetailScreen(vm, id)
            }
            composable("draft/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toIntOrNull()
                DraftDetailScreen(vm, id)
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
            composable("brews") { BrewsScreen(vm) { navController.navigate("brew/$it") } }
            composable("brew/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toIntOrNull()
                BrewDetailScreen(vm, id) { navController.navigate("recipe/$it") }
            }
            composable("tools") {
                ToolsScreen(
                    onOpenStats = { navController.navigate("stats") },
                    onOpen = { navController.navigate("tools/$it") },
                )
            }
            composable("tools/{id}") { entry ->
                ToolScreen(entry.arguments?.getString("id"))
            }
            composable("stats") { StatsScreen(vm) }
            composable("settings") {
                SettingsScreen(vm) {
                    navController.navigate("beers") { popUpTo("settings") { inclusive = true } }
                }
            }
        }
    }
}
