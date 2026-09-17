package com.megamarket.app.ui.navigation

sealed class Route(val path: String) {
    data object Splash : Route("splash")
    data object Login : Route("login")
    data object Home : Route("home")
    data object Catalog : Route("catalog")
    data object ProductDetail : Route("product_detail")
    data object Favorites : Route("favorites")
    data object Cart : Route("cart")
    data object Checkout : Route("checkout")
    data object Confirmation : Route("confirmation")
    data object Profile : Route("profile")
    data object AdminDashboard : Route("admin_dashboard")
    data object AdminProducts : Route("admin_products")
    data object AdminProductCreate : Route("admin_product_create")
    data object AdminProductEdit : Route("admin_product_edit")
}

object ClientBottomDestinations {
    val routes = setOf(
        Route.Home.path,
        Route.Catalog.path,
        Route.Favorites.path,
        Route.Cart.path
    )
}
