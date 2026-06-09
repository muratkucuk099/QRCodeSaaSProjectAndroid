package murat.com.saasproject.ui.navigation

sealed class Routes(val route: String) {
    data object Splash : Routes("splash")
    data object Login : Routes("login")
    data object UserSignUp : Routes("user_signup")
    data object AdminSignUp : Routes("admin_signup")
    data object UserHome : Routes("user_home")
    data object UserNotifications : Routes("user_notifications")
    data object QrScanner : Routes("qr_scanner")
    data object AdminMain : Routes("admin_main")
    data object AdminCreate : Routes("admin_create")
    data object AdminPanel : Routes("admin_panel")
    data object AdminProfile : Routes("admin_profile")
    data object MainAdmin : Routes("main_admin")
}
