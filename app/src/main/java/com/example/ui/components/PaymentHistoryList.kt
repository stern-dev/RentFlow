package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.finance.AnnualRentPaidSummary
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.LeaseAutomationEngine
import com.example.domain.finance.PaymentHistoryEntry
import com.example.domain.finance.PaymentHistoryStatus
import com.example.domain.finance.TenantPaymentHistorySummary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TabularMoneyStyle
import com.example.ui.viewmodel.UiState

/**
 * Displays a tenant's settled and partially paid Firestore invoices with Paid, Late,
 * and Partial status badges, year filter chips, and an annual KES rent paid summary card.
 */
@Composable
fun PaymentHistoryList(
    historyState: UiState<TenantPaymentHistorySummary>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    when (historyState) {
        is UiState.Loading -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                LoadingStateBox("Loading payment history...")
            }
        }
        is UiState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                ErrorBannerCard(historyState.message)
            }
        }
        is UiState.Success -> {
            val summary = historyState.data
            if (summary.entries.isEmpty()) {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    EmptyStateCard(
                        title = "No Payment History Yet",
                        message = "Once your rent invoices are settled or partially paid, your yearly KES totals and Paid, Late, or Partial badges will appear here."
                    )
                }
                return
            }

            val availableYears = remember(summary.annualSummaries) {
                summary.annualSummaries.map { it.year }
            }
            var selectedYear by remember(availableYears) { mutableStateOf<Int?>(null) }
            var expandedInvoiceIds by remember { mutableStateOf(emptySet<String>()) }

            val activeAnnual = remember(summary.annualSummaries, selectedYear) {
                selectedYear?.let { yr -> summary.annualSummaries.firstOrNull { it.year == yr } }
            }
            val filteredEntries = remember(summary.entries, selectedYear) {
                if (selectedYear == null) {
                    summary.entries
                } else {
                    summary.entries.filter { it.historyYear == selectedYear }
                }
            }

            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = modifier.fillMaxSize()
            ) {
                LazyColumn(
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .testTag("payment_history_list")
                ) {
                    item(key = "annual_summary_card") {
                        AnnualRentPaidSummaryCard(
                            summary = summary,
                            selectedYear = selectedYear,
                            selectedAnnual = activeAnnual,
                            onSelectYear = { yr ->
                                selectedYear = if (selectedYear == yr) null else yr
                            }
                        )
                    }

                    item(key = "year_filter_chips") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("payment_history_year_filters")
                        ) {
                            item(key = "all_years") {
                                FilterChip(
                                    selected = selectedYear == null,
                                    onClick = { selectedYear = null },
                                    label = { Text("All Years (${summary.entries.size})") },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("payment_history_year_chip_all")
                                )
                            }
                            items(summary.annualSummaries, key = { it.year }) { annual ->
                                FilterChip(
                                    selected = selectedYear == annual.year,
                                    onClick = { selectedYear = annual.year },
                                    label = { Text("${annual.year} (${annual.invoiceCount})") },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("payment_history_year_chip_${annual.year}")
                                )
                            }
                        }
                    }

                    if (filteredEntries.isEmpty()) {
                        item(key = "empty_year_filter") {
                            EmptyStateCard(
                                title = "No Invoices for ${selectedYear ?: ""}",
                                message = "No paid or partially paid invoices were recorded for this year.",
                                ctaLabel = "Show All Years",
                                onCtaClick = { selectedYear = null },
                                showIllustration = false
                            )
                        }
                    } else {
                        items(filteredEntries, key = { it.invoice.id }) { entry ->
                            val isExpanded = entry.invoice.id in expandedInvoiceIds
                            PaymentHistoryInvoiceCard(
                                entry = entry,
                                expanded = isExpanded,
                                onToggleExpand = {
                                    expandedInvoiceIds = if (isExpanded) {
                                        expandedInvoiceIds - entry.invoice.id
                                    } else {
                                        expandedInvoiceIds + entry.invoice.id
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnnualRentPaidSummaryCard(
    summary: TenantPaymentHistorySummary,
    selectedYear: Int?,
    selectedAnnual: AnnualRentPaidSummary?,
    onSelectYear: (Int) -> Unit
) {
    val displayedTotalCents = selectedAnnual?.totalPaidCents ?: summary.overallTotalPaidCents
    val paidCount = selectedAnnual?.paidOnTimeCount ?: summary.overallPaidCount
    val lateCount = selectedAnnual?.lateCount ?: summary.overallLateCount
    val partialCount = selectedAnnual?.partialCount ?: summary.overallPartialCount
    val scopeBadge = selectedYear?.toString() ?: "All Years"
    val subtitle = if (selectedYear != null) {
        "Total rent paid in $selectedYear (${selectedAnnual?.invoiceCount ?: 0} invoices)"
    } else {
        "Total rent paid across ${summary.annualSummaries.size} year(s)"
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("payment_history_summary_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
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
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Annual Rent Paid Summary",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = scopeBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = CurrencyUtils.formatKes(displayedTotalCents, includeDecimals = true),
                    style = TabularMoneyStyle.copy(fontSize = 26.sp),
                    color = StatusSuccess,
                    modifier = Modifier.testTag("payment_history_total_kes")
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusPill("Paid: $paidCount", PillTone.SUCCESS)
                StatusPill("Late: $lateCount", PillTone.ERROR)
                StatusPill("Partial: $partialCount", PillTone.WARNING)
            }

            if (summary.annualSummaries.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Total Rent Paid Per Year",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    summary.annualSummaries.forEach { annual ->
                        val isYearSelected = selectedYear == annual.year
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectYear(annual.year) }
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                                .testTag("payment_history_year_summary_${annual.year}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = annual.year.toString(),
                                    style = TabularMoneyStyle.copy(fontSize = 15.sp),
                                    color = if (isYearSelected) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    text = "${annual.invoiceCount} invoice${if (annual.invoiceCount == 1) "" else "s"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = CurrencyUtils.formatKes(annual.totalPaidCents, includeDecimals = true),
                                style = TabularMoneyStyle.copy(fontSize = 15.sp),
                                color = if (isYearSelected) StatusSuccess else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentHistoryInvoiceCard(
    entry: PaymentHistoryEntry,
    expanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val inv = entry.invoice
    val tone = when (entry.status) {
        PaymentHistoryStatus.PAID -> PillTone.SUCCESS
        PaymentHistoryStatus.LATE -> PillTone.ERROR
        PaymentHistoryStatus.PARTIAL -> PillTone.WARNING
    }
    val propertyUnitSubtitle = listOfNotNull(
        inv.propertyName.takeIf { it.isNotBlank() },
        inv.unitNumber.takeIf { it.isNotBlank() }?.let { "Unit $it" }
    ).joinToString(" • ")

    val dateLine = when {
        inv.paidAtEpochMs != null && inv.paidAtEpochMs > 0L ->
            "Paid ${CurrencyUtils.formatDate(inv.paidAtEpochMs)} (Due ${CurrencyUtils.formatDate(inv.dueDateEpochMs)})"
        inv.dueDateEpochMs > 0L ->
            "Due ${CurrencyUtils.formatDate(inv.dueDateEpochMs)}"
        else -> "Period ${CurrencyUtils.billingPeriodLabel(inv.billingPeriod)}"
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onToggleExpand)
            .testTag("payment_history_item_${inv.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "${inv.invoiceNumber} • ${CurrencyUtils.billingPeriodLabel(inv.billingPeriod)}",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (propertyUnitSubtitle.isNotBlank()) {
                            Text(
                                text = propertyUnitSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = dateLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                StatusPill(label = entry.status.label, tone = tone)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Paid: ${CurrencyUtils.formatKes(inv.paidCents, includeDecimals = true)}",
                    style = TabularMoneyStyle.copy(fontSize = 15.sp),
                    color = StatusSuccess
                )

                val trailingNote = when {
                    entry.status == PaymentHistoryStatus.PARTIAL ->
                        "Bal: ${CurrencyUtils.formatKes(inv.outstandingCents, includeDecimals = true)}"
                    entry.status == PaymentHistoryStatus.LATE && entry.daysLate > 0 ->
                        "${entry.daysLate}d past due"
                    else ->
                        "Total: ${CurrencyUtils.formatKes(inv.totalDueCents, includeDecimals = true)}"
                }
                val trailingColor = when (entry.status) {
                    PaymentHistoryStatus.PARTIAL -> StatusWarning
                    PaymentHistoryStatus.LATE -> StatusError
                    PaymentHistoryStatus.PAID -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = trailingNote,
                    style = MaterialTheme.typography.labelMedium,
                    color = trailingColor
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (expanded) "Hide Breakdown" else "View Breakdown",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Hide invoice breakdown" else "View invoice breakdown",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HistoryBreakdownRow(
                        label = if (inv.prorated) "Prorated Monthly Rent" else "Monthly Rent",
                        amount = CurrencyUtils.formatKes(inv.rentCents, true)
                    )
                    if (inv.extraChargesCents > 0L || !inv.extraChargesNote.isNullOrBlank()) {
                        val extraLabel = inv.extraChargesNote?.takeIf { it.isNotBlank() }?.let { "Extra Charges ($it)" }
                            ?: "Extra Charges"
                        HistoryBreakdownRow(extraLabel, CurrencyUtils.formatKes(inv.extraChargesCents, true))
                    }
                    if (inv.previousBalanceCents > 0L) {
                        HistoryBreakdownRow("Prior Arrears", CurrencyUtils.formatKes(inv.previousBalanceCents, true))
                    }
                    if (inv.lateFeeCents > 0L) {
                        HistoryBreakdownRow(
                            label = "Late Fee (${LeaseAutomationEngine.formatPercent(inv.lateFeeRatePercent)})",
                            amount = CurrencyUtils.formatKes(inv.lateFeeCents, true),
                            isHighlightError = true
                        )
                    } else if (inv.lateFeeWaived) {
                        HistoryBreakdownRow("Late Fee Waived", "KES 0.00")
                    }
                    HistoryBreakdownRow("Total Billed", CurrencyUtils.formatKes(inv.totalDueCents, true))
                    HistoryBreakdownRow("Paid Applied", CurrencyUtils.formatKes(inv.paidCents, true))
                    if (inv.outstandingCents > 0L) {
                        HistoryBreakdownRow(
                            label = "Remaining Balance",
                            amount = CurrencyUtils.formatKes(inv.outstandingCents, true),
                            isHighlightError = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryBreakdownRow(
    label: String,
    amount: String,
    isHighlightError: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        Text(
            text = amount,
            style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
            color = if (isHighlightError) StatusError else MaterialTheme.colorScheme.onSurface
        )
    }
}
