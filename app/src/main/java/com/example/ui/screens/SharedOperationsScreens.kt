package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ai.AiTaskState
import com.example.data.repository.ThemeMode
import com.example.domain.finance.CurrencyUtils
import com.example.domain.model.Announcement
import com.example.domain.model.IssueStatus
import com.example.domain.model.Lease
import com.example.domain.model.MaintenanceRequest
import com.example.domain.model.MaintenanceStatus
import com.example.domain.model.Property
import com.example.domain.model.ReportedIssue
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.PillTone
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoBg
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.AiTriageUiState
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.LandlordViewModel
import com.example.ui.viewmodel.OperationsViewModel
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandlordOperationsScreen(
    landlordViewModel: LandlordViewModel,
    opsViewModel: OperationsViewModel,
    currentUser: UserProfile
) {
    val maintState by landlordViewModel.maintenanceRequests.collectAsStateWithLifecycle()
    val propsState by landlordViewModel.properties.collectAsStateWithLifecycle()
    val annState by landlordViewModel.announcements.collectAsStateWithLifecycle()
    val leasesState by landlordViewModel.leases.collectAsStateWithLifecycle()
    val aiTriageState by landlordViewModel.aiTriageState.collectAsStateWithLifecycle()
    val aiAnnouncementDraftState by landlordViewModel.aiAnnouncementDraftState.collectAsStateWithLifecycle()
    val busy by landlordViewModel.busy.collectAsStateWithLifecycle()

    val properties = (propsState as? UiState.Success)?.data ?: emptyList()
    val leases = (leasesState as? UiState.Success)?.data ?: emptyList()

    var selectedTab by remember { mutableIntStateOf(0) }
    var requestToUpdate by remember { mutableStateOf<MaintenanceRequest?>(null) }
    var showAnnouncementDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Repairs") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Notices") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Messages") })
        }

        when (selectedTab) {
            0 -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item { SectionHeader("Maintenance Queue") }
                when (val st = maintState) {
                    is UiState.Loading -> item { LoadingStateBox("Loading maintenance queue...") }
                    is UiState.Error -> item { ErrorBannerCard(st.message) }
                    is UiState.Success -> {
                        if (st.data.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = "No Maintenance Tickets",
                                    message = "Tenant repair requests for your properties appear here for staff assignment and expense tracking."
                                )
                            }
                        } else {
                            items(st.data, key = { it.id }) { req ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    MaintenanceCard(req = req)
                                    OutlinedButton(
                                        onClick = { requestToUpdate = req },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                                    ) {
                                        Text("Assign Staff / Update Status")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Property Announcements", style = MaterialTheme.typography.titleMedium)
                        Button(
                            onClick = { showAnnouncementDialog = true },
                            enabled = properties.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Filled.Campaign, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Broadcast")
                        }
                    }
                }
                when (val st = annState) {
                    is UiState.Loading -> item { LoadingStateBox("Loading announcements...") }
                    is UiState.Error -> item { ErrorBannerCard(st.message) }
                    is UiState.Success -> {
                        if (st.data.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = "No Announcements Sent",
                                    message = "Broadcast water schedules, security notices, or estate updates to all tenants in a property."
                                )
                            }
                        } else {
                            items(st.data, key = { it.id }) { ann ->
                                AnnouncementCard(ann)
                            }
                        }
                    }
                }
            }

            2 -> ConversationsScreen(
                opsViewModel = opsViewModel,
                currentUser = currentUser,
                knownLeases = leases
            )
        }
    }

    requestToUpdate?.let { req ->
        ManageMaintenanceDialog(
            request = req,
            allowExpenseRecording = true,
            isBusy = busy,
            aiTriageState = aiTriageState,
            onTriageWithAi = { landlordViewModel.triageMaintenanceWithAi(req) },
            onDismiss = {
                landlordViewModel.clearAiTriage()
                requestToUpdate = null
            },
            onSave = { status, costCents, note, staffUid, staffName, recordExp ->
                landlordViewModel.updateMaintenance(req, status, costCents, note, staffUid, staffName, recordExp)
                landlordViewModel.clearAiTriage()
                requestToUpdate = null
            }
        )
    }

    if (showAnnouncementDialog) {
        PublishAnnouncementDialog(
            properties = properties.filter { !it.archived },
            isBusy = busy,
            aiDraftState = aiAnnouncementDraftState,
            onDraftWithAi = { propName, topic, contextText ->
                landlordViewModel.draftAnnouncementWithAi(propName, topic, contextText)
            },
            onDismiss = {
                landlordViewModel.clearAnnouncementDraft()
                showAnnouncementDialog = false
            },
            onPublish = { prop, title, body ->
                landlordViewModel.publishAnnouncement(prop, title, body, currentUser.fullName) {
                    landlordViewModel.clearAnnouncementDraft()
                    showAnnouncementDialog = false
                }
            }
        )
    }
}

@Composable
private fun AnnouncementCard(ann: Announcement) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(ann.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                StatusPill(ann.propertyName, PillTone.INFO)
            }
            Text(ann.body, style = MaterialTheme.typography.bodySmall)
            Text("Sent to ${ann.targetUids.size} tenant(s) • By ${ann.authorName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StaffMaintenanceQueueScreen(
    opsViewModel: OperationsViewModel
) {
    val queueState by opsViewModel.staffMaintenanceQueue.collectAsStateWithLifecycle()
    var selectedTask by remember { mutableStateOf<MaintenanceRequest?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { SectionHeader("Assigned Maintenance Jobs") }
        when (val st = queueState) {
            is UiState.Loading -> item { LoadingStateBox("Loading assigned jobs...") }
            is UiState.Error -> item { ErrorBannerCard(st.message) }
            is UiState.Success -> {
                if (st.data.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No Assigned Repair Tasks",
                            message = "Share your RentFlow User ID from your Profile with a landlord/manager so they can assign property maintenance tickets to you."
                        )
                    }
                } else {
                    items(st.data, key = { it.id }) { req ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MaintenanceCard(req = req)
                            if (req.statusEnum() !in listOf(MaintenanceStatus.COMPLETED, MaintenanceStatus.REJECTED)) {
                                Button(
                                    onClick = { selectedTask = req },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                ) {
                                    Text("Update Repair Progress")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedTask?.let { req ->
        ManageMaintenanceDialog(
            request = req,
            allowExpenseRecording = false,
            isBusy = false,
            onDismiss = { selectedTask = null },
            onSave = { status, costCents, note, _, _, _ ->
                opsViewModel.updateStaffTask(req, status, costCents, note)
                selectedTask = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManageMaintenanceDialog(
    request: MaintenanceRequest,
    allowExpenseRecording: Boolean,
    isBusy: Boolean,
    aiTriageState: AiTriageUiState = AiTriageUiState.Idle,
    onTriageWithAi: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (MaintenanceStatus, Long, String, String?, String?, Boolean) -> Unit
) {
    var status by remember { mutableStateOf(request.statusEnum()) }
    var staffUid by remember { mutableStateOf(request.assignedStaffUid ?: "") }
    var staffName by remember { mutableStateOf(request.assignedStaffName ?: "") }
    var costText by remember { mutableStateOf(CurrencyUtils.centsToInputString(request.repairCostCents)) }
    var note by remember { mutableStateOf(request.latestUpdateNote ?: "") }
    var recordExpense by remember { mutableStateOf(false) }

    val availableStatuses = if (allowExpenseRecording) {
        MaintenanceStatus.entries
    } else {
        listOf(MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.COMPLETED)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Ticket: ${request.title}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (allowExpenseRecording && onTriageWithAi != null) {
                    OutlinedButton(
                        onClick = onTriageWithAi,
                        enabled = aiTriageState !is AiTriageUiState.Loading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("triage_maintenance_ai_button")
                    ) {
                        if (aiTriageState is AiTriageUiState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Triaging with AI...")
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Triage Ticket with AI")
                        }
                    }
                    when (aiTriageState) {
                        is AiTriageUiState.Error -> ErrorBannerCard(aiTriageState.message)
                        is AiTriageUiState.Success -> {
                            val sug = aiTriageState.suggestion
                            Surface(
                                color = StatusInfoBg,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        "AI Triage: ${sug.suggestedPriority.label} Priority • ${sug.suggestedCategory.label}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = StatusInfo
                                    )
                                    Text("Technician: ${sug.technicianActionNote}", style = MaterialTheme.typography.bodySmall)
                                    Text("Tenant reply: ${sug.tenantReplyDraft}", style = MaterialTheme.typography.bodySmall)
                                    TextButton(
                                        onClick = {
                                            note = "${sug.technicianActionNote} | Update: ${sug.tenantReplyDraft}"
                                            if (status == MaintenanceStatus.SUBMITTED) {
                                                status = MaintenanceStatus.UNDER_REVIEW
                                            }
                                        },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Apply Note & Mark Under Review")
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }
                Text("Status", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    availableStatuses.forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.label) }
                        )
                    }
                }
                if (allowExpenseRecording) {
                    OutlinedTextField(value = staffUid, onValueChange = { staffUid = it }, label = { Text("Assigned Staff UID (Optional)") }, singleLine = true)
                    OutlinedTextField(value = staffName, onValueChange = { staffName = it }, label = { Text("Technician Name (Optional)") }, singleLine = true)
                }
                OutlinedTextField(value = costText, onValueChange = { costText = it }, label = { Text("Repair Cost (KES)") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Progress / Completion Notes") }, maxLines = 4)
                if (allowExpenseRecording) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = recordExpense, onCheckedChange = { recordExpense = it })
                        Text("Log repair cost as Property Expense", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val costCents = CurrencyUtils.parseKesToCents(costText) ?: 0L
                    onSave(status, costCents, note, staffUid.ifBlank { null }, staffName.ifBlank { null }, recordExpense)
                },
                enabled = !isBusy
            ) { Text("Save Update") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublishAnnouncementDialog(
    properties: List<Property>,
    isBusy: Boolean,
    aiDraftState: AiTaskState,
    onDraftWithAi: (propertyName: String, topic: String, extraContext: String) -> Unit,
    onDismiss: () -> Unit,
    onPublish: (Property, String, String) -> Unit
) {
    var selectedProp by remember { mutableStateOf(properties.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(aiDraftState) {
        when (aiDraftState) {
            is AiTaskState.Streaming -> body = aiDraftState.text
            is AiTaskState.Success -> body = aiDraftState.text
            else -> {}
        }
    }

    val aiLoading = aiDraftState is AiTaskState.Loading || aiDraftState is AiTaskState.Streaming

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Publish Property Announcement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (!err.isNullOrBlank()) ErrorBannerCard(err!!)
                if (aiDraftState is AiTaskState.Error) ErrorBannerCard(aiDraftState.message)
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = selectedProp?.name ?: "Select Property",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Property") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        properties.forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, onClick = { selectedProp = p; expanded = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Announcement Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        onDraftWithAi(
                            selectedProp?.name ?: "Property",
                            title.ifBlank { "Water & estate service schedule" },
                            body
                        )
                    },
                    enabled = !aiLoading && selectedProp != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("draft_announcement_ai_button")
                ) {
                    if (aiLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Drafting Notice...")
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Draft Notice with AI")
                    }
                }
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Message to Tenants") },
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val prop = selectedProp
                    if (prop == null) err = "Select a property"
                    else if (title.trim().length < 2) err = "Enter title"
                    else if (body.trim().length < 2) err = "Enter announcement message"
                    else onPublish(prop, title, body)
                },
                enabled = !isBusy
            ) { Text("Send Notice") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ConversationsScreen(
    opsViewModel: OperationsViewModel,
    currentUser: UserProfile,
    knownLeases: List<Lease>
) {
    val convsState by opsViewModel.conversations.collectAsStateWithLifecycle()
    val selectedConvId by opsViewModel.selectedConversationId.collectAsStateWithLifecycle()
    val messagesState by opsViewModel.activeMessages.collectAsStateWithLifecycle()

    val conversations = (convsState as? UiState.Success)?.data ?: emptyList()
    val activeConv = conversations.firstOrNull { it.id == selectedConvId }

    var showNewConvDialog by remember { mutableStateOf(false) }

    if (activeConv != null) {
        var replyText by remember { mutableStateOf("") }
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    IconButton(onClick = { opsViewModel.selectConversation(null) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to conversations")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(activeConv.subject, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${activeConv.propertyName}${activeConv.unitNumber?.let { " • Unit $it" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                when (val st = messagesState) {
                    is UiState.Loading -> item { LoadingStateBox("Loading messages...") }
                    is UiState.Error -> item { ErrorBannerCard(st.message) }
                    is UiState.Success -> {
                        items(st.data, key = { it.id }) { msg ->
                            val isMine = msg.senderUid == currentUser.userId
                            Row(
                                horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    color = if (isMine) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth(0.82f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = msg.senderName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isMine) Color(0xFFCCFBF1) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = msg.body,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    placeholder = { Text("Write a message...") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (replyText.isNotBlank()) {
                            opsViewModel.sendMessage(activeConv, replyText, currentUser.fullName)
                            replyText = ""
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Message", tint = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    } else {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showNewConvDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("new_conversation_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "New Conversation")
                }
            }
        ) { innerPadding ->
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                item { SectionHeader("Messages & Threads") }
                when (val st = convsState) {
                    is UiState.Loading -> item { LoadingStateBox("Loading conversations...") }
                    is UiState.Error -> item { ErrorBannerCard(st.message) }
                    is UiState.Success -> {
                        if (st.data.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = "No Messages Yet",
                                    message = "Start a direct thread with your landlord, property manager, or tenant about leases, rent, or repairs.",
                                    ctaLabel = "Start Conversation",
                                    onCtaClick = { showNewConvDialog = true }
                                )
                            }
                        } else {
                            items(st.data, key = { it.id }) { conv ->
                                Card(
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                                    modifier = Modifier.fillMaxWidth().clickable { opsViewModel.selectConversation(conv.id) }
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(conv.subject, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${conv.propertyName}${conv.unitNumber?.let { " • Unit $it" } ?: ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            conv.lastMessagePreview,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewConvDialog) {
        StartConversationDialog(
            currentUser = currentUser,
            knownLeases = knownLeases,
            onDismiss = { showNewConvDialog = false },
            onStart = { orgId, propId, propName, unitNum, recipientUid, subject, message ->
                opsViewModel.startConversation(
                    orgId = orgId,
                    propertyId = propId,
                    propertyName = propName,
                    unitNumber = unitNum,
                    participantUids = listOf(recipientUid),
                    subject = subject,
                    initialMessage = message,
                    senderName = currentUser.fullName
                )
                showNewConvDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartConversationDialog(
    currentUser: UserProfile,
    knownLeases: List<Lease>,
    onDismiss: () -> Unit,
    onStart: (orgId: String, propId: String, propName: String, unitNum: String?, recipientUid: String, subject: String, msg: String) -> Unit
) {
    var selectedLease by remember { mutableStateOf(knownLeases.firstOrNull()) }
    var leaseExpanded by remember { mutableStateOf(false) }
    var manualRecipientUid by remember {
        mutableStateOf(
            knownLeases.firstOrNull()?.let { l ->
                if (l.tenantUid == currentUser.userId) l.ownerUid else l.tenantUid
            } ?: ""
        )
    }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Message Thread") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (!err.isNullOrBlank()) ErrorBannerCard(err!!)
                if (knownLeases.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = leaseExpanded, onExpandedChange = { leaseExpanded = !leaseExpanded }) {
                        OutlinedTextField(
                            value = selectedLease?.let { "${it.propertyName} (${it.unitNumber}) - ${it.tenantName}" } ?: "Select Lease",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Lease Context") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(leaseExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = leaseExpanded, onDismissRequest = { leaseExpanded = false }) {
                            knownLeases.forEach { l ->
                                DropdownMenuItem(
                                    text = { Text("${l.propertyName} (${l.unitNumber}) • ${l.tenantName}") },
                                    onClick = {
                                        selectedLease = l
                                        manualRecipientUid = if (l.tenantUid == currentUser.userId) l.ownerUid else l.tenantUid
                                        leaseExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = manualRecipientUid,
                    onValueChange = { manualRecipientUid = it },
                    label = { Text("Recipient RentFlow UID") },
                    singleLine = true
                )
                OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, singleLine = true)
                OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Message") }, maxLines = 4)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (manualRecipientUid.trim().isBlank()) err = "Recipient UID is required"
                else if (subject.trim().isBlank()) err = "Enter a subject"
                else if (message.trim().isBlank()) err = "Enter a message"
                else {
                    val l = selectedLease
                    onStart(
                        l?.orgId ?: (currentUser.activeOrgId ?: "direct"),
                        l?.propertyId ?: "direct",
                        l?.propertyName ?: "Direct Message",
                        l?.unitNumber,
                        manualRecipientUid.trim(),
                        subject,
                        message
                    )
                }
            }) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun NotificationsScreen(opsViewModel: OperationsViewModel) {
    val state by opsViewModel.notifications.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { SectionHeader("Notifications") }
        when (val st = state) {
            is UiState.Loading -> item { LoadingStateBox("Loading notifications...") }
            is UiState.Error -> item { ErrorBannerCard(st.message) }
            is UiState.Success -> {
                if (st.data.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "All Caught Up",
                            message = "Rent due alerts, payment confirmations, application decisions, and maintenance updates appear here."
                        )
                    }
                } else {
                    items(st.data, key = { it.id }) { notif ->
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = if (notif.read) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!notif.read) opsViewModel.markNotificationRead(notif.id)
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Notifications,
                                    contentDescription = notif.typeEnum().label,
                                    tint = if (notif.read) MaterialTheme.colorScheme.onSurfaceVariant else EmeraldAccent
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text(notif.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                        if (!notif.read) {
                                            Surface(color = EmeraldAccent, shape = CircleShape, modifier = Modifier.size(10.dp)) {}
                                        }
                                    }
                                    Text(notif.body, style = MaterialTheme.typography.bodySmall)
                                    notif.createdAt?.let { ts ->
                                        Text(CurrencyUtils.formatDateTime(ts.toDate().time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSettingsScreen(
    authViewModel: AuthViewModel,
    opsViewModel: OperationsViewModel,
    currentUser: UserProfile,
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val busy by authViewModel.busy.collectAsStateWithLifecycle()

    var fullName by remember(currentUser.fullName) { mutableStateOf(currentUser.fullName) }
    var phone by remember(currentUser.phoneNumber) { mutableStateOf(currentUser.phoneNumber ?: "+254") }
    var idNumber by remember(currentUser.idNumber) { mutableStateOf(currentUser.idNumber ?: "") }
    var emergencyName by remember(currentUser.emergencyContactName) { mutableStateOf(currentUser.emergencyContactName ?: "") }
    var emergencyPhone by remember(currentUser.emergencyContactPhone) { mutableStateOf(currentUser.emergencyContactPhone ?: "") }
    var notifyRent by remember(currentUser.notifyRentDue) { mutableStateOf(currentUser.notifyRentDue) }
    var notifyMaint by remember(currentUser.notifyMaintenance) { mutableStateOf(currentUser.notifyMaintenance) }
    var notifyAnn by remember(currentUser.notifyAnnouncements) { mutableStateOf(currentUser.notifyAnnouncements) }

    var showReportIssueDialog by remember { mutableStateOf(false) }

    val availableRoles = remember(currentUser.roles, authViewModel.isBootstrappedAdmin) {
        buildList {
            add(UserRole.LANDLORD)
            add(UserRole.PROPERTY_MANAGER)
            add(UserRole.TENANT)
            add(UserRole.MAINTENANCE_STAFF)
            if (UserRole.ADMIN.name in currentUser.roles || authViewModel.isBootstrappedAdmin) {
                add(UserRole.ADMIN)
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(currentUser.fullName, style = MaterialTheme.typography.titleLarge)
                    Text(currentUser.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Your RentFlow User ID (Share with Landlord)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(currentUser.userId, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("RentFlow UID", currentUser.userId))
                                Toast.makeText(context, "User ID copied", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy User ID")
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Active Role Workspace", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        availableRoles.forEach { role ->
                            FilterChip(
                                selected = currentUser.activeRoleEnum() == role,
                                onClick = { authViewModel.switchRole(role, currentUser.roles) },
                                label = { Text(role.label) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = currentTheme == mode,
                                onClick = { onThemeChange(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Personal & Emergency Info", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Kenyan Phone (+254...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = idNumber, onValueChange = { idNumber = it }, label = { Text("National ID / Passport (Optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = emergencyName, onValueChange = { emergencyName = it }, label = { Text("Emergency Contact Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = emergencyPhone, onValueChange = { emergencyPhone = it }, label = { Text("Emergency Contact Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Rent Due & Payment Alerts", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = notifyRent, onCheckedChange = { notifyRent = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Maintenance Updates", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = notifyMaint, onCheckedChange = { notifyMaint = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Property Announcements", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = notifyAnn, onCheckedChange = { notifyAnn = it })
                    }

                    Button(
                        onClick = {
                            authViewModel.updateProfile(
                                fullName = fullName,
                                phoneNumber = if (phone == "+254") null else phone,
                                idNumber = idNumber.ifBlank { null },
                                emergencyContactName = emergencyName.ifBlank { null },
                                emergencyContactPhone = emergencyPhone.ifBlank { null },
                                notifyRentDue = notifyRent,
                                notifyMaintenance = notifyMaint,
                                notifyAnnouncements = notifyAnn
                            )
                        },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("save_profile_button")
                    ) {
                        Text("Save Profile Changes")
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { showReportIssueDialog = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(Icons.Filled.ReportProblem, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Report Platform Issue to Admin")
            }
        }

        item {
            OutlinedButton(
                onClick = onSignOut,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sign Out")
            }
        }
    }

    if (showReportIssueDialog) {
        var category by remember { mutableStateOf("Payment Verification") }
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showReportIssueDialog = false },
            title = { Text("Report Platform Issue") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!err.isNullOrBlank()) ErrorBannerCard(err!!)
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true)
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Issue Summary") }, singleLine = true)
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Details") }, maxLines = 4)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.trim().length < 2) err = "Enter issue summary"
                    else if (description.trim().length < 2) err = "Enter details"
                    else {
                        opsViewModel.submitReportedIssue(
                            orgId = currentUser.activeOrgId ?: "platform",
                            category = category,
                            title = title,
                            description = description,
                            reporterName = currentUser.fullName
                        ) { showReportIssueDialog = false }
                    }
                }) { Text("Submit") }
            },
            dismissButton = { TextButton(onClick = { showReportIssueDialog = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPortalScreen(
    adminViewModel: AdminViewModel,
    initialSection: Int = 0
) {
    val usersState by adminViewModel.users.collectAsStateWithLifecycle()
    val orgsState by adminViewModel.organizations.collectAsStateWithLifecycle()
    val issuesState by adminViewModel.reportedIssues.collectAsStateWithLifecycle()
    val logsState by adminViewModel.auditLogs.collectAsStateWithLifecycle()

    var selectedTab by remember(initialSection) { mutableIntStateOf(initialSection.coerceIn(0, 3)) }
    var issueToResolve by remember { mutableStateOf<ReportedIssue?>(null) }

    if (!adminViewModel.isAdmin) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            EmptyStateCard(
                title = "Administrator Access Restricted",
                message = "Only verified RentFlow platform administrators can access global account controls, reported issue triage, and full system audit logs."
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Users") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Orgs") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Issues") })
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Audit") })
        }

        when (selectedTab) {
            0 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                when (val st = usersState) {
                    is UiState.Loading -> item { LoadingStateBox("Loading users...") }
                    is UiState.Error -> item { ErrorBannerCard(st.message) }
                    is UiState.Success -> items(st.data, key = { it.userId }) { user ->
                        Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.padding(14.dp).fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(user.fullName, style = MaterialTheme.typography.titleSmall)
                                    Text("${user.email} • ${user.activeRoleEnum().label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                OutlinedButton(onClick = { adminViewModel.toggleUserSuspension(user) }) {
                                    Text(if (user.suspended) "Reinstate" else "Suspend")
                                }
                            }
                        }
                    }
                }
            }

            1 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                when (val st = orgsState) {
                    is UiState.Loading -> item { LoadingStateBox("Loading organizations...") }
                    is UiState.Error -> item { ErrorBannerCard(st.message) }
                    is UiState.Success -> items(st.data, key = { it.id }) { org ->
                        Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.padding(14.dp).fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(org.name, style = MaterialTheme.typography.titleSmall)
                                    Text("${org.country} (${org.currency}) • ${org.memberUids.size} members", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                OutlinedButton(onClick = { adminViewModel.toggleOrgSuspension(org) }) {
                                    Text(if (org.suspended) "Reinstate" else "Suspend")
                                }
                            }
                        }
                    }
                }
            }

            2 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                when (val st = issuesState) {
                    is UiState.Loading -> item { LoadingStateBox("Loading reported issues...") }
                    is UiState.Error -> item { ErrorBannerCard(st.message) }
                    is UiState.Success -> {
                        if (st.data.isEmpty()) {
                            item { EmptyStateCard("No Reported Issues", "Platform support and dispute tickets submitted by users appear here.") }
                        } else {
                            items(st.data, key = { it.id }) { issue ->
                                val tone = when (issue.statusEnum()) {
                                    IssueStatus.RESOLVED -> PillTone.SUCCESS
                                    IssueStatus.INVESTIGATING -> PillTone.WARNING
                                    IssueStatus.OPEN -> PillTone.ERROR
                                }
                                Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text(issue.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                            StatusPill(issue.statusEnum().label, tone)
                                        }
                                        Text("${issue.category} • By ${issue.reporterName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(issue.description, style = MaterialTheme.typography.bodySmall)
                                        OutlinedButton(onClick = { issueToResolve = issue }, modifier = Modifier.fillMaxWidth()) {
                                            Text("Update Status / Notes")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            3 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                when (val st = logsState) {
                    is UiState.Loading -> item { LoadingStateBox("Loading audit trail...") }
                    is UiState.Error -> item { ErrorBannerCard(st.message) }
                    is UiState.Success -> items(st.data, key = { it.id }) { log ->
                        Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${log.action} • ${log.entityType}", style = MaterialTheme.typography.titleSmall)
                                Text(log.details, style = MaterialTheme.typography.bodySmall)
                                Text("Actor: ${log.actorName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    issueToResolve?.let { issue ->
        var status by remember { mutableStateOf(issue.statusEnum()) }
        var notes by remember { mutableStateOf(issue.adminNotes ?: "") }
        AlertDialog(
            onDismissRequest = { issueToResolve = null },
            title = { Text("Triage Issue: ${issue.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IssueStatus.entries.forEach { st ->
                            FilterChip(selected = status == st, onClick = { status = st }, label = { Text(st.label) })
                        }
                    }
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Admin Resolution Notes") }, maxLines = 3)
                }
            },
            confirmButton = {
                Button(onClick = {
                    adminViewModel.resolveIssue(issue.id, status.name, notes)
                    issueToResolve = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { issueToResolve = null }) { Text("Cancel") } }
        )
    }
}
