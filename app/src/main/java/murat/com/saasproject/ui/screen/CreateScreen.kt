package murat.com.saasproject.ui.screen

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import murat.com.saasproject.ui.viewmodel.CreateNotificationViewModel
import murat.com.saasproject.ui.viewmodel.CreateRewardViewModel
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(
    rewardViewModel: CreateRewardViewModel = viewModel(),
    notificationViewModel: CreateNotificationViewModel = viewModel()
) {
    var selectedSegment by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Oluştur") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedSegment == 0,
                    onClick = { selectedSegment = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier.weight(1f)
                ) { Text("Ödül") }
                SegmentedButton(
                    selected = selectedSegment == 1,
                    onClick = { selectedSegment = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.weight(1f)
                ) { Text("Bildirim") }
            }

            when (selectedSegment) {
                0 -> CreateRewardContent(viewModel = rewardViewModel)
                1 -> CreateNotificationContent(viewModel = notificationViewModel)
            }
        }
    }
}

@Composable
private fun CreateRewardContent(viewModel: CreateRewardViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var pointsText by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        imageUri = uri
        uri?.let {
            val bitmap = uriToBitmap(context, it)
            viewModel.setSelectedImage(bitmap)
        }
    }

    if (uiState.success) {
        AlertDialog(
            onDismissRequest = { viewModel.resetSuccess() },
            title = { Text("Başarılı") },
            text = { Text("Ödül oluşturuldu.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    name = ""
                    description = ""
                    pointsText = ""
                    imageUri = null
                    viewModel.setSelectedImage(null)
                    viewModel.resetSuccess()
                }) { Text("Tamam") }
            }
        )
    }

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Ödül adı") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text("Açıklama") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = pointsText,
        onValueChange = { pointsText = it.filter(Char::isDigit) },
        label = { Text("Gerekli puan") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
        Text(if (imageUri == null) "Görsel Seç" else "Görsel Değiştir")
    }

    imageUri?.let {
        AsyncImage(
            model = it,
            contentDescription = "Seçilen görsel",
            modifier = Modifier.size(120.dp)
        )
    }

    Button(
        onClick = { viewModel.createReward(name, description, pointsText) },
        enabled = !uiState.isLoading,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator()
        } else {
            Text("Ödül Oluştur")
        }
    }

    uiState.errorMessage?.let {
        Text(text = it, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun CreateNotificationContent(viewModel: CreateNotificationViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    uiState.result?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.resetResult() },
            title = { Text("Bildirim Gönderildi") },
            text = { Text(result.message) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    title = ""
                    body = ""
                    viewModel.resetResult()
                }) { Text("Tamam") }
            }
        )
    }

    OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("Başlık") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = body,
        onValueChange = { body = it },
        label = { Text("Mesaj") },
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    )

    Button(
        onClick = { viewModel.createNotification(title, body) },
        enabled = !uiState.isLoading,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator()
        } else {
            Text("Bildirim Gönder")
        }
    }

    uiState.errorMessage?.let {
        Text(text = it, color = MaterialTheme.colorScheme.error)
    }
}

private fun uriToBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }.getOrNull()
}
