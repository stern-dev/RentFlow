package com.example.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.InspectionItemStatus
import com.example.domain.model.InspectionRoom
import com.example.domain.model.MoveInConditionChecklist
import com.example.domain.model.RoomInspectionItem
import com.example.ui.screens.MaintenancePhotoStrip
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorBg
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg

internal fun inspectionStatusPillTone(status: InspectionItemStatus): PillTone = when (status) {
    InspectionItemStatus.EXCELLENT, InspectionItemStatus.GOOD -> PillTone.SUCCESS
    InspectionItemStatus.FAIR -> PillTone.INFO
    InspectionItemStatus.NEEDS_REPAIR -> PillTone.WARNING
    InspectionItemStatus.DAMAGED, InspectionItemStatus.MISSING -> PillTone.ERROR
    InspectionItemStatus.NOT_APPLICABLE -> PillTone.NEUTRAL
}

@Composable
internal fun RoomSectionHeaderCard(
    room: InspectionRoom,
    inspectedCount: Int,
    canEdit: Boolean,
    canRemoveRoom: Boolean,
    onUpdateRoomRemarks: (String) -> Unit,
    onAddItemClick: () -> Unit,
    onRemoveRoomClick: () -> Unit
) {
    val allChecked = room.items.isNotEmpty() && inspectedCount == room.items.size
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (allChecked) MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("room_section_header_${room.roomId}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.MeetingRoom,
                    contentDescription = "Room section ${room.roomName}",
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = room.roomName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$inspectedCount of ${room.items.size} items checked",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (allChecked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canEdit && room.items.size < MoveInConditionChecklist.MAX_ITEMS_PER_ROOM) {
                    OutlinedButton(
                        onClick = onAddItemClick,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("add_item_in_room_${room.roomId}")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Item")
                    }
                }
                if (canRemoveRoom) {
                    IconButton(
                        onClick = onRemoveRoomClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("remove_room_${room.roomId}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Remove ${room.roomName}",
                            tint = StatusError
                        )
                    }
                }
            }

            if (canEdit) {
                OutlinedTextField(
                    value = room.roomRemarks,
                    onValueChange = { onUpdateRoomRemarks(it.take(400)) },
                    label = { Text("Room overview remarks (optional)") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("room_remarks_input_${room.roomId}")
                )
            } else if (room.roomRemarks.isNotBlank()) {
                Text(
                    text = "Room note: ${room.roomRemarks}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoomInspectionItemCard(
    roomId: String,
    item: RoomInspectionItem,
    isInspected: Boolean,
    isPhotosExpanded: Boolean,
    canLandlordEdit: Boolean,
    canTenantReview: Boolean,
    isTenantView: Boolean,
    tenantRemarkDraft: String,
    canRemoveItem: Boolean,
    isBusy: Boolean,
    onToggleInspected: (Boolean) -> Unit,
    onSelectStatus: (InspectionItemStatus) -> Unit,
    onLandlordRemarksChange: (String) -> Unit,
    onTenantRemarksChange: (String) -> Unit,
    onTogglePhotosExpanded: () -> Unit,
    onAttachPhotoClick: () -> Unit,
    onRemovePhoto: (Int) -> Unit,
    onRemoveItem: () -> Unit
) {
    val currentStatus = item.statusEnum()
    val statusTone = inspectionStatusPillTone(currentStatus)
    val isFlaggedIssue = currentStatus == InspectionItemStatus.NEEDS_REPAIR ||
        currentStatus == InspectionItemStatus.DAMAGED ||
        currentStatus == InspectionItemStatus.MISSING

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isFlaggedIssue -> StatusWarningBg.copy(alpha = 0.25f)
                isInspected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isFlaggedIssue -> StatusWarning.copy(alpha = 0.5f)
                isInspected -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inspection_item_card_${item.itemId}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Inspected Checkbox + Item Label + Condition Badge + Collapsible Photo Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = isInspected,
                    onCheckedChange = { checked -> onToggleInspected(checked) },
                    enabled = !isBusy,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.secondary,
                        checkmarkColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("item_inspected_checkbox_${item.itemId}")
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isInspected) "Inspected" else "Pending check",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isInspected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                StatusPill(label = currentStatus.label, tone = statusTone)

                if (canRemoveItem) {
                    IconButton(
                        onClick = onRemoveItem,
                        enabled = !isBusy,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("remove_item_${item.itemId}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Remove ${item.label}",
                            tint = StatusError
                        )
                    }
                }
            }

            // Condition Grade Chips
            if (canLandlordEdit) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    InspectionItemStatus.entries.forEach { option ->
                        val selected = option == currentStatus
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectStatus(option) },
                            enabled = !isBusy,
                            label = {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when (option) {
                                    InspectionItemStatus.EXCELLENT, InspectionItemStatus.GOOD ->
                                        MaterialTheme.colorScheme.secondaryContainer
                                    InspectionItemStatus.FAIR -> StatusInfo.copy(alpha = 0.16f)
                                    InspectionItemStatus.NEEDS_REPAIR -> StatusWarningBg
                                    InspectionItemStatus.DAMAGED, InspectionItemStatus.MISSING -> StatusErrorBg
                                    InspectionItemStatus.NOT_APPLICABLE -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                            modifier = Modifier
                                .testTag("item_status_chip_${item.itemId}_${option.name}")
                        )
                    }
                }
            }

            // Inline Remarks Inputs (Role-Specific)
            if (canLandlordEdit) {
                OutlinedTextField(
                    value = item.landlordRemarks,
                    onValueChange = { onLandlordRemarksChange(it.take(400)) },
                    label = { Text("Landlord / inspection remarks") },
                    placeholder = { Text("Note wall scratches, paint, taps, meter seal...") },
                    maxLines = 3,
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("item_landlord_remarks_${item.itemId}")
                )
                if (item.tenantRemarks.isNotBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = StatusWarningBg.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Tenant remark: ${item.tenantRemarks}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            } else {
                if (item.landlordRemarks.isNotBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Management remark: ${item.landlordRemarks}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }

                if (isTenantView && canTenantReview) {
                    OutlinedTextField(
                        value = tenantRemarkDraft,
                        onValueChange = { onTenantRemarksChange(it.take(400)) },
                        label = { Text("Your tenant remarks for this item (optional)") },
                        placeholder = { Text("Confirm condition or note any pre-existing defect...") },
                        maxLines = 3,
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("item_tenant_remarks_${item.itemId}")
                    )
                } else if (item.tenantRemarks.isNotBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Tenant remark: ${item.tenantRemarks}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Collapsible Photo Section Header + AnimatedVisibility Tray
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            TextButton(
                onClick = onTogglePhotosExpanded,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("item_photo_toggle_${item.itemId}")
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    tint = if (item.imageRefs.isNotEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Inspection Photos (${item.imageRefs.size}/${RoomInspectionItem.MAX_PHOTOS_PER_ITEM})",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isPhotosExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isPhotosExpanded) "Collapse photos for ${item.label}" else "Expand photos for ${item.label}"
                )
            }

            AnimatedVisibility(visible = isPhotosExpanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .testTag("item_photo_section_${item.itemId}")
                ) {
                    if (item.imageRefs.isNotEmpty()) {
                        MaintenancePhotoStrip(
                            imageRefs = item.imageRefs,
                            removable = canLandlordEdit && !isBusy,
                            onRemovePhoto = onRemovePhoto
                        )
                    } else {
                        Text(
                            text = "No compressed photos attached for this item yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (canLandlordEdit && item.imageRefs.size < RoomInspectionItem.MAX_PHOTOS_PER_ITEM) {
                        OutlinedButton(
                            onClick = onAttachPhotoClick,
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("item_add_photo_button_${item.itemId}")
                        ) {
                            Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Attach Compressed Photo (${item.imageRefs.size}/${RoomInspectionItem.MAX_PHOTOS_PER_ITEM})")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AddRoomDialog(
    onDismiss: () -> Unit,
    onConfirm: (roomName: String, firstItemLabel: String) -> Unit
) {
    var roomName by remember { mutableStateOf("") }
    var firstItemLabel by remember { mutableStateOf("Walls, Paint & Floor") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Inspection Room") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!err.isNullOrBlank()) ErrorBannerCard(err!!)
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it.take(60); err = null },
                    label = { Text("Room Name (e.g. Master Ensuite, DSQ, Balcony)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_room_name_input")
                )
                OutlinedTextField(
                    value = firstItemLabel,
                    onValueChange = { firstItemLabel = it.take(100); err = null },
                    label = { Text("First Inspection Item") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_room_first_item_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanRoom = roomName.trim()
                    val cleanItem = firstItemLabel.trim()
                    if (cleanRoom.isBlank() || cleanItem.isBlank()) {
                        err = "Enter both a room name and an initial inspection item."
                    } else {
                        onConfirm(cleanRoom, cleanItem)
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_add_room_button")
            ) { Text("Add Room") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    )
}

@Composable
internal fun AddRoomItemDialog(
    roomName: String,
    onDismiss: () -> Unit,
    onConfirm: (itemLabel: String) -> Unit
) {
    var itemLabel by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Item • $roomName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!err.isNullOrBlank()) ErrorBannerCard(err!!)
                OutlinedTextField(
                    value = itemLabel,
                    onValueChange = { itemLabel = it.take(100); err = null },
                    label = { Text("Fixture / Item Label") },
                    placeholder = { Text("e.g. Curtain Rail, Balcony Drain, Wardrobe Lock") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_item_label_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = itemLabel.trim()
                    if (clean.isBlank()) {
                        err = "Enter an item label."
                    } else {
                        onConfirm(clean)
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_add_item_button")
            ) { Text("Add Item") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    )
}

@Composable
internal fun ConfirmSignOffChecklistDialog(
    unitNumber: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign Off & Lock Reports?") },
        text = {
            Text(
                "Counter-signing Unit $unitNumber embeds your digital signature into both the Move-In Condition Report and Tenancy Lease Agreement and permanently locks both reports for move-out deposit settlement."
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_signoff_checklist_button")
            ) { Text("Acknowledge, Sign & Lock") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    )
}

@Composable
internal fun ConfirmDeleteDraftChecklistDialog(
    unitNumber: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Draft Checklist?") },
        text = {
            Text("Delete the saved room-by-room draft checklist for Unit $unitNumber? This cannot be undone.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_delete_draft_checklist_button")
            ) { Text("Delete Draft") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    )
}
