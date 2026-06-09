package murat.com.saasproject.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import murat.com.saasproject.ui.viewmodel.AdminMainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMainScreen(
    viewModel: AdminMainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pointsText by remember { mutableStateOf("10") }

    LaunchedEffect(Unit) {
        viewModel.loadBusiness()
    }

    if (uiState.approvalRequired) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissApprovalRequired() },
            title = { Text("Onay Bekleniyor") },
            text = { Text("İşletmeniz henüz onaylanmadı. QR oluşturmak için onay beklemeniz gerekiyor.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { viewModel.dismissApprovalRequired() }) {
                    Text("Tamam")
                }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Anasayfa") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            uiState.business?.let { business ->
                Text(
                    text = business.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!business.isApproved) {
                    Text(
                        text = "Onay bekleniyor",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            OutlinedTextField(
                value = pointsText,
                onValueChange = { pointsText = it.filter { ch -> ch.isDigit() || ch == '-' } },
                label = { Text("Puan değeri") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val points = pointsText.toIntOrNull() ?: return@Button
                    viewModel.generateQRCode(points)
                },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("QR Oluştur")
            }

            uiState.qrBitmap?.let { bitmap ->
                QrCard(bitmap = bitmap)
            }

            uiState.errorMessage?.let { error ->
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun QrCard(bitmap: Bitmap) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "QR Kod", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "QR Kod",
                modifier = Modifier.size(240.dp)
            )
            Text(
                text = "10 dakika geçerli",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
