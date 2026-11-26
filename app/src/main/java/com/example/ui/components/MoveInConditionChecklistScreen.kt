package com.example.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.repository.MoveInChecklistRepository
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.PdfSignatureBlock
import com.example.domain.finance.ReportExporter
import com.example.domain.model.DigitalSignature
import com.example.domain.model.InspectionItemStatus
import com.example.domain.model.InspectionRoom
import com.example.domain.model.Lease
import com.example.domain.model.MoveInChecklistStatus
import com.example.domain.model.MoveInConditionChecklist
import com.example.domain.model.RoomInspectionItem
import com.example.domain.model.UserRole
import com.example.ui.media.MaintenancePhotoProcessor
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import com.example.ui.theme.StatusWarningBgDark
import com.example.ui.theme.StatusWarningLight
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun MoveInConditionChecklistSummaryCard(
    checklist: MoveInConditionChecklist?,
    isTenantView: Boolean,
    propertyUnitLabel: String,
    onOpenChecklist: () -> Unit,
    onExportPdf: ((MoveInConditionChecklist) -> Unit)? = null,
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

    val isDark = LocalIsDarkTheme.current
    val warnBg = if (isDark) StatusWarningBgDark else StatusWarningBg
    val warnFg = if (isDark) StatusWarningLight else StatusWarning
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (needsUrgentAction) {
                warnBg.copy(alpha = 0.75f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (needsUrgentAction) warnFg.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag(if (isTenantView) "tenant_condition_checklist_card" else "landlord_condition_checklist_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.MeetingRoom,
                    contentDescription = "Room-by-room move-in condition checklist",
                    tint = if (needsUrgentAction) warnFg else MaterialTheme.colorScheme.secondary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Room-by-Room Move-In Checklist", style = MaterialTheme.typography.titleSmall)
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
                        "Your landlord has not published a room-by-room inspection report yet."
                    } else {
                        "Walk through each room, check off fixtures, record remarks, and attach up to 2 photos per item."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val allItems = checklist.rooms.flatMap { it.items }
                val issueCount = allItems.count {
                    val s = it.statusEnum()
                    s == InspectionItemStatus.NEEDS_REPAIR ||
                        s == InspectionItemStatus.DAMAGED ||
                        s == InspectionItemStatus.MISSING
                }
                val photoCount = checklist.totalPhotoCount()
                Text(
                    text = "${checklist.rooms.size} rooms • ${allItems.size} items • $issueCount flagged • $photoCount photo(s) • ${checklist.keysHandedOverCount} key(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!checklist.tenantSummary.isNullOrBlank()) {
                    Text(
                        text = "Tenant summary: ${checklist.tenantSummary}",
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
                        .testTag(if (isTenantView) "tenant_open_condition_checklist_button" else "landlord_open_condition_checklist_button")
                ) {
                    Text(
                        when {
                            checklist == null -> if (isTenantView) "Waiting on Landlord" else "Start Room Checklist"
                            isTenantView && status == MoveInChecklistStatus.PENDING_TENANT_REVIEW -> "Review & Sign Off"
                            !isTenantView && status == MoveInChecklistStatus.DISPUTED -> "Resolve Discrepancies"
                            status == MoveInChecklistStatus.ACKNOWLEDGED -> "View Signed Report"
                            else -> "Open Room Checklist"
                        }
                    )
                }
                if (checklist != null && onExportPdf != null) {
                    OutlinedButton(
                        onClick = { onExportPdf(checklist) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("condition_checklist_card_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = "Export room-by-room condition report PDF",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MoveInConditionChecklistFullScreenDialog(
    lease: Lease?,
    existingChecklist: MoveInConditionChecklist?,
    isTenantView: Boolean,
    canDeleteDraft: Boolean,
    isBusy: Boolean,
    currentUserUid: String = "",
    currentUserName: String = "",
    currentUserRole: UserRole = if (isTenantView) UserRole.TENANT else UserRole.LANDLORD,
    onDismiss: () -> Unit,
    onSaveLandlordChecklist: (
        waterMeter: String?,
        powerMeter: String?,
        keysCount: Long,
        rooms: List<InspectionRoom>,
        landlordSummary: String?,
        submitForTenantSignOff: Boolean,
        landlordSignature: DigitalSignature?,
        onSuccess: () -> Unit
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onDeleteDraft: (MoveInConditionChecklist, onSuccess: () -> Unit) -> Unit = { _, _ -> },
    onSubmitTenantReview: (
        checklist: MoveInConditionChecklist,
        acknowledge: Boolean,
        tenantRemarksByItemId: Map<String, String>,
        tenantSummary: String?,
        tenantSignature: DigitalSignature?,
        onSuccess: () -> Unit
    ) -> Unit = { _, _, _, _, _, _ -> }
) {
    Dialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        MoveInConditionChecklistFullScreen(
            lease = lease,
            existingChecklist = existingChecklist,
            isTenantView = isTenantView,
            canDeleteDraft = canDeleteDraft,
            isBusy = isBusy,
            currentUserUid = currentUserUid,
            currentUserName = currentUserName,
            currentUserRole = currentUserRole,
            onDismiss = onDismiss,
            onSaveLandlordChecklist = onSaveLandlordChecklist,
            onDeleteDraft = onDeleteDraft,
            onSubmitTenantReview = onSubmitTenantReview
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveInConditionChecklistFullScreen(
    lease: Lease?,
    existingChecklist: MoveInConditionChecklist?,
    isTenantView: Boolean,
    canDeleteDraft: Boolean,
    isBusy: Boolean,
    currentUserUid: String = "",
    currentUserName: String = "",
    currentUserRole: UserRole = if (isTenantView) UserRole.TENANT else UserRole.LANDLORD,
    onDismiss: () -> Unit,
    onSaveLandlordChecklist: (
        waterMeter: String?,
        powerMeter: String?,
        keysCount: Long,
        rooms: List<InspectionRoom>,
        landlordSummary: String?,
        submitForTenantSignOff: Boolean,
        landlordSignature: DigitalSignature?,
        onSuccess: () -> Unit
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onDeleteDraft: (MoveInConditionChecklist, onSuccess: () -> Unit) -> Unit = { _, _ -> },
    onSubmitTenantReview: (
        checklist: MoveInConditionChecklist,
        acknowledge: Boolean,
        tenantRemarksByItemId: Map<String, String>,
        tenantSummary: String?,
        tenantSignature: DigitalSignature?,
        onSuccess: () -> Unit
    ) -> Unit = { _, _, _, _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val statusEnum = existingChecklist?.statusEnum()
    val isLocked = statusEnum == MoveInChecklistStatus.ACKNOWLEDGED ||
        existingChecklist?.isMutuallySigned() == true
    val canLandlordEdit = !isTenantView && !isLocked
    val canTenantReview = isTenantView && statusEnum == MoveInChecklistStatus.PENDING_TENANT_REVIEW && !isLocked

    val propertyName = existingChecklist?.propertyName ?: lease?.propertyName.orEmpty()
    val unitNumber = existingChecklist?.unitNumber ?: lease?.unitNumber.orEmpty()
    val tenantName = existingChecklist?.tenantName ?: lease?.tenantName.orEmpty()
    val inspectionDateMs = existingChecklist?.inspectionDateEpochMs?.takeIf { it > 0L } ?: System.currentTimeMillis()

    val resolvedSignerUid = remember(currentUserUid, isTenantView, lease, existingChecklist) {
        currentUserUid.ifBlank {
            if (isTenantView) {
                lease?.tenantUid ?: existingChecklist?.tenantUid.orEmpty()
            } else {
                lease?.ownerUid ?: existingChecklist?.ownerUid.orEmpty()
            }
        }
    }
    val resolvedLandlordRole = if (currentUserRole in listOf(UserRole.ADMIN, UserRole.LANDLORD, UserRole.PROPERTY_MANAGER)) {
        currentUserRole
    } else {
        UserRole.LANDLORD
    }

    val resetKey = "${existingChecklist?.id.orEmpty()}_${lease?.id.orEmpty()}_${existingChecklist?.updatedAt?.seconds ?: 0L}"

    val rooms = remember(resetKey) {
        mutableStateListOf<InspectionRoom>().apply {
            val source = existingChecklist?.rooms?.takeIf { it.isNotEmpty() }
                ?: MoveInConditionChecklist.defaultKenyanRooms()
            addAll(source)
        }
    }

    var inspectedItemIds by remember(resetKey) {
        val initialChecked = existingChecklist?.rooms
            ?.flatMap { it.items }
            ?.filter { item ->
                (statusEnum != null && statusEnum != MoveInChecklistStatus.DRAFT) ||
                    item.landlordRemarks.isNotBlank() ||
                    item.tenantRemarks.isNotBlank() ||
                    item.imageRefs.isNotEmpty() ||
                    item.statusEnum() != InspectionItemStatus.GOOD
            }
            ?.map { it.itemId }
            ?.toSet()
            ?: emptySet()
        mutableStateOf(initialChecked)
    }

    var expandedPhotoItemIds by remember(resetKey) { mutableStateOf(emptySet<String>()) }

    val tenantRemarksDraft = remember(resetKey) {
        mutableStateMapOf<String, String>().apply {
            existingChecklist?.rooms?.flatMap { it.items }?.forEach { item ->
                if (item.tenantRemarks.isNotBlank()) {
                    put(item.itemId, item.tenantRemarks)
                }
            }
        }
    }

    var waterMeter by remember(resetKey) { mutableStateOf(existingChecklist?.waterMeterReading.orEmpty()) }
    var powerMeter by remember(resetKey) { mutableStateOf(existingChecklist?.electricityMeterReading.orEmpty()) }
    var keysCount by remember(resetKey) { mutableStateOf((existingChecklist?.keysHandedOverCount ?: 2L).toString()) }
    var landlordSummary by remember(resetKey) { mutableStateOf(existingChecklist?.landlordSummary.orEmpty()) }
    var tenantSummary by remember(resetKey) { mutableStateOf(existingChecklist?.tenantSummary.orEmpty()) }

    val existingLandlordSig = existingChecklist?.landlordSignature ?: lease?.landlordSignature
    val existingTenantSig = existingChecklist?.tenantSignature ?: lease?.tenantSignature

    var landlordSigDraft by remember(resetKey) {
        mutableStateOf(
            SignatureDraftState(
                typedName = existingLandlordSig?.signerName ?: currentUserName,
                priorSignature = existingLandlordSig
            )
        )
    }
    var tenantSigDraft by remember(resetKey) {
        mutableStateOf(
            SignatureDraftState(
                typedName = existingTenantSig?.signerName ?: currentUserName.ifBlank { tenantName },
                priorSignature = existingTenantSig
            )
        )
    }
    var pendingValidatedTenantSignature by remember(resetKey) { mutableStateOf<DigitalSignature?>(null) }

    var showAddRoomDialog by remember { mutableStateOf(false) }
    var roomForNewItem by remember { mutableStateOf<InspectionRoom?>(null) }
    var showConfirmSignOffDialog by remember { mutableStateOf(false) }
    var showConfirmDeleteDraftDialog by remember { mutableStateOf(false) }
    var activePhotoTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var isProcessingPhotos by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var checklistForPdfViewer by remember { mutableStateOf<MoveInConditionChecklist?>(null) }

    fun buildPreviewChecklistSnapshot(): MoveInConditionChecklist {
        val base = existingChecklist ?: MoveInConditionChecklist(
            id = lease?.id?.let { "draft_$it" } ?: "draft_preview",
            leaseId = lease?.id.orEmpty(),
            orgId = lease?.orgId.orEmpty(),
            propertyId = lease?.propertyId.orEmpty(),
            propertyName = propertyName,
            unitId = lease?.unitId.orEmpty(),
            unitNumber = unitNumber,
            ownerUid = lease?.ownerUid.orEmpty(),
            tenantUid = lease?.tenantUid.orEmpty(),
            tenantName = tenantName,
            status = MoveInChecklistStatus.DRAFT.name,
            inspectionDateEpochMs = inspectionDateMs
        )
        if (isLocked) return base
        val mergedRooms = if (canTenantReview) {
            base.rooms.map { room ->
                room.copy(
                    items = room.items.map { item ->
                        val draftRemark = tenantRemarksDraft[item.itemId]
                        if (draftRemark != null) item.copy(tenantRemarks = draftRemark) else item
                    }
                )
            }
        } else {
            rooms.toList()
        }
        return base.copy(
            propertyName = propertyName,
            unitNumber = unitNumber,
            tenantName = tenantName,
            inspectionDateEpochMs = inspectionDateMs,
            waterMeterReading = waterMeter.takeIf { it.isNotBlank() },
            electricityMeterReading = powerMeter.takeIf { it.isNotBlank() },
            keysHandedOverCount = keysCount.toLongOrNull()?.coerceIn(0L, 50L) ?: base.keysHandedOverCount,
            rooms = mergedRooms,
            landlordSummary = landlordSummary.takeIf { it.isNotBlank() } ?: base.landlordSummary,
            tenantSummary = tenantSummary.takeIf { it.isNotBlank() } ?: base.tenantSummary
        )
    }

    val totalItemsCount = rooms.sumOf { it.items.size }
    val inspectedCount = rooms.sumOf { room -> room.items.count { it.itemId in inspectedItemIds } }
    val flaggedIssuesCount = rooms.sumOf { room ->
        room.items.count {
            val st = it.statusEnum()
            st == InspectionItemStatus.NEEDS_REPAIR ||
                st == InspectionItemStatus.DAMAGED ||
                st == InspectionItemStatus.MISSING
        }
    }
    val totalPhotosCount = rooms.sumOf { room -> room.items.sumOf { it.imageRefs.size } }
    val totalPhotoChars = rooms.sumOf { room -> room.items.sumOf { item -> item.imageRefs.sumOf { it.length } } }
    val totalPhotoKb = totalPhotoChars / 1024
    val maxPhotoKb = MoveInChecklistRepository.MAX_TOTAL_DOCUMENT_PHOTO_CHARS / 1024

    val handlePickedUris: (List<Uri>) -> Unit = { uris ->
        val target = activePhotoTarget
        activePhotoTarget = null
        if (target != null && uris.isNotEmpty()) {
            val (targetRoomId, targetItemId) = target
            val targetItem = rooms.firstOrNull { it.roomId == targetRoomId }?.items?.firstOrNull { it.itemId == targetItemId }
            val remainingSlots = (RoomInspectionItem.MAX_PHOTOS_PER_ITEM - (targetItem?.imageRefs?.size ?: 0)).coerceAtLeast(0)
            if (remainingSlots <= 0) {
                errorText = "Each inspection item supports up to ${RoomInspectionItem.MAX_PHOTOS_PER_ITEM} photos."
            } else {
                isProcessingPhotos = true
                errorText = null
                scope.launch {
                    val compressed = MaintenancePhotoProcessor.compressUrisToDataUris(
                        context = context,
                        uris = uris,
                        maxCount = remainingSlots
                    )
                    isProcessingPhotos = false
                    if (compressed.isEmpty()) {
                        errorText = "Unable to read or compress selected image(s)."
                        return@launch
                    }
                    val addedChars = compressed.sumOf { it.length }
                    if (totalPhotoChars + addedChars > MoveInChecklistRepository.MAX_TOTAL_DOCUMENT_PHOTO_CHARS) {
                        errorText = "Adding these photos exceeds the $maxPhotoKb KB document payload budget. Remove older photos first."
                        return@launch
                    }
                    val roomIdx = rooms.indexOfFirst { it.roomId == targetRoomId }
                    if (roomIdx >= 0) {
                        val room = rooms[roomIdx]
                        val updatedItems = room.items.map { existing ->
                            if (existing.itemId == targetItemId) {
                                existing.copy(
                                    imageRefs = (existing.imageRefs + compressed).take(RoomInspectionItem.MAX_PHOTOS_PER_ITEM)
                                )
                            } else {
                                existing
                            }
                        }
                        rooms[roomIdx] = room.copy(items = updatedItems)
                        inspectedItemIds = inspectedItemIds + targetItemId
                        expandedPhotoItemIds = expandedPhotoItemIds + targetItemId
                    }
                }
            }
        }
    }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(RoomInspectionItem.MAX_PHOTOS_PER_ITEM)
    ) { uris ->
        handlePickedUris(uris)
    }

    BackHandler {
        if (!isBusy && !isProcessingPhotos) {
            onDismiss()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .testTag("move_in_condition_checklist_screen")
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Room-by-Room Inspection",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$propertyName • Unit $unitNumber",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { if (!isBusy && !isProcessingPhotos) onDismiss() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("condition_checklist_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close move-in condition checklist"
                            )
                        }
                    },
                    actions = {
                        val badgeStatus = statusEnum
                        StatusPill(
                            label = badgeStatus?.label ?: "New Draft",
                            tone = when (badgeStatus) {
                                MoveInChecklistStatus.ACKNOWLEDGED -> PillTone.SUCCESS
                                MoveInChecklistStatus.PENDING_TENANT_REVIEW -> PillTone.WARNING
                                MoveInChecklistStatus.DISPUTED -> PillTone.ERROR
                                MoveInChecklistStatus.DRAFT, null -> PillTone.INFO
                            }
                        )
                        if (existingChecklist != null || canLandlordEdit) {
                            IconButton(
                                onClick = { checklistForPdfViewer = buildPreviewChecklistSnapshot() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("condition_checklist_export_pdf_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Description,
                                    contentDescription = "Open room-by-room condition report PDF viewer"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            bottomBar = {
                Surface(
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        when {
                            canLandlordEdit -> {
                                if (canDeleteDraft && existingChecklist != null && statusEnum == MoveInChecklistStatus.DRAFT) {
                                    IconButton(
                                        onClick = { showConfirmDeleteDraftDialog = true },
                                        enabled = !isBusy && !isProcessingPhotos,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("condition_checklist_delete_draft_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = "Delete draft checklist",
                                            tint = StatusError
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        errorText = null
                                        val keys = keysCount.toLongOrNull()?.coerceIn(0L, 50L) ?: 0L
                                        val optionalDraftSig = DigitalSignatureCodec.buildDigitalSignature(
                                            draft = landlordSigDraft,
                                            signerUid = resolvedSignerUid,
                                            signerRole = resolvedLandlordRole
                                        ).getOrNull()
                                        onSaveLandlordChecklist(
                                            waterMeter,
                                            powerMeter,
                                            keys,
                                            rooms.toList(),
                                            landlordSummary,
                                            false,
                                            optionalDraftSig,
                                            onDismiss
                                        )
                                    },
                                    enabled = !isBusy && !isProcessingPhotos,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("condition_checklist_save_draft_button")
                                ) {
                                    Text("Save Draft", maxLines = 1)
                                }
                                Button(
                                    onClick = {
                                        val sigResult = DigitalSignatureCodec.buildDigitalSignature(
                                            draft = landlordSigDraft,
                                            signerUid = resolvedSignerUid,
                                            signerRole = resolvedLandlordRole
                                        )
                                        sigResult.onFailure { err ->
                                            errorText = err.message ?: "Complete your landlord signature before submitting."
                                            scope.launch { listState.animateScrollToItem(0) }
                                        }.onSuccess { sig ->
                                            errorText = null
                                            val keys = keysCount.toLongOrNull()?.coerceIn(0L, 50L) ?: 0L
                                            onSaveLandlordChecklist(
                                                waterMeter,
                                                powerMeter,
                                                keys,
                                                rooms.toList(),
                                                landlordSummary,
                                                true,
                                                sig,
                                                onDismiss
                                            )
                                        }
                                    },
                                    enabled = !isBusy && !isProcessingPhotos,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    modifier = Modifier
                                        .weight(1.25f)
                                        .heightIn(min = 48.dp)
                                        .testTag("condition_checklist_submit_button")
                                ) {
                                    Text(if (isBusy) "Saving..." else "Sign & Submit", maxLines = 1)
                                }
                            }
                            canTenantReview -> {
                                existingChecklist?.let { targetChecklist ->
                                    OutlinedButton(
                                        onClick = {
                                            if (tenantSummary.trim().length < 3) {
                                                errorText = "Enter an overall discrepancy note (at least 3 characters) before flagging."
                                                scope.launch { listState.animateScrollToItem(0) }
                                            } else {
                                                errorText = null
                                                onSubmitTenantReview(
                                                    targetChecklist,
                                                    false,
                                                    tenantRemarksDraft.toMap(),
                                                    tenantSummary.trim(),
                                                    null,
                                                    onDismiss
                                                )
                                            }
                                        },
                                        enabled = !isBusy,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("condition_checklist_flag_button")
                                    ) {
                                        Text("Flag Discrepancy", color = StatusError, maxLines = 1)
                                    }
                                    Button(
                                        onClick = {
                                            val sigResult = DigitalSignatureCodec.buildDigitalSignature(
                                                draft = tenantSigDraft,
                                                signerUid = resolvedSignerUid,
                                                signerRole = UserRole.TENANT
                                            )
                                            sigResult.onFailure { err ->
                                                errorText = err.message ?: "Complete your tenant counter-signature before signing off."
                                                scope.launch { listState.animateScrollToItem(0) }
                                            }.onSuccess { sig ->
                                                errorText = null
                                                pendingValidatedTenantSignature = sig
                                                showConfirmSignOffDialog = true
                                            }
                                        },
                                        enabled = !isBusy,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .heightIn(min = 48.dp)
                                            .testTag("condition_checklist_signoff_button")
                                    ) {
                                        Text(if (isBusy) "Signing..." else "Sign & Lock", maxLines = 1)
                                    }
                                }
                            }
                            else -> {
                                if (existingChecklist != null) {
                                    OutlinedButton(
                                        onClick = { checklistForPdfViewer = existingChecklist },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("condition_checklist_bottom_pdf_button")
                                    ) {
                                        Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("View PDF", maxLines = 1)
                                    }
                                }
                                Button(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("condition_checklist_done_button")
                                ) {
                                    Text("Done", maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Pinned Progress & Photo Budget Summary Strip
                Surface(
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        val progressFraction = if (totalItemsCount > 0) {
                            (inspectedCount.toFloat() / totalItemsCount.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "$inspectedCount / $totalItemsCount items inspected",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (inspectedCount == totalItemsCount && totalItemsCount > 0) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.testTag("condition_checklist_progress_label")
                            )
                            Text(
                                text = "${rooms.size} rooms • $flaggedIssuesCount flagged • $totalPhotosCount photo(s) (${totalPhotoKb}/${maxPhotoKb} KB)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (totalPhotoKb > maxPhotoKb) StatusError else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (isProcessingPhotos) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = "Compressing inspection photo...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                // Scrollable Room-by-Room LazyColumn
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("condition_checklist_lazy_column")
                ) {
                    if (!errorText.isNullOrBlank()) {
                        item(key = "error_banner") {
                            ErrorBannerCard(errorText!!)
                        }
                    }

                    val lockedChecklist = if (isLocked) existingChecklist else null
                    if (lockedChecklist != null) {
                        item(key = "locked_banner") {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                                ),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Icon(Icons.Filled.Lock, contentDescription = "Signed off and locked", tint = MaterialTheme.colorScheme.secondary)
                                    Text(
                                        text = "Permanently signed off by ${lockedChecklist.tenantName}" +
                                            (lockedChecklist.acknowledgedAtEpochMs?.let { " on ${CurrencyUtils.formatDate(it)}" } ?: "") +
                                            ". This baseline report is locked for move-out settlement.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    } else if (statusEnum == MoveInChecklistStatus.DISPUTED && !existingChecklist?.tenantSummary.isNullOrBlank()) {
                        item(key = "disputed_banner") {
                            val isDarkBanner = LocalIsDarkTheme.current
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDarkBanner) StatusWarningBgDark else StatusWarningBg.copy(alpha = 0.65f)
                                ),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Icon(Icons.Filled.Warning, contentDescription = "Discrepancy flagged", tint = StatusWarning)
                                    Column {
                                        Text(
                                            text = "Tenant Discrepancy Summary",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = existingChecklist.tenantSummary.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    } else if (isTenantView && statusEnum == MoveInChecklistStatus.PENDING_TENANT_REVIEW) {
                        item(key = "tenant_review_tip") {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = StatusInfo.copy(alpha = 0.1f)),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = StatusInfo)
                                    Text(
                                        text = "Inspect each room below, check off verified fixtures, add item remarks if anything looks different, then sign off or flag discrepancies.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

                    // Handover Meters & Summaries Card
                    item(key = "handover_metadata_card") {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Handover & Utility Readings", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "Tenant: $tenantName • Inspected ${CurrencyUtils.formatDate(inspectionDateMs)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(Icons.Filled.Checklist, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                }

                                if (canLandlordEdit) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = waterMeter,
                                            onValueChange = { waterMeter = it.take(40) },
                                            label = { Text("Water Meter") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).testTag("condition_water_meter_input")
                                        )
                                        OutlinedTextField(
                                            value = powerMeter,
                                            onValueChange = { powerMeter = it.take(40) },
                                            label = { Text("KPLC Meter") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).testTag("condition_power_meter_input")
                                        )
                                    }
                                    OutlinedTextField(
                                        value = keysCount,
                                        onValueChange = { keysCount = it.filter { ch -> ch.isDigit() }.take(2) },
                                        label = { Text("Keys / Gate Remotes Issued") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("condition_keys_count_input")
                                    )
                                    OutlinedTextField(
                                        value = landlordSummary,
                                        onValueChange = { landlordSummary = it.take(800) },
                                        label = { Text("Overall Handover Summary (optional)") },
                                        maxLines = 3,
                                        modifier = Modifier.fillMaxWidth().testTag("condition_landlord_summary_input")
                                    )
                                } else {
                                    Text(
                                        text = "Keys Issued: ${existingChecklist?.keysHandedOverCount ?: 0L} • " +
                                            "Water: ${existingChecklist?.waterMeterReading ?: "N/A"} • " +
                                            "KPLC: ${existingChecklist?.electricityMeterReading ?: "N/A"}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (!existingChecklist?.landlordSummary.isNullOrBlank()) {
                                        Text(
                                            text = "Management summary: ${existingChecklist?.landlordSummary}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (canTenantReview) {
                                    OutlinedTextField(
                                        value = tenantSummary,
                                        onValueChange = {
                                            tenantSummary = it.take(800)
                                            errorText = null
                                        },
                                        label = { Text("Overall Tenant Sign-Off / Discrepancy Summary") },
                                        placeholder = { Text("Required if flagging discrepancies (min 3 chars)") },
                                        maxLines = 3,
                                        enabled = !isBusy,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("condition_tenant_summary_input")
                                    )
                                } else if (!existingChecklist?.tenantSummary.isNullOrBlank() && statusEnum != MoveInChecklistStatus.DISPUTED) {
                                    Text(
                                        text = "Tenant summary: ${existingChecklist?.tenantSummary}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

                    // Room-by-Room Grouped Sections + Inspection Items
                    rooms.forEachIndexed { roomIndex, room ->
                        item(key = "room_header_${room.roomId}") {
                            val roomInspectedCount = room.items.count { it.itemId in inspectedItemIds }
                            RoomSectionHeaderCard(
                                room = room,
                                inspectedCount = roomInspectedCount,
                                canEdit = canLandlordEdit,
                                canRemoveRoom = canLandlordEdit && rooms.size > 1,
                                onUpdateRoomRemarks = { updatedRemark ->
                                    rooms[roomIndex] = room.copy(roomRemarks = updatedRemark)
                                },
                                onAddItemClick = { roomForNewItem = room },
                                onRemoveRoomClick = {
                                    if (rooms.size > 1) {
                                        val removedIds = room.items.map { it.itemId }.toSet()
                                        rooms.removeAt(roomIndex)
                                        inspectedItemIds = inspectedItemIds - removedIds
                                        expandedPhotoItemIds = expandedPhotoItemIds - removedIds
                                    }
                                }
                            )
                        }

                        items(
                            items = room.items,
                            key = { item -> "${room.roomId}__${item.itemId}" }
                        ) { item ->
                            val isChecked = item.itemId in inspectedItemIds
                            val isPhotosExpanded = item.itemId in expandedPhotoItemIds
                            val tenantRemarkValue = tenantRemarksDraft[item.itemId] ?: item.tenantRemarks

                            RoomInspectionItemCard(
                                roomId = room.roomId,
                                item = item,
                                isInspected = isChecked,
                                isPhotosExpanded = isPhotosExpanded,
                                canLandlordEdit = canLandlordEdit,
                                canTenantReview = canTenantReview,
                                isTenantView = isTenantView,
                                tenantRemarkDraft = tenantRemarkValue,
                                canRemoveItem = canLandlordEdit && room.items.size > 1,
                                isBusy = isBusy || isProcessingPhotos,
                                onToggleInspected = { checked ->
                                    inspectedItemIds = if (checked) {
                                        inspectedItemIds + item.itemId
                                    } else {
                                        inspectedItemIds - item.itemId
                                    }
                                },
                                onSelectStatus = { newStatus ->
                                    val updatedItems = room.items.map { existing ->
                                        if (existing.itemId == item.itemId) {
                                            existing.copy(status = newStatus.name)
                                        } else {
                                            existing
                                        }
                                    }
                                    rooms[roomIndex] = room.copy(items = updatedItems)
                                    inspectedItemIds = inspectedItemIds + item.itemId
                                },
                                onLandlordRemarksChange = { remark ->
                                    val updatedItems = room.items.map { existing ->
                                        if (existing.itemId == item.itemId) {
                                            existing.copy(landlordRemarks = remark)
                                        } else {
                                            existing
                                        }
                                    }
                                    rooms[roomIndex] = room.copy(items = updatedItems)
                                },
                                onTenantRemarksChange = { remark ->
                                    tenantRemarksDraft[item.itemId] = remark
                                },
                                onTogglePhotosExpanded = {
                                    expandedPhotoItemIds = if (isPhotosExpanded) {
                                        expandedPhotoItemIds - item.itemId
                                    } else {
                                        expandedPhotoItemIds + item.itemId
                                    }
                                },
                                onAttachPhotoClick = {
                                    activePhotoTarget = room.roomId to item.itemId
                                    multiPhotoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onRemovePhoto = { photoIdx ->
                                    val updatedItems = room.items.map { existing ->
                                        if (existing.itemId == item.itemId) {
                                            existing.copy(
                                                imageRefs = existing.imageRefs.filterIndexed { idx, _ -> idx != photoIdx }
                                            )
                                        } else {
                                            existing
                                        }
                                    }
                                    rooms[roomIndex] = room.copy(items = updatedItems)
                                },
                                onRemoveItem = {
                                    if (room.items.size > 1) {
                                        rooms[roomIndex] = room.copy(
                                            items = room.items.filterNot { it.itemId == item.itemId }
                                        )
                                        inspectedItemIds = inspectedItemIds - item.itemId
                                        expandedPhotoItemIds = expandedPhotoItemIds - item.itemId
                                    }
                                }
                            )
                        }
                    }

                    if (canLandlordEdit && rooms.size < MoveInConditionChecklist.MAX_ROOMS) {
                        item(key = "add_custom_room_footer") {
                            OutlinedButton(
                                onClick = { showAddRoomDialog = true },
                                enabled = !isBusy && !isProcessingPhotos,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("add_custom_room_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Room (${rooms.size}/${MoveInConditionChecklist.MAX_ROOMS})")
                            }
                        }
                    }

                    // Final Combined Lease & Digital Signature Sign-Off Section
                    item(key = "signature_signoff_section_header") {
                        SectionHeader(
                            title = "Digital Sign-Off & Lease Execution",
                            modifier = Modifier.testTag("signature_signoff_section")
                        )
                    }

                    if (lease != null) {
                        item(key = "combined_lease_summary_item") {
                            LeaseAgreementSummaryAccordionCard(
                                lease = lease,
                                checklist = existingChecklist,
                                onExportLeasePdf = { targetLease ->
                                    ReportExporter.exportSignedLeaseAgreementPdf(
                                        context = context,
                                        lease = targetLease,
                                        landlordFallbackSignature = existingChecklist?.landlordSignature,
                                        tenantFallbackSignature = existingChecklist?.tenantSignature
                                    )
                                }
                            )
                        }
                    }

                    item(key = "landlord_signature_item") {
                        if (canLandlordEdit) {
                            DigitalSignaturePadCard(
                                roleKey = "landlord",
                                title = "Landlord / Agent Digital Signature",
                                subtitle = "Executes both the Move-In Condition Report and Tenancy Lease Agreement before tenant sign-off.",
                                attestationLabel = "I confirm the room condition report and lease terms are accurate for both the Move-In Inspection Report and Tenancy Lease Agreement.",
                                draftState = landlordSigDraft,
                                onDraftChange = { updated ->
                                    landlordSigDraft = updated
                                    errorText = null
                                },
                                enabled = !isBusy && !isProcessingPhotos
                            )
                        } else {
                            SignedSignaturePreviewCard(
                                roleTitle = "Landlord / Authorized Agent",
                                signature = existingLandlordSig,
                                pendingMessage = "Awaiting landlord / management digital signature.",
                                testTagPrefix = "landlord_signature"
                            )
                        }
                    }

                    item(key = "tenant_signature_item") {
                        if (canTenantReview) {
                            DigitalSignaturePadCard(
                                roleKey = "tenant",
                                title = "Tenant Counter-Signature",
                                subtitle = "Counter-signs and permanently locks both the Move-In Condition Report and Tenancy Lease Agreement.",
                                attestationLabel = "I acknowledge the unit condition, remarks, and lease terms for both the Move-In Inspection Report and Tenancy Lease Agreement.",
                                draftState = tenantSigDraft,
                                onDraftChange = { updated ->
                                    tenantSigDraft = updated
                                    errorText = null
                                },
                                enabled = !isBusy
                            )
                        } else {
                            SignedSignaturePreviewCard(
                                roleTitle = "Tenant Counter-Signature",
                                signature = existingTenantSig,
                                pendingMessage = "Awaiting tenant counter-signature to lock both reports.",
                                testTagPrefix = "tenant_signature"
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddRoomDialog) {
        AddRoomDialog(
            onDismiss = { showAddRoomDialog = false },
            onConfirm = { newRoomName, firstItemLabel ->
                val newRoomId = "room_${UUID.randomUUID().toString().take(8)}"
                val newItemId = "${newRoomId}_item_${UUID.randomUUID().toString().take(6)}"
                rooms.add(
                    InspectionRoom(
                        roomId = newRoomId,
                        roomName = newRoomName,
                        items = listOf(
                            RoomInspectionItem(
                                itemId = newItemId,
                                label = firstItemLabel,
                                status = InspectionItemStatus.GOOD.name
                            )
                        )
                    )
                )
                showAddRoomDialog = false
            }
        )
    }

    roomForNewItem?.let { targetRoom ->
        AddRoomItemDialog(
            roomName = targetRoom.roomName,
            onDismiss = { roomForNewItem = null },
            onConfirm = { newLabel ->
                val rIdx = rooms.indexOfFirst { it.roomId == targetRoom.roomId }
                if (rIdx >= 0) {
                    val currentRoom = rooms[rIdx]
                    val newItem = RoomInspectionItem(
                        itemId = "${currentRoom.roomId}_item_${UUID.randomUUID().toString().take(8)}",
                        label = newLabel,
                        status = InspectionItemStatus.GOOD.name
                    )
                    rooms[rIdx] = currentRoom.copy(items = currentRoom.items + newItem)
                }
                roomForNewItem = null
            }
        )
    }

    if (showConfirmSignOffDialog && existingChecklist != null) {
        ConfirmSignOffChecklistDialog(
            unitNumber = unitNumber,
            onDismiss = { showConfirmSignOffDialog = false },
            onConfirm = {
                showConfirmSignOffDialog = false
                val validatedSig = pendingValidatedTenantSignature
                    ?: DigitalSignatureCodec.buildDigitalSignature(
                        draft = tenantSigDraft,
                        signerUid = resolvedSignerUid,
                        signerRole = UserRole.TENANT
                    ).getOrNull()
                if (validatedSig == null) {
                    errorText = "Complete your tenant counter-signature before signing off."
                    scope.launch { listState.animateScrollToItem(0) }
                } else {
                    onSubmitTenantReview(
                        existingChecklist,
                        true,
                        tenantRemarksDraft.toMap(),
                        tenantSummary.trim().takeIf { it.isNotBlank() },
                        validatedSig,
                        onDismiss
                    )
                }
            }
        )
    }

    if (showConfirmDeleteDraftDialog && existingChecklist != null) {
        ConfirmDeleteDraftChecklistDialog(
            unitNumber = unitNumber,
            onDismiss = { showConfirmDeleteDraftDialog = false },
            onConfirm = {
                showConfirmDeleteDraftDialog = false
                onDeleteDraft(existingChecklist, onDismiss)
            }
        )
    }

    checklistForPdfViewer?.let { previewChecklist ->
        MoveInChecklistPdfViewerDialog(
            checklist = previewChecklist,
            lease = lease,
            onDismiss = { checklistForPdfViewer = null }
        )
    }
}

internal fun buildMoveInConditionSignatureBlocks(
    checklist: MoveInConditionChecklist,
    lease: Lease? = null
): List<PdfSignatureBlock> {
    val landlordSig = checklist.landlordSignature ?: lease?.landlordSignature
    val tenantSig = checklist.tenantSignature ?: lease?.tenantSignature
    return listOf(
        PdfSignatureBlock("LANDLORD / AUTHORIZED AGENT", landlordSig),
        PdfSignatureBlock("TENANT", tenantSig)
    )
}

internal fun buildMoveInConditionChecklistLines(
    checklist: MoveInConditionChecklist
): List<String> = buildList {
    add("[H]HANDOVER METADATA")
    add("Property & Unit: ${checklist.propertyName} • Unit ${checklist.unitNumber}")
    add("Tenant: ${checklist.tenantName}")
    add("Inspection Date: ${CurrencyUtils.formatDate(checklist.inspectionDateEpochMs)}")
    add("Status: ${checklist.statusEnum().label}")
    add("Keys Handed Over: ${checklist.keysHandedOverCount}")
    add("Water Meter: ${checklist.waterMeterReading ?: "Not Recorded"}")
    add("Power / KPLC Meter: ${checklist.electricityMeterReading ?: "Not Recorded"}")
    if (checklist.acknowledgedAtEpochMs != null) {
        add("Tenant Signed Off On: ${CurrencyUtils.formatDate(checklist.acknowledgedAtEpochMs)}")
    }
    checklist.rooms.forEach { room ->
        add("---")
        add("[H]ROOM: ${room.roomName.uppercase()}")
        if (room.roomRemarks.isNotBlank()) {
            add("Room Remarks: ${room.roomRemarks}")
        }
        room.items.forEach { item ->
            val photoTag = if (item.imageRefs.isNotEmpty()) " [${item.imageRefs.size} photo(s)]" else ""
            add("${item.label}: ${item.statusEnum().label}$photoTag")
            if (item.landlordRemarks.isNotBlank()) {
                add("  Management Remark: ${item.landlordRemarks}")
            }
            if (item.tenantRemarks.isNotBlank()) {
                add("  Tenant Remark: ${item.tenantRemarks}")
            }
        }
    }
    if (!checklist.landlordSummary.isNullOrBlank() || !checklist.tenantSummary.isNullOrBlank()) {
        add("---")
        add("[H]OVERALL SUMMARIES")
        checklist.landlordSummary?.takeIf { it.isNotBlank() }?.let { add("Management: $it") }
        checklist.tenantSummary?.takeIf { it.isNotBlank() }?.let { add("Tenant: $it") }
    }
}

fun exportMoveInConditionChecklistPdf(
    context: Context,
    checklist: MoveInConditionChecklist,
    lease: Lease? = null
) {
    ReportExporter.exportPdfDocument(
        context = context,
        fileName = "room_move_in_checklist_${checklist.unitNumber}.pdf",
        title = "RentFlow Room-by-Room Move-In Condition Report",
        subtitle = "${checklist.propertyName} • Unit ${checklist.unitNumber}",
        lines = buildMoveInConditionChecklistLines(checklist),
        signatureBlocks = buildMoveInConditionSignatureBlocks(checklist, lease)
    )
}
