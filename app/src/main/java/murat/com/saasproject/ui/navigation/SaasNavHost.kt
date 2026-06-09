package murat.com.saasproject.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import murat.com.saasproject.ui.screen.AdminMainScreen
import murat.com.saasproject.ui.screen.AdminPanelScreen
import murat.com.saasproject.ui.screen.AdminProfileScreen
import murat.com.saasproject.ui.screen.AdminSignUpScreen
import murat.com.saasproject.ui.screen.CreateScreen
import murat.com.saasproject.ui.screen.LoginScreen
import murat.com.saasproject.ui.screen.MainAdminScreen
import murat.com.saasproject.ui.screen.QrScannerScreen
import murat.com.saasproject.ui.screen.SplashScreen
import murat.com.saasproject.ui.screen.UserHomeScreen
import murat.com.saasproject.ui.screen.UserNotificationsScreen
import murat.com.saasproject.ui.screen.UserSignUpScreen
import murat.com.saasproject.ui.viewmodel.AdminPanelViewModel
import murat.com.saasproject.ui.viewmodel.AuthViewModel
import murat.com.saasproject.util.FcmTokenManager

@Composable
fun SaasNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val scope = rememberCoroutineScope()
    val authViewModel: AuthViewModel = viewModel(activity)

    NavHost(
        navController = navController,
        startDestination = Routes.Splash.route
    ) {
        composable(Routes.Splash.route) {
            SplashScreen(
                viewModel = authViewModel,
                onNavigateToLogin = {
                    navController.navigate(Routes.Login.route) {
                        popUpTo(Routes.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToMainAdmin = {
                    scope.launch { FcmTokenManager.syncTokenForCurrentUser(context) }
                    navController.navigate(Routes.MainAdmin.route) {
                        popUpTo(Routes.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToAdminTabs = {
                    scope.launch { FcmTokenManager.syncTokenForCurrentUser(context) }
                    navController.navigate(Routes.AdminMain.route) {
                        popUpTo(Routes.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToUserTabs = {
                    scope.launch { FcmTokenManager.syncTokenForCurrentUser(context) }
                    navController.navigate(Routes.UserHome.route) {
                        popUpTo(Routes.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToUserSignUp = {
                    navController.navigate(Routes.UserSignUp.route)
                },
                onNavigateToAdminSignUp = {
                    navController.navigate(Routes.AdminSignUp.route)
                },
                onNavigateToMainAdmin = {
                    scope.launch { FcmTokenManager.syncTokenForCurrentUser(context) }
                    navController.navigate(Routes.MainAdmin.route) {
                        popUpTo(Routes.Login.route) { inclusive = true }
                    }
                },
                onNavigateToAdminTabs = {
                    scope.launch { FcmTokenManager.syncTokenForCurrentUser(context) }
                    navController.navigate(Routes.AdminMain.route) {
                        popUpTo(Routes.Login.route) { inclusive = true }
                    }
                },
                onNavigateToUserTabs = {
                    scope.launch { FcmTokenManager.syncTokenForCurrentUser(context) }
                    navController.navigate(Routes.UserHome.route) {
                        popUpTo(Routes.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.UserSignUp.route) {
            UserSignUpScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToUserTabs = {
                    scope.launch { FcmTokenManager.syncTokenForCurrentUser(context) }
                    navController.navigate(Routes.UserHome.route) {
                        popUpTo(Routes.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.AdminSignUp.route) {
            AdminSignUpScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAdminTabs = {
                    scope.launch { FcmTokenManager.syncTokenForCurrentUser(context) }
                    navController.navigate(Routes.AdminMain.route) {
                        popUpTo(Routes.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.MainAdmin.route) {
            MainAdminScreen(
                onSignedOut = {
                    navController.navigate(Routes.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.UserHome.route) {
            UserTabScaffold(
                onNavigateToLogin = {
                    navController.navigate(Routes.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.QrScanner.route) {
            QrScannerScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Routes.AdminMain.route) {
            AdminTabScaffold(
                onNavigateToLogin = {
                    navController.navigate(Routes.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}

@Composable
private fun UserTabScaffold(onNavigateToLogin: () -> Unit) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == Routes.UserHome.route,
                    onClick = {
                        navController.navigate(Routes.UserHome.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Anasayfa") },
                    label = { Text("Anasayfa") }
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.QrScanner.route,
                    onClick = {
                        navController.navigate(Routes.QrScanner.route) {
                            launchSingleTop = true
                        }
                    },
                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "QR") },
                    label = { Text("QR") }
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.UserNotifications.route,
                    onClick = {
                        navController.navigate(Routes.UserNotifications.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = "Bildirimler") },
                    label = { Text("Bildirimler") }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.UserHome.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.UserHome.route) {
                UserHomeScreen(
                    onOpenQrScanner = {
                        navController.navigate(Routes.QrScanner.route)
                    }
                )
            }
            composable(Routes.QrScanner.route) {
                QrScannerScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Routes.UserNotifications.route) {
                UserNotificationsScreen()
            }
        }
    }
}

@Composable
private fun AdminTabScaffold(onNavigateToLogin: () -> Unit) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val panelViewModel: AdminPanelViewModel = viewModel()

    LaunchedEffect(panelViewModel.uiState.value.signedOut) {
        if (panelViewModel.uiState.value.signedOut) {
            onNavigateToLogin()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == Routes.AdminMain.route,
                    onClick = {
                        navController.navigate(Routes.AdminMain.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Anasayfa") },
                    label = { Text("Anasayfa") }
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.AdminCreate.route,
                    onClick = {
                        navController.navigate(Routes.AdminCreate.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.AddCircle, contentDescription = "Oluştur") },
                    label = { Text("Oluştur") }
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.AdminPanel.route,
                    onClick = {
                        navController.navigate(Routes.AdminPanel.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.List, contentDescription = "Ödüller") },
                    label = { Text("Ödüller") }
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.AdminProfile.route,
                    onClick = {
                        navController.navigate(Routes.AdminProfile.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profil") },
                    label = { Text("Profil") }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.AdminMain.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.AdminMain.route) { AdminMainScreen() }
            composable(Routes.AdminCreate.route) { CreateScreen() }
            composable(Routes.AdminPanel.route) { AdminPanelScreen() }
            composable(Routes.AdminProfile.route) {
                AdminProfileScreen()
            }
        }
    }
}
