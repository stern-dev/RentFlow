package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.domain.model.Property
import com.example.domain.model.PropertyCategory
import com.example.domain.model.RentalUnit
import com.example.domain.model.UnitStatus
import com.example.domain.model.UnitType
import com.example.domain.model.User
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

/**
 * Unified Firestore CRUD service for [User] profiles, [Property] records, and [RentalUnit] records.
 * Always resolves the named Firestore database configured in R.string.firestore_database_id
 * and enforces authenticated user scoping and structured error reporting.
 */
class FirestoreService(
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
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    // region User Profile CRUD

    suspend fun createUser(user: User): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val path = "users/$uid"
        return try {
            require(user.fullName.trim().length in 1..100) { "Full name must be 1 to 100 characters" }
            require(user.email.trim().length in 3..150) { "Valid email is required" }
            val safeRoles = user.roles.filter { it != UserRole.ADMIN.name }.ifEmpty { listOf(UserRole.TENANT.name) }
            val safeActiveRole = if (user.activeRole == UserRole.ADMIN.name) safeRoles.first() else user.activeRole

            val sanitized = user.copy(
                userId = uid,
                fullName = user.fullName.trim().take(100),
                email = user.email.trim().take(150),
                phoneNumber = user.phoneNumber?.trim()?.take(30),
                idNumber = user.idNumber?.trim()?.take(40),
                emergencyContactName = user.emergencyContactName?.trim()?.take(100),
                emergencyContactPhone = user.emergencyContactPhone?.trim()?.take(30),
                roles = safeRoles,
                activeRole = safeActiveRole,
                suspended = false
            )
            db.collection("users").document(uid).set(sanitized.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun getUser(userId: String? = null): Result<User?> {
        val targetUid = try {
            userId?.takeIf { it.isNotBlank() } ?: requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val path = "users/$targetUid"
        return try {
            val snapshot = db.collection("users").document(targetUid).get().await()
            val profile = if (snapshot.exists()) {
                snapshot.toObject(User::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    fun observeUser(userId: String): Flow<User?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(User::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun updateUser(
        fullName: String,
        phoneNumber: String? = null,
        idNumber: String? = null,
        emergencyContactName: String? = null,
        emergencyContactPhone: String? = null,
        userId: String? = null
    ): Result<Unit> {
        val targetUid = try {
            userId?.takeIf { it.isNotBlank() } ?: requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val path = "users/$targetUid"
        return try {
            require(fullName.trim().length in 1..100) { "Full name must be 1 to 100 characters" }
            val updates = mutableMapOf<String, Any>(
                "fullName" to fullName.trim().take(100),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!phoneNumber.isNullOrBlank()) updates["phoneNumber"] = phoneNumber.trim().take(30)
            if (!idNumber.isNullOrBlank()) updates["idNumber"] = idNumber.trim().take(40)
            if (!emergencyContactName.isNullOrBlank()) updates["emergencyContactName"] = emergencyContactName.trim().take(100)
            if (!emergencyContactPhone.isNullOrBlank()) updates["emergencyContactPhone"] = emergencyContactPhone.trim().take(30)

            db.collection("users").document(targetUid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteUser(userId: String? = null): Result<Unit> {
        val targetUid = try {
            userId?.takeIf { it.isNotBlank() } ?: requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val path = "users/$targetUid"
        return try {
            db.collection("users").document(targetUid).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // endregion

    // region Property CRUD

    suspend fun createProperty(property: Property): Result<String> {
        val docRef = db.collection("properties").document()
        return try {
            val uid = requireUserId()
            val cleanOrgId = property.orgId.trim()
            require(cleanOrgId.isNotBlank()) { "An existing organization ID is required before adding a property." }
            require(property.name.trim().length in 2..120) { "Property name must be 2 to 120 characters" }
            require(property.city.trim().length in 2..60) { "City is required" }
            require(property.county.trim().length <= 60) { "County must be 60 characters or fewer" }

            val authorized = (property.authorizedUids + uid + property.managerUids)
                .filter { it.isNotBlank() }
                .distinct()
                .take(20)

            val sanitized = property.copy(
                orgId = cleanOrgId,
                ownerUid = uid,
                managerUids = property.managerUids.distinct().take(15),
                authorizedUids = authorized,
                name = property.name.trim().take(120),
                city = property.city.trim().take(60),
                county = property.county.trim().take(60),
                neighborhood = property.neighborhood.trim().take(80).ifBlank { property.city.trim().take(60) },
                addressLine = property.addressLine.trim().take(160).ifBlank { property.city.trim().take(60) },
                description = property.description.trim().take(1000),
                waterMeterNumber = property.waterMeterNumber?.trim()?.take(60)?.takeIf { it.isNotBlank() },
                electricityMeterNumber = property.electricityMeterNumber?.trim()?.take(60)?.takeIf { it.isNotBlank() },
                notes = property.notes?.trim()?.take(1000)?.takeIf { it.isNotBlank() },
                imageRefs = property.imageRefs.take(6)
            )
            docRef.set(sanitized.toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getProperty(propertyId: String): Result<Property?> {
        val path = "properties/$propertyId"
        return try {
            require(propertyId.isNotBlank()) { "Property ID must not be blank" }
            val snapshot = db.collection("properties").document(propertyId).get().await()
            val property = if (snapshot.exists()) {
                snapshot.toObject(Property::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
            Result.success(property)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    fun observePropertiesForOrganization(orgId: String, userId: String): Flow<List<Property>> = flow {
        val cleanOrgId = orgId.trim()
        val cleanUid = userId.trim()
        require(cleanOrgId.isNotBlank()) { "Organization ID must not be blank" }
        require(cleanUid.isNotBlank()) { "User ID must not be blank" }
        val path = "properties"
        emitAll(
            db.collection(path)
                .whereEqualTo("orgId", cleanOrgId)
                .whereArrayContains("authorizedUids", cleanUid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Property::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    fun observePropertiesForUser(userId: String): Flow<List<Property>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "properties"
        emitAll(
            db.collection(path)
                .whereArrayContains("authorizedUids", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Property::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun updateProperty(
        propertyId: String,
        name: String,
        category: PropertyCategory,
        city: String,
        county: String = "",
        neighborhood: String,
        addressLine: String,
        description: String,
        waterMeterNumber: String? = null,
        electricityMeterNumber: String? = null,
        notes: String? = null,
        imageRefs: List<String> = emptyList(),
        archived: Boolean = false
    ): Result<Unit> {
        val path = "properties/$propertyId"
        return try {
            requireUserId()
            require(propertyId.isNotBlank()) { "Property ID must not be blank" }
            require(name.trim().length in 2..120) { "Property name must be 2 to 120 characters" }
            require(city.trim().length in 2..60) { "City is required" }
            require(county.trim().length <= 60) { "County must be 60 characters or fewer" }

            val updates = mutableMapOf<String, Any>(
                "name" to name.trim().take(120),
                "category" to category.name,
                "city" to city.trim().take(60),
                "neighborhood" to neighborhood.trim().take(80).ifBlank { city.trim().take(60) },
                "addressLine" to addressLine.trim().take(160).ifBlank { city.trim().take(60) },
                "description" to description.trim().take(1000),
                "imageRefs" to imageRefs.take(6),
                "archived" to archived,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            updates["county"] = county.trim().take(60).takeIf { it.isNotBlank() } ?: FieldValue.delete()
            updates["waterMeterNumber"] = waterMeterNumber?.trim()?.take(60)?.takeIf { it.isNotBlank() } ?: FieldValue.delete()
            updates["electricityMeterNumber"] = electricityMeterNumber?.trim()?.take(60)?.takeIf { it.isNotBlank() } ?: FieldValue.delete()
            updates["notes"] = notes?.trim()?.take(1000)?.takeIf { it.isNotBlank() } ?: FieldValue.delete()

            db.collection("properties").document(propertyId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteProperty(propertyId: String): Result<Unit> {
        val path = "properties/$propertyId"
        return try {
            requireUserId()
            require(propertyId.isNotBlank()) { "Property ID must not be blank" }
            db.collection("properties").document(propertyId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // endregion

    // region RentalUnit CRUD (Scoped to Parent Property)

    suspend fun createUnit(propertyId: String, unit: RentalUnit): Result<String> {
        val cleanPropertyId = propertyId.trim()
        val propPath = "properties/$cleanPropertyId"
        return try {
            requireUserId()
            require(cleanPropertyId.isNotBlank()) { "Parent property ID is required" }
            val propSnap = db.collection("properties").document(cleanPropertyId).get().await()
            val parentProperty = if (propSnap.exists()) {
                propSnap.toObject(Property::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?.let { if (it.id.isBlank()) it.copy(id = cleanPropertyId) else it }
            } else {
                null
            } ?: return Result.failure(IllegalArgumentException("Parent property '$cleanPropertyId' does not exist."))

            createUnit(parentProperty, unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, propPath)
            Result.failure(e)
        }
    }

    suspend fun createUnit(parentProperty: Property, unit: RentalUnit): Result<String> {
        val docRef = db.collection("units").document()
        return try {
            val uid = requireUserId()
            val cleanPropertyId = parentProperty.id.trim()
            require(cleanPropertyId.isNotBlank()) { "Parent property ID is required" }
            require(parentProperty.orgId.isNotBlank()) { "Parent property organization ID is required" }
            require(parentProperty.ownerUid.isNotBlank()) { "Parent property owner UID is required" }
            require(uid == parentProperty.ownerUid || uid in parentProperty.authorizedUids) {
                "Signed-in user is not authorized to add units to this property."
            }

            val cleanUnitNumber = unit.unitNumber.trim()
            require(cleanUnitNumber.length in 1..40) { "Unit number must be 1 to 40 characters" }
            require(unit.rentCents in 1L..1_000_000_000L) { "Monthly rent must be between 1 and 1,000,000,000 cents" }
            require(unit.depositCents in 0L..1_000_000_000L) { "Deposit must be non-negative" }

            val authorized = (parentProperty.authorizedUids + parentProperty.ownerUid + parentProperty.managerUids)
                .filter { it.isNotBlank() }
                .distinct()
                .take(20)
            val resolvedStatus = unit.statusEnum()
            val sanitized = unit.copy(
                propertyId = cleanPropertyId,
                propertyName = parentProperty.name.trim().take(120).ifBlank { "Property" },
                propertyCity = parentProperty.city.trim().take(60).ifBlank { "Nairobi" },
                propertyCategory = parentProperty.categoryEnum().name,
                orgId = parentProperty.orgId.trim(),
                ownerUid = parentProperty.ownerUid.trim(),
                managerUids = parentProperty.managerUids.distinct().take(15),
                authorizedUids = authorized,
                unitNumber = cleanUnitNumber.take(40),
                unitType = unit.unitTypeEnum().name,
                rentCents = unit.rentCents,
                depositCents = unit.depositCents,
                status = resolvedStatus.name,
                amenities = unit.amenities.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(15),
                description = unit.description.trim().take(1000),
                imageRefs = unit.imageRefs.take(6),
                published = unit.published && resolvedStatus == UnitStatus.AVAILABLE
            )
            docRef.set(sanitized.toCreateMap()).await()
            recomputePropertyUnitStats(cleanPropertyId, uid)
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getUnit(propertyId: String, unitId: String): Result<RentalUnit?> {
        val cleanPropertyId = propertyId.trim()
        val cleanUnitId = unitId.trim()
        val path = "units/$cleanUnitId"
        return try {
            requireUserId()
            require(cleanPropertyId.isNotBlank()) { "Parent property ID is required" }
            require(cleanUnitId.isNotBlank()) { "Unit ID is required" }
            val snapshot = db.collection("units").document(cleanUnitId).get().await()
            val unit = if (snapshot.exists()) {
                snapshot.toObject(RentalUnit::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
            if (unit != null && unit.propertyId != cleanPropertyId) {
                return Result.failure(SecurityException("Unit does not belong to property '$cleanPropertyId'."))
            }
            Result.success(unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun getUnitsForProperty(propertyId: String): Result<List<RentalUnit>> {
        val cleanPropertyId = propertyId.trim()
        val path = "units"
        return try {
            val uid = requireUserId()
            require(cleanPropertyId.isNotBlank()) { "Parent property ID is required" }
            val snapshot = db.collection(path)
                .whereEqualTo("propertyId", cleanPropertyId)
                .whereArrayContains("authorizedUids", uid)
                .get()
                .await()
            val units = snapshot.toObjects(RentalUnit::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            Result.success(units)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    fun observeUnitsForProperty(
        propertyId: String,
        userId: String? = auth.currentUser?.uid
    ): Flow<List<RentalUnit>> {
        val cleanPropertyId = propertyId.trim()
        val cleanUid = userId?.trim().orEmpty()
        if (cleanPropertyId.isBlank() || cleanUid.isBlank()) return flowOf(emptyList())
        val path = "units"
        return db.collection(path)
            .whereEqualTo("propertyId", cleanPropertyId)
            .whereArrayContains("authorizedUids", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(RentalUnit::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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
    }

    suspend fun updateUnit(propertyId: String, unit: RentalUnit): Result<Unit> {
        val cleanPropertyId = propertyId.trim()
        val cleanUnitId = unit.id.trim()
        if (cleanPropertyId.isBlank()) {
            return Result.failure(IllegalArgumentException("Parent property ID is required"))
        }
        if (cleanUnitId.isBlank()) {
            return Result.failure(IllegalArgumentException("Unit ID is required"))
        }
        if (unit.propertyId.isNotBlank() && unit.propertyId.trim() != cleanPropertyId) {
            return Result.failure(SecurityException("Unit does not belong to property '$cleanPropertyId'."))
        }
        return updateUnit(
            propertyId = cleanPropertyId,
            unitId = cleanUnitId,
            unitNumber = unit.unitNumber,
            unitType = unit.unitTypeEnum(),
            rentCents = unit.rentCents,
            depositCents = unit.depositCents,
            status = unit.statusEnum(),
            amenities = unit.amenities,
            description = unit.description,
            imageRefs = unit.imageRefs,
            published = unit.published
        )
    }

    suspend fun updateUnit(
        propertyId: String,
        unitId: String,
        unitNumber: String,
        unitType: UnitType,
        rentCents: Long,
        depositCents: Long,
        status: UnitStatus,
        amenities: List<String>,
        description: String = "",
        imageRefs: List<String> = emptyList(),
        published: Boolean = false
    ): Result<Unit> {
        val cleanPropertyId = propertyId.trim()
        val cleanUnitId = unitId.trim()
        val path = "units/$cleanUnitId"
        return try {
            val uid = requireUserId()
            require(cleanPropertyId.isNotBlank()) { "Parent property ID is required" }
            require(cleanUnitId.isNotBlank()) { "Unit ID is required" }
            val cleanUnitNumber = unitNumber.trim()
            require(cleanUnitNumber.length in 1..40) { "Unit number must be 1 to 40 characters" }
            require(rentCents in 1L..1_000_000_000L) { "Monthly rent must be positive" }
            require(depositCents in 0L..1_000_000_000L) { "Deposit must be non-negative" }

            val existingSnap = db.collection("units").document(cleanUnitId).get().await()
            if (!existingSnap.exists()) {
                return Result.failure(NoSuchElementException("Rental unit '$cleanUnitId' not found."))
            }
            if (existingSnap.getString("propertyId") != cleanPropertyId) {
                return Result.failure(SecurityException("Unit does not belong to property '$cleanPropertyId'."))
            }

            db.collection("units").document(cleanUnitId).update(
                mapOf(
                    "unitNumber" to cleanUnitNumber.take(40),
                    "unitType" to unitType.name,
                    "rentCents" to rentCents,
                    "depositCents" to depositCents,
                    "status" to status.name,
                    "amenities" to amenities.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(15),
                    "description" to description.trim().take(1000),
                    "imageRefs" to imageRefs.take(6),
                    "published" to (published && status == UnitStatus.AVAILABLE),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recomputePropertyUnitStats(cleanPropertyId, uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteUnit(propertyId: String, unit: RentalUnit): Result<Unit> {
        val cleanPropertyId = propertyId.trim()
        val cleanUnitId = unit.id.trim()
        if (cleanPropertyId.isBlank()) {
            return Result.failure(IllegalArgumentException("Parent property ID is required"))
        }
        if (cleanUnitId.isBlank()) {
            return Result.failure(IllegalArgumentException("Unit ID is required"))
        }
        if (unit.propertyId.isNotBlank() && unit.propertyId.trim() != cleanPropertyId) {
            return Result.failure(SecurityException("Unit does not belong to property '$cleanPropertyId'."))
        }
        return deleteUnit(cleanPropertyId, cleanUnitId)
    }

    suspend fun deleteUnit(propertyId: String, unitId: String): Result<Unit> {
        val cleanPropertyId = propertyId.trim()
        val cleanUnitId = unitId.trim()
        val path = "units/$cleanUnitId"
        return try {
            val uid = requireUserId()
            require(cleanPropertyId.isNotBlank()) { "Parent property ID is required" }
            require(cleanUnitId.isNotBlank()) { "Unit ID is required" }

            val existingSnap = db.collection("units").document(cleanUnitId).get().await()
            if (!existingSnap.exists()) {
                return Result.failure(NoSuchElementException("Rental unit '$cleanUnitId' not found."))
            }
            val existingUnit = existingSnap.toObject(RentalUnit::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(NoSuchElementException("Rental unit '$cleanUnitId' not found."))
            if (existingUnit.propertyId != cleanPropertyId) {
                return Result.failure(SecurityException("Unit does not belong to property '$cleanPropertyId'."))
            }
            if (existingUnit.statusEnum() == UnitStatus.OCCUPIED || !existingUnit.currentLeaseId.isNullOrBlank()) {
                return Result.failure(IllegalStateException("Cannot delete an occupied unit or a unit linked to an active lease."))
            }

            db.collection("units").document(cleanUnitId).delete().await()
            recomputePropertyUnitStats(cleanPropertyId, uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private suspend fun recomputePropertyUnitStats(propertyId: String, uid: String) {
        val unitsSnap = db.collection("units")
            .whereEqualTo("propertyId", propertyId)
            .whereArrayContains("authorizedUids", uid)
            .get()
            .await()
        val units = unitsSnap.toObjects(RentalUnit::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        val total = units.size.toLong()
        val occupied = units.count { it.statusEnum() == UnitStatus.OCCUPIED }.toLong()
        val hasPub = units.any { it.published && it.statusEnum() == UnitStatus.AVAILABLE }
        db.collection("properties").document(propertyId).update(
            mapOf(
                "totalUnitsCount" to total,
                "occupiedUnitsCount" to occupied,
                "hasPublishedUnits" to hasPub,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    // endregion
}
