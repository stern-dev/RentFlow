package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.data.firebase.AuthSignInResult
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.notifications.FcmTokenRepository
import com.example.domain.model.UserProfile
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface GoogleCredentialResult {
    data class Token(val idToken: String) : GoogleCredentialResult
    data object Cancelled : GoogleCredentialResult
    data object NoCredential : GoogleCredentialResult
    data class Error(val message: String) : GoogleCredentialResult
}

interface GoogleCredentialGateway {
    suspend fun getAutoSignInCredential(context: Context, serverClientId: String): GoogleCredentialResult
    suspend fun getInteractiveSignInCredential(activity: Activity, serverClientId: String): GoogleCredentialResult
    suspend fun clearCredentialState(context: Context)
}

object AndroidGoogleCredentialGateway : GoogleCredentialGateway {
    private const val TAG = "Auth"

    override suspend fun getAutoSignInCredential(
        context: Context,
        serverClientId: String
    ): GoogleCredentialResult {
        return try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(true)
                .build()
            // Supply a single credential option per request for Android 16 responsiveness
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val credential = credentialManager.getCredential(context, request).credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                GoogleCredentialResult.Token(idToken)
            } else {
                GoogleCredentialResult.NoCredential
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Silent Google Sign-In cancelled or dismissed: ${e.message}", e)
            GoogleCredentialResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No saved authorized Google credentials available for silent sign-in.")
            GoogleCredentialResult.NoCredential
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Silent Google Sign-In unavailable: ${e.message}")
            GoogleCredentialResult.Error(e.localizedMessage ?: "Auto sign-in unavailable")
        }
    }

    override suspend fun getInteractiveSignInCredential(
        activity: Activity,
        serverClientId: String
    ): GoogleCredentialResult {
        return try {
            val credentialManager = CredentialManager.create(activity)
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = serverClientId).build()
            // Supply a single credential option per request for Android 16 responsiveness
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
            val credential = credentialManager.getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                GoogleCredentialResult.Token(idToken)
            } else {
                GoogleCredentialResult.Error("Unexpected credential type received")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            GoogleCredentialResult.Cancelled
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            GoogleCredentialResult.Error(e.localizedMessage ?: "Google Sign-In failed")
        }
    }

    override suspend fun clearCredentialState(context: Context) {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }
}

/**
 * Handles Firebase Authentication, Jetpack Credential Manager Google Sign-In,
 * reactive session state observation, and authenticated user profile reads.
 */
class UserRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val credentialGateway: GoogleCredentialGateway = AndroidGoogleCredentialGateway
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val currentUserId: String?
        get() = auth.currentUser?.uid

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        val tokenListener = FirebaseAuth.IdTokenListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(authListener)
        auth.addIdTokenListener(tokenListener)
        awaitClose {
            auth.removeAuthStateListener(authListener)
            auth.removeIdTokenListener(tokenListener)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCurrentUserProfile(): Flow<UserProfile?> =
        authStateFlow().flatMapLatest { firebaseUser ->
            val uid = firebaseUser?.uid
            if (uid.isNullOrBlank()) {
                flowOf(null)
            } else {
                observeUserProfile(uid)
            }
        }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val ex = fsErr ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(ex, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getCurrentUserProfile(): Result<UserProfile?> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User must be signed in before accessing Firestore."))
        val path = "users/$uid"
        return try {
            val snapshot = db.collection("users").document(uid).get().await()
            val profile = if (snapshot.exists()) {
                snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun attemptAutoSignIn(context: Context): Boolean {
        if (auth.currentUser != null) return true
        val clientId = resolveWebClientId(context) ?: return false
        return when (val cred = credentialGateway.getAutoSignInCredential(context, clientId)) {
            is GoogleCredentialResult.Token -> signInWithGoogleIdToken(cred.idToken).isSuccess
            else -> false
        }
    }

    suspend fun signInWithGoogle(context: Context): AuthSignInResult {
        val clientId = resolveWebClientId(context)
            ?: return AuthSignInResult.Error("Google Sign-In configuration missing: default_web_client_id not found")
        val activity = context as? Activity
            ?: return AuthSignInResult.Error("Interactive sign-in requires an Activity context")

        return when (val cred = credentialGateway.getInteractiveSignInCredential(activity, clientId)) {
            is GoogleCredentialResult.Token -> {
                signInWithGoogleIdToken(cred.idToken).fold(
                    onSuccess = { AuthSignInResult.Success },
                    onFailure = { AuthSignInResult.Error(mapAuthException(it)) }
                )
            }
            is GoogleCredentialResult.Cancelled -> AuthSignInResult.Cancelled
            is GoogleCredentialResult.NoCredential ->
                AuthSignInResult.Error("No Google account available on this device. Please add a Google account or use Email & Password.")
            is GoogleCredentialResult.Error -> AuthSignInResult.Error(cred.message)
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String): Result<FirebaseUser> {
        return try {
            require(idToken.isNotBlank()) { "Google ID token must not be blank" }
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user
                ?: return Result.failure(IllegalStateException("Firebase sign-in returned no user."))
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Google credential sign-in failed", e)
            Result.failure(IllegalStateException(mapAuthException(e), e))
        }
    }

    suspend fun signInWithEmail(email: String, password: String): AuthSignInResult {
        val cleanEmail = email.trim()
        if (!isValidEmailFormat(cleanEmail)) {
            return AuthSignInResult.Error("Please enter a valid email address.")
        }
        if (password.isBlank()) {
            return AuthSignInResult.Error("Please enter your password.")
        }
        return try {
            val res = auth.signInWithEmailAndPassword(cleanEmail, password).await()
            val user = res.user
                ?: return AuthSignInResult.Error("Sign-in did not return an authenticated user.")
            if (!user.isEmailVerified) {
                runCatching { user.reload().await() }
            }
            AuthSignInResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Email/password sign-in failed", e)
            AuthSignInResult.Error(mapAuthException(e))
        }
    }

    suspend fun registerWithEmail(fullName: String, email: String, password: String): AuthSignInResult {
        val cleanName = fullName.trim()
        val cleanEmail = email.trim()
        if (cleanName.length < 2) {
            return AuthSignInResult.Error("Please enter your full name (at least 2 characters).")
        }
        if (!isValidEmailFormat(cleanEmail)) {
            return AuthSignInResult.Error("Please enter a valid email address.")
        }
        if (!isStrongPassword(password)) {
            return AuthSignInResult.Error("Password must be at least 8 characters and include both letters and numbers.")
        }
        return try {
            val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
            val user = result.user
                ?: return AuthSignInResult.Error("Account creation failed to return a user.")
            runCatching {
                val profileUpdate = UserProfileChangeRequest.Builder()
                    .setDisplayName(cleanName.take(100))
                    .build()
                user.updateProfile(profileUpdate).await()
            }
            runCatching {
                user.sendEmailVerification().await()
            }.onFailure { err ->
                Log.w(TAG, "Failed to dispatch initial verification email: ${err.message}")
            }
            AuthSignInResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Email/password registration failed", e)
            AuthSignInResult.Error(mapAuthException(e))
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val cleanEmail = email.trim()
        if (!isValidEmailFormat(cleanEmail)) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        return try {
            auth.sendPasswordResetEmail(cleanEmail).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Password reset failed", e)
            Result.failure(IllegalStateException(mapAuthException(e), e))
        }
    }

    suspend fun resendVerificationEmail(): Result<Unit> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("No active account found. Please sign in again."))
        return try {
            user.sendEmailVerification().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Resend verification email failed", e)
            Result.failure(IllegalStateException(mapAuthException(e), e))
        }
    }

    suspend fun refreshEmailVerificationStatus(): Result<Boolean> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("No active account found. Please sign in again."))
        return try {
            user.reload().await()
            // Force-refresh ID token so email_verified=true propagates immediately to Firestore security rules.
            user.getIdToken(true).await()
            Result.success(auth.currentUser?.isEmailVerified == true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to refresh verification status", e)
            Result.failure(IllegalStateException(mapAuthException(e), e))
        }
    }

    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            runCatching {
                FcmTokenRepository(context.applicationContext).unregisterCurrentTokenBeforeSignOut()
            }.onFailure { err ->
                Log.w(TAG, "Failed to unregister FCM token on sign-out: ${err.message}")
            }
            try {
                credentialGateway.clearCredentialState(context)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                auth.signOut()
            }
        }
    }

    fun isBootstrappedAdminEmail(): Boolean {
        val user = auth.currentUser
        return user?.email == BOOTSTRAPPED_ADMIN_EMAIL && user.isEmailVerified
    }

    fun usesPasswordAuthOnly(): Boolean {
        val user = auth.currentUser ?: return false
        val providers = user.providerData.map { it.providerId }
        return EmailAuthProvider.PROVIDER_ID in providers && GoogleAuthProvider.PROVIDER_ID !in providers
    }

    fun isRecentlySignedIn(maxAgeMs: Long = DEFAULT_REAUTH_WINDOW_MS): Boolean {
        val lastSignIn = auth.currentUser?.metadata?.lastSignInTimestamp ?: return false
        if (lastSignIn <= 0L) return false
        return (System.currentTimeMillis() - lastSignIn) <= maxAgeMs
    }

    suspend fun reauthenticateWithGoogle(context: Context): Result<Unit> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("User must be signed in before verifying."))
        val clientId = resolveWebClientId(context)
            ?: return Result.failure(IllegalStateException("Google Sign-In configuration missing: default_web_client_id not found"))
        val activity = context as? Activity
            ?: return Result.failure(IllegalStateException("Re-authentication requires an Activity context"))

        return when (val cred = credentialGateway.getInteractiveSignInCredential(activity, clientId)) {
            is GoogleCredentialResult.Token -> reauthenticateWithGoogleIdToken(cred.idToken, user)
            is GoogleCredentialResult.Cancelled -> Result.failure(IllegalStateException("Identity verification cancelled."))
            is GoogleCredentialResult.NoCredential ->
                Result.failure(IllegalStateException("No Google account available on this device for verification."))
            is GoogleCredentialResult.Error -> Result.failure(IllegalStateException(cred.message))
        }
    }

    suspend fun reauthenticateWithGoogleIdToken(
        idToken: String,
        targetUser: FirebaseUser? = auth.currentUser
    ): Result<Unit> {
        return try {
            val user = targetUser
                ?: return Result.failure(IllegalStateException("User must be signed in before verifying."))
            require(idToken.isNotBlank()) { "Google ID token must not be blank" }
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            user.reauthenticate(firebaseCredential).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Google re-authentication failed", e)
            Result.failure(IllegalStateException(mapAuthException(e), e))
        }
    }

    suspend fun reauthenticateWithPassword(
        password: String,
        targetUser: FirebaseUser? = auth.currentUser
    ): Result<Unit> {
        return try {
            val user = targetUser
                ?: return Result.failure(IllegalStateException("User must be signed in before verifying."))
            val email = user.email?.trim().orEmpty()
            if (email.isBlank()) {
                return Result.failure(IllegalStateException("Account email unavailable for password verification."))
            }
            if (password.isBlank()) {
                return Result.failure(IllegalArgumentException("Enter your password to verify this action."))
            }
            val credential = EmailAuthProvider.getCredential(email, password)
            user.reauthenticate(credential).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firebase password re-authentication failed", e)
            Result.failure(IllegalStateException(mapAuthException(e), e))
        }
    }

    private fun resolveWebClientId(context: Context): String? {
        return try {
            context.getString(R.string.default_web_client_id).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun mapAuthException(error: Throwable): String {
        return when (error) {
            is FirebaseAuthWeakPasswordException ->
                "Password is too weak. Use at least 8 characters with letters and numbers."
            is FirebaseAuthInvalidCredentialsException -> {
                when (error.errorCode) {
                    "ERROR_INVALID_EMAIL" -> "Please enter a valid email address."
                    "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" ->
                        "Incorrect email or password. Please check your credentials and try again."
                    else -> error.localizedMessage ?: "Invalid authentication credentials."
                }
            }
            is FirebaseAuthInvalidUserException -> {
                when (error.errorCode) {
                    "ERROR_USER_DISABLED" -> "This account has been disabled. Contact support."
                    "ERROR_USER_NOT_FOUND" -> "No account found with this email. Switch to Create Account to register."
                    else -> error.localizedMessage ?: "Account unavailable."
                }
            }
            is FirebaseAuthUserCollisionException ->
                "An account with this email already exists. Try signing in or using Google Sign-In."
            is FirebaseAuthException -> {
                when (error.errorCode) {
                    "ERROR_OPERATION_NOT_ALLOWED" ->
                        "Email/Password sign-in is not yet enabled in Firebase Console (Authentication -> Sign-in method). Use Google Sign-In or enable the provider."
                    "ERROR_TOO_MANY_REQUESTS" ->
                        "Too many authentication attempts. Please wait a moment and try again."
                    "ERROR_REQUIRES_RECENT_LOGIN" ->
                        "Please verify your credentials again to complete this sensitive action."
                    "ERROR_NETWORK_REQUEST_FAILED" ->
                        "Network error. Check your internet connection and try again."
                    else -> error.localizedMessage ?: "Authentication failed (${error.errorCode})."
                }
            }
            is FirebaseNetworkException ->
                "Network error. Check your internet connection and try again."
            else -> error.localizedMessage ?: "Authentication failed. Please try again."
        }
    }

    companion object {
        private const val TAG = "UserRepository"
        private const val BOOTSTRAPPED_ADMIN_EMAIL = "sternomolo2@gmail.com"
        const val DEFAULT_REAUTH_WINDOW_MS = 5 * 60 * 1000L

        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        fun isValidEmailFormat(email: String): Boolean = EMAIL_REGEX.matches(email.trim())

        fun isStrongPassword(password: String): Boolean =
            password.length >= 8 && password.any { it.isLetter() } && password.any { it.isDigit() }
    }
}
