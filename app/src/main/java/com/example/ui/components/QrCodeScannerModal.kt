package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.domain.finance.ParsedRentQrNotice
import com.example.domain.finance.RentQrCodeParser
import com.example.ui.media.QrBitmapDecoder
import com.example.ui.theme.BrushedSilver
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MidnightInk
import com.example.ui.theme.MpesaEmerald
import com.example.ui.theme.MpesaEmeraldLight
import com.example.ui.theme.PaperWhite
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorBgDark
import com.example.ui.theme.StatusErrorLight
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch

/**
 * Full-screen edge-to-edge QR code scanner modal supporting live CameraX scanning
 * and zero-permission Android Photo Picker import for M-Pesa URIs and physical rent notices.
 */
@Composable
fun QrCodeScannerModal(
    onDismiss: () -> Unit,
    onQrParsed: (ParsedRentQrNotice) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var isTorchEnabled by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var cameraUnavailableError by remember { mutableStateOf<String?>(null) }
    var scanErrorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessingGallery by remember { mutableStateOf(false) }
    val scanLock = remember { AtomicBoolean(false) }

    fun handleDecodedPayload(rawText: String) {
        val parsed = RentQrCodeParser.parse(rawText)
        if (parsed != null) {
            scanLock.set(true)
            onQrParsed(parsed)
        } else {
            scanErrorMessage = context.getString(R.string.qr_scanner_invalid_notice)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isProcessingGallery = true
            scanErrorMessage = null
            val decoded = runCatching { QrBitmapDecoder.decodeFromUri(context, uri) }.getOrNull()
            isProcessingGallery = false
            if (decoded.isNullOrBlank()) {
                scanErrorMessage = context.getString(R.string.qr_scanner_no_qr_in_image)
            } else {
                handleDecodedPayload(decoded)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BackHandler { onDismiss() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg)
                .testTag("qr_code_scanner_modal")
        ) {
            if (hasCameraPermission && cameraUnavailableError == null) {
                val previewView = remember(context) {
                    PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                }
                val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

                DisposableEffect(lifecycleOwner, previewView) {
                    val providerFuture = ProcessCameraProvider.getInstance(context)
                    val mainExecutor = ContextCompat.getMainExecutor(context)
                    var lastAnalyzedMs = 0L

                    providerFuture.addListener({
                        runCatching {
                            val provider = providerFuture.get()
                            val selector = when {
                                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                                else -> throw IllegalStateException("No camera hardware available on this device.")
                            }
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { imageAnalysis ->
                                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                        try {
                                            val now = System.currentTimeMillis()
                                            if (!scanLock.get() && (now - lastAnalyzedMs >= 160L)) {
                                                lastAnalyzedMs = now
                                                val decoded = QrBitmapDecoder.decodeFromImageProxy(imageProxy)
                                                if (!decoded.isNullOrBlank() && scanLock.compareAndSet(false, true)) {
                                                    mainExecutor.execute {
                                                        handleDecodedPayload(decoded)
                                                    }
                                                }
                                            }
                                        } finally {
                                            imageProxy.close()
                                        }
                                    }
                                }
                            provider.unbindAll()
                            val cam = provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                            boundCamera = cam
                        }.onFailure { err ->
                            cameraUnavailableError = err.localizedMessage ?: "Camera preview unavailable."
                        }
                    }, mainExecutor)

                    onDispose {
                        if (providerFuture.isDone) {
                            runCatching { providerFuture.get().unbindAll() }
                        }
                        analysisExecutor.shutdown()
                    }
                }

                LaunchedEffect(isTorchEnabled, boundCamera) {
                    if (boundCamera?.cameraInfo?.hasFlashUnit() == true) {
                        boundCamera?.cameraControl?.enableTorch(isTorchEnabled)
                    }
                }

                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

                QrScannerViewfinderOverlay(
                    isPaused = scanLock.get() || isProcessingGallery,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Permission denied or camera unavailable fallback
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(24.dp)
                ) {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, DarkOutline),
                        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Surface(
                                color = MpesaEmerald.copy(alpha = 0.2f),
                                shape = CircleShape,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.QrCodeScanner,
                                        contentDescription = null,
                                        tint = MpesaEmeraldLight,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.qr_scanner_permission_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = PaperWhite,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = cameraUnavailableError
                                    ?: stringResource(R.string.qr_scanner_permission_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = BrushedSilver,
                                textAlign = TextAlign.Center
                            )
                            if (!hasCameraPermission) {
                                Button(
                                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MpesaEmerald,
                                        contentColor = PaperWhite
                                    ),
                                    shape = RoundedCornerShape(999.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("qr_scanner_grant_camera_button")
                                ) {
                                    Text("Allow Camera")
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    scanErrorMessage = null
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                border = BorderStroke(1.dp, BrushedSilver.copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("qr_scanner_gallery_fallback_button")
                            ) {
                                Icon(
                                    Icons.Filled.PhotoLibrary,
                                    contentDescription = null,
                                    tint = PaperWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.qr_scanner_import_gallery), color = PaperWhite)
                            }
                        }
                    }
                }
            }

            // Edge-to-edge top & bottom overlay controls
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
            ) {
                // Top Bar: Close + Title + Torch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(MidnightInk.copy(alpha = 0.72f))
                            .testTag("qr_scanner_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.qr_scanner_close_cd),
                            tint = PaperWhite
                        )
                    }

                    Surface(
                        color = MidnightInk.copy(alpha = 0.76f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, PaperWhite.copy(alpha = 0.14f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.QrCodeScanner,
                                contentDescription = null,
                                tint = MpesaEmeraldLight,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.qr_scanner_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = PaperWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (hasCameraPermission && boundCamera?.cameraInfo?.hasFlashUnit() == true) {
                        IconButton(
                            onClick = { isTorchEnabled = !isTorchEnabled },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .background(MidnightInk.copy(alpha = 0.72f))
                                .testTag("qr_scanner_torch_button")
                        ) {
                            Icon(
                                imageVector = if (isTorchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                                contentDescription = stringResource(R.string.qr_scanner_torch_cd),
                                tint = if (isTorchEnabled) MpesaEmeraldLight else PaperWhite
                            )
                        }
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                }

                // Bottom Dock: Error banner + Guidance + Gallery Picker
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .align(Alignment.CenterHorizontally)
                ) {
                    AnimatedVisibility(
                        visible = !scanErrorMessage.isNullOrBlank(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            color = StatusErrorBgDark,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, StatusError.copy(alpha = 0.65f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("qr_scanner_error_banner")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = StatusErrorLight,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = scanErrorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PaperWhite,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        scanErrorMessage = null
                                        scanLock.set(false)
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("qr_scanner_retry_button")
                                ) {
                                    Icon(
                                        Icons.Filled.Refresh,
                                        contentDescription = null,
                                        tint = MpesaEmeraldLight,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Scan Again", color = MpesaEmeraldLight)
                                }
                            }
                        }
                    }

                    Surface(
                        color = MidnightInk.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, PaperWhite.copy(alpha = 0.14f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            if (isProcessingGallery) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = MpesaEmeraldLight,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Reading QR code from image…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PaperWhite
                                    )
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.qr_scanner_hint),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = PaperWhite,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = stringResource(R.string.qr_scanner_supported_formats),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BrushedSilver,
                                    textAlign = TextAlign.Center
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    scanErrorMessage = null
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isProcessingGallery,
                                border = BorderStroke(1.dp, MpesaEmeraldLight.copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("qr_scanner_gallery_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoLibrary,
                                    contentDescription = null,
                                    tint = MpesaEmeraldLight,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.qr_scanner_import_gallery),
                                    color = PaperWhite,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QrScannerViewfinderOverlay(
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "qr_scan_pulse")
    val sweepFraction by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "qr_scan_line"
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val boxSide = (size.minDimension * 0.66f).coerceIn(180.dp.toPx(), 290.dp.toPx())
            val left = (size.width - boxSide) / 2f
            val top = (size.height - boxSide) / 2f - 28.dp.toPx()
            val right = left + boxSide
            val bottom = top + boxSide
            val cornerRadius = 18.dp.toPx()

            val cutoutPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(left, top, right, bottom),
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                    )
                )
            }

            clipPath(cutoutPath, clipOp = ClipOp.Difference) {
                drawRect(color = MidnightInk.copy(alpha = 0.62f))
            }

            val strokePx = 4.dp.toPx()
            val bracketLen = 30.dp.toPx()
            val accent = MpesaEmeraldLight

            // Top-left bracket
            drawLine(accent, Offset(left, top + cornerRadius), Offset(left, top + cornerRadius + bracketLen), strokePx, StrokeCap.Round)
            drawLine(accent, Offset(left + cornerRadius, top), Offset(left + cornerRadius + bracketLen, top), strokePx, StrokeCap.Round)
            // Top-right bracket
            drawLine(accent, Offset(right, top + cornerRadius), Offset(right, top + cornerRadius + bracketLen), strokePx, StrokeCap.Round)
            drawLine(accent, Offset(right - cornerRadius, top), Offset(right - cornerRadius - bracketLen, top), strokePx, StrokeCap.Round)
            // Bottom-left bracket
            drawLine(accent, Offset(left, bottom - cornerRadius), Offset(left, bottom - cornerRadius - bracketLen), strokePx, StrokeCap.Round)
            drawLine(accent, Offset(left + cornerRadius, bottom), Offset(left + cornerRadius + bracketLen, bottom), strokePx, StrokeCap.Round)
            // Bottom-right bracket
            drawLine(accent, Offset(right, bottom - cornerRadius), Offset(right, bottom - cornerRadius - bracketLen), strokePx, StrokeCap.Round)
            drawLine(accent, Offset(right - cornerRadius, bottom), Offset(right - cornerRadius - bracketLen, bottom), strokePx, StrokeCap.Round)

            if (!isPaused) {
                val lineY = top + (boxSide * sweepFraction)
                drawLine(
                    color = accent.copy(alpha = 0.78f),
                    start = Offset(left + 14.dp.toPx(), lineY),
                    end = Offset(right - 14.dp.toPx(), lineY),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
