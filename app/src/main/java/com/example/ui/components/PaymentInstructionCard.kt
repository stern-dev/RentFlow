package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.finance.FormattedMpesaInstruction
import com.example.domain.finance.MpesaInstructionChannel
import com.example.domain.finance.MpesaQrGenerator
import com.example.domain.finance.PaymentFormattingUtils
import com.example.domain.finance.QrMatrixData
import com.example.ui.theme.DarkBg
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.MidnightInk
import com.example.ui.theme.MpesaEmerald
import com.example.ui.theme.MpesaEmeraldBorderDark
import com.example.ui.theme.MpesaEmeraldBorderLight
import com.example.ui.theme.MpesaEmeraldContainerDark
import com.example.ui.theme.MpesaEmeraldContainerLight
import com.example.ui.theme.MpesaEmeraldLight
import com.example.ui.theme.MpesaEmeraldSurfaceDark
import com.example.ui.theme.MpesaEmeraldSurfaceLight
import com.example.ui.theme.PaperWhite
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoBg
import com.example.ui.theme.StatusInfoBgDark
import com.example.ui.theme.StatusInfoLight
import com.example.ui.theme.TabularMoneyStyle
import kotlinx.coroutines.delay

/**
 * Convenience overload that resolves M-Pesa Paybill and Buy Goods Till instructions
 * via [PaymentFormattingUtils] from landlord/unit payment settings.
 */
@Composable
fun PaymentInstructionCard(
    paybill: String?,
    till: String?,
    accountReference: String,
    amountCents: Long? = null,
    recipientName: String? = null,
    modifier: Modifier = Modifier,
    title: String = "Lipa na M-Pesa Payment Box",
    isPreview: Boolean = false
) {
    val instructions = buildList {
        PaymentFormattingUtils.buildPaybillInstructionFromReference(
            paybillNumber = paybill,
            accountReference = accountReference,
            amountCents = amountCents,
            recipientName = recipientName
        )?.let(::add)
        PaymentFormattingUtils.buildTillInstruction(
            tillNumber = till,
            amountCents = amountCents,
            recipientName = recipientName
        )?.let(::add)
    }

    PaymentInstructionCard(
        instructions = instructions,
        modifier = modifier,
        title = title,
        isPreview = isPreview
    )
}

/**
 * Branded Kenyan M-Pesa Paybill & Buy Goods Till instruction card with
 * tabular monospace shortcode boxes, one-tap copy buttons (48dp+ touch targets),
 * and full inline Lipa na M-Pesa steps.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentInstructionCard(
    instructions: List<FormattedMpesaInstruction>,
    modifier: Modifier = Modifier,
    title: String = "Lipa na M-Pesa Payment Box",
    isPreview: Boolean = false
) {
    if (instructions.isEmpty()) return

    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current

    val mpesaAccent = if (isDark) MpesaEmeraldLight else MpesaEmerald
    val mpesaOnAccent = if (isDark) DarkBg else PaperWhite
    val mpesaContainer = if (isDark) MpesaEmeraldContainerDark else MpesaEmeraldContainerLight
    val mpesaBoxSurface = if (isDark) MpesaEmeraldSurfaceDark else MpesaEmeraldSurfaceLight
    val mpesaBorder = if (isDark) MpesaEmeraldBorderDark else MpesaEmeraldBorderLight
    val previewBg = if (isDark) StatusInfoBgDark else StatusInfoBg
    val previewFg = if (isDark) StatusInfoLight else StatusInfo

    val recipientSubtitle = instructions.firstNotNullOfOrNull { it.recipientName }
    val combinedCopyable = instructions.joinToString("\n\n") { it.copyableText }
    var copiedAllRecently by remember { mutableStateOf(false) }
    var enlargedQrInstruction by remember { mutableStateOf<FormattedMpesaInstruction?>(null) }

    LaunchedEffect(copiedAllRecently) {
        if (copiedAllRecently) {
            delay(1800L)
            copiedAllRecently = false
        }
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, mpesaBorder),
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("payment_instruction_card")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(mpesaAccent)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                color = mpesaContainer,
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = "LIPA NA M-PESA",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = mpesaAccent,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            if (isPreview) {
                                Surface(
                                    color = previewBg,
                                    shape = RoundedCornerShape(999.dp),
                                    modifier = Modifier.testTag("payment_instruction_preview_badge")
                                ) {
                                    Text(
                                        text = "TENANT PREVIEW",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = previewFg,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (!recipientSubtitle.isNullOrBlank()) {
                            Text(
                                text = "Recipient: $recipientSubtitle",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            copyToClipboard(
                                context = context,
                                label = "M-Pesa Payment Details",
                                text = combinedCopyable,
                                toastMessage = "M-Pesa instructions copied"
                            )
                            copiedAllRecently = true
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("payment_instruction_copy_all")
                    ) {
                        Icon(
                            imageVector = if (copiedAllRecently) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            contentDescription = "Copy all M-Pesa instructions",
                            tint = mpesaAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (copiedAllRecently) "Copied" else "Copy All",
                            color = mpesaAccent,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                instructions.forEachIndexed { index, instruction ->
                    if (index > 0) {
                        HorizontalDivider(color = mpesaBorder.copy(alpha = 0.6f))
                    }

                    MpesaChannelSection(
                        instruction = instruction,
                        mpesaAccent = mpesaAccent,
                        mpesaOnAccent = mpesaOnAccent,
                        mpesaContainer = mpesaContainer,
                        mpesaBoxSurface = mpesaBoxSurface,
                        mpesaBorder = mpesaBorder,
                        context = context,
                        onZoomQr = { enlargedQrInstruction = instruction }
                    )
                }
            }
        }
    }

    enlargedQrInstruction?.let { zoomInstruction ->
        val zoomPayload = zoomInstruction.qrPayload.ifBlank {
            PaymentFormattingUtils.buildMpesaQrUri(
                channel = zoomInstruction.channel,
                shortcode = zoomInstruction.businessNumberRaw,
                accountReference = zoomInstruction.accountReference
            )
        }
        val zoomMatrix = remember(zoomPayload) { MpesaQrGenerator.encodeQrMatrix(zoomPayload) }
        AlertDialog(
            onDismissRequest = { enlargedQrInstruction = null },
            modifier = Modifier.testTag("payment_instruction_qr_zoom_dialog"),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCode2,
                        contentDescription = null,
                        tint = mpesaAccent
                    )
                    Text(
                        text = if (zoomInstruction.channel == MpesaInstructionChannel.PAYBILL) {
                            "Scan Paybill QR"
                        } else {
                            "Scan Buy Goods Till QR"
                        }
                    )
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (zoomMatrix != null) {
                        Surface(
                            color = PaperWhite,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, mpesaBorder),
                            modifier = Modifier.size(220.dp)
                        ) {
                            MpesaQrCanvas(
                                matrix = zoomMatrix,
                                moduleColor = MidnightInk,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            )
                        }
                    }
                    Text(
                        text = zoomInstruction.summaryLine,
                        style = TabularMoneyStyle.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = zoomPayload,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        copyToClipboard(
                            context = context,
                            label = "M-Pesa QR URI",
                            text = zoomPayload,
                            toastMessage = "M-Pesa QR link copied"
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = mpesaAccent,
                        contentColor = mpesaOnAccent
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("payment_instruction_copy_uri_button")
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy M-Pesa Link")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { enlargedQrInstruction = null },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("payment_instruction_qr_zoom_close")
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun MpesaChannelSection(
    instruction: FormattedMpesaInstruction,
    mpesaAccent: Color,
    mpesaOnAccent: Color,
    mpesaContainer: Color,
    mpesaBoxSurface: Color,
    mpesaBorder: Color,
    context: Context,
    onZoomQr: () -> Unit
) {
    val isPaybill = instruction.channel == MpesaInstructionChannel.PAYBILL
    val boxTestTag = if (isPaybill) "payment_instruction_paybill_box" else "payment_instruction_till_box"
    val stepsTestTag = if (isPaybill) "payment_instruction_steps_paybill" else "payment_instruction_steps_till"
    val copyNumberTag = if (isPaybill) "payment_instruction_copy_paybill" else "payment_instruction_copy_till"
    val channelIcon = if (isPaybill) Icons.Filled.Payments else Icons.Filled.Storefront
    val channelTitle = if (isPaybill) "Pay Bill Option" else "Buy Goods Till Option"
    val numberLabel = if (isPaybill) "Business No." else "Till Number"

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = channelIcon,
                contentDescription = null,
                tint = mpesaAccent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = channelTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Surface(
            color = mpesaBoxSurface,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, mpesaBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(boxTestTag)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                MpesaDisplayFieldRow(
                    label = numberLabel,
                    value = instruction.businessNumberFormatted,
                    accentColor = mpesaAccent,
                    onAccentColor = mpesaOnAccent,
                    pillContainerColor = mpesaContainer,
                    onCopy = {
                        copyToClipboard(
                            context = context,
                            label = if (isPaybill) "M-Pesa Paybill" else "M-Pesa Till",
                            text = instruction.businessNumberRaw,
                            toastMessage = if (isPaybill) "Paybill number copied" else "Till number copied"
                        )
                    },
                    copyContentDescription = if (isPaybill) "Tap to copy Paybill number" else "Tap to copy Till number",
                    copyButtonTestTag = copyNumberTag
                )

                if (isPaybill && !instruction.accountReference.isNullOrBlank()) {
                    HorizontalDivider(color = mpesaBorder.copy(alpha = 0.45f))
                    MpesaDisplayFieldRow(
                        label = "Account No.",
                        value = instruction.accountReference,
                        accentColor = mpesaAccent,
                        onAccentColor = mpesaOnAccent,
                        pillContainerColor = mpesaContainer,
                        onCopy = {
                            copyToClipboard(
                                context = context,
                                label = "M-Pesa Account Reference",
                                text = instruction.accountReference,
                                toastMessage = "Account reference copied"
                            )
                        },
                        copyContentDescription = "Tap to copy Account reference",
                        copyButtonTestTag = "payment_instruction_copy_account"
                    )
                }

                if (!instruction.amountKesFormatted.isNullOrBlank()) {
                    HorizontalDivider(color = mpesaBorder.copy(alpha = 0.45f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Amount Due",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = instruction.amountKesFormatted,
                            style = TabularMoneyStyle.copy(fontSize = 17.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        val resolvedQrPayload = instruction.qrPayload.ifBlank {
            PaymentFormattingUtils.buildMpesaQrUri(
                channel = instruction.channel,
                shortcode = instruction.businessNumberRaw,
                accountReference = instruction.accountReference
            )
        }
        val qrMatrix = remember(resolvedQrPayload) { MpesaQrGenerator.encodeQrMatrix(resolvedQrPayload) }
        if (qrMatrix != null) {
            MpesaInlineQrCard(
                instruction = instruction,
                qrPayload = resolvedQrPayload,
                qrMatrix = qrMatrix,
                mpesaAccent = mpesaAccent,
                mpesaContainer = mpesaContainer,
                mpesaBoxSurface = mpesaBoxSurface,
                mpesaBorder = mpesaBorder,
                onZoomQr = onZoomQr
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(stepsTestTag)
        ) {
            Text(
                text = "Lipa na M-Pesa Steps",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            instruction.steps.forEachIndexed { idx, stepText ->
                val cleanBody = stepText.replaceFirst(Regex("^\\d+\\.\\s*"), "")
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(mpesaContainer)
                    ) {
                        Text(
                            text = "${idx + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = mpesaAccent
                        )
                    }
                    Text(
                        text = cleanBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MpesaDisplayFieldRow(
    label: String,
    value: String,
    accentColor: Color,
    onAccentColor: Color,
    pillContainerColor: Color,
    onCopy: () -> Unit,
    copyContentDescription: String,
    copyButtonTestTag: String
) {
    var recentlyCopied by remember(value) { mutableStateOf(false) }

    LaunchedEffect(recentlyCopied) {
        if (recentlyCopied) {
            delay(1800L)
            recentlyCopied = false
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .heightIn(min = 48.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = copyContentDescription
            ) {
                onCopy()
                recentlyCopied = true
            }
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .testTag(copyButtonTestTag)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = TabularMoneyStyle.copy(
                    fontSize = 20.sp,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Surface(
            color = if (recentlyCopied) accentColor else pillContainerColor,
            shape = RoundedCornerShape(999.dp)
        ) {
            AnimatedContent(
                targetState = recentlyCopied,
                label = "tap_to_copy_badge"
            ) { isCopied ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = copyContentDescription,
                        tint = if (isCopied) onAccentColor else accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isCopied) "Copied" else "Tap to copy",
                        color = if (isCopied) onAccentColor else accentColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun MpesaInlineQrCard(
    instruction: FormattedMpesaInstruction,
    qrPayload: String,
    qrMatrix: QrMatrixData,
    mpesaAccent: Color,
    mpesaContainer: Color,
    mpesaBoxSurface: Color,
    mpesaBorder: Color,
    onZoomQr: () -> Unit
) {
    val isPaybill = instruction.channel == MpesaInstructionChannel.PAYBILL
    val qrTag = if (isPaybill) "payment_instruction_qr_paybill" else "payment_instruction_qr_till"
    val badgeText = if (isPaybill) "PAYBILL QR" else "TILL QR"
    val description = if (isPaybill) {
        buildString {
            append("Encodes Paybill ${instruction.businessNumberFormatted}")
            if (!instruction.accountReference.isNullOrBlank()) {
                append(", Account ${instruction.accountReference}")
            }
            if (!instruction.amountKesFormatted.isNullOrBlank()) {
                append(", ${instruction.amountKesFormatted}")
            }
        }
    } else {
        buildString {
            append("Encodes Till ${instruction.businessNumberFormatted}")
            if (!instruction.amountKesFormatted.isNullOrBlank()) {
                append(", ${instruction.amountKesFormatted}")
            }
        }
    }

    Surface(
        color = mpesaBoxSurface,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, mpesaBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(
                role = Role.Button,
                onClickLabel = "Enlarge $badgeText code"
            ) { onZoomQr() }
            .semantics {
                contentDescription = "$badgeText code for $qrPayload. Tap to enlarge."
            }
            .testTag(qrTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Surface(
                color = PaperWhite,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, mpesaBorder),
                modifier = Modifier.size(112.dp)
            ) {
                MpesaQrCanvas(
                    matrix = qrMatrix,
                    moduleColor = MidnightInk,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = mpesaContainer,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = mpesaAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = "Scan to Pay via M-Pesa",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ZoomIn,
                        contentDescription = null,
                        tint = mpesaAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Tap QR to enlarge",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = mpesaAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun MpesaQrCanvas(
    matrix: QrMatrixData,
    moduleColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val count = matrix.size
        if (count <= 0) return@Canvas
        val cellSize = size.minDimension / count.toFloat()
        val offsetX = (size.width - cellSize * count) / 2f
        val offsetY = (size.height - cellSize * count) / 2f
        for (row in 0 until count) {
            for (col in 0 until count) {
                if (matrix.isDarkAt(row, col)) {
                    drawRect(
                        color = moduleColor,
                        topLeft = Offset(offsetX + col * cellSize, offsetY + row * cellSize),
                        size = Size(cellSize + 0.5f, cellSize + 0.5f)
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(
    context: Context,
    label: String,
    text: String,
    toastMessage: String
) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}
