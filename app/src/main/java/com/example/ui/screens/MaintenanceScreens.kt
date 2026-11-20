package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.domain.finance.CurrencyUtils
import com.example.domain.model.Lease
import com.example.domain.model.MaintenanceCategory
import com.example.domain.model.MaintenancePriority
import com.example.domain.model.MaintenanceRequest
import com.example.domain.model.MaintenanceStatus
import com.example.domain.model.RentalUnit
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.PillTone
import com.example.ui.components.StatusPill
import com.example.ui.media.MaintenancePhotoProcessor
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorBg
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoBg
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import kotlinx.coroutines.launch

data class MaintenanceUnitTarget(
    val unitId: String,
    val propertyName: String,
    val unitNumber: String,
    val statusLabel: String,
    val subtitle: String,
    val lease: Lease? = null
) {
    fun displayLabel(): String = "$propertyName • Unit $unitNumber"

    companion object {
        fun fromLease(lease: Lease): MaintenanceUnitTarget = MaintenanceUnitTarget(
            unitId = lease.unitId,
            propertyName = lease.propertyName,
            unitNumber = lease.unitNumber,
            statusLabel = lease.statusEnum().label,
            subtitle = "Lease ${lease.id.take(8).ifBlank { "Active" }} • ${CurrencyUtils.formatKes(lease.rentCents)}/mo",
            lease = lease
        )

        fun fromRentalUnit(unit: RentalUnit, linkedLease: Lease? = null): MaintenanceUnitTarget = MaintenanceUnitTarget(
            unitId = unit.id,
            propertyName = unit.propertyName,
            unitNumber = unit.unitNumber,
            statusLabel = unit.statusEnum().label,
            subtitle = if (linkedLease != null) {
                "Tenant: ${linkedLease.tenantName}"
            } else if (!unit.currentTenantUid.isNullOrBlank()) {
                "Occupied Unit"
            } else {
                "Vacant Unit Inspection"
            },
            lease = linkedLease
        )
    }
}

enum class MaintenanceTicketFilter(val label: String) {
    ALL("All"),
    OPEN("Open"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    REJECTED("Rejected");

    fun matches(request: MaintenanceRequest): Boolean {
        val st = request.statusEnum()
        return when (this) {
            ALL -> true
            OPEN -> st == MaintenanceStatus.SUBMITTED || st == MaintenanceStatus.UNDER_REVIEW
            IN_PROGRESS -> st == MaintenanceStatus.ASSIGNED || st == MaintenanceStatus.IN_PROGRESS
            COMPLETED -> st == MaintenanceStatus.COMPLETED
            REJECTED -> st == MaintenanceStatus.REJECTED
        }
    }
}

data class PhotoLightboxState(
    val title: String,
    val imageRefs: List<String>,
    val initialIndex: Int = 0
)

private val TIMELINE_STEPS = listOf(
    MaintenanceStatus.SUBMITTED,
    MaintenanceStatus.UNDER_REVIEW,
    MaintenanceStatus.ASSIGNED,
    MaintenanceStatus.IN_PROGRESS,
    MaintenanceStatus.COMPLETED
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MaintenanceSubmissionScreenDialog(
    unitTargets: List<MaintenanceUnitTarget>,
    initialUnitId: String? = null,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        target: MaintenanceUnitTarget,
        title: String,
        description: String,
        category: MaintenanceCategory,
        priority: MaintenancePriority,
        imageRefs: List<String>
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUnitId by remember(unitTargets, initialUnitId) {
        mutableStateOf(
            unitTargets.firstOrNull { it.unitId == initialUnitId }?.unitId
                ?: unitTargets.firstOrNull()?.unitId.orEmpty()
        )
    }
    val selectedTarget = unitTargets.firstOrNull { it.unitId == selectedUnitId } ?: unitTargets.firstOrNull()

    var unitDropdownExpanded by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(MaintenanceCategory.PLUMBING) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var priority by remember { mutableStateOf(MaintenancePriority.MEDIUM) }
    val attachedImageRefs = remember { mutableStateListOf<String>() }
    var isProcessingPhotos by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var previewLightbox by remember { mutableStateOf<PhotoLightboxState?>(null) }

    fun handlePickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val remaining = (MaintenancePhotoProcessor.MAX_PHOTOS - attachedImageRefs.size).coerceAtLeast(0)
        if (remaining == 0) {
            validationError = "Maximum ${MaintenancePhotoProcessor.MAX_PHOTOS} photos already attached."
            return
        }
        val toCompress = uris.take(remaining)
        isProcessingPhotos = true
        validationError = null
        scope.launch {
            val encoded = MaintenancePhotoProcessor.compressUrisToDataUris(
                context = context,
                uris = toCompress,
                maxCount = remaining
            )
            isProcessingPhotos = false
            if (encoded.isEmpty()) {
                validationError = "Could not process the selected photo(s). Try another image."
            } else {
                val candidate = attachedImageRefs.toList() + encoded
                val check = MaintenancePhotoProcessor.validateImageRefsPayload(candidate)
                check.fold(
                    onSuccess = {
                        attachedImageRefs.clear()
                        attachedImageRefs.addAll(it)
                    },
                    onFailure = { err ->
                        validationError = err.localizedMessage ?: "Attached photos exceed ticket size limit."
                    }
                )
            }
        }
    }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MaintenancePhotoProcessor.MAX_PHOTOS)
    ) { uris -> handlePickedUris(uris) }

    val singlePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) handlePickedUris(listOf(uri)) }

    Dialog(
        onDismissRequest = { if (!isBusy && !isProcessingPhotos) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag("maintenance_submission_screen")
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text("Submit Maintenance Request", style = MaterialTheme.typography.titleMedium)
                                if (selectedTarget != null) {
                                    Text(
                                        selectedTarget.displayLabel(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onDismiss,
                                enabled = !isBusy && !isProcessingPhotos,
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("close_maintenance_submission")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Close maintenance request form")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                },
                bottomBar = {
                    Surface(
                        tonalElevation = 3.dp,
                        shadowElevation = 6.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val cleanTitle = title.trim()
                                    val cleanDesc = description.trim()
                                    when {
                                        selectedTarget == null || selectedTarget.unitId.isBlank() -> {
                                            validationError = "Select a linked rental unit before submitting."
                                        }
                                        cleanTitle.length < 2 -> {
                                            validationError = "Enter an issue title of at least 2 characters."
                                        }
                                        cleanDesc.length < 4 -> {
                                            validationError = "Describe the issue (at least 4 characters) so staff can prepare."
                                        }
                                        isProcessingPhotos -> {
                                            validationError = "Please wait for photo compression to finish."
                                        }
                                        else -> {
                                            val payloadCheck = MaintenancePhotoProcessor.validateImageRefsPayload(attachedImageRefs)
                                            payloadCheck.fold(
                                                onSuccess = { validRefs ->
                                                    validationError = null
                                                    onSubmit(
                                                        selectedTarget,
                                                        cleanTitle,
                                                        cleanDesc,
                                                        category,
                                                        priority,
                                                        validRefs
                                                    )
                                                },
                                                onFailure = { err ->
                                                    validationError = err.localizedMessage ?: "Invalid photo attachments."
                                                }
                                            )
                                        }
                                    }
                                },
                                enabled = !isBusy && !isProcessingPhotos && selectedTarget != null,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 600.dp)
                                    .align(Alignment.CenterHorizontally)
                                    .heightIn(min = 48.dp)
                                    .testTag("submit_maintenance_button")
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onSecondary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Submitting Request...")
                                } else {
                                    Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Submit Maintenance Request")
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (!validationError.isNullOrBlank()) {
                        ErrorBannerCard(validationError!!)
                    }

                    // 1. Linked Firestore Rental Unit Card
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Apartment,
                                    contentDescription = "Linked rental unit",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text("Linked Rental Unit", style = MaterialTheme.typography.titleSmall)
                            }

                            if (unitTargets.isEmpty()) {
                                ErrorBannerCard("No linked rental units found for this account.")
                            } else {
                                if (unitTargets.size > 1) {
                                    ExposedDropdownMenuBox(
                                        expanded = unitDropdownExpanded,
                                        onExpandedChange = { unitDropdownExpanded = !unitDropdownExpanded }
                                    ) {
                                        OutlinedTextField(
                                            value = selectedTarget?.displayLabel().orEmpty(),
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Select Rental Unit") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitDropdownExpanded) },
                                            modifier = Modifier
                                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                                .fillMaxWidth()
                                                .testTag("unit_selector_dropdown")
                                        )
                                        ExposedDropdownMenu(
                                            expanded = unitDropdownExpanded,
                                            onDismissRequest = { unitDropdownExpanded = false }
                                        ) {
                                            unitTargets.forEach { target ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(target.displayLabel(), style = MaterialTheme.typography.bodyMedium)
                                                            Text(
                                                                "${target.subtitle} • ${target.statusLabel}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        selectedUnitId = target.unitId
                                                        unitDropdownExpanded = false
                                                        validationError = null
                                                    },
                                                    modifier = Modifier.testTag("unit_option_${target.unitId}")
                                                )
                                            }
                                        }
                                    }
                                }

                                selectedTarget?.let { target ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .semantics(mergeDescendants = true) {}
                                            .testTag("linked_unit_summary_badge")
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(10.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    target.displayLabel(),
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                                Text(
                                                    target.subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    "Unit Doc ID: ${target.unitId}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            StatusPill(target.statusLabel, PillTone.INFO)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Issue Details Card
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Issue Details", style = MaterialTheme.typography.titleSmall)

                            OutlinedTextField(
                                value = title,
                                onValueChange = {
                                    if (it.length <= 140) {
                                        title = it
                                        validationError = null
                                    }
                                },
                                label = { Text("Issue Title") },
                                placeholder = { Text("e.g., Kitchen sink pipe leaking") },
                                singleLine = true,
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth().testTag("maintenance_title_input")
                            )

                            ExposedDropdownMenuBox(
                                expanded = categoryExpanded,
                                onExpandedChange = { categoryExpanded = !categoryExpanded }
                            ) {
                                OutlinedTextField(
                                    value = category.label,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Category") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                        .fillMaxWidth()
                                        .testTag("maintenance_category_dropdown")
                                )
                                ExposedDropdownMenu(
                                    expanded = categoryExpanded,
                                    onDismissRequest = { categoryExpanded = false }
                                ) {
                                    MaintenanceCategory.entries.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat.label) },
                                            onClick = {
                                                category = cat
                                                categoryExpanded = false
                                            },
                                            modifier = Modifier.testTag("maintenance_category_option_${cat.name.lowercase()}")
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Urgency / Priority", style = MaterialTheme.typography.labelMedium)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    MaintenancePriority.entries.forEach { p ->
                                        FilterChip(
                                            selected = priority == p,
                                            onClick = { priority = p },
                                            label = { Text(p.label) },
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("maintenance_priority_chip_${p.name.lowercase()}")
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = description,
                                onValueChange = {
                                    if (it.length <= 1000) {
                                        description = it
                                        validationError = null
                                    }
                                },
                                label = { Text("Problem Description") },
                                placeholder = { Text("Describe the exact location, symptoms, and preferred access time...") },
                                minLines = 3,
                                maxLines = 6,
                                supportingText = { Text("${description.length}/1000") },
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth().testTag("maintenance_description_input")
                            )
                        }
                    }

                    // 3. Photo Evidence Upload Card
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Photo Evidence", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Attach up to ${MaintenancePhotoProcessor.MAX_PHOTOS} photos. Compressed automatically for property & technician review.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusPill(
                                    label = "${attachedImageRefs.size}/${MaintenancePhotoProcessor.MAX_PHOTOS}",
                                    tone = if (attachedImageRefs.isEmpty()) PillTone.NEUTRAL else PillTone.SUCCESS
                                )
                            }

                            if (isProcessingPhotos) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    "Compressing selected photos...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (attachedImageRefs.isNotEmpty()) {
                                MaintenancePhotoStrip(
                                    imageRefs = attachedImageRefs,
                                    removable = true,
                                    onPhotoClick = { idx ->
                                        previewLightbox = PhotoLightboxState(
                                            title = "Attached Issue Photos",
                                            imageRefs = attachedImageRefs.toList(),
                                            initialIndex = idx
                                        )
                                    },
                                    onRemovePhoto = { idx ->
                                        if (idx in attachedImageRefs.indices) {
                                            attachedImageRefs.removeAt(idx)
                                            validationError = null
                                        }
                                    }
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val remaining = MaintenancePhotoProcessor.MAX_PHOTOS - attachedImageRefs.size
                                    if (remaining <= 0) {
                                        validationError = "Maximum ${MaintenancePhotoProcessor.MAX_PHOTOS} photos attached."
                                    } else if (remaining == 1) {
                                        singlePhotoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    } else {
                                        multiPhotoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                },
                                enabled = !isBusy && !isProcessingPhotos && attachedImageRefs.size < MaintenancePhotoProcessor.MAX_PHOTOS,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("attach_maintenance_photos_button")
                            ) {
                                Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (attachedImageRefs.isEmpty()) {
                                        "Add Photos (0/${MaintenancePhotoProcessor.MAX_PHOTOS})"
                                    } else if (attachedImageRefs.size < MaintenancePhotoProcessor.MAX_PHOTOS) {
                                        "Add More Photos (${attachedImageRefs.size}/${MaintenancePhotoProcessor.MAX_PHOTOS})"
                                    } else {
                                        "Photo Limit Reached (${MaintenancePhotoProcessor.MAX_PHOTOS}/${MaintenancePhotoProcessor.MAX_PHOTOS})"
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    previewLightbox?.let { state ->
        PhotoLightboxDialog(
            state = state,
            onDismiss = { previewLightbox = null }
        )
    }
}

@Composable
fun EnhancedMaintenanceCard(
    req: MaintenanceRequest,
    onPhotoClick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: (@Composable ColumnScope.() -> Unit)? = null
) {
    val status = req.statusEnum()
    val statusTone = when (status) {
        MaintenanceStatus.COMPLETED -> PillTone.SUCCESS
        MaintenanceStatus.REJECTED -> PillTone.ERROR
        MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.ASSIGNED -> PillTone.INFO
        MaintenanceStatus.SUBMITTED, MaintenanceStatus.UNDER_REVIEW -> PillTone.WARNING
    }
    val priorityTone = when (req.priorityEnum()) {
        MaintenancePriority.URGENT -> PillTone.ERROR
        MaintenancePriority.HIGH -> PillTone.WARNING
        MaintenancePriority.MEDIUM -> PillTone.INFO
        MaintenancePriority.LOW -> PillTone.NEUTRAL
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("maintenance_card_${req.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(req.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${req.propertyName} • Unit ${req.unitNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "${req.categoryEnum().label} • Logged by ${req.tenantName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatusPill(status.label, statusTone)
                    StatusPill(req.priorityEnum().label, priorityTone)
                }
            }

            if (req.unitId.isNotBlank()) {
                Text(
                    "Linked Unit ID: ${req.unitId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            MaintenanceStatusTimeline(status = status)

            Text(req.description, style = MaterialTheme.typography.bodySmall)

            if (!req.assignedStaffName.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.Engineering,
                        contentDescription = "Assigned technician",
                        tint = StatusInfo,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Technician: ${req.assignedStaffName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = StatusInfo
                    )
                }
            }

            if (req.repairCostCents > 0L) {
                Text(
                    "Recorded Repair Cost: ${CurrencyUtils.formatKes(req.repairCostCents, true)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (!req.latestUpdateNote.isNullOrBlank()) {
                Surface(
                    color = if (status == MaintenanceStatus.REJECTED) StatusErrorBg else StatusInfoBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Latest Update: ${req.latestUpdateNote}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status == MaintenanceStatus.REJECTED) StatusError else StatusInfo,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (req.imageRefs.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Attached Photos (${req.imageRefs.size}) • Tap to enlarge",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MaintenancePhotoStrip(
                        imageRefs = req.imageRefs,
                        removable = false,
                        onPhotoClick = { idx -> onPhotoClick?.invoke(idx) }
                    )
                }
            }

            if (actions != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                    content = actions
                )
            }
        }
    }
}

@Composable
fun MaintenanceStatusTimeline(
    status: MaintenanceStatus,
    modifier: Modifier = Modifier
) {
    if (status == MaintenanceStatus.REJECTED) {
        Surface(
            color = StatusErrorBg,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, StatusError.copy(alpha = 0.35f)),
            modifier = modifier.fillMaxWidth().testTag("timeline_rejected_banner")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Filled.Cancel,
                    contentDescription = "Request rejected",
                    tint = StatusError,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "Ticket Closed as Rejected",
                    style = MaterialTheme.typography.labelMedium,
                    color = StatusError
                )
            }
        }
        return
    }

    val currentIndex = TIMELINE_STEPS.indexOf(status).coerceAtLeast(0)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth().testTag("maintenance_status_timeline")
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            TIMELINE_STEPS.forEachIndexed { idx, step ->
                val isCompleted = idx < currentIndex || (idx == currentIndex && status == MaintenanceStatus.COMPLETED)
                val isCurrent = idx == currentIndex && status != MaintenanceStatus.COMPLETED
                val nodeColor = when {
                    isCompleted -> StatusSuccess
                    isCurrent -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.outline
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("timeline_step_${step.name.lowercase()}")
                            .semantics {
                                contentDescription = "${step.label} ${if (isCompleted) "completed" else if (isCurrent) "current" else "pending"}"
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(2.dp)
                                    .background(
                                        if (idx == 0) Color.Transparent
                                        else if (idx <= currentIndex) StatusSuccess
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                    )
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(if (isCurrent) 22.dp else 18.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isCompleted -> StatusSuccess
                                            isCurrent -> MaterialTheme.colorScheme.secondary
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    )
                            ) {
                                if (isCompleted) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isCurrent) Color.White else nodeColor.copy(alpha = 0.55f))
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(2.dp)
                                    .background(
                                        if (idx == TIMELINE_STEPS.lastIndex) Color.Transparent
                                        else if (idx < currentIndex) StatusSuccess
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                    )
                            )
                        }
                        Text(
                            text = when (step) {
                                MaintenanceStatus.UNDER_REVIEW -> "Review"
                                MaintenanceStatus.IN_PROGRESS -> "Active"
                                else -> step.label
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isCurrent || isCompleted) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = when {
                                isCompleted -> StatusSuccess
                                isCurrent -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MaintenancePhotoStrip(
    imageRefs: List<String>,
    removable: Boolean = false,
    onPhotoClick: ((Int) -> Unit)? = null,
    onRemovePhoto: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        itemsIndexed(imageRefs) { index, ref ->
            MaintenancePhotoThumbnail(
                imageRef = ref,
                index = index,
                removable = removable,
                onClick = { onPhotoClick?.invoke(index) },
                onRemove = { onRemovePhoto?.invoke(index) }
            )
        }
    }
}

@Composable
fun MaintenancePhotoThumbnail(
    imageRef: String,
    index: Int,
    removable: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val decodedBitmap = remember(imageRef) {
        if (MaintenancePhotoProcessor.isDataImageUri(imageRef)) {
            MaintenancePhotoProcessor.decodeDataUriToImageBitmap(imageRef)
        } else {
            null
        }
    }
    val sizeKb = remember(imageRef) { MaintenancePhotoProcessor.estimateDataUriKb(imageRef) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClickLabel = "Open photo ${index + 1}") { onClick() }
                .testTag("maintenance_photo_thumb_$index")
        ) {
            if (decodedBitmap != null) {
                Image(
                    bitmap = decodedBitmap,
                    contentDescription = "Maintenance photo ${index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = imageRef,
                    contentDescription = "Maintenance photo ${index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (removable) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .clickable { onRemove() }
                        .testTag("remove_maintenance_photo_$index")
                        .semantics { contentDescription = "Remove photo ${index + 1}" }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.ZoomIn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
        if (sizeKb != null) {
            Text(
                "${sizeKb} KB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PhotoLightboxDialog(
    state: PhotoLightboxState,
    onDismiss: () -> Unit
) {
    if (state.imageRefs.isEmpty()) return
    var currentIndex by remember(state) {
        mutableIntStateOf(state.initialIndex.coerceIn(0, state.imageRefs.lastIndex))
    }
    val currentRef = state.imageRefs[currentIndex]
    val decodedBitmap = remember(currentRef) {
        if (MaintenancePhotoProcessor.isDataImageUri(currentRef)) {
            MaintenancePhotoProcessor.decodeDataUriToImageBitmap(currentRef)
        } else {
            null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color.Black,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag("photo_lightbox_dialog")
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            state.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Photo ${currentIndex + 1} of ${state.imageRefs.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("close_lightbox_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close photo preview", tint = Color.White)
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    if (decodedBitmap != null) {
                        Image(
                            bitmap = decodedBitmap,
                            contentDescription = "Full size maintenance photo ${currentIndex + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = currentRef,
                            contentDescription = "Full size maintenance photo ${currentIndex + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                if (state.imageRefs.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { if (currentIndex > 0) currentIndex-- },
                            enabled = currentIndex > 0,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("lightbox_prev_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Previous", color = Color.White)
                        }
                        OutlinedButton(
                            onClick = { if (currentIndex < state.imageRefs.lastIndex) currentIndex++ },
                            enabled = currentIndex < state.imageRefs.lastIndex,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("lightbox_next_button")
                        ) {
                            Text("Next", color = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White)
                        }
                    }
                } else {
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun TenantFollowUpDialog(
    request: MaintenanceRequest,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (appendedNote: String, updatedImageRefs: List<String>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var note by remember { mutableStateOf("") }
    val imageRefs = remember(request.id) { mutableStateListOf<String>().apply { addAll(request.imageRefs) } }
    var isProcessingPhotos by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    fun handlePickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val remaining = (MaintenancePhotoProcessor.MAX_PHOTOS - imageRefs.size).coerceAtLeast(0)
        if (remaining == 0) {
            err = "Maximum ${MaintenancePhotoProcessor.MAX_PHOTOS} photos allowed per ticket."
            return
        }
        isProcessingPhotos = true
        err = null
        scope.launch {
            val encoded = MaintenancePhotoProcessor.compressUrisToDataUris(
                context = context,
                uris = uris.take(remaining),
                maxCount = remaining
            )
            isProcessingPhotos = false
            if (encoded.isEmpty()) {
                err = "Could not compress selected image."
            } else {
                val candidate = imageRefs.toList() + encoded
                MaintenancePhotoProcessor.validateImageRefsPayload(candidate).fold(
                    onSuccess = {
                        imageRefs.clear()
                        imageRefs.addAll(it)
                    },
                    onFailure = { e -> err = e.localizedMessage ?: "Photo size limit exceeded." }
                )
            }
        }
    }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MaintenancePhotoProcessor.MAX_PHOTOS)
    ) { uris -> handlePickedUris(uris) }

    val singlePhotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) handlePickedUris(listOf(uri)) }

    AlertDialog(
        onDismissRequest = { if (!isBusy && !isProcessingPhotos) onDismiss() },
        title = { Text("Follow Up: ${request.title}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "${request.propertyName} • Unit ${request.unitNumber} • Status: ${request.statusEnum().label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (!err.isNullOrBlank()) ErrorBannerCard(err!!)

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it; err = null },
                    label = { Text("Follow-up Note (optional if adding photos)") },
                    placeholder = { Text("Add access details or new observations...") },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth().testTag("followup_note_input")
                )

                if (isProcessingPhotos) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (imageRefs.isNotEmpty()) {
                    Text(
                        "Ticket Photos (${imageRefs.size}/${MaintenancePhotoProcessor.MAX_PHOTOS})",
                        style = MaterialTheme.typography.labelSmall
                    )
                    MaintenancePhotoStrip(
                        imageRefs = imageRefs,
                        removable = true,
                        onRemovePhoto = { idx ->
                            if (idx in imageRefs.indices) {
                                imageRefs.removeAt(idx)
                                err = null
                            }
                        }
                    )
                }

                OutlinedButton(
                    onClick = {
                        val remaining = MaintenancePhotoProcessor.MAX_PHOTOS - imageRefs.size
                        if (remaining == 1) {
                            singlePhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        } else {
                            multiPhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    },
                    enabled = !isBusy && !isProcessingPhotos && imageRefs.size < MaintenancePhotoProcessor.MAX_PHOTOS,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("followup_attach_photos_button")
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Attach Follow-Up Photos (${imageRefs.size}/${MaintenancePhotoProcessor.MAX_PHOTOS})")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanNote = note.trim()
                    val photosChanged = imageRefs.toList() != request.imageRefs
                    if (cleanNote.isBlank() && !photosChanged) {
                        err = "Enter a follow-up note or attach new photos."
                    } else if (isProcessingPhotos) {
                        err = "Wait for photo compression to finish."
                    } else {
                        MaintenancePhotoProcessor.validateImageRefsPayload(imageRefs).fold(
                            onSuccess = { validRefs -> onSubmit(cleanNote, validRefs) },
                            onFailure = { e -> err = e.localizedMessage ?: "Invalid photo payload." }
                        )
                    }
                },
                enabled = !isBusy && !isProcessingPhotos,
                modifier = Modifier.heightIn(min = 48.dp).testTag("submit_followup_button")
            ) {
                Text(if (isBusy) "Saving..." else "Save Update")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy && !isProcessingPhotos,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}
