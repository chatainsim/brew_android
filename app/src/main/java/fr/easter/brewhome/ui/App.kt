package fr.easter.brewhome.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.MenuBook
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import fr.easter.brewhome.BrewViewModel

data class Tab(val route: String, val label: String, val icon: ImageVector)

val tabs = listOf(
    Tab("beers", "Cave", Icons.Outlined.LocalDrink),
    Tab("recipes", "Recettes", Icons.Outlined.MenuBook),
    Tab("inventory", "Ingrédients", Icons.Outlined.Inventory2),
    Tab("brews", "Brassins", Icons.Outlined.Science),
)

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

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val startDestination = if (serverUrl.isNullOrBlank()) "settings" else "beers"

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
                            else -> "BrewHome"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                actions = {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(24.dp),
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        IconButton(onClick = { vm.refreshAll() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Rafraîchir")
                        }
                    }
                    IconButton(onClick = {
                        if (currentRoute != "settings") navController.navigate("settings")
                    }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Réglages")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo("beers") { inclusive = tab.route == "beers" }
                                launchSingleTop = true
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
            composable("recipes") { RecipesScreen(vm) { navController.navigate("recipe/$it") } }
            composable("recipe/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toIntOrNull()
                RecipeDetailScreen(vm, id)
            }
            composable("inventory") { InventoryScreen(vm) }
            composable("brews") { BrewsScreen(vm) }
            composable("settings") {
                SettingsScreen(vm) {
                    navController.navigate("beers") { popUpTo("settings") { inclusive = true } }
                }
            }
        }
    }
}
