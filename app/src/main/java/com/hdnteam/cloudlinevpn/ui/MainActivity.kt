package com.hdnteam.cloudlinevpn.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.hdnteam.cloudlinevpn.data.model.ServerConfig
import com.hdnteam.cloudlinevpn.ui.screen.*
import com.hdnteam.cloudlinevpn.ui.theme.*
import com.hdnteam.cloudlinevpn.ui.viewmodel.HomeViewModel
import com.hdnteam.cloudlinevpn.ui.viewmodel.OnboardingViewModel
import com.hdnteam.cloudlinevpn.ui.viewmodel.ServersViewModel
import com.hdnteam.cloudlinevpn.vpn.VpnConnectionState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingVpnConnect: (() -> Unit)? = null

    // VPN permission
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingVpnConnect?.invoke()
            pendingVpnConnect = null
        }
    }

    // Notification permission (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied - VPN works either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Schedule background workers
        Thread {
            try {
                val wm = androidx.work.WorkManager.getInstance(applicationContext)
                com.hdnteam.cloudlinevpn.worker.SubscriptionUpdateWorker.schedule(wm)
                com.hdnteam.cloudlinevpn.worker.AppUpdateWorker.schedule(wm)
                com.hdnteam.cloudlinevpn.worker.DailyNotificationWorker.schedule(wm)
            } catch (_: Throwable) {}
        }.start()

        setContent {
            CloudLineVPNTheme {
                RootApp(
                    onRequestVpnPermission = { intent, onGranted ->
                        pendingVpnConnect = onGranted
                        vpnPermissionLauncher.launch(intent)
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RootApp(
    onRequestVpnPermission: (Intent, () -> Unit) -> Unit
) {
    val onboardingVm: OnboardingViewModel = hiltViewModel()
    val onboardingDone by onboardingVm.onboardingDone.collectAsState()
    val isLoading by onboardingVm.isLoading.collectAsState()
    val error by onboardingVm.error.collectAsState()

    when (onboardingDone) {
        null -> {
            // Still loading preference - show blank/splash
            Box(Modifier.fillMaxSize().background(CloudDarkBlue))
        }
        false -> {
            OnboardingScreen(
                isLoading = isLoading,
                errorMessage = error,
                onSubscribe = { url, alias -> onboardingVm.submitSubscription(url, alias) },
                onSkip = { onboardingVm.skipOnboarding() }
            )
        }
        true -> {
            MainApp(onRequestVpnPermission = onRequestVpnPermission)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MainApp(
    onRequestVpnPermission: (Intent, () -> Unit) -> Unit
) {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = hiltViewModel()
    val connectionState by homeViewModel.connectionState.collectAsState()

    val navItems = listOf(
        NavRoute.Home, NavRoute.Servers, NavRoute.Account,
        NavRoute.Settings, NavRoute.Purchase
    )

    Scaffold(
        containerColor = CloudDarkBlue,
        bottomBar = {
            CloudLineBottomBar(
                navItems = navItems,
                navController = navController,
                connectionState = connectionState
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoute.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(180)) },
            exitTransition  = { fadeOut(animationSpec = androidx.compose.animation.core.tween(180)) }
        ) {
            composable(NavRoute.Home.route) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onRequestVpnPermission = { intent ->
                        onRequestVpnPermission(intent) { homeViewModel.connectAutomatic() }
                    }
                )
            }
            composable(NavRoute.Servers.route) {
                val serversVm: ServersViewModel = hiltViewModel()
                ServersScreen(
                    viewModel = serversVm,
                    onConnectServer = { server: ServerConfig ->
                        // Use hot-switch if already connected
                        if (homeViewModel.connectionState.value == VpnConnectionState.CONNECTED) {
                            homeViewModel.switchServer(server)
                            // Stay on servers page — no nav
                        } else {
                            val permIntent = homeViewModel.requestVpnPermission()
                            if (permIntent != null) {
                                onRequestVpnPermission(permIntent) { homeViewModel.connectToServer(server) }
                            } else {
                                homeViewModel.connectToServer(server)
                            }
                            navController.navigate(NavRoute.Home.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    onEditServer = { server ->
                        navController.navigate("edit_server/${server.id}")
                    }
                )
            }
            composable(
                route = "edit_server/{serverId}",
                arguments = listOf(navArgument("serverId") { type = NavType.LongType })
            ) { backStackEntry ->
                val serverId = backStackEntry.arguments?.getLong("serverId") ?: return@composable
                val serversVm: ServersViewModel = hiltViewModel()
                val servers by serversVm.servers.collectAsState()
                val server = servers.find { it.id == serverId }
                if (server != null) {
                    EditServerScreen(
                        server = server,
                        onSave = { updated -> serversVm.updateServer(updated) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(NavRoute.Account.route)  { AccountScreen() }
            composable(NavRoute.Settings.route) {
                SettingsScreen(
                    onNavigateToPerApp = { navController.navigate("per_app") },
                    onNavigateToProxyApp = { navController.navigate("proxy_app") },
                    onNavigateToLog    = { navController.navigate("log_screen") }
                )
            }
            composable(NavRoute.Purchase.route) { PurchaseScreen() }
            composable("per_app") { PerAppScreen(onBack = { navController.popBackStack() }) }
            composable("proxy_app") { ProxyAppScreen(onBack = { navController.popBackStack() }) }
            composable("log_screen") { LogScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

// ── Navigation routes ─────────────────────────────────────────────────────────

sealed class NavRoute(val route: String, val labelRes: String, val icon: ImageVector) {
    object Home     : NavRoute("home",     "خانه",    Icons.Default.Home)
    object Servers  : NavRoute("servers",  "سرورها",  Icons.Default.Storage)
    object Account  : NavRoute("account",  "حساب کاربری",    Icons.Default.Person)
    object Settings : NavRoute("settings", "تنظیمات", Icons.Default.Settings)
    object Purchase : NavRoute("purchase", "خرید/تمدید",    Icons.Default.ShoppingCart)
    // Not in bottom bar
    object EditServer : NavRoute("edit_server/{serverId}", "", Icons.Default.Edit)
}

// ── Bottom Bar ────────────────────────────────────────────────────────────────

@Composable
fun CloudLineBottomBar(
    navItems: List<NavRoute>,
    navController: androidx.navigation.NavHostController,
    connectionState: VpnConnectionState
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Surface(color = CloudMidBlue, tonalElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(62.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { item ->
                val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                val isHome = item is NavRoute.Home
                val activeColor = if (isHome && connectionState == VpnConnectionState.CONNECTED) CloudGreen else CloudAccent

                Column(
                    modifier = Modifier
                        .clickable {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box {
                        Icon(
                            item.icon,
                            contentDescription = item.labelRes,
                            tint = if (selected) activeColor else CloudGray.copy(0.4f),
                            modifier = Modifier.size(23.dp)
                        )
                        if (isHome && connectionState == VpnConnectionState.CONNECTED) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(CloudGreen, CircleShape)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }
                    Text(
                        item.labelRes,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) activeColor else CloudGray.copy(0.4f),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
