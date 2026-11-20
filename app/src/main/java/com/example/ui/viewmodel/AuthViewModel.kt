package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.AuthSignInResult
import com.example.data.repository.UserOrgRepository
import com.example.data.repository.UserRepository
import com.example.domain.model.Organization
import com.example.domain.model.RoleInvite
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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

    private val _manualVerifiedOverride = MutableStateFlow(false)

    val isEmailVerified: StateFlow<Boolean> = combine(
        currentUser,
        _manualVerifiedOverride
    ) { user, override ->
        (user?.isEmailVerified == true) || (user != null && override)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = userRepository.currentUser?.isEmailVerified == true
    )

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _authInfoMessage = MutableStateFlow<String?>(null)
    val authInfoMessage: StateFlow<String?> = _authInfoMessage.asStateFlow()

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

    @OptIn(ExperimentalCoroutinesApi::class)
    val incomingInvites: StateFlow<UiState<List<RoleInvite>>> =
        if (currentUserId.isNullOrBlank()) {
            flowOf<UiState<List<RoleInvite>>>(UiState.Success(emptyList()))
        } else {
            currentUser.flatMapLatest { user ->
                val email = user?.email
                if (email.isNullOrBlank()) flowOf(emptyList())
                else userOrgRepo.observeIncomingRoleInvites(email)
            }.asUiState("AuthVM", "Failed to load role invitations")
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = if (currentUserId.isNullOrBlank()) UiState.Success(emptyList()) else UiState.Loading
        )

    private val _activeInviteOrgId = MutableStateFlow<String?>(null)

    fun setActiveInviteOrgId(orgId: String?) {
        val clean = orgId?.trim()?.takeIf { it.isNotBlank() }
        if (_activeInviteOrgId.value != clean) {
            _activeInviteOrgId.value = clean
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val orgInvites: StateFlow<UiState<List<RoleInvite>>> =
        if (currentUserId.isNullOrBlank()) {
            flowOf<UiState<List<RoleInvite>>>(UiState.Success(emptyList()))
        } else {
            _activeInviteOrgId.flatMapLatest { orgId ->
                if (orgId.isNullOrBlank()) flowOf(emptyList())
                else userOrgRepo.observeOrgRoleInvites(orgId, currentUserId)
            }.asUiState("AuthVM", "Failed to load organization invitations")
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

    fun usesPasswordAuthOnly(): Boolean = userRepository.usesPasswordAuthOnly()

    fun isRecentlySignedIn(): Boolean = userRepository.isRecentlySignedIn()

    suspend fun verifyRecentGoogleAuth(context: Context): Result<Unit> =
        if (userRepository.isRecentlySignedIn()) {
            Result.success(Unit)
        } else {
            userRepository.reauthenticateWithGoogle(context)
        }

    suspend fun verifyPasswordReauth(password: String): Result<Unit> =
        if (userRepository.isRecentlySignedIn()) {
            Result.success(Unit)
        } else {
            userRepository.reauthenticateWithPassword(password)
        }

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
            _authInfoMessage.value = null
            _manualVerifiedOverride.value = false
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

    fun signInWithEmail(email: String, password: String, onSuccess: () -> Unit = {}) {
        if (_isSigningIn.value) return
        viewModelScope.launch {
            _authError.value = null
            _authInfoMessage.value = null
            _manualVerifiedOverride.value = false
            _isSigningIn.value = true
            when (val res = userRepository.signInWithEmail(email, password)) {
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

    fun registerWithEmail(fullName: String, email: String, password: String, onSuccess: () -> Unit = {}) {
        if (_isSigningIn.value) return
        viewModelScope.launch {
            _authError.value = null
            _authInfoMessage.value = null
            _manualVerifiedOverride.value = false
            _isSigningIn.value = true
            when (val res = userRepository.registerWithEmail(fullName, email, password)) {
                is AuthSignInResult.Success -> {
                    _isSigningIn.value = false
                    _authInfoMessage.value = "Account created! Check your inbox for the verification link."
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

    fun sendPasswordReset(email: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _authError.value = null
            _authInfoMessage.value = null
            _isSigningIn.value = true
            val res = userRepository.sendPasswordReset(email)
            _isSigningIn.value = false
            res.fold(
                onSuccess = {
                    _authInfoMessage.value = "Password reset link sent to ${email.trim().lowercase()}. Check your inbox."
                    onSuccess()
                },
                onFailure = {
                    _authError.value = it.localizedMessage ?: "Failed to send password reset email."
                }
            )
        }
    }

    fun resendVerificationEmail() {
        viewModelScope.launch {
            _authError.value = null
            _authInfoMessage.value = null
            _busy.value = true
            val res = userRepository.resendVerificationEmail()
            _busy.value = false
            res.fold(
                onSuccess = {
                    _authInfoMessage.value = "Verification email sent. Please open the link in your inbox, then tap Continue."
                },
                onFailure = {
                    _authError.value = it.localizedMessage ?: "Could not resend verification email."
                }
            )
        }
    }

    fun refreshEmailVerification() {
        viewModelScope.launch {
            _authError.value = null
            _authInfoMessage.value = null
            _busy.value = true
            val res = userRepository.refreshEmailVerificationStatus()
            _busy.value = false
            res.fold(
                onSuccess = { verified ->
                    if (verified) {
                        _manualVerifiedOverride.value = true
                    } else {
                        _authError.value = "Email is not verified yet. Please click the verification link in your inbox and try again."
                    }
                },
                onFailure = {
                    _authError.value = it.localizedMessage ?: "Failed to check verification status."
                }
            )
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            _authError.value = null
            _authInfoMessage.value = null
            _manualVerifiedOverride.value = false
            _isSigningIn.value = false
            autoSignInAttempted = true
            userRepository.signOut(context)
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun clearAuthMessages() {
        _authError.value = null
        _authInfoMessage.value = null
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
        organizationName: String?,
        invitesToAccept: List<RoleInvite> = emptyList()
    ) {
        viewModelScope.launch {
            _busy.value = true
            val res = userOrgRepo.saveOnboardingProfile(
                fullName = fullName,
                email = email,
                phoneNumber = phoneNumber,
                roles = roles,
                activeRole = activeRole,
                organizationName = organizationName,
                invitesToAccept = invitesToAccept
            )
            _busy.value = false
            _feedback.value = res.fold(
                onSuccess = {
                    if (invitesToAccept.isNotEmpty()) "Welcome to RentFlow! Joined ${invitesToAccept.first().orgName}."
                    else "Welcome to RentFlow!"
                },
                onFailure = { it.localizedMessage ?: "Failed to save profile" }
            )
        }
    }

    fun switchRole(role: UserRole, currentRoles: List<String>, actorName: String = "User") {
        viewModelScope.launch {
            _busy.value = true
            val res = userOrgRepo.switchActiveRole(role, currentRoles, actorName)
            _busy.value = false
            if (res.isFailure) {
                _feedback.value = res.exceptionOrNull()?.localizedMessage ?: "Unable to switch role"
            }
        }
    }

    fun createRoleInvite(
        org: Organization,
        inviteeEmail: String,
        role: UserRole,
        inviterName: String,
        propertyIds: List<String> = emptyList(),
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _busy.value = true
            val res = userOrgRepo.createRoleInvite(
                org = org,
                inviteeEmail = inviteeEmail,
                role = role,
                inviterName = inviterName,
                propertyIds = propertyIds
            )
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = "Invitation sent to ${inviteeEmail.trim().lowercase()}"
                    onSuccess()
                },
                onFailure = { _feedback.value = it.localizedMessage ?: "Could not send invitation" }
            )
        }
    }

    fun acceptRoleInvite(invite: RoleInvite, currentProfile: UserProfile) {
        viewModelScope.launch {
            _busy.value = true
            val res = userOrgRepo.acceptRoleInvite(invite, currentProfile)
            _busy.value = false
            _feedback.value = res.fold(
                onSuccess = { "Joined ${invite.orgName} as ${invite.roleEnum().label}" },
                onFailure = { it.localizedMessage ?: "Could not accept invitation" }
            )
        }
    }

    fun declineRoleInvite(invite: RoleInvite, actorName: String) {
        viewModelScope.launch {
            _busy.value = true
            val res = userOrgRepo.declineRoleInvite(invite, actorName)
            _busy.value = false
            _feedback.value = res.fold(
                onSuccess = { "Invitation declined" },
                onFailure = { it.localizedMessage ?: "Could not decline invitation" }
            )
        }
    }

    fun revokeRoleInvite(invite: RoleInvite, actorName: String) {
        viewModelScope.launch {
            _busy.value = true
            val res = userOrgRepo.revokeRoleInvite(invite, actorName)
            _busy.value = false
            _feedback.value = res.fold(
                onSuccess = { "Invitation revoked" },
                onFailure = { it.localizedMessage ?: "Could not revoke invitation" }
            )
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
