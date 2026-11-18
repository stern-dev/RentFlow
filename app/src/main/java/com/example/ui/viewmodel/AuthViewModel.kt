package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.AuthSignInResult
import com.example.data.repository.UserOrgRepository
import com.example.data.repository.UserRepository
import com.example.domain.model.Organization
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthSessionState {
    data object Unauthenticated : AuthSessionState
    data object SigningIn : AuthSessionState
    data class Authenticated(val user: FirebaseUser) : AuthSessionState
    data class Error(val message: String) : AuthSessionState
}

class AuthViewModel(
    private val userRepository: UserRepository,
    private val userOrgRepo: UserOrgRepository,
    val currentUserId: String? = userRepository.currentUserId
) : ViewModel() {

    private var autoSignInAttempted = false

    val currentUser: StateFlow<FirebaseUser?> = userRepository.authStateFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = userRepository.currentUser
        )

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    val authSessionState: StateFlow<AuthSessionState> = combine(
        currentUser,
        _isSigningIn,
        _authError
    ) { user, signingIn, error ->
        when {
            user != null -> AuthSessionState.Authenticated(user)
            signingIn -> AuthSessionState.SigningIn
            !error.isNullOrBlank() -> AuthSessionState.Error(error)
            else -> AuthSessionState.Unauthenticated
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = userRepository.currentUser?.let { AuthSessionState.Authenticated(it) }
            ?: AuthSessionState.Unauthenticated
    )

    val userProfile: StateFlow<UiState<UserProfile?>> =
        if (currentUserId.isNullOrBlank()) {
            flowOf<UiState<UserProfile?>>(UiState.Success(null))
        } else {
            userRepository.observeUserProfile(currentUserId)
                .asUiState("AuthVM", "Failed to load user profile")
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = if (currentUserId.isNullOrBlank()) UiState.Success(null) else UiState.Loading
        )

    val organizations: StateFlow<UiState<List<Organization>>> =
        if (currentUserId.isNullOrBlank()) {
            flowOf<UiState<List<Organization>>>(UiState.Success(emptyList()))
        } else {
            userOrgRepo.observeMyOrganizations(currentUserId)
                .asUiState("AuthVM", "Failed to load organizations")
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = if (currentUserId.isNullOrBlank()) UiState.Success(emptyList()) else UiState.Loading
        )

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    val isBootstrappedAdmin: Boolean
        get() = userRepository.isBootstrappedAdminEmail()

    fun attemptAutoSignIn(context: Context) {
        if (autoSignInAttempted || userRepository.currentUser != null) return
        autoSignInAttempted = true
        viewModelScope.launch {
            userRepository.attemptAutoSignIn(context)
        }
    }

    fun signInWithGoogle(context: Context, onSuccess: () -> Unit = {}) {
        if (_isSigningIn.value) return
        viewModelScope.launch {
            _authError.value = null
            _isSigningIn.value = true
            when (val res = userRepository.signInWithGoogle(context)) {
                is AuthSignInResult.Success -> {
                    _isSigningIn.value = false
                    onSuccess()
                }
                is AuthSignInResult.Cancelled -> {
                    _isSigningIn.value = false
                }
                is AuthSignInResult.Error -> {
                    _isSigningIn.value = false
                    _authError.value = res.message
                }
            }
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            _authError.value = null
            _isSigningIn.value = false
            autoSignInAttempted = true
            userRepository.signOut(context)
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun completeOnboarding(
        fullName: String,
        email: String,
        phoneNumber: String?,
        roles: List<UserRole>,
        activeRole: UserRole,
        organizationName: String?
    ) {
        viewModelScope.launch {
            _busy.value = true
            val res = userOrgRepo.saveOnboardingProfile(
                fullName = fullName,
                email = email,
                phoneNumber = phoneNumber,
                roles = roles,
                activeRole = activeRole,
                organizationName = organizationName
            )
            _busy.value = false
            _feedback.value = res.fold(
                onSuccess = { "Welcome to RentFlow!" },
                onFailure = { it.localizedMessage ?: "Failed to save profile" }
            )
        }
    }

    fun switchRole(role: UserRole, currentRoles: List<String>) {
        viewModelScope.launch {
            _busy.value = true
            val res = userOrgRepo.switchActiveRole(role, currentRoles)
            _busy.value = false
            if (res.isFailure) {
                _feedback.value = res.exceptionOrNull()?.localizedMessage ?: "Unable to switch role"
            }
        }
    }

    fun createOrganization(name: String, dueDay: Long) {
        viewModelScope.launch {
            _busy.value = true
            val res = userOrgRepo.createOrganization(name, dueDay)
            _busy.value = false
            _feedback.value = res.fold(
                onSuccess = { "Organization created" },
                onFailure = { it.localizedMessage ?: "Could not create organization" }
            )
        }
    }

    fun updateProfile(
        fullName: String,
        phoneNumber: String?,
        idNumber: String?,
        emergencyContactName: String?,
        emergencyContactPhone: String?,
        notifyRentDue: Boolean,
        notifyMaintenance: Boolean,
        notifyAnnouncements: Boolean
    ) {
        viewModelScope.launch {
            _busy.value = true
            val res = userOrgRepo.updateProfile(
                fullName,
                phoneNumber,
                idNumber,
                emergencyContactName,
                emergencyContactPhone,
                notifyRentDue,
                notifyMaintenance,
                notifyAnnouncements
            )
            _busy.value = false
            _feedback.value = res.fold(
                onSuccess = { "Profile updated" },
                onFailure = { it.localizedMessage ?: "Failed to update profile" }
            )
        }
    }
}
