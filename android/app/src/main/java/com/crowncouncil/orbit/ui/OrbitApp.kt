package com.crowncouncil.orbit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

enum class Screen(val route: String, val label: String, val icon: ImageVector, val inBar: Boolean = false) {
    Home("home", "Home", Icons.Default.Home, true),
    Plan("plan", "Plan", Icons.Default.Create, true),
    Guide("guide", "Guide", Icons.Default.Face, true),
    VisitedMap("visited", "Map", Icons.Default.Place, true),
    More("more", "More", Icons.Default.Menu, true),
    Explore("explore", "Explore", Icons.Default.Search),
    Trip("trip", "Itinerary", Icons.Default.LocationOn),
    Budget("budget", "Budget", Icons.Default.ShoppingCart),
    Live("live", "Live map", Icons.Default.LocationOn),
    Photos("photos", "Photo editor", Icons.Default.Edit),
    Rankings("rankings", "Rankings", Icons.Default.Star),
    Submit("submit", "Add a spot", Icons.Default.Add),
    Account("account", "Account", Icons.Default.Person),
    Groups("groups", "Group trips", Icons.Default.Face)
}

@Composable
fun OrbitApp(vm: OrbitViewModel = viewModel()) {
    val user by vm.user.collectAsState()
    val guest by vm.guest.collectAsState()
    if (user == null && !guest) { AuthGate(vm); return }

    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val barItems = Screen.entries.filter { it.inBar }

    // Back should return through the app's navigation stack; it should not abruptly exit a nested screen.
    BackHandler(enabled = current != Screen.Home.route) {
        if (!nav.popBackStack()) nav.navigate(Screen.Home.route) { launchSingleTop = true }
    }

    fun go(screen: Screen) = nav.navigate(screen.route) {
        if (screen.inBar) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            restoreState = true
        }
        launchSingleTop = true
    }
    fun selected(s: Screen) = current == s.route || (s == Screen.More && Screen.entries.any { !it.inBar && it.route == current })

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp   // tablets and foldables get a side rail instead of a bottom bar
        Row(Modifier.fillMaxSize()) {
            if (wide) NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                barItems.forEach { s ->
                    NavigationRailItem(selected = selected(s), onClick = { go(s) }, icon = { Icon(s.icon, contentDescription = tr(s.label)) }, label = { Text(tr(s.label)) })
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (!wide) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        barItems.forEach { s ->
                            NavigationBarItem(selected = selected(s), onClick = { go(s) }, icon = { Icon(s.icon, contentDescription = tr(s.label)) }, label = { Text(tr(s.label), maxLines = 1) })
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    NavHost(nav, startDestination = Screen.Home.route, modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                        composable(Screen.Home.route) { HomeScreen(vm) }
                        composable(Screen.Plan.route) { PlanScreen(vm, onFind = { go(Screen.Explore) }) }
                        composable(Screen.Guide.route) { AgentScreen(vm, onPlanSaved = { go(Screen.Trip) }) }
                        composable(Screen.VisitedMap.route) { VisitedMapScreen(vm) }
                        composable(Screen.More.route) { MoreScreen(onOpen = { go(it) }) }
                        composable(Screen.Explore.route) { ExploreScreen(vm, onPlanned = { go(Screen.Trip) }) }
                        composable(Screen.Trip.route) { ItineraryScreen(vm) }
                        composable(Screen.Budget.route) { BudgetScreen(vm) }
                        composable(Screen.Live.route) { LiveMapScreen(vm, onOpenGuide = { go(Screen.Guide) }) }
                        composable(Screen.Photos.route) { PhotoEditorScreen() }
                        composable(Screen.Rankings.route) { RankingScreen(vm) }
                        composable(Screen.Submit.route) { SubmitSpotScreen(vm) }
                        composable(Screen.Account.route) { AccountScreen(vm) }
                        composable(Screen.Groups.route) { GroupsScreen(vm) }
                    }
                }
            }
        }
    }
}
