package com.danglingmind.convotrack.ui.scan

import com.danglingmind.convotrack.data.analytics.Analytics

import android.Manifest
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsGradient
import com.danglingmind.convotrack.ui.designsystem.DsShape
import com.danglingmind.convotrack.ui.designsystem.DsSpace
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.FloatingButton
import com.danglingmind.convotrack.ui.designsystem.IconDisc
import com.danglingmind.convotrack.ui.designsystem.PrimaryButton
import com.danglingmind.convotrack.ui.designsystem.SecondaryButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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

    LaunchedEffect(Unit) { Analytics.capture(Analytics.Event.QrScanOpened) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        Analytics.capture(Analytics.Event.PermissionResult, mapOf("kind" to "camera", "status" to if (ok) "granted" else "denied"))
        granted = ok
        denied = !ok
    }

    Box(Modifier.fillMaxSize().background(if (granted) Color.Black else DsColor.bgBase)) {
        when {
            granted -> {
                CameraPreview(onCode = onCode)
                Viewfinder()
            }
            denied -> PermissionState(
                icon = Icons.Filled.NoPhotography,
                danger = true,
                title = "Camera access denied",
                body = "Enable camera access in Settings to scan QR codes.",
                actionLabel = "Close",
                onAction = onClose,
            )
            else -> PermissionState(
                icon = Icons.Filled.CameraAlt,
                danger = false,
                title = "Camera access required",
                body = "ConvoTrack needs camera access to scan QR codes.",
                actionLabel = "Continue",
                onAction = { permLauncher.launch(Manifest.permission.CAMERA) },
            )
        }

        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(DsSpace.gutter), horizontalArrangement = Arrangement.End) {
            FloatingButton(Icons.Filled.Close, "Close", onClose)
        }

        if (granted) {
            Text(
                "Point at a ConvoTrack QR code",
                style = DsType.bodySmall,
                color = DsColor.textOnPhoto,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 48.dp)
                    .clip(DsShape.pill)
                    .background(DsColor.glassPill)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** Accent corner brackets framing the scan area, with the rest of the feed dimmed. */
@Composable
private fun Viewfinder() {
    Canvas(Modifier.fillMaxSize()) {
        val box = 260.dp.toPx()
        val left = (size.width - box) / 2
        val top = (size.height - box) / 2
        val dim = DsColor.scrim
        drawRect(dim, size = androidx.compose.ui.geometry.Size(size.width, top))
        drawRect(dim, topLeft = Offset(0f, top + box), size = androidx.compose.ui.geometry.Size(size.width, size.height - top - box))
        drawRect(dim, topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(left, box))
        drawRect(dim, topLeft = Offset(left + box, top), size = androidx.compose.ui.geometry.Size(size.width - left - box, box))
        val len = 34.dp.toPx()
        val w = 4.dp.toPx()
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(DsColor.accent, Offset(x, y + dy * len), Offset(x, y), w, StrokeCap.Round)
            drawLine(DsColor.accent, Offset(x, y), Offset(x + dx * len, y), w, StrokeCap.Round)
        }
        corner(left, top, 1f, 1f)
        corner(left + box, top, -1f, 1f)
        corner(left, top + box, 1f, -1f)
        corner(left + box, top + box, -1f, -1f)
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
    danger: Boolean,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(DsGradient.appBackground).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconDisc(
            icon,
            size = 72.dp,
            iconSize = 34.dp,
            background = if (danger) DsColor.dangerTint else DsColor.accentTint,
            tint = if (danger) DsColor.danger else DsColor.accent,
        )
        Spacer(Modifier.height(24.dp))
        Text(title, style = DsType.titleLarge, color = DsColor.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = DsType.body, color = DsColor.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        if (danger) SecondaryButton(actionLabel, onClick = onAction) else PrimaryButton(actionLabel, onClick = onAction)
    }
}
