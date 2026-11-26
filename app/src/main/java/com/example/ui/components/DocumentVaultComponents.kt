package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.PdfPrintAndShareHelper
import com.example.domain.model.DocumentType
import com.example.domain.model.ScannedDocument
import com.example.ui.media.DocumentScanProcessor
import com.example.ui.theme.MidnightInk
import com.example.ui.theme.MutedCobalt
import com.example.ui.theme.StatusError
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentVaultDialog(
    title: String,
    subtitle: String,
    documents: List<ScannedDocument>,
    canUpload: Boolean,
    canDeleteDoc: (ScannedDocument) -> Boolean,
    canToggleTenantVisibility: Boolean,
    isBusy: Boolean,
    onScanNewDocument: () -> Unit,
    onToggleTenantVisibility: (ScannedDocument, Boolean) -> Unit,
    onDeleteDocument: (ScannedDocument) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<DocumentType?>(null) }
    var activeDocId by remember { mutableStateOf<String?>(null) }

    val filteredDocs = remember(documents, searchQuery, selectedType) {
        val q = searchQuery.trim().lowercase()
        documents.filter { doc ->
            val typeOk = selectedType == null || doc.typeEnum() == selectedType
            val queryOk = q.isBlank() ||
                doc.title.lowercase().contains(q) ||
                doc.unitNumber.lowercase().contains(q) ||
                doc.propertyName.lowercase().contains(q) ||
                doc.tenantName.orEmpty().lowercase().contains(q) ||
                doc.extractedReference.orEmpty().lowercase().contains(q) ||
                doc.notes.orEmpty().lowercase().contains(q)
            typeOk && queryOk
        }
    }

    val activeDoc = remember(activeDocId, documents) {
        activeDocId?.let { id -> documents.firstOrNull { it.id == id } }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BackHandler { onDismiss() }

        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize().testTag("document_vault_dialog")
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = "$subtitle • ${documents.size} document(s)",
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
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("doc_vault_close_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close document vault")
                            }
                        },
                        actions = {
                            if (canUpload) {
                                Button(
                                    onClick = onScanNewDocument,
                                    enabled = !isBusy,
                                    colors = ButtonDefaults.buttonColors(containerColor = MutedCobalt),
                                    modifier = Modifier.padding(end = 8.dp).heightIn(min = 48.dp).testTag("doc_vault_scan_button")
                                ) {
                                    Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Scan")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            placeholder = { Text("Search title, ref code, tenant, unit...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("doc_vault_search_input")
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedType == null,
                                    onClick = { selectedType = null },
                                    label = { Text("All (${documents.size})") }
                                )
                            }
                            items(DocumentType.entries) { dt ->
                                val count = documents.count { it.typeEnum() == dt }
                                if (count > 0 || selectedType == dt) {
                                    FilterChip(
                                        selected = selectedType == dt,
                                        onClick = { selectedType = if (selectedType == dt) null else dt },
                                        label = { Text("${dt.label} ($count)") }
                                    )
                                }
                            }
                        }
                    }

                    if (filteredDocs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            EmptyStateCard(
                                title = stringResource(R.string.doc_vault_empty_title),
                                message = stringResource(R.string.doc_vault_empty_message),
                                ctaLabel = if (canUpload) "Scan Document" else null,
                                onCtaClick = if (canUpload) onScanNewDocument else null,
                                showIllustration = false
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredDocs, key = { it.id }) { doc ->
                                ScannedDocumentCard(
                                    document = doc,
                                    onClick = { activeDocId = doc.id }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (activeDoc != null) {
        ScannedDocumentViewerDialog(
            document = activeDoc,
            canDelete = canDeleteDoc(activeDoc),
            canToggleTenantVisibility = canToggleTenantVisibility,
            isBusy = isBusy,
            onToggleTenantVisibility = { visible ->
                onToggleTenantVisibility(activeDoc, visible)
            },
            onDelete = {
                onDeleteDocument(activeDoc)
                activeDocId = null
            },
            onDismiss = { activeDocId = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScannedDocumentCard(
    document: ScannedDocument,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumb = remember(document.id, document.pageImageRefs.firstOrNull()) {
        document.pageImageRefs.firstOrNull()?.let { DocumentScanProcessor.decodeDataUriToImageBitmap(it) }
    }
    val typeTone = when (document.typeEnum()) {
        DocumentType.LEASE_AGREEMENT -> PillTone.INFO
        DocumentType.PAYMENT_RECEIPT -> PillTone.SUCCESS
        DocumentType.EXPENSE_RECEIPT -> PillTone.WARNING
        DocumentType.TENANT_ID_KYC, DocumentType.OTHER -> PillTone.NEUTRAL
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .testTag("scanned_document_card_${document.id}")
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 64.dp, height = 80.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MidnightInk.copy(alpha = 0.08f))
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb,
                        contentDescription = document.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Filled.DocumentScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = document.title.ifBlank { document.typeEnum().label },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    StatusPill("${document.pageImageRefs.size}p", PillTone.NEUTRAL)
                }

                Text(
                    text = buildString {
                        append("${document.propertyName} • Unit ${document.unitNumber}")
                        if (!document.tenantName.isNullOrBlank()) append(" • ${document.tenantName}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusPill(document.typeEnum().label, typeTone)
                    if (!document.extractedReference.isNullOrBlank()) {
                        StatusPill("Ref ${document.extractedReference}", PillTone.INFO)
                    }
                    if (document.extractedAmountCents != null && document.extractedAmountCents > 0L) {
                        StatusPill(CurrencyUtils.formatKes(document.extractedAmountCents), PillTone.SUCCESS)
                    }
                    if (document.aiExtracted) {
                        StatusPill("AI", PillTone.INFO)
                    }
                }

                val uploadedEpoch = document.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (document.tenantVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${if (document.tenantVisible) "Tenant visible" else "Landlord only"} • ${CurrencyUtils.formatDate(uploadedEpoch)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScannedDocumentViewerDialog(
    document: ScannedDocument,
    canDelete: Boolean,
    canToggleTenantVisibility: Boolean,
    isBusy: Boolean,
    onToggleTenantVisibility: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pageIdx by remember(document.id) { mutableIntStateOf(0) }
    var zoom by remember(document.id, pageIdx) { mutableFloatStateOf(1f) }
    var pan by remember(document.id, pageIdx) { mutableStateOf(Offset.Zero) }
    var confirmDelete by remember { mutableStateOf(false) }
    var exportNotice by remember { mutableStateOf<String?>(null) }

    val safeIdx = pageIdx.coerceIn(0, (document.pageImageRefs.size - 1).coerceAtLeast(0))
    val pageBitmap = remember(document.id, safeIdx, document.pageImageRefs.getOrNull(safeIdx)) {
        document.pageImageRefs.getOrNull(safeIdx)?.let { DocumentScanProcessor.decodeDataUriToImageBitmap(it) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BackHandler { onDismiss() }
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize().testTag("scanned_document_viewer_dialog")
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(document.title.ifBlank { document.typeEnum().label }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = "${document.propertyName} • Unit ${document.unitNumber} • Page ${safeIdx + 1}/${document.pageImageRefs.size.coerceAtLeast(1)}",
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
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close document viewer")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        DocumentScanProcessor.exportScannedDocumentPdf(context, document).fold(
                                            onSuccess = { file ->
                                                PdfPrintAndShareHelper.sharePdfFile(
                                                    context = context,
                                                    file = file,
                                                    chooserTitle = "Share Document PDF",
                                                    subject = document.title
                                                )
                                            },
                                            onFailure = { exportNotice = it.localizedMessage ?: "PDF export failed." }
                                        )
                                    }
                                },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("doc_viewer_share_pdf_button")
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = "Share PDF")
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        DocumentScanProcessor.exportScannedDocumentPdf(context, document).fold(
                                            onSuccess = { file ->
                                                PdfPrintAndShareHelper.printPdfFile(
                                                    context = context,
                                                    file = file,
                                                    jobName = "RentFlow_${document.unitNumber}_${document.typeEnum().name}",
                                                    onUnavailableOrError = { exportNotice = it }
                                                )
                                            },
                                            onFailure = { exportNotice = it.localizedMessage ?: "PDF print failed." }
                                        )
                                    }
                                },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("doc_viewer_print_pdf_button")
                            ) {
                                Icon(Icons.Filled.Print, contentDescription = "Print PDF")
                            }
                            if (canDelete) {
                                IconButton(
                                    onClick = { confirmDelete = true },
                                    enabled = !isBusy,
                                    modifier = Modifier.minimumInteractiveComponentSize().testTag("doc_viewer_delete_button")
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete document", tint = StatusError)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
            ) { innerPadding ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (!exportNotice.isNullOrBlank()) ErrorBannerCard(exportNotice!!)

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp, max = 430.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(MidnightInk)
                            .clipToBounds()
                            .pointerInput(document.id, safeIdx) {
                                detectTransformGestures { _, panChange, zoomChange, _ ->
                                    val newZoom = (zoom * zoomChange).coerceIn(1f, 4f)
                                    zoom = newZoom
                                    pan = if (newZoom <= 1.02f) Offset.Zero else pan + panChange
                                }
                            }
                    ) {
                        if (pageBitmap != null) {
                            Image(
                                bitmap = pageBitmap,
                                contentDescription = "${document.title} page ${safeIdx + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = zoom,
                                        scaleY = zoom,
                                        translationX = pan.x,
                                        translationY = pan.y
                                    )
                            )
                        } else {
                            Text("Unable to decode page image", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }

                    if (document.pageImageRefs.size > 1) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(document.pageImageRefs) { idx, ref ->
                                val thumb = remember(document.id, idx, ref) {
                                    DocumentScanProcessor.decodeDataUriToImageBitmap(ref)
                                }
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    border = BorderStroke(
                                        width = if (idx == safeIdx) 2.5.dp else 1.dp,
                                        color = if (idx == safeIdx) MutedCobalt else MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier
                                        .size(width = 56.dp, height = 72.dp)
                                        .clickable { pageIdx = idx }
                                ) {
                                    if (thumb != null) {
                                        Image(bitmap = thumb, contentDescription = "Page ${idx + 1}", contentScale = ContentScale.Crop)
                                    }
                                }
                            }
                        }
                    }

                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                StatusPill(document.typeEnum().label, PillTone.INFO)
                                if (!document.extractedReference.isNullOrBlank()) {
                                    StatusPill("Ref ${document.extractedReference}", PillTone.INFO)
                                }
                                if (document.extractedAmountCents != null && document.extractedAmountCents > 0L) {
                                    StatusPill(CurrencyUtils.formatKes(document.extractedAmountCents, true), PillTone.SUCCESS)
                                }
                                if (!document.extractedDateText.isNullOrBlank()) {
                                    StatusPill(document.extractedDateText, PillTone.NEUTRAL)
                                }
                                if (document.aiExtracted) {
                                    StatusPill("AI Extracted", PillTone.INFO)
                                }
                            }
                            if (!document.extractedSummary.isNullOrBlank()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                    Text(document.extractedSummary, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (!document.notes.isNullOrBlank()) {
                                Text("Notes: ${document.notes}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                text = "Uploaded by ${document.uploadedByName.ifBlank { "User" }} • ${DocumentScanProcessor.estimatePayloadKb(document.pageImageRefs)} KB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (canToggleTenantVisibility) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Visible to Linked Tenant", style = MaterialTheme.typography.bodyMedium)
                                    Switch(
                                        checked = document.tenantVisible,
                                        onCheckedChange = onToggleTenantVisibility,
                                        enabled = !isBusy
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Scanned Document?") },
            text = { Text("This removes \"${document.title}\" and all ${document.pageImageRefs.size} scanned page(s) from the unit vault.") },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}
