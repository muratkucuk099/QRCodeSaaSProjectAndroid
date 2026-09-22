package murat.com.saasproject.ui.customer.qr

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import murat.com.saasproject.R
import murat.com.saasproject.data.model.QrPayload
import murat.com.saasproject.databinding.ActivityQrScannerBinding
import murat.com.saasproject.ui.common.openAppSettings
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * iOS `QRScannerViewController` karşılığı.
 * QR sekmesi tab değiştirmez; bu activity tam ekran modal gibi açılır.
 */
class QrScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrScannerBinding
    private val viewModel: QrScannerViewModel by viewModels()
    private val analyzing = AtomicBoolean(false)
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var cameraBound = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else showPermissionUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.openSettingsButton.setOnClickListener { openAppSettings() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.isLoading.collect { binding.loadingOverlay.root.isVisible = it } }
                launch {
                    viewModel.events.collect { event ->
                        event?.getIfNotHandled()?.let(::handleEvent)
                    }
                }
            }
        }

        ensureCameraPermission()
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    private fun ensureCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> startCamera()
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun showPermissionUi() {
        binding.permissionContainer.isVisible = true
        binding.scanFrame.isVisible = false
        binding.hintTextView.isVisible = false
    }

    private fun startCamera() {
        binding.permissionContainer.isVisible = false
        binding.scanFrame.isVisible = true
        binding.hintTextView.isVisible = true
        if (cameraBound) return

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val scanner = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
            )

            analysis.setAnalyzer(cameraExecutor) { proxy ->
                val media = proxy.image
                if (media == null || analyzing.get()) {
                    proxy.close()
                    return@setAnalyzer
                }
                val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val raw = barcodes.firstOrNull()?.rawValue ?: return@addOnSuccessListener
                        val payload = QrPayload.fromJson(raw) ?: return@addOnSuccessListener
                        if (analyzing.compareAndSet(false, true)) {
                            viewModel.process(payload)
                        }
                    }
                    .addOnCompleteListener { proxy.close() }
            }

            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                cameraBound = true
            }.onFailure {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.qr_scan_error_title)
                    .setMessage(R.string.camera_unavailable)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleEvent(event: QrScanEvent) = when (event) {
        is QrScanEvent.Success -> {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_NEW_POINTS, event.newPoints))
            finish()
        }
        is QrScanEvent.Failure -> {
            val message = event.messageRes?.let(::getString)
                ?: event.message
                ?: getString(R.string.qr_error_invalid)
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.qr_scan_error_title)
                .setMessage(message)
                .setPositiveButton(R.string.ok) { _, _ -> analyzing.set(false) }
                .setOnCancelListener { analyzing.set(false) }
                .show()
        }
    }

    companion object {
        const val EXTRA_NEW_POINTS = "new_points"

        fun intent(context: Context): Intent = Intent(context, QrScannerActivity::class.java)
    }
}
