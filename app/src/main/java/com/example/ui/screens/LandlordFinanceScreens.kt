package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.MpesaSmsParser
import com.example.domain.finance.RentReminderEngine
import com.example.domain.model.Expense
import com.example.domain.model.ExpenseCategory
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.Lease
import com.example.domain.model.LeaseStatus
import com.example.domain.model.Payment
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.Property
import com.example.domain.model.UserProfile
import com.example.domain.security.PermissionPolicy
import com.example.ui.components.CriticalActionConfirmationDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.PillTone
import com.example.ui.components.StatusPill
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import com.example.ui.theme.TabularMoneyStyle
import com.example.ui.viewmodel.LandlordViewModel
import com.example.ui.viewmodel.UiState

private enum class LandlordInvoiceFilter(val label: String) {
    ALL("All"),
    UNPAID("Unpaid"),
    OVERDUE("Overdue"),
    PARTIALLY_PAID("Partial"),
    PAID("Paid")
}

private enum class LandlordPaymentFilter(val label: String) {
    ALL("All"),
    PENDING("Pending"),
    VERIFIED("Verified"),
    DECLINED("Rejected/Reversed")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandlordFinanceScreen(
    viewModel: LandlordViewModel,
    currentUser: UserProfile,
    isRecentlyAuthenticated: () -> Boolean = { true },
    onReauthenticate: suspend (Context) -> Result<Unit> = { Result.success(Unit) }
) {
    val invoicesState by viewModel.invoices.collectAsStateWithLifecycle()
    val paymentsState by viewModel.payments.collectAsStateWithLifecycle()
    val expensesState by viewModel.expenses.collectAsStateWithLifecycle()
    val leasesState by viewModel.leases.collectAsStateWithLifecycle()
    val propertiesState by viewModel.properties.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    val leases = (leasesState as? UiState.Success)?.data ?: emptyList()
    val properties = (propertiesState as? UiState.Success)?.data ?: emptyList()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showGenerateInvoiceDialog by remember { mutableStateOf(false) }
    var showRecordPaymentDialog by remember { mutableStateOf(false) }
    var showRecordExpenseDialog by remember { mutableStateOf(false) }
    var paymentToVerify by remember { mutableStateOf<Payment?>(null) }
    var paymentToReject by remember { mutableStateOf<Payment?>(null) }
    var paymentToReverse by remember { mutableStateOf<Payment?>(null) }

    val tabs = listOf("Invoices", "Payments", "Expenses")

    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { idx, label ->
                Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(label) })
            }
        }

        when (selectedTab) {
            0 -> InvoicesTab(
                invoicesState = invoicesState,
                hasActiveLeases = leases.any { it.statusEnum() == LeaseStatus.ACTIVE },
                isBusy = busy,
                onGenerateInvoices = { showGenerateInvoiceDialog = true },
                onSendReminders = { viewModel.sendBatchRentReminders(currentUser.fullName) }
            )
            1 -> PaymentsTab(
                paymentsState = paymentsState,
                hasActiveLeases = leases.any { it.statusEnum() == LeaseStatus.ACTIVE },
                canReversePayment = { pay ->
                    PermissionPolicy.canReversePayment(currentUser.userId, currentUser.activeRoleEnum(), pay)
                },
                onRecordPayment = { showRecordPaymentDialog = true },
                onVerify = { pay -> paymentToVerify = pay },
                onReject = { pay -> paymentToReject = pay },
                onReverse = { pay -> paymentToReverse = pay }
            )
            2 -> ExpensesTab(
                expensesState = expensesState,
                hasProperties = properties.isNotEmpty(),
                onAddExpense = { showRecordExpenseDialog = true }
            )
        }
    }

    if (showGenerateInvoiceDialog) {
        var period by remember { mutableStateOf(CurrencyUtils.currentBillingPeriod()) }
        var extraText by remember { mutableStateOf("0") }
        var extraNote by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showGenerateInvoiceDialog = false },
            title = { Text("Generate Monthly Invoices") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Generates one invoice per active lease for the selected billing month (duplicates are automatically prevented).")
                    if (!err.isNullOrBlank()) ErrorBannerCard(err!!)
                    OutlinedTextField(
                        value = period,
                        onValueChange = { period = it; err = null },
                        label = { Text("Billing Period (YYYY-MM)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("invoice_period_input")
                    )
                    OutlinedTextField(
                        value = extraText,
                        onValueChange = { extraText = it; err = null },
                        label = { Text("Shared Water / Garbage Fee (KES)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = extraNote,
                        onValueChange = { extraNote = it },
                        label = { Text("Charge Note (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedPeriod = period.trim()
                        val extraCents = CurrencyUtils.parseKesToCents(extraText)
                        if (!CurrencyUtils.isValidBillingPeriod(trimmedPeriod)) {
                            err = "Enter a valid billing period in YYYY-MM format (e.g. 2026-11)"
                        } else if (extraCents == null || extraCents < 0L) {
                            err = "Enter a valid non-negative KES amount for shared fees"
                        } else {
                            viewModel.generateMonthlyInvoices(trimmedPeriod, extraCents, extraNote.ifBlank { null }, currentUser.fullName)
                            showGenerateInvoiceDialog = false
                        }
                    },
                    enabled = !busy
                ) { Text("Generate") }
            },
            dismissButton = { TextButton(onClick = { showGenerateInvoiceDialog = false }) { Text("Cancel") } }
        )
    }

    if (showRecordPaymentDialog) {
        RecordLandlordPaymentDialog(
            leases = leases.filter { it.statusEnum() == LeaseStatus.ACTIVE },
            isBusy = busy,
            onDismiss = { showRecordPaymentDialog = false },
            onSubmit = { lease, ref, amountCents, method, verifyNow ->
                viewModel.recordLandlordPayment(lease, ref, amountCents, method, verifyNow, currentUser.fullName) {
                    showRecordPaymentDialog = false
                }
            }
        )
    }

    if (showRecordExpenseDialog) {
        RecordExpenseDialog(
            properties = properties.filter { !it.archived },
            isBusy = busy,
            onDismiss = { showRecordExpenseDialog = false },
            onSubmit = { prop, cat, title, desc, amountCents, docRef ->
                viewModel.recordExpense(prop, cat, title, desc, amountCents, docRef, currentUser.fullName) {
                    showRecordExpenseDialog = false
                }
            }
        )
    }

    paymentToVerify?.let { pay ->
        var notes by remember { mutableStateOf("Verified against M-Pesa / bank statement") }
        AlertDialog(
            onDismissRequest = { paymentToVerify = null },
            title = { Text("Verify & Allocate Payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Confirm ${CurrencyUtils.formatKes(pay.amountCents, true)} from ${pay.tenantName} (Ref: ${pay.referenceCode}) and allocate to oldest open invoices.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Verification Note") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.verifyPayment(pay, notes.ifBlank { "Verified by management" }, currentUser.fullName)
                        paymentToVerify = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess)
                ) { Text("Verify & Issue Receipt") }
            },
            dismissButton = { TextButton(onClick = { paymentToVerify = null }) { Text("Cancel") } }
        )
    }

    paymentToReject?.let { pay ->
        var reason by remember { mutableStateOf("Reference not matched in statement") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { paymentToReject = null },
            title = { Text("Reject Payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reject ${pay.referenceCode} (${CurrencyUtils.formatKes(pay.amountCents, true)}) from ${pay.tenantName}?")
                    if (!err.isNullOrBlank()) ErrorBannerCard(err!!)
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it; err = null },
                        label = { Text("Rejection Reason (Required)") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (reason.trim().length < 3) err = "Enter a clear rejection reason"
                        else {
                            viewModel.rejectPayment(pay, reason.trim(), currentUser.fullName)
                            paymentToReject = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) { Text("Reject") }
            },
            dismissButton = { TextButton(onClick = { paymentToReject = null }) { Text("Cancel") } }
        )
    }

    paymentToReverse?.let { pay ->
        CriticalActionConfirmationDialog(
            title = "Reverse Verified Payment",
            impactSummary = "Reversing ${pay.referenceCode} (${CurrencyUtils.formatKes(pay.amountCents, true)}) restores affected invoice balances, revokes the receipt, and writes an immutable audit entry.",
            confirmButtonText = "Reverse Payment",
            requiredPhrase = "REVERSE",
            reasonLabel = "Audit Reason (Required)",
            requireReasonMinLen = 3,
            isRecentlyAuthenticated = isRecentlyAuthenticated(),
            onReauthenticate = onReauthenticate,
            onDismiss = { paymentToReverse = null },
            onConfirm = { reason ->
                viewModel.reversePayment(pay, reason, currentUser.fullName)
                paymentToReverse = null
            }
        )
    }
}

@Composable
private fun InvoicesTab(
    invoicesState: UiState<List<Invoice>>,
    hasActiveLeases: Boolean,
    isBusy: Boolean,
    onGenerateInvoices: () -> Unit,
    onSendReminders: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf(LandlordInvoiceFilter.ALL) }
    var expandedInvoiceIds by remember { mutableStateOf(emptySet<String>()) }
    val allInvoicesList = (invoicesState as? UiState.Success)?.data ?: emptyList()
    val reminderCandidates = remember(allInvoicesList) {
        RentReminderEngine.evaluateInvoices(allInvoicesList, respectCooldown = true)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Rent Invoices", style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = onGenerateInvoices,
                    enabled = hasActiveLeases,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("generate_invoices_button")
                ) {
                    Icon(Icons.Filled.Receipt, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Bill Month")
                }
            }
        }
        if (allInvoicesList.isNotEmpty()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(12.dp).fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Automated Rent Reminders", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (reminderCandidates.isEmpty()) {
                                    "All due & overdue tenants were recently reminded"
                                } else {
                                    "${reminderCandidates.size} invoice(s) eligible for a due/overdue alert"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = onSendReminders,
                            enabled = !isBusy && reminderCandidates.isNotEmpty(),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("send_rent_reminders_button")
                        ) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Remind (${reminderCandidates.size})")
                        }
                    }
                }
            }
        }
        when (invoicesState) {
            is UiState.Loading -> item { LoadingStateBox("Loading invoices...") }
            is UiState.Error -> item { ErrorBannerCard(invoicesState.message) }
            is UiState.Success -> {
                val allInvoices = invoicesState.data
                if (allInvoices.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No Rent Invoices Generated",
                            message = if (hasActiveLeases) "Tap 'Bill Month' to automatically generate KES invoices for all active leases." else "Create an active lease first to generate monthly invoices.",
                            ctaLabel = if (hasActiveLeases) "Generate Monthly Invoices" else null,
                            onCtaClick = if (hasActiveLeases) onGenerateInvoices else null
                        )
                    }
                } else {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(LandlordInvoiceFilter.entries) { filter ->
                                val count = when (filter) {
                                    LandlordInvoiceFilter.ALL -> allInvoices.size
                                    LandlordInvoiceFilter.UNPAID -> allInvoices.count { it.statusEnum() == InvoiceStatus.UNPAID }
                                    LandlordInvoiceFilter.OVERDUE -> allInvoices.count { it.statusEnum() == InvoiceStatus.OVERDUE }
                                    LandlordInvoiceFilter.PARTIALLY_PAID -> allInvoices.count { it.statusEnum() == InvoiceStatus.PARTIALLY_PAID }
                                    LandlordInvoiceFilter.PAID -> allInvoices.count { it.statusEnum() == InvoiceStatus.PAID }
                                }
                                FilterChip(
                                    selected = selectedFilter == filter,
                                    onClick = { selectedFilter = filter },
                                    label = { Text("${filter.label} ($count)") },
                                    modifier = Modifier.testTag("landlord_invoice_filter_${filter.name.lowercase()}")
                                )
                            }
                        }
                    }

                    val filtered = when (selectedFilter) {
                        LandlordInvoiceFilter.ALL -> allInvoices
                        LandlordInvoiceFilter.UNPAID -> allInvoices.filter { it.statusEnum() == InvoiceStatus.UNPAID }
                        LandlordInvoiceFilter.OVERDUE -> allInvoices.filter { it.statusEnum() == InvoiceStatus.OVERDUE }
                        LandlordInvoiceFilter.PARTIALLY_PAID -> allInvoices.filter { it.statusEnum() == InvoiceStatus.PARTIALLY_PAID }
                        LandlordInvoiceFilter.PAID -> allInvoices.filter { it.statusEnum() == InvoiceStatus.PAID }
                    }

                    if (filtered.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No Matching Invoices",
                                message = "No invoices match the '${selectedFilter.label}' filter.",
                                ctaLabel = "Show All",
                                onCtaClick = { selectedFilter = LandlordInvoiceFilter.ALL },
                                showIllustration = false
                            )
                        }
                    } else {
                        items(filtered, key = { it.id }) { inv ->
                            val expanded = inv.id in expandedInvoiceIds
                            val tone = when (inv.statusEnum()) {
                                InvoiceStatus.PAID -> PillTone.SUCCESS
                                InvoiceStatus.PARTIALLY_PAID -> PillTone.WARNING
                                InvoiceStatus.OVERDUE -> PillTone.ERROR
                                InvoiceStatus.UNPAID -> PillTone.INFO
                                InvoiceStatus.CANCELLED -> PillTone.NEUTRAL
                            }
                            Card(
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize()
                                    .clickable {
                                        expandedInvoiceIds = if (expanded) expandedInvoiceIds - inv.id else expandedInvoiceIds + inv.id
                                    }
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${inv.invoiceNumber} • ${inv.tenantName}", style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                "${inv.propertyName} (${inv.unitNumber}) • ${CurrencyUtils.billingPeriodLabel(inv.billingPeriod)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        StatusPill(inv.statusEnum().label, tone)
                                    }
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text("Total: ${CurrencyUtils.formatKes(inv.totalDueCents, true)}", style = MaterialTheme.typography.bodySmall)
                                        Text("Paid: ${CurrencyUtils.formatKes(inv.paidCents, true)}", style = MaterialTheme.typography.bodySmall, color = StatusSuccess)
                                        Text(
                                            "Due: ${CurrencyUtils.formatKes(inv.outstandingCents, true)}",
                                            style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                                            color = if (inv.outstandingCents > 0) StatusError else StatusSuccess
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "Due Date: ${CurrencyUtils.formatDate(inv.dueDateEpochMs)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                if (expanded) "Hide Breakdown" else "KES Breakdown",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                            Icon(
                                                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                contentDescription = if (expanded) "Collapse breakdown" else "Expand breakdown",
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    if (expanded) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            InvoiceBreakdownLine("Base Monthly Rent", CurrencyUtils.formatKes(inv.rentCents, true))
                                            if (inv.extraChargesCents > 0L || !inv.extraChargesNote.isNullOrBlank()) {
                                                val extraLabel = inv.extraChargesNote?.takeIf { it.isNotBlank() }?.let { "Extra Charges ($it)" } ?: "Extra Charges"
                                                InvoiceBreakdownLine(extraLabel, CurrencyUtils.formatKes(inv.extraChargesCents, true))
                                            }
                                            if (inv.previousBalanceCents > 0L) {
                                                InvoiceBreakdownLine("Previous Arrears Carried Forward", CurrencyUtils.formatKes(inv.previousBalanceCents, true))
                                            }
                                            InvoiceBreakdownLine("Paid / Credit Applied", "- ${CurrencyUtils.formatKes(inv.paidCents, true)}")
                                            InvoiceBreakdownLine("Outstanding Balance", CurrencyUtils.formatKes(inv.outstandingCents, true), highlightError = inv.outstandingCents > 0L)
                                        }
                                    }
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
private fun InvoiceBreakdownLine(
    label: String,
    value: String,
    highlightError: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            value,
            style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
            color = if (highlightError) StatusError else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PaymentsTab(
    paymentsState: UiState<List<Payment>>,
    hasActiveLeases: Boolean,
    canReversePayment: (Payment) -> Boolean,
    onRecordPayment: () -> Unit,
    onVerify: (Payment) -> Unit,
    onReject: (Payment) -> Unit,
    onReverse: (Payment) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(LandlordPaymentFilter.ALL) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Payment Ledger", style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = onRecordPayment,
                    enabled = hasActiveLeases,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Record Payment")
                }
            }
        }
        when (paymentsState) {
            is UiState.Loading -> item { LoadingStateBox("Loading payment ledger...") }
            is UiState.Error -> item { ErrorBannerCard(paymentsState.message) }
            is UiState.Success -> {
                val allPayments = paymentsState.data
                val verifiedGrossCents = allPayments
                    .filter { it.statusEnum() == PaymentStatus.VERIFIED }
                    .sumOf { it.amountCents }
                if (verifiedGrossCents > 0L) {
                    item {
                        KenyaMriTaxEstimateCard(grossCollectedCents = verifiedGrossCents)
                    }
                }
                if (allPayments.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No Payments Recorded",
                            message = "Tenant M-Pesa/bank references and management-recorded payments appear here for verification and invoice allocation."
                        )
                    }
                } else {
                    val pendingCount = allPayments.count { it.statusEnum() == PaymentStatus.PENDING_VERIFICATION }
                    if (pendingCount > 0) {
                        item {
                            Surface(
                                color = StatusWarningBg,
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFilter = LandlordPaymentFilter.PENDING }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        "$pendingCount payment(s) awaiting statement verification",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = StatusWarning,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        if (selectedFilter == LandlordPaymentFilter.PENDING) "Filtered" else "Filter Pending",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = StatusWarning
                                    )
                                }
                            }
                        }
                    }

                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(LandlordPaymentFilter.entries) { filter ->
                                val count = when (filter) {
                                    LandlordPaymentFilter.ALL -> allPayments.size
                                    LandlordPaymentFilter.PENDING -> pendingCount
                                    LandlordPaymentFilter.VERIFIED -> allPayments.count { it.statusEnum() == PaymentStatus.VERIFIED }
                                    LandlordPaymentFilter.DECLINED -> allPayments.count {
                                        it.statusEnum() == PaymentStatus.REJECTED || it.statusEnum() == PaymentStatus.REVERSED
                                    }
                                }
                                FilterChip(
                                    selected = selectedFilter == filter,
                                    onClick = { selectedFilter = filter },
                                    label = { Text("${filter.label} ($count)") },
                                    modifier = Modifier.testTag("landlord_payment_filter_${filter.name.lowercase()}")
                                )
                            }
                        }
                    }

                    val filtered = when (selectedFilter) {
                        LandlordPaymentFilter.ALL -> allPayments
                        LandlordPaymentFilter.PENDING -> allPayments.filter { it.statusEnum() == PaymentStatus.PENDING_VERIFICATION }
                        LandlordPaymentFilter.VERIFIED -> allPayments.filter { it.statusEnum() == PaymentStatus.VERIFIED }
                        LandlordPaymentFilter.DECLINED -> allPayments.filter {
                            it.statusEnum() == PaymentStatus.REJECTED || it.statusEnum() == PaymentStatus.REVERSED
                        }
                    }

                    if (filtered.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No Matching Payments",
                                message = "No payments match the '${selectedFilter.label}' filter.",
                                ctaLabel = "Show All",
                                onCtaClick = { selectedFilter = LandlordPaymentFilter.ALL },
                                showIllustration = false
                            )
                        }
                    } else {
                        items(filtered, key = { it.id }) { pay ->
                            val tone = when (pay.statusEnum()) {
                                PaymentStatus.VERIFIED -> PillTone.SUCCESS
                                PaymentStatus.PENDING_VERIFICATION -> PillTone.WARNING
                                PaymentStatus.REJECTED, PaymentStatus.REVERSED -> PillTone.ERROR
                            }
                            Card(
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "${pay.tenantName} • ${CurrencyUtils.formatKes(pay.amountCents, true)}",
                                                style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize)
                                            )
                                            Text("${pay.propertyName} (${pay.unitNumber}) • ${pay.methodEnum().label}", style = MaterialTheme.typography.bodySmall)
                                            Text(
                                                "Ref: ${pay.referenceCode}${if (pay.simulatedCheckout) " [Simulated Checkout]" else ""}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (!pay.receiptNumber.isNullOrBlank()) {
                                                Text("Receipt: ${pay.receiptNumber} • ${pay.allocationsSummary ?: ""}", style = MaterialTheme.typography.labelSmall, color = StatusSuccess)
                                            }
                                            if (!pay.verificationNotes.isNullOrBlank()) {
                                                Text("Note: ${pay.verificationNotes}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        StatusPill(pay.statusEnum().label, tone)
                                    }
                                    when (pay.statusEnum()) {
                                        PaymentStatus.PENDING_VERIFICATION -> {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = { onVerify(pay) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                                ) {
                                                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Verify & Allocate")
                                                }
                                                OutlinedButton(
                                                    onClick = { onReject(pay) },
                                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                                ) { Text("Reject") }
                                            }
                                        }
                                        PaymentStatus.VERIFIED -> {
                                            if (canReversePayment(pay)) {
                                                OutlinedButton(
                                                    onClick = { onReverse(pay) },
                                                    modifier = Modifier
                                                        .heightIn(min = 48.dp)
                                                        .testTag("reverse_payment_button_${pay.id}")
                                                ) {
                                                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Reverse Payment")
                                                }
                                            }
                                        }
                                        else -> {}
                                    }
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
fun KenyaMriTaxEstimateCard(grossCollectedCents: Long) {
    val estimatedTaxCents = MpesaSmsParser.computeKenyaMriTaxCents(grossCollectedCents)
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("kenya_mri_tax_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kenya KRA 7.5% MRI Tax Estimate", style = MaterialTheme.typography.titleSmall)
                StatusPill("7.5% MRI", PillTone.INFO)
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("Verified Gross Rent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(CurrencyUtils.formatKes(grossCollectedCents, true), style = TabularMoneyStyle)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Est. MRI Tax (7.5%)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        CurrencyUtils.formatKes(estimatedTaxCents, true),
                        style = TabularMoneyStyle,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Text(
                text = "Advisory estimate on verified residential rent collected (KRA iTax due by 20th of the following month).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExpensesTab(
    expensesState: UiState<List<Expense>>,
    hasProperties: Boolean,
    onAddExpense: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Operating Expenses", style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = onAddExpense,
                    enabled = hasProperties,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Log Expense")
                }
            }
        }
        when (expensesState) {
            is UiState.Loading -> item { LoadingStateBox("Loading expenses...") }
            is UiState.Error -> item { ErrorBannerCard(expensesState.message) }
            is UiState.Success -> {
                if (expensesState.data.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No Expenses Logged",
                            message = "Track property maintenance, security, utilities, insurance, and management fees to compute true net rental income."
                        )
                    }
                } else {
                    items(expensesState.data, key = { it.id }) { exp ->
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp).fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(exp.title, style = MaterialTheme.typography.titleSmall)
                                    Text("${exp.propertyName} • ${exp.categoryEnum().label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(CurrencyUtils.formatDate(exp.expenseDateEpochMs), style = MaterialTheme.typography.labelSmall)
                                }
                                Text(
                                    CurrencyUtils.formatKes(exp.amountCents, true),
                                    style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize),
                                    color = StatusWarning
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordLandlordPaymentDialog(
    leases: List<Lease>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Lease, String, Long, PaymentMethod, Boolean) -> Unit
) {
    var selectedLease by remember { mutableStateOf(leases.firstOrNull()) }
    var leaseExpanded by remember { mutableStateOf(false) }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var methodExpanded by remember { mutableStateOf(false) }
    var refCode by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf(selectedLease?.let { CurrencyUtils.centsToInputString(it.rentCents) } ?: "") }
    var verifyNow by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Tenant Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (!err.isNullOrBlank()) ErrorBannerCard(err!!)
                ExposedDropdownMenuBox(expanded = leaseExpanded, onExpandedChange = { leaseExpanded = !leaseExpanded }) {
                    OutlinedTextField(
                        value = selectedLease?.let { "${it.tenantName} (${it.unitNumber})" } ?: "Select Lease",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tenant Lease") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(leaseExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = leaseExpanded, onDismissRequest = { leaseExpanded = false }) {
                        leases.forEach { l ->
                            DropdownMenuItem(
                                text = { Text("${l.tenantName} • ${l.propertyName} (${l.unitNumber})") },
                                onClick = {
                                    selectedLease = l
                                    amountText = CurrencyUtils.centsToInputString(l.rentCents)
                                    leaseExpanded = false
                                }
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = methodExpanded, onExpandedChange = { methodExpanded = !methodExpanded }) {
                    OutlinedTextField(
                        value = method.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Payment Method") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(methodExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = methodExpanded, onDismissRequest = { methodExpanded = false }) {
                        PaymentMethod.entries.forEach { m ->
                            DropdownMenuItem(text = { Text(m.label) }, onClick = { method = m; methodExpanded = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = refCode,
                    onValueChange = { refCode = it; err = null },
                    label = { Text("Reference / Receipt Code (4–30 chars)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; err = null },
                    label = { Text("Amount (KES)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = verifyNow, onCheckedChange = { verifyNow = it })
                    Text("Verify & allocate to invoices immediately", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lease = selectedLease
                    val amount = CurrencyUtils.parseKesToCents(amountText)
                    if (lease == null) err = "Select a lease"
                    else if (!CurrencyUtils.isValidMpesaReference(refCode)) err = "Enter a 4–30 character alphanumeric/hyphen reference code"
                    else if (amount == null || amount <= 0L) err = "Enter valid amount greater than 0"
                    else onSubmit(lease, refCode.trim().uppercase(), amount, method, verifyNow)
                },
                enabled = !isBusy
            ) { Text("Save Payment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordExpenseDialog(
    properties: List<Property>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Property, ExpenseCategory, String, String, Long, String?) -> Unit
) {
    var selectedProp by remember { mutableStateOf(properties.firstOrNull()) }
    var propExpanded by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(ExpenseCategory.MAINTENANCE) }
    var catExpanded by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var docRef by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Property Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (!err.isNullOrBlank()) ErrorBannerCard(err!!)
                ExposedDropdownMenuBox(expanded = propExpanded, onExpandedChange = { propExpanded = !propExpanded }) {
                    OutlinedTextField(
                        value = selectedProp?.name ?: "Select Property",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Property") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(propExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = propExpanded, onDismissRequest = { propExpanded = false }) {
                        properties.forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, onClick = { selectedProp = p; propExpanded = false })
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = !catExpanded }) {
                    OutlinedTextField(
                        value = category.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Expense Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(catExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                        ExpenseCategory.entries.forEach { c ->
                            DropdownMenuItem(text = { Text(c.label) }, onClick = { category = c; catExpanded = false })
                        }
                    }
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Expense Title") }, singleLine = true)
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Amount (KES)") }, singleLine = true)
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Notes / Vendor Details") }, maxLines = 2)
                OutlinedTextField(value = docRef, onValueChange = { docRef = it }, label = { Text("Receipt / Ref Number (Optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val prop = selectedProp
                    val amount = CurrencyUtils.parseKesToCents(amountText)
                    if (prop == null) err = "Select a property"
                    else if (title.trim().length < 2) err = "Enter expense title"
                    else if (amount == null || amount <= 0L) err = "Enter valid KES amount"
                    else onSubmit(prop, category, title, desc, amount, docRef.ifBlank { null })
                },
                enabled = !isBusy
            ) { Text("Log Expense") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
