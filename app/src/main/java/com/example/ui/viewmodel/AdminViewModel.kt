package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.UserOrgRepository
import com.example.domain.model.AuditLog
import com.example.domain.model.Organization
import com.example.domain.model.ReportedIssue
import com.example.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminViewModel(
    private val userOrgRepo: UserOrgRepository,
    currentUserId: String,
    val isAdmin: Boolean
) : ViewModel() {

    val users: StateFlow<UiState<List<UserProfile>>> =
        if (isAdmin) {
            userOrgRepo.observeAllUsersForAdmin()
                .asUiState("AdminVM", "Failed to load platform users")
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
        } else {
            flowOf(UiState.Success(emptyList<UserProfile>()))
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Success(emptyList()))
        }

    val organizations: StateFlow<UiState<List<Organization>>> =
        if (isAdmin) {
            userOrgRepo.observeAllOrganizationsForAdmin()
                .asUiState("AdminVM", "Failed to load organizations")
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
        } else {
            flowOf(UiState.Success(emptyList<Organization>()))
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Success(emptyList()))
        }

    val auditLogs: StateFlow<UiState<List<AuditLog>>> =
        userOrgRepo.observeAuditLogsForUserOrAdmin(currentUserId, isAdmin)
            .asUiState("AdminVM", "Failed to load audit logs")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val reportedIssues: StateFlow<UiState<List<ReportedIssue>>> =
        userOrgRepo.observeReportedIssues(currentUserId, isAdmin)
            .asUiState("AdminVM", "Failed to load reported issues")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    fun clearFeedback() {
        _feedback.value = null
    }

    fun toggleUserSuspension(user: UserProfile) {
        viewModelScope.launch {
            val res = userOrgRepo.setUserSuspended(user.userId, !user.suspended)
            _feedback.value = res.fold(
                onSuccess = { if (!user.suspended) "Account suspended" else "Account reinstated" },
                onFailure = { it.localizedMessage ?: "Action failed" }
            )
        }
    }

    fun toggleOrgSuspension(org: Organization) {
        viewModelScope.launch {
            val res = userOrgRepo.setOrganizationSuspended(org.id, !org.suspended)
            _feedback.value = res.fold(
                onSuccess = { if (!org.suspended) "Organization suspended" else "Organization reinstated" },
                onFailure = { it.localizedMessage ?: "Action failed" }
            )
        }
    }

    fun resolveIssue(issueId: String, status: String, adminNotes: String) {
        viewModelScope.launch {
            val res = userOrgRepo.updateReportedIssueStatus(issueId, status, adminNotes)
            _feedback.value = res.fold(
                onSuccess = { "Issue updated to $status" },
                onFailure = { it.localizedMessage ?: "Failed to update issue" }
            )
        }
    }
}
