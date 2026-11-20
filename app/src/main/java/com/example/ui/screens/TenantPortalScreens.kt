package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.MpesaSmsParser
import com.example.domain.finance.RentReminderEngine
import com.example.domain.finance.RentReminderUrgency
import com.example.domain.finance.ReportExporter
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.Lease
import com.example.domain.model.MaintenanceRequest
import com.example.domain.model.MaintenanceStatus
import com.example.domain.model.Payment
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.RentalUnit
import com.example.domain.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.PillTone
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.NavySecondary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoBg
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import com.example.ui.theme.TabularMoneyStyle
import com.example.ui.viewmodel.TenantViewModel
import com.example.ui.viewmodel.UiState
import java.util.UUID

@Composable
fun TenantDashboardScreen(
    viewModel: TenantViewModel,
    currentUser: UserProfile,
    onNavigatePayRent: () -> Unit,
    onNavigateMaintenance: () -> Unit,
    onNavigateDiscovery: () -> Unit
) {
    val context = LocalContext.current
    val activeLease by viewModel.activeLease.collectAsStateWithLifecycle()
    val accountSummary by viewModel.accountSummary.collectAsStateWithLifecycle()
    val invoicesState by viewModel.invoices.collectAsStateWithLifecycle()
    val paymentsState by viewModel.payments.collectAsStateWithLifecycle()
    val maintenanceState by viewModel.maintenanceRequests.collectAsStateWithLifecycle()
    val announcementsState by viewModel.announcements.collectAsStateWithLifecycle()

    val invoices = (invoicesState as? UiState.Success)?.data ?: emptyList()
    val payments = (paymentsState as? UiState.Success)?.data ?: emptyList()
    val tickets = (maintenanceState as? UiState.Success)?.data ?: emptyList()
    val announcements = (announcementsState as? UiState.Success)?.data ?: emptyList()
    val dueReminders = remember(invoices) {
        RentReminderEngine.evaluateInvoices(invoices, respectCooldown = false)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            val statusTone = when (accountSummary.paymentStatusLabel) {
                "Settled" -> PillTone.SUCCESS
                "Overdue" -> PillTone.ERROR
                "Verification Pending", "Partially Paid" -> PillTone.WARNING
                else -> PillTone.INFO
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(Brush.horizontalGradient(listOf(NavyPrimary, NavySecondary)))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "Rent Account",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFFCCFBF1)
                            )
                            Text(
                                text = activeLease?.let { "${it.propertyName} • Unit ${it.unitNumber}" }
                                    ?: "No Active Lease Linked",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                        if (activeLease != null) {
                            StatusPill(accountSummary.paymentStatusLabel, statusTone)
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text("Outstanding Balance", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                            Text(
                                text = CurrencyUtils.formatKes(accountSummary.totalOutstandingCents, true),
                                style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize),
                                color = Color.White
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Monthly Rent", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                            Text(
                                text = CurrencyUtils.formatKes(accountSummary.monthlyRentCents),
                                style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                                color = Color(0xFFCCFBF1)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Paid this cycle: ${CurrencyUtils.formatKes(accountSummary.amountPaidThisPeriodCents)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE2E8F0)
                        )
                        Text(
                            text = "Credit: ${CurrencyUtils.formatKes(accountSummary.accountCreditCents)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5EEAD4)
                        )
                    }
                    if (activeLease != null) {
                        Text(
                            text = "Next Due Date: ${CurrencyUtils.formatDate(accountSummary.nextDueDateEpochMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }
        }

        if (dueReminders.isNotEmpty() && activeLease != null) {
            val topReminder = dueReminders.first()
            val isOverdue = topReminder.urgency == RentReminderUrgency.OVERDUE
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOverdue) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                        } else {
                            StatusWarningBg
                        }
                    ),
                    border = BorderStroke(1.dp, if (isOverdue) StatusError.copy(alpha = 0.5f) else StatusWarning.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tenant_rent_reminder_banner")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = if (isOverdue) StatusError else StatusWarning
                            )
                            Text(
                                text = topReminder.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isOverdue) StatusError else StatusWarning,
                                modifier = Modifier.weight(1f)
                            )
                            StatusPill(
                                label = topReminder.urgency.label,
                                tone = if (isOverdue) PillTone.ERROR else PillTone.WARNING
                            )
                        }
                        Text(
                            text = topReminder.body,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = onNavigatePayRent,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isOverdue) StatusError else MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text("Pay Now via M-Pesa")
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onNavigatePayRent,
                    enabled = activeLease != null,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("tenant_quick_pay_button")
                ) {
                    Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pay Rent")
                }
                OutlinedButton(
                    onClick = onNavigateMaintenance,
                    enabled = activeLease != null,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("tenant_quick_ticket_button")
                ) {
                    Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Repairs")
                }
            }
        }

        if (activeLease == null) {
            item {
                EmptyStateCard(
                    title = "Welcome to Your Tenant Home",
                    message = "You don't have an active tenancy assigned yet. Browse published Kenyan rentals to apply, or share your RentFlow User ID (${currentUser.userId}) from your Profile with your landlord.",
                    ctaLabel = "Browse Available Units",
                    onCtaClick = onNavigateDiscovery
                )
            }
        } else {
            val lease = activeLease!!
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Home, contentDescription = null, tint = EmeraldAccent)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${lease.propertyName} • Unit ${lease.unitNumber}", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Term: ${CurrencyUtils.formatDate(lease.startDateEpochMs)} – ${CurrencyUtils.formatDate(lease.endDateEpochMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(lease.statusEnum().label, PillTone.SUCCESS)
                        }
                        Text(
                            "Deposit held: ${CurrencyUtils.formatKes(lease.depositCents, true)} • Rent due Day ${lease.dueDayOfMonth}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                ReportExporter.exportPdfDocument(
                                    context = context,
                                    fileName = "tenancy_agreement_${lease.unitNumber}.pdf",
                                    title = "RentFlow Tenancy Agreement",
                                    subtitle = "${lease.propertyName} • Unit ${lease.unitNumber}",
                                    lines = listOf(
                                        "[H]TENANCY DETAILS",
                                        "Tenant: ${lease.tenantName} (${lease.tenantPhone})",
                                        "Property & Unit: ${lease.propertyName} - Unit ${lease.unitNumber}",
                                        "Lease Term: ${CurrencyUtils.formatDate(lease.startDateEpochMs)} to ${CurrencyUtils.formatDate(lease.endDateEpochMs)}",
                                        "Status: ${lease.statusEnum().label}",
                                        "---",
                                        "[H]FINANCIAL TERMS (KES)",
                                        "Monthly Rent: ${CurrencyUtils.formatKes(lease.rentCents, true)}",
                                        "Security Deposit: ${CurrencyUtils.formatKes(lease.depositCents, true)}",
                                        "Due Day: Day ${lease.dueDayOfMonth} of every month",
                                        "Account Credit: ${CurrencyUtils.formatKes(lease.creditBalanceCents, true)}",
                                        "---",
                                        "[H]TERMS SUMMARY",
                                        lease.termsSummary.ifBlank { "Standard Kenyan residential tenancy terms apply." }
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download Tenancy Agreement (PDF)")
                        }
                    }
                }
            }
        }

        if (announcements.isNotEmpty()) {
            item { SectionHeader("Property Announcements") }
            items(announcements.take(3), key = { it.id }) { ann ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, StatusInfo.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Campaign, contentDescription = null, tint = StatusInfo)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(ann.title, style = MaterialTheme.typography.titleSmall)
                            Text(ann.body, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${ann.propertyName} • ${ann.authorName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (payments.isNotEmpty()) {
            item { SectionHeader("Recent Rent Payments", "View All", onNavigatePayRent) }
            items(payments.take(3), key = { it.id }) { pay ->
                TenantPaymentCard(pay = pay, onDownloadReceipt = {
                    exportPaymentReceiptPdf(context, pay)
                })
            }
        }

        if (tickets.isNotEmpty()) {
            item { SectionHeader("Active Maintenance Requests", "All Tickets", onNavigateMaintenance) }
            items(tickets.take(3), key = { it.id }) { req ->
                MaintenanceCard(req = req)
            }
        }

        item {
            OutlinedButton(
                onClick = onNavigateDiscovery,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Browse Other Kenyan Rentals")
            }
        }
    }
}

private enum class TenantInvoiceFilter(val label: String) {
    ALL("All"),
    DUE("Balance Due"),
    OVERDUE("Overdue"),
    PAID("Paid")
}

private enum class TenantPaymentFilter(val label: String) {
    ALL("All"),
    PENDING("Pending"),
    VERIFIED("Verified"),
    DECLINED("Declined")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantPaymentsScreen(
    viewModel: TenantViewModel,
    currentUser: UserProfile
) {
    val context = LocalContext.current
    val activeLease by viewModel.activeLease.collectAsStateWithLifecycle()
    val activeUnit by viewModel.activeUnit.collectAsStateWithLifecycle()
    val invoicesState by viewModel.invoices.collectAsStateWithLifecycle()
    val paymentsState by viewModel.payments.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    val invoices = (invoicesState as? UiState.Success)?.data ?: emptyList()
    val payments = (paymentsState as? UiState.Success)?.data ?: emptyList()

    var selectedTab by remember { mutableIntStateOf(0) }
    var invoiceFilter by remember { mutableStateOf(TenantInvoiceFilter.ALL) }
    var paymentFilter by remember { mutableStateOf(TenantPaymentFilter.ALL) }
    var expandedInvoiceIds by remember { mutableStateOf(emptySet<String>()) }
    var paymentTargetInvoice by remember { mutableStateOf<Invoice?>(null) }
    var showPayDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Invoices (${invoices.size})") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Payments (${payments.size})") })
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            activeLease?.let { lease ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                paymentTargetInvoice = invoices.firstOrNull { it.outstandingCents > 0L }
                                showPayDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("submit_rent_payment_button")
                        ) {
                            Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pay Rent / M-Pesa")
                        }
                        OutlinedButton(
                            onClick = {
                                ReportExporter.exportTenantStatementCsv(context, lease, invoices, payments)
                            },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("tenant_export_csv_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Statement")
                        }
                    }
                }
                if (activeUnit?.mpesaPaybill != null || activeUnit?.mpesaTill != null) {
                    item {
                        MpesaInstructionsCard(
                            paybill = activeUnit?.mpesaPaybill,
                            till = activeUnit?.mpesaTill,
                            accountReference = MpesaSmsParser.buildMpesaAccountReference(
                                prefix = activeUnit?.mpesaAccountPrefix,
                                unitNumber = lease.unitNumber
                            )
                        )
                    }
                }
            }

            if (selectedTab == 0) {
                when (invoicesState) {
                    is UiState.Loading -> item { LoadingStateBox("Loading your invoices...") }
                    is UiState.Error -> item { ErrorBannerCard((invoicesState as UiState.Error).message) }
                    is UiState.Success -> {
                        if (invoices.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = "No Invoices Yet",
                                    message = "When your landlord generates monthly KES invoices for your lease, they will appear here with exact due dates."
                                )
                            }
                        } else {
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(TenantInvoiceFilter.entries) { filter ->
                                        val count = when (filter) {
                                            TenantInvoiceFilter.ALL -> invoices.size
                                            TenantInvoiceFilter.DUE -> invoices.count { it.outstandingCents > 0L }
                                            TenantInvoiceFilter.OVERDUE -> invoices.count { it.statusEnum() == InvoiceStatus.OVERDUE }
                                            TenantInvoiceFilter.PAID -> invoices.count { it.statusEnum() == InvoiceStatus.PAID }
                                        }
                                        FilterChip(
                                            selected = invoiceFilter == filter,
                                            onClick = { invoiceFilter = filter },
                                            label = { Text("${filter.label} ($count)") },
                                            modifier = Modifier.testTag("tenant_invoice_filter_${filter.name.lowercase()}")
                                        )
                                    }
                                }
                            }

                            val filteredInvoices = when (invoiceFilter) {
                                TenantInvoiceFilter.ALL -> invoices
                                TenantInvoiceFilter.DUE -> invoices.filter { it.outstandingCents > 0L }
                                TenantInvoiceFilter.OVERDUE -> invoices.filter { it.statusEnum() == InvoiceStatus.OVERDUE }
                                TenantInvoiceFilter.PAID -> invoices.filter { it.statusEnum() == InvoiceStatus.PAID }
                            }

                            if (filteredInvoices.isEmpty()) {
                                item {
                                    EmptyStateCard(
                                        title = "No Matching Invoices",
                                        message = "No invoices match '${invoiceFilter.label}'.",
                                        ctaLabel = "Show All",
                                        onCtaClick = { invoiceFilter = TenantInvoiceFilter.ALL },
                                        showIllustration = false
                                    )
                                }
                            } else {
                                items(filteredInvoices, key = { it.id }) { inv ->
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
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateContentSize()
                                            .clickable {
                                                expandedInvoiceIds = if (expanded) expandedInvoiceIds - inv.id else expandedInvoiceIds + inv.id
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("${inv.invoiceNumber} • ${CurrencyUtils.billingPeriodLabel(inv.billingPeriod)}", style = MaterialTheme.typography.titleSmall)
                                                    Text("Due: ${CurrencyUtils.formatDate(inv.dueDateEpochMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                StatusPill(inv.statusEnum().label, tone)
                                            }
                                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                Text("Total: ${CurrencyUtils.formatKes(inv.totalDueCents, true)}", style = MaterialTheme.typography.bodySmall)
                                                Text("Paid: ${CurrencyUtils.formatKes(inv.paidCents, true)}", style = MaterialTheme.typography.bodySmall, color = StatusSuccess)
                                                Text(
                                                    "Bal: ${CurrencyUtils.formatKes(inv.outstandingCents, true)}",
                                                    style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                                                    color = if (inv.outstandingCents > 0L) StatusError else StatusSuccess
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.End,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    if (expanded) "Hide KES Breakdown" else "View KES Breakdown",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                                Icon(
                                                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                    contentDescription = if (expanded) "Hide breakdown" else "View breakdown",
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            if (expanded) {
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    TenantBreakdownRow("Monthly Rent", CurrencyUtils.formatKes(inv.rentCents, true))
                                                    if (inv.extraChargesCents > 0L || !inv.extraChargesNote.isNullOrBlank()) {
                                                        val label = inv.extraChargesNote?.takeIf { it.isNotBlank() }?.let { "Extra Charges ($it)" } ?: "Extra Charges"
                                                        TenantBreakdownRow(label, CurrencyUtils.formatKes(inv.extraChargesCents, true))
                                                    }
                                                    if (inv.previousBalanceCents > 0L) {
                                                        TenantBreakdownRow("Prior Arrears", CurrencyUtils.formatKes(inv.previousBalanceCents, true))
                                                    }
                                                    TenantBreakdownRow("Paid / Credit Applied", "- ${CurrencyUtils.formatKes(inv.paidCents, true)}")
                                                    TenantBreakdownRow("Remaining Balance", CurrencyUtils.formatKes(inv.outstandingCents, true), isError = inv.outstandingCents > 0L)
                                                }
                                            }
                                            if (inv.outstandingCents > 0L && activeLease != null) {
                                                OutlinedButton(
                                                    onClick = {
                                                        paymentTargetInvoice = inv
                                                        showPayDialog = true
                                                    },
                                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                                ) {
                                                    Text("Pay This Invoice")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                when (paymentsState) {
                    is UiState.Loading -> item { LoadingStateBox("Loading payment history...") }
                    is UiState.Error -> item { ErrorBannerCard((paymentsState as UiState.Error).message) }
                    is UiState.Success -> {
                        if (payments.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = "No Payments Submitted Yet",
                                    message = "Submit an M-Pesa/bank reference or run the simulated M-Pesa demo checkout to settle your rent."
                                )
                            }
                        } else {
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(TenantPaymentFilter.entries) { filter ->
                                        val count = when (filter) {
                                            TenantPaymentFilter.ALL -> payments.size
                                            TenantPaymentFilter.PENDING -> payments.count { it.statusEnum() == PaymentStatus.PENDING_VERIFICATION }
                                            TenantPaymentFilter.VERIFIED -> payments.count { it.statusEnum() == PaymentStatus.VERIFIED }
                                            TenantPaymentFilter.DECLINED -> payments.count {
                                                it.statusEnum() == PaymentStatus.REJECTED || it.statusEnum() == PaymentStatus.REVERSED
                                            }
                                        }
                                        FilterChip(
                                            selected = paymentFilter == filter,
                                            onClick = { paymentFilter = filter },
                                            label = { Text("${filter.label} ($count)") },
                                            modifier = Modifier.testTag("tenant_payment_filter_${filter.name.lowercase()}")
                                        )
                                    }
                                }
                            }

                            val filteredPayments = when (paymentFilter) {
                                TenantPaymentFilter.ALL -> payments
                                TenantPaymentFilter.PENDING -> payments.filter { it.statusEnum() == PaymentStatus.PENDING_VERIFICATION }
                                TenantPaymentFilter.VERIFIED -> payments.filter { it.statusEnum() == PaymentStatus.VERIFIED }
                                TenantPaymentFilter.DECLINED -> payments.filter {
                                    it.statusEnum() == PaymentStatus.REJECTED || it.statusEnum() == PaymentStatus.REVERSED
                                }
                            }

                            if (filteredPayments.isEmpty()) {
                                item {
                                    EmptyStateCard(
                                        title = "No Matching Payments",
                                        message = "No payments match '${paymentFilter.label}'.",
                                        ctaLabel = "Show All",
                                        onCtaClick = { paymentFilter = TenantPaymentFilter.ALL },
                                        showIllustration = false
                                    )
                                }
                            } else {
                                items(filteredPayments, key = { it.id }) { pay ->
                                    TenantPaymentCard(pay = pay, onDownloadReceipt = {
                                        exportPaymentReceiptPdf(context, pay)
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPayDialog && activeLease != null) {
        TenantPaymentSubmissionDialog(
            lease = activeLease!!,
            unit = activeUnit,
            targetInvoice = paymentTargetInvoice,
            defaultPhone = currentUser.phoneNumber ?: activeLease!!.tenantPhone,
            isBusy = busy,
            onDismiss = { showPayDialog = false },
            onSubmit = { invoiceId, refCode, amountCents, method, simulated ->
                viewModel.submitPayment(
                    lease = activeLease!!,
                    invoiceId = invoiceId,
                    referenceCode = refCode,
                    amountCents = amountCents,
                    method = method,
                    simulatedCheckout = simulated
                ) {
                    showPayDialog = false
                }
            }
        )
    }
}

@Composable
fun MpesaInstructionsCard(
    paybill: String?,
    till: String?,
    accountReference: String
) {
    val context = LocalContext.current
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mpesa_instructions_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("M-Pesa Rent Payment Details", style = MaterialTheme.typography.titleSmall)
            if (!paybill.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Paybill: $paybill • Account: $accountReference", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("M-Pesa Paybill", "$paybill / $accountReference"))
                            Toast.makeText(context, "Paybill & account copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Paybill", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy")
                    }
                }
            }
            if (!till.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Buy Goods Till: $till", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("M-Pesa Till", till))
                            Toast.makeText(context, "Till number copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Till", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy")
                    }
                }
            }
        }
    }
}

@Composable
private fun TenantBreakdownRow(label: String, value: String, isError: Boolean = false) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            value,
            style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
            color = if (isError) StatusError else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TenantPaymentCard(pay: Payment, onDownloadReceipt: () -> Unit) {
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
                        CurrencyUtils.formatKes(pay.amountCents, true),
                        style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize)
                    )
                    Text(
                        "${pay.methodEnum().label} • Ref: ${pay.referenceCode}${if (pay.simulatedCheckout) " (Simulated Demo)" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        CurrencyUtils.formatDateTime(pay.paymentDateEpochMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(pay.statusEnum().label, tone)
            }
            if (!pay.allocationsSummary.isNullOrBlank()) {
                Text("Allocated: ${pay.allocationsSummary}", style = MaterialTheme.typography.labelSmall, color = StatusSuccess)
            }
            if (!pay.reversalReason.isNullOrBlank()) {
                Text("Reversal note: ${pay.reversalReason}", style = MaterialTheme.typography.labelSmall, color = StatusError)
            }
            if (pay.statusEnum() == PaymentStatus.VERIFIED) {
                OutlinedButton(
                    onClick = onDownloadReceipt,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                ) {
                    Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Download Official Receipt (PDF)")
                }
            }
        }
    }
}

private fun exportPaymentReceiptPdf(context: android.content.Context, pay: Payment) {
    ReportExporter.exportPdfDocument(
        context = context,
        fileName = "receipt_${pay.receiptNumber ?: pay.referenceCode}.pdf",
        title = "RentFlow Payment Receipt",
        subtitle = "Receipt #${pay.receiptNumber ?: pay.id.take(8)} • ${CurrencyUtils.formatDateTime(pay.paymentDateEpochMs)}",
        lines = listOf(
            "[H]PAYMENT CONFIRMATION",
            "Tenant: ${pay.tenantName}",
            "Property & Unit: ${pay.propertyName} • Unit ${pay.unitNumber}",
            "Amount Paid: ${CurrencyUtils.formatKes(pay.amountCents, true)}",
            "Method: ${pay.methodEnum().label}${if (pay.simulatedCheckout) " (Simulated Checkout)" else ""}",
            "Transaction Reference: ${pay.referenceCode}",
            "Status: ${pay.statusEnum().label}",
            "---",
            "[H]INVOICE ALLOCATION",
            pay.allocationsSummary ?: "Credited to lease ledger",
            "Verification Notes: ${pay.verificationNotes ?: "Verified"}"
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TenantPaymentSubmissionDialog(
    lease: Lease,
    unit: RentalUnit? = null,
    targetInvoice: Invoice?,
    defaultPhone: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (invoiceId: String?, refCode: String, amountCents: Long, method: PaymentMethod, simulated: Boolean) -> Unit
) {
    var mode by remember { mutableIntStateOf(0) } // 0 = Manual Reference, 1 = Simulated M-Pesa STK
    var method by remember { mutableStateOf(PaymentMethod.MPESA) }
    var methodExpanded by remember { mutableStateOf(false) }
    var smsPasteText by remember { mutableStateOf("") }
    var smsParsedStatus by remember { mutableStateOf<String?>(null) }
    var refCode by remember { mutableStateOf("") }
    var mpesaPhone by remember { mutableStateOf(defaultPhone) }
    val defaultAmount = targetInvoice?.outstandingCents?.takeIf { it > 0L } ?: lease.rentCents
    var amountText by remember { mutableStateOf(CurrencyUtils.centsToInputString(defaultAmount)) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Submit Rent Payment") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = mode == 0,
                        onClick = { mode = 0; err = null },
                        label = { Text("Manual Reference") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = mode == 1,
                        onClick = { mode = 1; err = null },
                        label = { Text("Simulated M-Pesa") },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (unit?.mpesaPaybill != null || unit?.mpesaTill != null) {
                    MpesaInstructionsCard(
                        paybill = unit.mpesaPaybill,
                        till = unit.mpesaTill,
                        accountReference = MpesaSmsParser.buildMpesaAccountReference(
                            prefix = unit.mpesaAccountPrefix,
                            unitNumber = lease.unitNumber
                        )
                    )
                }

                if (!err.isNullOrBlank()) ErrorBannerCard(err!!)

                if (mode == 0) {
                    OutlinedTextField(
                        value = smsPasteText,
                        onValueChange = { pasted ->
                            smsPasteText = pasted
                            val parsed = MpesaSmsParser.parse(pasted)
                            if (parsed != null) {
                                refCode = parsed.transactionCode
                                amountText = CurrencyUtils.centsToInputString(parsed.amountCents)
                                method = PaymentMethod.MPESA
                                smsParsedStatus = "Detected ${parsed.transactionCode} • ${CurrencyUtils.formatKes(parsed.amountCents, true)}"
                                err = null
                            } else {
                                smsParsedStatus = null
                            }
                        },
                        leadingIcon = { Icon(Icons.Filled.Sms, contentDescription = null) },
                        label = { Text("Paste M-Pesa Confirmation SMS (Optional)") },
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mpesa_sms_paste_input")
                    )
                    if (!smsParsedStatus.isNullOrBlank()) {
                        Surface(
                            color = StatusInfoBg,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = smsParsedStatus!!,
                                style = MaterialTheme.typography.labelMedium,
                                color = StatusInfo,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("mpesa_sms_parsed_status")
                            )
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
                        label = { Text("Transaction Code (e.g. UBK7X92M10)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("payment_reference_input")
                    )
                } else {
                    Surface(
                        color = StatusWarningBg,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = StatusWarning, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Simulated M-Pesa Demo Checkout — Not a live Daraja transaction. Generates a test reference for landlord verification.",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusWarning
                            )
                        }
                    }
                    OutlinedTextField(
                        value = mpesaPhone,
                        onValueChange = { mpesaPhone = it; err = null },
                        label = { Text("M-Pesa Phone (+2547... or 07...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (targetInvoice != null && targetInvoice.outstandingCents > 0L) {
                        FilterChip(
                            selected = amountText == CurrencyUtils.centsToInputString(targetInvoice.outstandingCents),
                            onClick = { amountText = CurrencyUtils.centsToInputString(targetInvoice.outstandingCents); err = null },
                            label = { Text("Full Balance (${CurrencyUtils.formatKes(targetInvoice.outstandingCents)})") }
                        )
                    }
                    FilterChip(
                        selected = amountText == CurrencyUtils.centsToInputString(lease.rentCents),
                        onClick = { amountText = CurrencyUtils.centsToInputString(lease.rentCents); err = null },
                        label = { Text("Monthly Rent (${CurrencyUtils.formatKes(lease.rentCents)})") }
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; err = null },
                    label = { Text("Amount (KES)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("payment_amount_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = CurrencyUtils.parseKesToCents(amountText)
                    if (amount == null || amount <= 0L) {
                        err = "Enter a valid KES amount greater than 0"
                    } else if (mode == 0) {
                        if (!CurrencyUtils.isValidMpesaReference(refCode)) {
                            err = "Enter a 4–30 character alphanumeric/hyphen transaction code"
                        } else {
                            onSubmit(targetInvoice?.id, refCode.trim().uppercase(), amount, method, false)
                        }
                    } else {
                        if (!CurrencyUtils.isValidKenyanPhone(mpesaPhone)) {
                            err = "Enter a valid Kenyan M-Pesa phone number (+2547... or 07...)"
                        } else {
                            mpesaPhone = CurrencyUtils.normalizeKenyanPhone(mpesaPhone)
                            val simCode = "SIM-${UUID.randomUUID().toString().take(8).uppercase()}"
                            onSubmit(targetInvoice?.id, simCode, amount, PaymentMethod.MPESA, true)
                        }
                    }
                },
                enabled = !isBusy
            ) {
                Text(if (mode == 1) "Send Simulated Prompt" else "Submit Reference")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TenantMaintenanceScreen(
    viewModel: TenantViewModel,
    currentUser: UserProfile = UserProfile()
) {
    val activeLease by viewModel.activeLease.collectAsStateWithLifecycle()
    val linkedLeases by viewModel.linkedLeases.collectAsStateWithLifecycle()
    val maintenanceState by viewModel.maintenanceRequests.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    val unitTargets = remember(linkedLeases, activeLease) {
        val base = linkedLeases.ifEmpty { listOfNotNull(activeLease) }
        base.filter { it.unitId.isNotBlank() }
            .distinctBy { it.unitId }
            .map { MaintenanceUnitTarget.fromLease(it) }
    }

    var selectedFilter by remember { mutableStateOf(MaintenanceTicketFilter.ALL) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var followUpRequest by remember { mutableStateOf<MaintenanceRequest?>(null) }
    var lightboxState by remember { mutableStateOf<PhotoLightboxState?>(null) }

    Scaffold(
        floatingActionButton = {
            if (unitTargets.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_maintenance_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Submit Maintenance Request")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            item {
                SectionHeader(
                    title = "My Maintenance Requests",
                    actionLabel = if (unitTargets.isNotEmpty()) "New Ticket" else null,
                    onActionClick = if (unitTargets.isNotEmpty()) { { showCreateDialog = true } } else null
                )
            }
            when (val st = maintenanceState) {
                is UiState.Loading -> item { LoadingStateBox("Loading repair tickets...") }
                is UiState.Error -> item { ErrorBannerCard(st.message) }
                is UiState.Success -> {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(MaintenanceTicketFilter.entries) { filter ->
                                val count = st.data.count { filter.matches(it) }
                                FilterChip(
                                    selected = selectedFilter == filter,
                                    onClick = { selectedFilter = filter },
                                    label = { Text("${filter.label} ($count)") },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("maintenance_filter_chip_${filter.name.lowercase()}")
                                )
                            }
                        }
                    }

                    val filtered = st.data.filter { selectedFilter.matches(it) }
                    if (st.data.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No Maintenance Requests",
                                message = if (unitTargets.isNotEmpty()) {
                                    "Need plumbing, electrical, or general repairs? Tap + to submit a ticket with photos linked to your rental unit."
                                } else {
                                    "Link an active tenancy first to submit property maintenance requests."
                                },
                                ctaLabel = if (unitTargets.isNotEmpty()) "Submit Request" else null,
                                onCtaClick = if (unitTargets.isNotEmpty()) { { showCreateDialog = true } } else null
                            )
                        }
                    } else if (filtered.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No ${selectedFilter.label} Tickets",
                                message = "No maintenance requests match this status filter.",
                                ctaLabel = "Show All Tickets",
                                onCtaClick = { selectedFilter = MaintenanceTicketFilter.ALL },
                                showIllustration = false
                            )
                        }
                    } else {
                        items(filtered, key = { it.id }) { req ->
                            val isOpenTicket = req.statusEnum() !in listOf(
                                MaintenanceStatus.COMPLETED,
                                MaintenanceStatus.REJECTED
                            )
                            EnhancedMaintenanceCard(
                                req = req,
                                onPhotoClick = { idx ->
                                    lightboxState = PhotoLightboxState(
                                        title = req.title,
                                        imageRefs = req.imageRefs,
                                        initialIndex = idx
                                    )
                                },
                                actions = if (isOpenTicket) {
                                    {
                                        OutlinedButton(
                                            onClick = { followUpRequest = req },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .testTag("followup_ticket_button_${req.id}")
                                        ) {
                                            Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Add Follow-Up / Photos")
                                        }
                                    }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog && unitTargets.isNotEmpty()) {
        MaintenanceSubmissionScreenDialog(
            unitTargets = unitTargets,
            initialUnitId = activeLease?.unitId,
            isBusy = busy,
            onDismiss = { showCreateDialog = false },
            onSubmit = { target, title, desc, cat, prio, imgs ->
                val submitterName = currentUser.fullName.ifBlank { target.lease?.tenantName ?: "Tenant" }
                viewModel.submitMaintenanceForUnit(
                    unitId = target.unitId,
                    lease = target.lease,
                    title = title,
                    description = desc,
                    category = cat,
                    priority = prio,
                    imageRefs = imgs,
                    submitterName = submitterName
                ) {
                    showCreateDialog = false
                }
            }
        )
    }

    followUpRequest?.let { req ->
        TenantFollowUpDialog(
            request = req,
            isBusy = busy,
            onDismiss = { followUpRequest = null },
            onSubmit = { note, updatedImages ->
                viewModel.appendMaintenanceFollowUp(
                    request = req,
                    appendedNote = note,
                    updatedImageRefs = updatedImages,
                    actorName = currentUser.fullName.ifBlank { req.tenantName }
                ) {
                    followUpRequest = null
                }
            }
        )
    }

    lightboxState?.let { lb ->
        PhotoLightboxDialog(
            state = lb,
            onDismiss = { lightboxState = null }
        )
    }
}

@Composable
fun MaintenanceCard(req: MaintenanceRequest) {
    EnhancedMaintenanceCard(req = req)
}
