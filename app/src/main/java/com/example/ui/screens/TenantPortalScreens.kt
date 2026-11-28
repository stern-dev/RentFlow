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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import android.graphics.Bitmap
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.location.DirectionsLauncher
import com.example.data.notifications.PushDeepLink
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.LeaseAutomationEngine
import com.example.domain.finance.MpesaInstructionChannel
import com.example.domain.finance.MpesaSmsParser
import com.example.domain.finance.ParsedRentQrNotice
import com.example.domain.finance.PaymentFormattingUtils
import com.example.domain.finance.RentQrCodeParser
import com.example.domain.finance.RentReminderEngine
import com.example.domain.finance.RentReminderUrgency
import com.example.domain.finance.ReportExporter
import com.example.data.ai.ScanOcrUiState
import com.example.domain.model.DepositSettlement
import com.example.domain.model.DepositSettlementStatus
import com.example.domain.model.DocumentType
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.Lease
import com.example.domain.model.LeaseStatus
import com.example.domain.model.MaintenanceRequest
import com.example.domain.model.MaintenanceStatus
import com.example.domain.model.MoveInChecklist
import com.example.domain.model.MoveInConditionChecklist
import com.example.domain.model.MoveOutNoticeStatus
import com.example.domain.model.MpesaReceipt
import com.example.domain.model.Payment
import com.example.domain.model.PaymentChannelType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentReceiptPdf
import com.example.domain.model.PaymentStatus
import com.example.domain.model.PaymentVerificationSource
import com.example.domain.model.RenewalOfferStatus
import com.example.domain.model.RentalUnit
import com.example.domain.model.SavedPaymentMethod
import com.example.domain.model.ScannedDocument
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.example.ui.components.BuildingMapPreviewCard
import com.example.ui.components.DepositSettlementSummaryCard
import com.example.ui.components.DocumentScannerModal
import com.example.ui.components.DocumentVaultDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.MoveInChecklistPdfViewerDialog
import com.example.ui.components.MoveInChecklistSummaryCard
import com.example.ui.components.MoveInConditionChecklistFullScreenDialog
import com.example.ui.components.MoveInConditionChecklistSummaryCard
import com.example.ui.components.PaymentHistoryList
import com.example.ui.components.PaymentInstructionCard
import com.example.ui.components.PendingScanPayload
import com.example.ui.components.PillTone
import com.example.ui.components.RentPaymentTrendsCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.TenantDepositSettlementReviewDialog
import com.example.ui.components.TenantDocumentPreviewSheet
import com.example.ui.components.TenantMoveInChecklistReviewDialog
import com.example.ui.components.TenantMoveOutNoticeCard
import com.example.ui.components.TenantMoveOutNoticeDialog
import com.example.ui.components.TenantPreviewDocument
import com.example.ui.components.TenantRenewalOfferCard
import com.example.ui.components.TenantRenewalOfferReviewDialog
import com.example.ui.components.exportDepositSettlementPdf
import com.example.ui.components.exportMoveInChecklistPdf
import com.example.ui.theme.BrushedSilver
import com.example.ui.theme.CobaltLight
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkPrimaryContainer
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.MidnightInk
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.MpesaEmerald
import com.example.ui.theme.MpesaEmeraldBorderDark
import com.example.ui.theme.MpesaEmeraldBorderLight
import com.example.ui.theme.MpesaEmeraldContainerDark
import com.example.ui.theme.MpesaEmeraldContainerLight
import com.example.ui.theme.MpesaEmeraldLight
import com.example.ui.theme.MutedCobalt
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoBg
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusSuccessBgDark
import com.example.ui.theme.StatusSuccessLight
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import com.example.ui.theme.StatusWarningBgDark
import com.example.ui.theme.StatusWarningLight
import com.example.ui.theme.TabularMoneyStyle
import com.example.ui.viewmodel.PaymentMethodViewModel
import com.example.ui.viewmodel.TenantViewModel
import com.example.ui.viewmodel.UiState
import java.util.UUID

@Composable
fun TenantDashboardScreen(
    viewModel: TenantViewModel,
    currentUser: UserProfile,
    onNavigatePayRent: () -> Unit,
    onNavigateMaintenance: () -> Unit,
    onNavigateDiscovery: () -> Unit,
    deepLinkTarget: PushDeepLink? = null,
    onConsumeDeepLinkTarget: () -> Unit = {},
    onNavigatePayInvoice: (String) -> Unit = { onNavigatePayRent() },
    onOpenQrScanner: () -> Unit = {}
) {
    val context = LocalContext.current
    val activeLease by viewModel.activeLease.collectAsStateWithLifecycle()
    val activeUnit by viewModel.activeUnit.collectAsStateWithLifecycle()
    val leasesState by viewModel.leases.collectAsStateWithLifecycle()
    val activeBuildingLocation by viewModel.activeBuildingLocation.collectAsStateWithLifecycle()
    val accountSummary by viewModel.accountSummary.collectAsStateWithLifecycle()
    val paymentTrends by viewModel.paymentTrends.collectAsStateWithLifecycle()
    val invoicesState by viewModel.invoices.collectAsStateWithLifecycle()
    val paymentsState by viewModel.payments.collectAsStateWithLifecycle()
    val maintenanceState by viewModel.maintenanceRequests.collectAsStateWithLifecycle()
    val announcementsState by viewModel.announcements.collectAsStateWithLifecycle()
    val checklistsState by viewModel.moveInChecklists.collectAsStateWithLifecycle()
    val conditionChecklistsState by viewModel.conditionChecklists.collectAsStateWithLifecycle()
    val settlementsState by viewModel.depositSettlements.collectAsStateWithLifecycle()
    val scannedDocsState by viewModel.scannedDocuments.collectAsStateWithLifecycle()
    val paymentReceiptsState by viewModel.paymentReceipts.collectAsStateWithLifecycle()
    val scanOcrState by viewModel.scanOcrState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    val tenantLeases = (leasesState as? UiState.Success)?.data ?: emptyList()
    val invoices = (invoicesState as? UiState.Success)?.data ?: emptyList()
    val payments = (paymentsState as? UiState.Success)?.data ?: emptyList()
    val paymentReceipts = (paymentReceiptsState as? UiState.Success)?.data ?: emptyList()
    val tickets = (maintenanceState as? UiState.Success)?.data ?: emptyList()
    val announcements = (announcementsState as? UiState.Success)?.data ?: emptyList()
    val checklists = (checklistsState as? UiState.Success)?.data ?: emptyList()
    val conditionChecklists = (conditionChecklistsState as? UiState.Success)?.data ?: emptyList()
    val settlements = (settlementsState as? UiState.Success)?.data ?: emptyList()
    val scannedDocuments = (scannedDocsState as? UiState.Success)?.data ?: emptyList()
    val dueReminders = remember(invoices) {
        RentReminderEngine.evaluateInvoices(invoices, respectCooldown = false)
    }

    var checklistForReview by remember { mutableStateOf<MoveInChecklist?>(null) }
    var conditionChecklistForReview by remember { mutableStateOf<MoveInConditionChecklist?>(null) }
    var conditionChecklistForPdfViewer by remember { mutableStateOf<Pair<MoveInConditionChecklist, Lease?>?>(null) }
    var settlementForReview by remember { mutableStateOf<DepositSettlement?>(null) }
    var leaseOfferToReview by remember { mutableStateOf<Lease?>(null) }
    var showMoveOutNoticeDialog by remember { mutableStateOf(false) }
    var showTenantVault by remember { mutableStateOf(false) }
    var showTenantScanner by remember { mutableStateOf(false) }
    var previewDocument by remember { mutableStateOf<TenantPreviewDocument?>(null) }

    LaunchedEffect(
        deepLinkTarget,
        checklists,
        conditionChecklists,
        settlements,
        checklistsState,
        conditionChecklistsState,
        settlementsState
    ) {
        val dl = deepLinkTarget ?: return@LaunchedEffect
        val matchedConditionChecklist = dl.checklistId?.let { cid -> conditionChecklists.firstOrNull { it.id == cid } }
            ?: dl.leaseId?.takeIf { dl.settlementId == null }?.let { lid -> conditionChecklists.firstOrNull { it.leaseId == lid } }
        val matchedChecklist = dl.checklistId?.let { cid -> checklists.firstOrNull { it.id == cid } }
            ?: dl.leaseId?.takeIf { dl.settlementId == null }?.let { lid -> checklists.firstOrNull { it.leaseId == lid } }
        val matchedSettlement = dl.settlementId?.let { sid -> settlements.firstOrNull { it.id == sid } }
            ?: dl.leaseId?.takeIf { dl.checklistId == null }?.let { lid -> settlements.firstOrNull { it.leaseId == lid } }

        if (matchedConditionChecklist != null) {
            conditionChecklistForReview = matchedConditionChecklist
            onConsumeDeepLinkTarget()
        } else if (matchedChecklist != null) {
            checklistForReview = matchedChecklist
            onConsumeDeepLinkTarget()
        } else if (matchedSettlement != null) {
            settlementForReview = matchedSettlement
            onConsumeDeepLinkTarget()
        } else if (
            checklistsState is UiState.Success &&
            conditionChecklistsState is UiState.Success &&
            settlementsState is UiState.Success
        ) {
            onConsumeDeepLinkTarget()
        }
    }

    val activeChecklist = remember(activeLease, checklists) {
        activeLease?.let { lease -> checklists.firstOrNull { it.leaseId == lease.id } } ?: checklists.firstOrNull()
    }
    val activeConditionChecklist = remember(activeLease, conditionChecklists) {
        activeLease?.let { lease -> conditionChecklists.firstOrNull { it.leaseId == lease.id } }
            ?: conditionChecklists.firstOrNull()
    }
    val primarySettlement = remember(activeLease, settlements) {
        settlements.firstOrNull {
            it.statusEnum() in listOf(
                DepositSettlementStatus.ISSUED,
                DepositSettlementStatus.DISPUTED,
                DepositSettlementStatus.APPROVED,
                DepositSettlementStatus.SETTLED
            )
        } ?: activeLease?.let { al -> settlements.firstOrNull { it.leaseId == al.id } }
    }
    val settlementLease = remember(primarySettlement, tenantLeases, activeLease) {
        primarySettlement?.let { s ->
            tenantLeases.firstOrNull { it.id == s.leaseId } ?: activeLease
        } ?: activeLease
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            val isDark = LocalIsDarkTheme.current
            val statusTone = when (accountSummary.paymentStatusLabel) {
                "Settled" -> PillTone.SUCCESS
                "Overdue" -> PillTone.ERROR
                "Verification Pending", "Partially Paid" -> PillTone.WARNING
                else -> PillTone.INFO
            }
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = Color.Transparent,
                border = BorderStroke(1.dp, if (isDark) DarkOutline else BrushedSilver.copy(alpha = 0.24f)),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            if (isDark) listOf(DarkSurfaceVariant, DarkPrimaryContainer)
                            else listOf(MidnightInk, MidnightSlate)
                        ),
                        shape = MaterialTheme.shapes.extraLarge
                    )
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                            Surface(
                                color = MutedCobalt.copy(alpha = 0.34f),
                                border = BorderStroke(1.dp, BrushedSilver.copy(alpha = 0.32f)),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = "RENT ACCOUNT",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = BrushedSilver,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = activeLease?.let { "${it.propertyName} • Unit ${it.unitNumber}" }
                                    ?: "No Active Lease Linked",
                                style = MaterialTheme.typography.titleMedium,
                                color = DarkTextPrimary
                            )
                        }
                        if (activeLease != null) {
                            Spacer(Modifier.width(8.dp))
                            StatusPill(accountSummary.paymentStatusLabel, statusTone)
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text("Outstanding Balance", style = MaterialTheme.typography.labelSmall, color = DarkTextSecondary)
                            Text(
                                text = CurrencyUtils.formatKes(accountSummary.totalOutstandingCents, true),
                                style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize),
                                color = DarkTextPrimary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Monthly Rent", style = MaterialTheme.typography.labelSmall, color = DarkTextSecondary)
                            Text(
                                text = CurrencyUtils.formatKes(accountSummary.monthlyRentCents),
                                style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                                color = CobaltLight
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
                            color = DarkTextPrimary.copy(alpha = 0.85f)
                        )
                        Text(
                            text = "Credit: ${CurrencyUtils.formatKes(accountSummary.accountCreditCents)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrushedSilver
                        )
                    }
                    if (activeLease != null) {
                        Text(
                            text = "Next Due Date: ${CurrencyUtils.formatDate(accountSummary.nextDueDateEpochMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkTextSecondary
                        )
                    }
                }
            }
        }

        if (dueReminders.isNotEmpty() && activeLease != null) {
            val topReminder = dueReminders.first()
            val isOverdue = topReminder.urgency == RentReminderUrgency.OVERDUE
            item {
                val isDark = LocalIsDarkTheme.current
                val warnBg = if (isDark) StatusWarningBgDark else StatusWarningBg
                val warnFg = if (isDark) StatusWarningLight else StatusWarning
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOverdue) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                        } else {
                            warnBg
                        }
                    ),
                    border = BorderStroke(1.dp, if (isOverdue) StatusError.copy(alpha = 0.5f) else warnFg.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                                tint = if (isOverdue) StatusError else warnFg
                            )
                            Text(
                                text = topReminder.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isOverdue) StatusError else warnFg,
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
                            onClick = { onNavigatePayInvoice(topReminder.invoice.id) },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isOverdue) StatusError else MaterialTheme.colorScheme.secondary,
                                contentColor = if (isOverdue) Color.White else MaterialTheme.colorScheme.onSecondary
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
            val isDark = LocalIsDarkTheme.current
            val qrAccent = if (isDark) MpesaEmeraldLight else MpesaEmerald
            val qrContainer = if (isDark) MpesaEmeraldContainerDark else MpesaEmeraldContainerLight
            val qrBorder = if (isDark) MpesaEmeraldBorderDark else MpesaEmeraldBorderLight
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onNavigatePayRent,
                    enabled = activeLease != null,
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("tenant_quick_pay_button")
                ) {
                    Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pay Rent", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onOpenQrScanner,
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = qrContainer,
                        contentColor = qrAccent
                    ),
                    border = BorderStroke(1.dp, qrBorder),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("tenant_quick_scan_qr_button")
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Scan QR", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onNavigateMaintenance,
                    enabled = activeLease != null,
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("tenant_quick_ticket_button")
                ) {
                    Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Repairs", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        item {
            RentPaymentTrendsCard(summary = paymentTrends)
        }

        val pendingOfferLease = activeLease?.takeIf {
            it.statusEnum() == LeaseStatus.ACTIVE && it.renewalOfferStatusEnum() == RenewalOfferStatus.PENDING
        }
        if (pendingOfferLease != null) {
            item(key = "tenant_renewal_offer_banner") {
                TenantRenewalOfferCard(
                    lease = pendingOfferLease,
                    onReviewOffer = { leaseOfferToReview = pendingOfferLease }
                )
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
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Home, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
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
                        val leaseMutuallySigned = lease.isMutuallySigned() || activeConditionChecklist?.isMutuallySigned() == true
                        val leaseLandlordSigned = lease.landlordSignature != null || activeConditionChecklist?.landlordSignature != null
                        if (leaseMutuallySigned) {
                            StatusPill("Lease & Condition Report Mutually Signed", PillTone.SUCCESS)
                        } else if (leaseLandlordSigned) {
                            StatusPill("Awaiting Your Counter-Signature in Move-In Checklist", PillTone.WARNING)
                        }
                        OutlinedButton(
                            onClick = {
                                previewDocument = TenantPreviewDocument.TenancyLease(
                                    lease = lease,
                                    landlordFallbackSignature = activeConditionChecklist?.landlordSignature,
                                    tenantFallbackSignature = activeConditionChecklist?.tenantSignature
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("tenant_preview_lease_button")
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Preview & Download Tenancy Agreement (PDF)")
                        }
                    }
                }
            }

            activeBuildingLocation?.let { buildingLoc ->
                item {
                    BuildingMapPreviewCard(
                        buildingName = lease.propertyName,
                        location = buildingLoc,
                        onDirectionsClick = {
                            DirectionsLauncher.openGoogleMapsDirections(
                                context = context,
                                latitude = buildingLoc.latitude,
                                longitude = buildingLoc.longitude,
                                label = lease.propertyName
                            )
                        },
                        testTag = "tenant_building_location_card"
                    )
                }
            }
        }

        val activeMoveOutLease = activeLease?.takeIf { it.statusEnum() == LeaseStatus.ACTIVE }
        if (activeMoveOutLease != null) {
            item(key = "tenant_move_out_notice_section") {
                TenantMoveOutNoticeCard(
                    lease = activeMoveOutLease,
                    isBusy = busy,
                    onOpenSubmitNotice = { showMoveOutNoticeDialog = true },
                    onWithdrawNotice = {
                        viewModel.withdrawMoveOutNotice(
                            lease = activeMoveOutLease,
                            actorName = currentUser.fullName
                        )
                    }
                )
            }
        }

        if (activeLease != null || activeConditionChecklist != null) {
            item(key = "tenant_condition_checklist_summary") {
                val label = activeLease?.let { "${it.propertyName} • Unit ${it.unitNumber}" }
                    ?: activeConditionChecklist?.let { "${it.propertyName} • Unit ${it.unitNumber}" }
                    ?: "Move-In Room Inspection"
                MoveInConditionChecklistSummaryCard(
                    checklist = activeConditionChecklist,
                    isTenantView = true,
                    propertyUnitLabel = label,
                    onOpenChecklist = {
                        if (activeConditionChecklist != null) {
                            conditionChecklistForReview = activeConditionChecklist
                        }
                    },
                    onExportPdf = { cl ->
                        val linkedLease = tenantLeases.firstOrNull { it.id == cl.leaseId } ?: activeLease
                        conditionChecklistForPdfViewer = cl to linkedLease
                    }
                )
            }
        }

        if (activeChecklist != null) {
            item(key = "tenant_checklist_summary") {
                val label = activeLease?.let { "${it.propertyName} • Unit ${it.unitNumber}" }
                    ?: "${activeChecklist.propertyName} • Unit ${activeChecklist.unitNumber}"
                MoveInChecklistSummaryCard(
                    checklist = activeChecklist,
                    isTenantView = true,
                    propertyUnitLabel = label,
                    onOpenChecklist = {
                        checklistForReview = activeChecklist
                    },
                    onExportPdf = { cl -> exportMoveInChecklistPdf(context, cl) }
                )
            }
        }

        if (settlementLease != null && (primarySettlement != null || activeLease != null)) {
            item(key = "tenant_deposit_settlement_summary") {
                DepositSettlementSummaryCard(
                    settlement = primarySettlement,
                    lease = settlementLease,
                    isTenantView = true,
                    onOpenSettlement = {
                        if (primarySettlement != null) {
                            settlementForReview = primarySettlement
                        }
                    },
                    onExportPdf = { ds -> exportDepositSettlementPdf(context, ds) }
                )
            }
        }

        if (activeLease != null || scannedDocuments.isNotEmpty()) {
            item(key = "tenant_unit_documents_card") {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth().testTag("tenant_documents_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Unit Documents & Leases", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = if (scannedDocuments.isEmpty()) "View shared leases or scan your M-Pesa/bank receipts."
                                    else "${scannedDocuments.size} document(s) in your unit vault",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill("${scannedDocuments.size}", PillTone.INFO)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { showTenantVault = true },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("tenant_open_vault_button")
                            ) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Open Vault", maxLines = 1)
                            }
                            if (activeLease != null && activeUnit != null) {
                                Button(
                                    onClick = { showTenantScanner = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("tenant_scan_receipt_button")
                                ) {
                                    Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Scan Receipt", maxLines = 1)
                                }
                            }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { previewDocument = TenantPreviewDocument.PropertyNotice(ann) }
                        .testTag("tenant_announcement_card_${ann.id}")
                ) {
                    Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Campaign, contentDescription = null, tint = StatusInfo)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                            Text(ann.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = ann.body,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${ann.propertyName} • ${ann.authorName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Visibility,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = stringResource(R.string.doc_preview_tap_to_preview),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }

        if (payments.isNotEmpty()) {
            item { SectionHeader("Recent Rent Payments", "View All", onNavigatePayRent) }
            items(payments.take(3), key = { it.id }) { pay ->
                TenantPaymentCard(pay = pay, onDownloadReceipt = {
                    previewDocument = resolvePaymentReceiptPreview(pay, paymentReceipts, invoices)
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

    checklistForReview?.let { cl ->
        TenantMoveInChecklistReviewDialog(
            checklist = cl,
            isBusy = busy,
            onDismiss = { checklistForReview = null },
            onRespond = { acknowledge, note ->
                viewModel.respondToMoveInChecklist(
                    checklist = cl,
                    acknowledge = acknowledge,
                    responseNote = note,
                    actorName = currentUser.fullName,
                    onSuccess = { checklistForReview = null }
                )
            }
        )
    }

    conditionChecklistForReview?.let { cl ->
        val matchingLease = tenantLeases.firstOrNull { it.id == cl.leaseId } ?: activeLease
        MoveInConditionChecklistFullScreenDialog(
            lease = matchingLease,
            existingChecklist = cl,
            isTenantView = true,
            canDeleteDraft = false,
            isBusy = busy,
            currentUserUid = currentUser.userId,
            currentUserName = currentUser.fullName,
            currentUserRole = UserRole.TENANT,
            onDismiss = { conditionChecklistForReview = null },
            onSubmitTenantReview = { targetChecklist, acknowledge, tenantRemarksByItemId, tenantSummary, tenantSignature, onSuccess ->
                viewModel.submitConditionChecklistReview(
                    checklist = targetChecklist,
                    acknowledge = acknowledge,
                    tenantRemarksByItemId = tenantRemarksByItemId,
                    tenantSummary = tenantSummary,
                    actorName = currentUser.fullName,
                    tenantSignature = tenantSignature,
                    onSuccess = onSuccess
                )
            }
        )
    }

    conditionChecklistForPdfViewer?.let { (cl, linkedLease) ->
        MoveInChecklistPdfViewerDialog(
            checklist = cl,
            lease = linkedLease,
            onDismiss = { conditionChecklistForPdfViewer = null }
        )
    }

    settlementForReview?.let { ds ->
        TenantDepositSettlementReviewDialog(
            settlement = ds,
            isBusy = busy,
            onDismiss = { settlementForReview = null },
            onRespond = { approve, note ->
                viewModel.respondToDepositSettlement(
                    settlement = ds,
                    approve = approve,
                    responseNote = note,
                    actorName = currentUser.fullName,
                    onSuccess = { settlementForReview = null }
                )
            }
        )
    }

    if (showTenantVault) {
        val vaultSubtitle = activeLease?.let { "${it.propertyName} • Unit ${it.unitNumber}" } ?: "My Tenancy Documents"
        DocumentVaultDialog(
            title = "Unit Document Vault",
            subtitle = vaultSubtitle,
            documents = scannedDocuments,
            canUpload = activeLease != null && activeUnit != null,
            canDeleteDoc = { doc -> doc.uploadedByUid == currentUser.userId },
            canToggleTenantVisibility = false,
            isBusy = busy,
            onScanNewDocument = { showTenantScanner = true },
            onToggleTenantVisibility = { _, _ -> },
            onDeleteDocument = { doc -> viewModel.deleteScannedDocument(doc.id) },
            onDismiss = { showTenantVault = false },
            preferPreviewSheet = true
        )
    }

    leaseOfferToReview?.let { offerLease ->
        TenantRenewalOfferReviewDialog(
            lease = offerLease,
            isBusy = busy,
            onDismiss = { leaseOfferToReview = null },
            onRespond = { accept, note ->
                viewModel.respondToRenewalOffer(
                    lease = offerLease,
                    accept = accept,
                    responseNote = note,
                    actorName = currentUser.fullName,
                    onSuccess = { leaseOfferToReview = null }
                )
            }
        )
    }

    if (showMoveOutNoticeDialog && activeLease != null && activeLease?.statusEnum() == LeaseStatus.ACTIVE) {
        val targetLease = activeLease!!
        TenantMoveOutNoticeDialog(
            lease = targetLease,
            isBusy = busy,
            onDismiss = { showMoveOutNoticeDialog = false },
            onSubmit = { requestedMs, reason ->
                viewModel.submitMoveOutNotice(
                    lease = targetLease,
                    requestedMoveOutEpochMs = requestedMs,
                    reason = reason,
                    actorName = currentUser.fullName,
                    onSuccess = { showMoveOutNoticeDialog = false }
                )
            }
        )
    }

    if (showTenantScanner && activeLease != null && activeUnit != null) {
        val lease = activeLease!!
        val unit = activeUnit!!
        DocumentScannerModal(
            unitLabel = "${unit.propertyName} • Unit ${unit.unitNumber}",
            tenantLabel = currentUser.fullName,
            allowedDocumentTypes = listOf(DocumentType.PAYMENT_RECEIPT, DocumentType.OTHER),
            initialDocumentType = DocumentType.PAYMENT_RECEIPT,
            allowTenantVisibilityToggle = false,
            initialTenantVisible = true,
            scanOcrState = scanOcrState,
            onExtractWithAi = { bmp, hint -> viewModel.extractDocumentWithAi(bmp, hint) },
            onClearOcr = { viewModel.clearDocumentOcr() },
            onDismiss = { showTenantScanner = false },
            onConfirmScan = { payload ->
                viewModel.uploadScannedDocument(
                    unit = unit,
                    lease = lease,
                    documentType = payload.documentType,
                    title = payload.title,
                    notes = payload.notes,
                    filterMode = payload.filterMode,
                    pageImageRefs = payload.pageImageRefs,
                    extractedReference = payload.extractedReference,
                    extractedAmountCents = payload.extractedAmountCents,
                    extractedDateText = payload.extractedDateText,
                    extractedSummary = payload.extractedSummary,
                    aiExtracted = payload.aiExtracted,
                    uploaderName = currentUser.fullName
                ) {
                    showTenantScanner = false
                }
            }
        )
    }

    previewDocument?.let { doc ->
        TenantDocumentPreviewSheet(
            document = doc,
            onDismiss = { previewDocument = null }
        )
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
    currentUser: UserProfile,
    paymentMethodViewModel: PaymentMethodViewModel? = null,
    onOpenPaymentMethods: () -> Unit = {},
    detectedMpesaReceipts: List<MpesaReceipt> = emptyList(),
    initialPrefilledReceipt: MpesaReceipt? = null,
    onConsumePrefilledReceipt: () -> Unit = {},
    deepLinkTarget: PushDeepLink? = null,
    onConsumeDeepLinkTarget: () -> Unit = {},
    onReceiptSubmittedForPayment: (receipt: MpesaReceipt, paymentId: String, leaseId: String) -> Unit = { _, _, _ -> },
    onDismissDetectedReceipt: (MpesaReceipt) -> Unit = {},
    onOpenQrScanner: () -> Unit = {},
    initialQrNotice: ParsedRentQrNotice? = null,
    onConsumeQrNotice: () -> Unit = {}
) {
    val context = LocalContext.current
    val activeLease by viewModel.activeLease.collectAsStateWithLifecycle()
    val activeUnit by viewModel.activeUnit.collectAsStateWithLifecycle()
    val linkedLeases by viewModel.linkedLeases.collectAsStateWithLifecycle()
    val linkedUnits by viewModel.linkedUnits.collectAsStateWithLifecycle()
    val invoicesState by viewModel.invoices.collectAsStateWithLifecycle()
    val paymentsState by viewModel.payments.collectAsStateWithLifecycle()
    val paymentHistoryState by viewModel.paymentHistory.collectAsStateWithLifecycle()
    val paymentReceiptsState by viewModel.paymentReceipts.collectAsStateWithLifecycle()
    val paymentReceiptsByInvoiceId by viewModel.paymentReceiptsByInvoiceId.collectAsStateWithLifecycle()
    val scanOcrState by viewModel.scanOcrState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    val savedMethodsState = paymentMethodViewModel?.methods?.collectAsStateWithLifecycle()
    val effectiveSavedMethod = paymentMethodViewModel?.effectiveSelectedMethod?.collectAsStateWithLifecycle()
    val savedMethods = (savedMethodsState?.value as? UiState.Success)?.data ?: emptyList()
    val selectedSavedMethod = effectiveSavedMethod?.value

    val invoices = (invoicesState as? UiState.Success)?.data ?: emptyList()
    val payments = (paymentsState as? UiState.Success)?.data ?: emptyList()
    val paymentReceipts = (paymentReceiptsState as? UiState.Success)?.data ?: emptyList()
    val historyCount = (paymentHistoryState as? UiState.Success)?.data?.entries?.size ?: 0

    var selectedTab by remember { mutableIntStateOf(0) }
    var invoiceFilter by remember { mutableStateOf(TenantInvoiceFilter.ALL) }
    var paymentFilter by remember { mutableStateOf(TenantPaymentFilter.ALL) }
    var expandedInvoiceIds by remember { mutableStateOf(emptySet<String>()) }
    var highlightedInvoiceId by remember { mutableStateOf<String?>(null) }
    var highlightedPaymentId by remember { mutableStateOf<String?>(null) }
    var paymentTargetInvoice by remember { mutableStateOf<Invoice?>(null) }
    var paymentTargetLease by remember { mutableStateOf<Lease?>(null) }
    var paymentTargetUnit by remember { mutableStateOf<RentalUnit?>(null) }
    var activePrefilledReceipt by remember { mutableStateOf<MpesaReceipt?>(null) }
    var activeQrNotice by remember { mutableStateOf<ParsedRentQrNotice?>(null) }
    var activeQrExactLeaseMatch by remember { mutableStateOf(true) }
    var showPayDialog by remember { mutableStateOf(false) }
    var previewDocument by remember { mutableStateOf<TenantPreviewDocument?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(initialPrefilledReceipt) {
        if (initialPrefilledReceipt != null) {
            activePrefilledReceipt = initialPrefilledReceipt
            activeQrNotice = null
            paymentTargetLease = activeLease
            paymentTargetUnit = activeUnit
            paymentTargetInvoice = invoices.firstOrNull { it.outstandingCents > 0L }
            showPayDialog = true
            onConsumePrefilledReceipt()
        }
    }

    LaunchedEffect(initialQrNotice, linkedLeases, linkedUnits, activeLease, activeUnit, invoices) {
        val qrNotice = initialQrNotice ?: return@LaunchedEffect
        val candidateLeases = linkedLeases.ifEmpty { listOfNotNull(activeLease) }
        val candidateUnits = if (activeUnit != null) {
            linkedUnits + (activeUnit!!.id to activeUnit!!)
        } else {
            linkedUnits
        }
        val matched = RentQrCodeParser.matchLeaseAndInvoice(
            notice = qrNotice,
            leases = candidateLeases,
            unitsById = candidateUnits,
            invoices = invoices
        )
        selectedTab = 0
        invoiceFilter = TenantInvoiceFilter.ALL
        activePrefilledReceipt = null
        activeQrNotice = qrNotice
        activeQrExactLeaseMatch = matched.isExactLeaseMatch
        paymentTargetLease = matched.matchedLease ?: activeLease
        paymentTargetUnit = matched.matchedUnit ?: activeUnit
        paymentTargetInvoice = matched.targetInvoice ?: invoices.firstOrNull { it.outstandingCents > 0L }
        matched.targetInvoice?.let { inv ->
            highlightedInvoiceId = inv.id
            highlightedPaymentId = null
            expandedInvoiceIds = expandedInvoiceIds + inv.id
        }
        if ((matched.matchedLease ?: activeLease) != null) {
            showPayDialog = true
        }
        onConsumeQrNotice()
    }

    LaunchedEffect(deepLinkTarget) {
        if (deepLinkTarget != null) {
            val resolvedTab = deepLinkTarget.targetTab ?: when {
                !deepLinkTarget.paymentId.isNullOrBlank() -> 1
                else -> 0
            }
            selectedTab = resolvedTab.coerceIn(0, 2)
            if (!deepLinkTarget.invoiceId.isNullOrBlank()) {
                invoiceFilter = TenantInvoiceFilter.ALL
                highlightedInvoiceId = deepLinkTarget.invoiceId
                highlightedPaymentId = null
                expandedInvoiceIds = expandedInvoiceIds + deepLinkTarget.invoiceId
            } else if (!deepLinkTarget.paymentId.isNullOrBlank()) {
                paymentFilter = TenantPaymentFilter.ALL
                highlightedPaymentId = deepLinkTarget.paymentId
                highlightedInvoiceId = null
            }
            onConsumeDeepLinkTarget()
        }
    }

    LaunchedEffect(highlightedInvoiceId, highlightedPaymentId, selectedTab, invoices, payments, invoiceFilter, paymentFilter) {
        val headerItems = (if (activeLease != null) {
            1 + (if (detectedMpesaReceipts.isNotEmpty()) 1 else 0) +
                (if (paymentMethodViewModel != null) 1 else 0) +
                (if (PaymentFormattingUtils.isValidMpesaShortcode(activeUnit?.mpesaPaybill) ||
                    PaymentFormattingUtils.isValidMpesaShortcode(activeUnit?.mpesaTill)
                ) 1 else 0)
        } else 0) + 1 // +1 for filter row
        if (selectedTab == 0 && !highlightedInvoiceId.isNullOrBlank()) {
            val visibleInvoices = when (invoiceFilter) {
                TenantInvoiceFilter.ALL -> invoices
                TenantInvoiceFilter.DUE -> invoices.filter { it.outstandingCents > 0L }
                TenantInvoiceFilter.OVERDUE -> invoices.filter { it.statusEnum() == InvoiceStatus.OVERDUE }
                TenantInvoiceFilter.PAID -> invoices.filter { it.statusEnum() == InvoiceStatus.PAID }
            }.filter { it.statusEnum() != InvoiceStatus.DRAFT }
            val idx = visibleInvoices.indexOfFirst { it.id == highlightedInvoiceId }
            if (idx >= 0) listState.animateScrollToItem(headerItems + idx)
        } else if (selectedTab == 1 && !highlightedPaymentId.isNullOrBlank()) {
            val visiblePayments = when (paymentFilter) {
                TenantPaymentFilter.ALL -> payments
                TenantPaymentFilter.VERIFIED -> payments.filter { it.statusEnum() == PaymentStatus.VERIFIED }
                TenantPaymentFilter.PENDING -> payments.filter { it.statusEnum() == PaymentStatus.PENDING_VERIFICATION }
                TenantPaymentFilter.DECLINED -> payments.filter {
                    it.statusEnum() == PaymentStatus.REJECTED || it.statusEnum() == PaymentStatus.REVERSED
                }
            }
            val idx = visiblePayments.indexOfFirst { it.id == highlightedPaymentId }
            if (idx >= 0) listState.animateScrollToItem(headerItems + idx)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Invoices (${invoices.size})") },
                modifier = Modifier.testTag("tenant_invoices_tab")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Payments (${payments.size})") },
                modifier = Modifier.testTag("tenant_payments_tab")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("History ($historyCount)") },
                modifier = Modifier.testTag("tenant_history_tab")
            )
        }

        if (selectedTab == 2) {
            PaymentHistoryList(
                historyState = paymentHistoryState,
                paymentReceiptsByInvoiceId = paymentReceiptsByInvoiceId,
                onPreviewReceipt = { entry, receipt ->
                    val matchedPayment = payments.firstOrNull {
                        it.invoiceId == entry.invoice.id ||
                            (receipt?.paymentId != null && it.id == receipt.paymentId)
                    }
                    previewDocument = TenantPreviewDocument.PaymentReceipt(
                        receipt = receipt,
                        payment = matchedPayment,
                        invoice = entry.invoice
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            activeLease?.let { lease ->
                if (detectedMpesaReceipts.isNotEmpty()) {
                    val topDetected = detectedMpesaReceipts.first()
                    item {
                        DetectedMpesaReceiptCard(
                            receipt = topDetected,
                            extraCount = (detectedMpesaReceipts.size - 1).coerceAtLeast(0),
                            onUseReceipt = {
                                activePrefilledReceipt = topDetected
                                activeQrNotice = null
                                paymentTargetLease = activeLease
                                paymentTargetUnit = activeUnit
                                paymentTargetInvoice = invoices.firstOrNull { it.outstandingCents > 0L }
                                showPayDialog = true
                            },
                            onDismiss = { onDismissDetectedReceipt(topDetected) }
                        )
                    }
                }
                item {
                    val isDark = LocalIsDarkTheme.current
                    val qrAccent = if (isDark) MpesaEmeraldLight else MpesaEmerald
                    val qrContainer = if (isDark) MpesaEmeraldContainerDark else MpesaEmeraldContainerLight
                    val qrBorder = if (isDark) MpesaEmeraldBorderDark else MpesaEmeraldBorderLight
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    activePrefilledReceipt = null
                                    activeQrNotice = null
                                    paymentTargetLease = activeLease
                                    paymentTargetUnit = activeUnit
                                    paymentTargetInvoice = invoices.firstOrNull { it.outstandingCents > 0L }
                                    showPayDialog = true
                                },
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("submit_rent_payment_button")
                            ) {
                                Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pay Rent / M-Pesa", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = onOpenQrScanner,
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = qrContainer,
                                    contentColor = qrAccent
                                ),
                                border = BorderStroke(1.dp, qrBorder),
                                modifier = Modifier.heightIn(min = 48.dp).testTag("tenant_payments_scan_qr_button")
                            ) {
                                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Scan QR", maxLines = 1)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                ReportExporter.exportTenantStatementCsv(context, lease, invoices, payments)
                            },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("tenant_export_csv_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export Statement (CSV)")
                        }
                    }
                }
                if (paymentMethodViewModel != null) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tenant_selected_method_card")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = selectedSavedMethod?.let { "Payment Method: ${it.label}" }
                                            ?: "Saved M-Pesa Paybill / Till",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = selectedSavedMethod?.summarySubtitle()
                                            ?: "Add & validate a Paybill, Till, STK Phone, Bank, or Cash method",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = onOpenPaymentMethods,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("open_payment_method_selector")
                                ) {
                                    Text(if (selectedSavedMethod != null) "Change (${savedMethods.size})" else "Select / Add")
                                }
                            }
                        }
                    }
                }
                val hasMpesaInstructions = PaymentFormattingUtils.isValidMpesaShortcode(activeUnit?.mpesaPaybill) ||
                    PaymentFormattingUtils.isValidMpesaShortcode(activeUnit?.mpesaTill)
                if (hasMpesaInstructions) {
                    val nextDueAmountCents = invoices.firstOrNull { it.outstandingCents > 0L }?.outstandingCents
                        ?: lease.rentCents.takeIf { it > 0L }
                    item {
                        PaymentInstructionCard(
                            paybill = activeUnit?.mpesaPaybill,
                            till = activeUnit?.mpesaTill,
                            accountReference = PaymentFormattingUtils.formatAccountReference(
                                prefix = activeUnit?.mpesaAccountPrefix,
                                unitNumber = lease.unitNumber
                            ),
                            amountCents = nextDueAmountCents,
                            recipientName = lease.propertyName,
                            modifier = Modifier.testTag("tenant_pay_tab_mpesa_card")
                        )
                    }
                }
            } ?: run {
                item {
                    OutlinedButton(
                        onClick = onOpenQrScanner,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("tenant_payments_scan_qr_button")
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Scan Rent Collection QR Notice")
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
                                items(filteredInvoices.filter { it.statusEnum() != InvoiceStatus.DRAFT }, key = { it.id }) { inv ->
                                    val expanded = inv.id in expandedInvoiceIds
                                    val isHighlighted = inv.id == highlightedInvoiceId
                                    val tone = when (inv.statusEnum()) {
                                        InvoiceStatus.DRAFT -> PillTone.NEUTRAL
                                        InvoiceStatus.ISSUED -> PillTone.INFO
                                        InvoiceStatus.PAID -> PillTone.SUCCESS
                                        InvoiceStatus.PARTIALLY_PAID -> PillTone.WARNING
                                        InvoiceStatus.OVERDUE -> PillTone.ERROR
                                        InvoiceStatus.UNPAID -> PillTone.INFO
                                        InvoiceStatus.CANCELLED -> PillTone.NEUTRAL
                                    }
                                    Card(
                                        shape = MaterialTheme.shapes.medium,
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isHighlighted) {
                                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                                            } else {
                                                MaterialTheme.colorScheme.surface
                                            }
                                        ),
                                        border = BorderStroke(
                                            width = if (isHighlighted) 2.dp else 1.dp,
                                            color = if (isHighlighted) {
                                                MaterialTheme.colorScheme.secondary
                                            } else {
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                            }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateContentSize()
                                            .clickable {
                                                expandedInvoiceIds = if (expanded) expandedInvoiceIds - inv.id else expandedInvoiceIds + inv.id
                                            }
                                            .let { mod ->
                                                if (isHighlighted) mod.testTag("highlighted_invoice_${inv.id}") else mod
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (isHighlighted) {
                                                val reminderCandidate = remember(inv) {
                                                    RentReminderEngine.evaluateInvoices(
                                                        invoices = listOf(inv),
                                                        respectCooldown = false
                                                    ).firstOrNull()
                                                }
                                                val alertPillLabel = when (reminderCandidate?.urgency) {
                                                    RentReminderUrgency.OVERDUE -> "Overdue Payment Alert"
                                                    RentReminderUrgency.DUE_TODAY -> "Rent Due Today"
                                                    RentReminderUrgency.DUE_SOON -> "Due in ${reminderCandidate.daysDelta} Day(s)"
                                                    null -> "Selected Invoice"
                                                }
                                                val alertTone = when (reminderCandidate?.urgency) {
                                                    RentReminderUrgency.OVERDUE -> PillTone.ERROR
                                                    RentReminderUrgency.DUE_TODAY,
                                                    RentReminderUrgency.DUE_SOON -> PillTone.WARNING
                                                    null -> PillTone.INFO
                                                }
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .testTag("highlighted_invoice_alert_banner_${inv.id}")
                                                ) {
                                                    StatusPill(alertPillLabel, alertTone)
                                                    TextButton(
                                                        onClick = { highlightedInvoiceId = null },
                                                        modifier = Modifier.heightIn(min = 48.dp)
                                                    ) {
                                                        Text("Dismiss")
                                                    }
                                                }
                                            }
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
                                            if (inv.prorated || inv.lateFeeCents > 0L || inv.lateFeeWaived) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    if (inv.prorated) {
                                                        StatusPill("Prorated", PillTone.INFO)
                                                    }
                                                    if (inv.lateFeeWaived) {
                                                        StatusPill("Late Fee Waived", PillTone.INFO)
                                                    } else if (inv.lateFeeCents > 0L) {
                                                        StatusPill(
                                                            "Late Fee +${CurrencyUtils.formatKes(inv.lateFeeCents)} (${LeaseAutomationEngine.formatPercent(inv.lateFeeRatePercent)})",
                                                            PillTone.ERROR
                                                        )
                                                    }
                                                }
                                            }
                                            if (inv.lateFeeCents == 0L && !inv.lateFeeWaived && inv.gracePeriodEndsEpochMs != null && inv.outstandingCents > 0L && activeLease?.lateFeeEnabled == true) {
                                                Text(
                                                    "Grace period ends: ${CurrencyUtils.formatDate(inv.gracePeriodEndsEpochMs)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                                    if (inv.prorated && !inv.prorationNote.isNullOrBlank()) {
                                                        Text(
                                                            inv.prorationNote,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.secondary
                                                        )
                                                    }
                                                    TenantBreakdownRow(if (inv.prorated) "Prorated Monthly Rent" else "Monthly Rent", CurrencyUtils.formatKes(inv.rentCents, true))
                                                    if (inv.extraChargesCents > 0L || !inv.extraChargesNote.isNullOrBlank()) {
                                                        val label = inv.extraChargesNote?.takeIf { it.isNotBlank() }?.let { "Extra Charges ($it)" } ?: "Extra Charges"
                                                        TenantBreakdownRow(label, CurrencyUtils.formatKes(inv.extraChargesCents, true))
                                                    }
                                                    if (inv.previousBalanceCents > 0L) {
                                                        TenantBreakdownRow("Prior Arrears", CurrencyUtils.formatKes(inv.previousBalanceCents, true))
                                                    }
                                                    if (inv.lateFeeCents > 0L) {
                                                        TenantBreakdownRow(
                                                            "Late Fee (${LeaseAutomationEngine.formatPercent(inv.lateFeeRatePercent)})",
                                                            CurrencyUtils.formatKes(inv.lateFeeCents, true),
                                                            isError = true
                                                        )
                                                    } else if (inv.lateFeeWaived) {
                                                        val reasonSuffix = inv.lateFeeWaivedReason?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                                                        TenantBreakdownRow("Late Fee Waived$reasonSuffix", "KES 0.00")
                                                    }
                                                    TenantBreakdownRow("Paid / Credit Applied", "- ${CurrencyUtils.formatKes(inv.paidCents, true)}")
                                                    TenantBreakdownRow("Remaining Balance", CurrencyUtils.formatKes(inv.outstandingCents, true), isError = inv.outstandingCents > 0L)
                                                    inv.issuedAtEpochMs?.let { issuedMs ->
                                                        TenantBreakdownRow("Issued On", CurrencyUtils.formatDate(issuedMs))
                                                    }
                                                    inv.paidAtEpochMs?.let { paidMs ->
                                                        TenantBreakdownRow("Settled On", CurrencyUtils.formatDate(paidMs))
                                                    }
                                                }
                                            }
                                            if (inv.outstandingCents > 0L && activeLease != null) {
                                                if (isHighlighted) {
                                                    Button(
                                                        onClick = {
                                                            activePrefilledReceipt = null
                                                            activeQrNotice = null
                                                            paymentTargetLease = activeLease
                                                            paymentTargetUnit = activeUnit
                                                            paymentTargetInvoice = inv
                                                            showPayDialog = true
                                                        },
                                                        shape = RoundedCornerShape(999.dp),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (inv.statusEnum() == InvoiceStatus.OVERDUE) {
                                                                StatusError
                                                            } else {
                                                                MaterialTheme.colorScheme.secondary
                                                            },
                                                            contentColor = if (inv.statusEnum() == InvoiceStatus.OVERDUE) {
                                                                Color.White
                                                            } else {
                                                                MaterialTheme.colorScheme.onSecondary
                                                            }
                                                        ),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(min = 48.dp)
                                                            .testTag("highlighted_invoice_pay_button_${inv.id}")
                                                    ) {
                                                        Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                                                        Spacer(Modifier.width(6.dp))
                                                        Text("Pay Now via M-Pesa")
                                                    }
                                                } else {
                                                    OutlinedButton(
                                                        onClick = {
                                                            activePrefilledReceipt = null
                                                            activeQrNotice = null
                                                            paymentTargetLease = activeLease
                                                            paymentTargetUnit = activeUnit
                                                            paymentTargetInvoice = inv
                                                            showPayDialog = true
                                                        },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(min = 48.dp)
                                                            .testTag("pay_invoice_button_${inv.id}")
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
                                    TenantPaymentCard(
                                        pay = pay,
                                        highlighted = pay.id == highlightedPaymentId,
                                        onDownloadReceipt = {
                                            previewDocument = resolvePaymentReceiptPreview(pay, paymentReceipts, invoices)
                                        }
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

    val resolvedDialogLease = paymentTargetLease ?: activeLease
    val resolvedDialogUnit = paymentTargetUnit ?: activeUnit
    if (showPayDialog && resolvedDialogLease != null) {
        val currentLease = resolvedDialogLease
        val usedReceipt = activePrefilledReceipt
        val usedQrNotice = activeQrNotice
        TenantPaymentSubmissionDialog(
            lease = currentLease,
            unit = resolvedDialogUnit,
            targetInvoice = paymentTargetInvoice,
            defaultPhone = currentUser.phoneNumber ?: currentLease.tenantPhone,
            isBusy = busy,
            savedMethods = savedMethods,
            selectedSavedMethod = selectedSavedMethod,
            initialPrefilledReceipt = usedReceipt,
            initialQrNotice = usedQrNotice,
            isExactQrLeaseMatch = activeQrExactLeaseMatch,
            scanOcrState = scanOcrState,
            onExtractWithAi = { bmp, hint -> viewModel.extractDocumentWithAi(bmp, hint) },
            onClearOcr = { viewModel.clearDocumentOcr() },
            onSelectSavedMethod = { methodId -> paymentMethodViewModel?.selectMethod(methodId) },
            onOpenPaymentMethodSelector = {
                showPayDialog = false
                onOpenPaymentMethods()
            },
            onDismiss = {
                activePrefilledReceipt = null
                activeQrNotice = null
                showPayDialog = false
            },
            onSubmit = { invoiceId, refCode, amountCents, method, simulated, paymentMethodId, channelType, shortcode, pendingScan ->
                viewModel.submitPayment(
                    lease = currentLease,
                    invoiceId = invoiceId,
                    referenceCode = refCode,
                    amountCents = amountCents,
                    method = method,
                    simulatedCheckout = simulated,
                    paymentMethodId = paymentMethodId,
                    paymentChannelType = channelType,
                    paymentShortcode = shortcode,
                    verificationSource = if (usedReceipt != null) PaymentVerificationSource.MPESA_SMS_MATCH else null,
                    matchedReceiptId = usedReceipt?.id,
                    onPaymentCreated = { paymentId ->
                        if (usedReceipt != null) {
                            onReceiptSubmittedForPayment(usedReceipt, paymentId, currentLease.id)
                        }
                        val unitForScan = resolvedDialogUnit
                        if (pendingScan != null && unitForScan != null) {
                            viewModel.uploadScannedDocument(
                                unit = unitForScan,
                                lease = currentLease,
                                documentType = pendingScan.documentType,
                                title = pendingScan.title,
                                notes = pendingScan.notes,
                                filterMode = pendingScan.filterMode,
                                pageImageRefs = pendingScan.pageImageRefs,
                                extractedReference = pendingScan.extractedReference ?: refCode,
                                extractedAmountCents = pendingScan.extractedAmountCents ?: amountCents,
                                extractedDateText = pendingScan.extractedDateText,
                                extractedSummary = pendingScan.extractedSummary,
                                aiExtracted = pendingScan.aiExtracted,
                                paymentId = paymentId,
                                uploaderName = currentUser.fullName
                            )
                        }
                    }
                ) {
                    activePrefilledReceipt = null
                    activeQrNotice = null
                    showPayDialog = false
                }
            }
        )
    }

    previewDocument?.let { doc ->
        TenantDocumentPreviewSheet(
            document = doc,
            onDismiss = { previewDocument = null }
        )
    }
}

@Composable
fun DetectedMpesaReceiptCard(
    receipt: MpesaReceipt,
    extraCount: Int = 0,
    onUseReceipt: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("detected_mpesa_receipt_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Icon(Icons.Filled.Sms, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Column {
                        Text(
                            text = "M-Pesa Receipt Detected • ${receipt.transactionCode}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = buildString {
                                append(CurrencyUtils.formatKes(receipt.amountCents, true))
                                if (!receipt.counterpartyName.isNullOrBlank()) {
                                    append(" to ")
                                    append(receipt.counterpartyName)
                                }
                                if (extraCount > 0) {
                                    append(" (+$extraCount more)")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                StatusPill("Auto-Fill Ready", PillTone.INFO)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onUseReceipt,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("use_detected_receipt_button")
                ) {
                    Text("Use to Pay Rent")
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("dismiss_detected_receipt_button")
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
fun MpesaInstructionsCard(
    paybill: String?,
    till: String?,
    accountReference: String,
    amountCents: Long? = null,
    recipientName: String? = null,
    modifier: Modifier = Modifier
) {
    PaymentInstructionCard(
        paybill = paybill,
        till = till,
        accountReference = accountReference,
        amountCents = amountCents,
        recipientName = recipientName,
        modifier = modifier.testTag("mpesa_instructions_card")
    )
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
private fun TenantPaymentCard(
    pay: Payment,
    highlighted: Boolean = false,
    onDownloadReceipt: () -> Unit
) {
    val tone = when (pay.statusEnum()) {
        PaymentStatus.VERIFIED -> PillTone.SUCCESS
        PaymentStatus.PENDING_VERIFICATION -> PillTone.WARNING
        PaymentStatus.REJECTED, PaymentStatus.REVERSED -> PillTone.ERROR
    }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (highlighted) 2.dp else 1.dp,
            color = if (highlighted) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .let { mod ->
                if (highlighted) mod.testTag("highlighted_payment_${pay.id}") else mod
            }
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        CurrencyUtils.formatKes(pay.amountCents, true),
                        style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize)
                    )
                    val channelDesc = buildString {
                        append(pay.channelTypeEnum()?.label ?: pay.methodEnum().label)
                        if (!pay.paymentShortcode.isNullOrBlank()) {
                            append(" (")
                            append(pay.paymentShortcode)
                            append(")")
                        }
                        append(" • Ref: ")
                        append(pay.referenceCode)
                        if (pay.simulatedCheckout) append(" (Simulated Demo)")
                    }
                    Text(
                        channelDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        CurrencyUtils.formatDateTime(pay.paymentDateEpochMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusPill(pay.statusEnum().label, tone)
                    if (pay.verificationSourceEnum() == PaymentVerificationSource.MPESA_SMS_MATCH) {
                        StatusPill("SMS Matched", PillTone.INFO)
                    }
                }
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
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("tenant_payment_receipt_button_${pay.id}")
                ) {
                    Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Preview & Download Receipt (PDF)")
                }
            }
        }
    }
}

private fun resolvePaymentReceiptPreview(
    pay: Payment,
    receipts: List<PaymentReceiptPdf>,
    invoices: List<Invoice>
): TenantPreviewDocument.PaymentReceipt {
    val matchedReceipt = receipts.firstOrNull { it.paymentId == pay.id }
        ?: receipts.firstOrNull {
            !pay.receiptNumber.isNullOrBlank() &&
                it.receiptNumber.equals(pay.receiptNumber, ignoreCase = true)
        }
        ?: receipts.firstOrNull {
            !it.paymentReferenceCode.isNullOrBlank() &&
                it.paymentReferenceCode.equals(pay.referenceCode, ignoreCase = true)
        }
        ?: receipts.firstOrNull {
            !pay.invoiceId.isNullOrBlank() && it.invoiceId == pay.invoiceId
        }
    val matchedInvoice = matchedReceipt?.let { rec ->
        invoices.firstOrNull { it.id == rec.invoiceId }
    } ?: invoices.firstOrNull { it.id == pay.invoiceId }
    return TenantPreviewDocument.PaymentReceipt(
        receipt = matchedReceipt,
        payment = pay,
        invoice = matchedInvoice
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
    savedMethods: List<SavedPaymentMethod> = emptyList(),
    selectedSavedMethod: SavedPaymentMethod? = null,
    initialPrefilledReceipt: MpesaReceipt? = null,
    initialQrNotice: ParsedRentQrNotice? = null,
    isExactQrLeaseMatch: Boolean = true,
    scanOcrState: ScanOcrUiState = ScanOcrUiState.Idle,
    onExtractWithAi: (Bitmap, DocumentType?) -> Unit = { _, _ -> },
    onClearOcr: () -> Unit = {},
    onSelectSavedMethod: (String?) -> Unit = {},
    onOpenPaymentMethodSelector: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSubmit: (
        invoiceId: String?,
        refCode: String,
        amountCents: Long,
        method: PaymentMethod,
        simulated: Boolean,
        paymentMethodId: String?,
        channelType: PaymentChannelType?,
        shortcode: String?,
        pendingScan: PendingScanPayload?
    ) -> Unit
) {
    val qrKey = initialQrNotice?.rawPayload
    val initialMode = if (initialQrNotice == null &&
        initialPrefilledReceipt == null &&
        selectedSavedMethod?.channelTypeEnum() == PaymentChannelType.MPESA_STK
    ) 1 else 0
    var mode by remember(selectedSavedMethod?.id, initialPrefilledReceipt?.id, qrKey) { mutableIntStateOf(initialMode) } // 0 = Manual Reference, 1 = Simulated M-Pesa STK
    var method by remember(selectedSavedMethod?.id, initialPrefilledReceipt?.id, qrKey) {
        mutableStateOf(
            if (initialQrNotice != null || initialPrefilledReceipt != null) {
                PaymentMethod.MPESA
            } else {
                selectedSavedMethod?.channelTypeEnum()?.baseMethod ?: PaymentMethod.MPESA
            }
        )
    }
    var methodExpanded by remember { mutableStateOf(false) }
    var smsPasteText by remember(initialPrefilledReceipt?.id, qrKey) {
        mutableStateOf(initialPrefilledReceipt?.rawSnippet ?: "")
    }
    var smsParsedStatus by remember(initialPrefilledReceipt?.id, qrKey) {
        mutableStateOf(
            initialPrefilledReceipt?.let {
                "Auto-filled ${it.transactionCode} • ${CurrencyUtils.formatKes(it.amountCents, true)}"
            }
        )
    }
    var refCode by remember(initialPrefilledReceipt?.id, qrKey) {
        mutableStateOf(initialPrefilledReceipt?.transactionCode ?: "")
    }
    var mpesaPhone by remember(selectedSavedMethod?.id, defaultPhone) {
        mutableStateOf(
            if (selectedSavedMethod?.channelTypeEnum() == PaymentChannelType.MPESA_STK &&
                !selectedSavedMethod.phoneNumber.isNullOrBlank()
            ) {
                selectedSavedMethod.phoneNumber
            } else {
                defaultPhone
            }
        )
    }
    val defaultAmount = initialQrNotice?.amountCents?.takeIf { it > 0L }
        ?: initialPrefilledReceipt?.amountCents
        ?: targetInvoice?.outstandingCents?.takeIf { it > 0L }
        ?: lease.rentCents
    var amountText by remember(initialPrefilledReceipt?.id, qrKey, defaultAmount) {
        mutableStateOf(CurrencyUtils.centsToInputString(defaultAmount))
    }
    var pendingScan by remember { mutableStateOf<PendingScanPayload?>(null) }
    var showScanner by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    val displayPaybill = when {
        initialQrNotice?.channel == MpesaInstructionChannel.PAYBILL -> initialQrNotice.shortcode
        initialQrNotice?.channel == MpesaInstructionChannel.TILL -> null
        selectedSavedMethod?.channelTypeEnum() == PaymentChannelType.MPESA_PAYBILL -> selectedSavedMethod.paybillNumber
        else -> unit?.mpesaPaybill
    }
    val displayTill = when {
        initialQrNotice?.channel == MpesaInstructionChannel.TILL -> initialQrNotice.shortcode
        initialQrNotice?.channel == MpesaInstructionChannel.PAYBILL -> null
        selectedSavedMethod?.channelTypeEnum() == PaymentChannelType.MPESA_TILL -> selectedSavedMethod.tillNumber
        else -> unit?.mpesaTill
    }
    val displayAccountRef = when {
        initialQrNotice?.channel == MpesaInstructionChannel.PAYBILL && !initialQrNotice.accountReference.isNullOrBlank() ->
            initialQrNotice.accountReference
        selectedSavedMethod?.channelTypeEnum() == PaymentChannelType.MPESA_PAYBILL && !selectedSavedMethod.accountReference.isNullOrBlank() ->
            selectedSavedMethod.accountReference
        else -> MpesaSmsParser.buildMpesaAccountReference(
            prefix = unit?.mpesaAccountPrefix,
            unitNumber = lease.unitNumber
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Submit Rent Payment") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (initialQrNotice != null) {
                    val isDark = LocalIsDarkTheme.current
                    val qrSuccessFg = if (isDark) StatusSuccessLight else StatusSuccess
                    val qrSuccessBg = if (isDark) StatusSuccessBgDark else StatusSuccessBg
                    val warnFg = if (isDark) StatusWarningLight else StatusWarning
                    Surface(
                        color = qrSuccessBg,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, qrSuccessFg.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scanned_qr_prefill_card")
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.QrCodeScanner,
                                    contentDescription = null,
                                    tint = qrSuccessFg,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Scanned ${initialQrNotice.sourceFormat.label}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = qrSuccessFg
                                )
                            }
                            Text(
                                text = buildString {
                                    append(initialQrNotice.channel.displayName)
                                    append(" • ")
                                    append(initialQrNotice.formattedShortcode)
                                    if (!initialQrNotice.accountReference.isNullOrBlank()) {
                                        append(" • Acc: ")
                                        append(initialQrNotice.accountReference)
                                    }
                                },
                                style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Paying for: ${lease.propertyName} • Unit ${lease.unitNumber}" +
                                    (targetInvoice?.let { " (Invoice ${it.invoiceNumber})" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!isExactQrLeaseMatch) {
                                Text(
                                    text = "Notice details did not match your unit prefix directly — confirm shortcode and account before paying.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = warnFg
                                )
                            }
                        }
                    }
                }
                if (savedMethods.isNotEmpty() || onOpenPaymentMethodSelector != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Saved Kenya Channels", style = MaterialTheme.typography.labelMedium)
                            if (onOpenPaymentMethodSelector != null) {
                                TextButton(
                                    onClick = onOpenPaymentMethodSelector,
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("dialog_manage_payment_methods_button")
                                ) {
                                    Text("Select / Add")
                                }
                            }
                        }
                        if (savedMethods.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                savedMethods.take(5).forEach { saved ->
                                    FilterChip(
                                        selected = selectedSavedMethod?.id == saved.id,
                                        onClick = {
                                            onSelectSavedMethod(saved.id)
                                            method = saved.channelTypeEnum().baseMethod
                                            if (saved.channelTypeEnum() == PaymentChannelType.MPESA_STK) {
                                                mode = 1
                                                if (!saved.phoneNumber.isNullOrBlank()) {
                                                    mpesaPhone = saved.phoneNumber
                                                }
                                            } else {
                                                mode = 0
                                            }
                                            err = null
                                        },
                                        label = { Text(saved.label) },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    )
                                }
                            }
                        }
                    }
                }

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

                val hasDialogMpesa = PaymentFormattingUtils.isValidMpesaShortcode(displayPaybill) ||
                    PaymentFormattingUtils.isValidMpesaShortcode(displayTill)
                if (hasDialogMpesa) {
                    val liveAmountCents = CurrencyUtils.parseKesToCents(amountText) ?: defaultAmount
                    MpesaInstructionsCard(
                        paybill = displayPaybill,
                        till = displayTill,
                        accountReference = displayAccountRef,
                        amountCents = liveAmountCents,
                        recipientName = lease.propertyName
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

                    if (unit != null) {
                        OutlinedButton(
                            onClick = { showScanner = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("tenant_payment_scan_receipt_button")
                        ) {
                            Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (pendingScan == null) "Scan Receipt (Camera / AI)"
                                else "Re-scan Receipt (${pendingScan!!.pageImageRefs.size}p)"
                            )
                        }
                        if (pendingScan != null) {
                            StatusPill("Receipt scan attached (${pendingScan!!.pageImageRefs.size}p)", PillTone.SUCCESS)
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
                            onSubmit(
                                targetInvoice?.id,
                                refCode.trim().uppercase(),
                                amount,
                                method,
                                false,
                                selectedSavedMethod?.id,
                                selectedSavedMethod?.channelTypeEnum() ?: initialQrNotice?.paymentChannelType,
                                selectedSavedMethod?.primaryShortcodeOrIdentifier() ?: initialQrNotice?.shortcode,
                                pendingScan
                            )
                        }
                    } else {
                        if (!CurrencyUtils.isValidKenyanPhone(mpesaPhone)) {
                            err = "Enter a valid Kenyan M-Pesa phone number (+2547... or 07...)"
                        } else {
                            mpesaPhone = CurrencyUtils.normalizeKenyanPhone(mpesaPhone)
                            val simCode = "SIM-${UUID.randomUUID().toString().take(8).uppercase()}"
                            onSubmit(
                                targetInvoice?.id,
                                simCode,
                                amount,
                                PaymentMethod.MPESA,
                                true,
                                selectedSavedMethod?.id,
                                selectedSavedMethod?.channelTypeEnum() ?: PaymentChannelType.MPESA_STK,
                                selectedSavedMethod?.primaryShortcodeOrIdentifier() ?: mpesaPhone,
                                null
                            )
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

    if (showScanner && unit != null) {
        DocumentScannerModal(
            unitLabel = "${unit.propertyName} • Unit ${unit.unitNumber}",
            tenantLabel = lease.tenantName,
            allowedDocumentTypes = listOf(DocumentType.PAYMENT_RECEIPT, DocumentType.OTHER),
            initialDocumentType = DocumentType.PAYMENT_RECEIPT,
            allowTenantVisibilityToggle = false,
            initialTenantVisible = true,
            scanOcrState = scanOcrState,
            onExtractWithAi = onExtractWithAi,
            onClearOcr = onClearOcr,
            confirmButtonLabel = "Attach Receipt & Fill",
            onDismiss = { showScanner = false },
            onConfirmScan = { payload ->
                pendingScan = payload
                if (!payload.extractedReference.isNullOrBlank() && refCode.isBlank()) {
                    refCode = payload.extractedReference
                }
                if (payload.extractedAmountCents != null && payload.extractedAmountCents > 0L) {
                    amountText = CurrencyUtils.centsToInputString(payload.extractedAmountCents)
                }
                showScanner = false
            }
        )
    }
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
                    contentColor = MaterialTheme.colorScheme.onSecondary,
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
