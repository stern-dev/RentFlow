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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Receipt
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
import com.example.domain.finance.AutoBillingCandidate
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.LateFeeSweepCandidate
import com.example.domain.finance.LeaseAutomationEngine
import com.example.domain.finance.RenewalActionType
import com.example.domain.finance.RenewalSweepCandidate
import com.example.domain.model.EscalationType
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.Lease
import com.example.domain.model.MoveOutNoticeStatus
import com.example.domain.model.RecurringInvoiceSchedule
import com.example.domain.model.RecurringIssueMode
import com.example.domain.model.RecurringScheduleStatus
import com.example.domain.model.RenewalMode
import com.example.domain.model.RenewalOfferStatus
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TabularMoneyStyle
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

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

        if (lease.autoBillingEnabled) {
            StatusPill("Auto-Bill ${lease.billingLeadDays}d before due", PillTone.INFO)
        } else {
            StatusPill("Auto-Bill: Off", PillTone.NEUTRAL)
        }

        when (lease.moveOutNoticeStatusEnum()) {
            MoveOutNoticeStatus.SUBMITTED -> {
                val dateLabel = lease.requestedMoveOutEpochMs?.let { CurrencyUtils.formatDate(it) } ?: "Pending"
                StatusPill("Move-Out Notice: $dateLabel", PillTone.WARNING)
            }
            MoveOutNoticeStatus.ACKNOWLEDGED -> {
                val dateLabel = lease.requestedMoveOutEpochMs?.let { CurrencyUtils.formatDate(it) } ?: "Confirmed"
                StatusPill("Vacating: $dateLabel", PillTone.INFO)
            }
            MoveOutNoticeStatus.DECLINED -> StatusPill("Notice Revision Requested", PillTone.ERROR)
            MoveOutNoticeStatus.WITHDRAWN -> StatusPill("Notice Withdrawn", PillTone.NEUTRAL)
            MoveOutNoticeStatus.NONE -> Unit
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
        lateFeeEnabled: Boolean,
        noticePeriodDays: Long,
        autoBillingEnabled: Boolean,
        billingLeadDays: Long
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
    var noticeDaysText by remember(lease.id) { mutableStateOf(lease.noticePeriodDays.toString()) }
    var autoBillingEnabled by remember(lease.id) { mutableStateOf(lease.autoBillingEnabled) }
    var billingLeadDaysText by remember(lease.id) { mutableStateOf(lease.billingLeadDays.toString()) }
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

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Move-Out Notice & Auto-Billing", style = MaterialTheme.typography.labelLarge)
                    if (!canEditPricing) {
                        StatusPill("Owner Only", PillTone.WARNING)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Automated Monthly Rent Billing", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Issues KES invoices automatically ahead of due day ${lease.dueDayOfMonth} (prorated on move-out)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoBillingEnabled,
                        onCheckedChange = { if (canEditPricing) autoBillingEnabled = it },
                        enabled = canEditPricing,
                        modifier = Modifier.testTag("policy_auto_billing_switch")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = noticeDaysText,
                        onValueChange = { noticeDaysText = it.filter { ch -> ch.isDigit() }.take(2); errorMsg = null },
                        label = { Text("Notice Period (Days)") },
                        supportingText = { Text("1–90 days") },
                        enabled = canEditPricing,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("policy_notice_days_input")
                    )
                    if (autoBillingEnabled) {
                        OutlinedTextField(
                            value = billingLeadDaysText,
                            onValueChange = { billingLeadDaysText = it.filter { ch -> ch.isDigit() }.take(2); errorMsg = null },
                            label = { Text("Bill Lead (Days)") },
                            supportingText = { Text("0–15 days") },
                            enabled = canEditPricing,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("policy_billing_lead_days_input")
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
                    val noticeDays = noticeDaysText.toLongOrNull()
                    val billingLead = billingLeadDaysText.toLongOrNull()

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
                        canEditPricing && (noticeDays == null || noticeDays !in 1L..90L) ->
                            errorMsg = "Notice period must be between 1 and 90 days."
                        canEditPricing && autoBillingEnabled && (billingLead == null || billingLead !in 0L..15L) ->
                            errorMsg = "Auto-billing lead must be between 0 and 15 days."
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
                                if (canEditPricing) lateFeeEnabled else lease.lateFeeEnabled,
                                if (canEditPricing) (noticeDays ?: lease.noticePeriodDays) else lease.noticePeriodDays,
                                if (canEditPricing) autoBillingEnabled else lease.autoBillingEnabled,
                                if (canEditPricing) (billingLead ?: lease.billingLeadDays) else lease.billingLeadDays
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

private fun formatNairobiIsoDate(epochMs: Long): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Africa/Nairobi")).apply {
        timeInMillis = epochMs
    }
    return String.format(
        Locale.US,
        "%04d-%02d-%02d",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
    )
}

private fun parseNairobiIsoDateEndOfDayMs(raw: String): Long? {
    val match = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(raw.trim()) ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: return null
    val month = match.groupValues[2].toIntOrNull() ?: return null
    val day = match.groupValues[3].toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31) return null
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Africa/Nairobi")).apply {
        isLenient = false
        clear()
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, day)
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return runCatching { cal.timeInMillis }.getOrNull()
}

@Composable
fun TenantMoveOutNoticeCard(
    lease: Lease,
    isBusy: Boolean,
    onOpenSubmitNotice: () -> Unit,
    onWithdrawNotice: () -> Unit
) {
    val status = lease.moveOutNoticeStatusEnum()
    val tone = when (status) {
        MoveOutNoticeStatus.ACKNOWLEDGED -> PillTone.INFO
        MoveOutNoticeStatus.SUBMITTED -> PillTone.WARNING
        MoveOutNoticeStatus.DECLINED -> PillTone.ERROR
        MoveOutNoticeStatus.WITHDRAWN, MoveOutNoticeStatus.NONE -> PillTone.NEUTRAL
    }
    val vacateDateStr = lease.requestedMoveOutEpochMs?.let { CurrencyUtils.formatDate(it) }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                MoveOutNoticeStatus.SUBMITTED, MoveOutNoticeStatus.ACKNOWLEDGED ->
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.dp,
            when (status) {
                MoveOutNoticeStatus.ACKNOWLEDGED -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
                MoveOutNoticeStatus.SUBMITTED -> StatusWarning.copy(alpha = 0.5f)
                MoveOutNoticeStatus.DECLINED -> StatusError.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            }
        ),
        modifier = Modifier.fillMaxWidth().testTag("tenant_move_out_notice_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(stringResource(R.string.move_out_notice_title), style = MaterialTheme.typography.titleSmall)
                }
                StatusPill(status.label, tone)
            }

            when (status) {
                MoveOutNoticeStatus.NONE, MoveOutNoticeStatus.WITHDRAWN -> {
                    Text(
                        "Required notice period: ${lease.noticePeriodDays} days before vacating Unit ${lease.unitNumber}. Final month's rent is automatically prorated once acknowledged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onOpenSubmitNotice,
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("tenant_give_move_out_notice_button")
                    ) {
                        Icon(Icons.Filled.EventAvailable, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.move_out_notice_submit_button))
                    }
                }
                MoveOutNoticeStatus.SUBMITTED -> {
                    Text(
                        "Submitted target vacate date: ${vacateDateStr ?: "Pending"}. Awaiting landlord/manager acknowledgement.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!lease.moveOutNoticeReason.isNullOrBlank()) {
                        Text(
                            "Reason: ${lease.moveOutNoticeReason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onWithdrawNotice,
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("tenant_withdraw_move_out_notice_button")
                    ) {
                        Text("Withdraw Move-Out Notice")
                    }
                }
                MoveOutNoticeStatus.ACKNOWLEDGED -> {
                    Text(
                        "Confirmed vacate date: ${vacateDateStr ?: "Confirmed"}. Final month billing will prorate automatically through your move-out day.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!lease.moveOutNoticeResponseNote.isNullOrBlank()) {
                        Text(
                            "Management Note: ${lease.moveOutNoticeResponseNote}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                MoveOutNoticeStatus.DECLINED -> {
                    Text(
                        "Management requested a revision to your ${vacateDateStr ?: "previous"} move-out notice.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusError
                    )
                    if (!lease.moveOutNoticeResponseNote.isNullOrBlank()) {
                        Text(
                            "Note: ${lease.moveOutNoticeResponseNote}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onOpenSubmitNotice,
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("tenant_resubmit_move_out_notice_button")
                    ) {
                        Text("Submit Updated Move-Out Notice")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TenantMoveOutNoticeDialog(
    lease: Lease,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (requestedMoveOutEpochMs: Long, reason: String?) -> Unit
) {
    val nowMs = remember { System.currentTimeMillis() }
    val defaultTargetMs = remember(lease.id, lease.noticePeriodDays) {
        nowMs + lease.noticePeriodDays.coerceIn(1L, 90L) * LeaseAutomationEngine.DAY_MS
    }
    var dateText by remember(lease.id) { mutableStateOf(formatNairobiIsoDate(defaultTargetMs)) }
    var reason by remember(lease.id) { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val parsedMs = remember(dateText) { parseNairobiIsoDateEndOfDayMs(dateText) }
    val compliance = remember(parsedMs, lease) {
        parsedMs?.let { LeaseAutomationEngine.evaluateMoveOutNotice(lease, it, nowMs) }
    }
    val proratedPreview = remember(parsedMs, lease) {
        parsedMs?.let { ms ->
            val period = LeaseAutomationEngine.nairobiBillingPeriodForEpochMs(ms)
            val simLease = lease.copy(
                moveOutNoticeStatus = MoveOutNoticeStatus.ACKNOWLEDGED.name,
                requestedMoveOutEpochMs = ms
            )
            period to LeaseAutomationEngine.computeBillingPeriodRentCents(simLease, period)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move-Out Notice • Unit ${lease.unitNumber}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (!errorMsg.isNullOrBlank()) ErrorBannerCard(errorMsg!!)
                Text(
                    "Choose your planned move-out date. Your lease requires ${lease.noticePeriodDays} days' advance notice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val reqDays = lease.noticePeriodDays.coerceIn(1L, 90L)
                    val optMin = formatNairobiIsoDate(nowMs + reqDays * LeaseAutomationEngine.DAY_MS)
                    val optPlus45 = formatNairobiIsoDate(nowMs + 45L * LeaseAutomationEngine.DAY_MS)
                    val optLeaseEnd = formatNairobiIsoDate(maxOf(lease.endDateEpochMs, nowMs + LeaseAutomationEngine.DAY_MS))
                    FilterChip(
                        selected = dateText == optMin,
                        onClick = { dateText = optMin; errorMsg = null },
                        label = { Text("+${reqDays}d Notice") }
                    )
                    FilterChip(
                        selected = dateText == optPlus45,
                        onClick = { dateText = optPlus45; errorMsg = null },
                        label = { Text("+45 Days") }
                    )
                    FilterChip(
                        selected = dateText == optLeaseEnd,
                        onClick = { dateText = optLeaseEnd; errorMsg = null },
                        label = { Text("Lease End") }
                    )
                }
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it.take(10); errorMsg = null },
                    label = { Text("Target Vacate Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("move_out_date_input")
                )
                if (compliance != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            label = if (compliance.isCompliant) {
                                "${compliance.noticeDaysGiven}d notice (Compliant)"
                            } else {
                                "Short notice: ${compliance.noticeDaysGiven}/${compliance.requiredNoticeDays}d"
                            },
                            tone = if (compliance.isCompliant) PillTone.SUCCESS else PillTone.WARNING
                        )
                        if (compliance.isEarlyTermination) {
                            StatusPill("Before Term End", PillTone.INFO)
                        }
                    }
                }
                proratedPreview?.let { (period, result) ->
                    Card(
                        shape = MaterialTheme.shapes.small,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Estimated ${CurrencyUtils.billingPeriodLabel(period)} Rent",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = if (result.prorated) {
                                    "${CurrencyUtils.formatKes(result.rentCents, true)} (${result.occupiedDays}/${result.daysInMonth} days prorated)"
                                } else {
                                    "${CurrencyUtils.formatKes(result.rentCents, true)} (Full month)"
                                },
                                style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(500) },
                    label = { Text("Reason / Forwarding Note (Optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("move_out_reason_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetMs = parsedMs
                    when {
                        targetMs == null -> errorMsg = "Enter a valid date in YYYY-MM-DD format."
                        targetMs <= nowMs -> errorMsg = "Move-out date must be in the future."
                        else -> onSubmit(targetMs, reason.trim().takeIf { it.isNotBlank() })
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("submit_move_out_notice_button")
            ) {
                Text("Submit Notice")
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
fun MoveOutNoticeReviewDialog(
    lease: Lease,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onReview: (acknowledge: Boolean, note: String?) -> Unit
) {
    val targetMs = lease.requestedMoveOutEpochMs ?: System.currentTimeMillis()
    val submittedAtMs = lease.moveOutNoticeSubmittedAtMs ?: System.currentTimeMillis()
    val compliance = remember(lease.id, targetMs, submittedAtMs) {
        LeaseAutomationEngine.evaluateMoveOutNotice(lease, targetMs, submittedAtMs)
    }
    val proration = remember(lease.id, targetMs) {
        val period = LeaseAutomationEngine.nairobiBillingPeriodForEpochMs(targetMs)
        val simLease = lease.copy(moveOutNoticeStatus = MoveOutNoticeStatus.ACKNOWLEDGED.name)
        period to LeaseAutomationEngine.computeBillingPeriodRentCents(simLease, period)
    }
    var responseNote by remember(lease.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${stringResource(R.string.move_out_notice_review_title)} • Unit ${lease.unitNumber}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "${lease.tenantName} • ${lease.propertyName} (Unit ${lease.unitNumber})",
                    style = MaterialTheme.typography.labelLarge
                )
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OfferComparisonRow("Target Vacate Date", CurrencyUtils.formatDate(targetMs))
                        OfferComparisonRow(
                            "Notice Lead",
                            "${compliance.noticeDaysGiven}d / ${compliance.requiredNoticeDays}d required",
                            highlight = !compliance.isCompliant
                        )
                        OfferComparisonRow(
                            "Final Month (${CurrencyUtils.billingPeriodLabel(proration.first)})",
                            if (proration.second.prorated) {
                                "${CurrencyUtils.formatKes(proration.second.rentCents, true)} (${proration.second.occupiedDays}/${proration.second.daysInMonth}d)"
                            } else {
                                CurrencyUtils.formatKes(proration.second.rentCents, true)
                            }
                        )
                    }
                }
                if (!lease.moveOutNoticeReason.isNullOrBlank()) {
                    Text(
                        "Tenant Note: \"${lease.moveOutNoticeReason}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Acknowledging locks automated lease renewals for this unit and prorates the final monthly bill.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = responseNote,
                    onValueChange = { responseNote = it.take(500) },
                    label = { Text("Response Note to Tenant (Optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("review_move_out_note_input")
                )
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onReview(true, responseNote.trim().takeIf { it.isNotBlank() }) },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("acknowledge_move_out_button")
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Acknowledge Notice")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { onReview(false, responseNote.trim().takeIf { it.isNotBlank() }) },
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("decline_move_out_button")
                    ) {
                        Text("Request Revision", color = StatusError)
                    }
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isBusy,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    )
}

@Composable
fun AutoBillingSweepDialog(
    candidates: List<AutoBillingCandidate>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirmSweep: () -> Unit
) {
    val totalBilledCents = remember(candidates) { candidates.sumOf { it.totalDueCents } }
    val proratedCount = remember(candidates) { candidates.count { it.prorated } }
    val draftCount = remember(candidates) { candidates.count { it.targetStatus == InvoiceStatus.DRAFT } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auto_billing_sweep_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (candidates.isEmpty()) {
                    Text(
                        "No active leases are currently inside their automated monthly billing lead window or missing this month's invoice.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        "${candidates.size} lease(s) ready ($proratedCount prorated, $draftCount draft) • Total ${CurrencyUtils.formatKes(totalBilledCents, true)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    candidates.forEach { candidate ->
                        val lease = candidate.lease
                        val isDraft = candidate.targetStatus == InvoiceStatus.DRAFT
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
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        StatusPill(
                                            if (isDraft) "Stage Draft" else "Auto-Issue",
                                            if (isDraft) PillTone.NEUTRAL else PillTone.INFO
                                        )
                                        if (candidate.prorated) {
                                            StatusPill("Prorated", PillTone.INFO)
                                        }
                                    }
                                }
                                Text(
                                    "Tenant: ${lease.tenantName} • Due ${CurrencyUtils.formatDate(candidate.dueDateEpochMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Lease Grace: ${lease.gracePeriodDays}d • Late Fee: " +
                                        if (lease.lateFeeEnabled) LeaseAutomationEngine.formatPercent(lease.lateFeePercent) else "Off",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                candidate.prorationNote?.let { note ->
                                    Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                }
                                Text(
                                    "Rent ${CurrencyUtils.formatKes(candidate.rentCents, true)}" +
                                        (if (candidate.extraChargesCents > 0L) " + Extras ${CurrencyUtils.formatKes(candidate.extraChargesCents)}" else "") +
                                        (if (candidate.previousBalanceCents > 0L) " + Arrears ${CurrencyUtils.formatKes(candidate.previousBalanceCents)}" else "") +
                                        (if (candidate.creditToApplyCents > 0L) " - Credit ${CurrencyUtils.formatKes(candidate.creditToApplyCents)}" else "") +
                                        " -> Net ${CurrencyUtils.formatKes(candidate.outstandingCents, true)}",
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
                    modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_auto_billing_sweep_button")
                ) {
                    Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Bill ${candidates.size} Lease(s)")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (candidates.isEmpty()) "Close" else "Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurringScheduleEditorDialog(
    lease: Lease,
    existingSchedule: RecurringInvoiceSchedule?,
    canEditPricing: Boolean,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        amountCents: Long,
        recurringExtraCents: Long,
        recurringExtraNote: String?,
        billingDayOfMonth: Long,
        leadDays: Long,
        scheduleStatus: RecurringScheduleStatus,
        issueMode: RecurringIssueMode
    ) -> Unit
) {
    var amountText by remember(lease.id, existingSchedule?.amountCents) {
        mutableStateOf(CurrencyUtils.centsToInputString(existingSchedule?.amountCents ?: lease.rentCents))
    }
    var extraText by remember(lease.id, existingSchedule?.recurringExtraCents) {
        mutableStateOf(
            (existingSchedule?.recurringExtraCents ?: 0L).let {
                if (it > 0L) CurrencyUtils.centsToInputString(it) else "0"
            }
        )
    }
    var extraNote by remember(lease.id, existingSchedule?.recurringExtraNote) {
        mutableStateOf(existingSchedule?.recurringExtraNote.orEmpty())
    }
    var billingDayText by remember(lease.id, existingSchedule?.billingDayOfMonth) {
        mutableStateOf((existingSchedule?.billingDayOfMonth ?: lease.dueDayOfMonth).coerceIn(1L, 28L).toString())
    }
    var leadDaysText by remember(lease.id, existingSchedule?.leadDays) {
        mutableStateOf((existingSchedule?.leadDays ?: lease.billingLeadDays).coerceIn(0L, 15L).toString())
    }
    var scheduleStatus by remember(lease.id, existingSchedule?.scheduleStatus) {
        mutableStateOf(
            existingSchedule?.scheduleStatusEnum()
                ?: if (lease.autoBillingEnabled) RecurringScheduleStatus.ACTIVE else RecurringScheduleStatus.PAUSED
        )
    }
    var issueMode by remember(lease.id, existingSchedule?.issueMode) {
        mutableStateOf(existingSchedule?.issueModeEnum() ?: RecurringIssueMode.AUTO_ISSUE)
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recurring Billing • Unit ${lease.unitNumber}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "${lease.tenantName} • ${lease.propertyName}",
                    style = MaterialTheme.typography.labelLarge
                )
                if (!errorMsg.isNullOrBlank()) {
                    ErrorBannerCard(errorMsg!!)
                }

                // Linked Lease Grace Period (Single Source of Truth)
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth().testTag("schedule_lease_grace_reference")
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Grace Period Settings (From Lease)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            "${lease.gracePeriodDays} day(s) grace • Late fee: " +
                                if (lease.lateFeeEnabled) LeaseAutomationEngine.formatPercent(lease.lateFeePercent) else "Disabled",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Text("Schedule Status", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RecurringScheduleStatus.entries.forEach { st ->
                        FilterChip(
                            selected = scheduleStatus == st,
                            onClick = { scheduleStatus = st; errorMsg = null },
                            label = { Text(st.label, maxLines = 1) },
                            modifier = Modifier.testTag("schedule_status_${st.name.lowercase()}")
                        )
                    }
                }

                Text("Monthly Issue Mode", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RecurringIssueMode.entries.forEach { mode ->
                        FilterChip(
                            selected = issueMode == mode,
                            onClick = { issueMode = mode; errorMsg = null },
                            label = { Text(mode.label, maxLines = 1) },
                            modifier = Modifier.testTag("schedule_mode_${mode.name.lowercase()}")
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = billingDayText,
                        onValueChange = { billingDayText = it.filter { ch -> ch.isDigit() }.take(2); errorMsg = null },
                        label = { Text("Due Day (1-28)") },
                        enabled = canEditPricing,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("schedule_billing_day_input")
                    )
                    OutlinedTextField(
                        value = leadDaysText,
                        onValueChange = { leadDaysText = it.filter { ch -> ch.isDigit() }.take(2); errorMsg = null },
                        label = { Text("Lead Days (0-15)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("schedule_lead_days_input")
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Recurring Charges", style = MaterialTheme.typography.labelLarge)
                    if (!canEditPricing) StatusPill("Owner Only", PillTone.WARNING)
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; errorMsg = null },
                    label = { Text("Base Monthly Rent (KES)") },
                    enabled = canEditPricing,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("schedule_amount_input")
                )
                OutlinedTextField(
                    value = extraText,
                    onValueChange = { extraText = it; errorMsg = null },
                    label = { Text("Fixed Recurring Extras (KES)") },
                    supportingText = { Text("e.g. Monthly water, service charge, or garbage") },
                    enabled = canEditPricing,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("schedule_extra_input")
                )
                OutlinedTextField(
                    value = extraNote,
                    onValueChange = { extraNote = it.take(200) },
                    label = { Text("Recurring Extras Note (Optional)") },
                    enabled = canEditPricing,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("schedule_extra_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedAmount = CurrencyUtils.parseKesToCents(amountText)
                    val parsedExtra = CurrencyUtils.parseKesToCents(extraText.ifBlank { "0" })
                    val day = billingDayText.toLongOrNull()
                    val lead = leadDaysText.toLongOrNull()
                    when {
                        parsedAmount == null || parsedAmount <= 0L -> errorMsg = "Enter a valid positive base rent amount."
                        parsedExtra == null || parsedExtra < 0L -> errorMsg = "Recurring extra charges cannot be negative."
                        day == null || day !in 1L..28L -> errorMsg = "Billing day must be between 1 and 28."
                        lead == null || lead !in 0L..15L -> errorMsg = "Lead days must be between 0 and 15."
                        else -> onSave(
                            parsedAmount,
                            parsedExtra,
                            extraNote.trim().takeIf { it.isNotBlank() && parsedExtra > 0L },
                            day,
                            lead,
                            scheduleStatus,
                            issueMode
                        )
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("save_recurring_schedule_button")
            ) {
                Text("Save Schedule")
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
fun IssueDraftInvoiceDialog(
    invoice: Invoice,
    lease: Lease,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirmIssue: (extraChargesCents: Long, extraChargesNote: String?) -> Unit
) {
    var extraText by remember(invoice.id) {
        mutableStateOf(
            if (invoice.extraChargesCents > 0L) CurrencyUtils.centsToInputString(invoice.extraChargesCents) else "0"
        )
    }
    var extraNote by remember(invoice.id) {
        mutableStateOf(invoice.extraChargesNote.orEmpty())
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val parsedExtra = CurrencyUtils.parseKesToCents(extraText.ifBlank { "0" }) ?: 0L
    val preview = remember(invoice.id, parsedExtra, lease.creditBalanceCents, lease.gracePeriodDays) {
        LeaseAutomationEngine.evaluateDraftIssuance(
            draftInvoice = invoice,
            lease = lease,
            extraChargesCents = parsedExtra.coerceAtLeast(0L)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Issue Draft • ${invoice.invoiceNumber}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "${invoice.tenantName} • ${invoice.propertyName} (${invoice.unitNumber}) • ${CurrencyUtils.billingPeriodLabel(invoice.billingPeriod)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!errorMsg.isNullOrBlank()) ErrorBannerCard(errorMsg!!)

                OutlinedTextField(
                    value = extraText,
                    onValueChange = { extraText = it; errorMsg = null },
                    label = { Text("Extra Charges (KES)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("issue_draft_extra_input")
                )
                OutlinedTextField(
                    value = extraNote,
                    onValueChange = { extraNote = it.take(300) },
                    label = { Text("Extra Charges Note (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("issue_draft_note_input")
                )

                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OfferComparisonRow("Base Rent", CurrencyUtils.formatKes(invoice.rentCents, true))
                        if (invoice.previousBalanceCents > 0L) {
                            OfferComparisonRow("Prior Arrears", CurrencyUtils.formatKes(invoice.previousBalanceCents, true))
                        }
                        if (preview.creditToApplyCents > 0L) {
                            OfferComparisonRow("Lease Credit Applied", "- ${CurrencyUtils.formatKes(preview.creditToApplyCents, true)}")
                        }
                        OfferComparisonRow("Net Payable", CurrencyUtils.formatKes(preview.newOutstandingCents, true), highlight = true)
                        Text(
                            "Grace ends ${CurrencyUtils.formatDate(preview.gracePeriodEndsEpochMs)} (${lease.gracePeriodDays}d from Lease)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val extras = CurrencyUtils.parseKesToCents(extraText.ifBlank { "0" })
                    if (extras == null || extras < 0L) {
                        errorMsg = "Enter a valid non-negative extra charges amount."
                    } else {
                        onConfirmIssue(extras, extraNote.trim().takeIf { it.isNotBlank() && extras > 0L })
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_issue_draft_button")
            ) {
                Text("Issue to Tenant")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel")
            }
        }
    )
}

