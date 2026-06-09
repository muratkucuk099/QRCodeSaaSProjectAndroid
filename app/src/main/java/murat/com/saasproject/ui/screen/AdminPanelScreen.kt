package murat.com.saasproject.ui.screen

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import murat.com.saasproject.ui.viewmodel.AdminPanelContent
import murat.com.saasproject.ui.viewmodel.AdminPanelViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    viewModel: AdminPanelViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedSegment by remember {
        mutableIntStateOf(
            if (uiState.selectedContent == AdminPanelContent.NOTIFICATIONS) 1 else 0
        )
    }

    LaunchedEffect(Unit) {
        viewModel.refresh(AdminPanelContent.REWARDS)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ödüller") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                SegmentedButton(
                    selected = selectedSegment == 0,
                    onClick = {
                        selectedSegment = 0
                        viewModel.selectContent(AdminPanelContent.REWARDS)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier.weight(1f)
                ) { Text("Ödüller") }
                SegmentedButton(
                    selected = selectedSegment == 1,
                    onClick = {
                        selectedSegment = 1
                        viewModel.selectContent(AdminPanelContent.NOTIFICATIONS)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.weight(1f)
                ) { Text("Bildirimler") }
            }

            if (uiState.isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            } else {
                when (uiState.selectedContent) {
                    AdminPanelContent.REWARDS -> {
                        if (uiState.rewards.isEmpty()) {
                            EmptyPanelMessage("Henüz ödül yok.")
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.rewards, key = { it.rewardId }) { reward ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = reward.imageUrl,
                                            contentDescription = reward.name,
                                            modifier = Modifier.size(56.dp),
                                            contentScale = ContentScale.Crop
                                        )
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 12.dp)
                                        ) {
                                            Text(reward.name, fontWeight = FontWeight.SemiBold)
                                            Text("${reward.requiredPoints} puan")
                                        }
                                        IconButton(onClick = { viewModel.deleteReward(reward) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Sil")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    AdminPanelContent.NOTIFICATIONS -> {
                        if (uiState.notifications.isEmpty()) {
                            EmptyPanelMessage("Henüz bildirim yok.")
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.notifications, key = { it.id }) { notification ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(notification.title, fontWeight = FontWeight.SemiBold)
                                            Text(notification.body)
                                            Text(
                                                text = notification.detailText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteNotification(notification) }
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Sil")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPanelMessage(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
