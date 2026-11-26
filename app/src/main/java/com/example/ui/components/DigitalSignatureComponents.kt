package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.finance.CurrencyUtils
import com.example.domain.model.DigitalSignature
import com.example.domain.model.Lease
import com.example.domain.model.MoveInConditionChecklist
import com.example.domain.model.UserRole
import com.example.ui.media.MaintenancePhotoProcessor
import com.example.ui.theme.BrushedSilver
import com.example.ui.theme.CoolSlateBorder
import com.example.ui.theme.MidnightInk
import com.example.ui.theme.PaperWhite
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusWarning
import java.io.ByteArrayOutputStream

@Immutable
data class SignaturePoint(
    val x: Float,
    val y: Float
)

@Immutable
data class SignatureDraftState(
    val strokes: List<List<SignaturePoint>> = emptyList(),
    val canvasWidth: Float = 480f,
    val canvasHeight: Float = 180f,
    val typedName: String = "",
    val attested: Boolean = false,
    val reusingPriorSignature: Boolean = false,
    val priorSignature: DigitalSignature? = null
) {
    fun hasSignature(): Boolean =
        (reusingPriorSignature && priorSignature != null) || strokes.isNotEmpty()
}

object DigitalSignatureCodec {
    private const val TARGET_WIDTH_PX = 480
    private const val TARGET_HEIGHT_PX = 180
    private const val FALLBACK_WIDTH_PX = 320
    private const val FALLBACK_HEIGHT_PX = 120
    private const val PNG_PREFIX = "data:image/png;base64,"
    private const val JPEG_PREFIX = "data:image/jpeg;base64,"

    fun encodeStrokesToDataUri(
        strokes: List<List<SignaturePoint>>,
        sourceWidth: Float,
        sourceHeight: Float
    ): String? {
        if (strokes.isEmpty()) return null
        val safeSrcW = sourceWidth.takeIf { it > 1f } ?: TARGET_WIDTH_PX.toFloat()
        val safeSrcH = sourceHeight.takeIf { it > 1f } ?: TARGET_HEIGHT_PX.toFloat()

        val primaryPng = renderStrokes(strokes, safeSrcW, safeSrcH, TARGET_WIDTH_PX, TARGET_HEIGHT_PX)
        val primaryBytes = ByteArrayOutputStream().use { out ->
            primaryPng.compress(Bitmap.CompressFormat.PNG, 100, out)
            primaryPng.recycle()
            out.toByteArray()
        }
        val primaryUri = "$PNG_PREFIX${Base64.encodeToString(primaryBytes, Base64.NO_WRAP)}"
        if (primaryUri.length <= DigitalSignature.MAX_SIGNATURE_IMAGE_CHARS) {
            return primaryUri
        }

        val compactBitmap = renderStrokes(strokes, safeSrcW, safeSrcH, FALLBACK_WIDTH_PX, FALLBACK_HEIGHT_PX)
        val compactPngBytes = ByteArrayOutputStream().use { out ->
            compactBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        val compactPngUri = "$PNG_PREFIX${Base64.encodeToString(compactPngBytes, Base64.NO_WRAP)}"
        if (compactPngUri.length <= DigitalSignature.MAX_SIGNATURE_IMAGE_CHARS) {
            compactBitmap.recycle()
            return compactPngUri
        }

        val jpegBytes = ByteArrayOutputStream().use { out ->
            compactBitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
            compactBitmap.recycle()
            out.toByteArray()
        }
        val jpegUri = "$JPEG_PREFIX${Base64.encodeToString(jpegBytes, Base64.NO_WRAP)}"
        return jpegUri.takeIf { it.length <= DigitalSignature.MAX_SIGNATURE_IMAGE_CHARS }
    }

    private fun renderStrokes(
        strokes: List<List<SignaturePoint>>,
        sourceWidth: Float,
        sourceHeight: Float,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(AndroidColor.WHITE)

        val scaleX = targetWidth.toFloat() / sourceWidth
        val scaleY = targetHeight.toFloat() / sourceHeight
        val paint = Paint().apply {
            color = AndroidColor.rgb(15, 30, 54)
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        val dotPaint = Paint().apply {
            color = AndroidColor.rgb(15, 30, 54)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        strokes.forEach { stroke ->
            if (stroke.isEmpty()) return@forEach
            if (stroke.size == 1) {
                val p = stroke.first()
                canvas.drawCircle(p.x * scaleX, p.y * scaleY, 2.8f, dotPaint)
            } else {
                val path = AndroidPath()
                val first = stroke.first()
                path.moveTo(first.x * scaleX, first.y * scaleY)
                for (i in 1 until stroke.size) {
                    val prev = stroke[i - 1]
                    val curr = stroke[i]
                    val midX = ((prev.x + curr.x) * 0.5f) * scaleX
                    val midY = ((prev.y + curr.y) * 0.5f) * scaleY
                    path.quadTo(prev.x * scaleX, prev.y * scaleY, midX, midY)
                }
                val last = stroke.last()
                path.lineTo(last.x * scaleX, last.y * scaleY)
                canvas.drawPath(path, paint)
            }
        }
        return bitmap
    }

    fun buildDigitalSignature(
        draft: SignatureDraftState,
        signerUid: String,
        signerRole: UserRole
    ): Result<DigitalSignature> {
        val cleanName = draft.typedName.trim().take(100)
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter your typed legal full name before signing."))
        }
        if (!draft.attested) {
            return Result.failure(
                IllegalArgumentException("Check the attestation box confirming both the Inspection Report and Lease Agreement.")
            )
        }
        val nowMs = System.currentTimeMillis()
        if (draft.reusingPriorSignature && draft.priorSignature != null) {
            return Result.success(
                draft.priorSignature.copy(
                    signerUid = signerUid,
                    signerName = cleanName,
                    signerRole = signerRole.name,
                    signedAtEpochMs = nowMs,
                    coversInspectionReport = true,
                    coversLeaseReport = true
                )
            )
        }
        val encoded = encodeStrokesToDataUri(draft.strokes, draft.canvasWidth, draft.canvasHeight)
            ?: return Result.failure(
                IllegalArgumentException("Draw your signature in the signature pad above the line before continuing.")
            )
        return Result.success(
            DigitalSignature(
                signerUid = signerUid,
                signerName = cleanName,
                signerRole = signerRole.name,
                signedAtEpochMs = nowMs,
                signatureImageRef = encoded,
                coversInspectionReport = true,
                coversLeaseReport = true
            )
        )
    }
}

@Composable
fun LeaseAgreementSummaryAccordionCard(
    lease: Lease?,
    checklist: MoveInConditionChecklist?,
    onExportLeasePdf: ((Lease) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (lease == null) return
    var expanded by remember { mutableStateOf(false) }

    val landlordSigned = lease.landlordSignature != null || checklist?.landlordSignature != null
    val tenantSigned = lease.tenantSignature != null || checklist?.tenantSignature != null
    val execLabel = when {
        landlordSigned && tenantSigned -> "Mutually Signed"
        landlordSigned -> "Landlord Signed"
        else -> "Awaiting Signatures"
    }
    val execTone = when {
        landlordSigned && tenantSigned -> PillTone.SUCCESS
        landlordSigned -> PillTone.WARNING
        else -> PillTone.INFO
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("combined_lease_summary_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Gavel,
                    contentDescription = "Linked Tenancy Lease Agreement",
                    tint = MaterialTheme.colorScheme.secondary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Linked Tenancy Lease Agreement",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "${CurrencyUtils.formatKes(lease.rentCents)}/mo • Deposit ${CurrencyUtils.formatKes(lease.depositCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                StatusPill(execLabel, execTone)
            }

            Text(
                text = "Term: ${CurrencyUtils.formatDate(lease.startDateEpochMs)} – ${CurrencyUtils.formatDate(lease.endDateEpochMs)} • Due Day ${lease.dueDayOfMonth}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("toggle_lease_terms_button")
                ) {
                    Text(if (expanded) "Hide Full Lease Terms" else "Review Lease Terms")
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse lease terms" else "Expand lease terms"
                    )
                }
                if (onExportLeasePdf != null) {
                    OutlinedButton(
                        onClick = { onExportLeasePdf(lease) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("lease_agreement_inline_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = "Export Lease Agreement PDF",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Lease PDF")
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expanded_lease_terms_content")
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    Text(
                        text = "Tenant: ${lease.tenantName} (${lease.tenantPhone})",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Property & Unit: ${lease.propertyName} • Unit ${lease.unitNumber}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Terms Summary: ${lease.termsSummary.ifBlank { "Standard Kenyan residential tenancy terms apply." }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DigitalSignaturePadCard(
    roleKey: String,
    title: String,
    subtitle: String,
    attestationLabel: String,
    draftState: SignatureDraftState,
    onDraftChange: (SignatureDraftState) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var activeStroke by remember(roleKey) { mutableStateOf<List<SignaturePoint>>(emptyList()) }

    val commitActiveStroke = {
        if (activeStroke.isNotEmpty()) {
            onDraftChange(
                draftState.copy(
                    strokes = draftState.strokes + listOf(activeStroke),
                    reusingPriorSignature = false
                )
            )
            activeStroke = emptyList()
        }
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (draftState.hasSignature() && draftState.attested) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("signature_pad_card_$roleKey")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Draw,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = {
                        if (draftState.strokes.isNotEmpty()) {
                            onDraftChange(
                                draftState.copy(
                                    strokes = draftState.strokes.dropLast(1),
                                    reusingPriorSignature = false
                                )
                            )
                        }
                    },
                    enabled = enabled && draftState.strokes.isNotEmpty(),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("signature_undo_button_$roleKey")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo last signature stroke"
                    )
                }
                IconButton(
                    onClick = {
                        activeStroke = emptyList()
                        onDraftChange(
                            draftState.copy(
                                strokes = emptyList(),
                                reusingPriorSignature = false
                            )
                        )
                    },
                    enabled = enabled && (draftState.strokes.isNotEmpty() || draftState.reusingPriorSignature),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("signature_clear_button_$roleKey")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = "Clear signature pad"
                    )
                }
            }

            if (draftState.priorSignature != null && enabled) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = draftState.reusingPriorSignature,
                        onClick = {
                            onDraftChange(
                                draftState.copy(
                                    reusingPriorSignature = !draftState.reusingPriorSignature,
                                    strokes = if (!draftState.reusingPriorSignature) emptyList() else draftState.strokes
                                )
                            )
                        },
                        label = {
                            Text(
                                if (draftState.reusingPriorSignature) {
                                    "Reusing prior signature"
                                } else {
                                    "Reuse prior signature (${CurrencyUtils.formatDate(draftState.priorSignature.signedAtEpochMs)})"
                                }
                            )
                        },
                        modifier = Modifier.testTag("signature_reuse_chip_$roleKey")
                    )
                }
            }

            OutlinedTextField(
                value = draftState.typedName,
                onValueChange = { onDraftChange(draftState.copy(typedName = it.take(100))) },
                label = { Text("Typed Legal Full Name") },
                placeholder = { Text("Enter full legal name matching your ID") },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signature_name_input_$roleKey")
            )

            if (draftState.reusingPriorSignature && draftState.priorSignature != null) {
                val priorBmp = remember(draftState.priorSignature.signatureImageRef) {
                    MaintenancePhotoProcessor.decodeDataUriToImageBitmap(draftState.priorSignature.signatureImageRef)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(PaperWhite)
                        .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f), MaterialTheme.shapes.medium)
                        .padding(12.dp)
                ) {
                    if (priorBmp != null) {
                        Image(
                            bitmap = priorBmp,
                            contentDescription = "Reused signature of ${draftState.priorSignature.signerName}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = "Prior signature attached",
                            style = MaterialTheme.typography.bodySmall,
                            color = MidnightInk
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(PaperWhite)
                        .border(1.dp, CoolSlateBorder, MaterialTheme.shapes.medium)
                        .semantics { contentDescription = "Draw signature pad for $title" }
                        .testTag("signature_canvas_$roleKey")
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { newSize ->
                                val w = newSize.width.toFloat().coerceAtLeast(1f)
                                val h = newSize.height.toFloat().coerceAtLeast(1f)
                                if (draftState.canvasWidth != w || draftState.canvasHeight != h) {
                                    onDraftChange(draftState.copy(canvasWidth = w, canvasHeight = h))
                                }
                            }
                            .pointerInput(enabled, roleKey, draftState.strokes) {
                                if (!enabled) return@pointerInput
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()
                                    val start = SignaturePoint(
                                        x = down.position.x.coerceIn(0f, size.width.toFloat().coerceAtLeast(1f)),
                                        y = down.position.y.coerceIn(0f, size.height.toFloat().coerceAtLeast(1f))
                                    )
                                    activeStroke = listOf(start)
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: event.changes.firstOrNull()
                                        if (change == null || !change.pressed) {
                                            commitActiveStroke()
                                            break
                                        }
                                        change.consume()
                                        val pt = SignaturePoint(
                                            x = change.position.x.coerceIn(0f, size.width.toFloat().coerceAtLeast(1f)),
                                            y = change.position.y.coerceIn(0f, size.height.toFloat().coerceAtLeast(1f))
                                        )
                                        activeStroke = activeStroke + pt
                                    }
                                }
                            }
                    ) {
                        // Subtle signature baseline
                        val baselineY = size.height * 0.76f
                        drawLine(
                            color = BrushedSilver,
                            start = Offset(24f, baselineY),
                            end = Offset(size.width - 24f, baselineY),
                            strokeWidth = 2f
                        )

                        val allStrokes = if (activeStroke.isNotEmpty()) {
                            draftState.strokes + listOf(activeStroke)
                        } else {
                            draftState.strokes
                        }

                        allStrokes.forEach { stroke ->
                            if (stroke.size == 1) {
                                val p = stroke.first()
                                drawCircle(
                                    color = MidnightInk,
                                    radius = 4f,
                                    center = Offset(p.x, p.y)
                                )
                            } else if (stroke.size > 1) {
                                val path = Path().apply {
                                    moveTo(stroke.first().x, stroke.first().y)
                                    for (i in 1 until stroke.size) {
                                        val prev = stroke[i - 1]
                                        val curr = stroke[i]
                                        quadraticTo(
                                            prev.x,
                                            prev.y,
                                            (prev.x + curr.x) * 0.5f,
                                            (prev.y + curr.y) * 0.5f
                                        )
                                    }
                                    lineTo(stroke.last().x, stroke.last().y)
                                }
                                drawPath(
                                    path = path,
                                    color = MidnightInk,
                                    style = Stroke(
                                        width = 5f,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }

                    if (draftState.strokes.isEmpty() && activeStroke.isEmpty()) {
                        Text(
                            text = "Sign above the line with your finger",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(bottom = 16.dp)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(enabled = enabled) {
                        onDraftChange(draftState.copy(attested = !draftState.attested))
                    }
            ) {
                Checkbox(
                    checked = draftState.attested,
                    onCheckedChange = { checked ->
                        onDraftChange(draftState.copy(attested = checked))
                    },
                    enabled = enabled,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("signature_attestation_checkbox_$roleKey")
                )
                Text(
                    text = attestationLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignedSignaturePreviewCard(
    roleTitle: String,
    signature: DigitalSignature?,
    pendingMessage: String,
    testTagPrefix: String,
    modifier: Modifier = Modifier
) {
    val bmp: ImageBitmap? = remember(signature?.signatureImageRef) {
        signature?.signatureImageRef?.let { MaintenancePhotoProcessor.decodeDataUriToImageBitmap(it) }
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (signature != null) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_preview_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = roleTitle,
                    tint = if (signature != null) MaterialTheme.colorScheme.secondary else StatusWarning
                )
                Text(
                    text = roleTitle,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    label = if (signature != null) "Signed" else "Pending",
                    tone = if (signature != null) PillTone.SUCCESS else PillTone.WARNING
                )
            }

            if (signature == null) {
                Text(
                    text = pendingMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(PaperWhite)
                        .border(1.dp, CoolSlateBorder, MaterialTheme.shapes.small)
                        .padding(8.dp)
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "Digital signature of ${signature.signerName}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = "Digitally Executed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MidnightInk
                        )
                    }
                }
                Text(
                    text = "${signature.signerName} (${signature.signerRoleEnum().label})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("${testTagPrefix}_signer_name")
                )
                Text(
                    text = "Signed on ${CurrencyUtils.formatDateTime(signature.signedAtEpochMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusInfo
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (signature.coversInspectionReport) {
                        StatusPill("Inspection Report", PillTone.SUCCESS)
                    }
                    if (signature.coversLeaseReport) {
                        StatusPill("Lease Report", PillTone.INFO)
                    }
                }
            }
        }
    }
}
