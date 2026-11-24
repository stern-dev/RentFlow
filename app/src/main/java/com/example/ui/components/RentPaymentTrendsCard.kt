package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.TenantMonthTrendStatus
import com.example.domain.finance.TenantPaymentTrendPoint
import com.example.domain.finance.TenantPaymentTrendsSummary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TabularMoneyStyle

@Composable
fun RentPaymentTrendsCard(
    summary: TenantPaymentTrendsSummary,
    modifier: Modifier = Modifier
) {
    val points = summary.points
    val defaultSelectedKey = remember(points) {
        points.lastOrNull { it.status != TenantMonthTrendStatus.NO_INVOICE }?.billingPeriod
            ?: points.lastOrNull()?.billingPeriod
            ?: ""
    }
    var selectedPeriodKey by remember(defaultSelectedKey) { mutableStateOf(defaultSelectedKey) }
    val selectedPoint = remember(points, selectedPeriodKey) {
        points.firstOrNull { it.billingPeriod == selectedPeriodKey } ?: points.lastOrNull()
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("tenant_payment_trends_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("12-Month Payment Trends", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "KES Paid vs. Billed history",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (summary.hasHistoricalActivity) {
                        TrendBadgeChip(
                            label = if (summary.currentStreakMonths > 0) {
                                "${summary.currentStreakMonths} mo streak"
                            } else {
                                "0 mo streak"
                            },
                            icon = if (summary.currentStreakMonths > 0) {
                                Icons.Filled.LocalFireDepartment
                            } else {
                                Icons.Filled.Verified
                            },
                            tint = if (summary.currentStreakMonths > 0) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                            containerColor = if (summary.currentStreakMonths > 0) {
                                StatusSuccess.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            },
                            testTag = "tenant_payment_streak_badge"
                        )
                        TrendBadgeChip(
                            label = "${summary.consistencyPercent}%",
                            icon = Icons.Filled.Verified,
                            tint = if (summary.consistencyPercent >= 80) MaterialTheme.colorScheme.secondary else StatusWarning,
                            containerColor = if (summary.consistencyPercent >= 80) {
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
                            } else {
                                StatusWarning.copy(alpha = 0.14f)
                            },
                            testTag = "tenant_payment_consistency_badge"
                        )
                    }
                }
            }

            if (!summary.hasHistoricalActivity || points.isEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tenant_payment_trends_empty")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BarChart,
                                contentDescription = "No payment history chart",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            "No 12-Month Billing History Yet",
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Your monthly KES invoices and verified M-Pesa payments will chart here as billing cycles are recorded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                return@Column
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TrendLegendItem("Billed", MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f))
                TrendLegendItem("Paid", MaterialTheme.colorScheme.secondary)
                TrendLegendItem("Partial / Arrears", StatusWarning)
            }

            val maxScaleCents = remember(points) {
                (points.maxOfOrNull { maxOf(it.billedCents, it.paidCents) } ?: 100L).coerceAtLeast(100L)
            }
            var animateIn by remember(points) { mutableStateOf(false) }
            LaunchedEffect(points) { animateIn = true }
            val heightProgress by animateFloatAsState(
                targetValue = if (animateIn) 1f else 0f,
                animationSpec = tween(durationMillis = 420),
                label = "trend_bar_entrance"
            )

            val scrollState = rememberScrollState()
            LaunchedEffect(points.size) {
                if (scrollState.maxValue > 0) {
                    scrollState.scrollTo(scrollState.maxValue)
                }
            }

            val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            val billedBarColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
            val selectedBgColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
            val dueBarColor = MaterialTheme.colorScheme.secondary

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columnWidth = remember(maxWidth, points.size) {
                    val count = points.size.coerceAtLeast(1)
                    val evenWidth = maxWidth / count
                    if (evenWidth >= 48.dp) evenWidth else 48.dp
                }
                val totalChartWidth = columnWidth * points.size

                Column(
                    modifier = Modifier
                        .horizontalScroll(scrollState)
                        .width(totalChartWidth)
                ) {
                    Box(
                        modifier = Modifier
                            .width(totalChartWidth)
                            .height(148.dp)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .matchParentSize()
                                .semantics {
                                    contentDescription = "12 month rent paid versus billed bar chart"
                                }
                        ) {
                            val topPad = 10.dp.toPx()
                            val bottomPad = 6.dp.toPx()
                            val usableHeight = (size.height - topPad - bottomPad).coerceAtLeast(1f)
                            val colPx = size.width / points.size.coerceAtLeast(1)
                            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

                            listOf(0f, 0.5f, 1f).forEach { fraction ->
                                val y = topPad + usableHeight * fraction
                                drawLine(
                                    color = gridLineColor,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = if (fraction == 1f) 1.5.dp.toPx() else 1.dp.toPx(),
                                    pathEffect = if (fraction < 1f) dashEffect else null
                                )
                            }

                            val pairGap = 3.dp.toPx()
                            val barWidth = (colPx * 0.28f).coerceIn(6.dp.toPx(), 16.dp.toPx())
                            val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            val baselineY = topPad + usableHeight

                            points.forEachIndexed { index, pt ->
                                val colLeft = index * colPx
                                val centerX = colLeft + colPx / 2f
                                if (pt.billingPeriod == selectedPeriodKey) {
                                    drawRoundRect(
                                        color = selectedBgColor,
                                        topLeft = Offset(colLeft + 3.dp.toPx(), 2.dp.toPx()),
                                        size = Size(colPx - 6.dp.toPx(), size.height - 4.dp.toPx()),
                                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                                    )
                                }

                                val billedRatio = (pt.billedCents.toFloat() / maxScaleCents.toFloat()).coerceIn(0f, 1f)
                                val paidRatio = (pt.paidCents.toFloat() / maxScaleCents.toFloat()).coerceIn(0f, 1f)

                                val billedHeight = if (pt.billedCents > 0L) {
                                    (billedRatio * usableHeight * heightProgress).coerceAtLeast(4.dp.toPx())
                                } else {
                                    0f
                                }
                                val paidHeight = if (pt.paidCents > 0L) {
                                    (paidRatio * usableHeight * heightProgress).coerceAtLeast(4.dp.toPx())
                                } else {
                                    0f
                                }

                                val billedLeft = centerX - barWidth - (pairGap / 2f)
                                val paidLeft = centerX + (pairGap / 2f)

                                if (billedHeight > 0f) {
                                    drawRoundRect(
                                        color = billedBarColor,
                                        topLeft = Offset(billedLeft, baselineY - billedHeight),
                                        size = Size(barWidth, billedHeight),
                                        cornerRadius = corner
                                    )
                                }

                                val paidColor = when (pt.status) {
                                    TenantMonthTrendStatus.PAID_ON_TIME,
                                    TenantMonthTrendStatus.PAID_LATE -> dueBarColor
                                    TenantMonthTrendStatus.PARTIAL -> StatusWarning
                                    TenantMonthTrendStatus.OVERDUE -> StatusError
                                    TenantMonthTrendStatus.DUE -> dueBarColor
                                    TenantMonthTrendStatus.NO_INVOICE -> billedBarColor
                                }

                                if (paidHeight > 0f) {
                                    drawRoundRect(
                                        color = paidColor,
                                        topLeft = Offset(paidLeft, baselineY - paidHeight),
                                        size = Size(barWidth, paidHeight),
                                        cornerRadius = corner
                                    )
                                } else if (pt.status == TenantMonthTrendStatus.OVERDUE) {
                                    val dotHeight = 3.dp.toPx()
                                    drawRoundRect(
                                        color = StatusError,
                                        topLeft = Offset(paidLeft, baselineY - dotHeight),
                                        size = Size(barWidth, dotHeight),
                                        cornerRadius = corner
                                    )
                                }
                            }
                        }

                        Row(modifier = Modifier.matchParentSize()) {
                            points.forEach { pt ->
                                Box(
                                    modifier = Modifier
                                        .width(columnWidth)
                                        .height(148.dp)
                                        .clickable { selectedPeriodKey = pt.billingPeriod }
                                        .testTag("trend_bar_col_${pt.billingPeriod}")
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.width(totalChartWidth)) {
                        points.forEach { pt ->
                            val isSelected = pt.billingPeriod == selectedPeriodKey
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier
                                    .width(columnWidth)
                                    .heightIn(min = 48.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { selectedPeriodKey = pt.billingPeriod }
                                    .padding(vertical = 6.dp)
                                    .testTag("trend_month_label_${pt.billingPeriod}")
                            ) {
                                Text(
                                    text = pt.shortLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (pt.status) {
                                                TenantMonthTrendStatus.PAID_ON_TIME,
                                                TenantMonthTrendStatus.PAID_LATE,
                                                TenantMonthTrendStatus.DUE -> MaterialTheme.colorScheme.secondary
                                                TenantMonthTrendStatus.PARTIAL -> StatusWarning
                                                TenantMonthTrendStatus.OVERDUE -> StatusError
                                                TenantMonthTrendStatus.NO_INVOICE -> Color.Transparent
                                            }
                                        )
                                )
                            }
                        }
                    }
                }
            }

            if (selectedPoint != null) {
                AnimatedContent(
                    targetState = selectedPoint,
                    label = "selected_trend_month"
                ) { pt ->
                    SelectedTrendMonthDetailBox(point = pt)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "12M Paid: ${CurrencyUtils.formatKes(summary.totalPaid12MonthsCents)}",
                    style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                    color = StatusSuccess
                )
                Text(
                    text = "Billed: ${CurrencyUtils.formatKes(summary.totalBilled12MonthsCents)}",
                    style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SelectedTrendMonthDetailBox(point: TenantPaymentTrendPoint) {
    val pillTone = when (point.status) {
        TenantMonthTrendStatus.PAID_ON_TIME,
        TenantMonthTrendStatus.PAID_LATE -> PillTone.SUCCESS
        TenantMonthTrendStatus.PARTIAL -> PillTone.WARNING
        TenantMonthTrendStatus.OVERDUE -> PillTone.ERROR
        TenantMonthTrendStatus.DUE -> PillTone.INFO
        TenantMonthTrendStatus.NO_INVOICE -> PillTone.NEUTRAL
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tenant_trend_selected_detail")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(point.fullLabel, style = MaterialTheme.typography.titleSmall)
                StatusPill(point.status.label, pillTone)
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                TrendMetricColumn(
                    label = "Billed",
                    value = CurrencyUtils.formatKes(point.billedCents),
                    valueColor = MaterialTheme.colorScheme.onSurface
                )
                TrendMetricColumn(
                    label = "Paid",
                    value = CurrencyUtils.formatKes(point.paidCents),
                    valueColor = StatusSuccess
                )
                TrendMetricColumn(
                    label = "Balance",
                    value = CurrencyUtils.formatKes(point.outstandingCents),
                    valueColor = if (point.outstandingCents > 0L) StatusError else MaterialTheme.colorScheme.onSurfaceVariant,
                    alignment = Alignment.End
                )
            }
        }
    }
}

@Composable
private fun TrendMetricColumn(
    label: String,
    value: String,
    valueColor: Color,
    alignment: Alignment.Horizontal = Alignment.Start
) {
    Column(horizontalAlignment = alignment) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
            color = valueColor
        )
    }
}

@Composable
private fun TrendBadgeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    containerColor: Color,
    testTag: String
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = tint
            )
        }
    }
}

@Composable
private fun TrendLegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
