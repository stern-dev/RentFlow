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
import com.example.domain.validation.PropertyUnitSchemas
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
            val authorized = (property.authorizedUids + uid + property.managerUids)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            val candidate = property.copy(
                ownerUid = uid,
                managerUids = property.managerUids.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
                authorizedUids = authorized
            )
            val validated = PropertyUnitSchemas.propertyCreateSchema.parseOrThrow(candidate)
            docRef.set(validated.toCreateMap()).await()
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
        val cleanId = propertyId.trim()
        val path = "properties/$cleanId"
        return try {
            val uid = requireUserId()
            val validated = PropertyUnitSchemas.propertyUpdateSchema.parseOrThrow(
                PropertyUnitSchemas.PropertyUpdateInput(
                    propertyId = cleanId,
                    name = name,
                    category = category.name,
                    city = city,
                    county = county,
                    neighborhood = neighborhood,
                    addressLine = addressLine,
                    description = description,
                    waterMeterNumber = waterMeterNumber,
                    electricityMeterNumber = electricityMeterNumber,
                    notes = notes,
                    imageRefs = imageRefs,
                    archived = archived
                )
            )
            val existingSnap = db.collection("properties").document(validated.propertyId).get().await()
            if (existingSnap.exists() && existingSnap.getString("ownerUid") != uid) {
                return Result.failure(SecurityException("Only the property owner can edit property details."))
            }

            val updates = mutableMapOf<String, Any>(
                "name" to validated.name,
                "category" to validated.category,
                "city" to validated.city,
                "neighborhood" to validated.neighborhood,
                "addressLine" to validated.addressLine,
                "description" to validated.description,
                "imageRefs" to validated.imageRefs,
                "archived" to validated.archived,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            updates["county"] = validated.county.takeIf { it.isNotBlank() } ?: FieldValue.delete()
            updates["waterMeterNumber"] = validated.waterMeterNumber ?: FieldValue.delete()
            updates["electricityMeterNumber"] = validated.electricityMeterNumber ?: FieldValue.delete()
            updates["notes"] = validated.notes ?: FieldValue.delete()

            db.collection("properties").document(validated.propertyId).update(updates).await()
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

            val authorized = (parentProperty.authorizedUids + parentProperty.ownerUid + parentProperty.managerUids)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            val candidate = unit.copy(
                propertyId = cleanPropertyId,
                propertyName = parentProperty.name.trim().ifBlank { "Property" },
                propertyCity = parentProperty.city.trim().ifBlank { "Nairobi" },
                propertyCategory = parentProperty.categoryEnum().name,
                orgId = parentProperty.orgId.trim(),
                ownerUid = parentProperty.ownerUid.trim(),
                managerUids = parentProperty.managerUids.distinct(),
                authorizedUids = authorized,
                unitType = unit.unitTypeEnum().name,
                status = unit.statusEnum().name
            )
            val validated = PropertyUnitSchemas.unitCreateSchema.parseOrThrow(candidate)
            docRef.set(validated.toCreateMap()).await()
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
            val validated = PropertyUnitSchemas.unitUpdateSchema.parseOrThrow(
                PropertyUnitSchemas.RentalUnitUpdateInput(
                    propertyId = cleanPropertyId,
                    unitId = cleanUnitId,
                    unitNumber = unitNumber,
                    unitType = unitType.name,
                    rentCents = rentCents,
                    depositCents = depositCents,
                    status = status.name,
                    amenities = amenities,
                    description = description,
                    imageRefs = imageRefs,
                    published = published
                )
            )

            val existingSnap = db.collection("units").document(validated.unitId).get().await()
            if (!existingSnap.exists()) {
                return Result.failure(NoSuchElementException("Rental unit '${validated.unitId}' not found."))
            }
            if (existingSnap.getString("propertyId") != validated.propertyId) {
                return Result.failure(SecurityException("Unit does not belong to property '${validated.propertyId}'."))
            }
            val ownerUid = existingSnap.getString("ownerUid").orEmpty()
            val existingRent = existingSnap.getLong("rentCents") ?: validated.rentCents
            val existingDeposit = existingSnap.getLong("depositCents") ?: validated.depositCents
            if (ownerUid != uid && (validated.rentCents != existingRent || validated.depositCents != existingDeposit)) {
                return Result.failure(SecurityException("Only the property owner can modify unit rent or deposit."))
            }

            val updates = mutableMapOf<String, Any>(
                "unitNumber" to validated.unitNumber,
                "unitType" to validated.unitType,
                "status" to validated.status,
                "amenities" to validated.amenities,
                "description" to validated.description,
                "imageRefs" to validated.imageRefs,
                "published" to validated.published,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (ownerUid == uid) {
                updates["rentCents"] = validated.rentCents
                updates["depositCents"] = validated.depositCents
            }

            db.collection("units").document(validated.unitId).update(updates).await()
            recomputePropertyUnitStats(validated.propertyId, uid)
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
            if (existingUnit.ownerUid != uid) {
                return Result.failure(SecurityException("Only the property owner can delete rental units."))
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
