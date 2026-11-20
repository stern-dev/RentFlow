package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.guardFirestoreReads
import com.example.data.firebase.handleFirestoreError
import com.example.domain.model.AppNotification
import com.example.domain.model.ApplicationStatus
import com.example.domain.model.AuditLog
import com.example.domain.model.IssueStatus
import com.example.domain.model.Lease
import com.example.domain.model.LeaseStatus
import com.example.domain.model.NotificationType
import com.example.domain.model.Organization
import com.example.domain.model.Property
import com.example.domain.model.PropertyCategory
import com.example.domain.model.RentalApplication
import com.example.domain.model.RentalUnit
import com.example.domain.model.ReportedIssue
import com.example.domain.model.SavedUnit
import com.example.domain.model.UnitStatus
import com.example.domain.model.UnitType
import com.example.domain.validation.PropertyUnitSchemas
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PropertyRepository(
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
            ?: throw IllegalStateException("User must be signed in with Google before performing this action.")

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

    fun observePublishedProperties(): Flow<List<Property>> =
        db.collection("properties")
            .whereEqualTo("hasPublishedUnits", true)
            .whereEqualTo("archived", false)
            .snapshots()
            .map { it.toObjects(Property::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .guardFirestoreReads(OperationType.LIST, "properties")

    fun observePublishedUnits(): Flow<List<RentalUnit>> =
        db.collection("units")
            .whereEqualTo("published", true)
            .snapshots()
            .map { it.toObjects(RentalUnit::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .guardFirestoreReads(OperationType.LIST, "units")

    fun observeSavedUnits(userId: String): Flow<List<SavedUnit>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("users").document(cleanUid).collection("saved_units")
            .snapshots()
            .map { snap ->
                snap.toObjects(SavedUnit::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.savedAt?.seconds ?: 0L }
            }
            .guardFirestoreReads(OperationType.LIST, "users/$cleanUid/saved_units")
    }

    fun observeUnit(unitId: String): Flow<RentalUnit?> {
        val cleanId = unitId.trim()
        if (cleanId.isBlank()) return flowOf(null)
        return db.collection("units").document(cleanId)
            .snapshots()
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(RentalUnit::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .guardFirestoreReads(OperationType.GET, "units/$cleanId")
    }

    suspend fun toggleSavedUnit(userId: String, unit: RentalUnit, currentlySaved: Boolean): Result<Boolean> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank() || unit.id.isBlank()) {
            return Result.failure(IllegalArgumentException("Valid user and unit required."))
        }
        val docRef = db.collection("users").document(cleanUid).collection("saved_units").document(unit.id)
        return try {
            requireUserId()
            if (currentlySaved) {
                docRef.delete().await()
                Result.success(false)
            } else {
                val saved = SavedUnit(
                    unitId = unit.id,
                    propertyId = unit.propertyId,
                    propertyName = unit.propertyName,
                    unitNumber = unit.unitNumber,
                    rentCents = unit.rentCents.coerceAtLeast(1L)
                )
                docRef.set(saved.toCreateMap()).await()
                Result.success(true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (currentlySaved) OperationType.DELETE else OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun submitListingScamReport(
        unit: RentalUnit,
        reason: String,
        details: String,
        reporterName: String
    ): Result<String> {
        val docRef = db.collection("reported_issues").document()
        return try {
            val uid = requireUserId()
            val cleanReason = reason.trim().ifBlank { "Suspicious Listing" }
            val cleanDetails = details.trim()
            require(cleanDetails.length >= 5) { "Please describe what seemed suspicious (at least 5 characters)." }

            val title = "Scam Report: ${unit.propertyName} • Unit ${unit.unitNumber}".take(140)
            val body = "Reason: $cleanReason | Unit ID: ${unit.id} | Property: ${unit.propertyName} (${unit.propertyCity}) | Details: $cleanDetails"
                .take(1000)

            val issue = ReportedIssue(
                reporterUid = uid,
                reporterName = reporterName.trim().ifBlank { "RentFlow User" }.take(100),
                orgId = unit.orgId.ifBlank { "listing_report" }.take(128),
                category = "LISTING_SCAM",
                title = title,
                description = body,
                status = IssueStatus.OPEN.name
            )
            docRef.set(issue.toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observePropertiesForOrganization(orgId: String, userId: String): Flow<List<Property>> {
        val cleanOrgId = orgId.trim()
        val cleanUid = userId.trim()
        if (cleanOrgId.isBlank() || cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("properties")
            .whereEqualTo("orgId", cleanOrgId)
            .whereArrayContains("authorizedUids", cleanUid)
            .snapshots()
            .map { it.toObjects(Property::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .guardFirestoreReads(OperationType.LIST, "properties")
    }

    fun observeUnitsForOrganization(orgId: String, userId: String): Flow<List<RentalUnit>> {
        val cleanOrgId = orgId.trim()
        val cleanUid = userId.trim()
        if (cleanOrgId.isBlank() || cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("units")
            .whereEqualTo("orgId", cleanOrgId)
            .whereArrayContains("authorizedUids", cleanUid)
            .snapshots()
            .map { it.toObjects(RentalUnit::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .guardFirestoreReads(OperationType.LIST, "units")
    }

    fun observeMyProperties(userId: String): Flow<List<Property>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("properties")
            .whereArrayContains("authorizedUids", cleanUid)
            .snapshots()
            .map { it.toObjects(Property::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .guardFirestoreReads(OperationType.LIST, "properties")
    }

    fun observeMyUnits(userId: String): Flow<List<RentalUnit>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("units")
            .whereArrayContains("authorizedUids", cleanUid)
            .snapshots()
            .map { it.toObjects(RentalUnit::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .guardFirestoreReads(OperationType.LIST, "units")
    }

    fun observeApplicationsForLandlord(userId: String): Flow<List<RentalApplication>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("applications")
            .whereArrayContains("authorizedUids", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(RentalApplication::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .guardFirestoreReads(OperationType.LIST, "applications")
    }

    fun observeMyApplications(userId: String): Flow<List<RentalApplication>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("applications")
            .whereEqualTo("applicantUid", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(RentalApplication::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .guardFirestoreReads(OperationType.LIST, "applications")
    }

    fun observeLeasesForLandlord(userId: String): Flow<List<Lease>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("leases")
            .whereArrayContains("authorizedUids", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(Lease::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .guardFirestoreReads(OperationType.LIST, "leases")
    }

    fun observeMyLeases(userId: String): Flow<List<Lease>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("leases")
            .whereEqualTo("tenantUid", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(Lease::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .guardFirestoreReads(OperationType.LIST, "leases")
    }

    suspend fun getProperty(propertyId: String, expectedOrgId: String? = null): Result<Property?> {
        val cleanId = propertyId.trim()
        val path = "properties/$cleanId"
        return try {
            requireUserId()
            require(cleanId.isNotBlank()) { "Property ID is required" }
            val snapshot = db.collection("properties").document(cleanId).get().await()
            val property = if (snapshot.exists()) {
                snapshot.toObject(Property::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
            val cleanExpectedOrg = expectedOrgId?.trim()?.takeIf { it.isNotBlank() }
            if (property != null && cleanExpectedOrg != null && property.orgId != cleanExpectedOrg) {
                return Result.failure(SecurityException("Property does not belong to the active organization."))
            }
            Result.success(property)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun createProperty(
        orgId: String,
        name: String,
        category: PropertyCategory,
        city: String,
        county: String = "",
        neighborhood: String = "",
        addressLine: String = "",
        description: String = "",
        waterMeterNumber: String? = null,
        electricityMeterNumber: String? = null,
        notes: String? = null,
        imageRefs: List<String> = emptyList(),
        mpesaPaybill: String? = null,
        mpesaTill: String? = null,
        mpesaAccountPrefix: String? = null,
        actorName: String = "Owner"
    ): Result<String> {
        val docRef = db.collection("properties").document()
        return try {
            val uid = requireUserId()
            val orgSnap = db.collection("organizations").document(orgId.trim()).get().await()
            val org = orgSnap.toObject(Organization::class.java)
            val orgVerified = org?.verified == true

            val candidate = Property(
                orgId = orgId,
                ownerUid = uid,
                managerUids = emptyList(),
                authorizedUids = listOf(uid),
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
                archived = false,
                hasPublishedUnits = false,
                totalUnitsCount = 0L,
                occupiedUnitsCount = 0L,
                landlordVerified = orgVerified,
                mpesaPaybill = mpesaPaybill?.trim()?.take(20)?.takeIf { it.isNotBlank() } ?: org?.mpesaPaybill,
                mpesaTill = mpesaTill?.trim()?.take(20)?.takeIf { it.isNotBlank() } ?: org?.mpesaTill,
                mpesaAccountPrefix = mpesaAccountPrefix?.trim()?.take(24)?.takeIf { it.isNotBlank() }
            )
            val validated = PropertyUnitSchemas.propertyCreateSchema.parseOrThrow(candidate)
            docRef.set(validated.toCreateMap()).await()
            writeAuditLogSafe(
                orgId = validated.orgId,
                actorUid = uid,
                actorName = actorName,
                action = "CREATE_PROPERTY",
                entityType = "Property",
                entityId = docRef.id,
                details = "Registered property '${validated.name}' in ${validated.city}"
            )
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
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
        imageRefs: List<String>,
        archived: Boolean,
        mpesaPaybill: String? = null,
        mpesaTill: String? = null,
        mpesaAccountPrefix: String? = null,
        actorName: String = "Owner"
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
            if (!existingSnap.exists()) {
                return Result.failure(NoSuchElementException("Property '${validated.propertyId}' not found."))
            }
            val ownerUid = existingSnap.getString("ownerUid").orEmpty()
            val orgId = existingSnap.getString("orgId").orEmpty()
            if (ownerUid != uid) {
                return Result.failure(SecurityException("Only the property owner can edit structural property details."))
            }

            val cleanPaybill = mpesaPaybill?.trim()?.take(20)?.takeIf { it.isNotBlank() }
            val cleanTill = mpesaTill?.trim()?.take(20)?.takeIf { it.isNotBlank() }
            val cleanPrefix = mpesaAccountPrefix?.trim()?.take(24)?.takeIf { it.isNotBlank() }

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
            updates["mpesaPaybill"] = cleanPaybill ?: FieldValue.delete()
            updates["mpesaTill"] = cleanTill ?: FieldValue.delete()
            updates["mpesaAccountPrefix"] = cleanPrefix ?: FieldValue.delete()

            db.collection("properties").document(validated.propertyId).update(updates).await()

            // Sync M-Pesa payment metadata down to units in this property
            val childUnits = db.collection("units")
                .whereEqualTo("propertyId", validated.propertyId)
                .whereArrayContains("authorizedUids", uid)
                .get()
                .await()
            if (!childUnits.isEmpty) {
                val unitUpdates = mapOf<String, Any>(
                    "propertyName" to validated.name,
                    "propertyCity" to validated.city,
                    "propertyCategory" to validated.category,
                    "mpesaPaybill" to (cleanPaybill ?: FieldValue.delete()),
                    "mpesaTill" to (cleanTill ?: FieldValue.delete()),
                    "mpesaAccountPrefix" to (cleanPrefix ?: FieldValue.delete()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val batch = db.batch()
                childUnits.documents.forEach { batch.update(it.reference, unitUpdates) }
                batch.commit().await()
            }

            writeAuditLogSafe(
                orgId = orgId,
                actorUid = uid,
                actorName = actorName,
                action = "UPDATE_PROPERTY",
                entityType = "Property",
                entityId = validated.propertyId,
                details = "Updated property '${validated.name}' (archived=${validated.archived})"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteProperty(propertyId: String, actorName: String = "Owner"): Result<Unit> {
        val cleanId = propertyId.trim()
        val path = "properties/$cleanId"
        return try {
            val uid = requireUserId()
            require(cleanId.isNotBlank()) { "Property ID is required" }
            val propSnap = db.collection("properties").document(cleanId).get().await()
            if (!propSnap.exists()) {
                return Result.failure(NoSuchElementException("Property '$cleanId' not found."))
            }
            val ownerUid = propSnap.getString("ownerUid").orEmpty()
            val orgId = propSnap.getString("orgId").orEmpty()
            val propName = propSnap.getString("name").orEmpty()
            if (ownerUid != uid) {
                return Result.failure(SecurityException("Only the property owner can delete this property."))
            }

            val existingUnits = db.collection("units")
                .whereEqualTo("propertyId", cleanId)
                .whereArrayContains("authorizedUids", uid)
                .limit(1)
                .get()
                .await()
            if (!existingUnits.isEmpty) {
                return Result.failure(IllegalStateException("Cannot delete a property that still has rental units."))
            }
            db.collection("properties").document(cleanId).delete().await()
            writeAuditLogSafe(
                orgId = orgId,
                actorUid = uid,
                actorName = actorName,
                action = "DELETE_PROPERTY",
                entityType = "Property",
                entityId = cleanId,
                details = "Deleted property '$propName'"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun assignManagerToProperty(
        property: Property,
        managerUid: String,
        actorName: String = "Owner"
    ): Result<Unit> {
        val cleanManager = managerUid.trim()
        val path = "properties/${property.id}"
        return try {
            val uid = requireUserId()
            if (property.ownerUid != uid) {
                return Result.failure(SecurityException("Only the property owner can assign property managers."))
            }
            require(cleanManager.isNotBlank()) { "Manager UID is required" }
            val newManagers = (property.managerUids + cleanManager).distinct().take(15)
            val newAuth = (listOf(property.ownerUid) + newManagers).distinct().take(20)
            db.collection("properties").document(property.id).update(
                mapOf(
                    "managerUids" to newManagers,
                    "authorizedUids" to newAuth,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            // Sync org memberUids so manager has org context
            db.collection("organizations").document(property.orgId).update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(cleanManager),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            // Propagate authorizedUids/managerUids to existing units and leases under this property
            val unitDocs = db.collection("units")
                .whereEqualTo("propertyId", property.id)
                .whereArrayContains("authorizedUids", uid)
                .get()
                .await()
            for (uDoc in unitDocs.documents) {
                uDoc.reference.update(
                    mapOf(
                        "managerUids" to newManagers,
                        "authorizedUids" to newAuth,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            val leaseDocs = db.collection("leases")
                .whereEqualTo("propertyId", property.id)
                .whereArrayContains("authorizedUids", uid)
                .get()
                .await()
            for (lDoc in leaseDocs.documents) {
                lDoc.reference.update(
                    mapOf(
                        "managerUids" to newManagers,
                        "authorizedUids" to newAuth,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            writeAuditLogSafe(
                orgId = property.orgId,
                actorUid = uid,
                actorName = actorName,
                action = "ASSIGN_MANAGER",
                entityType = "Property",
                entityId = property.id,
                details = "Assigned manager $cleanManager to '${property.name}'"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun createUnit(
        property: Property,
        unitNumber: String,
        unitType: UnitType,
        rentCents: Long,
        depositCents: Long,
        status: UnitStatus,
        amenities: List<String>,
        description: String,
        imageRefs: List<String>,
        published: Boolean,
        actorName: String = "Management"
    ): Result<String> {
        val docRef = db.collection("units").document()
        return try {
            val uid = requireUserId()
            val candidate = RentalUnit(
                propertyId = property.id,
                propertyName = property.name,
                propertyCity = property.city,
                propertyCategory = property.category,
                orgId = property.orgId,
                ownerUid = property.ownerUid,
                managerUids = property.managerUids,
                authorizedUids = property.authorizedUids,
                unitNumber = unitNumber,
                unitType = unitType.name,
                rentCents = rentCents,
                depositCents = depositCents,
                status = status.name,
                amenities = amenities,
                description = description,
                imageRefs = imageRefs,
                published = published,
                landlordVerified = property.landlordVerified,
                mpesaPaybill = property.mpesaPaybill,
                mpesaTill = property.mpesaTill,
                mpesaAccountPrefix = property.mpesaAccountPrefix
            )
            val validated = PropertyUnitSchemas.unitCreateSchema.parseOrThrow(candidate)

            val existingUnitsSnap = db.collection("units")
                .whereEqualTo("propertyId", validated.propertyId)
                .whereArrayContains("authorizedUids", uid)
                .get()
                .await()
            val duplicateExists = existingUnitsSnap.toObjects(RentalUnit::class.java)
                .any { it.unitNumber.trim().equals(validated.unitNumber, ignoreCase = true) }
            if (duplicateExists) {
                return Result.failure(IllegalArgumentException("Unit ${validated.unitNumber} already exists in ${property.name}."))
            }

            docRef.set(validated.toCreateMap()).await()
            recomputePropertyUnitStats(validated.propertyId)
            writeAuditLogSafe(
                orgId = validated.orgId,
                actorUid = uid,
                actorName = actorName,
                action = "CREATE_UNIT",
                entityType = "RentalUnit",
                entityId = docRef.id,
                details = "Created unit ${validated.unitNumber} in '${validated.propertyName}' (rent=${validated.rentCents})"
            )
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateUnitStatusAndPublish(
        unit: RentalUnit,
        status: UnitStatus,
        published: Boolean,
        rentCents: Long = unit.rentCents,
        actorName: String = "Management"
    ): Result<Unit> {
        val path = "units/${unit.id}"
        return try {
            val uid = requireUserId()
            require(rentCents in 1L..PropertyUnitSchemas.MAX_MONEY_CENTS) {
                "Monthly rent must be greater than zero"
            }
            if (rentCents != unit.rentCents && unit.ownerUid != uid) {
                return Result.failure(SecurityException("Only the property owner can modify unit rent."))
            }
            val updates = mutableMapOf<String, Any>(
                "status" to status.name,
                "published" to (published && status == UnitStatus.AVAILABLE),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (rentCents != unit.rentCents) {
                updates["rentCents"] = rentCents
            }
            db.collection("units").document(unit.id).update(updates).await()
            recomputePropertyUnitStats(unit.propertyId)
            writeAuditLogSafe(
                orgId = unit.orgId,
                actorUid = uid,
                actorName = actorName,
                action = "UPDATE_UNIT",
                entityType = "RentalUnit",
                entityId = unit.id,
                details = "Updated unit ${unit.unitNumber} status=${status.name}, published=${published && status == UnitStatus.AVAILABLE}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeUnitsForProperty(propertyId: String, userId: String): Flow<List<RentalUnit>> {
        val cleanPropertyId = propertyId.trim()
        val cleanUid = userId.trim()
        if (cleanPropertyId.isBlank() || cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("units")
            .whereEqualTo("propertyId", cleanPropertyId)
            .whereArrayContains("authorizedUids", cleanUid)
            .snapshots()
            .map { it.toObjects(RentalUnit::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .guardFirestoreReads(OperationType.LIST, "units")
    }

    suspend fun updateUnit(
        unit: RentalUnit,
        unitNumber: String,
        unitType: UnitType,
        rentCents: Long,
        depositCents: Long,
        status: UnitStatus,
        amenities: List<String>,
        description: String,
        imageRefs: List<String>,
        published: Boolean,
        actorName: String = "Management"
    ): Result<Unit> {
        val path = "units/${unit.id}"
        return try {
            val uid = requireUserId()
            val validated = PropertyUnitSchemas.unitUpdateSchema.parseOrThrow(
                PropertyUnitSchemas.RentalUnitUpdateInput(
                    propertyId = unit.propertyId,
                    unitId = unit.id,
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
            if (unit.ownerUid != uid && (validated.rentCents != unit.rentCents || validated.depositCents != unit.depositCents)) {
                return Result.failure(SecurityException("Only the property owner can modify unit rent or deposit."))
            }

            val siblingUnits = db.collection("units")
                .whereEqualTo("propertyId", validated.propertyId)
                .whereArrayContains("authorizedUids", uid)
                .get()
                .await()
            val duplicateExists = siblingUnits.documents.any { doc ->
                doc.id != validated.unitId &&
                    (doc.getString("unitNumber")?.trim()?.equals(validated.unitNumber, ignoreCase = true) == true)
            }
            if (duplicateExists) {
                return Result.failure(IllegalArgumentException("Another unit with number ${validated.unitNumber} already exists in ${unit.propertyName}."))
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
            if (unit.ownerUid == uid) {
                updates["rentCents"] = validated.rentCents
                updates["depositCents"] = validated.depositCents
            }

            db.collection("units").document(validated.unitId).update(updates).await()
            recomputePropertyUnitStats(validated.propertyId)
            writeAuditLogSafe(
                orgId = unit.orgId,
                actorUid = uid,
                actorName = actorName,
                action = "UPDATE_UNIT",
                entityType = "RentalUnit",
                entityId = validated.unitId,
                details = "Updated unit ${validated.unitNumber} in '${unit.propertyName}'"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteUnit(unit: RentalUnit, actorName: String = "Owner"): Result<Unit> {
        val path = "units/${unit.id}"
        return try {
            val uid = requireUserId()
            require(unit.id.isNotBlank()) { "Unit ID is required" }
            require(unit.propertyId.isNotBlank()) { "Parent property ID is required" }
            if (unit.ownerUid != uid) {
                return Result.failure(SecurityException("Only the property owner can delete rental units."))
            }
            if (unit.statusEnum() == UnitStatus.OCCUPIED || !unit.currentLeaseId.isNullOrBlank()) {
                return Result.failure(IllegalStateException("Cannot delete an occupied unit with an active lease."))
            }
            db.collection("units").document(unit.id).delete().await()
            recomputePropertyUnitStats(unit.propertyId)
            writeAuditLogSafe(
                orgId = unit.orgId,
                actorUid = uid,
                actorName = actorName,
                action = "DELETE_UNIT",
                entityType = "RentalUnit",
                entityId = unit.id,
                details = "Deleted unit ${unit.unitNumber} from '${unit.propertyName}'"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private suspend fun recomputePropertyUnitStats(propertyId: String) {
        val uid = requireUserId()
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

    suspend fun submitApplication(
        unit: RentalUnit,
        applicantName: String,
        applicantPhone: String,
        applicantEmail: String?,
        occupation: String?,
        desiredMoveInDate: String,
        notes: String?
    ): Result<String> {
        val docRef = db.collection("applications").document()
        return try {
            val uid = requireUserId()
            require(applicantName.isNotBlank()) { "Applicant name is required" }
            require(applicantPhone.length >= 7) { "Valid phone number is required" }
            val app = RentalApplication(
                unitId = unit.id,
                unitNumber = unit.unitNumber,
                propertyId = unit.propertyId,
                propertyName = unit.propertyName,
                orgId = unit.orgId,
                ownerUid = unit.ownerUid,
                managerUids = unit.managerUids,
                authorizedUids = unit.authorizedUids,
                applicantUid = uid,
                applicantName = applicantName.trim().take(100),
                applicantPhone = applicantPhone.trim().take(30),
                applicantEmail = applicantEmail?.trim()?.take(150),
                occupation = occupation?.trim()?.take(100),
                desiredMoveInDate = desiredMoveInDate.trim().take(40),
                notes = notes?.trim()?.take(800),
                status = ApplicationStatus.SUBMITTED.name
            )
            docRef.set(app.toCreateMap()).await()

            val notif = AppNotification(
                recipientUid = unit.ownerUid,
                senderUid = uid,
                orgId = unit.orgId,
                type = NotificationType.APPLICATION.name,
                title = "New Rental Application",
                body = "${applicantName.trim()} applied for ${unit.propertyName} • Unit ${unit.unitNumber}",
                relatedEntityId = docRef.id,
                read = false
            )
            db.collection("notifications").document().set(notif.toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun reviewApplication(
        application: RentalApplication,
        newStatus: ApplicationStatus,
        reviewerNotes: String?,
        reserveUnitOnApprove: Boolean = true
    ): Result<Unit> {
        val path = "applications/${application.id}"
        return try {
            val uid = requireUserId()
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!reviewerNotes.isNullOrBlank()) {
                updates["reviewerNotes"] = reviewerNotes.trim().take(800)
            }
            db.collection("applications").document(application.id).update(updates).await()

            if (newStatus == ApplicationStatus.APPROVED && reserveUnitOnApprove) {
                db.collection("units").document(application.unitId).update(
                    mapOf(
                        "status" to UnitStatus.RESERVED.name,
                        "published" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                recomputePropertyUnitStats(application.propertyId)
            }

            val notif = AppNotification(
                recipientUid = application.applicantUid,
                senderUid = uid,
                orgId = application.orgId,
                type = NotificationType.APPLICATION.name,
                title = "Application ${newStatus.label}",
                body = "Your application for ${application.propertyName} (${application.unitNumber}) is ${newStatus.label}.",
                relatedEntityId = application.id,
                read = false
            )
            db.collection("notifications").document().set(notif.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun withdrawApplication(applicationId: String): Result<Unit> {
        val path = "applications/$applicationId"
        return try {
            requireUserId()
            db.collection("applications").document(applicationId).update(
                mapOf(
                    "status" to ApplicationStatus.WITHDRAWN.name,
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

    suspend fun createLease(
        unit: RentalUnit,
        tenantUid: String,
        tenantName: String,
        tenantPhone: String,
        rentCents: Long,
        depositCents: Long,
        startDateEpochMs: Long,
        endDateEpochMs: Long,
        dueDayOfMonth: Long,
        termsSummary: String,
        actorName: String
    ): Result<String> {
        val leaseRef = db.collection("leases").document()
        return try {
            val uid = requireUserId()
            require(tenantUid.isNotBlank()) { "Tenant UID is required" }
            require(rentCents > 0L) { "Rent must be positive" }
            require(endDateEpochMs >= startDateEpochMs) { "Lease end date must be after start date" }

            // Guard against overlapping active leases on same unit
            val existingActive = db.collection("leases")
                .whereEqualTo("unitId", unit.id)
                .whereEqualTo("status", LeaseStatus.ACTIVE.name)
                .get()
                .await()
            if (!existingActive.isEmpty) {
                return Result.failure(IllegalStateException("Unit ${unit.unitNumber} already has an active lease."))
            }

            val lease = Lease(
                orgId = unit.orgId,
                propertyId = unit.propertyId,
                propertyName = unit.propertyName,
                unitId = unit.id,
                unitNumber = unit.unitNumber,
                ownerUid = unit.ownerUid,
                managerUids = unit.managerUids,
                authorizedUids = unit.authorizedUids,
                tenantUid = tenantUid.trim(),
                tenantName = tenantName.trim(),
                tenantPhone = tenantPhone.trim(),
                rentCents = rentCents,
                depositCents = depositCents.coerceAtLeast(0L),
                startDateEpochMs = startDateEpochMs,
                endDateEpochMs = endDateEpochMs,
                dueDayOfMonth = dueDayOfMonth.coerceIn(1L, 28L),
                status = LeaseStatus.ACTIVE.name,
                creditBalanceCents = 0L,
                termsSummary = termsSummary.trim().take(1500)
            )
            leaseRef.set(lease.toCreateMap()).await()

            db.collection("units").document(unit.id).update(
                mapOf(
                    "status" to UnitStatus.OCCUPIED.name,
                    "published" to false,
                    "currentTenantUid" to tenantUid.trim(),
                    "currentLeaseId" to leaseRef.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recomputePropertyUnitStats(unit.propertyId)

            val notif = AppNotification(
                recipientUid = tenantUid.trim(),
                senderUid = uid,
                orgId = unit.orgId,
                type = NotificationType.LEASE.name,
                title = "Tenancy Agreement Active",
                body = "Welcome to ${unit.propertyName}, Unit ${unit.unitNumber}. Your lease is now active.",
                relatedEntityId = leaseRef.id,
                read = false
            )
            db.collection("notifications").document().set(notif.toCreateMap()).await()

            val audit = AuditLog(
                orgId = unit.orgId,
                actorUid = uid,
                actorName = actorName,
                action = "CREATE_LEASE",
                entityType = "Lease",
                entityId = leaseRef.id,
                details = "Onboarded ${tenantName.trim()} to ${unit.propertyName} Unit ${unit.unitNumber}"
            )
            db.collection("audit_logs").document().set(audit.toCreateMap()).await()

            Result.success(leaseRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, leaseRef.path)
            Result.failure(e)
        }
    }

    suspend fun renewLease(
        lease: Lease,
        newEndDateEpochMs: Long,
        newRentCents: Long,
        actorName: String
    ): Result<Unit> {
        val path = "leases/${lease.id}"
        return try {
            val uid = requireUserId()
            if (newRentCents != lease.rentCents && lease.ownerUid != uid) {
                return Result.failure(SecurityException("Only the property owner can change lease rent during renewal."))
            }
            val updates = mutableMapOf<String, Any>(
                "endDateEpochMs" to newEndDateEpochMs,
                "status" to LeaseStatus.ACTIVE.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (lease.ownerUid == uid) {
                updates["rentCents"] = newRentCents
            }
            db.collection("leases").document(lease.id).update(updates).await()
            val notif = AppNotification(
                recipientUid = lease.tenantUid,
                senderUid = uid,
                orgId = lease.orgId,
                type = NotificationType.LEASE.name,
                title = "Lease Renewed",
                body = "Your lease for ${lease.propertyName} (${lease.unitNumber}) has been renewed.",
                relatedEntityId = lease.id,
                read = false
            )
            db.collection("notifications").document().set(notif.toCreateMap()).await()
            db.collection("audit_logs").document().set(
                AuditLog(
                    orgId = lease.orgId,
                    actorUid = uid,
                    actorName = actorName,
                    action = "RENEW_LEASE",
                    entityType = "Lease",
                    entityId = lease.id,
                    details = "Renewed lease for ${lease.tenantName}"
                ).toCreateMap()
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun terminateLease(
        lease: Lease,
        noticeReason: String,
        actorName: String
    ): Result<Unit> {
        val path = "leases/${lease.id}"
        return try {
            val uid = requireUserId()
            if (lease.ownerUid != uid) {
                return Result.failure(SecurityException("Only the property owner can terminate an active lease."))
            }
            db.collection("leases").document(lease.id).update(
                mapOf(
                    "status" to LeaseStatus.TERMINATED.name,
                    "terminationNotice" to noticeReason.trim().take(800),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("units").document(lease.unitId).update(
                mapOf(
                    "status" to UnitStatus.AVAILABLE.name,
                    "published" to false,
                    "currentTenantUid" to FieldValue.delete(),
                    "currentLeaseId" to FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recomputePropertyUnitStats(lease.propertyId)

            val notif = AppNotification(
                recipientUid = lease.tenantUid,
                senderUid = uid,
                orgId = lease.orgId,
                type = NotificationType.LEASE.name,
                title = "Lease Termination Notice",
                body = "Tenancy for ${lease.propertyName} (${lease.unitNumber}) marked terminated: ${noticeReason.take(120)}",
                relatedEntityId = lease.id,
                read = false
            )
            db.collection("notifications").document().set(notif.toCreateMap()).await()
            db.collection("audit_logs").document().set(
                AuditLog(
                    orgId = lease.orgId,
                    actorUid = uid,
                    actorName = actorName,
                    action = "TERMINATE_LEASE",
                    entityType = "Lease",
                    entityId = lease.id,
                    details = "Terminated lease for ${lease.tenantName}: $noticeReason"
                ).toCreateMap()
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
