package com.example.ui.components

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.MonthlyCashFlowPoint
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.NavySecondary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorBg
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoBg
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import com.example.ui.theme.TabularMoneyStyle
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class PillTone {
    SUCCESS, WARNING, ERROR, INFO, NEUTRAL
}

@Composable
fun StatusPill(
    label: String,
    tone: PillTone,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (tone) {
        PillTone.SUCCESS -> StatusSuccessBg to StatusSuccess
        PillTone.WARNING -> StatusWarningBg to StatusWarning
        PillTone.ERROR -> StatusErrorBg to StatusError
        PillTone.INFO -> StatusInfoBg to StatusInfo
        PillTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun RentFlowKpiCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector,
    accentColor: Color = MaterialTheme.colorScheme.secondary,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .then(if (onClick != null) Modifier.minimumInteractiveComponentSize().clickable { onClick() } else Modifier)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = value,
                style = TabularMoneyStyle.copy(
                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                    lineHeight = MaterialTheme.typography.titleLarge.lineHeight
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (!actionLabel.isNullOrBlank() && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    message: String,
    ctaLabel: String? = null,
    onCtaClick: (() -> Unit)? = null,
    showIllustration: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            if (showIllustration) {
                Image(
                    painter = painterResource(id = R.drawable.img_onboarding_welcome),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(MaterialTheme.shapes.medium)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!ctaLabel.isNullOrBlank() && onCtaClick != null) {
                Button(
                    onClick = onCtaClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(ctaLabel)
                }
            }
        }
    }
}

@Composable
fun LoadingStateBox(label: String = "Syncing with RentFlow...") {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ErrorBannerCard(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = StatusErrorBg),
        border = BorderStroke(1.dp, StatusError.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = StatusError,
                modifier = Modifier.weight(1f)
            )
            if (onRetry != null) {
                OutlinedButton(
                    onClick = onRetry,
                    border = BorderStroke(1.dp, StatusError.copy(alpha = 0.5f)),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = StatusError, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Retry", color = StatusError)
                }
            }
        }
    }
}

@Composable
fun AiInsightCard(
    title: String,
    subtitle: String,
    isLoading: Boolean,
    streamingOrFinalText: String?,
    errorText: String?,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "ai_insight_card"
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = title,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = onAction,
                    enabled = !isLoading,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(if (isLoading) "Analyzing..." else actionLabel)
                }
            }

            if (isLoading && streamingOrFinalText.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "Synthesizing KES rent & occupancy signals...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!errorText.isNullOrBlank()) {
                ErrorBannerCard(message = errorText, onRetry = onAction)
            }

            if (!streamingOrFinalText.isNullOrBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = streamingOrFinalText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}

data class OnboardingStepItem(
    val title: String,
    val subtitle: String,
    val completed: Boolean,
    val onAction: () -> Unit
)

@Composable
fun GuidedOnboardingCard(
    steps: List<OnboardingStepItem>,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(Brush.horizontalGradient(listOf(NavyPrimary, NavySecondary)))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "Guided Portfolio Setup",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Complete these steps to activate your Kenyan rental workspace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCCFBF1)
                    )
                }
            }
            steps.forEachIndexed { idx, step ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { step.onAction() }
                        .padding(vertical = 8.dp, horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = if (step.completed) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (step.completed) "Completed" else "Pending step ${idx + 1}",
                        tint = if (step.completed) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${idx + 1}. ${step.title}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = step.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!step.completed) {
                        OutlinedButton(
                            onClick = step.onAction,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Start")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OccupancyDonutChart(
    occupied: Int,
    vacant: Int,
    other: Int,
    modifier: Modifier = Modifier
) {
    val total = (occupied + vacant + other).coerceAtLeast(1)
    val rate = ((occupied.toFloat() / total.toFloat()) * 100f).toInt()
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .semantics { contentDescription = "Occupancy rate $rate percent" }
            ) {
                Canvas(modifier = Modifier.size(96.dp)) {
                    val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                    val diameter = size.minDimension - 16.dp.toPx()
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                    val arcSize = Size(diameter, diameter)
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke
                    )
                    if (occupied > 0) {
                        val occSweep = (occupied.toFloat() / total.toFloat()) * 360f
                        drawArc(
                            color = EmeraldAccent,
                            startAngle = -90f,
                            sweepAngle = occSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke
                        )
                    }
                }
                Text(
                    text = "$rate%",
                    style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                Text("Unit Occupancy", style = MaterialTheme.typography.titleSmall)
                LegendRow("Occupied", occupied.toString(), EmeraldAccent)
                LegendRow("Vacant", vacant.toString(), StatusInfo)
                LegendRow("Reserved / Maint.", other.toString(), StatusWarning)
            }
        }
    }
}

@Composable
private fun LegendRow(label: String, count: String, dotColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(dotColor))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(count, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun IncomeExpenseBarChart(
    points: List<MonthlyCashFlowPoint>,
    modifier: Modifier = Modifier
) {
    val maxVal = (points.maxOfOrNull { maxOf(it.incomeCents, it.expenseCents) } ?: 1L).coerceAtLeast(100L)
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Rent Income vs. Expenses", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot("Income", EmeraldAccent)
                    LegendDot("Expenses", StatusWarning)
                }
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                if (points.isEmpty()) return@Canvas
                val groupWidth = size.width / points.size
                val barWidth = (groupWidth * 0.26f).coerceAtMost(24.dp.toPx())
                val chartHeight = size.height - 8.dp.toPx()
                points.forEachIndexed { idx, pt ->
                    val centerX = groupWidth * idx + groupWidth / 2f
                    val incH = ((pt.incomeCents.toFloat() / maxVal.toFloat()) * chartHeight).coerceAtLeast(4f)
                    val expH = ((pt.expenseCents.toFloat() / maxVal.toFloat()) * chartHeight).coerceAtLeast(4f)
                    drawRoundRect(
                        color = EmeraldAccent,
                        topLeft = Offset(centerX - barWidth - 3f, size.height - incH),
                        size = Size(barWidth, incH),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawRoundRect(
                        color = StatusWarning,
                        topLeft = Offset(centerX + 3f, size.height - expH),
                        size = Size(barWidth, expH),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                points.forEach { pt ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(pt.periodLabel, style = MaterialTheme.typography.labelSmall)
                        Text(
                            CurrencyUtils.formatKesCompact(pt.incomeCents),
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldAccent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) StatusError else MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CriticalActionConfirmationDialog(
    title: String,
    impactSummary: String,
    confirmButtonText: String,
    requiredPhrase: String? = null,
    reasonLabel: String? = null,
    requireReasonMinLen: Int = 0,
    isRecentlyAuthenticated: Boolean,
    onReauthenticate: suspend (android.content.Context) -> Result<Unit>,
    onDismiss: () -> Unit,
    onConfirm: (reason: String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var reason by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var phraseInput by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var passwordInput by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var isVerifying by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var errorMessage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    val isPasswordOnlyUser = remember(isRecentlyAuthenticated) {
        runCatching {
            val user = Firebase.auth.currentUser ?: return@runCatching false
            val providers = user.providerData.map { it.providerId }
            EmailAuthProvider.PROVIDER_ID in providers && GoogleAuthProvider.PROVIDER_ID !in providers
        }.getOrDefault(false)
    }

    AlertDialog(
        onDismissRequest = { if (!isVerifying) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = StatusErrorBg,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, StatusError.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = impactSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusError,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                if (!isRecentlyAuthenticated) {
                    Surface(
                        color = StatusWarningBg,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isPasswordOnlyUser) {
                                "Security verification required: enter your account password before this action completes."
                            } else {
                                "Security verification required: you will be prompted to confirm your Google account before this action completes."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusWarning,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    if (isPasswordOnlyUser) {
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it; errorMessage = null },
                            label = { Text("Account Password") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            singleLine = true,
                            enabled = !isVerifying,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("critical_action_password_input")
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    ErrorBannerCard(errorMessage!!)
                }

                if (!reasonLabel.isNullOrBlank()) {
                    androidx.compose.material3.OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it; errorMessage = null },
                        label = { Text(reasonLabel) },
                        maxLines = 3,
                        enabled = !isVerifying,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("critical_action_reason_input")
                    )
                }

                if (!requiredPhrase.isNullOrBlank()) {
                    androidx.compose.material3.OutlinedTextField(
                        value = phraseInput,
                        onValueChange = { phraseInput = it; errorMessage = null },
                        label = { Text("Type $requiredPhrase to confirm") },
                        singleLine = true,
                        enabled = !isVerifying,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("critical_action_phrase_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedReason = reason.trim()
                    if (!reasonLabel.isNullOrBlank() && requireReasonMinLen > 0 && trimmedReason.length < requireReasonMinLen) {
                        errorMessage = "Enter at least $requireReasonMinLen characters for the audit reason."
                        return@Button
                    }
                    if (!requiredPhrase.isNullOrBlank() && !phraseInput.trim().equals(requiredPhrase, ignoreCase = true)) {
                        errorMessage = "Type $requiredPhrase exactly to continue."
                        return@Button
                    }
                    if (isRecentlyAuthenticated) {
                        onConfirm(trimmedReason)
                    } else if (isPasswordOnlyUser) {
                        if (passwordInput.isBlank()) {
                            errorMessage = "Enter your account password to verify this action."
                            return@Button
                        }
                        isVerifying = true
                        errorMessage = null
                        scope.launch {
                            val authRes = runCatching {
                                val user = Firebase.auth.currentUser
                                    ?: throw IllegalStateException("Session expired. Sign in again.")
                                val email = user.email?.trim().orEmpty()
                                user.reauthenticate(EmailAuthProvider.getCredential(email, passwordInput)).await()
                            }
                            isVerifying = false
                            authRes.fold(
                                onSuccess = { onConfirm(trimmedReason) },
                                onFailure = {
                                    errorMessage = it.localizedMessage ?: "Password verification failed. Action cancelled."
                                }
                            )
                        }
                    } else {
                        isVerifying = true
                        errorMessage = null
                        scope.launch {
                            val authRes = onReauthenticate(context)
                            isVerifying = false
                            authRes.fold(
                                onSuccess = { onConfirm(trimmedReason) },
                                onFailure = {
                                    errorMessage = it.localizedMessage ?: "Account verification failed. Action cancelled."
                                }
                            )
                        }
                    }
                },
                enabled = !isVerifying,
                colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("critical_action_confirm_button")
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Verifying...")
                } else {
                    Text(if (isRecentlyAuthenticated) confirmButtonText else "Verify & $confirmButtonText")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isVerifying,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("critical_action_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
