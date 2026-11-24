package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.domain.finance.GeneratedChecklistPdf
import com.example.domain.finance.MoveInChecklistPdfService
import com.example.domain.finance.PdfPrintAndShareHelper
import com.example.domain.model.Lease
import com.example.domain.model.MoveInConditionChecklist
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import com.example.ui.theme.StatusWarningBgDark
import com.example.ui.theme.StatusWarningLight
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface PdfViewerUiState {
    data object Generating : PdfViewerUiState
    data class Ready(val document: GeneratedChecklistPdf) : PdfViewerUiState
    data class Error(val message: String) : PdfViewerUiState
}

internal class PdfDocumentRenderController(file: File) {
    private val mutex = Mutex()
    private val pfd: ParcelFileDescriptor? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }.getOrNull()
    private val renderer: PdfRenderer? = pfd?.let { descriptor ->
        runCatching { PdfRenderer(descriptor) }.getOrNull()
    }

    val pageCount: Int
        get() = renderer?.pageCount ?: 0

    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int = 1240): ImageBitmap? =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val activeRenderer = renderer ?: return@withContext null
                if (pageIndex < 0 || pageIndex >= activeRenderer.pageCount) return@withContext null
                runCatching {
                    activeRenderer.openPage(pageIndex).use { page ->
                        val safeWidth = targetWidthPx.coerceIn(595, 1650)
                        val scale = (safeWidth.toFloat() / page.width.toFloat()).coerceIn(1f, 2.8f)
                        val bmpW = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val bmpH = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(AndroidColor.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap.asImageBitmap()
                    }
                }.getOrNull()
            }
        }

    fun close() {
        runCatching { renderer?.close() }
        runCatching { pfd?.close() }
    }
}

@Composable
fun MoveInChecklistPdfViewerDialog(
    checklist: MoveInConditionChecklist,
    lease: Lease? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var retryNonce by remember { mutableIntStateOf(0) }
    var uiState by remember { mutableStateOf<PdfViewerUiState>(PdfViewerUiState.Generating) }
    var actionNotice by remember { mutableStateOf<String?>(null) }

    val fallbackErrorMsg = stringResource(R.string.pdf_viewer_generation_error)
    val printUnavailableMsg = stringResource(R.string.pdf_viewer_print_unavailable)
    val shareTitle = stringResource(R.string.pdf_viewer_share)

    LaunchedEffect(checklist.id, checklist.updatedAt, lease?.id, lease?.updatedAt, retryNonce) {
        uiState = PdfViewerUiState.Generating
        actionNotice = null
        val result = MoveInChecklistPdfService.generateMoveInChecklistPdf(
            context = context,
            checklist = checklist,
            lease = lease
        )
        uiState = result.fold(
            onSuccess = { generated -> PdfViewerUiState.Ready(generated) },
            onFailure = { err ->
                PdfViewerUiState.Error(err.localizedMessage ?: fallbackErrorMsg)
            }
        )
    }

    val propertyDisplay = checklist.propertyName.ifBlank { lease?.propertyName.orEmpty() }
    val unitDisplay = checklist.unitNumber.ifBlank { lease?.unitNumber.orEmpty() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        PdfViewerFullScreen(
            documentTitle = stringResource(R.string.pdf_viewer_title_default),
            documentSubtitle = "$propertyDisplay • Unit $unitDisplay",
            uiState = uiState,
            actionNotice = actionNotice,
            onDismissNotice = { actionNotice = null },
            onRetry = { retryNonce++ },
            onDismiss = onDismiss,
            onShare = { doc ->
                PdfPrintAndShareHelper.sharePdfFile(
                    context = context,
                    file = doc.file,
                    chooserTitle = shareTitle,
                    subject = "${doc.title} — ${doc.subtitle}"
                )
            },
            onPrint = { doc ->
                PdfPrintAndShareHelper.printPdfFile(
                    context = context,
                    file = doc.file,
                    jobName = "RentFlow_${doc.subtitle.replace(" ", "_")}",
                    onUnavailableOrError = { errMsg ->
                        actionNotice = errMsg.ifBlank { printUnavailableMsg }
                    }
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerFullScreen(
    documentTitle: String,
    documentSubtitle: String,
    uiState: PdfViewerUiState,
    actionNotice: String? = null,
    onDismissNotice: () -> Unit = {},
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onShare: (GeneratedChecklistPdf) -> Unit,
    onPrint: (GeneratedChecklistPdf) -> Unit,
    customPageRenderer: (suspend (GeneratedChecklistPdf, Int) -> ImageBitmap?)? = null
) {
    val listState = rememberLazyListState()
    var targetZoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val animatedZoom by animateFloatAsState(
        targetValue = targetZoom,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pdf_viewer_zoom"
    )

    LaunchedEffect(uiState) {
        targetZoom = 1f
        panOffset = Offset.Zero
    }

    fun clampPan(raw: Offset, zoom: Float): Offset {
        if (zoom <= 1.02f || viewportSize.width <= 0 || viewportSize.height <= 0) {
            return Offset.Zero
        }
        val maxX = (viewportSize.width * (zoom - 1f)) / 2f
        val maxY = (viewportSize.height * (zoom - 1f)) / 2f
        return Offset(
            x = raw.x.coerceIn(-maxX, maxX),
            y = raw.y.coerceIn(-maxY, maxY)
        )
    }

    BackHandler { onDismiss() }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .testTag("pdf_viewer_screen")
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = documentTitle,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = documentSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("pdf_viewer_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.pdf_viewer_close_cd)
                            )
                        }
                    },
                    actions = {
                        if (uiState is PdfViewerUiState.Ready) {
                            StatusPill(
                                label = if (uiState.document.isFinalizedSigned) {
                                    stringResource(R.string.pdf_viewer_status_finalized)
                                } else {
                                    stringResource(R.string.pdf_viewer_status_draft)
                                },
                                tone = if (uiState.document.isFinalizedSigned) {
                                    PillTone.SUCCESS
                                } else {
                                    PillTone.WARNING
                                },
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .semantics(mergeDescendants = true) {}
                                    .testTag("pdf_viewer_status_pill")
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                if (uiState is PdfViewerUiState.Ready) {
                    val doc = uiState.document
                    Surface(
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            // Zoom stepper group
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val next = (targetZoom - 0.5f).coerceIn(1f, 4f)
                                        targetZoom = next
                                        panOffset = clampPan(panOffset, next)
                                    },
                                    enabled = targetZoom > 1.05f,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("pdf_viewer_zoom_out_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Remove,
                                        contentDescription = stringResource(R.string.pdf_viewer_zoom_out_cd)
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        targetZoom = 1f
                                        panOffset = Offset.Zero
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("pdf_viewer_zoom_reset_button")
                                ) {
                                    Text(
                                        text = "${(animatedZoom * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val next = (targetZoom + 0.5f).coerceIn(1f, 4f)
                                        targetZoom = next
                                        panOffset = clampPan(panOffset, next)
                                    },
                                    enabled = targetZoom < 3.95f,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("pdf_viewer_zoom_in_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = stringResource(R.string.pdf_viewer_zoom_in_cd)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            OutlinedButton(
                                onClick = { onPrint(doc) },
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("pdf_viewer_print_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Print,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.pdf_viewer_print), maxLines = 1)
                            }

                            Button(
                                onClick = { onShare(doc) },
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("pdf_viewer_share_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.pdf_viewer_share), maxLines = 1)
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            val isDark = LocalIsDarkTheme.current
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (uiState is PdfViewerUiState.Ready && !uiState.document.isFinalizedSigned) {
                    val warnBg = if (isDark) StatusWarningBgDark else StatusWarningBg
                    val warnFg = if (isDark) StatusWarningLight else StatusWarning
                    Surface(
                        color = warnBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pdf_viewer_draft_banner")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = warnFg,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.pdf_viewer_draft_notice),
                                style = MaterialTheme.typography.labelSmall,
                                color = warnFg
                            )
                        }
                    }
                }

                if (!actionNotice.isNullOrBlank()) {
                    ErrorBannerCard(
                        message = actionNotice,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    TextButton(
                        onClick = onDismissNotice,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(end = 16.dp)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Dismiss")
                    }
                }

                when (uiState) {
                    is PdfViewerUiState.Generating -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                                .testTag("pdf_viewer_loading_box")
                        ) {
                            Card(
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                                    Text(
                                        text = stringResource(R.string.pdf_viewer_generating),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    is PdfViewerUiState.Error -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                                .testTag("pdf_viewer_error_box")
                        ) {
                            Card(
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, StatusError.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Text(
                                        text = uiState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = StatusError
                                    )
                                    Button(
                                        onClick = onRetry,
                                        shape = RoundedCornerShape(999.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondary,
                                            contentColor = MaterialTheme.colorScheme.onSecondary
                                        ),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("pdf_viewer_retry_button")
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.pdf_viewer_retry))
                                    }
                                }
                            }
                        }
                    }

                    is PdfViewerUiState.Ready -> {
                        val doc = uiState.document
                        val controller = remember(doc.file.absolutePath, doc.generatedAtEpochMs, customPageRenderer) {
                            if (customPageRenderer == null) PdfDocumentRenderController(doc.file) else null
                        }
                        DisposableEffect(controller) {
                            onDispose { controller?.close() }
                        }

                        val totalPages = remember(doc.pageCount, controller) {
                            (controller?.pageCount?.takeIf { it > 0 } ?: doc.pageCount).coerceAtLeast(1)
                        }
                        val renderedPages = remember(doc.file.absolutePath, doc.generatedAtEpochMs) {
                            mutableStateMapOf<Int, ImageBitmap>()
                        }
                        val currentPageNumber by remember(totalPages) {
                            derivedStateOf {
                                (listState.firstVisibleItemIndex + 1).coerceIn(1, totalPages)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                .clipToBounds()
                                .onSizeChanged { viewportSize = it }
                                .pointerInput(doc.file.absolutePath) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (targetZoom > 1.05f) {
                                                targetZoom = 1f
                                                panOffset = Offset.Zero
                                            } else {
                                                targetZoom = 2.2f
                                            }
                                        }
                                    )
                                }
                                .pointerInput(doc.file.absolutePath) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        do {
                                            val event = awaitPointerEvent()
                                            val pressedCount = event.changes.count { it.pressed }
                                            if (pressedCount >= 2) {
                                                val zoomFactor = event.calculateZoom()
                                                val panDelta = event.calculatePan()
                                                val nextZoom = (targetZoom * zoomFactor).coerceIn(1f, 4f)
                                                targetZoom = nextZoom
                                                panOffset = clampPan(panOffset + panDelta, nextZoom)
                                                event.changes.forEach { change ->
                                                    if (change.positionChanged()) change.consume()
                                                }
                                            } else if (pressedCount == 1 && targetZoom > 1.02f) {
                                                val panDelta = event.calculatePan()
                                                if (panDelta != Offset.Zero) {
                                                    panOffset = clampPan(panOffset + panDelta, targetZoom)
                                                    event.changes.forEach { change ->
                                                        if (change.positionChanged()) change.consume()
                                                    }
                                                }
                                            }
                                        } while (event.changes.any { it.pressed })
                                    }
                                }
                        ) {
                            LazyColumn(
                                state = listState,
                                userScrollEnabled = targetZoom <= 1.02f,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = animatedZoom
                                        scaleY = animatedZoom
                                        translationX = if (animatedZoom > 1.01f) panOffset.x else 0f
                                        translationY = if (animatedZoom > 1.01f) panOffset.y else 0f
                                    }
                                    .testTag("pdf_viewer_pages_list")
                            ) {
                                items(count = totalPages, key = { pageIndex -> pageIndex }) { pageIndex ->
                                    LaunchedEffect(pageIndex, doc.file.absolutePath, doc.generatedAtEpochMs) {
                                        if (renderedPages[pageIndex] == null) {
                                            val bmp = customPageRenderer?.invoke(doc, pageIndex)
                                                ?: controller?.renderPage(pageIndex)
                                            if (bmp != null) {
                                                renderedPages[pageIndex] = bmp
                                            }
                                        }
                                    }

                                    PdfPageSheetCard(
                                        pageNumber = pageIndex + 1,
                                        totalPages = totalPages,
                                        bitmap = renderedPages[pageIndex],
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .widthIn(max = 820.dp)
                                            .testTag("pdf_viewer_page_card_$pageIndex")
                                    )
                                }
                            }

                            // Floating Page & Zoom Indicator Pill
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shape = RoundedCornerShape(999.dp),
                                shadowElevation = 0.dp,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .semantics(mergeDescendants = true) {}
                                    .testTag("pdf_viewer_page_zoom_badge")
                            ) {
                                Text(
                                    text = "Page $currentPageNumber of $totalPages • ${(animatedZoom * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
private fun PdfPageSheetCard(
    pageNumber: Int,
    totalPages: Int,
    bitmap: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = CrispWhite),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.aspectRatio(595f / 842f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "PDF page $pageNumber of $totalPages",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Rendering page $pageNumber…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
