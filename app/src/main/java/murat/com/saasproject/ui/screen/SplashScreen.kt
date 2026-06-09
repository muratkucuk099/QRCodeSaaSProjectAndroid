package murat.com.saasproject.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import murat.com.saasproject.data.model.LoginResult
import murat.com.saasproject.ui.viewmodel.AuthViewModel

@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToMainAdmin: () -> Unit,
    onNavigateToAdminTabs: () -> Unit,
    onNavigateToUserTabs: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.checkExistingSession()
    }

    LaunchedEffect(uiState.isLoading, uiState.loginResult) {
        when (uiState.loginResult) {
            LoginResult.MAIN_ADMIN -> onNavigateToMainAdmin()
            LoginResult.SUB_ADMIN -> onNavigateToAdminTabs()
            LoginResult.USER -> onNavigateToUserTabs()
            null -> if (!uiState.isLoading) onNavigateToLogin()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
            Text(
                text = "SAAS Project",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
