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
import com.example.domain.model.UserProfile
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
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
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
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
            ?: return Result.failure(IllegalStateException("User must be signed in with Google."))
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
                    onFailure = { AuthSignInResult.Error(it.localizedMessage ?: "Firebase authentication failed") }
                )
            }
            is GoogleCredentialResult.Cancelled -> AuthSignInResult.Cancelled
            is GoogleCredentialResult.NoCredential ->
                AuthSignInResult.Error("No Google account available on this device. Please add a Google account and try again.")
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
            Result.failure(e)
        }
    }

    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
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

    private fun resolveWebClientId(context: Context): String? {
        return try {
            context.getString(R.string.default_web_client_id).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "UserRepository"
        private const val BOOTSTRAPPED_ADMIN_EMAIL = "sternomolo2@gmail.com"
    }
}
