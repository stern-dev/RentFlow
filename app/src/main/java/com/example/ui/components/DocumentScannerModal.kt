package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.ai.ScanOcrUiState
import com.example.domain.finance.CurrencyUtils
import com.example.domain.model.DocumentType
import com.example.domain.model.ScanFilterMode
import com.example.domain.model.ScannedDocument
import com.example.ui.media.DocumentScanProcessor
import com.example.ui.media.NormalizedCropRect
import com.example.ui.theme.BrushedSilver
import com.example.ui.theme.CobaltLight
import com.example.ui.theme.MidnightInk
import com.example.ui.theme.MutedCobalt
import com.example.ui.theme.PaperWhite
import com.example.ui.theme.StatusError
import java.io.File
import kotlin.math.hypot
import kotlinx.coroutines.launch

data class PendingScanPayload(
    val documentType: DocumentType,
    val title: String,
    val notes: String?,
    val filterMode: ScanFilterMode,
    val pageImageRefs: List<String>,
    val extractedReference: String?,
    val extractedAmountCents: Long?,
    val extractedDateText: String?,
    val extractedSummary: String?,
    val aiExtracted: Boolean,
    val tenantVisible: Boolean
)

private data class EditableScanPage(
    val id: Long,
    val rawBitmap: Bitmap,
    val cropRect: NormalizedCropRect = NormalizedCropRect(),
    val rotationDegrees: Int = 0,
    val filterMode: ScanFilterMode = ScanFilterMode.CRISP_DOCUMENT
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DocumentScannerModal(
    unitLabel: String,
    tenantLabel: String? = null,
    allowedDocumentTypes: List<DocumentType> = DocumentType.entries,
    initialDocumentType: DocumentType = DocumentType.LEASE_AGREEMENT,
    allowTenantVisibilityToggle: Boolean = true,
    initialTenantVisible: Boolean = true,
    scanOcrState: ScanOcrUiState,
    onExtractWithAi: (Bitmap, DocumentType?) -> Unit,
    onClearOcr: () -> Unit,
    confirmButtonLabel: String? = null,
    onDismiss: () -> Unit,
    onConfirmScan: (PendingScanPayload) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val safeTypes = remember(allowedDocumentTypes) {
        allowedDocumentTypes.ifEmpty { DocumentType.entries }
    }
    var selectedType by remember {
        mutableStateOf(if (initialDocumentType in safeTypes) initialDocumentType else safeTypes.first())
    }
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var extractedRef by remember { mutableStateOf("") }
    var extractedAmountText by remember { mutableStateOf("") }
    var extractedDateText by remember { mutableStateOf("") }
    var extractedSummary by remember { mutableStateOf("") }
    var aiExtracted by remember { mutableStateOf(false) }
    var tenantVisible by remember { mutableStateOf(initialTenantVisible) }
    var visibilityTouched by remember { mutableStateOf(false) }

    val pages = remember { mutableStateListOf<EditableScanPage>() }
    var nextPageId by remember { mutableLongStateOf(1L) }
    var selectedPageIdx by remember { mutableIntStateOf(0) }
    var isCameraStep by remember { mutableStateOf(true) }
    var isCropping by remember { mutableStateOf(false) }
    var isEncoding by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { onClearOcr() }
    DisposableEffect(Unit) { onDispose { onClearOcr() } }

    LaunchedEffect(scanOcrState) {
        if (scanOcrState is ScanOcrUiState.Success) {
            val ext = scanOcrState.extraction
            if (title.isBlank()) title = ext.title
            if (ext.documentType in safeTypes) {
                selectedType = ext.documentType
                if (!visibilityTouched && allowTenantVisibilityToggle) {
                    tenantVisible = ext.documentType in listOf(DocumentType.LEASE_AGREEMENT, DocumentType.PAYMENT_RECEIPT)
                }
            }
            if (!ext.referenceCode.isNullOrBlank()) extractedRef = ext.referenceCode
            if (ext.amountCents != null && ext.amountCents > 0L) {
                extractedAmountText = CurrencyUtils.centsToInputString(ext.amountCents)
            }
            if (!ext.documentDate.isNullOrBlank()) extractedDateText = ext.documentDate
            if (ext.summary.isNotBlank()) extractedSummary = ext.summary
            aiExtracted = true
        }
    }

    fun appendBitmapPage(bmp: Bitmap) {
        if (pages.size >= ScannedDocument.MAX_PAGES) {
            localError = "Maximum ${ScannedDocument.MAX_PAGES} pages per document."
            return
        }
        pages.add(EditableScanPage(id = nextPageId++, rawBitmap = bmp))
        selectedPageIdx = pages.lastIndex
        isCameraStep = false
        isCropping = false
        localError = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = DocumentScanProcessor.decodeUriForEditing(context, uri)
                if (bmp != null) appendBitmapPage(bmp)
                else localError = "Could not load selected image."
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BackHandler {
            when {
                isCropping -> isCropping = false
                !isCameraStep && pages.isNotEmpty() -> onDismiss()
                isCameraStep && pages.isNotEmpty() -> isCameraStep = false
                else -> onDismiss()
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize().testTag("document_scanner_modal")
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(stringResource(R.string.doc_scanner_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = buildString {
                                        append(unitLabel)
                                        if (!tenantLabel.isNullOrBlank()) append(" • $tenantLabel")
                                        if (pages.isNotEmpty()) append(" • ${pages.size}/${ScannedDocument.MAX_PAGES}p")
                                    },
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
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("doc_scanner_close_button")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Close scanner")
                            }
                        },
                        actions = {
                            if (!isCameraStep && pages.size < ScannedDocument.MAX_PAGES) {
                                TextButton(
                                    onClick = { isCropping = false; isCameraStep = true },
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("doc_scanner_add_page_button")
                                ) {
                                    Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add Page")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
            ) { innerPadding ->
                if (isCameraStep) {
                    CameraCaptureStep(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        hasExistingPages = pages.isNotEmpty(),
                        errorMessage = localError,
                        onBackToReview = { isCameraStep = false },
                        onBitmapCaptured = { bmp -> appendBitmapPage(bmp) },
                        onPickFromGallery = {
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onError = { localError = it }
                    )
                } else if (pages.isNotEmpty()) {
                    val activeIdx = selectedPageIdx.coerceIn(0, pages.lastIndex)
                    val currentPage = pages[activeIdx]
                    val previewBitmap = remember(
                        currentPage.id,
                        currentPage.cropRect,
                        currentPage.rotationDegrees,
                        currentPage.filterMode,
                        isCropping
                    ) {
                        if (isCropping) {
                            DocumentScanProcessor.processPagePreview(
                                source = currentPage.rawBitmap,
                                cropRect = NormalizedCropRect(),
                                rotationDegrees = currentPage.rotationDegrees,
                                filterMode = ScanFilterMode.ORIGINAL,
                                previewMaxDimPx = 960
                            )
                        } else {
                            DocumentScanProcessor.processPagePreview(
                                source = currentPage.rawBitmap,
                                cropRect = currentPage.cropRect,
                                rotationDegrees = currentPage.rotationDegrees,
                                filterMode = currentPage.filterMode,
                                previewMaxDimPx = 960
                            )
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        if (!localError.isNullOrBlank()) ErrorBannerCard(localError!!)
                        if (scanOcrState is ScanOcrUiState.Error) ErrorBannerCard(scanOcrState.message)

                        // Interactive preview / crop stage
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MidnightInk),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 220.dp, max = 320.dp)
                                    .padding(8.dp)
                            ) {
                                val aspect = (previewBitmap.width.toFloat() / previewBitmap.height.toFloat().coerceAtLeast(1f))
                                    .coerceIn(0.45f, 2.2f)
                                if (isCropping) {
                                    InteractiveCropOverlay(
                                        bitmap = previewBitmap,
                                        cropRect = currentPage.cropRect,
                                        aspectRatio = aspect,
                                        onCropChange = { updated ->
                                            pages[activeIdx] = currentPage.copy(cropRect = updated)
                                        }
                                    )
                                } else {
                                    Image(
                                        bitmap = previewBitmap.asImageBitmap(),
                                        contentDescription = "Scanned page ${activeIdx + 1}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .aspectRatio(aspect)
                                            .clip(MaterialTheme.shapes.medium)
                                    )
                                }
                            }
                        }

                        // Page queue thumbnails
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            itemsIndexed(pages, key = { _, p -> p.id }) { idx, page ->
                                val thumb = remember(page.id, page.cropRect, page.rotationDegrees, page.filterMode) {
                                    DocumentScanProcessor.processPagePreview(
                                        page.rawBitmap, page.cropRect, page.rotationDegrees, page.filterMode, 180
                                    ).asImageBitmap()
                                }
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .border(
                                            width = if (idx == activeIdx) 2.5.dp else 1.dp,
                                            color = if (idx == activeIdx) MutedCobalt else BrushedSilver,
                                            shape = MaterialTheme.shapes.small
                                        )
                                        .clickable { selectedPageIdx = idx; isCropping = false }
                                ) {
                                    Image(
                                        bitmap = thumb,
                                        contentDescription = "Page ${idx + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Surface(
                                        color = MidnightInk.copy(alpha = 0.8f),
                                        shape = RoundedCornerShape(bottomEnd = 6.dp),
                                        modifier = Modifier.align(Alignment.TopStart)
                                    ) {
                                        Text(
                                            text = "${idx + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PaperWhite,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                    Surface(
                                        color = StatusError.copy(alpha = 0.9f),
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(20.dp)
                                            .clickable {
                                                pages.removeAt(idx)
                                                if (pages.isEmpty()) isCameraStep = true
                                                else selectedPageIdx = activeIdx.coerceAtMost(pages.lastIndex)
                                            }
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Remove page ${idx + 1}", tint = PaperWhite, modifier = Modifier.padding(2.dp))
                                    }
                                }
                            }
                        }

                        // Crop, Rotate & Filter Controls
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { isCropping = !isCropping },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("doc_scanner_crop_button")
                            ) {
                                Icon(if (isCropping) Icons.Filled.Check else Icons.Filled.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isCropping) "Done Crop" else "Crop")
                            }
                            if (isCropping && !currentPage.cropRect.isFullFrame()) {
                                OutlinedButton(
                                    onClick = { pages[activeIdx] = currentPage.copy(cropRect = NormalizedCropRect()) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Reset crop", modifier = Modifier.size(16.dp))
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    pages[activeIdx] = currentPage.copy(rotationDegrees = (currentPage.rotationDegrees + 90) % 360)
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("doc_scanner_rotate_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Rotate 90°")
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ScanFilterMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = currentPage.filterMode == mode,
                                    onClick = { pages[activeIdx] = currentPage.copy(filterMode = mode) },
                                    label = { Text(mode.label) }
                                )
                            }
                        }

                        // Smart AI OCR Extraction
                        OutlinedButton(
                            onClick = {
                                val ocrSource = DocumentScanProcessor.processPagePreview(
                                    currentPage.rawBitmap,
                                    currentPage.cropRect,
                                    currentPage.rotationDegrees,
                                    currentPage.filterMode,
                                    1280
                                )
                                onExtractWithAi(ocrSource, selectedType)
                            },
                            enabled = scanOcrState !is ScanOcrUiState.Loading && !isEncoding,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("doc_scanner_ai_extract_button")
                        ) {
                            if (scanOcrState is ScanOcrUiState.Loading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Reading Document with AI...")
                            } else {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.doc_scanner_smart_extract))
                            }
                        }

                        if (aiExtracted) {
                            StatusPill("AI Smart Extraction Applied", PillTone.INFO)
                        }

                        Text("Document Category", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            safeTypes.forEach { dt ->
                                FilterChip(
                                    selected = selectedType == dt,
                                    onClick = {
                                        selectedType = dt
                                        if (!visibilityTouched && allowTenantVisibilityToggle) {
                                            tenantVisible = dt in listOf(DocumentType.LEASE_AGREEMENT, DocumentType.PAYMENT_RECEIPT)
                                        }
                                    },
                                    label = { Text(dt.label) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it; localError = null },
                            label = { Text("Document Title") },
                            placeholder = { Text("e.g. ${selectedType.label} — $unitLabel") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("doc_scanner_title_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = extractedRef,
                                onValueChange = { extractedRef = it },
                                label = { Text("Ref / Code") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("doc_scanner_ref_input")
                            )
                            OutlinedTextField(
                                value = extractedAmountText,
                                onValueChange = { extractedAmountText = it },
                                label = { Text("Amount (KES)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("doc_scanner_amount_input")
                            )
                        }
                        if (extractedDateText.isNotBlank() || aiExtracted) {
                            OutlinedTextField(
                                value = extractedDateText,
                                onValueChange = { extractedDateText = it },
                                label = { Text("Document Date") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes / Filing Remarks (Optional)") },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (extractedSummary.isNotBlank()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "AI Summary: $extractedSummary",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        if (allowTenantVisibilityToggle) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Share with Tenant in Unit Vault", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = if (tenantVisible) "Linked tenant can view & export this document"
                                        else "Private to landlord & property managers",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = tenantVisible,
                                    onCheckedChange = { tenantVisible = it; visibilityTouched = true }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val resolvedTitle = title.trim().ifBlank { "${selectedType.label} • $unitLabel" }
                                if (resolvedTitle.length < 2) {
                                    localError = "Enter a document title."
                                    return@Button
                                }
                                isEncoding = true
                                localError = null
                                scope.launch {
                                    val renderedBitmaps = pages.map { page ->
                                        DocumentScanProcessor.processPagePreview(
                                            source = page.rawBitmap,
                                            cropRect = page.cropRect,
                                            rotationDegrees = page.rotationDegrees,
                                            filterMode = page.filterMode,
                                            previewMaxDimPx = 1120
                                        )
                                    }
                                    val compressed = DocumentScanProcessor.compressPagesToBudget(renderedBitmaps)
                                    renderedBitmaps.forEach { it.recycle() }
                                    isEncoding = false
                                    compressed.fold(
                                        onSuccess = { refs ->
                                            val parsedAmount = extractedAmountText.trim().takeIf { it.isNotBlank() }
                                                ?.let { CurrencyUtils.parseKesToCents(it) }
                                            onConfirmScan(
                                                PendingScanPayload(
                                                    documentType = selectedType,
                                                    title = resolvedTitle.take(140),
                                                    notes = notes.trim().takeIf { it.isNotBlank() },
                                                    filterMode = currentPage.filterMode,
                                                    pageImageRefs = refs,
                                                    extractedReference = extractedRef.trim().uppercase().takeIf { it.isNotBlank() },
                                                    extractedAmountCents = parsedAmount,
                                                    extractedDateText = extractedDateText.trim().takeIf { it.isNotBlank() },
                                                    extractedSummary = extractedSummary.trim().takeIf { it.isNotBlank() },
                                                    aiExtracted = aiExtracted,
                                                    tenantVisible = if (allowTenantVisibilityToggle) tenantVisible else true
                                                )
                                            )
                                        },
                                        onFailure = { err ->
                                            localError = err.localizedMessage ?: "Unable to compress scanned pages."
                                        }
                                    )
                                }
                            },
                            enabled = !isEncoding,
                            colors = ButtonDefaults.buttonColors(containerColor = MutedCobalt),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("doc_scanner_save_button")
                        ) {
                            if (isEncoding) {
                                CircularProgressIndicator(color = PaperWhite, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Compressing ${pages.size} Page(s)...")
                            } else {
                                Text(confirmButtonLabel ?: stringResource(R.string.doc_scanner_save_upload))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraCaptureStep(
    modifier: Modifier,
    hasExistingPages: Boolean,
    errorMessage: String?,
    onBackToReview: () -> Unit,
    onBitmapCaptured: (Bitmap) -> Unit,
    onPickFromGallery: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    var flashEnabled by remember { mutableStateOf(false) }
    var cameraUnavailableError by remember { mutableStateOf<String?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.background(MidnightInk).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!errorMessage.isNullOrBlank()) ErrorBannerCard(errorMessage)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (hasCameraPermission && cameraUnavailableError == null) {
                val previewView = remember(context) {
                    PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
                }
                DisposableEffect(lifecycleOwner, previewView) {
                    val providerFuture = ProcessCameraProvider.getInstance(context)
                    val executor = ContextCompat.getMainExecutor(context)
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
                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .setTargetResolution(Size(1280, 1600))
                                .build()
                            provider.unbindAll()
                            val cam = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                            boundCamera = cam
                            imageCapture = capture
                        }.onFailure { err ->
                            cameraUnavailableError = err.localizedMessage ?: "Camera preview unavailable."
                        }
                    }, executor)
                    onDispose {
                        if (providerFuture.isDone) {
                            runCatching { providerFuture.get().unbindAll() }
                        }
                    }
                }
                LaunchedEffect(flashEnabled, boundCamera) {
                    if (boundCamera?.cameraInfo?.hasFlashUnit() == true) {
                        boundCamera?.cameraControl?.enableTorch(flashEnabled)
                    }
                }

                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

                // Cobalt alignment corner reticle
                Canvas(modifier = Modifier.fillMaxSize().padding(28.dp)) {
                    val corner = 34.dp.toPx()
                    val stroke = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    val w = size.width
                    val h = size.height
                    drawLine(CobaltLight, Offset(0f, 0f), Offset(corner, 0f), stroke.width, StrokeCap.Round)
                    drawLine(CobaltLight, Offset(0f, 0f), Offset(0f, corner), stroke.width, StrokeCap.Round)
                    drawLine(CobaltLight, Offset(w, 0f), Offset(w - corner, 0f), stroke.width, StrokeCap.Round)
                    drawLine(CobaltLight, Offset(w, 0f), Offset(w, corner), stroke.width, StrokeCap.Round)
                    drawLine(CobaltLight, Offset(0f, h), Offset(corner, h), stroke.width, StrokeCap.Round)
                    drawLine(CobaltLight, Offset(0f, h), Offset(0f, h - corner), stroke.width, StrokeCap.Round)
                    drawLine(CobaltLight, Offset(w, h), Offset(w - corner, h), stroke.width, StrokeCap.Round)
                    drawLine(CobaltLight, Offset(w, h), Offset(w, h - corner), stroke.width, StrokeCap.Round)
                }

                if (boundCamera?.cameraInfo?.hasFlashUnit() == true) {
                    IconButton(
                        onClick = { flashEnabled = !flashEnabled },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(MidnightInk.copy(alpha = 0.7f), CircleShape)
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            contentDescription = stringResource(R.string.doc_scanner_flash_cd),
                            tint = PaperWhite
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = CobaltLight, modifier = Modifier.size(48.dp))
                    Text(
                        text = cameraUnavailableError ?: "Camera permission lets you snap lease agreements and receipts directly.",
                        color = PaperWhite,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!hasCameraPermission) {
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = MutedCobalt),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("doc_scanner_grant_camera_button")
                        ) { Text("Enable Camera") }
                    }
                    OutlinedButton(
                        onClick = onPickFromGallery,
                        border = BorderStroke(1.dp, BrushedSilver),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = PaperWhite, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.doc_scanner_gallery_fallback), color = PaperWhite)
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onPickFromGallery,
                border = BorderStroke(1.dp, BrushedSilver.copy(alpha = 0.6f)),
                modifier = Modifier.heightIn(min = 48.dp).testTag("doc_scanner_gallery_button")
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = PaperWhite, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Gallery", color = PaperWhite)
            }

            if (hasCameraPermission && cameraUnavailableError == null) {
                Surface(
                    shape = CircleShape,
                    color = MutedCobalt,
                    border = BorderStroke(3.dp, PaperWhite),
                    modifier = Modifier
                        .size(68.dp)
                        .clickable(enabled = !isCapturing && imageCapture != null) {
                            val capture = imageCapture ?: return@clickable
                            isCapturing = true
                            val tempFile = File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
                            val options = ImageCapture.OutputFileOptions.Builder(tempFile).build()
                            capture.takePicture(
                                options,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        scope.launch {
                                            val bmp = DocumentScanProcessor.decodeFileForEditing(tempFile)
                                            tempFile.delete()
                                            isCapturing = false
                                            if (bmp != null) onBitmapCaptured(bmp)
                                            else onError("Unable to decode captured photo.")
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        tempFile.delete()
                                        isCapturing = false
                                        onError(exception.localizedMessage ?: "Capture failed.")
                                    }
                                }
                            )
                        }
                        .testTag("doc_scanner_shutter_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isCapturing) {
                            CircularProgressIndicator(color = PaperWhite, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                        } else {
                            Icon(
                                Icons.Filled.CameraAlt,
                                contentDescription = stringResource(R.string.doc_scanner_capture_cd),
                                tint = PaperWhite,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
            }

            if (hasExistingPages) {
                TextButton(onClick = onBackToReview, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Done", color = CobaltLight, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Spacer(Modifier.width(64.dp))
            }
        }
    }
}

@Composable
private fun InteractiveCropOverlay(
    bitmap: Bitmap,
    cropRect: NormalizedCropRect,
    aspectRatio: Float,
    onCropChange: (NormalizedCropRect) -> Unit
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var activeHandle by remember { mutableIntStateOf(-1) } // 0=TL,1=TR,2=BL,3=BR

    Box(
        modifier = Modifier
            .aspectRatio(aspectRatio)
            .onSizeChanged { boxSize = it }
            .pointerInput(cropRect, boxSize) {
                detectDragGestures(
                    onDragStart = { start ->
                        if (boxSize.width <= 0 || boxSize.height <= 0) return@detectDragGestures
                        val w = boxSize.width.toFloat()
                        val h = boxSize.height.toFloat()
                        val corners = listOf(
                            Offset(cropRect.left * w, cropRect.top * h),
                            Offset(cropRect.right * w, cropRect.top * h),
                            Offset(cropRect.left * w, cropRect.bottom * h),
                            Offset(cropRect.right * w, cropRect.bottom * h)
                        )
                        activeHandle = corners.indices.minByOrNull { idx ->
                            hypot(corners[idx].x - start.x, corners[idx].y - start.y)
                        } ?: -1
                    },
                    onDragEnd = { activeHandle = -1 },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (boxSize.width <= 0 || boxSize.height <= 0 || activeHandle < 0) return@detectDragGestures
                        val dx = dragAmount.x / boxSize.width.toFloat()
                        val dy = dragAmount.y / boxSize.height.toFloat()
                        val updated = when (activeHandle) {
                            0 -> cropRect.copy(left = cropRect.left + dx, top = cropRect.top + dy)
                            1 -> cropRect.copy(right = cropRect.right + dx, top = cropRect.top + dy)
                            2 -> cropRect.copy(left = cropRect.left + dx, bottom = cropRect.bottom + dy)
                            3 -> cropRect.copy(right = cropRect.right + dx, bottom = cropRect.bottom + dy)
                            else -> cropRect
                        }.normalized()
                        onCropChange(updated)
                    }
                )
            }
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Crop document preview",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val l = cropRect.left * size.width
            val t = cropRect.top * size.height
            val r = cropRect.right * size.width
            val b = cropRect.bottom * size.height
            val scrim = Color.Black.copy(alpha = 0.45f)
            drawRect(scrim, Offset.Zero, ComposeSize(size.width, t))
            drawRect(scrim, Offset(0f, b), ComposeSize(size.width, size.height - b))
            drawRect(scrim, Offset(0f, t), ComposeSize(l, b - t))
            drawRect(scrim, Offset(r, t), ComposeSize(size.width - r, b - t))

            drawRect(
                color = CobaltLight,
                topLeft = Offset(l, t),
                size = ComposeSize(r - l, b - t),
                style = Stroke(width = 2.5.dp.toPx())
            )
            val handleRadius = 9.dp.toPx()
            listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach { pt ->
                drawCircle(PaperWhite, radius = handleRadius, center = pt)
                drawCircle(MutedCobalt, radius = handleRadius - 2.dp.toPx(), center = pt)
            }
        }
    }
}
