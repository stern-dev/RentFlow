package com.example.ui.components

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
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.LateFeeSweepCandidate
import com.example.domain.finance.LeaseAutomationEngine
import com.example.domain.finance.RenewalActionType
import com.example.domain.finance.RenewalSweepCandidate
import com.example.domain.model.EscalationType
import com.example.domain.model.Invoice
import com.example.domain.model.Lease
import com.example.domain.model.RenewalMode
import com.example.domain.model.RenewalOfferStatus
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TabularMoneyStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeaseAutomationBadges(lease: Lease) {
    val mode = lease.renewalModeEnum()
    val escType = lease.escalationTypeEnum()
    val offerStatus = lease.renewalOfferStatusEnum()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val modeTone = when (mode) {
            RenewalMode.AUTO_RENEW -> PillTone.INFO
            RenewalMode.OFFER -> PillTone.SUCCESS
            RenewalMode.MANUAL -> PillTone.NEUTRAL
        }
        val escLabel = when (escType) {
            EscalationType.NONE -> "Flat Rent"
            EscalationType.PERCENT -> "+${LeaseAutomationEngine.formatPercent(lease.escalationPercent)}"
            EscalationType.FIXED_KES -> "+${CurrencyUtils.formatKes(lease.escalationFixedCents)}"
        }
        StatusPill("${mode.label} • ${lease.renewalLeadDays}d • $escLabel", modeTone)

        if (lease.lateFeeEnabled && lease.lateFeePercent > 0.0) {
            StatusPill(
                "Late Fee: ${LeaseAutomationEngine.formatPercent(lease.lateFeePercent)} after ${lease.gracePeriodDays}d",
                PillTone.WARNING
            )
        } else {
            StatusPill("Late Fee: Off", PillTone.NEUTRAL)
        }

        when (offerStatus) {
            RenewalOfferStatus.PENDING -> StatusPill("Renewal Offer Pending", PillTone.WARNING)
            RenewalOfferStatus.ACCEPTED -> StatusPill("Offer Accepted", PillTone.SUCCESS)
            RenewalOfferStatus.DECLINED -> StatusPill("Offer Declined", PillTone.ERROR)
            RenewalOfferStatus.AUTO_RENEWED -> StatusPill("Auto-Renewed", PillTone.INFO)
            RenewalOfferStatus.NONE -> Unit
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeaseAutomationPolicyDialog(
    lease: Lease,
    canEditPricing: Boolean,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        renewalMode: RenewalMode,
        renewalLeadDays: Long,
        renewalDurationMonths: Long,
        escalationType: EscalationType,
        escalationPercent: Double,
        escalationFixedCents: Long,
        gracePeriodDays: Long,
        lateFeePercent: Double,
        lateFeeEnabled: Boolean
    ) -> Unit
) {
    var renewalMode by remember(lease.id) { mutableStateOf(lease.renewalModeEnum()) }
    var leadDaysText by remember(lease.id) { mutableStateOf(lease.renewalLeadDays.toString()) }
    var durationMonthsText by remember(lease.id) { mutableStateOf(lease.renewalDurationMonths.toString()) }
    var escalationType by remember(lease.id) { mutableStateOf(lease.escalationTypeEnum()) }
    var escPercentText by remember(lease.id) {
        mutableStateOf(
            if (lease.escalationPercent == 0.0) "0"
            else LeaseAutomationEngine.formatPercent(lease.escalationPercent).removeSuffix("%")
        )
    }
    var escFixedText by remember(lease.id) {
        mutableStateOf(CurrencyUtils.centsToInputString(lease.escalationFixedCents))
    }
    var lateFeeEnabled by remember(lease.id) { mutableStateOf(lease.lateFeeEnabled) }
    var graceDaysText by remember(lease.id) { mutableStateOf(lease.gracePeriodDays.toString()) }
    var latePercentText by remember(lease.id) {
        mutableStateOf(LeaseAutomationEngine.formatPercent(lease.lateFeePercent).removeSuffix("%"))
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val previewRent = remember(lease.rentCents, escalationType, escPercentText, escFixedText) {
        val pct = escPercentText.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0
        val fixed = CurrencyUtils.parseKesToCents(escFixedText) ?: 0L
        LeaseAutomationEngine.computeEscalatedRentCents(
            currentRentCents = lease.rentCents,
            escalationType = escalationType,
            escalationPercent = pct,
            escalationFixedCents = fixed
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${stringResource(R.string.lease_automation_policy_title)} • Unit ${lease.unitNumber}")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (!errorMsg.isNullOrBlank()) {
                    ErrorBannerCard(errorMsg!!)
                }
                Text(
                    text = "${lease.propertyName} • ${lease.tenantName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Renewal Behavior", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RenewalMode.entries.forEach { mode ->
                        FilterChip(
                            selected = renewalMode == mode,
                            onClick = { renewalMode = mode; errorMsg = null },
                            label = { Text(mode.label, maxLines = 1) },
                            modifier = Modifier.testTag("policy_renewal_mode_${mode.name.lowercase()}")
                        )
                    }
                }
                Text(
                    text = when (renewalMode) {
                        RenewalMode.AUTO_RENEW -> "Automatically extends the active lease and applies any configured rent increase when within the lead window."
                        RenewalMode.OFFER -> "Sends a renewal offer to the tenant to review, accept, or decline before the lease ends."
                        RenewalMode.MANUAL -> "No automated renewals or offers will be dispatched for this lease."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = leadDaysText,
                        onValueChange = { leadDaysText = it.filter { ch -> ch.isDigit() }.take(3); errorMsg = null },
                        label = { Text("Lead Window (Days)") },
                        supportingText = { Text("1–180 days") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("policy_lead_days_input")
                    )
                    OutlinedTextField(
                        value = durationMonthsText,
                        onValueChange = { durationMonthsText = it.filter { ch -> ch.isDigit() }.take(2); errorMsg = null },
                        label = { Text("Renew Term (Mos)") },
                        supportingText = { Text("1–60 months") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("policy_duration_months_input")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Rent Escalation on Renewal", style = MaterialTheme.typography.labelLarge)
                    if (!canEditPricing) {
                        StatusPill("Owner Only", PillTone.WARNING)
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    EscalationType.entries.forEach { esc ->
                        FilterChip(
                            selected = escalationType == esc,
                            onClick = { if (canEditPricing) { escalationType = esc; errorMsg = null } },
                            enabled = canEditPricing,
                            label = { Text(esc.label, maxLines = 1) },
                            modifier = Modifier.testTag("policy_escalation_${esc.name.lowercase()}")
                        )
                    }
                }
                if (escalationType == EscalationType.PERCENT) {
                    OutlinedTextField(
                        value = escPercentText,
                        onValueChange = { escPercentText = it.take(6); errorMsg = null },
                        label = { Text("Escalation Percentage (%)") },
                        supportingText = { Text("0–100%") },
                        enabled = canEditPricing,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("policy_escalation_percent_input")
                    )
                } else if (escalationType == EscalationType.FIXED_KES) {
                    OutlinedTextField(
                        value = escFixedText,
                        onValueChange = { escFixedText = it.take(10); errorMsg = null },
                        label = { Text("Fixed Increase (KES)") },
                        enabled = canEditPricing,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("policy_escalation_fixed_input")
                    )
                }

                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp).fillMaxWidth()
                    ) {
                        Text("Projected Renewal Rent", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${CurrencyUtils.formatKes(lease.rentCents, true)} -> ${CurrencyUtils.formatKes(previewRent, true)}",
                            style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Automated Late Fees", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = if (canEditPricing) "One-time fee on overdue balance after grace period"
                            else "Owner permission required to modify late fee pricing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = lateFeeEnabled,
                        onCheckedChange = { if (canEditPricing) lateFeeEnabled = it },
                        enabled = canEditPricing,
                        modifier = Modifier.testTag("policy_late_fee_switch")
                    )
                }

                if (lateFeeEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = graceDaysText,
                            onValueChange = { graceDaysText = it.filter { ch -> ch.isDigit() }.take(2); errorMsg = null },
                            label = { Text("Grace Period (Days)") },
                            supportingText = { Text("0–30 days") },
                            enabled = canEditPricing,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("policy_grace_days_input")
                        )
                        OutlinedTextField(
                            value = latePercentText,
                            onValueChange = { latePercentText = it.take(5); errorMsg = null },
                            label = { Text("Late Fee (%)") },
                            supportingText = { Text("0–50% of balance") },
                            enabled = canEditPricing,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("policy_late_fee_percent_input")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val leadDays = leadDaysText.toLongOrNull()
                    val durMonths = durationMonthsText.toLongOrNull()
                    val escPct = escPercentText.toDoubleOrNull()
                    val escFixedCents = CurrencyUtils.parseKesToCents(escFixedText)
                    val graceDays = graceDaysText.toLongOrNull()
                    val latePct = latePercentText.toDoubleOrNull()

                    when {
                        leadDays == null || leadDays !in 1L..180L ->
                            errorMsg = "Lead window must be between 1 and 180 days."
                        durMonths == null || durMonths !in 1L..60L ->
                            errorMsg = "Renewal duration must be between 1 and 60 months."
                        canEditPricing && escalationType == EscalationType.PERCENT && (escPct == null || escPct !in 0.0..100.0) ->
                            errorMsg = "Escalation percentage must be between 0 and 100."
                        canEditPricing && escalationType == EscalationType.FIXED_KES && (escFixedCents == null || escFixedCents < 0L) ->
                            errorMsg = "Enter a valid non-negative KES increase."
                        canEditPricing && lateFeeEnabled && (graceDays == null || graceDays !in 0L..30L) ->
                            errorMsg = "Grace period must be between 0 and 30 days."
                        canEditPricing && lateFeeEnabled && (latePct == null || latePct !in 0.0..50.0) ->
                            errorMsg = "Late fee percentage must be between 0 and 50."
                        else -> {
                            onSave(
                                renewalMode,
                                leadDays,
                                durMonths,
                                if (canEditPricing) escalationType else lease.escalationTypeEnum(),
                                if (canEditPricing) (escPct ?: lease.escalationPercent) else lease.escalationPercent,
                                if (canEditPricing) (escFixedCents ?: lease.escalationFixedCents) else lease.escalationFixedCents,
                                if (canEditPricing) (graceDays ?: lease.gracePeriodDays) else lease.gracePeriodDays,
                                if (canEditPricing) (latePct ?: lease.lateFeePercent) else lease.lateFeePercent,
                                if (canEditPricing) lateFeeEnabled else lease.lateFeeEnabled
                            )
                        }
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("save_lease_policy_button")
            ) {
                Text("Save Policy")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LeaseRenewalSweepDialog(
    candidates: List<RenewalSweepCandidate>,
    isOwnerUid: (Lease) -> Boolean,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirmSweep: () -> Unit
) {
    val autoRenewCount = candidates.count { it.actionType == RenewalActionType.AUTO_RENEW }
    val offerCount = candidates.count { it.actionType == RenewalActionType.SEND_OFFER }
    val ownerSkippedCount = candidates.count {
        it.actionType == RenewalActionType.AUTO_RENEW && !isOwnerUid(it.lease) && it.projectedRentCents != it.lease.rentCents
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lease_automation_sweep_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (candidates.isEmpty()) {
                    Text(
                        "No active leases are currently inside their renewal lead window or awaiting offer dispatch.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        "${candidates.size} lease(s) ready ($autoRenewCount auto-renew, $offerCount renewal offer).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (ownerSkippedCount > 0) {
                        StatusPill(
                            "$ownerSkippedCount escalated auto-renew(s) require Owner/Scheduler",
                            PillTone.WARNING
                        )
                    }
                    candidates.forEach { candidate ->
                        val lease = candidate.lease
                        val tone = if (candidate.actionType == RenewalActionType.AUTO_RENEW) PillTone.INFO else PillTone.SUCCESS
                        Card(
                            shape = MaterialTheme.shapes.small,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "${lease.propertyName} • Unit ${lease.unitNumber}",
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatusPill(candidate.actionType.label, tone)
                                }
                                Text(
                                    "Tenant: ${lease.tenantName} • Expires in ${candidate.daysUntilExpiry}d",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Rent: ${CurrencyUtils.formatKes(lease.rentCents, true)} -> ${CurrencyUtils.formatKes(candidate.projectedRentCents, true)} • Until ${CurrencyUtils.formatDate(candidate.projectedEndDateEpochMs)}",
                                    style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (candidates.isNotEmpty()) {
                Button(
                    onClick = onConfirmSweep,
                    enabled = !isBusy,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_renewal_sweep_button")
                ) {
                    Icon(Icons.Filled.Autorenew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Run Sweep (${candidates.size})")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(if (candidates.isEmpty()) "Close" else "Cancel")
            }
        }
    )
}

@Composable
fun LateFeeSweepDialog(
    candidates: List<LateFeeSweepCandidate>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirmSweep: () -> Unit
) {
    val totalFeeCents = remember(candidates) { candidates.sumOf { it.lateFeeCents } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.late_fee_sweep_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (candidates.isEmpty()) {
                    Text(
                        "All overdue invoices are either within their grace period, already assessed, or waived.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        "${candidates.size} overdue invoice(s) past grace period. Total new late fees: ${CurrencyUtils.formatKes(totalFeeCents, true)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    candidates.forEach { candidate ->
                        val inv = candidate.invoice
                        Card(
                            shape = MaterialTheme.shapes.small,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "${inv.invoiceNumber} • ${inv.tenantName}",
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatusPill("${candidate.daysPastGrace}d past grace", PillTone.ERROR)
                                }
                                Text(
                                    "${inv.propertyName} (${inv.unitNumber}) • Overdue ${CurrencyUtils.formatKes(inv.outstandingCents, true)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "+${CurrencyUtils.formatKes(candidate.lateFeeCents, true)} (${LeaseAutomationEngine.formatPercent(candidate.lateFeeRatePercent)}) -> New Due ${CurrencyUtils.formatKes(candidate.newOutstandingCents, true)}",
                                    style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                                    color = StatusError
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (candidates.isNotEmpty()) {
                Button(
                    onClick = onConfirmSweep,
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_late_fee_sweep_button")
                ) {
                    Icon(Icons.Filled.Gavel, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Assess Fees (${candidates.size})")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(if (candidates.isEmpty()) "Close" else "Cancel")
            }
        }
    )
}

@Composable
fun WaiveLateFeeDialog(
    invoice: Invoice,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirmWaive: (String) -> Unit
) {
    var reason by remember(invoice.id) { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${stringResource(R.string.late_fee_waive_title)} • ${invoice.invoiceNumber}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Waive the ${CurrencyUtils.formatKes(invoice.lateFeeCents, true)} late fee on ${invoice.tenantName}'s invoice? The invoice balance and audit log will update immediately.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (!err.isNullOrBlank()) {
                    ErrorBannerCard(err!!)
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(300); err = null },
                    label = { Text("Audit Reason (Required)") },
                    placeholder = { Text("e.g. Verified bank transfer delayed over weekend") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("waive_late_fee_reason_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (reason.trim().length < 3) {
                        err = "Enter an audit reason (at least 3 characters)."
                    } else {
                        onConfirmWaive(reason.trim())
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_waive_late_fee_button")
            ) {
                Text("Confirm Waiver")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun TenantRenewalOfferCard(
    lease: Lease,
    onReviewOffer: () -> Unit
) {
    val offeredRent = lease.offeredRentCents ?: lease.rentCents
    val offeredEnd = lease.offeredEndDateEpochMs ?: lease.endDateEpochMs

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().testTag("tenant_renewal_offer_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        imageVector = Icons.Filled.Autorenew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        stringResource(R.string.tenant_renewal_offer_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                StatusPill("Action Needed", PillTone.WARNING)
            }
            Text(
                "${lease.propertyName} • Unit ${lease.unitNumber}: New term through ${CurrencyUtils.formatDate(offeredEnd)} at ${CurrencyUtils.formatKes(offeredRent, true)}/mo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(
                onClick = onReviewOffer,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("tenant_review_renewal_offer_button")
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.tenant_renewal_review_button))
            }
        }
    }
}

@Composable
fun TenantRenewalOfferReviewDialog(
    lease: Lease,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onRespond: (accept: Boolean, note: String?) -> Unit
) {
    val offeredRent = lease.offeredRentCents ?: lease.rentCents
    val offeredEnd = lease.offeredEndDateEpochMs ?: lease.endDateEpochMs
    val rentDelta = offeredRent - lease.rentCents
    var note by remember(lease.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renewal Offer • Unit ${lease.unitNumber}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "${lease.propertyName} • Unit ${lease.unitNumber}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OfferComparisonRow("Current Rent", "${CurrencyUtils.formatKes(lease.rentCents, true)}/mo")
                        OfferComparisonRow("Proposed Rent", "${CurrencyUtils.formatKes(offeredRent, true)}/mo", highlight = rentDelta > 0L)
                        OfferComparisonRow("Current End Date", CurrencyUtils.formatDate(lease.endDateEpochMs))
                        OfferComparisonRow("New End Date", CurrencyUtils.formatDate(offeredEnd))
                        if (rentDelta != 0L) {
                            val sign = if (rentDelta > 0L) "+" else "-"
                            OfferComparisonRow(
                                "Monthly Change",
                                "$sign${CurrencyUtils.formatKes(kotlin.math.abs(rentDelta), true)}",
                                highlight = rentDelta > 0L
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text("Response Note to Landlord (Optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("tenant_renewal_response_note_input")
                )
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onRespond(true, note.trim().takeIf { it.isNotBlank() }) },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("tenant_accept_renewal_button")
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.tenant_renewal_accept_button))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { onRespond(false, note.trim().takeIf { it.isNotBlank() }) },
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("tenant_decline_renewal_button")
                    ) {
                        Text(stringResource(R.string.tenant_renewal_decline_button), color = StatusError)
                    }
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isBusy,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Later")
                    }
                }
            }
        }
    )
}

@Composable
private fun OfferComparisonRow(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
            color = if (highlight) StatusWarning else MaterialTheme.colorScheme.onSurface
        )
    }
}
