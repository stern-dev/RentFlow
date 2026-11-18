package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ai.AiTaskState
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.ReportExporter
import com.example.domain.model.Organization
import com.example.domain.model.PaymentStatus
import com.example.domain.model.Property
import com.example.domain.model.UnitStatus
import com.example.ui.components.AiInsightCard
import com.example.ui.components.GuidedOnboardingCard
import com.example.ui.components.IncomeExpenseBarChart
import com.example.ui.components.OccupancyDonutChart
import com.example.ui.components.OnboardingStepItem
import com.example.ui.components.PillTone
import com.example.ui.components.RentFlowKpiCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.viewmodel.LandlordViewModel
import com.example.ui.viewmodel.UiState

@Composable
fun LandlordDashboardScreen(
    viewModel: LandlordViewModel,
    organizations: List<Organization>,
    onNavigatePortfolio: () -> Unit,
    onNavigateFinance: () -> Unit,
    onNavigateOperations: () -> Unit,
    onOpenCreateOrg: () -> Unit,
    onOpenAddProperty: () -> Unit
) {
    val context = LocalContext.current
    val summary by viewModel.portfolioSummary.collectAsStateWithLifecycle()
    val cashFlow by viewModel.cashFlowPoints.collectAsStateWithLifecycle()
    val propertiesState by viewModel.properties.collectAsStateWithLifecycle()
    val unitsState by viewModel.units.collectAsStateWithLifecycle()
    val leasesState by viewModel.leases.collectAsStateWithLifecycle()
    val invoicesState by viewModel.invoices.collectAsStateWithLifecycle()
    val paymentsState by viewModel.payments.collectAsStateWithLifecycle()
    val expensesState by viewModel.expenses.collectAsStateWithLifecycle()
    val aiInsightsState by viewModel.aiInsightsState.collectAsStateWithLifecycle()

    val properties = (propertiesState as? UiState.Success)?.data ?: emptyList()
    val units = (unitsState as? UiState.Success)?.data ?: emptyList()
    val leases = (leasesState as? UiState.Success)?.data ?: emptyList()
    val invoices = (invoicesState as? UiState.Success)?.data ?: emptyList()
    val payments = (paymentsState as? UiState.Success)?.data ?: emptyList()
    val expenses = (expensesState as? UiState.Success)?.data ?: emptyList()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (properties.isEmpty() || units.isEmpty() || leases.isEmpty()) {
            item {
                GuidedOnboardingCard(
                    steps = listOf(
                        OnboardingStepItem(
                            title = "Create Property Organization",
                            subtitle = if (organizations.isNotEmpty()) "${organizations.first().name} ready" else "Isolates your properties & accounting",
                            completed = organizations.isNotEmpty(),
                            onAction = onOpenCreateOrg
                        ),
                        OnboardingStepItem(
                            title = "Register Your First Property",
                            subtitle = "Add location, Kenyan city, category & photos",
                            completed = properties.isNotEmpty(),
                            onAction = onOpenAddProperty
                        ),
                        OnboardingStepItem(
                            title = "Add Rental Units & Publish Vacancies",
                            subtitle = "Configure KES rent, deposit & amenities",
                            completed = units.isNotEmpty(),
                            onAction = onNavigatePortfolio
                        ),
                        OnboardingStepItem(
                            title = "Approve Application or Assign Tenant",
                            subtitle = "Activate lease & generate monthly KES invoices",
                            completed = leases.isNotEmpty(),
                            onAction = onNavigatePortfolio
                        )
                    )
                )
            }
        }

        item {
            SectionHeader(
                title = "Portfolio Overview",
                actionLabel = "Export CSV",
                onActionClick = {
                    ReportExporter.exportLandlordFinancialCsv(context, summary, invoices, payments, expenses)
                }
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                RentFlowKpiCard(
                    title = "Properties",
                    value = summary.totalProperties.toString(),
                    subtitle = "${summary.totalUnits} total units",
                    icon = Icons.Filled.Apartment,
                    accentColor = StatusInfo,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigatePortfolio
                )
                RentFlowKpiCard(
                    title = "Occupancy",
                    value = "${summary.occupiedUnits}/${summary.totalUnits}",
                    subtitle = "${summary.vacantUnits} vacant • ${summary.activeTenants} tenants",
                    icon = Icons.Filled.HomeWork,
                    accentColor = EmeraldAccent,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigatePortfolio
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                RentFlowKpiCard(
                    title = "Expected Monthly",
                    value = CurrencyUtils.formatKesCompact(summary.expectedMonthlyRentCents),
                    subtitle = "Active lease roll",
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    accentColor = StatusInfo,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateFinance
                )
                RentFlowKpiCard(
                    title = "Rent Collected",
                    value = CurrencyUtils.formatKesCompact(summary.totalCollectedCents),
                    subtitle = "${summary.pendingPaymentsCount} pending verify",
                    icon = Icons.Filled.Payments,
                    accentColor = StatusSuccess,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateFinance
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                RentFlowKpiCard(
                    title = "Outstanding Rent",
                    value = CurrencyUtils.formatKesCompact(summary.outstandingRentCents),
                    subtitle = "${summary.overdueInvoices.size} overdue",
                    icon = Icons.Filled.WarningAmber,
                    accentColor = StatusError,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateFinance
                )
                RentFlowKpiCard(
                    title = "Net Rental Income",
                    value = CurrencyUtils.formatKesCompact(summary.netRentalIncomeCents),
                    subtitle = "Exp: ${CurrencyUtils.formatKesCompact(summary.totalExpensesCents)}",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    accentColor = if (summary.netRentalIncomeCents >= 0) StatusSuccess else StatusError,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateFinance
                )
            }
        }

        item {
            OccupancyDonutChart(
                occupied = summary.occupiedUnits,
                vacant = summary.vacantUnits,
                other = (summary.totalUnits - summary.occupiedUnits - summary.vacantUnits).coerceAtLeast(0)
            )
        }

        item {
            IncomeExpenseBarChart(points = cashFlow)
        }

        item {
            val aiLoading = aiInsightsState is AiTaskState.Loading
            val aiText = when (val st = aiInsightsState) {
                is AiTaskState.Streaming -> st.text
                is AiTaskState.Success -> st.text
                else -> null
            }
            val aiError = (aiInsightsState as? AiTaskState.Error)?.message
            AiInsightCard(
                title = "Smart Rent Insights",
                subtitle = "Portfolio arrears, occupancy & cash-flow brief",
                isLoading = aiLoading,
                streamingOrFinalText = aiText,
                errorText = aiError,
                actionLabel = if (aiText != null) "Refresh" else "Analyze",
                onAction = { viewModel.generatePortfolioInsights() },
                testTag = "smart_rent_insights_card"
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        ReportExporter.exportPdfDocument(
                            context = context,
                            fileName = "rentflow_income_statement.pdf",
                            title = "RentFlow Monthly Income & Arrears Report",
                            subtitle = "Generated ${CurrencyUtils.formatDateTime(System.currentTimeMillis())} • Currency: KES",
                            lines = listOf(
                                "[H]PORTFOLIO SUMMARY",
                                "Active Properties: ${summary.totalProperties} | Units: ${summary.totalUnits} (${summary.occupiedUnits} occupied)",
                                "Expected Monthly Rent: ${CurrencyUtils.formatKes(summary.expectedMonthlyRentCents, true)}",
                                "Verified Rent Collected: ${CurrencyUtils.formatKes(summary.totalCollectedCents, true)}",
                                "Outstanding Arrears: ${CurrencyUtils.formatKes(summary.outstandingRentCents, true)}",
                                "Operating Expenses: ${CurrencyUtils.formatKes(summary.totalExpensesCents, true)}",
                                "Net Rental Income: ${CurrencyUtils.formatKes(summary.netRentalIncomeCents, true)}",
                                "---",
                                "[H]RECENT INVOICES (${invoices.size})"
                            ) + invoices.take(15).map {
                                "${it.invoiceNumber} | ${it.propertyName} (${it.unitNumber}) | Due ${CurrencyUtils.formatKes(it.outstandingCents, true)} | ${it.status}"
                            } + listOf("---", "[H]EXPENSES (${expenses.size})") + expenses.take(15).map {
                                "${it.title} (${it.category}) | ${it.propertyName} | ${CurrencyUtils.formatKes(it.amountCents, true)}"
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("export_pdf_report_button")
                ) {
                    Icon(Icons.Filled.Description, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("PDF Report")
                }
                OutlinedButton(
                    onClick = {
                        ReportExporter.exportLandlordFinancialCsv(context, summary, invoices, payments, expenses)
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("export_csv_report_button")
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("CSV Ledger")
                }
            }
        }

        // Action Alerts
        if (summary.pendingPaymentsCount > 0 || summary.overdueInvoices.isNotEmpty() || summary.upcomingLeaseExpiries.isNotEmpty() || summary.pendingMaintenanceCount > 0) {
            item { SectionHeader("Attention Required") }
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (summary.pendingPaymentsCount > 0) {
                            AlertLine("${summary.pendingPaymentsCount} payment(s) awaiting verification", "Verify", onNavigateFinance)
                        }
                        if (summary.overdueInvoices.isNotEmpty()) {
                            AlertLine("${summary.overdueInvoices.size} overdue rent invoice(s)", "View", onNavigateFinance)
                        }
                        if (summary.upcomingLeaseExpiries.isNotEmpty()) {
                            AlertLine("${summary.upcomingLeaseExpiries.size} lease(s) expiring within 30 days", "Manage", onNavigatePortfolio)
                        }
                        if (summary.pendingMaintenanceCount > 0) {
                            AlertLine("${summary.pendingMaintenanceCount} open maintenance ticket(s)", "Assign", onNavigateOperations)
                        }
                    }
                }
            }
        }

        if (payments.isNotEmpty()) {
            item {
                SectionHeader("Recent Payments", "All Ledger", onNavigateFinance)
            }
            items(payments.take(5), key = { it.id }) { pay ->
                val tone = when (pay.statusEnum()) {
                    PaymentStatus.VERIFIED -> PillTone.SUCCESS
                    PaymentStatus.PENDING_VERIFICATION -> PillTone.WARNING
                    PaymentStatus.REJECTED, PaymentStatus.REVERSED -> PillTone.ERROR
                }
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(14.dp).fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${pay.tenantName} • ${pay.propertyName} (${pay.unitNumber})", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${pay.methodEnum().label} • Ref: ${pay.referenceCode}${if (pay.simulatedCheckout) " (Simulated)" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(CurrencyUtils.formatKes(pay.amountCents, true), style = MaterialTheme.typography.titleSmall)
                            StatusPill(pay.statusEnum().label, tone)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertLine(text: String, cta: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = 40.dp)) {
            Text(cta)
        }
    }
}
