package com.megamarket.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.megamarket.app.ui.screens.admin.dashboard.AdminDashboardScreen
import com.megamarket.app.ui.screens.admin.productform.AdminProductFormScreen
import com.megamarket.app.ui.screens.admin.products.AdminProductsScreen
import com.megamarket.app.ui.screens.auth.LoginScreen
import com.megamarket.app.ui.screens.cart.CartScreen
import com.megamarket.app.ui.screens.catalog.CatalogScreen
import com.megamarket.app.ui.screens.checkout.CheckoutScreen
import com.megamarket.app.ui.screens.confirmation.ConfirmationScreen
import com.megamarket.app.ui.screens.favorites.FavoritesScreen
import com.megamarket.app.ui.screens.home.HomeScreen
import com.megamarket.app.ui.screens.product.ProductDetailScreen
import com.megamarket.app.ui.screens.profile.ProfileScreen
import com.megamarket.app.ui.screens.splash.SplashScreen

@Composable
fun MegaMarketNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Route.Splash.path
    ) {
        composable(Route.Splash.path) {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate(Route.Login.path) {
                        popUpTo(Route.Splash.path) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.Login.path) {
            LoginScreen(
                onLogin = {
                    // Temporal: solo navegación de UI. Sin validación ni base de datos.
                    // TODO: AuthViewModel decidirá Home vs AdminDashboard según el rol.
                    navController.navigate(Route.Home.path) {
                        popUpTo(Route.Login.path) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.Home.path) {
            HomeScreen(
                onNavigate = { route -> navController.navigateClient(route) },
                onLogout = { navController.logoutToLogin() }
            )
        }

        composable(Route.Catalog.path) {
            CatalogScreen(
                onNavigate = { route -> navController.navigateClient(route) },
                onLogout = { navController.logoutToLogin() }
            )
        }

        composable(Route.ProductDetail.path) {
            ProductDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.Favorites.path) {
            FavoritesScreen(
                onNavigate = { route -> navController.navigateClient(route) },
                onLogout = { navController.logoutToLogin() }
            )
        }

        composable(Route.Cart.path) {
            CartScreen(
                onNavigate = { route ->
                    when (route) {
                        Route.Checkout.path -> navController.navigate(Route.Checkout.path)
                        else -> navController.navigateClient(route)
                    }
                },
                onLogout = { navController.logoutToLogin() }
            )
        }

        composable(Route.Checkout.path) {
            CheckoutScreen(
                onBack = { navController.popBackStack() },
                onConfirmPurchase = {
                    navController.navigate(Route.Confirmation.path)
                }
            )
        }

        composable(Route.Confirmation.path) {
            ConfirmationScreen(
                onBackToHome = {
                    navController.navigate(Route.Home.path) {
                        popUpTo(Route.Home.path) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Route.Profile.path) {
            ProfileScreen(
                onNavigate = { route -> navController.navigateClient(route) },
                onLogout = { navController.logoutToLogin() }
            )
        }

        composable(Route.AdminDashboard.path) {
            AdminDashboardScreen(
                onNavigate = { route -> navController.navigateAdmin(route) },
                onLogout = { navController.logoutToLogin() }
            )
        }

        composable(Route.AdminProducts.path) {
            AdminProductsScreen(
                onNavigate = { route -> navController.navigateAdmin(route) },
                onLogout = { navController.logoutToLogin() }
            )
        }

        composable(Route.AdminProductCreate.path) {
            AdminProductFormScreen(
                isEditMode = false,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.AdminProductEdit.path) {
            AdminProductFormScreen(
                isEditMode = true,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

private fun NavHostController.logoutToLogin() {
    navigate(Route.Login.path) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavHostController.navigateClient(route: String) {
    if (route in ClientBottomDestinations.routes) {
        navigate(route) {
            popUpTo(Route.Home.path) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    } else {
        navigate(route)
    }
}

private fun NavHostController.navigateAdmin(route: String) {
    val adminRoots = setOf(Route.AdminDashboard.path, Route.AdminProducts.path)
    if (route in adminRoots) {
        navigate(route) {
            popUpTo(Route.AdminDashboard.path) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    } else {
        navigate(route)
    }
}
