package com.example.data.notifications

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.domain.model.FcmDeviceToken
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest

class FcmTokenRepository(
    private val context: Context,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(
        context.applicationContext.getString(R.string.firestore_database_id)
    ),
    private val auth: FirebaseAuth = Firebase.auth
) {
    suspend fun syncCurrentToken(profile: UserProfile): Result<String> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("User is not signed in."))
        if (!user.isEmailVerified) {
            return Result.failure(IllegalStateException("Email verification required before registering FCM token."))
        }
        return try {
            val token = FirebaseMessaging.getInstance().token.await().trim()
            require(token.length >= 10) { "Received invalid FCM token." }
            upsertToken(
                uid = user.uid,
                token = token,
                notifyMaintenance = profile.notifyMaintenance,
                activeRole = profile.activeRole
            ).getOrThrow()
            Result.success(token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "FCM token sync skipped/failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun registerRefreshedToken(token: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.success(Unit)
        if (!user.isEmailVerified) return Result.success(Unit)
        val cleanToken = token.trim()
        if (cleanToken.length < 10) return Result.failure(IllegalArgumentException("Invalid FCM token"))

        return try {
            val userDoc = db.collection("users").document(user.uid).get().await()
            val notifyMaint = userDoc.getBoolean("notifyMaintenance") ?: true
            val activeRole = userDoc.getString("activeRole") ?: UserRole.TENANT.name
            upsertToken(
                uid = user.uid,
                token = cleanToken,
                notifyMaintenance = notifyMaint,
                activeRole = activeRole
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/${user.uid}/fcm_tokens")
            Result.failure(e)
        }
    }

    suspend fun upsertToken(
        uid: String,
        token: String,
        notifyMaintenance: Boolean,
        activeRole: String
    ): Result<Unit> {
        val cleanUid = uid.trim()
        val cleanToken = token.trim().take(512)
        val docId = tokenDocId(cleanToken)
        val path = "users/$cleanUid/fcm_tokens/$docId"
        return try {
            require(cleanUid.isNotBlank()) { "User ID is required." }
            require(cleanToken.length >= 10) { "Invalid FCM registration token." }
            val validRole = UserRole.entries.firstOrNull { it.name == activeRole }?.name ?: UserRole.TENANT.name
            val docRef = db.collection("users").document(cleanUid)
                .collection("fcm_tokens").document(docId)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "token" to cleanToken,
                        "platform" to "ANDROID",
                        "notifyMaintenance" to notifyMaintenance,
                        "activeRole" to validRole,
                        "appVersion" to BuildConfig.VERSION_NAME.take(40),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val payload = FcmDeviceToken(
                    userId = cleanUid,
                    token = cleanToken,
                    platform = "ANDROID",
                    notifyMaintenance = notifyMaintenance,
                    activeRole = validRole,
                    appVersion = BuildConfig.VERSION_NAME.take(40)
                ).toCreateMap()
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun unregisterCurrentTokenBeforeSignOut() {
        val user = auth.currentUser ?: return
        val uid = user.uid
        try {
            val token = withTimeoutOrNull(1500L) {
                FirebaseMessaging.getInstance().token.await()
            }?.trim() ?: return
            if (token.length < 10) return
            val docId = tokenDocId(token)
            withTimeoutOrNull(1500L) {
                db.collection("users").document(uid)
                    .collection("fcm_tokens").document(docId)
                    .delete()
                    .await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not remove FCM token on sign-out: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "FcmTokenRepo"

        fun tokenDocId(token: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(token.trim().toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.take(32)
        }
    }
}
