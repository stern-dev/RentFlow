package com.example.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.ReportExporter
import com.example.domain.model.ChecklistCondition
import com.example.domain.model.ChecklistItem
import com.example.domain.model.Lease
import com.example.domain.model.MoveInChecklist
import com.example.domain.model.MoveInChecklistStatus
import com.example.ui.media.MaintenancePhotoProcessor
import com.example.ui.screens.MaintenancePhotoStrip
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorBg
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun MoveInChecklistSummaryCard(
    checklist: MoveInChecklist?,
    isTenantView: Boolean,
    propertyUnitLabel: String,
    onOpenChecklist: () -> Unit,
    onExportPdf: ((MoveInChecklist) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val status = checklist?.statusEnum()
    val tone = when (status) {
        MoveInChecklistStatus.ACKNOWLEDGED -> PillTone.SUCCESS
        MoveInChecklistStatus.PENDING_TENANT_REVIEW -> PillTone.WARNING
        MoveInChecklistStatus.DISPUTED -> PillTone.ERROR
        MoveInChecklistStatus.DRAFT -> PillTone.INFO
        null -> PillTone.NEUTRAL
    }
    val needsUrgentAction = (isTenantView && status == MoveInChecklistStatus.PENDING_TENANT_REVIEW) ||
        (!isTenantView && status == MoveInChecklistStatus.DISPUTED)

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (needsUrgentAction) {
                StatusWarningBg.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (needsUrgentAction) StatusWarning.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag(if (isTenantView) "tenant_move_in_checklist_card" else "landlord_move_in_checklist_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Checklist,
                    contentDescription = "Move-in condition checklist",
                    tint = if (needsUrgentAction) StatusWarning else EmeraldAccent
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Move-In Condition Checklist", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = propertyUnitLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(status?.label ?: "Not Started", tone)
            }

            if (checklist == null) {
                Text(
                    text = if (isTenantView) {
                        "Your landlord has not published a move-in condition checklist yet."
                    } else {
                        "Document baseline walls, plumbing, KPLC token meter & key count before handover."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val issueCount = checklist.items.count {
                    it.conditionEnum() == ChecklistCondition.NEEDS_REPAIR ||
                        it.conditionEnum() == ChecklistCondition.DAMAGED
                }
                Text(
                    text = "${checklist.items.size} items inspected • $issueCount flagged • ${checklist.keysHandedOverCount} key(s) issued",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!checklist.tenantResponseNote.isNullOrBlank()) {
                    Text(
                        text = "Tenant note: ${checklist.tenantResponseNote}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status == MoveInChecklistStatus.DISPUTED) StatusError else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onOpenChecklist,
                    enabled = checklist != null || !isTenantView,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (needsUrgentAction) StatusWarning else MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag(if (isTenantView) "tenant_open_checklist_button" else "landlord_open_checklist_button")
                ) {
                    Text(
                        when {
                            checklist == null -> if (isTenantView) "Waiting on Landlord" else "Start Checklist"
                            isTenantView && status == MoveInChecklistStatus.PENDING_TENANT_REVIEW -> "Review & Sign Off"
                            !isTenantView && status == MoveInChecklistStatus.DISPUTED -> "Resolve Discrepancy"
                            status == MoveInChecklistStatus.ACKNOWLEDGED -> "View Signed Report"
                            else -> "Edit / View Checklist"
                        }
                    )
                }
                if (checklist != null && onExportPdf != null) {
                    OutlinedButton(
                        onClick = { onExportPdf(checklist) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = "Export checklist PDF", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LandlordMoveInChecklistDialog(
    lease: Lease,
    existingChecklist: MoveInChecklist?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        waterMeter: String?,
        electricityMeter: String?,
        keysCount: Long,
        items: List<ChecklistItem>,
        imageRefs: List<String>,
        landlordNotes: String?,
        submitToTenant: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isLocked = existingChecklist?.statusEnum() == MoveInChecklistStatus.ACKNOWLEDGED

    val items = remember(existingChecklist?.id, lease.id) {
        mutableStateListOf<ChecklistItem>().apply {
            addAll(
                existingChecklist?.items?.takeIf { it.isNotEmpty() }
                    ?: MoveInChecklist.defaultKenyanItems()
            )
        }
    }
    var waterMeter by remember(existingChecklist?.id) { mutableStateOf(existingChecklist?.waterMeterReading.orEmpty()) }
    var powerMeter by remember(existingChecklist?.id) { mutableStateOf(existingChecklist?.electricityMeterReading.orEmpty()) }
    var keysCount by remember(existingChecklist?.id) { mutableStateOf((existingChecklist?.keysHandedOverCount ?: 2L).toString()) }
    var landlordNotes by remember(existingChecklist?.id) { mutableStateOf(existingChecklist?.landlordNotes.orEmpty()) }
    val imageRefs = remember(existingChecklist?.id) {
        mutableStateListOf<String>().apply {
            existingChecklist?.imageRefs?.let { addAll(it) }
        }
    }

    var newArea by remember { mutableStateOf("") }
    var newLabel by remember { mutableStateOf("") }
    var showAddRow by remember { mutableStateOf(false) }
    var isProcessingPhotos by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val handlePickedUris: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) {
            isProcessingPhotos = true
            scope.launch {
                val remaining = (MaintenancePhotoProcessor.MAX_PHOTOS - imageRefs.size).coerceAtLeast(0)
                val encoded = MaintenancePhotoProcessor.compressUrisToDataUris(context, uris, remaining)
                val merged = (imageRefs + encoded).take(MaintenancePhotoProcessor.MAX_PHOTOS)
                val check = MaintenancePhotoProcessor.validateImageRefsPayload(merged)
                check.fold(
                    onSuccess = {
                        imageRefs.clear()
                        imageRefs.addAll(it)
                        errorText = null
                    },
                    onFailure = { errorText = it.localizedMessage ?: "Selected photos exceed size limits." }
                )
                isProcessingPhotos = false
            }
        }
    }

    val multiPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MaintenancePhotoProcessor.MAX_PHOTOS)
    ) { handlePickedUris(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Move-In Checklist • Unit ${lease.unitNumber}")
                Text(
                    "${lease.propertyName} • ${lease.tenantName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isLocked) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Text(
                                "Signed off & locked by ${lease.tenantName}. You can export the official PDF report.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (existingChecklist?.statusEnum() == MoveInChecklistStatus.DISPUTED &&
                    !existingChecklist.tenantResponseNote.isNullOrBlank()
                ) {
                    Surface(
                        color = StatusErrorBg,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, StatusError.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = StatusError)
                            Column {
                                Text("Tenant Flagged Discrepancy", style = MaterialTheme.typography.labelLarge, color = StatusError)
                                Text(existingChecklist.tenantResponseNote, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                if (!errorText.isNullOrBlank()) {
                    ErrorBannerCard(errorText!!)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = waterMeter,
                        onValueChange = { waterMeter = it.take(40) },
                        label = { Text("Water Meter") },
                        singleLine = true,
                        enabled = !isLocked && !isBusy,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = powerMeter,
                        onValueChange = { powerMeter = it.take(40) },
                        label = { Text("KPLC / Token Meter") },
                        singleLine = true,
                        enabled = !isLocked && !isBusy,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = keysCount,
                    onValueChange = { v -> keysCount = v.filter { it.isDigit() }.take(2) },
                    label = { Text("Keys Handed Over") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !isLocked && !isBusy,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Room & Fixture Inspection (${items.size})", style = MaterialTheme.typography.titleSmall)
                    if (!isLocked && items.size < 30) {
                        TextButton(onClick = { showAddRow = !showAddRow }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (showAddRow) "Hide" else "Add Item")
                        }
                    }
                }

                AnimatedVisibility(visible = showAddRow && !isLocked) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newArea,
                                onValueChange = { newArea = it.take(60) },
                                label = { Text("Room / Area (e.g. Balcony)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newLabel,
                                onValueChange = { newLabel = it.take(100) },
                                label = { Text("Fixture / Item (e.g. Sliding Door)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (newLabel.isBlank()) {
                                        errorText = "Enter an inspection item label."
                                    } else {
                                        items.add(
                                            ChecklistItem(
                                                itemId = "custom_${UUID.randomUUID().toString().take(8)}",
                                                area = newArea.trim().ifBlank { "General" },
                                                label = newLabel.trim(),
                                                condition = ChecklistCondition.GOOD.name,
                                                notes = ""
                                            )
                                        )
                                        newArea = ""
                                        newLabel = ""
                                        showAddRow = false
                                        errorText = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) { Text("Add to Checklist") }
                        }
                    }
                }

                items.forEachIndexed { idx, item ->
                    Card(
                        shape = MaterialTheme.shapes.small,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.area, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                    Text(item.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                                if (!isLocked && items.size > 1) {
                                    IconButton(onClick = { items.removeAt(idx) }) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove item", tint = StatusError)
                                    }
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                ChecklistCondition.entries.forEach { cond ->
                                    FilterChip(
                                        selected = item.conditionEnum() == cond,
                                        onClick = {
                                            if (!isLocked && !isBusy) {
                                                items[idx] = item.copy(condition = cond.name)
                                            }
                                        },
                                        enabled = !isLocked && !isBusy,
                                        label = { Text(cond.label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = item.notes,
                                onValueChange = { note ->
                                    if (!isLocked) items[idx] = item.copy(notes = note.take(300))
                                },
                                label = { Text("Condition notes (optional)") },
                                singleLine = true,
                                enabled = !isLocked && !isBusy,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = landlordNotes,
                    onValueChange = { landlordNotes = it.take(800) },
                    label = { Text("Handover Remarks") },
                    maxLines = 3,
                    enabled = !isLocked && !isBusy,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Handover Condition Photos (${imageRefs.size}/${MaintenancePhotoProcessor.MAX_PHOTOS})", style = MaterialTheme.typography.titleSmall)
                if (isProcessingPhotos) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (imageRefs.isNotEmpty()) {
                    MaintenancePhotoStrip(
                        imageRefs = imageRefs,
                        removable = !isLocked && !isBusy,
                        onRemovePhoto = { idx -> if (idx in imageRefs.indices) imageRefs.removeAt(idx) }
                    )
                }
                if (!isLocked) {
                    OutlinedButton(
                        onClick = {
                            multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        enabled = !isBusy && !isProcessingPhotos && imageRefs.size < MaintenancePhotoProcessor.MAX_PHOTOS,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Attach Handover Photos")
                    }
                }

                if (existingChecklist != null) {
                    OutlinedButton(
                        onClick = { exportMoveInChecklistPdf(context, existingChecklist) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export Move-In Report (PDF)")
                    }
                }
            }
        },
        confirmButton = {
            if (!isLocked) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val keys = keysCount.toLongOrNull() ?: 0L
                            onSave(waterMeter, powerMeter, keys, items.toList(), imageRefs.toList(), landlordNotes, false)
                        },
                        enabled = !isBusy && !isProcessingPhotos,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("checklist_save_draft_button")
                    ) {
                        Text("Draft")
                    }
                    Button(
                        onClick = {
                            val keys = keysCount.toLongOrNull() ?: 0L
                            onSave(waterMeter, powerMeter, keys, items.toList(), imageRefs.toList(), landlordNotes, true)
                        },
                        enabled = !isBusy && !isProcessingPhotos,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("checklist_send_tenant_button")
                    ) {
                        Text("Send for Sign-Off")
                    }
                }
            } else {
                Button(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
            }
        },
        dismissButton = {
            if (!isLocked) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun TenantMoveInChecklistReviewDialog(
    checklist: MoveInChecklist,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onRespond: (acknowledge: Boolean, responseNote: String?) -> Unit
) {
    val context = LocalContext.current
    val canRespond = checklist.statusEnum() == MoveInChecklistStatus.PENDING_TENANT_REVIEW
    var tenantNote by remember(checklist.id) { mutableStateOf(checklist.tenantResponseNote.orEmpty()) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Move-In Inspection • Unit ${checklist.unitNumber}")
                StatusPill(
                    label = checklist.statusEnum().label,
                    tone = when (checklist.statusEnum()) {
                        MoveInChecklistStatus.ACKNOWLEDGED -> PillTone.SUCCESS
                        MoveInChecklistStatus.PENDING_TENANT_REVIEW -> PillTone.WARNING
                        MoveInChecklistStatus.DISPUTED -> PillTone.ERROR
                        MoveInChecklistStatus.DRAFT -> PillTone.INFO
                    }
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!err.isNullOrBlank()) ErrorBannerCard(err!!)

                Text(
                    "Inspected on ${CurrencyUtils.formatDate(checklist.inspectionDateEpochMs)} • ${checklist.keysHandedOverCount} key(s) issued",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!checklist.waterMeterReading.isNullOrBlank() || !checklist.electricityMeterReading.isNullOrBlank()) {
                    Text(
                        "Starting Meters — Water: ${checklist.waterMeterReading ?: "N/A"} • Power: ${checklist.electricityMeterReading ?: "N/A"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (!checklist.landlordNotes.isNullOrBlank()) {
                    Text("Management remarks: ${checklist.landlordNotes}", style = MaterialTheme.typography.bodySmall)
                }

                checklist.items.forEach { item ->
                    val cond = item.conditionEnum()
                    val tone = when (cond) {
                        ChecklistCondition.GOOD -> PillTone.SUCCESS
                        ChecklistCondition.FAIR -> PillTone.INFO
                        ChecklistCondition.NEEDS_REPAIR -> PillTone.WARNING
                        ChecklistCondition.DAMAGED -> PillTone.ERROR
                    }
                    Card(
                        shape = MaterialTheme.shapes.small,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.area, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                    Text(item.label, style = MaterialTheme.typography.bodyMedium)
                                }
                                StatusPill(cond.label, tone)
                            }
                            if (item.notes.isNotBlank()) {
                                Text(item.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                if (checklist.imageRefs.isNotEmpty()) {
                    Text("Handover Photos", style = MaterialTheme.typography.titleSmall)
                    MaintenancePhotoStrip(imageRefs = checklist.imageRefs, removable = false)
                }

                if (canRespond) {
                    OutlinedTextField(
                        value = tenantNote,
                        onValueChange = {
                            tenantNote = it.take(800)
                            err = null
                        },
                        label = { Text("Comments / Discrepancy Notes (required if flagging)") },
                        maxLines = 3,
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("tenant_checklist_note_input")
                    )
                } else if (!checklist.tenantResponseNote.isNullOrBlank()) {
                    Text("Your response: ${checklist.tenantResponseNote}", style = MaterialTheme.typography.bodySmall)
                }

                OutlinedButton(
                    onClick = { exportMoveInChecklistPdf(context, checklist) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Download Condition Report (PDF)")
                }
            }
        },
        confirmButton = {
            if (canRespond) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (tenantNote.trim().length < 3) {
                                err = "Describe the discrepancy before flagging."
                            } else {
                                onRespond(false, tenantNote.trim())
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("tenant_flag_checklist_button")
                    ) { Text("Flag Issue", color = StatusError) }
                    Button(
                        onClick = { onRespond(true, tenantNote.trim().takeIf { it.isNotBlank() }) },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("tenant_acknowledge_checklist_button")
                    ) { Text("Sign Off") }
                }
            } else {
                Button(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
            }
        },
        dismissButton = {
            if (canRespond) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Later") }
            }
        }
    )
}

fun exportMoveInChecklistPdf(context: Context, checklist: MoveInChecklist) {
    val lines = buildList {
        add("[H]HANDOVER METADATA")
        add("Property & Unit: ${checklist.propertyName} • Unit ${checklist.unitNumber}")
        add("Tenant: ${checklist.tenantName}")
        add("Inspection Date: ${CurrencyUtils.formatDate(checklist.inspectionDateEpochMs)}")
        add("Status: ${checklist.statusEnum().label}")
        add("Keys Handed Over: ${checklist.keysHandedOverCount}")
        add("Water Meter: ${checklist.waterMeterReading ?: "Not Recorded"}")
        add("Power / Token Meter: ${checklist.electricityMeterReading ?: "Not Recorded"}")
        if (checklist.acknowledgedAtEpochMs != null) {
            add("Tenant Signed Off On: ${CurrencyUtils.formatDate(checklist.acknowledgedAtEpochMs)}")
        }
        add("---")
        add("[H]ROOM & FIXTURE CONDITION")
        checklist.items.forEach { item ->
            val notePart = if (item.notes.isNotBlank()) " — ${item.notes}" else ""
            add("${item.area} | ${item.label}: ${item.conditionEnum().label}$notePart")
        }
        if (!checklist.landlordNotes.isNullOrBlank() || !checklist.tenantResponseNote.isNullOrBlank()) {
            add("---")
            add("[H]REMARKS")
            checklist.landlordNotes?.takeIf { it.isNotBlank() }?.let { add("Management: $it") }
            checklist.tenantResponseNote?.takeIf { it.isNotBlank() }?.let { add("Tenant: $it") }
        }
    }
    ReportExporter.exportPdfDocument(
        context = context,
        fileName = "move_in_checklist_${checklist.unitNumber}.pdf",
        title = "RentFlow Move-In Condition Report",
        subtitle = "${checklist.propertyName} • Unit ${checklist.unitNumber}",
        lines = lines
    )
}
