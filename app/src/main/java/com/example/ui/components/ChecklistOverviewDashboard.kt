package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.R
import com.example.domain.finance.CurrencyUtils
import com.example.domain.model.ChecklistOverviewBucket
import com.example.domain.model.ChecklistOverviewEngine
import com.example.domain.model.ChecklistOverviewItem
import com.example.domain.model.ChecklistOverviewSummary
import com.example.domain.model.ChecklistWorkflowStage
import com.example.domain.model.Lease
import com.example.domain.model.MoveInConditionChecklist
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
import com.example.ui.theme.MutedCobalt
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorBg
import com.example.ui.theme.StatusErrorBgDark
import com.example.ui.theme.StatusErrorLight
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoBg
import com.example.ui.theme.StatusInfoBgDark
import com.example.ui.theme.StatusInfoLight
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusSuccessBgDark
import com.example.ui.theme.StatusSuccessLight
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import com.example.ui.theme.StatusWarningBgDark
import com.example.ui.theme.StatusWarningLight
import com.example.ui.viewmodel.ChecklistOverviewFilterState
import com.example.ui.viewmodel.UiState

@Composable
fun ChecklistOverviewTab(
    overviewState: UiState<ChecklistOverviewSummary>,
    filterState: ChecklistOverviewFilterState,
    canManageChecklist: (Lease) -> Boolean,
    onBucketFilterToggle: (ChecklistOverviewBucket?) -> Unit,
    onPropertyFilterToggle: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearFilters: () -> Unit,
    onOpenChecklist: (Lease) -> Unit,
    onOpenPdfPreview: (MoveInConditionChecklist, Lease) -> Unit
) {
    when (overviewState) {
        is UiState.Loading -> {
            LoadingStateBox(stringResource(R.string.checklist_overview_loading))
        }
        is UiState.Error -> {
            Box(Modifier.padding(16.dp)) {
                ErrorBannerCard(overviewState.message)
            }
        }
        is UiState.Success -> {
            val summary = overviewState.data
            val filteredItems = remember(
                summary,
                filterState.bucket,
                filterState.propertyId,
                filterState.searchQuery
            ) {
                ChecklistOverviewEngine.filterItems(
                    summary = summary,
                    bucketFilter = filterState.bucket,
                    propertyIdFilter = filterState.propertyId,
                    searchQuery = filterState.searchQuery
                )
            }
            val hasActiveFilters = filterState.bucket != null ||
                !filterState.propertyId.isNullOrBlank() ||
                filterState.searchQuery.isNotBlank()

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("checklist_overview_lazy_column")
            ) {
                item(key = "overview_health_banner") {
                    ChecklistPortfolioHealthCard(summary = summary)
                }

                item(key = "overview_kpi_strip") {
                    ChecklistBucketKpiRow(
                        summary = summary,
                        selectedBucket = filterState.bucket,
                        onSelectBucket = onBucketFilterToggle
                    )
                }

                if (summary.items.isNotEmpty()) {
                    item(key = "overview_search_bar") {
                        OutlinedTextField(
                            value = filterState.searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            label = { Text(stringResource(R.string.checklist_overview_search_placeholder)) },
                            leadingIcon = {
                                Icon(Icons.Filled.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (filterState.searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onSearchQueryChange("") },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("checklist_search_clear_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Clear,
                                            contentDescription = "Clear search query"
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("checklist_search_input")
                        )
                    }

                    item(key = "overview_filter_chips") {
                        ChecklistFilterChipStrip(
                            summary = summary,
                            filterState = filterState,
                            onBucketFilterToggle = onBucketFilterToggle,
                            onPropertyFilterToggle = onPropertyFilterToggle
                        )
                    }
                }

                if (summary.items.isEmpty()) {
                    item(key = "overview_empty") {
                        EmptyStateCard(
                            title = stringResource(R.string.checklist_overview_empty_title),
                            message = stringResource(R.string.checklist_overview_empty_message)
                        )
                    }
                } else if (filteredItems.isEmpty()) {
                    item(key = "overview_filtered_empty") {
                        EmptyStateCard(
                            title = stringResource(R.string.checklist_overview_filtered_empty_title),
                            message = stringResource(R.string.checklist_overview_filtered_empty_message),
                            ctaLabel = if (hasActiveFilters) stringResource(R.string.checklist_overview_clear_filters) else null,
                            onCtaClick = if (hasActiveFilters) onClearFilters else null
                        )
                    }
                } else {
                    items(filteredItems, key = { "${it.lease.id}_${it.checklist?.id.orEmpty()}" }) { item ->
                        ChecklistOverviewItemCard(
                            item = item,
                            canManage = canManageChecklist(item.lease),
                            onOpenChecklist = { onOpenChecklist(item.lease) },
                            onOpenPdfPreview = {
                                item.checklist?.let { checklist ->
                                    onOpenPdfPreview(checklist, item.lease)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistPortfolioHealthCard(summary: ChecklistOverviewSummary) {
    val isDark = LocalIsDarkTheme.current
    val gradientColors = if (isDark) {
        listOf(DarkSurfaceVariant, DarkPrimaryContainer)
    } else {
        listOf(MidnightInk, MidnightSlate)
    }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, if (isDark) DarkOutline else BrushedSilver.copy(alpha = 0.24f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(gradientColors),
                shape = MaterialTheme.shapes.large
            )
            .testTag("checklist_overview_health_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.checklist_overview_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary
                    )
                    Text(
                        text = stringResource(R.string.checklist_overview_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkTextSecondary
                    )
                }
                Surface(
                    color = MutedCobalt.copy(alpha = 0.34f),
                    border = BorderStroke(1.dp, BrushedSilver.copy(alpha = 0.32f)),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "${summary.completionRatePercent}% Done",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = BrushedSilver,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = {
                    if (summary.totalCount > 0) {
                        (summary.completedCount.toFloat() / summary.totalCount.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                },
                color = CobaltLight,
                trackColor = DarkTextPrimary.copy(alpha = 0.18f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
            )

            Text(
                text = "${summary.pendingStartCount} to start • ${summary.awaitingTenantCount} awaiting tenant • ${summary.draftCount} draft(s) • ${summary.disputedCount} disputed",
                style = MaterialTheme.typography.labelSmall,
                color = DarkTextSecondary
            )
        }
    }
}

@Composable
private fun ChecklistBucketKpiRow(
    summary: ChecklistOverviewSummary,
    selectedBucket: ChecklistOverviewBucket?,
    onSelectBucket: (ChecklistOverviewBucket?) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val warnAccent = if (isDark) StatusWarningLight else StatusWarning
    val warnBg = if (isDark) StatusWarningBgDark else StatusWarningBg
    val infoAccent = if (isDark) StatusInfoLight else StatusInfo
    val infoBg = if (isDark) StatusInfoBgDark else StatusInfoBg
    val errAccent = if (isDark) StatusErrorLight else StatusError
    val errBg = if (isDark) StatusErrorBgDark else StatusErrorBg
    val successAccent = if (isDark) StatusSuccessLight else StatusSuccess
    val successBg = if (isDark) StatusSuccessBgDark else StatusSuccessBg

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        ChecklistKpiTile(
            title = stringResource(R.string.checklist_overview_pending),
            count = summary.pendingCount,
            subtitle = "Start / Sign-off",
            icon = Icons.Filled.HourglassTop,
            accentColor = warnAccent,
            selectedBg = warnBg,
            selected = selectedBucket == ChecklistOverviewBucket.PENDING,
            onClick = { onSelectBucket(ChecklistOverviewBucket.PENDING) },
            modifier = Modifier
                .weight(1f)
                .testTag("checklist_kpi_pending")
        )
        ChecklistKpiTile(
            title = stringResource(R.string.checklist_overview_in_progress),
            count = summary.inProgressCount,
            subtitle = if (summary.disputedCount > 0) "${summary.disputedCount} disputed" else "Draft / Review",
            icon = if (summary.disputedCount > 0) Icons.Filled.Warning else Icons.Filled.EditNote,
            accentColor = if (summary.disputedCount > 0) errAccent else infoAccent,
            selectedBg = if (summary.disputedCount > 0) errBg else infoBg,
            selected = selectedBucket == ChecklistOverviewBucket.IN_PROGRESS,
            onClick = { onSelectBucket(ChecklistOverviewBucket.IN_PROGRESS) },
            modifier = Modifier
                .weight(1f)
                .testTag("checklist_kpi_in_progress")
        )
        ChecklistKpiTile(
            title = stringResource(R.string.checklist_overview_completed),
            count = summary.completedCount,
            subtitle = "Signed & Locked",
            icon = Icons.Filled.Verified,
            accentColor = successAccent,
            selectedBg = successBg,
            selected = selectedBucket == ChecklistOverviewBucket.COMPLETED,
            onClick = { onSelectBucket(ChecklistOverviewBucket.COMPLETED) },
            modifier = Modifier
                .weight(1f)
                .testTag("checklist_kpi_completed")
        )
    }
}

@Composable
private fun ChecklistKpiTile(
    title: String,
    count: Int,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    selectedBg: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) selectedBg else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .heightIn(min = 88.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {}
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChecklistFilterChipStrip(
    summary: ChecklistOverviewSummary,
    filterState: ChecklistOverviewFilterState,
    onBucketFilterToggle: (ChecklistOverviewBucket?) -> Unit,
    onPropertyFilterToggle: (String?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item(key = "chip_all") {
            FilterChip(
                selected = filterState.bucket == null,
                onClick = { onBucketFilterToggle(null) },
                label = { Text("All (${summary.totalCount})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("checklist_filter_chip_all")
            )
        }
        item(key = "chip_pending") {
            FilterChip(
                selected = filterState.bucket == ChecklistOverviewBucket.PENDING,
                onClick = { onBucketFilterToggle(ChecklistOverviewBucket.PENDING) },
                label = { Text("Pending (${summary.pendingCount})") },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("checklist_filter_chip_pending")
            )
        }
        item(key = "chip_in_progress") {
            FilterChip(
                selected = filterState.bucket == ChecklistOverviewBucket.IN_PROGRESS,
                onClick = { onBucketFilterToggle(ChecklistOverviewBucket.IN_PROGRESS) },
                label = { Text("In Progress (${summary.inProgressCount})") },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("checklist_filter_chip_in_progress")
            )
        }
        item(key = "chip_completed") {
            FilterChip(
                selected = filterState.bucket == ChecklistOverviewBucket.COMPLETED,
                onClick = { onBucketFilterToggle(ChecklistOverviewBucket.COMPLETED) },
                label = { Text("Completed (${summary.completedCount})") },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("checklist_filter_chip_completed")
            )
        }
        if (summary.propertiesWithItems.size > 1) {
            items(summary.propertiesWithItems, key = { "prop_${it.first}" }) { (propId, propName) ->
                FilterChip(
                    selected = filterState.propertyId == propId,
                    onClick = { onPropertyFilterToggle(propId) },
                    label = { Text(propName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("checklist_property_chip_$propId")
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChecklistOverviewItemCard(
    item: ChecklistOverviewItem,
    canManage: Boolean,
    onOpenChecklist: () -> Unit,
    onOpenPdfPreview: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val stageTone = when (item.stage) {
        ChecklistWorkflowStage.COMPLETED -> PillTone.SUCCESS
        ChecklistWorkflowStage.PENDING_START,
        ChecklistWorkflowStage.PENDING_TENANT_SIGNOFF -> PillTone.WARNING
        ChecklistWorkflowStage.DRAFT -> PillTone.INFO
        ChecklistWorkflowStage.DISPUTED -> PillTone.ERROR
    }
    val errFg = if (isDark) StatusErrorLight else StatusError
    val errBg = if (isDark) StatusErrorBgDark else StatusErrorBg
    val warnFg = if (isDark) StatusWarningLight else StatusWarning
    val borderColor = if (item.stage == ChecklistWorkflowStage.DISPUTED) {
        errFg.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("checklist_overview_card_${item.lease.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${item.propertyName} • Unit ${item.unitNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tenant: ${item.tenantName.ifBlank { "Assigned Tenant" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusPill(
                    label = item.stage.label,
                    tone = stageTone
                )
            }

            if (item.checklist == null) {
                val startDateText = item.lease.startDateEpochMs.takeIf { it > 0L }
                    ?.let { "Lease started ${CurrencyUtils.formatDate(it)}" }
                    ?: "Active tenancy"
                Text(
                    text = "$startDateText • Room-by-room inspection not started yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = warnFg
                )
            } else {
                Text(
                    text = "${item.roomCount} rooms • ${item.totalItems} items • ${item.flaggedItems} flagged • ${item.photoCount} photo(s) • ${item.checklist.keysHandedOverCount} key(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SignatureMicroBadge(
                    label = if (item.landlordSigned) "Landlord Signed" else "Landlord Pending",
                    signed = item.landlordSigned
                )
                SignatureMicroBadge(
                    label = if (item.tenantSigned) "Tenant Signed" else "Tenant Pending",
                    signed = item.tenantSigned
                )
            }

            AnimatedVisibility(visible = item.stage == ChecklistWorkflowStage.DISPUTED && !item.checklist?.tenantSummary.isNullOrBlank()) {
                Surface(
                    color = errBg,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Discrepancy note: ${item.checklist?.tenantSummary.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = errFg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val ctaLabel = when {
                    !canManage -> "View Checklist"
                    item.stage == ChecklistWorkflowStage.PENDING_START -> "Start Checklist"
                    item.stage == ChecklistWorkflowStage.DRAFT -> "Continue Draft"
                    item.stage == ChecklistWorkflowStage.DISPUTED -> "Resolve Discrepancies"
                    item.stage == ChecklistWorkflowStage.PENDING_TENANT_SIGNOFF -> "Review Progress"
                    else -> "View Signed"
                }
                val isPrimaryHighlight = canManage && (
                    item.stage == ChecklistWorkflowStage.PENDING_START ||
                        item.stage == ChecklistWorkflowStage.DISPUTED ||
                        item.stage == ChecklistWorkflowStage.DRAFT
                    )

                if (isPrimaryHighlight) {
                    val isDisputed = item.stage == ChecklistWorkflowStage.DISPUTED
                    Button(
                        onClick = onOpenChecklist,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDisputed) StatusError else MaterialTheme.colorScheme.secondary,
                            contentColor = if (isDisputed) Color.White else MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("checklist_overview_action_button_${item.lease.id}")
                    ) {
                        Icon(Icons.Filled.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(ctaLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    OutlinedButton(
                        onClick = onOpenChecklist,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("checklist_overview_action_button_${item.lease.id}")
                    ) {
                        Icon(Icons.Filled.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(ctaLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (item.checklist != null) {
                    OutlinedButton(
                        onClick = onOpenPdfPreview,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("checklist_overview_pdf_button_${item.lease.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = "Open room-by-room condition report PDF viewer",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("PDF", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun SignatureMicroBadge(
    label: String,
    signed: Boolean
) {
    val isDark = LocalIsDarkTheme.current
    val successBg = if (isDark) StatusSuccessBgDark else StatusSuccessBg
    val successFg = if (isDark) StatusSuccessLight else StatusSuccess
    Surface(
        color = if (signed) successBg else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = if (signed) Icons.Filled.CheckCircle else Icons.Filled.HourglassTop,
                contentDescription = null,
                tint = if (signed) successFg else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (signed) successFg else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
