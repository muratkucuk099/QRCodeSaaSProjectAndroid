package murat.com.saasproject.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import murat.com.saasproject.data.model.Business
import murat.com.saasproject.data.model.UserBusiness
import murat.com.saasproject.ui.viewmodel.UserHomeSection
import murat.com.saasproject.ui.viewmodel.UserHomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserHomeScreen(
    onOpenQrScanner: () -> Unit,
    viewModel: UserHomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedBusinessId by remember { mutableStateOf<String?>(null) }
    var showRewardSheet by remember { mutableStateOf(false) }

    if (showRewardSheet && selectedBusinessId != null) {
        RewardListBottomSheet(
            businessId = selectedBusinessId!!,
            points = viewModel.pointsFor(selectedBusinessId!!),
            onDismiss = { showRewardSheet = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Anasayfa") },
                actions = {
                    Text(
                        text = "QR Tara",
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clickable { onOpenQrScanner() },
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading && uiState.myBusinesses.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SectionHeader(UserHomeSection.MY_BUSINESSES.title)
                }

                if (uiState.myBusinesses.isEmpty()) {
                    item {
                        EmptyPlaceholder(UserHomeSection.MY_BUSINESSES.emptyMessage)
                    }
                } else {
                    items(uiState.myBusinesses, key = { it.businessId }) { userBusiness ->
                        BusinessRow(
                            business = viewModel.getBusinessInfo(userBusiness.businessId),
                            points = userBusiness.points,
                            onClick = {
                                selectedBusinessId = userBusiness.businessId
                                showRewardSheet = true
                            }
                        )
                    }
                }

                item {
                    SectionHeader(UserHomeSection.ALL_BUSINESSES.title)
                }

                if (uiState.discoverBusinesses.isEmpty()) {
                    item {
                        EmptyPlaceholder(UserHomeSection.ALL_BUSINESSES.emptyMessage)
                    }
                } else {
                    items(uiState.discoverBusinesses, key = { it.id }) { business ->
                        BusinessRow(
                            business = business,
                            points = viewModel.pointsFor(business.id).takeIf { it > 0 },
                            onClick = {
                                selectedBusinessId = business.id
                                showRewardSheet = true
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun EmptyPlaceholder(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BusinessRow(
    business: Business?,
    points: Int?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = business?.logoURL,
                contentDescription = business?.name,
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = business?.name ?: "İşletme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = business?.businessType ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (points != null) {
                Text(
                    text = "$points puan",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
