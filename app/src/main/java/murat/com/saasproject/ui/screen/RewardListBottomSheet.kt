package murat.com.saasproject.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import murat.com.saasproject.data.FirebaseRepository
import murat.com.saasproject.data.model.Reward

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardListBottomSheet(
    businessId: String,
    points: Int,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var rewards by remember { mutableStateOf<List<Reward>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(businessId) {
        isLoading = true
        rewards = withContext(Dispatchers.IO) {
            FirebaseRepository.fetchRewards(businessId)
        }
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "Ödüller",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Mevcut puanınız: $points",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (rewards.isEmpty()) {
                Text(
                    text = "Bu işletmede henüz ödül yok.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(rewards, key = { it.rewardId }) { reward ->
                        RewardItem(reward = reward, userPoints = points)
                    }
                }
            }
        }
    }
}

@Composable
private fun RewardItem(reward: Reward, userPoints: Int) {
    val canRedeem = userPoints >= reward.requiredPoints
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = reward.imageUrl,
            contentDescription = reward.name,
            modifier = Modifier.size(56.dp),
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = reward.name, fontWeight = FontWeight.SemiBold)
            Text(
                text = reward.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${reward.requiredPoints} puan",
                style = MaterialTheme.typography.labelLarge,
                color = if (canRedeem) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.error
            )
        }
    }
}
