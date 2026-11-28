package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.DownloadedPdfResult
import com.example.domain.finance.PaymentReceiptPdfGenerator
import com.example.domain.finance.PdfDownloadHelper
import com.example.domain.finance.PdfPrintAndShareHelper
import com.example.domain.finance.ReportExporter
import com.example.domain.model.Announcement
import com.example.domain.model.DigitalSignature
import com.example.domain.model.Invoice
import com.example.domain.model.Lease
import com.example.domain.model.Payment
import com.example.domain.model.PaymentReceiptPdf
import com.example.domain.model.PaymentReceiptStatus
import com.example.domain.model.ScannedDocument
import com.example.ui.media.DocumentScanProcessor
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.PaperWhite
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusSuccessBgDark
import com.example.ui.theme.StatusSuccessLight
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TenantDocumentCategory(val label: String) {
    VAULT("Vault Doc"),
    RECEIPT("Receipt"),
    LEASE("Tenancy Lease"),
    NOTICE("Property Notice")
}

sealed interface TenantPreviewDocument {
    val stableKey: String
    val category: TenantDocumentCategory
    val title: String
    val subtitle: String
    val suggestedFileName: String
    val metadataPills: List<Pair<String, PillTone>>

    data class VaultDoc(val document: ScannedDocument) : TenantPreviewDocument {
        override val stableKey: String
            get() = "vault:${document.id}:${document.updatedAt?.seconds ?: 0L}"
        override val category: TenantDocumentCategory
            get() = TenantDocumentCategory.VAULT
        override val title: String
            get() = document.title.ifBlank { document.typeEnum().label }
        override val subtitle: String
            get() = buildString {
                append(document.propertyName.ifBlank { "Unit Vault" })
                if (document.unitNumber.isNotBlank()) append(" • Unit ${document.unitNumber}")
                append(" • ${document.pageImageRefs.size.coerceAtLeast(1)} page(s)")
            }
        override val suggestedFileName: String
            get() = PdfDownloadHelper.normalizePdfFileName(
                "rentflow_${document.typeEnum().name.lowercase()}_${document.unitNumber.ifBlank { document.id.take(8) }}.pdf"
            )
        override val metadataPills: List<Pair<String, PillTone>>
            get() = buildList {
                add(document.typeEnum().label to PillTone.INFO)
                document.extractedReference?.takeIf { it.isNotBlank() }?.let {
                    add("Ref $it" to PillTone.NEUTRAL)
                }
                if (document.extractedAmountCents != null && document.extractedAmountCents > 0L) {
                    add(CurrencyUtils.formatKes(document.extractedAmountCents, true) to PillTone.SUCCESS)
                }
            }
    }

    data class PaymentReceipt(
        val receipt: PaymentReceiptPdf? = null,
        val payment: Payment? = null,
        val invoice: Invoice? = null
    ) : TenantPreviewDocument {
        override val stableKey: String
            get() = "receipt:${receipt?.id ?: invoice?.id ?: payment?.id ?: "unknown"}:${receipt?.updatedAt?.seconds ?: 0L}"
        override val category: TenantDocumentCategory
            get() = TenantDocumentCategory.RECEIPT
        override val title: String
            get() = receipt?.let { "Receipt ${it.receiptNumber}" }
                ?: payment?.receiptNumber?.takeIf { it.isNotBlank() }?.let { "Receipt $it" }
                ?: invoice?.let { "Receipt • ${it.invoiceNumber}" }
                ?: "Official Payment Receipt"
        override val subtitle: String
            get() = when {
                receipt != null -> "${receipt.propertyName} • Unit ${receipt.unitNumber} • ${CurrencyUtils.billingPeriodLabel(receipt.billingPeriod)}"
                payment != null -> "${payment.propertyName} • Unit ${payment.unitNumber} • ${CurrencyUtils.formatDate(payment.paymentDateEpochMs)}"
                invoice != null -> "${invoice.propertyName} • Unit ${invoice.unitNumber} • ${CurrencyUtils.billingPeriodLabel(invoice.billingPeriod)}"
                else -> "RentFlow Official Payment Receipt"
            }
        override val suggestedFileName: String
            get() = PdfDownloadHelper.normalizePdfFileName(
                receipt?.fileName?.takeIf { it.isNotBlank() }
                    ?: "rentflow_receipt_${payment?.receiptNumber ?: payment?.referenceCode ?: invoice?.invoiceNumber ?: "payment"}.pdf"
            )
        override val metadataPills: List<Pair<String, PillTone>>
            get() = buildList {
                val amount = receipt?.totalPaidCents ?: payment?.amountCents ?: invoice?.paidCents
                if (amount != null && amount > 0L) {
                    add(CurrencyUtils.formatKes(amount, true) to PillTone.SUCCESS)
                }
                if (receipt != null) {
                    val statusTone = if (receipt.statusEnum() == PaymentReceiptStatus.ISSUED) PillTone.SUCCESS else PillTone.ERROR
                    add(receipt.statusEnum().label to statusTone)
                } else if (payment != null) {
                    add(payment.statusEnum().label to PillTone.SUCCESS)
                }
                val ref = receipt?.paymentReferenceCode ?: payment?.referenceCode
                if (!ref.isNullOrBlank()) {
                    add("Ref $ref" to PillTone.INFO)
                }
            }
    }

    data class TenancyLease(
        val lease: Lease,
        val landlordFallbackSignature: DigitalSignature? = null,
        val tenantFallbackSignature: DigitalSignature? = null
    ) : TenantPreviewDocument {
        override val stableKey: String
            get() = "lease:${lease.id}:${lease.updatedAt?.seconds ?: 0L}"
        override val category: TenantDocumentCategory
            get() = TenantDocumentCategory.LEASE
        override val title: String
            get() = "Tenancy Agreement • Unit ${lease.unitNumber}"
        override val subtitle: String
            get() = "${lease.propertyName} • ${CurrencyUtils.formatDate(lease.startDateEpochMs)} – ${CurrencyUtils.formatDate(lease.endDateEpochMs)}"
        override val suggestedFileName: String
            get() = PdfDownloadHelper.normalizePdfFileName("rentflow_lease_${lease.unitNumber}.pdf")
        override val metadataPills: List<Pair<String, PillTone>>
            get() = buildList {
                add(lease.statusEnum().label to PillTone.SUCCESS)
                add(CurrencyUtils.formatKes(lease.rentCents) + "/mo" to PillTone.INFO)
                val signed = (lease.landlordSignature ?: landlordFallbackSignature) != null &&
                    (lease.tenantSignature ?: tenantFallbackSignature) != null
                add((if (signed) "Mutually Signed" else "Active Terms") to (if (signed) PillTone.SUCCESS else PillTone.NEUTRAL))
            }
    }

    data class PropertyNotice(val announcement: Announcement) : TenantPreviewDocument {
        override val stableKey: String
            get() = "notice:${announcement.id}:${announcement.updatedAt?.seconds ?: 0L}"
        override val category: TenantDocumentCategory
            get() = TenantDocumentCategory.NOTICE
        override val title: String
            get() = announcement.title.ifBlank { "Property Notice" }
        override val subtitle: String
            get() {
                val posted = announcement.createdAt?.toDate()?.time
                    ?: announcement.updatedAt?.toDate()?.time
                val datePart = posted?.let { " • ${CurrencyUtils.formatDate(it)}" }.orEmpty()
                return "${announcement.propertyName.ifBlank { "Property Management" }} • ${announcement.authorName.ifBlank { "Landlord" }}$datePart"
            }
        override val suggestedFileName: String
            get() = PdfDownloadHelper.normalizePdfFileName(
                "rentflow_notice_${announcement.propertyName.ifBlank { "property" }}_${announcement.id.take(8)}.pdf"
            )
        override val metadataPills: List<Pair<String, PillTone>>
            get() = buildList {
                add("Official Notice" to PillTone.INFO)
                if (announcement.propertyName.isNotBlank()) {
                    add(announcement.propertyName to PillTone.NEUTRAL)
                }
            }
    }
}

sealed interface DocumentPreviewRenderState {
    data object Loading : DocumentPreviewRenderState
    data class Ready(
        val pdfFile: File,
        val pageCount: Int,
        val fallbackBitmaps: List<ImageBitmap> = emptyList()
    ) : DocumentPreviewRenderState
    data class Error(val message: String) : DocumentPreviewRenderState
}

internal sealed interface PreviewActionFeedback {
    data class Saved(
        val message: String,
        val uri: Uri?,
        val file: File
    ) : PreviewActionFeedback

    data class Error(val message: String) : PreviewActionFeedback
}

internal suspend fun prepareTenantPreviewDocument(
    context: Context,
    document: TenantPreviewDocument
): Result<DocumentPreviewRenderState.Ready> = withContext(Dispatchers.IO) {
    runCatching {
        when (document) {
            is TenantPreviewDocument.VaultDoc -> {
                val pdfFile = DocumentScanProcessor.exportScannedDocumentPdf(context, document.document)
                    .getOrThrow()
                val fallbackBitmaps = document.document.pageImageRefs.mapNotNull {
                    DocumentScanProcessor.decodeDataUriToImageBitmap(it)
                }
                val rendererPageCount = resolvePdfPageCount(pdfFile)
                DocumentPreviewRenderState.Ready(
                    pdfFile = pdfFile,
                    pageCount = rendererPageCount.coerceAtLeast(document.document.pageImageRefs.size).coerceAtLeast(1),
                    fallbackBitmaps = fallbackBitmaps
                )
            }

            is TenantPreviewDocument.PaymentReceipt -> {
                val pdfFile = when {
                    document.receipt != null && document.receipt.pdfDataUri.isNotBlank() -> {
                        PaymentReceiptPdfGenerator.writeReceiptToCacheFile(context, document.receipt)
                    }
                    document.invoice != null -> {
                        val content = PaymentReceiptPdfGenerator.buildReceiptContent(
                            invoice = document.invoice,
                            payment = document.payment
                        )
                        val bytes = PaymentReceiptPdfGenerator.generatePdfBytes(content)
                        val dir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
                        File(dir, PdfDownloadHelper.normalizePdfFileName(document.suggestedFileName)).apply {
                            writeBytes(bytes)
                        }
                    }
                    document.payment != null -> {
                        ReportExporter.generatePaymentConfirmationPdfFile(context, document.payment)
                    }
                    else -> error("Receipt data is unavailable.")
                }
                val count = resolvePdfPageCount(pdfFile).coerceAtLeast(1)
                DocumentPreviewRenderState.Ready(pdfFile = pdfFile, pageCount = count)
            }

            is TenantPreviewDocument.TenancyLease -> {
                val pdfFile = ReportExporter.generateSignedLeaseAgreementPdfFile(
                    context = context,
                    lease = document.lease,
                    landlordFallbackSignature = document.landlordFallbackSignature,
                    tenantFallbackSignature = document.tenantFallbackSignature
                )
                val count = resolvePdfPageCount(pdfFile).coerceAtLeast(1)
                DocumentPreviewRenderState.Ready(pdfFile = pdfFile, pageCount = count)
            }

            is TenantPreviewDocument.PropertyNotice -> {
                val pdfFile = ReportExporter.generatePropertyNoticePdfFile(
                    context = context,
                    announcement = document.announcement
                )
                val count = resolvePdfPageCount(pdfFile).coerceAtLeast(1)
                DocumentPreviewRenderState.Ready(pdfFile = pdfFile, pageCount = count)
            }
        }
    }
}

private fun resolvePdfPageCount(file: File): Int {
    val controller = PdfDocumentRenderController(file)
    return try {
        controller.pageCount.coerceAtLeast(1)
    } finally {
        controller.close()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantDocumentPreviewSheet(
    document: TenantPreviewDocument,
    onDismiss: () -> Unit,
    canDeleteVaultDoc: Boolean = false,
    onDeleteVaultDocument: ((ScannedDocument) -> Unit)? = null,
    initialFullscreen: Boolean = false,
    customPdfPreparer: (suspend (TenantPreviewDocument) -> Result<DocumentPreviewRenderState.Ready>)? = null,
    customPageRenderer: (suspend (DocumentPreviewRenderState.Ready, Int) -> ImageBitmap?)? = null,
    onDownloadPdfOverride: ((DocumentPreviewRenderState.Ready) -> Unit)? = null,
    onSharePdfOverride: ((DocumentPreviewRenderState.Ready) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isFullscreen by remember(document.stableKey) { mutableStateOf(initialFullscreen) }
    var retryNonce by remember(document.stableKey) { mutableIntStateOf(0) }
    var renderState by remember(document.stableKey) {
        mutableStateOf<DocumentPreviewRenderState>(DocumentPreviewRenderState.Loading)
    }
    var currentPageIndex by remember(document.stableKey) { mutableIntStateOf(0) }
    var targetZoom by remember(document.stableKey) { mutableFloatStateOf(1f) }
    var panOffset by remember(document.stableKey) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var isDownloading by remember(document.stableKey) { mutableStateOf(false) }
    var actionFeedback by remember(document.stableKey) { mutableStateOf<PreviewActionFeedback?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val fallbackErrorMsg = stringResource(R.string.doc_preview_error_fallback)

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri ->
        val ready = renderState as? DocumentPreviewRenderState.Ready ?: return@rememberLauncherForActivityResult
        if (targetUri == null) {
            isDownloading = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            isDownloading = true
            PdfDownloadHelper.copyPdfToUri(context, ready.pdfFile, targetUri).fold(
                onSuccess = {
                    actionFeedback = PreviewActionFeedback.Saved(
                        message = "Saved ${document.suggestedFileName}",
                        uri = targetUri,
                        file = ready.pdfFile
                    )
                },
                onFailure = { err ->
                    actionFeedback = PreviewActionFeedback.Error(
                        err.localizedMessage ?: "Failed to save PDF."
                    )
                }
            )
            isDownloading = false
        }
    }

    LaunchedEffect(document.stableKey, retryNonce) {
        renderState = DocumentPreviewRenderState.Loading
        currentPageIndex = 0
        targetZoom = 1f
        panOffset = Offset.Zero
        actionFeedback = null
        val result = customPdfPreparer?.invoke(document)
            ?: prepareTenantPreviewDocument(context, document)
        renderState = result.fold(
            onSuccess = { ready -> ready },
            onFailure = { err ->
                DocumentPreviewRenderState.Error(err.localizedMessage ?: fallbackErrorMsg)
            }
        )
    }

    val readyDoc = renderState as? DocumentPreviewRenderState.Ready
    val controller = remember(
        readyDoc?.pdfFile?.absolutePath,
        document.stableKey,
        retryNonce,
        customPageRenderer
    ) {
        if (readyDoc != null && customPageRenderer == null) {
            PdfDocumentRenderController(readyDoc.pdfFile)
        } else {
            null
        }
    }
    DisposableEffect(controller) {
        onDispose { controller?.close() }
    }

    val renderedPages = remember(readyDoc?.pdfFile?.absolutePath, document.stableKey, retryNonce) {
        mutableStateMapOf<Int, ImageBitmap>()
    }

    val totalPages = remember(readyDoc, controller) {
        val pdfPages = controller?.pageCount?.takeIf { it > 0 } ?: 0
        maxOf(readyDoc?.pageCount ?: 1, pdfPages).coerceAtLeast(1)
    }
    val safePageIndex = currentPageIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0))

    LaunchedEffect(readyDoc, safePageIndex, totalPages) {
        val activeReady = readyDoc ?: return@LaunchedEffect
        if (renderedPages[safePageIndex] == null) {
            val bmp = customPageRenderer?.invoke(activeReady, safePageIndex)
                ?: controller?.renderPage(safePageIndex, targetWidthPx = 1240)
                ?: activeReady.fallbackBitmaps.getOrNull(safePageIndex)
            if (bmp != null) {
                renderedPages[safePageIndex] = bmp
            }
        }
    }

    fun selectPage(index: Int) {
        val clamped = index.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        if (clamped != currentPageIndex) {
            currentPageIndex = clamped
            targetZoom = 1f
            panOffset = Offset.Zero
        }
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

    fun handleDownloadClick() {
        val ready = readyDoc ?: return
        if (onDownloadPdfOverride != null) {
            onDownloadPdfOverride(ready)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scope.launch {
                isDownloading = true
                PdfDownloadHelper.savePdfToDownloads(
                    context = context,
                    sourceFile = ready.pdfFile,
                    suggestedFileName = document.suggestedFileName
                ).fold(
                    onSuccess = { saved: DownloadedPdfResult ->
                        actionFeedback = PreviewActionFeedback.Saved(
                            message = context.getString(
                                R.string.doc_preview_saved_message,
                                saved.locationLabel,
                                saved.displayName
                            ),
                            uri = saved.uri,
                            file = ready.pdfFile
                        )
                    },
                    onFailure = { err ->
                        actionFeedback = PreviewActionFeedback.Error(
                            err.localizedMessage ?: "Unable to save PDF to Downloads."
                        )
                    }
                )
                isDownloading = false
            }
        } else {
            isDownloading = true
            createDocumentLauncher.launch(PdfDownloadHelper.normalizePdfFileName(document.suggestedFileName))
        }
    }

    fun handleShareClick() {
        val ready = readyDoc ?: return
        if (onSharePdfOverride != null) {
            onSharePdfOverride(ready)
            return
        }
        runCatching {
            PdfPrintAndShareHelper.sharePdfFile(
                context = context,
                file = ready.pdfFile,
                chooserTitle = "Share ${document.category.label}",
                subject = "${document.title} — ${document.subtitle}"
            )
        }.onFailure { err ->
            actionFeedback = PreviewActionFeedback.Error(err.localizedMessage ?: "Unable to share PDF.")
        }
    }

    fun handlePrintClick() {
        val ready = readyDoc ?: return
        PdfPrintAndShareHelper.printPdfFile(
            context = context,
            file = ready.pdfFile,
            jobName = "RentFlow_${document.suggestedFileName}",
            onUnavailableOrError = { err ->
                actionFeedback = PreviewActionFeedback.Error(err)
            }
        )
    }

    val animatedZoom by animateFloatAsState(
        targetValue = targetZoom,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "doc_preview_zoom"
    )

    val canDelete = document is TenantPreviewDocument.VaultDoc &&
        canDeleteVaultDoc &&
        onDeleteVaultDocument != null

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            BackHandler { isFullscreen = false }
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("doc_preview_fullscreen_dialog")
            ) {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        text = document.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = document.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = { isFullscreen = false },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("doc_preview_fullscreen_toggle")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FullscreenExit,
                                        contentDescription = stringResource(R.string.doc_preview_fullscreen_exit_cd)
                                    )
                                }
                            },
                            actions = {
                                if (readyDoc != null) {
                                    IconButton(
                                        onClick = ::handlePrintClick,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("doc_preview_print_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Print,
                                            contentDescription = stringResource(R.string.pdf_viewer_print)
                                        )
                                    }
                                }
                                if (canDelete) {
                                    IconButton(
                                        onClick = { confirmDelete = true },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("doc_preview_delete_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = "Delete document",
                                            tint = StatusError
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("doc_preview_close_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.doc_preview_close_cd)
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    bottomBar = {
                        DocumentPreviewActionFooter(
                            isReady = readyDoc != null,
                            isDownloading = isDownloading,
                            onDownloadClick = ::handleDownloadClick,
                            onShareClick = ::handleShareClick,
                            modifier = Modifier.navigationBarsPadding()
                        )
                    }
                ) { innerPadding ->
                    DocumentPreviewBody(
                        document = document,
                        renderState = renderState,
                        renderedBitmap = renderedPages[safePageIndex],
                        currentPageIndex = safePageIndex,
                        totalPages = totalPages,
                        targetZoom = targetZoom,
                        animatedZoom = animatedZoom,
                        panOffset = panOffset,
                        actionFeedback = actionFeedback,
                        onDismissFeedback = { actionFeedback = null },
                        onOpenSaved = { uri, file ->
                            PdfDownloadHelper.openPdfDocument(context, uri, file)
                        },
                        onRetry = { retryNonce++ },
                        onSelectPage = ::selectPage,
                        onZoomChange = { nextZoom, nextPan ->
                            targetZoom = nextZoom
                            panOffset = nextPan
                        },
                        onViewportSized = { viewportSize = it },
                        clampPan = ::clampPan,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    } else {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            modifier = Modifier.testTag("doc_preview_bottom_sheet")
        ) {
            BackHandler { onDismiss() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .navigationBarsPadding()
            ) {
                DocumentPreviewSheetHeader(
                    document = document,
                    isReady = readyDoc != null,
                    canDelete = canDelete,
                    onPrint = ::handlePrintClick,
                    onDelete = { confirmDelete = true },
                    onToggleFullscreen = { isFullscreen = true },
                    onClose = onDismiss
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                DocumentPreviewBody(
                    document = document,
                    renderState = renderState,
                    renderedBitmap = renderedPages[safePageIndex],
                    currentPageIndex = safePageIndex,
                    totalPages = totalPages,
                    targetZoom = targetZoom,
                    animatedZoom = animatedZoom,
                    panOffset = panOffset,
                    actionFeedback = actionFeedback,
                    onDismissFeedback = { actionFeedback = null },
                    onOpenSaved = { uri, file ->
                        PdfDownloadHelper.openPdfDocument(context, uri, file)
                    },
                    onRetry = { retryNonce++ },
                    onSelectPage = ::selectPage,
                    onZoomChange = { nextZoom, nextPan ->
                        targetZoom = nextZoom
                        panOffset = nextPan
                    },
                    onViewportSized = { viewportSize = it },
                    clampPan = ::clampPan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                DocumentPreviewActionFooter(
                    isReady = readyDoc != null,
                    isDownloading = isDownloading,
                    onDownloadClick = ::handleDownloadClick,
                    onShareClick = ::handleShareClick
                )
            }
        }
    }

    if (confirmDelete && document is TenantPreviewDocument.VaultDoc && onDeleteVaultDocument != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Scanned Document?") },
            text = {
                Text(
                    "This removes \"${document.title}\" and all ${document.document.pageImageRefs.size} scanned page(s) from your unit vault."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDeleteVaultDocument(document.document)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DocumentPreviewSheetHeader(
    document: TenantPreviewDocument,
    isReady: Boolean,
    canDelete: Boolean,
    onPrint: () -> Unit,
    onDelete: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onClose: () -> Unit
) {
    val categoryTone = when (document.category) {
        TenantDocumentCategory.RECEIPT -> PillTone.SUCCESS
        TenantDocumentCategory.LEASE -> PillTone.INFO
        TenantDocumentCategory.NOTICE -> PillTone.WARNING
        TenantDocumentCategory.VAULT -> PillTone.INFO
    }
    val categoryIcon: ImageVector = when (document.category) {
        TenantDocumentCategory.VAULT -> Icons.Filled.DocumentScanner
        TenantDocumentCategory.RECEIPT -> Icons.Filled.Receipt
        TenantDocumentCategory.LEASE -> Icons.Filled.Description
        TenantDocumentCategory.NOTICE -> Icons.Filled.Campaign
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                StatusPill(
                    label = document.category.label,
                    tone = categoryTone,
                    modifier = Modifier
                        .semantics(mergeDescendants = true) {}
                        .testTag("doc_preview_category_pill")
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isReady) {
                    IconButton(
                        onClick = onPrint,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("doc_preview_print_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Print,
                            contentDescription = stringResource(R.string.pdf_viewer_print)
                        )
                    }
                }
                if (canDelete) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("doc_preview_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete document",
                            tint = StatusError
                        )
                    }
                }
                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("doc_preview_fullscreen_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fullscreen,
                        contentDescription = stringResource(R.string.doc_preview_fullscreen_enter_cd)
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("doc_preview_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.doc_preview_close_cd)
                    )
                }
            }
        }
        Text(
            text = document.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("doc_preview_title")
        )
        Text(
            text = document.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("doc_preview_subtitle")
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocumentPreviewBody(
    document: TenantPreviewDocument,
    renderState: DocumentPreviewRenderState,
    renderedBitmap: ImageBitmap?,
    currentPageIndex: Int,
    totalPages: Int,
    targetZoom: Float,
    animatedZoom: Float,
    panOffset: Offset,
    actionFeedback: PreviewActionFeedback?,
    onDismissFeedback: () -> Unit,
    onOpenSaved: (Uri?, File) -> Unit,
    onRetry: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onZoomChange: (Float, Offset) -> Unit,
    onViewportSized: (IntSize) -> Unit,
    clampPan: (Offset, Float) -> Offset,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    Column(modifier = modifier) {
        if (document.metadataPills.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                document.metadataPills.forEach { (label, tone) ->
                    StatusPill(label = label, tone = tone)
                }
            }
        }

        AnimatedVisibility(visible = actionFeedback != null) {
            actionFeedback?.let { fb ->
                when (fb) {
                    is PreviewActionFeedback.Saved -> {
                        val bg = if (isDark) StatusSuccessBgDark else StatusSuccessBg
                        val fg = if (isDark) StatusSuccessLight else StatusSuccess
                        Surface(
                            color = bg,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, fg.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .testTag("doc_preview_feedback_banner")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = fg,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = fb.message,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = fg,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { onOpenSaved(fb.uri, fb.file) },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("doc_preview_open_saved_button")
                                ) {
                                    Text(stringResource(R.string.doc_preview_open_saved), color = fg)
                                }
                            }
                        }
                    }

                    is PreviewActionFeedback.Error -> {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            ErrorBannerCard(
                                message = fb.message,
                                modifier = Modifier.testTag("doc_preview_feedback_banner")
                            )
                            TextButton(
                                onClick = onDismissFeedback,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }
        }

        when (renderState) {
            is DocumentPreviewRenderState.Loading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(24.dp)
                        .testTag("doc_preview_loading_box")
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
                                text = stringResource(R.string.doc_preview_loading),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            is DocumentPreviewRenderState.Error -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(24.dp)
                        .testTag("doc_preview_error_box")
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
                                text = renderState.message,
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
                                    .testTag("doc_preview_retry_button")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.pdf_viewer_retry))
                            }
                        }
                    }
                }
            }

            is DocumentPreviewRenderState.Ready -> {
                // Recessed Document Stage
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                        .clipToBounds()
                        .onSizeChanged { onViewportSized(it) }
                        .pointerInput(document.stableKey, currentPageIndex) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (targetZoom > 1.05f) {
                                        onZoomChange(1f, Offset.Zero)
                                    } else {
                                        onZoomChange(2.2f, Offset.Zero)
                                    }
                                }
                            )
                        }
                        .pointerInput(document.stableKey, currentPageIndex) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val pressedCount = event.changes.count { it.pressed }
                                    if (pressedCount >= 2) {
                                        val zoomFactor = event.calculateZoom()
                                        val panDelta = event.calculatePan()
                                        val nextZoom = (targetZoom * zoomFactor).coerceIn(1f, 4f)
                                        onZoomChange(nextZoom, clampPan(panOffset + panDelta, nextZoom))
                                        event.changes.forEach { change ->
                                            if (change.positionChanged()) change.consume()
                                        }
                                    } else if (pressedCount == 1 && targetZoom > 1.02f) {
                                        val panDelta = event.calculatePan()
                                        if (panDelta != Offset.Zero) {
                                            onZoomChange(targetZoom, clampPan(panOffset + panDelta, targetZoom))
                                            event.changes.forEach { change ->
                                                if (change.positionChanged()) change.consume()
                                            }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Card(
                        shape = MaterialTheme.shapes.small,
                        colors = CardDefaults.cardColors(containerColor = PaperWhite),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = 760.dp)
                            .aspectRatio(595f / 842f, matchHeightConstraintsFirst = true)
                            .graphicsLayer {
                                scaleX = animatedZoom
                                scaleY = animatedZoom
                                translationX = if (animatedZoom > 1.01f) panOffset.x else 0f
                                translationY = if (animatedZoom > 1.01f) panOffset.y else 0f
                            }
                            .testTag("doc_preview_page_card_$currentPageIndex")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            if (renderedBitmap != null) {
                                Image(
                                    bitmap = renderedBitmap,
                                    contentDescription = "${document.title} page ${currentPageIndex + 1} of $totalPages",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = "Rendering page ${currentPageIndex + 1}…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .semantics(mergeDescendants = true) {}
                            .testTag("doc_preview_page_zoom_badge")
                    ) {
                        Text(
                            text = "Page ${currentPageIndex + 1} of $totalPages • ${(animatedZoom * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Multi-page chips + Zoom & Paging Control Strip
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        if (totalPages > 1) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(vertical = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items((0 until totalPages).toList()) { idx ->
                                    val selected = idx == currentPageIndex
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        modifier = Modifier
                                            .heightIn(min = 36.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .clickable { onSelectPage(idx) }
                                            .testTag("doc_preview_page_chip_$idx")
                                    ) {
                                        Text(
                                            text = "P${idx + 1}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onSecondary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Paging stepper
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconButton(
                                    onClick = { onSelectPage(currentPageIndex - 1) },
                                    enabled = currentPageIndex > 0,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("doc_preview_prev_page_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = stringResource(R.string.doc_preview_prev_page_cd)
                                    )
                                }
                                Text(
                                    text = "${currentPageIndex + 1} / $totalPages",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.testTag("doc_preview_page_indicator")
                                )
                                IconButton(
                                    onClick = { onSelectPage(currentPageIndex + 1) },
                                    enabled = currentPageIndex < totalPages - 1,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("doc_preview_next_page_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = stringResource(R.string.doc_preview_next_page_cd)
                                    )
                                }
                            }

                            // Zoom stepper
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val next = (targetZoom - 0.5f).coerceIn(1f, 4f)
                                        onZoomChange(next, clampPan(panOffset, next))
                                    },
                                    enabled = targetZoom > 1.05f,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("doc_preview_zoom_out_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Remove,
                                        contentDescription = stringResource(R.string.pdf_viewer_zoom_out_cd)
                                    )
                                }
                                TextButton(
                                    onClick = { onZoomChange(1f, Offset.Zero) },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("doc_preview_zoom_reset_button")
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
                                        onZoomChange(next, clampPan(panOffset, next))
                                    },
                                    enabled = targetZoom < 3.95f,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("doc_preview_zoom_in_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = stringResource(R.string.pdf_viewer_zoom_in_cd)
                                    )
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
private fun DocumentPreviewActionFooter(
    isReady: Boolean,
    isDownloading: Boolean,
    onDownloadClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            OutlinedButton(
                onClick = onDownloadClick,
                enabled = isReady && !isDownloading,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("doc_preview_download_button")
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.doc_preview_downloading), maxLines = 1)
                } else {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.doc_preview_download_pdf), maxLines = 1)
                }
            }

            Button(
                onClick = onShareClick,
                enabled = isReady && !isDownloading,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("doc_preview_share_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.doc_preview_share), maxLines = 1)
            }
        }
    }
}
