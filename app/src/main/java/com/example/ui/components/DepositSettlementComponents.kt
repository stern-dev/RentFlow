package com.example.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.FinancialEngine
import com.example.domain.finance.ReportExporter
import com.example.domain.model.DeductionCategory
import com.example.domain.model.DepositDeductionItem
import com.example.domain.model.DepositSettlement
import com.example.domain.model.DepositSettlementStatus
import com.example.domain.model.Invoice
import com.example.domain.model.Lease
import com.example.domain.model.RefundMethod
import com.example.ui.media.MaintenancePhotoProcessor
import com.example.ui.screens.MaintenancePhotoStrip
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorBg
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import com.example.ui.theme.StatusWarningBgDark
import com.example.ui.theme.StatusWarningLight
import com.example.ui.theme.TabularMoneyStyle
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun DepositSettlementSummaryCard(
    settlement: DepositSettlement?,
    lease: Lease,
    isTenantView: Boolean,
    onOpenSettlement: () -> Unit,
    onExportPdf: ((DepositSettlement) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val status = settlement?.statusEnum()
    val tone = when (status) {
        DepositSettlementStatus.SETTLED, DepositSettlementStatus.APPROVED -> PillTone.SUCCESS
        DepositSettlementStatus.ISSUED -> PillTone.WARNING
        DepositSettlementStatus.DISPUTED -> PillTone.ERROR
        DepositSettlementStatus.DRAFT -> PillTone.INFO
        null -> PillTone.NEUTRAL
    }
    val actionHighlight = (isTenantView && status == DepositSettlementStatus.ISSUED) ||
        (!isTenantView && status == DepositSettlementStatus.DISPUTED)

    val isDark = LocalIsDarkTheme.current
    val warnBg = if (isDark) StatusWarningBgDark else StatusWarningBg
    val warnFg = if (isDark) StatusWarningLight else StatusWarning
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (actionHighlight) {
                warnBg.copy(alpha = 0.75f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (actionHighlight) warnFg.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag(if (isTenantView) "tenant_deposit_settlement_card" else "landlord_deposit_settlement_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountBalanceWallet,
                    contentDescription = "Security deposit settlement",
                    tint = if (actionHighlight) warnFg else MaterialTheme.colorScheme.secondary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Security Deposit & Settlement", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Held: ${CurrencyUtils.formatKes(settlement?.depositHeldCents ?: lease.depositCents, true)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(status?.label ?: "Deposit Held", tone)
            }

            if (settlement != null) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("Deductions & Arrears", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            CurrencyUtils.formatKes(settlement.deductionsTotalCents + settlement.arrearsIncludedCents, true),
                            style = TabularMoneyStyle
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        val isOwed = settlement.balanceOwedCents > 0L
                        Text(
                            if (isOwed) "Balance Owed" else "Net Deposit Refund",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            CurrencyUtils.formatKes(
                                if (isOwed) settlement.balanceOwedCents else settlement.netRefundCents,
                                true
                            ),
                            style = TabularMoneyStyle,
                            color = if (isOwed) StatusError else StatusSuccess
                        )
                    }
                }
                if (!settlement.refundReferenceCode.isNullOrBlank()) {
                    Text(
                        "Payout Ref (${settlement.refundMethodEnum().label}): ${settlement.refundReferenceCode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            } else if (isTenantView) {
                Text(
                    "Your security deposit of ${CurrencyUtils.formatKes(lease.depositCents, true)} is held under your lease terms. Itemized move-out settlement appears here when your tenancy ends.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (settlement != null || !isTenantView) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onOpenSettlement,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (actionHighlight) StatusWarning else MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag(if (isTenantView) "tenant_open_settlement_button" else "landlord_open_settlement_button")
                    ) {
                        Text(
                            when {
                                settlement == null -> "Settle Deposit"
                                isTenantView && status == DepositSettlementStatus.ISSUED -> "Review Statement"
                                isTenantView -> "View Settlement"
                                status == DepositSettlementStatus.SETTLED -> "View Settled Record"
                                status == DepositSettlementStatus.DISPUTED -> "Resolve Dispute & Update"
                                status == DepositSettlementStatus.APPROVED -> "Disburse Refund"
                                else -> "Manage Settlement"
                            }
                        )
                    }
                    if (settlement != null && onExportPdf != null) {
                        OutlinedButton(
                            onClick = { onExportPdf(settlement) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = "Export settlement PDF", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LandlordDepositSettlementDialog(
    lease: Lease,
    existingSettlement: DepositSettlement?,
    leaseInvoices: List<Invoice>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        depositHeldCents: Long,
        arrearsIncludedCents: Long,
        creditAppliedCents: Long,
        deductions: List<DepositDeductionItem>,
        waterMeterFinal: String?,
        electricityMeterFinal: String?,
        keysReturnedCount: Long,
        imageRefs: List<String>,
        landlordNotes: String?,
        targetStatus: DepositSettlementStatus,
        refundMethod: RefundMethod,
        refundReferenceCode: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isLocked = existingSettlement?.statusEnum() == DepositSettlementStatus.SETTLED
    val defaultArrearsCents = remember(lease.id, leaseInvoices) {
        FinancialEngine.computeLeaseUnpaidArrearsCents(lease.id, leaseInvoices)
    }

    var depositKesText by remember(existingSettlement?.id, lease.id) {
        mutableStateOf(
            CurrencyUtils.centsToWholeKesInput(existingSettlement?.depositHeldCents ?: lease.depositCents)
        )
    }
    var arrearsKesText by remember(existingSettlement?.id, lease.id) {
        mutableStateOf(
            CurrencyUtils.centsToWholeKesInput(existingSettlement?.arrearsIncludedCents ?: defaultArrearsCents)
        )
    }
    var creditKesText by remember(existingSettlement?.id, lease.id) {
        mutableStateOf(
            CurrencyUtils.centsToWholeKesInput(existingSettlement?.creditAppliedCents ?: lease.creditBalanceCents)
        )
    }
    val deductions = remember(existingSettlement?.id, lease.id) {
        mutableStateListOf<DepositDeductionItem>().apply {
            existingSettlement?.deductions?.let { addAll(it) }
        }
    }
    var waterMeterFinal by remember(existingSettlement?.id) { mutableStateOf(existingSettlement?.waterMeterFinal.orEmpty()) }
    var powerMeterFinal by remember(existingSettlement?.id) { mutableStateOf(existingSettlement?.electricityMeterFinal.orEmpty()) }
    var keysReturned by remember(existingSettlement?.id) { mutableStateOf((existingSettlement?.keysReturnedCount ?: 2L).toString()) }
    var landlordNotes by remember(existingSettlement?.id) { mutableStateOf(existingSettlement?.landlordNotes.orEmpty()) }
    val imageRefs = remember(existingSettlement?.id) {
        mutableStateListOf<String>().apply { existingSettlement?.imageRefs?.let { addAll(it) } }
    }

    var showAddDeduction by remember { mutableStateOf(false) }
    var dedCategory by remember { mutableStateOf(DeductionCategory.DAMAGE_REPAIR) }
    var dedDesc by remember { mutableStateOf("") }
    var dedAmountText by remember { mutableStateOf("") }

    var refundMethod by remember(existingSettlement?.id) {
        mutableStateOf(existingSettlement?.refundMethodEnum() ?: RefundMethod.MPESA)
    }
    var refundRefCode by remember(existingSettlement?.id) {
        mutableStateOf(existingSettlement?.refundReferenceCode.orEmpty())
    }
    var showSettleSection by remember { mutableStateOf(false) }
    var isProcessingPhotos by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val depositHeldCents = (depositKesText.toLongOrNull() ?: 0L) * 100L
    val arrearsCents = (arrearsKesText.toLongOrNull() ?: 0L) * 100L
    val creditCents = (creditKesText.toLongOrNull() ?: 0L) * 100L
    val reconciliation = remember(depositHeldCents, creditCents, arrearsCents, deductions.toList()) {
        FinancialEngine.computeDepositReconciliation(
            depositHeldCents = depositHeldCents,
            creditAppliedCents = creditCents,
            arrearsIncludedCents = arrearsCents,
            deductions = deductions.toList()
        )
    }

    val handlePickedUris: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) {
            isProcessingPhotos = true
            scope.launch {
                val remaining = (MaintenancePhotoProcessor.MAX_PHOTOS - imageRefs.size).coerceAtLeast(0)
                val encoded = MaintenancePhotoProcessor.compressUrisToDataUris(context, uris, remaining)
                val merged = (imageRefs + encoded).take(MaintenancePhotoProcessor.MAX_PHOTOS)
                MaintenancePhotoProcessor.validateImageRefsPayload(merged).fold(
                    onSuccess = {
                        imageRefs.clear()
                        imageRefs.addAll(it)
                        errorText = null
                    },
                    onFailure = { errorText = it.localizedMessage ?: "Photos exceed size limits." }
                )
                isProcessingPhotos = false
            }
        }
    }

    val multiPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MaintenancePhotoProcessor.MAX_PHOTOS)
    ) { handlePickedUris(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Deposit Settlement • Unit ${lease.unitNumber}")
                Text(
                    "${lease.propertyName} • ${lease.tenantName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isLocked) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Text(
                                "Finalized & locked (${existingSettlement?.refundMethodEnum()?.label} ${existingSettlement?.refundReferenceCode.orEmpty()}).",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (existingSettlement?.statusEnum() == DepositSettlementStatus.DISPUTED &&
                    !existingSettlement.tenantResponseNote.isNullOrBlank()
                ) {
                    Surface(
                        color = StatusErrorBg,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, StatusError.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = StatusError)
                            Column {
                                Text("Tenant Requested Review", style = MaterialTheme.typography.labelLarge, color = StatusError)
                                Text(existingSettlement.tenantResponseNote, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else if (existingSettlement?.statusEnum() == DepositSettlementStatus.APPROVED) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Text("${lease.tenantName} approved this statement. Record refund payout to settle.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (!errorText.isNullOrBlank()) {
                    ErrorBannerCard(errorText!!)
                }

                // Live KES Reconciliation Ledger Card
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Live KES Reconciliation", style = MaterialTheme.typography.titleSmall)
                        LedgerSummaryRow("Deposit + Account Credit", reconciliation.totalAvailableCents)
                        LedgerSummaryRow("Unpaid Rent Arrears", reconciliation.arrearsIncludedCents)
                        LedgerSummaryRow("Itemized Deductions (${deductions.size})", reconciliation.deductionsTotalCents)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        if (reconciliation.balanceOwedCents > 0L) {
                            LedgerSummaryRow("Remaining Balance Owed by Tenant", reconciliation.balanceOwedCents, highlightError = true)
                        } else {
                            LedgerSummaryRow("Net Refund Due to Tenant", reconciliation.netRefundCents, highlightSuccess = true)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = depositKesText,
                        onValueChange = { depositKesText = it.filter { ch -> ch.isDigit() }.take(9) },
                        label = { Text("Deposit Held (KES)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !isLocked && !isBusy,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = creditKesText,
                        onValueChange = { creditKesText = it.filter { ch -> ch.isDigit() }.take(9) },
                        label = { Text("Credit Applied") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !isLocked && !isBusy,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = arrearsKesText,
                    onValueChange = { arrearsKesText = it.filter { ch -> ch.isDigit() }.take(9) },
                    label = { Text("Unpaid Rent Arrears Included (KES)") },
                    supportingText = {
                        if (defaultArrearsCents > 0L) {
                            Text("Detected from open invoices: ${CurrencyUtils.formatKes(defaultArrearsCents)}")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !isLocked && !isBusy,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Itemized Deductions (${deductions.size})", style = MaterialTheme.typography.titleSmall)
                    if (!isLocked && deductions.size < 30) {
                        TextButton(
                            onClick = { showAddDeduction = !showAddDeduction },
                            modifier = Modifier.testTag("toggle_add_deduction_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (showAddDeduction) "Hide" else "Add Deduction")
                        }
                    }
                }

                AnimatedVisibility(visible = showAddDeduction && !isLocked) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                DeductionCategory.entries.forEach { cat ->
                                    FilterChip(
                                        selected = dedCategory == cat,
                                        onClick = { dedCategory = cat },
                                        label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = dedDesc,
                                onValueChange = { dedDesc = it.take(200) },
                                label = { Text("Description (e.g. Repainting living room wall)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("deduction_description_input")
                            )
                            OutlinedTextField(
                                value = dedAmountText,
                                onValueChange = { dedAmountText = it.filter { ch -> ch.isDigit() }.take(8) },
                                label = { Text("Amount (KES)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("deduction_amount_input")
                            )
                            Button(
                                onClick = {
                                    val amtKes = dedAmountText.toLongOrNull() ?: 0L
                                    if (dedDesc.trim().length < 2) {
                                        errorText = "Enter a deduction description."
                                    } else if (amtKes <= 0L) {
                                        errorText = "Enter a positive deduction amount in KES."
                                    } else {
                                        deductions.add(
                                            DepositDeductionItem(
                                                id = "ded_${UUID.randomUUID().toString().take(8)}",
                                                category = dedCategory.name,
                                                description = dedDesc.trim(),
                                                amountCents = amtKes * 100L
                                            )
                                        )
                                        dedDesc = ""
                                        dedAmountText = ""
                                        showAddDeduction = false
                                        errorText = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("confirm_add_deduction_button")
                            ) { Text("Add Deduction Row") }
                        }
                    }
                }

                deductions.forEachIndexed { idx, ded ->
                    Card(
                        shape = MaterialTheme.shapes.small,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(10.dp).fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ded.categoryEnum().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                Text(ded.description, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(CurrencyUtils.formatKes(ded.amountCents, true), style = TabularMoneyStyle)
                            if (!isLocked) {
                                IconButton(onClick = { deductions.removeAt(idx) }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove deduction", tint = StatusError)
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = waterMeterFinal,
                        onValueChange = { waterMeterFinal = it.take(40) },
                        label = { Text("Final Water Meter") },
                        singleLine = true,
                        enabled = !isLocked && !isBusy,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = powerMeterFinal,
                        onValueChange = { powerMeterFinal = it.take(40) },
                        label = { Text("Final Power Meter") },
                        singleLine = true,
                        enabled = !isLocked && !isBusy,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = keysReturned,
                    onValueChange = { v -> keysReturned = v.filter { it.isDigit() }.take(2) },
                    label = { Text("Keys Returned") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !isLocked && !isBusy,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = landlordNotes,
                    onValueChange = { landlordNotes = it.take(800) },
                    label = { Text("Move-Out Assessment Notes") },
                    maxLines = 3,
                    enabled = !isLocked && !isBusy,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Move-Out Evidence Photos (${imageRefs.size}/${MaintenancePhotoProcessor.MAX_PHOTOS})", style = MaterialTheme.typography.titleSmall)
                if (isProcessingPhotos) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (imageRefs.isNotEmpty()) {
                    MaintenancePhotoStrip(
                        imageRefs = imageRefs,
                        removable = !isLocked && !isBusy,
                        onRemovePhoto = { idx -> if (idx in imageRefs.indices) imageRefs.removeAt(idx) }
                    )
                }
                if (!isLocked) {
                    OutlinedButton(
                        onClick = { multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !isBusy && !isProcessingPhotos && imageRefs.size < MaintenancePhotoProcessor.MAX_PHOTOS,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Attach Move-Out Photos")
                    }
                }

                if (!isLocked && existingSettlement != null) {
                    HorizontalDivider()
                    TextButton(
                        onClick = { showSettleSection = !showSettleSection },
                        modifier = Modifier.testTag("toggle_settle_payout_button")
                    ) {
                        Text(if (showSettleSection) "Hide Refund Disbursement" else "Record Refund Payout & Finalize Lock")
                    }
                    AnimatedVisibility(visible = showSettleSection) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (reconciliation.netRefundCents > 0L) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(RefundMethod.MPESA, RefundMethod.BANK_TRANSFER, RefundMethod.CASH).forEach { m ->
                                        FilterChip(
                                            selected = refundMethod == m,
                                            onClick = { refundMethod = m },
                                            label = { Text(m.label) }
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = refundRefCode,
                                    onValueChange = { refundRefCode = it.uppercase().take(60) },
                                    label = { Text("Payout Reference (e.g. M-Pesa Code)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("refund_reference_input")
                                )
                            } else {
                                Text(
                                    "Net refund due is KES 0. Finalizing will lock this settlement permanently.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    if (reconciliation.netRefundCents > 0L &&
                                        (refundMethod == RefundMethod.NONE || refundRefCode.trim().length < 3)
                                    ) {
                                        errorText = "Enter a valid payout method and reference code (min 3 characters)."
                                    } else {
                                        val effectiveMethod = if (reconciliation.netRefundCents == 0L) RefundMethod.NONE else refundMethod
                                        onSave(
                                            depositHeldCents,
                                            arrearsCents,
                                            creditCents,
                                            deductions.toList(),
                                            waterMeterFinal,
                                            powerMeterFinal,
                                            keysReturned.toLongOrNull() ?: 0L,
                                            imageRefs.toList(),
                                            landlordNotes,
                                            DepositSettlementStatus.SETTLED,
                                            effectiveMethod,
                                            refundRefCode.trim().takeIf { it.isNotBlank() }
                                        )
                                    }
                                },
                                enabled = !isBusy && !isProcessingPhotos,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("finalize_settlement_button")
                            ) { Text("Mark Refund Settled & Lock") }
                        }
                    }
                }

                if (existingSettlement != null) {
                    OutlinedButton(
                        onClick = { exportDepositSettlementPdf(context, existingSettlement) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export Deposit Statement (PDF)")
                    }
                }
            }
        },
        confirmButton = {
            if (!isLocked) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onSave(
                                depositHeldCents,
                                arrearsCents,
                                creditCents,
                                deductions.toList(),
                                waterMeterFinal,
                                powerMeterFinal,
                                keysReturned.toLongOrNull() ?: 0L,
                                imageRefs.toList(),
                                landlordNotes,
                                DepositSettlementStatus.DRAFT,
                                refundMethod,
                                refundRefCode.trim().takeIf { it.isNotBlank() }
                            )
                        },
                        enabled = !isBusy && !isProcessingPhotos,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("settlement_save_draft_button")
                    ) { Text("Draft") }
                    Button(
                        onClick = {
                            onSave(
                                depositHeldCents,
                                arrearsCents,
                                creditCents,
                                deductions.toList(),
                                waterMeterFinal,
                                powerMeterFinal,
                                keysReturned.toLongOrNull() ?: 0L,
                                imageRefs.toList(),
                                landlordNotes,
                                DepositSettlementStatus.ISSUED,
                                refundMethod,
                                refundRefCode.trim().takeIf { it.isNotBlank() }
                            )
                        },
                        enabled = !isBusy && !isProcessingPhotos,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("settlement_issue_button")
                    ) { Text("Issue Statement") }
                }
            } else {
                Button(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
            }
        },
        dismissButton = {
            if (!isLocked) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun TenantDepositSettlementReviewDialog(
    settlement: DepositSettlement,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onRespond: (approve: Boolean, responseNote: String?) -> Unit
) {
    val context = LocalContext.current
    val canRespond = settlement.statusEnum() == DepositSettlementStatus.ISSUED
    var note by remember(settlement.id) { mutableStateOf(settlement.tenantResponseNote.orEmpty()) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Deposit Settlement • Unit ${settlement.unitNumber}")
                StatusPill(
                    label = settlement.statusEnum().label,
                    tone = when (settlement.statusEnum()) {
                        DepositSettlementStatus.SETTLED, DepositSettlementStatus.APPROVED -> PillTone.SUCCESS
                        DepositSettlementStatus.ISSUED -> PillTone.WARNING
                        DepositSettlementStatus.DISPUTED -> PillTone.ERROR
                        DepositSettlementStatus.DRAFT -> PillTone.INFO
                    }
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!err.isNullOrBlank()) ErrorBannerCard(err!!)

                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LedgerSummaryRow("Security Deposit Held", settlement.depositHeldCents)
                        if (settlement.creditAppliedCents > 0L) {
                            LedgerSummaryRow("Account Credit Applied", settlement.creditAppliedCents)
                        }
                        if (settlement.arrearsIncludedCents > 0L) {
                            LedgerSummaryRow("Unpaid Rent Arrears", settlement.arrearsIncludedCents)
                        }
                        LedgerSummaryRow("Itemized Deductions", settlement.deductionsTotalCents)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        if (settlement.balanceOwedCents > 0L) {
                            LedgerSummaryRow("Balance Owed", settlement.balanceOwedCents, highlightError = true)
                        } else {
                            LedgerSummaryRow("Net Deposit Refund", settlement.netRefundCents, highlightSuccess = true)
                        }
                    }
                }

                if (settlement.deductions.isNotEmpty()) {
                    Text("Deduction Breakdown", style = MaterialTheme.typography.titleSmall)
                    settlement.deductions.forEach { ded ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ded.description, style = MaterialTheme.typography.bodySmall)
                                Text(ded.categoryEnum().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(CurrencyUtils.formatKes(ded.amountCents, true), style = TabularMoneyStyle)
                        }
                    }
                }

                if (!settlement.refundReferenceCode.isNullOrBlank()) {
                    Text(
                        "Payout Disbursed (${settlement.refundMethodEnum().label}): ${settlement.refundReferenceCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusSuccess
                    )
                }
                if (!settlement.landlordNotes.isNullOrBlank()) {
                    Text("Management remarks: ${settlement.landlordNotes}", style = MaterialTheme.typography.bodySmall)
                }
                if (settlement.imageRefs.isNotEmpty()) {
                    Text("Move-Out Inspection Photos", style = MaterialTheme.typography.titleSmall)
                    MaintenancePhotoStrip(imageRefs = settlement.imageRefs, removable = false)
                }

                if (canRespond) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = {
                            note = it.take(800)
                            err = null
                        },
                        label = { Text("Comments / Dispute Reason (required if requesting review)") },
                        maxLines = 3,
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("tenant_settlement_note_input")
                    )
                } else if (!settlement.tenantResponseNote.isNullOrBlank()) {
                    Text("Your note: ${settlement.tenantResponseNote}", style = MaterialTheme.typography.bodySmall)
                }

                OutlinedButton(
                    onClick = { exportDepositSettlementPdf(context, settlement) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Download Settlement Statement (PDF)")
                }
            }
        },
        confirmButton = {
            if (canRespond) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (note.trim().length < 3) {
                                err = "Explain which deduction you want reviewed."
                            } else {
                                onRespond(false, note.trim())
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("tenant_dispute_settlement_button")
                    ) { Text("Request Review", color = StatusError) }
                    Button(
                        onClick = { onRespond(true, note.trim().takeIf { it.isNotBlank() }) },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("tenant_approve_settlement_button")
                    ) { Text("Approve") }
                }
            } else {
                Button(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
            }
        },
        dismissButton = {
            if (canRespond) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Later") }
            }
        }
    )
}

@Composable
private fun LedgerSummaryRow(
    label: String,
    amountCents: Long,
    highlightSuccess: Boolean = false,
    highlightError: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = if (highlightSuccess || highlightError) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall
        )
        Text(
            text = CurrencyUtils.formatKes(amountCents, true),
            style = TabularMoneyStyle,
            color = when {
                highlightError -> StatusError
                highlightSuccess -> StatusSuccess
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

fun exportDepositSettlementPdf(context: Context, settlement: DepositSettlement) {
    val lines = buildList {
        add("[H]DEPOSIT SETTLEMENT STATEMENT")
        add("Property & Unit: ${settlement.propertyName} • Unit ${settlement.unitNumber}")
        add("Tenant: ${settlement.tenantName}")
        add("Move-Out Date: ${CurrencyUtils.formatDate(settlement.moveOutDateEpochMs)}")
        add("Status: ${settlement.statusEnum().label}")
        add("Keys Returned: ${settlement.keysReturnedCount}")
        add("Final Meters: Water ${settlement.waterMeterFinal ?: "N/A"} | Power ${settlement.electricityMeterFinal ?: "N/A"}")
        add("---")
        add("[H]FINANCIAL RECONCILIATION (KES)")
        add("Security Deposit Held: ${CurrencyUtils.formatKes(settlement.depositHeldCents, true)}")
        add("Account Credit Applied: ${CurrencyUtils.formatKes(settlement.creditAppliedCents, true)}")
        add("Unpaid Rent Arrears Included: ${CurrencyUtils.formatKes(settlement.arrearsIncludedCents, true)}")
        add("Total Itemized Deductions: ${CurrencyUtils.formatKes(settlement.deductionsTotalCents, true)}")
        if (settlement.balanceOwedCents > 0L) {
            add("Remaining Balance Owed by Tenant: ${CurrencyUtils.formatKes(settlement.balanceOwedCents, true)}")
        } else {
            add("Net Refund Due to Tenant: ${CurrencyUtils.formatKes(settlement.netRefundCents, true)}")
        }
        if (settlement.deductions.isNotEmpty()) {
            add("---")
            add("[H]ITEMIZED DEDUCTIONS")
            settlement.deductions.forEach { ded ->
                add("${ded.categoryEnum().label} — ${ded.description}: ${CurrencyUtils.formatKes(ded.amountCents, true)}")
            }
        }
        if (!settlement.refundReferenceCode.isNullOrBlank() || settlement.settledAtEpochMs != null) {
            add("---")
            add("[H]PAYOUT CONFIRMATION")
            add("Method: ${settlement.refundMethodEnum().label}")
            settlement.refundReferenceCode?.takeIf { it.isNotBlank() }?.let { add("Reference Code: $it") }
            settlement.settledAtEpochMs?.let { add("Settled Date: ${CurrencyUtils.formatDate(it)}") }
        }
        if (!settlement.landlordNotes.isNullOrBlank() || !settlement.tenantResponseNote.isNullOrBlank()) {
            add("---")
            add("[H]REMARKS")
            settlement.landlordNotes?.takeIf { it.isNotBlank() }?.let { add("Management: $it") }
            settlement.tenantResponseNote?.takeIf { it.isNotBlank() }?.let { add("Tenant: $it") }
        }
    }
    ReportExporter.exportPdfDocument(
        context = context,
        fileName = "deposit_settlement_${settlement.unitNumber}.pdf",
        title = "RentFlow Deposit Settlement Statement",
        subtitle = "${settlement.propertyName} • Unit ${settlement.unitNumber}",
        lines = lines
    )
}
