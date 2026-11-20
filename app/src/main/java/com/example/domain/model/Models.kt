package com.example.domain.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

inline fun <reified T : Enum<T>> safeEnum(raw: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: default

enum class UserRole(val label: String) {
    LANDLORD("Landlord"),
    PROPERTY_MANAGER("Property Manager"),
    TENANT("Tenant"),
    MAINTENANCE_STAFF("Maintenance Staff"),
    ADMIN("Administrator")
}

enum class PropertyCategory(val label: String) {
    APARTMENTS("Apartments"),
    BEDSITTERS("Bedsitters"),
    RESIDENTIAL_HOUSES("Residential Houses"),
    COMMERCIAL_BUILDINGS("Commercial Buildings"),
    SHOPS("Shops"),
    OFFICES("Offices"),
    MIXED_USE("Mixed-Use")
}

enum class UnitType(val label: String) {
    BEDSITTER("Bedsitter"),
    SINGLE_ROOM("Single Room"),
    ONE_BEDROOM("1 Bedroom"),
    TWO_BEDROOM("2 Bedroom"),
    THREE_BEDROOM("3 Bedroom"),
    COMMERCIAL("Commercial Space"),
    SHOP("Shop"),
    OFFICE("Office")
}

enum class UnitStatus(val label: String) {
    AVAILABLE("Available"),
    RESERVED("Reserved"),
    OCCUPIED("Occupied"),
    UNDER_MAINTENANCE("Under Maintenance")
}

enum class ApplicationStatus(val label: String) {
    SUBMITTED("Submitted"),
    UNDER_REVIEW("Under Review"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    WITHDRAWN("Withdrawn")
}

enum class LeaseStatus(val label: String) {
    ACTIVE("Active"),
    EXPIRED("Expired"),
    TERMINATED("Terminated"),
    RENEWED("Renewed")
}

enum class InvoiceStatus(val label: String) {
    UNPAID("Unpaid"),
    PARTIALLY_PAID("Partially Paid"),
    PAID("Paid"),
    OVERDUE("Overdue"),
    CANCELLED("Cancelled")
}

enum class PaymentMethod(val label: String) {
    MPESA("M-Pesa"),
    BANK_TRANSFER("Bank Transfer"),
    CASH("Cash")
}

enum class PaymentStatus(val label: String) {
    PENDING_VERIFICATION("Pending Verification"),
    VERIFIED("Verified"),
    REJECTED("Rejected"),
    REVERSED("Reversed")
}

enum class ExpenseCategory(val label: String) {
    MAINTENANCE("Maintenance"),
    REPAIRS("Repairs"),
    SECURITY("Security"),
    CLEANING("Cleaning"),
    UTILITIES("Utilities"),
    MANAGEMENT_FEES("Management Fees"),
    INSURANCE("Insurance"),
    OTHER("Other Expenses")
}

enum class MaintenanceCategory(val label: String) {
    PLUMBING("Plumbing"),
    ELECTRICAL("Electrical"),
    WATER_SUPPLY("Water Supply"),
    DOORS_WINDOWS("Doors & Locks"),
    ROOF_LEAK("Roof Leak"),
    GENERAL_REPAIR("General Repair")
}

enum class MaintenancePriority(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    URGENT("Urgent")
}

enum class MaintenanceStatus(val label: String) {
    SUBMITTED("Submitted"),
    UNDER_REVIEW("Under Review"),
    ASSIGNED("Assigned"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    REJECTED("Rejected")
}

enum class NotificationType(val label: String) {
    INVOICE("Invoice"),
    PAYMENT("Payment"),
    MAINTENANCE("Maintenance"),
    LEASE("Lease"),
    APPLICATION("Application"),
    ANNOUNCEMENT("Announcement"),
    MESSAGE("Message")
}

enum class IssueStatus(val label: String) {
    OPEN("Open"),
    INVESTIGATING("Investigating"),
    RESOLVED("Resolved")
}

enum class InviteStatus(val label: String) {
    PENDING("Pending"),
    ACCEPTED("Accepted"),
    DECLINED("Declined"),
    REVOKED("Revoked")
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val phoneNumber: String? = null,
    val idNumber: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val roles: List<String> = listOf(UserRole.TENANT.name),
    val activeRole: String = UserRole.TENANT.name,
    val activeOrgId: String? = null,
    val lastAcceptedInviteId: String? = null,
    val notifyRentDue: Boolean = true,
    val notifyMaintenance: Boolean = true,
    val notifyAnnouncements: Boolean = true,
    val suspended: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun roleEnums(): List<UserRole> = roles.map { safeEnum(it, UserRole.TENANT) }
    fun activeRoleEnum(): UserRole = safeEnum(activeRole, UserRole.TENANT)

    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "fullName" to fullName,
        "email" to email,
        "phoneNumber" to phoneNumber?.takeIf { it.isNotBlank() },
        "idNumber" to idNumber?.takeIf { it.isNotBlank() },
        "emergencyContactName" to emergencyContactName?.takeIf { it.isNotBlank() },
        "emergencyContactPhone" to emergencyContactPhone?.takeIf { it.isNotBlank() },
        "roles" to roles,
        "activeRole" to activeRole,
        "activeOrgId" to activeOrgId?.takeIf { it.isNotBlank() },
        "lastAcceptedInviteId" to lastAcceptedInviteId?.takeIf { it.isNotBlank() },
        "notifyRentDue" to notifyRentDue,
        "notifyMaintenance" to notifyMaintenance,
        "notifyAnnouncements" to notifyAnnouncements,
        "suspended" to suspended,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

typealias User = UserProfile

@Keep
@IgnoreExtraProperties
data class RoleInvite(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val orgName: String = "",
    val propertyIds: List<String> = emptyList(),
    val inviterUid: String = "",
    val inviterName: String = "",
    val inviteeEmail: String = "",
    val role: String = UserRole.PROPERTY_MANAGER.name,
    val status: String = InviteStatus.PENDING.name,
    val acceptedByUid: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun roleEnum(): UserRole = safeEnum(role, UserRole.PROPERTY_MANAGER)
    fun statusEnum(): InviteStatus = safeEnum(status, InviteStatus.PENDING)

    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "orgId" to orgId,
        "orgName" to orgName,
        "propertyIds" to propertyIds,
        "inviterUid" to inviterUid,
        "inviterName" to inviterName,
        "inviteeEmail" to inviteeEmail.trim().lowercase(),
        "role" to role,
        "status" to status,
        "acceptedByUid" to acceptedByUid?.takeIf { it.isNotBlank() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class Organization(
    @DocumentId val id: String = "",
    val name: String = "",
    val ownerUid: String = "",
    val country: String = "Kenya",
    val currency: String = "KES",
    val defaultDueDay: Long = 5L,
    val memberUids: List<String> = emptyList(),
    val suspended: Boolean = false,
    val verified: Boolean = false,
    val verifiedAtEpochMs: Long? = null,
    val verifiedByUid: String? = null,
    val mpesaPaybill: String? = null,
    val mpesaTill: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "name" to name,
        "ownerUid" to ownerUid,
        "country" to country,
        "currency" to currency,
        "defaultDueDay" to defaultDueDay,
        "memberUids" to memberUids,
        "suspended" to suspended,
        "verified" to verified,
        "verifiedAtEpochMs" to verifiedAtEpochMs,
        "verifiedByUid" to verifiedByUid?.takeIf { it.isNotBlank() },
        "mpesaPaybill" to mpesaPaybill?.takeIf { it.isNotBlank() },
        "mpesaTill" to mpesaTill?.takeIf { it.isNotBlank() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class Property(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerUid: String = "",
    val managerUids: List<String> = emptyList(),
    val authorizedUids: List<String> = emptyList(),
    val name: String = "",
    val category: String = PropertyCategory.APARTMENTS.name,
    val city: String = "Nairobi",
    val county: String = "",
    val neighborhood: String = "",
    val addressLine: String = "",
    val description: String = "",
    val waterMeterNumber: String? = null,
    val electricityMeterNumber: String? = null,
    val notes: String? = null,
    val imageRefs: List<String> = emptyList(),
    val archived: Boolean = false,
    val hasPublishedUnits: Boolean = false,
    val totalUnitsCount: Long = 0L,
    val occupiedUnitsCount: Long = 0L,
    val landlordVerified: Boolean = false,
    val mpesaPaybill: String? = null,
    val mpesaTill: String? = null,
    val mpesaAccountPrefix: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun categoryEnum(): PropertyCategory = safeEnum(category, PropertyCategory.APARTMENTS)

    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "orgId" to orgId,
        "ownerUid" to ownerUid,
        "managerUids" to managerUids,
        "authorizedUids" to authorizedUids,
        "name" to name,
        "category" to category,
        "city" to city,
        "county" to county.takeIf { it.isNotBlank() },
        "neighborhood" to neighborhood,
        "addressLine" to addressLine,
        "description" to description,
        "waterMeterNumber" to waterMeterNumber?.takeIf { it.isNotBlank() },
        "electricityMeterNumber" to electricityMeterNumber?.takeIf { it.isNotBlank() },
        "notes" to notes?.takeIf { it.isNotBlank() },
        "imageRefs" to imageRefs,
        "archived" to archived,
        "hasPublishedUnits" to hasPublishedUnits,
        "totalUnitsCount" to totalUnitsCount,
        "occupiedUnitsCount" to occupiedUnitsCount,
        "landlordVerified" to landlordVerified,
        "mpesaPaybill" to mpesaPaybill?.takeIf { it.isNotBlank() },
        "mpesaTill" to mpesaTill?.takeIf { it.isNotBlank() },
        "mpesaAccountPrefix" to mpesaAccountPrefix?.takeIf { it.isNotBlank() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class RentalUnit(
    @DocumentId val id: String = "",
    val propertyId: String = "",
    val propertyName: String = "",
    val propertyCity: String = "",
    val propertyCategory: String = PropertyCategory.APARTMENTS.name,
    val orgId: String = "",
    val ownerUid: String = "",
    val managerUids: List<String> = emptyList(),
    val authorizedUids: List<String> = emptyList(),
    val unitNumber: String = "",
    val unitType: String = UnitType.ONE_BEDROOM.name,
    val rentCents: Long = 0L,
    val depositCents: Long = 0L,
    val status: String = UnitStatus.AVAILABLE.name,
    val amenities: List<String> = emptyList(),
    val description: String = "",
    val imageRefs: List<String> = emptyList(),
    val published: Boolean = true,
    val currentTenantUid: String? = null,
    val currentLeaseId: String? = null,
    val landlordVerified: Boolean = false,
    val mpesaPaybill: String? = null,
    val mpesaTill: String? = null,
    val mpesaAccountPrefix: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun unitTypeEnum(): UnitType = safeEnum(unitType, UnitType.ONE_BEDROOM)
    fun statusEnum(): UnitStatus = safeEnum(status, UnitStatus.AVAILABLE)
    fun monthlyRentKes(): Long = rentCents / 100L
    fun depositKes(): Long = depositCents / 100L

    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "propertyId" to propertyId,
        "propertyName" to propertyName,
        "propertyCity" to propertyCity,
        "propertyCategory" to propertyCategory,
        "orgId" to orgId,
        "ownerUid" to ownerUid,
        "managerUids" to managerUids,
        "authorizedUids" to authorizedUids,
        "unitNumber" to unitNumber,
        "unitType" to unitType,
        "rentCents" to rentCents,
        "depositCents" to depositCents,
        "status" to status,
        "amenities" to amenities,
        "description" to description,
        "imageRefs" to imageRefs,
        "published" to published,
        "currentTenantUid" to currentTenantUid?.takeIf { it.isNotBlank() },
        "currentLeaseId" to currentLeaseId?.takeIf { it.isNotBlank() },
        "landlordVerified" to landlordVerified,
        "mpesaPaybill" to mpesaPaybill?.takeIf { it.isNotBlank() },
        "mpesaTill" to mpesaTill?.takeIf { it.isNotBlank() },
        "mpesaAccountPrefix" to mpesaAccountPrefix?.takeIf { it.isNotBlank() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class RentalApplication(
    @DocumentId val id: String = "",
    val unitId: String = "",
    val unitNumber: String = "",
    val propertyId: String = "",
    val propertyName: String = "",
    val orgId: String = "",
    val ownerUid: String = "",
    val managerUids: List<String> = emptyList(),
    val authorizedUids: List<String> = emptyList(),
    val applicantUid: String = "",
    val applicantName: String = "",
    val applicantPhone: String = "",
    val applicantEmail: String? = null,
    val occupation: String? = null,
    val desiredMoveInDate: String = "",
    val notes: String? = null,
    val reviewerNotes: String? = null,
    val status: String = ApplicationStatus.SUBMITTED.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): ApplicationStatus = safeEnum(status, ApplicationStatus.SUBMITTED)

    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "unitId" to unitId,
        "unitNumber" to unitNumber,
        "propertyId" to propertyId,
        "propertyName" to propertyName,
        "orgId" to orgId,
        "ownerUid" to ownerUid,
        "managerUids" to managerUids,
        "authorizedUids" to authorizedUids,
        "applicantUid" to applicantUid,
        "applicantName" to applicantName,
        "applicantPhone" to applicantPhone,
        "applicantEmail" to applicantEmail?.takeIf { it.isNotBlank() },
        "occupation" to occupation?.takeIf { it.isNotBlank() },
        "desiredMoveInDate" to desiredMoveInDate,
        "notes" to notes?.takeIf { it.isNotBlank() },
        "reviewerNotes" to reviewerNotes?.takeIf { it.isNotBlank() },
        "status" to status,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class Lease(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val propertyId: String = "",
    val propertyName: String = "",
    val unitId: String = "",
    val unitNumber: String = "",
    val ownerUid: String = "",
    val managerUids: List<String> = emptyList(),
    val authorizedUids: List<String> = emptyList(),
    val tenantUid: String = "",
    val tenantName: String = "",
    val tenantPhone: String = "",
    val rentCents: Long = 0L,
    val depositCents: Long = 0L,
    val startDateEpochMs: Long = 0L,
    val endDateEpochMs: Long = 0L,
    val dueDayOfMonth: Long = 5L,
    val status: String = LeaseStatus.ACTIVE.name,
    val creditBalanceCents: Long = 0L,
    val termsSummary: String = "",
    val terminationNotice: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): LeaseStatus = safeEnum(status, LeaseStatus.ACTIVE)

    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "orgId" to orgId,
        "propertyId" to propertyId,
        "propertyName" to propertyName,
        "unitId" to unitId,
        "unitNumber" to unitNumber,
        "ownerUid" to ownerUid,
        "managerUids" to managerUids,
        "authorizedUids" to authorizedUids,
        "tenantUid" to tenantUid,
        "tenantName" to tenantName,
        "tenantPhone" to tenantPhone,
        "rentCents" to rentCents,
        "depositCents" to depositCents,
        "startDateEpochMs" to startDateEpochMs,
        "endDateEpochMs" to endDateEpochMs,
        "dueDayOfMonth" to dueDayOfMonth,
        "status" to status,
        "creditBalanceCents" to creditBalanceCents,
        "termsSummary" to termsSummary,
        "terminationNotice" to terminationNotice?.takeIf { it.isNotBlank() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class Invoice(
    @DocumentId val id: String = "",
    val invoiceNumber: String = "",
    val leaseId: String = "",
    val orgId: String = "",
    val propertyId: String = "",
    val propertyName: String = "",
    val unitId: String = "",
    val unitNumber: String = "",
    val ownerUid: String = "",
    val managerUids: List<String> = emptyList(),
    val authorizedUids: List<String> = emptyList(),
    val tenantUid: String = "",
    val tenantName: String = "",
    val billingPeriod: String = "",
    val rentCents: Long = 0L,
    val extraChargesCents: Long = 0L,
    val extraChargesNote: String? = null,
    val previousBalanceCents: Long = 0L,
    val totalDueCents: Long = 0L,
    val paidCents: Long = 0L,
    val outstandingCents: Long = 0L,
    val dueDateEpochMs: Long = 0L,
    val status: String = InvoiceStatus.UNPAID.name,
    val lastReminderSentEpochMs: Long? = null,
    val reminderCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): InvoiceStatus = safeEnum(status, InvoiceStatus.UNPAID)

    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "invoiceNumber" to invoiceNumber,
        "leaseId" to leaseId,
        "orgId" to orgId,
        "propertyId" to propertyId,
        "propertyName" to propertyName,
        "unitId" to unitId,
        "unitNumber" to unitNumber,
        "ownerUid" to ownerUid,
        "managerUids" to managerUids,
        "authorizedUids" to authorizedUids,
        "tenantUid" to tenantUid,
        "tenantName" to tenantName,
        "billingPeriod" to billingPeriod,
        "rentCents" to rentCents,
        "extraChargesCents" to extraChargesCents,
        "extraChargesNote" to extraChargesNote?.takeIf { it.isNotBlank() },
        "previousBalanceCents" to previousBalanceCents,
        "totalDueCents" to totalDueCents,
        "paidCents" to paidCents,
        "outstandingCents" to outstandingCents,
        "dueDateEpochMs" to dueDateEpochMs,
        "status" to status,
        "lastReminderSentEpochMs" to lastReminderSentEpochMs,
        "reminderCount" to reminderCount,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class Payment(
    @DocumentId val id: String = "",
    val referenceCode: String = "",
    val leaseId: String = "",
    val invoiceId: String? = null,
    val orgId: String = "",
    val propertyId: String = "",
    val propertyName: String = "",
    val unitId: String = "",
    val unitNumber: String = "",
    val ownerUid: String = "",
    val managerUids: List<String> = emptyList(),
    val authorizedUids: List<String> = emptyList(),
    val tenantUid: String = "",
    val tenantName: String = "",
    val amountCents: Long = 0L,
    val method: String = PaymentMethod.MPESA.name,
    val simulatedCheckout: Boolean = false,
    val paymentDateEpochMs: Long = 0L,
    val status: String = PaymentStatus.PENDING_VERIFICATION.name,
    val recordedByUid: String = "",
    val verifiedByUid: String? = null,
    val verificationNotes: String? = null,
    val reversalReason: String? = null,
    val allocationsSummary: String? = null,
    val receiptNumber: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun methodEnum(): PaymentMethod = safeEnum(method, PaymentMethod.MPESA)
    fun statusEnum(): PaymentStatus = safeEnum(status, PaymentStatus.PENDING_VERIFICATION)

    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "referenceCode" to referenceCode,
        "leaseId" to leaseId,
        "invoiceId" to invoiceId?.takeIf { it.isNotBlank() },
        "orgId" to orgId,
        "propertyId" to propertyId,
        "propertyName" to propertyName,
        "unitId" to unitId,
        "unitNumber" to unitNumber,
        "ownerUid" to ownerUid,
        "managerUids" to managerUids,
        "authorizedUids" to authorizedUids,
        "tenantUid" to tenantUid,
        "tenantName" to tenantName,
        "amountCents" to amountCents,
        "method" to method,
        "simulatedCheckout" to simulatedCheckout,
        "paymentDateEpochMs" to paymentDateEpochMs,
        "status" to status,
        "recordedByUid" to recordedByUid,
        "verifiedByUid" to verifiedByUid?.takeIf { it.isNotBlank() },
        "verificationNotes" to verificationNotes?.takeIf { it.isNotBlank() },
        "reversalReason" to reversalReason?.takeIf { it.isNotBlank() },
        "allocationsSummary" to allocationsSummary?.takeIf { it.isNotBlank() },
        "receiptNumber" to receiptNumber?.takeIf { it.isNotBlank() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class Expense(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val propertyId: String = "",
    val propertyName: String = "",
    val unitId: String? = null,
    val ownerUid: String = "",
    val managerUids: List<String> = emptyList(),
    val authorizedUids: List<String> = emptyList(),
    val category: String = ExpenseCategory.MAINTENANCE.name,
    val title: String = "",
    val description: String = "",
    val amountCents: Long = 0L,
    val expenseDateEpochMs: Long = 0L,
    val documentRef: String? = null,
    val recordedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun categoryEnum(): ExpenseCategory = safeEnum(category, ExpenseCategory.MAINTENANCE)

    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "orgId" to orgId,
        "propertyId" to propertyId,
        "propertyName" to propertyName,
        "unitId" to unitId?.takeIf { it.isNotBlank() },
        "ownerUid" to ownerUid,
        "managerUids" to managerUids,
        "authorizedUids" to authorizedUids,
        "category" to category,
        "title" to title,
        "description" to description,
        "amountCents" to amountCents,
        "expenseDateEpochMs" to expenseDateEpochMs,
        "documentRef" to documentRef?.takeIf { it.isNotBlank() },
        "recordedByUid" to recordedByUid,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class MaintenanceRequest(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val propertyId: String = "",
    val propertyName: String = "",
    val unitId: String = "",
    val unitNumber: String = "",
    val ownerUid: String = "",
    val managerUids: List<String> = emptyList(),
    val authorizedUids: List<String> = emptyList(),
    val tenantUid: String = "",
    val tenantName: String = "",
    val assignedStaffUid: String? = null,
    val assignedStaffName: String? = null,
    val title: String = "",
    val description: String = "",
    val category: String = MaintenanceCategory.GENERAL_REPAIR.name,
    val priority: String = MaintenancePriority.MEDIUM.name,
    val status: String = MaintenanceStatus.SUBMITTED.name,
    val repairCostCents: Long = 0L,
    val imageRefs: List<String> = emptyList(),
    val latestUpdateNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun categoryEnum(): MaintenanceCategory = safeEnum(category, MaintenanceCategory.GENERAL_REPAIR)
    fun priorityEnum(): MaintenancePriority = safeEnum(priority, MaintenancePriority.MEDIUM)
    fun statusEnum(): MaintenanceStatus = safeEnum(status, MaintenanceStatus.SUBMITTED)

    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "orgId" to orgId,
        "propertyId" to propertyId,
        "propertyName" to propertyName,
        "unitId" to unitId,
        "unitNumber" to unitNumber,
        "ownerUid" to ownerUid,
        "managerUids" to managerUids,
        "authorizedUids" to authorizedUids,
        "tenantUid" to tenantUid,
        "tenantName" to tenantName,
        "assignedStaffUid" to assignedStaffUid?.takeIf { it.isNotBlank() },
        "assignedStaffName" to assignedStaffName?.takeIf { it.isNotBlank() },
        "title" to title,
        "description" to description,
        "category" to category,
        "priority" to priority,
        "status" to status,
        "repairCostCents" to repairCostCents,
        "imageRefs" to imageRefs,
        "latestUpdateNote" to latestUpdateNote?.takeIf { it.isNotBlank() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val propertyId: String = "",
    val propertyName: String = "",
    val unitNumber: String? = null,
    val participantUids: List<String> = emptyList(),
    val subject: String = "",
    val lastMessagePreview: String = "",
    val lastSenderUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "orgId" to orgId,
        "propertyId" to propertyId,
        "propertyName" to propertyName,
        "unitNumber" to unitNumber?.takeIf { it.isNotBlank() },
        "participantUids" to participantUids,
        "subject" to subject,
        "lastMessagePreview" to lastMessagePreview,
        "lastSenderUid" to lastSenderUid,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val convId: String = "",
    val participantUids: List<String> = emptyList(),
    val senderUid: String = "",
    val senderName: String = "",
    val body: String = "",
    val readByUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "convId" to convId,
        "participantUids" to participantUids,
        "senderUid" to senderUid,
        "senderName" to senderName,
        "body" to body,
        "readByUids" to readByUids,
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class Announcement(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val propertyId: String = "",
    val propertyName: String = "",
    val ownerUid: String = "",
    val authorizedUids: List<String> = emptyList(),
    val targetUids: List<String> = emptyList(),
    val title: String = "",
    val body: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "orgId" to orgId,
        "propertyId" to propertyId,
        "propertyName" to propertyName,
        "ownerUid" to ownerUid,
        "authorizedUids" to authorizedUids,
        "targetUids" to targetUids,
        "title" to title,
        "body" to body,
        "authorUid" to authorUid,
        "authorName" to authorName,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val recipientUid: String = "",
    val senderUid: String = "",
    val orgId: String = "",
    val type: String = NotificationType.INVOICE.name,
    val title: String = "",
    val body: String = "",
    val relatedEntityId: String? = null,
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun typeEnum(): NotificationType = safeEnum(type, NotificationType.INVOICE)

    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "recipientUid" to recipientUid,
        "senderUid" to senderUid,
        "orgId" to orgId,
        "type" to type,
        "title" to title,
        "body" to body,
        "relatedEntityId" to relatedEntityId?.takeIf { it.isNotBlank() },
        "read" to read,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val action: String = "",
    val entityType: String = "",
    val entityId: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "orgId" to orgId,
        "actorUid" to actorUid,
        "actorName" to actorName,
        "action" to action,
        "entityType" to entityType,
        "entityId" to entityId,
        "details" to details,
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class ReportedIssue(
    @DocumentId val id: String = "",
    val reporterUid: String = "",
    val reporterName: String = "",
    val orgId: String = "",
    val category: String = "",
    val title: String = "",
    val description: String = "",
    val status: String = IssueStatus.OPEN.name,
    val adminNotes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): IssueStatus = safeEnum(status, IssueStatus.OPEN)

    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "reporterUid" to reporterUid,
        "reporterName" to reporterName,
        "orgId" to orgId,
        "category" to category,
        "title" to title,
        "description" to description,
        "status" to status,
        "adminNotes" to adminNotes?.takeIf { it.isNotBlank() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class FcmDeviceToken(
    @DocumentId val id: String = "",
    val userId: String = "",
    val token: String = "",
    val platform: String = "ANDROID",
    val notifyMaintenance: Boolean = true,
    val activeRole: String = UserRole.TENANT.name,
    val appVersion: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "token" to token,
        "platform" to platform,
        "notifyMaintenance" to notifyMaintenance,
        "activeRole" to activeRole,
        "appVersion" to appVersion?.takeIf { it.isNotBlank() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class SavedUnit(
    @DocumentId val id: String = "",
    val unitId: String = "",
    val propertyId: String = "",
    val propertyName: String = "",
    val unitNumber: String = "",
    val rentCents: Long = 0L,
    @ServerTimestamp val savedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "unitId" to unitId,
        "propertyId" to propertyId,
        "propertyName" to propertyName,
        "unitNumber" to unitNumber,
        "rentCents" to rentCents,
        "savedAt" to FieldValue.serverTimestamp()
    )
}

object KenyaCounties {
    val MAJOR_COUNTIES: List<String> = listOf(
        "Nairobi",
        "Kiambu",
        "Mombasa",
        "Kisumu",
        "Nakuru",
        "Uasin Gishu",
        "Machakos",
        "Kajiado",
        "Kilifi",
        "Nyeri",
        "Laikipia",
        "Meru",
        "Kakamega"
    )
}

