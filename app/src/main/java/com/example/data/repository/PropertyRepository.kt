package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.guardFirestoreReads
import com.example.data.firebase.handleFirestoreError
import com.example.domain.model.AppNotification
import com.example.domain.model.ApplicationStatus
import com.example.domain.model.AuditLog
import com.example.domain.model.Lease
import com.example.domain.model.LeaseStatus
import com.example.domain.model.NotificationType
import com.example.domain.model.Property
import com.example.domain.model.PropertyCategory
import com.example.domain.model.RentalApplication
import com.example.domain.model.RentalUnit
import com.example.domain.model.UnitStatus
import com.example.domain.model.UnitType
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
        imageRefs: List<String> = emptyList()
    ): Result<String> {
        val docRef = db.collection("properties").document()
        return try {
            val uid = requireUserId()
            val cleanOrgId = orgId.trim()
            require(cleanOrgId.isNotBlank()) { "An existing organization ID is required before adding a property." }
            val cleanName = name.trim()
            val cleanCity = city.trim()
            val cleanCounty = county.trim()
            require(cleanName.length in 2..120) { "Property name must be 2-120 characters" }
            require(cleanCity.length in 2..60) { "City is required" }
            require(cleanCounty.length <= 60) { "County must be 60 characters or fewer" }

            val prop = Property(
                orgId = cleanOrgId,
                ownerUid = uid,
                managerUids = emptyList(),
                authorizedUids = listOf(uid),
                name = cleanName.take(120),
                category = category.name,
                city = cleanCity.take(60),
                county = cleanCounty.take(60),
                neighborhood = neighborhood.trim().take(80).ifBlank { cleanCity.take(60) },
                addressLine = addressLine.trim().take(160).ifBlank { cleanCity.take(60) },
                description = description.trim().take(1000),
                waterMeterNumber = waterMeterNumber?.trim()?.take(60)?.takeIf { it.isNotBlank() },
                electricityMeterNumber = electricityMeterNumber?.trim()?.take(60)?.takeIf { it.isNotBlank() },
                notes = notes?.trim()?.take(1000)?.takeIf { it.isNotBlank() },
                imageRefs = imageRefs.take(6),
                archived = false,
                hasPublishedUnits = false,
                totalUnitsCount = 0L,
                occupiedUnitsCount = 0L
            )
            docRef.set(prop.toCreateMap()).await()
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
        archived: Boolean
    ): Result<Unit> {
        val cleanId = propertyId.trim()
        val path = "properties/$cleanId"
        return try {
            requireUserId()
            require(cleanId.isNotBlank()) { "Property ID is required" }
            val cleanName = name.trim()
            val cleanCity = city.trim()
            val cleanCounty = county.trim()
            require(cleanName.length in 2..120) { "Property name must be 2-120 characters" }
            require(cleanCity.length in 2..60) { "City is required" }
            require(cleanCounty.length <= 60) { "County must be 60 characters or fewer" }

            val updates = mutableMapOf<String, Any>(
                "name" to cleanName.take(120),
                "category" to category.name,
                "city" to cleanCity.take(60),
                "neighborhood" to neighborhood.trim().take(80).ifBlank { cleanCity.take(60) },
                "addressLine" to addressLine.trim().take(160).ifBlank { cleanCity.take(60) },
                "description" to description.trim().take(1000),
                "imageRefs" to imageRefs.take(6),
                "archived" to archived,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            updates["county"] = cleanCounty.take(60).takeIf { it.isNotBlank() } ?: FieldValue.delete()
            updates["waterMeterNumber"] = waterMeterNumber?.trim()?.take(60)?.takeIf { it.isNotBlank() } ?: FieldValue.delete()
            updates["electricityMeterNumber"] = electricityMeterNumber?.trim()?.take(60)?.takeIf { it.isNotBlank() } ?: FieldValue.delete()
            updates["notes"] = notes?.trim()?.take(1000)?.takeIf { it.isNotBlank() } ?: FieldValue.delete()

            db.collection("properties").document(cleanId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteProperty(propertyId: String): Result<Unit> {
        val cleanId = propertyId.trim()
        val path = "properties/$cleanId"
        return try {
            val uid = requireUserId()
            require(cleanId.isNotBlank()) { "Property ID is required" }
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
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun assignManagerToProperty(property: Property, managerUid: String): Result<Unit> {
        val cleanManager = managerUid.trim()
        val path = "properties/${property.id}"
        return try {
            val uid = requireUserId()
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
        published: Boolean
    ): Result<String> {
        val docRef = db.collection("units").document()
        return try {
            val uid = requireUserId()
            val cleanUnitNumber = unitNumber.trim()
            require(property.id.isNotBlank()) { "Parent property ID is required" }
            require(cleanUnitNumber.length in 1..40) { "Unit number is required" }
            require(rentCents > 0L) { "Monthly rent must be greater than zero" }
            require(depositCents >= 0L) { "Deposit must be non-negative" }

            val existingUnitsSnap = db.collection("units")
                .whereEqualTo("propertyId", property.id)
                .whereArrayContains("authorizedUids", uid)
                .get()
                .await()
            val duplicateExists = existingUnitsSnap.toObjects(RentalUnit::class.java)
                .any { it.unitNumber.trim().equals(cleanUnitNumber, ignoreCase = true) }
            if (duplicateExists) {
                return Result.failure(IllegalArgumentException("Unit $cleanUnitNumber already exists in ${property.name}."))
            }

            val unit = RentalUnit(
                propertyId = property.id,
                propertyName = property.name,
                propertyCity = property.city,
                propertyCategory = property.category,
                orgId = property.orgId,
                ownerUid = property.ownerUid,
                managerUids = property.managerUids,
                authorizedUids = property.authorizedUids,
                unitNumber = cleanUnitNumber,
                unitType = unitType.name,
                rentCents = rentCents,
                depositCents = depositCents.coerceAtLeast(0L),
                status = status.name,
                amenities = amenities.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(15),
                description = description.trim().take(1000),
                imageRefs = imageRefs.take(6),
                published = published && status == UnitStatus.AVAILABLE
            )
            docRef.set(unit.toCreateMap()).await()
            recomputePropertyUnitStats(property.id)
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
        rentCents: Long = unit.rentCents
    ): Result<Unit> {
        val path = "units/${unit.id}"
        return try {
            requireUserId()
            require(rentCents > 0L) { "Monthly rent must be greater than zero" }
            db.collection("units").document(unit.id).update(
                mapOf(
                    "status" to status.name,
                    "published" to (published && status == UnitStatus.AVAILABLE),
                    "rentCents" to rentCents,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recomputePropertyUnitStats(unit.propertyId)
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
        published: Boolean
    ): Result<Unit> {
        val path = "units/${unit.id}"
        return try {
            val uid = requireUserId()
            val cleanUnitNumber = unitNumber.trim()
            require(unit.id.isNotBlank()) { "Unit ID is required" }
            require(unit.propertyId.isNotBlank()) { "Parent property ID is required" }
            require(cleanUnitNumber.length in 1..40) { "Unit number is required" }
            require(rentCents > 0L) { "Monthly rent must be greater than zero" }
            require(depositCents >= 0L) { "Deposit must be non-negative" }

            val siblingUnits = db.collection("units")
                .whereEqualTo("propertyId", unit.propertyId)
                .whereArrayContains("authorizedUids", uid)
                .get()
                .await()
            val duplicateExists = siblingUnits.documents.any { doc ->
                doc.id != unit.id &&
                    (doc.getString("unitNumber")?.trim()?.equals(cleanUnitNumber, ignoreCase = true) == true)
            }
            if (duplicateExists) {
                return Result.failure(IllegalArgumentException("Another unit with number $cleanUnitNumber already exists in ${unit.propertyName}."))
            }

            db.collection("units").document(unit.id).update(
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
            recomputePropertyUnitStats(unit.propertyId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteUnit(unit: RentalUnit): Result<Unit> {
        val path = "units/${unit.id}"
        return try {
            requireUserId()
            require(unit.id.isNotBlank()) { "Unit ID is required" }
            require(unit.propertyId.isNotBlank()) { "Parent property ID is required" }
            if (unit.statusEnum() == UnitStatus.OCCUPIED || !unit.currentLeaseId.isNullOrBlank()) {
                return Result.failure(IllegalStateException("Cannot delete an occupied unit with an active lease."))
            }
            db.collection("units").document(unit.id).delete().await()
            recomputePropertyUnitStats(unit.propertyId)
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
            db.collection("leases").document(lease.id).update(
                mapOf(
                    "endDateEpochMs" to newEndDateEpochMs,
                    "rentCents" to newRentCents,
                    "status" to LeaseStatus.ACTIVE.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
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
