package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.MpesaReceiptMatchEvaluation
import com.example.domain.finance.MpesaSmsParser
import com.example.domain.finance.ReceiptMatchOutcome
import com.example.domain.model.Lease
import com.example.domain.model.LeaseStatus
import com.example.domain.model.MpesaReceipt
import com.example.domain.model.MpesaReceiptStatus
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.PillTone
import com.example.ui.components.StatusPill
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorBg
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import com.example.ui.theme.TabularMoneyStyle

@Composable
fun MpesaAutoCaptureSettingsCard(
    autoCaptureEnabled: Boolean,
    listenerEnabled: Boolean,
    onToggleAutoCapture: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("mpesa_autocapture_settings_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text("M-Pesa Notification Auto-Capture", style = MaterialTheme.typography.titleSmall)
                }
                StatusPill(
                    label = if (listenerEnabled) "Listener Active" else "Setup Needed",
                    tone = if (listenerEnabled) PillTone.SUCCESS else PillTone.WARNING
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Stage Safaricom Confirmations Automatically", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Reads incoming M-Pesa notification banners without SMS inbox permissions. You can also Share or Paste SMS anytime.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoCaptureEnabled,
                    onCheckedChange = onToggleAutoCapture,
                    modifier = Modifier.testTag("mpesa_autocapture_switch")
                )
            }

            OutlinedButton(
                onClick = onOpenNotificationSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("mpesa_listener_settings_button")
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (listenerEnabled) "Manage Android Notification Access" else "Enable Android Notification Access")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MpesaReconcileScreen(
    evaluations: List<MpesaReceiptMatchEvaluation>,
    leases: List<Lease>,
    autoCaptureEnabled: Boolean,
    listenerEnabled: Boolean,
    isBusy: Boolean,
    onToggleAutoCapture: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onStageSmsBatch: (String, () -> Unit) -> Unit,
    onApproveMatched: (MpesaReceiptMatchEvaluation) -> Unit,
    onReconcileToLease: (MpesaReceipt, Lease) -> Unit,
    onMarkAlreadyVerified: (MpesaReceiptMatchEvaluation) -> Unit,
    onDismissReceipt: (MpesaReceipt) -> Unit,
    onReopenReceipt: (MpesaReceipt) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val readyQueue = remember(evaluations) {
        evaluations.filter {
            it.receipt.statusEnum() == MpesaReceiptStatus.STAGED &&
                (it.outcome == ReceiptMatchOutcome.EXACT_MATCH ||
                    it.outcome == ReceiptMatchOutcome.DISCREPANCY_REVIEW ||
                    it.outcome == ReceiptMatchOutcome.ALREADY_VERIFIED)
        }
    }
    val unmatchedQueue = remember(evaluations) {
        evaluations.filter {
            it.receipt.statusEnum() == MpesaReceiptStatus.STAGED &&
                (it.outcome == ReceiptMatchOutcome.UNMATCHED_LEASE_SUGGESTION ||
                    it.outcome == ReceiptMatchOutcome.UNMATCHED ||
                    it.outcome == ReceiptMatchOutcome.OUTGOING_TENANT_ONLY)
        }
    }
    val processedQueue = remember(evaluations) {
        evaluations.filter { it.receipt.statusEnum() != MpesaReceiptStatus.STAGED }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var receiptToLinkLease by remember { mutableStateOf<MpesaReceipt?>(null) }
    var discrepancyToConfirm by remember { mutableStateOf<MpesaReceiptMatchEvaluation?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("mpesa_reconcile_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Finance")
                }
                Column {
                    Text("M-Pesa SMS Auto-Verify", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Stage & verify Safaricom rent receipts",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = { showPasteDialog = true },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("mpesa_open_paste_dialog_button")
            ) {
                Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Paste SMS")
            }
        }

        SecondaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Ready (${readyQueue.size})") },
                modifier = Modifier.testTag("reconcile_tab_ready")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Unmatched (${unmatchedQueue.size})") },
                modifier = Modifier.testTag("reconcile_tab_unmatched")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("History (${processedQueue.size})") },
                modifier = Modifier.testTag("reconcile_tab_processed")
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().testTag("mpesa_reconcile_list")
        ) {
            item {
                MpesaAutoCaptureSettingsCard(
                    autoCaptureEnabled = autoCaptureEnabled,
                    listenerEnabled = listenerEnabled,
                    onToggleAutoCapture = onToggleAutoCapture,
                    onOpenNotificationSettings = onOpenNotificationSettings
                )
            }

            when (selectedTab) {
                0 -> {
                    if (readyQueue.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No Matched Receipts Staged",
                                message = "Incoming Safaricom M-Pesa confirmations that match pending tenant submissions appear here for 1-tap verification.",
                                ctaLabel = "Paste M-Pesa SMS",
                                onCtaClick = { showPasteDialog = true },
                                showIllustration = false
                            )
                        }
                    } else {
                        items(readyQueue, key = { it.receipt.id }) { eval ->
                            ReadyMatchReceiptCard(
                                evaluation = eval,
                                isBusy = isBusy,
                                onApprove = {
                                    if (eval.outcome == ReceiptMatchOutcome.ALREADY_VERIFIED) {
                                        onMarkAlreadyVerified(eval)
                                    } else if (
                                        eval.outcome == ReceiptMatchOutcome.DISCREPANCY_REVIEW &&
                                        eval.matchedPayment != null &&
                                        eval.matchedPayment.amountCents != eval.receipt.amountCents
                                    ) {
                                        discrepancyToConfirm = eval
                                    } else {
                                        onApproveMatched(eval)
                                    }
                                },
                                onMatchLeaseInstead = { receiptToLinkLease = eval.receipt },
                                onDismiss = { onDismissReceipt(eval.receipt) }
                            )
                        }
                    }
                }

                1 -> {
                    if (unmatchedQueue.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "Unmatched Queue Clear",
                                message = "Receipts that do not match a pending tenant submission are held safely here until you link them to an active lease or dismiss them.",
                                showIllustration = false
                            )
                        }
                    } else {
                        items(unmatchedQueue, key = { it.receipt.id }) { eval ->
                            UnmatchedReceiptCard(
                                evaluation = eval,
                                isBusy = isBusy,
                                hasActiveLeases = leases.any { it.statusEnum() == LeaseStatus.ACTIVE },
                                onQuickLinkSuggested = { lease -> onReconcileToLease(eval.receipt, lease) },
                                onPickLease = { receiptToLinkLease = eval.receipt },
                                onDismiss = { onDismissReceipt(eval.receipt) }
                            )
                        }
                    }
                }

                2 -> {
                    if (processedQueue.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No Processed Receipts Yet",
                                message = "Reconciled and dismissed M-Pesa confirmations appear here for your audit trail.",
                                showIllustration = false
                            )
                        }
                    } else {
                        items(processedQueue, key = { it.receipt.id }) { eval ->
                            ProcessedReceiptCard(
                                evaluation = eval,
                                isBusy = isBusy,
                                onReopen = { onReopenReceipt(eval.receipt) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPasteDialog) {
        BatchMpesaSmsPasteDialog(
            isBusy = isBusy,
            onDismiss = { showPasteDialog = false },
            onStage = { rawBatch ->
                onStageSmsBatch(rawBatch) {
                    showPasteDialog = false
                }
            }
        )
    }

    receiptToLinkLease?.let { receipt ->
        val activeLeases = remember(leases) { leases.filter { it.statusEnum() == LeaseStatus.ACTIVE } }
        LinkReceiptToLeaseDialog(
            receipt = receipt,
            activeLeases = activeLeases,
            isBusy = isBusy,
            onDismiss = { receiptToLinkLease = null },
            onConfirmLease = { lease ->
                onReconcileToLease(receipt, lease)
                receiptToLinkLease = null
            }
        )
    }

    discrepancyToConfirm?.let { eval ->
        val pending = eval.matchedPayment!!
        AlertDialog(
            onDismissRequest = { discrepancyToConfirm = null },
            title = { Text("Confirm SMS Amount Override") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Pending submission from ${pending.tenantName} (${pending.unitNumber}) shows ${CurrencyUtils.formatKes(pending.amountCents, true)} (${pending.referenceCode}), whereas the Safaricom SMS shows ${CurrencyUtils.formatKes(eval.receipt.amountCents, true)} (${eval.receipt.transactionCode}).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Approving replaces the mismatched pending submission with a verified payment for the authoritative Safaricom SMS amount (${CurrencyUtils.formatKes(eval.receipt.amountCents, true)}).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onApproveMatched(eval)
                        discrepancyToConfirm = null
                    },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusWarning),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Verify SMS Amount")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { discrepancyToConfirm = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ReadyMatchReceiptCard(
    evaluation: MpesaReceiptMatchEvaluation,
    isBusy: Boolean,
    onApprove: () -> Unit,
    onMatchLeaseInstead: () -> Unit,
    onDismiss: () -> Unit
) {
    val receipt = evaluation.receipt
    val tone = when (evaluation.outcome) {
        ReceiptMatchOutcome.EXACT_MATCH -> PillTone.SUCCESS
        ReceiptMatchOutcome.DISCREPANCY_REVIEW -> PillTone.WARNING
        ReceiptMatchOutcome.ALREADY_VERIFIED -> PillTone.INFO
        else -> PillTone.NEUTRAL
    }
    val outcomeLabel = when (evaluation.outcome) {
        ReceiptMatchOutcome.EXACT_MATCH -> "Exact Match"
        ReceiptMatchOutcome.DISCREPANCY_REVIEW -> "Review Diff"
        ReceiptMatchOutcome.ALREADY_VERIFIED -> "Already Verified"
        else -> "Staged"
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ready_receipt_card_${receipt.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "${receipt.transactionCode} • ${CurrencyUtils.formatKes(receipt.amountCents, true)}",
                        style = TabularMoneyStyle.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    val party = listOfNotNull(
                        receipt.counterpartyName,
                        receipt.counterpartyPhone,
                        receipt.accountReference?.let { "Acct: $it" }
                    ).joinToString(" • ")
                    if (party.isNotBlank()) {
                        Text(party, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "${receipt.captureSourceEnum().label} • ${CurrencyUtils.formatDateTime(receipt.transactionTimestampMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(outcomeLabel, tone)
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(evaluation.matchSummary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    evaluation.matchedPayment?.let { pending ->
                        Text(
                            "Pending: ${pending.referenceCode} • ${CurrencyUtils.formatKes(pending.amountCents, true)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!evaluation.discrepancyDetails.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = StatusWarning, modifier = Modifier.size(16.dp))
                            Text(evaluation.discrepancyDetails, style = MaterialTheme.typography.labelSmall, color = StatusWarning)
                        }
                    }
                }
            }

            Text(
                text = receipt.rawSnippet,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onApprove,
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (evaluation.outcome == ReceiptMatchOutcome.DISCREPANCY_REVIEW) {
                            StatusWarning
                        } else {
                            StatusSuccess
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("approve_receipt_btn_${receipt.id}")
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when (evaluation.outcome) {
                            ReceiptMatchOutcome.ALREADY_VERIFIED -> "Mark Reconciled"
                            ReceiptMatchOutcome.DISCREPANCY_REVIEW -> "Approve SMS Match"
                            else -> "Approve & Verify"
                        }
                    )
                }
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isBusy,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("dismiss_receipt_btn_${receipt.id}")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss receipt", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Dismiss")
                }
            }
            if (evaluation.outcome == ReceiptMatchOutcome.DISCREPANCY_REVIEW) {
                TextButton(
                    onClick = onMatchLeaseInstead,
                    enabled = !isBusy,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Link to a different active lease instead")
                }
            }
        }
    }
}

@Composable
private fun UnmatchedReceiptCard(
    evaluation: MpesaReceiptMatchEvaluation,
    isBusy: Boolean,
    hasActiveLeases: Boolean,
    onQuickLinkSuggested: (Lease) -> Unit,
    onPickLease: () -> Unit,
    onDismiss: () -> Unit
) {
    val receipt = evaluation.receipt
    val suggested = evaluation.suggestedLease
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("unmatched_receipt_card_${receipt.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "${receipt.transactionCode} • ${CurrencyUtils.formatKes(receipt.amountCents, true)}",
                        style = TabularMoneyStyle.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    val party = listOfNotNull(
                        receipt.counterpartyName,
                        receipt.counterpartyPhone,
                        receipt.accountReference?.let { "Acct: $it" }
                    ).joinToString(" • ")
                    if (party.isNotBlank()) {
                        Text(party, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "${receipt.captureSourceEnum().label} • ${CurrencyUtils.formatDateTime(receipt.transactionTimestampMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(
                    label = when (evaluation.outcome) {
                        ReceiptMatchOutcome.UNMATCHED_LEASE_SUGGESTION -> "Lease Hint"
                        ReceiptMatchOutcome.OUTGOING_TENANT_ONLY -> "Outgoing"
                        else -> "Unmatched"
                    },
                    tone = if (evaluation.outcome == ReceiptMatchOutcome.UNMATCHED_LEASE_SUGGESTION) {
                        PillTone.INFO
                    } else {
                        PillTone.WARNING
                    }
                )
            }

            Text(evaluation.matchSummary, style = MaterialTheme.typography.bodySmall)
            if (!evaluation.discrepancyDetails.isNullOrBlank()) {
                Surface(color = StatusWarningBg, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = evaluation.discrepancyDetails,
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusWarning,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            Text(
                text = receipt.rawSnippet,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (suggested != null) {
                    Button(
                        onClick = { onQuickLinkSuggested(suggested) },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("quick_link_lease_btn_${receipt.id}")
                    ) {
                        Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Verify on ${suggested.unitNumber}")
                    }
                } else {
                    Button(
                        onClick = onPickLease,
                        enabled = !isBusy && hasActiveLeases,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("match_lease_btn_${receipt.id}")
                    ) {
                        Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Match to Lease")
                    }
                }
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isBusy,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("dismiss_receipt_btn_${receipt.id}")
                ) {
                    Text("Dismiss")
                }
            }

            if (suggested != null && hasActiveLeases) {
                TextButton(
                    onClick = onPickLease,
                    enabled = !isBusy,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("match_lease_btn_${receipt.id}")
                ) {
                    Text("Choose another active lease")
                }
            }
        }
    }
}

@Composable
private fun ProcessedReceiptCard(
    evaluation: MpesaReceiptMatchEvaluation,
    isBusy: Boolean,
    onReopen: () -> Unit
) {
    val receipt = evaluation.receipt
    val isReconciled = receipt.statusEnum() == MpesaReceiptStatus.RECONCILED
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${receipt.transactionCode} • ${CurrencyUtils.formatKes(receipt.amountCents, true)}",
                        style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize)
                    )
                    Text(
                        "${receipt.captureSourceEnum().label} • ${CurrencyUtils.formatDateTime(receipt.transactionTimestampMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(
                    label = receipt.statusEnum().label,
                    tone = if (isReconciled) PillTone.SUCCESS else PillTone.NEUTRAL
                )
            }
            Text(
                receipt.rawSnippet,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            if (!isReconciled) {
                OutlinedButton(
                    onClick = onReopen,
                    enabled = !isBusy,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("reopen_receipt_btn_${receipt.id}")
                ) {
                    Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Move Back to Review")
                }
            }
        }
    }
}

@Composable
fun BatchMpesaSmsPasteDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onStage: (String) -> Unit
) {
    var rawText by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val preview = remember(rawText) { MpesaSmsParser.parseAll(rawText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste Safaricom M-Pesa SMS") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Paste one or multiple M-Pesa confirmation messages below to stage them for 1-tap rent verification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!err.isNullOrBlank()) ErrorBannerCard(err!!)
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it; err = null },
                    label = { Text("M-Pesa Confirmation Message(s)") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mpesa_paste_sms_input")
                )
                if (preview.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Detected ${preview.size} confirmation(s):",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            preview.take(4).forEach { parsed ->
                                Text(
                                    "• ${parsed.transactionCode} • ${CurrencyUtils.formatKes(parsed.amountCents, true)}${parsed.counterpartyName?.let { " ($it)" } ?: ""}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (preview.isEmpty()) {
                        err = "No valid Safaricom M-Pesa confirmation (10-char code + KES/Ksh amount) detected."
                    } else {
                        onStage(rawText)
                    }
                },
                enabled = !isBusy,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("mpesa_stage_sms_button")
            ) {
                Text(if (preview.size > 1) "Stage ${preview.size} Receipts" else "Stage Receipt")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun LinkReceiptToLeaseDialog(
    receipt: MpesaReceipt,
    activeLeases: List<Lease>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirmLease: (Lease) -> Unit
) {
    var selectedLease by remember(receipt.id) { mutableStateOf(activeLeases.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Match ${receipt.transactionCode} to Lease") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Record & verify ${CurrencyUtils.formatKes(receipt.amountCents, true)} against an active lease ledger:",
                    style = MaterialTheme.typography.bodySmall
                )
                if (activeLeases.isEmpty()) {
                    Surface(color = StatusErrorBg, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "No active leases available to match this receipt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusError,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else {
                    activeLeases.forEach { lease ->
                        val isSelected = selectedLease?.id == lease.id
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedLease = lease }
                                .testTag("lease_pick_option_${lease.id}")
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("${lease.tenantName} • ${lease.unitNumber}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${lease.propertyName} • Rent ${CurrencyUtils.formatKes(lease.rentCents, true)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedLease?.let(onConfirmLease) },
                enabled = !isBusy && selectedLease != null,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_link_lease_button")
            ) {
                Text("Verify & Allocate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel")
            }
        }
    )
}
