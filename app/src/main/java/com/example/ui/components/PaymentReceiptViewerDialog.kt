package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.PaymentReceiptPdfGenerator
import com.example.domain.model.PaymentReceiptPdf
import com.example.domain.model.PaymentReceiptStatus
import com.example.ui.theme.PaperWhite
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TabularMoneyStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentReceiptViewerDialog(
    receipt: PaymentReceiptPdf,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    BackHandler(onBack = onDismiss)

    var actionNotice by remember { mutableStateOf<String?>(null) }
    var renderedPages by remember(receipt.id, receipt.updatedAt) {
        mutableStateOf<List<ImageBitmap>>(emptyList())
    }
    var renderChecked by remember(receipt.id, receipt.updatedAt) {
        mutableStateOf(false)
    }

    val cacheFile = remember(receipt.id, receipt.pdfDataUri) {
        runCatching { PaymentReceiptPdfGenerator.writeReceiptToCacheFile(context, receipt) }.getOrNull()
    }
    val renderController = remember(cacheFile?.absolutePath) {
        cacheFile?.let { PdfDocumentRenderController(it) }
    }

    DisposableEffect(renderController) {
        onDispose { renderController?.close() }
    }

    LaunchedEffect(renderController) {
        if (renderController == null || renderController.pageCount <= 0) {
            renderedPages = emptyList()
            renderChecked = true
            return@LaunchedEffect
        }
        val bitmaps = buildList {
            for (idx in 0 until renderController.pageCount) {
                renderController.renderPage(idx, targetWidthPx = 1190)?.let { add(it) }
            }
        }
        renderedPages = bitmaps
        renderChecked = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.payment_receipt_viewer_title),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${receipt.receiptNumber} • ${receipt.propertyName} (${receipt.unitNumber})",
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
                            modifier = Modifier.testTag("receipt_viewer_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.pdf_viewer_close_cd)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                PaymentReceiptPdfGenerator.printReceiptPdf(context, receipt) { err ->
                                    actionNotice = err
                                }
                            },
                            modifier = Modifier.testTag("receipt_viewer_print_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Print,
                                contentDescription = stringResource(R.string.pdf_viewer_print)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                runCatching { PaymentReceiptPdfGenerator.openExternalPdf(context, receipt) }
                                    .onFailure { actionNotice = it.localizedMessage ?: "Unable to open PDF." }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("receipt_viewer_open_external_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.payment_receipt_open_pdf))
                        }
                        Button(
                            onClick = {
                                runCatching { PaymentReceiptPdfGenerator.shareReceiptPdf(context, receipt) }
                                    .onFailure { actionNotice = it.localizedMessage ?: "Unable to share PDF." }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("receipt_viewer_share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.pdf_viewer_share))
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(innerPadding)
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                        .testTag("payment_receipt_viewer_content")
                ) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = if (receipt.statusEnum() == PaymentReceiptStatus.ISSUED) StatusSuccess else StatusError,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = receipt.fileName,
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Stored in Firestore • ${((receipt.fileSizeBytes + 1023L) / 1024L).coerceAtLeast(1L)} KB PDF",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                StatusPill(
                                    label = receipt.statusEnum().label,
                                    tone = if (receipt.statusEnum() == PaymentReceiptStatus.ISSUED) PillTone.SUCCESS else PillTone.ERROR
                                )
                            }
                        }
                    }

                    actionNotice?.let { msg ->
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { actionNotice = null }) {
                                        Text("Dismiss")
                                    }
                                }
                            }
                        }
                    }

                    if (!renderChecked) {
                        item {
                            LoadingStateBox("Rendering stored PDF receipt...")
                        }
                    } else if (renderedPages.isNotEmpty()) {
                        items(renderedPages.size, key = { it }) { index ->
                            val bmp = renderedPages[index]
                            Surface(
                                color = PaperWhite,
                                shape = RoundedCornerShape(8.dp),
                                shadowElevation = 4.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("payment_receipt_rendered_page_$index")
                            ) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = "Payment Receipt PDF Page ${index + 1}",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(595f / 842f)
                                )
                            }
                        }
                    } else {
                        item {
                            ReceiptStructuredFallbackCard(receipt = receipt)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptStructuredFallbackCard(receipt: PaymentReceiptPdf) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = PaperWhite),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("payment_receipt_fallback_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Receipt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "RentFlow Official Receipt",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                StatusPill(
                    label = if (receipt.statusEnum() == PaymentReceiptStatus.ISSUED) "PAID IN FULL" else "VOIDED",
                    tone = if (receipt.statusEnum() == PaymentReceiptStatus.ISSUED) PillTone.SUCCESS else PillTone.ERROR
                )
            }

            Text(
                text = CurrencyUtils.formatKes(receipt.totalPaidCents, includeDecimals = true),
                style = TabularMoneyStyle.copy(fontSize = 24.sp),
                color = StatusSuccess
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ReceiptMetaLine("Receipt Number", receipt.receiptNumber)
            ReceiptMetaLine("Invoice Number", receipt.invoiceNumber)
            ReceiptMetaLine("Tenant", receipt.tenantName)
            ReceiptMetaLine("Property & Unit", "${receipt.propertyName} • Unit ${receipt.unitNumber}")
            ReceiptMetaLine("Billing Period", CurrencyUtils.billingPeriodLabel(receipt.billingPeriod))
            receipt.paymentMethod?.let { ReceiptMetaLine("Payment Method", it) }
            receipt.paymentReferenceCode?.let { ReceiptMetaLine("Reference Code", it) }
            ReceiptMetaLine("Paid Date", CurrencyUtils.formatDateTime(receipt.paidAtEpochMs))
            ReceiptMetaLine("Generated On", CurrencyUtils.formatDateTime(receipt.generatedAtEpochMs))
            ReceiptMetaLine("Confirmed By", receipt.generatedByName)
            if (!receipt.voidReason.isNullOrBlank()) {
                ReceiptMetaLine("Void Reason", receipt.voidReason)
            }
        }
    }
}

@Composable
private fun ReceiptMetaLine(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
