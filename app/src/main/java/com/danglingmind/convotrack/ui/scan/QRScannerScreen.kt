package com.danglingmind.convotrack.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.danglingmind.convotrack.ui.theme.LimePrimaryButton
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen QR scanner (CameraX + ML Kit). Ported from iOS QRScannerSheet: reads either a
 * `convotrack://join/CODE` deep link or a plain 6-char code and returns the invite code.
 */
@Composable
fun QRScannerScreen(
    onClose: () -> Unit,
    onCode: (String) -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var denied by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            granted -> CameraPreview(onCode = onCode)
            denied -> PermissionState(
                icon = Icons.Filled.NoPhotography,
                iconTint = CT.errorColor,
                title = "Camera Access Denied",
                body = "Enable camera access in Settings to scan QR codes.",
                actionLabel = "Close",
                onAction = onClose,
            )
            else -> PermissionState(
                icon = Icons.Filled.CameraAlt,
                iconTint = CT.primaryFixed,
                title = "Camera Access Required",
                body = "ConvoTrack needs camera access to scan QR codes.",
                actionLabel = "Continue",
                onAction = { permLauncher.launch(Manifest.permission.CAMERA) },
            )
        }

        // Close button
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(20.dp), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, "Close", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        if (granted) {
            Text(
                "Point at a ConvoTrack QR code",
                style = CTType.labelCaps,
                color = Color.White.copy(alpha = 0.8f),
                letterSpacing = 2.sp,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 48.dp),
            )
        }
    }
}

@Composable
private fun CameraPreview(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val handled = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, QrAnalyzer(handled, onCode)) }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/** ML Kit QR analyzer that fires [onCode] exactly once with an extracted 6-char invite code. */
private class QrAnalyzer(
    private val handled: AtomicBoolean,
    private val onCode: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
    )

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (media == null || handled.get()) {
            image.close()
            return
        }
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val raw = barcodes.firstOrNull()?.rawValue
                val code = raw?.let(::extractInviteCode)
                if (code != null && handled.compareAndSet(false, true)) {
                    onCode(code)
                }
            }
            .addOnCompleteListener { image.close() }
    }
}

/** convotrack://join/CODE or a plain 6-char code -> normalized 6-char invite code, or null. */
internal fun extractInviteCode(raw: String): String? {
    val candidate = raw.substringAfterLast("join/", raw)
    val code = candidate.uppercase().filter { it.isLetterOrDigit() }.take(6)
    return if (code.length == 6) code else null
}

@Composable
private fun PermissionState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(24.dp))
        Text(title, style = CTType.headlineMd, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(body, style = CTType.bodyMd, color = CT.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Column(Modifier.padding(horizontal = 8.dp)) {
            LimePrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}
