package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.OperationsRepository
import com.example.domain.model.AppNotification
import com.example.domain.model.ChatMessage
import com.example.domain.model.Conversation
import com.example.domain.model.MaintenanceRequest
import com.example.domain.model.MaintenanceStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class OperationsViewModel(
    private val opsRepo: OperationsRepository,
    private val currentUserId: String
) : ViewModel() {

    val notifications: StateFlow<UiState<List<AppNotification>>> = opsRepo.observeNotifications(currentUserId)
        .asUiState("OpsVM", "Failed to load notifications")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val unreadNotificationCount: StateFlow<Int> = notifications
        .map { state -> ((state as? UiState.Success)?.data ?: emptyList()).count { !it.read } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val conversations: StateFlow<UiState<List<Conversation>>> = opsRepo.observeConversations(currentUserId)
        .asUiState("OpsVM", "Failed to load conversations")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val staffMaintenanceQueue: StateFlow<UiState<List<MaintenanceRequest>>> = opsRepo.observeMaintenanceForStaff(currentUserId)
        .asUiState("OpsVM", "Failed to load assigned tasks")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _selectedConversationId = MutableStateFlow<String?>(null)
    val selectedConversationId: StateFlow<String?> = _selectedConversationId.asStateFlow()

    val activeMessages: StateFlow<UiState<List<ChatMessage>>> = _selectedConversationId
        .flatMapLatest { convId ->
            if (convId.isNullOrBlank()) flowOf(UiState.Success(emptyList()))
            else opsRepo.observeMessages(convId).asUiState("OpsVM", "Failed to load messages")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Success(emptyList()))

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    fun clearFeedback() {
        _feedback.value = null
    }

    fun selectConversation(convId: String?) {
        _selectedConversationId.value = convId
    }

    fun startConversation(
        orgId: String,
        propertyId: String,
        propertyName: String,
        unitNumber: String?,
        participantUids: List<String>,
        subject: String,
        initialMessage: String,
        senderName: String
    ) {
        viewModelScope.launch {
            val res = opsRepo.createConversation(
                orgId, propertyId, propertyName, unitNumber, participantUids, subject, initialMessage, senderName
            )
            res.fold(
                onSuccess = { convId -> _selectedConversationId.value = convId },
                onFailure = { _feedback.value = it.localizedMessage ?: "Failed to start conversation" }
            )
        }
    }

    fun sendMessage(conversation: Conversation, body: String, senderName: String) {
        viewModelScope.launch {
            val res = opsRepo.sendMessage(conversation, body, senderName)
            if (res.isFailure) {
                _feedback.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to send message"
            }
        }
    }

    fun markNotificationRead(notifId: String) {
        viewModelScope.launch {
            opsRepo.markNotificationRead(notifId)
        }
    }

    fun updateStaffTask(request: MaintenanceRequest, newStatus: MaintenanceStatus, costCents: Long, note: String) {
        viewModelScope.launch {
            val res = opsRepo.updateMaintenanceProgress(
                request = request,
                newStatus = newStatus,
                repairCostCents = costCents,
                updateNote = note,
                assignedStaffUid = request.assignedStaffUid,
                assignedStaffName = request.assignedStaffName,
                recordAsExpense = false
            )
            _feedback.value = res.fold(
                onSuccess = { "Task updated to ${newStatus.label}" },
                onFailure = { it.localizedMessage ?: "Failed to update task" }
            )
        }
    }

    fun submitReportedIssue(orgId: String, category: String, title: String, description: String, reporterName: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val res = opsRepo.submitReportedIssue(orgId, category, title, description, reporterName)
            res.fold(
                onSuccess = {
                    _feedback.value = "Issue reported to RentFlow Administration"
                    onDone()
                },
                onFailure = { _feedback.value = it.localizedMessage ?: "Failed to submit issue" }
            )
        }
    }
}
