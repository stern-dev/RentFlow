package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.domain.model.AppNotification
import com.example.domain.model.AuditLog
import com.example.domain.model.InviteStatus
import com.example.domain.model.NotificationType
import com.example.domain.model.Organization
import com.example.domain.model.ReportedIssue
import com.example.domain.model.RoleInvite
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
import kotlinx.coroutines.flow.flowOf
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

    private suspend fun writeAuditLogSafe(
        orgId: String,
        actorUid: String,
        actorName: String,
        action: String,
        entityType: String,
        entityId: String,
        details: String
    ) {
        val audit = AuditLog(
            orgId = orgId.ifBlank { "user_$actorUid" },
            actorUid = actorUid,
            actorName = actorName.trim().ifBlank { "RentFlow User" }.take(100),
            action = action.trim().take(80),
            entityType = entityType.trim().take(50),
            entityId = entityId.trim().ifBlank { actorUid }.take(128),
            details = details.trim().ifBlank { action }.take(500)
        )
        db.collection("audit_logs").document().set(audit.toCreateMap()).await()
    }

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

    fun observeIncomingRoleInvites(email: String?): Flow<List<RoleInvite>> {
        val cleanEmail = email?.trim()?.lowercase().orEmpty()
        if (cleanEmail.isBlank()) return flowOf(emptyList())
        val path = "role_invites"
        return flow {
            emitAll(
                db.collection(path)
                    .whereEqualTo("inviteeEmail", cleanEmail)
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(RoleInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedByDescending { it.createdAt?.seconds ?: 0L }
                    }
                    .catch { error ->
                        val fsErr = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && error is CancellationException) throw error
                        val ex = fsErr ?: (error as? Exception)
                        if (ex != null && auth.currentUser != null) {
                            handleFirestoreError(ex, OperationType.LIST, path)
                        }
                        throw error
                    }
            )
        }
    }

    fun observeOrgRoleInvites(orgId: String?, inviterUid: String?): Flow<List<RoleInvite>> {
        val cleanOrgId = orgId?.trim().orEmpty()
        val cleanUid = inviterUid?.trim().orEmpty()
        if (cleanOrgId.isBlank() || cleanUid.isBlank()) return flowOf(emptyList())
        val path = "role_invites"
        return flow {
            emitAll(
                db.collection(path)
                    .whereEqualTo("inviterUid", cleanUid)
                    .whereEqualTo("orgId", cleanOrgId)
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(RoleInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedByDescending { it.createdAt?.seconds ?: 0L }
                    }
                    .catch { error ->
                        val fsErr = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && error is CancellationException) throw error
                        val ex = fsErr ?: (error as? Exception)
                        if (ex != null && auth.currentUser?.uid == cleanUid) {
                            handleFirestoreError(ex, OperationType.LIST, path)
                        }
                        throw error
                    }
            )
        }
    }

    suspend fun saveOnboardingProfile(
        fullName: String,
        email: String,
        phoneNumber: String?,
        roles: List<UserRole>,
        activeRole: UserRole,
        organizationName: String?,
        invitesToAccept: List<RoleInvite> = emptyList()
    ): Result<Unit> {
        val currentUser = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        val uid = currentUser.uid
        val verifiedEmail = (currentUser.email?.trim()?.takeIf { it.isNotBlank() } ?: email.trim()).take(150)
        val userPath = "users/$uid"
        return try {
            require(fullName.trim().length in 1..100) { "Full name must be 1 to 100 characters" }
            val invitedRoles = invitesToAccept
                .map { it.roleEnum() }
                .filter { it != UserRole.ADMIN }
            val combinedRoles = (roles + invitedRoles)
                .filter { it != UserRole.ADMIN }
                .distinct()
            require(combinedRoles.isNotEmpty()) { "Select at least one role" }

            val safeActive = if (activeRole == UserRole.ADMIN && !isBootstrappedAdminEmail()) {
                combinedRoles.first()
            } else if (activeRole !in combinedRoles && activeRole != UserRole.ADMIN) {
                combinedRoles.first()
            } else {
                activeRole
            }

            val willCreateOrg = !organizationName.isNullOrBlank() && UserRole.LANDLORD in combinedRoles
            val orgRef = if (willCreateOrg) db.collection("organizations").document() else null
            val orgId: String? = orgRef?.id

            val finalRoles = if (isBootstrappedAdminEmail() && UserRole.ADMIN !in combinedRoles) {
                (combinedRoles + UserRole.ADMIN).map { it.name }
            } else {
                combinedRoles.map { it.name }
            }

            val profile = UserProfile(
                userId = uid,
                fullName = fullName.trim().take(100),
                email = verifiedEmail,
                phoneNumber = phoneNumber?.trim()?.take(30),
                roles = finalRoles,
                activeRole = safeActive.name,
                activeOrgId = orgId ?: invitesToAccept.firstOrNull()?.orgId,
                notifyRentDue = true,
                notifyMaintenance = true,
                notifyAnnouncements = true,
                suspended = false
            )
            // Create the verified user profile first so subsequent role-gated writes (e.g., organization creation / invite acceptance) pass Firestore RBAC lookups.
            db.collection("users").document(uid).set(profile.toCreateMap()).await()

            if (orgRef != null && !organizationName.isNullOrBlank()) {
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

            writeAuditLogSafe(
                orgId = orgId ?: "user_$uid",
                actorUid = uid,
                actorName = fullName,
                action = "ONBOARD_USER",
                entityType = "UserProfile",
                entityId = uid,
                details = "Completed onboarding with locked roles: ${finalRoles.joinToString(", ")}"
            )

            var rollingProfile = profile
            for (invite in invitesToAccept) {
                val inviteRes = acceptRoleInvite(invite, rollingProfile)
                if (inviteRes.isSuccess) {
                    val nextRoles = (rollingProfile.roles + invite.role).distinct().take(5)
                    rollingProfile = rollingProfile.copy(
                        roles = nextRoles,
                        activeRole = invite.role,
                        activeOrgId = invite.orgId,
                        lastAcceptedInviteId = invite.id
                    )
                } else {
                    return Result.failure(
                        inviteRes.exceptionOrNull()
                            ?: IllegalStateException("Profile created, but failed to accept invitation for ${invite.orgName}")
                    )
                }
            }
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
            writeAuditLogSafe(
                orgId = orgRef.id,
                actorUid = uid,
                actorName = auth.currentUser?.displayName ?: "Owner",
                action = "CREATE_ORGANIZATION",
                entityType = "Organization",
                entityId = orgRef.id,
                details = "Created organization '${name.trim()}'"
            )
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

    suspend fun switchActiveRole(
        role: UserRole,
        currentRoles: List<String>,
        actorName: String = "User"
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val path = "users/$uid"
        return try {
            if (role == UserRole.ADMIN && !isBootstrappedAdminEmail()) {
                return Result.failure(SecurityException("Administrator access requires a verified platform admin account."))
            }
            if (role.name !in currentRoles) {
                return Result.failure(
                    SecurityException("Role '${role.label}' is not granted to your account. Accept an organization invitation or request an Admin grant.")
                )
            }
            db.collection("users").document(uid).update(
                mapOf(
                    "activeRole" to role.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLogSafe(
                orgId = "user_$uid",
                actorUid = uid,
                actorName = actorName,
                action = "SWITCH_ROLE",
                entityType = "UserProfile",
                entityId = uid,
                details = "Switched active workspace role to ${role.name}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun createRoleInvite(
        org: Organization,
        inviteeEmail: String,
        role: UserRole,
        inviterName: String,
        propertyIds: List<String> = emptyList()
    ): Result<String> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val inviteRef = db.collection("role_invites").document()
        return try {
            if (org.ownerUid != uid && !isBootstrappedAdminEmail()) {
                return Result.failure(SecurityException("Only the organization owner can invite team members."))
            }
            if (role == UserRole.ADMIN) {
                return Result.failure(SecurityException("Administrator role cannot be granted via invitation."))
            }
            val cleanEmail = inviteeEmail.trim().lowercase()
            require(cleanEmail.length in 3..150 && "@" in cleanEmail && "." in cleanEmail.substringAfter("@")) {
                "Enter a valid invitee email address"
            }
            val invite = RoleInvite(
                orgId = org.id,
                orgName = org.name.take(120),
                propertyIds = propertyIds.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(20),
                inviterUid = uid,
                inviterName = inviterName.trim().ifBlank { "Organization Owner" }.take(100),
                inviteeEmail = cleanEmail,
                role = role.name,
                status = InviteStatus.PENDING.name
            )
            inviteRef.set(invite.toCreateMap()).await()
            writeAuditLogSafe(
                orgId = org.id,
                actorUid = uid,
                actorName = inviterName,
                action = "CREATE_INVITE",
                entityType = "RoleInvite",
                entityId = inviteRef.id,
                details = "Invited $cleanEmail as ${role.name} in ${org.name}"
            )
            Result.success(inviteRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, inviteRef.path)
            Result.failure(e)
        }
    }

    suspend fun acceptRoleInvite(
        invite: RoleInvite,
        currentProfile: UserProfile
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val invitePath = "role_invites/${invite.id}"
        return try {
            require(invite.id.isNotBlank()) { "Invite ID is required" }
            if (invite.statusEnum() != InviteStatus.PENDING) {
                return Result.failure(IllegalStateException("This invitation is no longer pending."))
            }
            if (invite.role == UserRole.ADMIN.name) {
                return Result.failure(SecurityException("Invalid role invitation target."))
            }
            val authEmail = auth.currentUser?.email?.trim()?.lowercase().orEmpty()
            if (authEmail.isBlank() || authEmail != invite.inviteeEmail.trim().lowercase()) {
                return Result.failure(SecurityException("Signed-in email does not match this invitation."))
            }

            val updatedRoles = if (invite.role in currentProfile.roles) {
                currentProfile.roles
            } else {
                (currentProfile.roles + invite.role).distinct().take(5)
            }

            val batch = db.batch()
            batch.update(
                db.collection("role_invites").document(invite.id),
                mapOf(
                    "status" to InviteStatus.ACCEPTED.name,
                    "acceptedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "roles" to updatedRoles,
                    "activeRole" to invite.role,
                    "activeOrgId" to invite.orgId,
                    "lastAcceptedInviteId" to invite.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("organizations").document(invite.orgId),
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            writeAuditLogSafe(
                orgId = invite.orgId,
                actorUid = uid,
                actorName = currentProfile.fullName,
                action = "ACCEPT_INVITE",
                entityType = "RoleInvite",
                entityId = invite.id,
                details = "Accepted ${invite.role} role invitation for ${invite.orgName}"
            )
            db.collection("notifications").document().set(
                AppNotification(
                    recipientUid = invite.inviterUid,
                    senderUid = uid,
                    orgId = invite.orgId,
                    type = NotificationType.ANNOUNCEMENT.name,
                    title = "Invitation Accepted",
                    body = "${currentProfile.fullName} accepted the ${invite.roleEnum().label} invitation for ${invite.orgName}.",
                    relatedEntityId = invite.id,
                    read = false
                ).toCreateMap()
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, invitePath)
            Result.failure(e)
        }
    }

    suspend fun declineRoleInvite(invite: RoleInvite, actorName: String): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val invitePath = "role_invites/${invite.id}"
        return try {
            require(invite.id.isNotBlank()) { "Invite ID is required" }
            db.collection("role_invites").document(invite.id).update(
                mapOf(
                    "status" to InviteStatus.DECLINED.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLogSafe(
                orgId = "user_$uid",
                actorUid = uid,
                actorName = actorName,
                action = "DECLINE_INVITE",
                entityType = "RoleInvite",
                entityId = invite.id,
                details = "Declined ${invite.role} invitation from ${invite.orgName}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, invitePath)
            Result.failure(e)
        }
    }

    suspend fun revokeRoleInvite(invite: RoleInvite, actorName: String): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val invitePath = "role_invites/${invite.id}"
        return try {
            require(invite.id.isNotBlank()) { "Invite ID is required" }
            db.collection("role_invites").document(invite.id).update(
                mapOf(
                    "status" to InviteStatus.REVOKED.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLogSafe(
                orgId = invite.orgId,
                actorUid = uid,
                actorName = actorName,
                action = "REVOKE_INVITE",
                entityType = "RoleInvite",
                entityId = invite.id,
                details = "Revoked ${invite.role} invite for ${invite.inviteeEmail}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, invitePath)
            Result.failure(e)
        }
    }

    suspend fun updateUserRolesByAdmin(
        targetUser: UserProfile,
        newRoles: List<UserRole>,
        actorName: String
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val path = "users/${targetUser.userId}"
        return try {
            if (!isBootstrappedAdminEmail()) {
                return Result.failure(SecurityException("Only a verified Administrator can modify user roles."))
            }
            val safeRoleNames = newRoles
                .filter { it != UserRole.ADMIN || targetUser.email.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true) }
                .map { it.name }
                .distinct()
            require(safeRoleNames.isNotEmpty()) { "User must retain at least one role" }

            val nextActive = if (targetUser.activeRole in safeRoleNames) {
                targetUser.activeRole
            } else {
                safeRoleNames.first()
            }

            db.collection("users").document(targetUser.userId).update(
                mapOf(
                    "roles" to safeRoleNames,
                    "activeRole" to nextActive,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val granted = safeRoleNames.size >= targetUser.roles.size
            writeAuditLogSafe(
                orgId = "user_$uid",
                actorUid = uid,
                actorName = actorName,
                action = if (granted) "GRANT_ROLE" else "REVOKE_ROLE",
                entityType = "UserProfile",
                entityId = targetUser.userId,
                details = "Updated roles for ${targetUser.email} from ${targetUser.roles.joinToString()} to ${safeRoleNames.joinToString()}"
            )
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

    suspend fun setUserSuspended(targetUid: String, suspended: Boolean, actorName: String = "Administrator"): Result<Unit> {
        val path = "users/$targetUid"
        return try {
            val uid = requireUserId()
            db.collection("users").document(targetUid).update(
                mapOf("suspended" to suspended, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            writeAuditLogSafe(
                orgId = "user_$uid",
                actorUid = uid,
                actorName = actorName,
                action = if (suspended) "SUSPEND_USER" else "REINSTATE_USER",
                entityType = "UserProfile",
                entityId = targetUid,
                details = if (suspended) "Suspended user $targetUid" else "Reinstated user $targetUid"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setOrganizationSuspended(orgId: String, suspended: Boolean, actorName: String = "Administrator"): Result<Unit> {
        val path = "organizations/$orgId"
        return try {
            val uid = requireUserId()
            db.collection("organizations").document(orgId).update(
                mapOf("suspended" to suspended, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            writeAuditLogSafe(
                orgId = orgId,
                actorUid = uid,
                actorName = actorName,
                action = if (suspended) "SUSPEND_ORGANIZATION" else "REINSTATE_ORGANIZATION",
                entityType = "Organization",
                entityId = orgId,
                details = if (suspended) "Suspended organization $orgId" else "Reinstated organization $orgId"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setOrganizationVerified(
        orgId: String,
        verified: Boolean,
        actorName: String = "Administrator"
    ): Result<Unit> {
        val cleanOrgId = orgId.trim()
        val path = "organizations/$cleanOrgId"
        return try {
            val uid = requireUserId()
            require(cleanOrgId.isNotBlank()) { "Organization ID is required" }

            val updates = mutableMapOf<String, Any>(
                "verified" to verified,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (verified) {
                updates["verifiedAtEpochMs"] = System.currentTimeMillis()
                updates["verifiedByUid"] = uid
            } else {
                updates["verifiedAtEpochMs"] = FieldValue.delete()
                updates["verifiedByUid"] = FieldValue.delete()
            }
            db.collection("organizations").document(cleanOrgId).update(updates).await()

            // Propagate verified badge to all properties and units owned by this org
            val propsSnap = db.collection("properties").whereEqualTo("orgId", cleanOrgId).get().await()
            val unitsSnap = db.collection("units").whereEqualTo("orgId", cleanOrgId).get().await()
            val allRefs = propsSnap.documents.map { it.reference } + unitsSnap.documents.map { it.reference }
            allRefs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { ref ->
                    batch.update(
                        ref,
                        mapOf(
                            "landlordVerified" to verified,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }

            writeAuditLogSafe(
                orgId = cleanOrgId,
                actorUid = uid,
                actorName = actorName,
                action = if (verified) "VERIFY_ORGANIZATION" else "REVOKE_ORG_VERIFICATION",
                entityType = "Organization",
                entityId = cleanOrgId,
                details = if (verified) {
                    "Verified landlord organization $cleanOrgId and synced ${allRefs.size} property/unit records"
                } else {
                    "Revoked verification for organization $cleanOrgId"
                }
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateOrganizationMpesa(
        orgId: String,
        paybill: String?,
        till: String?,
        actorName: String = "Owner"
    ): Result<Unit> {
        val cleanOrgId = orgId.trim()
        val path = "organizations/$cleanOrgId"
        return try {
            val uid = requireUserId()
            require(cleanOrgId.isNotBlank()) { "Organization ID is required" }
            val cleanPaybill = paybill?.trim()?.take(20)?.takeIf { it.isNotBlank() }
            val cleanTill = till?.trim()?.take(20)?.takeIf { it.isNotBlank() }
            db.collection("organizations").document(cleanOrgId).update(
                mapOf(
                    "mpesaPaybill" to (cleanPaybill ?: FieldValue.delete()),
                    "mpesaTill" to (cleanTill ?: FieldValue.delete()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLogSafe(
                orgId = cleanOrgId,
                actorUid = uid,
                actorName = actorName,
                action = "UPDATE_ORG_MPESA",
                entityType = "Organization",
                entityId = cleanOrgId,
                details = "Updated organization M-Pesa config (paybill=${cleanPaybill ?: "-"}, till=${cleanTill ?: "-"})"
            )
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
        return user?.email == BOOTSTRAPPED_ADMIN_EMAIL && user.isEmailVerified
    }

    companion object {
        private const val BOOTSTRAPPED_ADMIN_EMAIL = "sternomolo2@gmail.com"
    }
}
