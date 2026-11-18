package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.domain.model.AuditLog
import com.example.domain.model.Organization
import com.example.domain.model.ReportedIssue
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class UserOrgRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing RentFlow.")

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        require(userId.isNotBlank())
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
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

    fun observeMyOrganizations(userId: String): Flow<List<Organization>> = flow {
        require(userId.isNotBlank())
        val path = "organizations"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Organization::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val ex = fsErr ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveOnboardingProfile(
        fullName: String,
        email: String,
        phoneNumber: String?,
        roles: List<UserRole>,
        activeRole: UserRole,
        organizationName: String?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val userPath = "users/$uid"
        return try {
            require(fullName.trim().length in 1..100) { "Full name must be 1 to 100 characters" }
            require(roles.isNotEmpty()) { "Select at least one role" }
            val safeRoles = roles.filter { it != UserRole.ADMIN }.ifEmpty { listOf(UserRole.TENANT) }
            val safeActive = if (activeRole == UserRole.ADMIN && !isBootstrappedAdminEmail()) safeRoles.first() else activeRole

            var orgId: String? = null
            if (!organizationName.isNullOrBlank() && (UserRole.LANDLORD in safeRoles || UserRole.PROPERTY_MANAGER in safeRoles)) {
                val orgRef = db.collection("organizations").document()
                orgId = orgRef.id
                val org = Organization(
                    name = organizationName.trim().take(120),
                    ownerUid = uid,
                    country = "Kenya",
                    currency = "KES",
                    defaultDueDay = 5L,
                    memberUids = listOf(uid),
                    suspended = false
                )
                orgRef.set(org.toCreateMap()).await()
            }

            val finalRoles = if (isBootstrappedAdminEmail() && UserRole.ADMIN !in safeRoles) {
                (safeRoles + UserRole.ADMIN).map { it.name }
            } else {
                safeRoles.map { it.name }
            }

            val profile = UserProfile(
                userId = uid,
                fullName = fullName.trim().take(100),
                email = email.trim().take(150),
                phoneNumber = phoneNumber?.trim()?.take(30),
                roles = finalRoles,
                activeRole = safeActive.name,
                activeOrgId = orgId,
                notifyRentDue = true,
                notifyMaintenance = true,
                notifyAnnouncements = true,
                suspended = false
            )
            db.collection("users").document(uid).set(profile.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, userPath)
            Result.failure(e)
        }
    }

    suspend fun createOrganization(name: String, defaultDueDay: Long = 5L): Result<String> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val orgRef = db.collection("organizations").document()
        return try {
            require(name.trim().length in 2..120) { "Organization name must be 2-120 characters" }
            val org = Organization(
                name = name.trim(),
                ownerUid = uid,
                country = "Kenya",
                currency = "KES",
                defaultDueDay = defaultDueDay.coerceIn(1L, 28L),
                memberUids = listOf(uid),
                suspended = false
            )
            orgRef.set(org.toCreateMap()).await()
            db.collection("users").document(uid).update(
                mapOf("activeOrgId" to orgRef.id, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(orgRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, orgRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        fullName: String,
        phoneNumber: String?,
        idNumber: String?,
        emergencyContactName: String?,
        emergencyContactPhone: String?,
        notifyRentDue: Boolean,
        notifyMaintenance: Boolean,
        notifyAnnouncements: Boolean
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val path = "users/$uid"
        return try {
            require(fullName.trim().length in 1..100) { "Full name must be 1-100 characters" }
            val updates = mutableMapOf<String, Any>(
                "fullName" to fullName.trim().take(100),
                "notifyRentDue" to notifyRentDue,
                "notifyMaintenance" to notifyMaintenance,
                "notifyAnnouncements" to notifyAnnouncements,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!phoneNumber.isNullOrBlank()) updates["phoneNumber"] = phoneNumber.trim().take(30)
            if (!idNumber.isNullOrBlank()) updates["idNumber"] = idNumber.trim().take(40)
            if (!emergencyContactName.isNullOrBlank()) updates["emergencyContactName"] = emergencyContactName.trim().take(100)
            if (!emergencyContactPhone.isNullOrBlank()) updates["emergencyContactPhone"] = emergencyContactPhone.trim().take(30)

            db.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun switchActiveRole(role: UserRole, currentRoles: List<String>): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val path = "users/$uid"
        return try {
            val updatedRoles = if (role.name in currentRoles) currentRoles else (currentRoles + role.name).distinct().take(5)
            db.collection("users").document(uid).update(
                mapOf(
                    "roles" to updatedRoles,
                    "activeRole" to role.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // Admin Streams & Actions
    fun observeAllUsersForAdmin(): Flow<List<UserProfile>> =
        db.collection("users").snapshots()
            .map { it.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    fun observeAllOrganizationsForAdmin(): Flow<List<Organization>> =
        db.collection("organizations").snapshots()
            .map { it.toObjects(Organization::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    fun observeAuditLogsForUserOrAdmin(userId: String, isAdmin: Boolean): Flow<List<AuditLog>> {
        val query = if (isAdmin) {
            db.collection("audit_logs")
        } else {
            db.collection("audit_logs").whereEqualTo("actorUid", userId)
        }
        return query.snapshots()
            .map { snap ->
                snap.toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
    }

    fun observeReportedIssues(userId: String, isAdmin: Boolean): Flow<List<ReportedIssue>> {
        val query = if (isAdmin) {
            db.collection("reported_issues")
        } else {
            db.collection("reported_issues").whereEqualTo("reporterUid", userId)
        }
        return query.snapshots()
            .map { snap ->
                snap.toObjects(ReportedIssue::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
    }

    suspend fun setUserSuspended(targetUid: String, suspended: Boolean): Result<Unit> {
        val path = "users/$targetUid"
        return try {
            requireUserId()
            db.collection("users").document(targetUid).update(
                mapOf("suspended" to suspended, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setOrganizationSuspended(orgId: String, suspended: Boolean): Result<Unit> {
        val path = "organizations/$orgId"
        return try {
            requireUserId()
            db.collection("organizations").document(orgId).update(
                mapOf("suspended" to suspended, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateReportedIssueStatus(issueId: String, status: String, adminNotes: String): Result<Unit> {
        val path = "reported_issues/$issueId"
        return try {
            requireUserId()
            db.collection("reported_issues").document(issueId).update(
                mapOf(
                    "status" to status,
                    "adminNotes" to adminNotes.trim().take(800),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun isBootstrappedAdminEmail(): Boolean {
        val user = auth.currentUser
        return user?.email == "sternomolo2@gmail.com" && user.isEmailVerified
    }
}
