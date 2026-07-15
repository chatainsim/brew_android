package fr.easter.brewhome.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.MenuBook
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
import fr.easter.brewhome.share.ShareText
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class Tab(val route: String, val label: String, val icon: ImageVector)

val tabs = listOf(
    Tab("beers", "Cave", Icons.Outlined.LocalDrink),
    Tab("recipes", "Recettes", Icons.AutoMirrored.Outlined.MenuBook),
    Tab("inventory", "Stock", Icons.Outlined.Inventory2),
    Tab("brews", "Brassins", Icons.Outlined.Science),
    Tab("tools", "Outils", Icons.Outlined.Calculate),
)

/** Onglet auquel appartient une route (pour la sélection de la barre du bas). */
private fun tabOf(route: String?): String? = when {
    route == null -> null
    route == "beers" || route.startsWith("beer/") -> "beers"
    route == "recipes" || route.startsWith("recipe/") || route.startsWith("draft/") -> "recipes"
    route == "inventory" -> "inventory"
    route == "brews" || route.startsWith("brew/") -> "brews"
    route == "tools" || route.startsWith("tools/") || route == "stats" -> "tools"
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewHomeApp(vm: BrewViewModel = viewModel()) {
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
                            currentRoute == "settings" -> "Réglages"
                            currentRoute?.startsWith("recipe/") == true -> "Recette"
                            currentRoute?.startsWith("beer/") == true -> "Bière"
                            currentRoute?.startsWith("brew/") == true -> "Brassin"
                            currentRoute?.startsWith("draft/") == true -> "Brouillon"
                            currentRoute == "stats" -> "Statistiques"
                            currentRoute == "tools" -> "Outils"
                            currentRoute?.startsWith("tools/") == true ->
                                toolTitle(backStack?.arguments?.getString("id"))
                            else -> "BrewHome"
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
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
                        IconButton(onClick = {
                            shareText(
                                context,
                                ShareText.recipe(recipeToShare),
                                "Recette ${recipeToShare.name}",
                            )
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Partager la recette")
                        }
                    }
                    // Partage du stock complet
                    if (currentRoute == "inventory" && state.inventory.isNotEmpty()) {
                        val context = LocalContext.current
                        IconButton(onClick = {
                            val date = LocalDate.now()
                                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                            shareText(
                                context,
                                ShareText.inventory(state.inventory, date),
                                "Stock d'ingrédients de brasserie",
                            )
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Partager le stock")
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
                                contentDescription = "Ouvrir la vitrine de la cave",
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
                            Icon(Icons.Filled.Refresh, contentDescription = "Rafraîchir")
                        }
                    }
                    if (currentRoute != "settings") {
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Réglages")
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
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
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
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
