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

enum class PaymentChannelType(
    val label: String,
    val baseMethod: PaymentMethod
) {
    MPESA_PAYBILL("M-Pesa Paybill", PaymentMethod.MPESA),
    MPESA_TILL("M-Pesa Buy Goods Till", PaymentMethod.MPESA),
    MPESA_STK("M-Pesa Express (STK)", PaymentMethod.MPESA),
    BANK_TRANSFER("Bank Transfer", PaymentMethod.BANK_TRANSFER),
    CASH("Cash", PaymentMethod.CASH)
}

enum class PaymentRoleScope(val label: String) {
    ALL("Tenant & Landlord"),
    TENANT("Tenant Only"),
    LANDLORD("Landlord Only")
}

enum class PaymentStatus(val label: String) {
    PENDING_VERIFICATION("Pending Verification"),
    VERIFIED("Verified"),
    REJECTED("Rejected"),
    REVERSED("Reversed")
}

enum class PaymentVerificationSource(val label: String) {
    MANUAL("Manual Review"),
    MPESA_SMS_MATCH("M-Pesa SMS Verified")
}

enum class MpesaReceiptDirection(val label: String) {
    INCOMING_RECEIVED("Incoming Received"),
    OUTGOING_SENT("Outgoing Sent"),
    UNKNOWN("Detected")
}

enum class MpesaCaptureSource(val label: String) {
    NOTIFICATION("Notification Auto-Capture"),
    SHARE_INTENT("Shared SMS"),
    MANUAL_PASTE("Pasted SMS")
}

enum class MpesaReceiptStatus(val label: String) {
    STAGED("Staged"),
    RECONCILED("Reconciled"),
    DISMISSED("Dismissed")
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

enum class ChecklistCondition(val label: String) {
    GOOD("Good"),
    FAIR("Fair"),
    NEEDS_REPAIR("Needs Repair"),
    DAMAGED("Damaged")
}

enum class InspectionItemStatus(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair"),
    NEEDS_REPAIR("Needs Repair"),
    DAMAGED("Damaged"),
    MISSING("Missing"),
    NOT_APPLICABLE("N/A")
}

enum class MoveInChecklistStatus(val label: String) {
    DRAFT("Draft"),
    PENDING_TENANT_REVIEW("Pending Sign-Off"),
    ACKNOWLEDGED("Acknowledged"),
    DISPUTED("Discrepancy Flagged")
}

enum class DeductionCategory(val label: String) {
    DAMAGE_REPAIR("Damage Repair"),
    CLEANING("Deep Cleaning"),
    UNPAID_RENT("Unpaid Rent / Arrears"),
    UTILITIES("Unpaid Utilities"),
    MISSING_KEYS("Missing Keys / Locks"),
    OTHER("Other Deduction")
}

enum class DepositSettlementStatus(val label: String) {
    DRAFT("Draft"),
    ISSUED("Statement Issued"),
    DISPUTED("Under Review"),
    APPROVED("Approved by Tenant"),
    SETTLED("Refund Settled")
}

enum class RefundMethod(val label: String) {
    MPESA("M-Pesa"),
    BANK_TRANSFER("Bank Transfer"),
    CASH("Cash"),
    NONE("No Refund Due")
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
    val latitude: Double? = null,
    val longitude: Double? = null,
    val formattedAddress: String? = null,
    val placeId: String? = null,
    val locationVerified: Boolean = false,
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
        "latitude" to latitude?.takeIf { longitude != null && it.isFinite() && it in -90.0..90.0 && !(it == 0.0 && longitude == 0.0) },
        "longitude" to longitude?.takeIf { latitude != null && it.isFinite() && it in -180.0..180.0 && !(latitude == 0.0 && it == 0.0) },
        "formattedAddress" to formattedAddress?.trim()?.takeIf { it.isNotBlank() }?.take(200),
        "placeId" to placeId?.trim()?.takeIf { it.isNotBlank() }?.take(160),
        "locationVerified" to (locationVerified && latitude != null && longitude != null),
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
    val latitude: Double? = null,
    val longitude: Double? = null,
    val formattedAddress: String? = null,
    val locationVerified: Boolean = false,
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
        "latitude" to latitude?.takeIf { longitude != null && it.isFinite() && it in -90.0..90.0 && !(it == 0.0 && longitude == 0.0) },
        "longitude" to longitude?.takeIf { latitude != null && it.isFinite() && it in -180.0..180.0 && !(latitude == 0.0 && it == 0.0) },
        "formattedAddress" to formattedAddress?.trim()?.takeIf { it.isNotBlank() }?.take(200),
        "locationVerified" to (locationVerified && latitude != null && longitude != null),
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
    val paymentMethodId: String? = null,
    val paymentChannelType: String? = null,
    val paymentShortcode: String? = null,
    val simulatedCheckout: Boolean = false,
    val paymentDateEpochMs: Long = 0L,
    val status: String = PaymentStatus.PENDING_VERIFICATION.name,
    val recordedByUid: String = "",
    val verifiedByUid: String? = null,
    val verificationNotes: String? = null,
    val reversalReason: String? = null,
    val allocationsSummary: String? = null,
    val receiptNumber: String? = null,
    val verificationSource: String? = null,
    val matchedReceiptId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun methodEnum(): PaymentMethod = safeEnum(method, PaymentMethod.MPESA)
    fun channelTypeEnum(): PaymentChannelType? =
        paymentChannelType?.let { raw -> enumValues<PaymentChannelType>().firstOrNull { it.name == raw } }
    fun statusEnum(): PaymentStatus = safeEnum(status, PaymentStatus.PENDING_VERIFICATION)
    fun verificationSourceEnum(): PaymentVerificationSource? =
        verificationSource?.let { raw -> enumValues<PaymentVerificationSource>().firstOrNull { it.name == raw } }

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
        "paymentMethodId" to paymentMethodId?.takeIf { it.isNotBlank() },
        "paymentChannelType" to paymentChannelType?.takeIf { it.isNotBlank() },
        "paymentShortcode" to paymentShortcode?.takeIf { it.isNotBlank() },
        "simulatedCheckout" to simulatedCheckout,
        "paymentDateEpochMs" to paymentDateEpochMs,
        "status" to status,
        "recordedByUid" to recordedByUid,
        "verifiedByUid" to verifiedByUid?.takeIf { it.isNotBlank() },
        "verificationNotes" to verificationNotes?.takeIf { it.isNotBlank() },
        "reversalReason" to reversalReason?.takeIf { it.isNotBlank() },
        "allocationsSummary" to allocationsSummary?.takeIf { it.isNotBlank() },
        "receiptNumber" to receiptNumber?.takeIf { it.isNotBlank() },
        "verificationSource" to verificationSource?.takeIf { it.isNotBlank() },
        "matchedReceiptId" to matchedReceiptId?.takeIf { it.isNotBlank() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class MpesaReceipt(
    @DocumentId val id: String = "",
    val userId: String = "",
    val transactionCode: String = "",
    val amountCents: Long = 0L,
    val direction: String = MpesaReceiptDirection.UNKNOWN.name,
    val counterpartyName: String? = null,
    val counterpartyPhone: String? = null,
    val accountReference: String? = null,
    val shortcode: String? = null,
    val captureSource: String = MpesaCaptureSource.MANUAL_PASTE.name,
    val status: String = MpesaReceiptStatus.STAGED.name,
    val matchedPaymentId: String? = null,
    val matchedLeaseId: String? = null,
    val rawSnippet: String = "",
    val transactionTimestampMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun directionEnum(): MpesaReceiptDirection = safeEnum(direction, MpesaReceiptDirection.UNKNOWN)
    fun captureSourceEnum(): MpesaCaptureSource = safeEnum(captureSource, MpesaCaptureSource.MANUAL_PASTE)
    fun statusEnum(): MpesaReceiptStatus = safeEnum(status, MpesaReceiptStatus.STAGED)

    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "transactionCode" to transactionCode.trim().uppercase(),
        "amountCents" to amountCents,
        "direction" to direction,
        "counterpartyName" to counterpartyName?.trim()?.take(100)?.takeIf { it.isNotBlank() },
        "counterpartyPhone" to counterpartyPhone?.trim()?.take(24)?.takeIf { it.isNotBlank() },
        "accountReference" to accountReference?.trim()?.take(40)?.takeIf { it.isNotBlank() },
        "shortcode" to shortcode?.trim()?.take(24)?.takeIf { it.isNotBlank() },
        "captureSource" to captureSource,
        "status" to status,
        "matchedPaymentId" to matchedPaymentId?.trim()?.take(128)?.takeIf { it.isNotBlank() },
        "matchedLeaseId" to matchedLeaseId?.trim()?.take(128)?.takeIf { it.isNotBlank() },
        "rawSnippet" to rawSnippet.trim().take(240),
        "transactionTimestampMs" to transactionTimestampMs,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class SavedPaymentMethod(
    @DocumentId val id: String = "",
    val userId: String = "",
    val label: String = "",
    val channelType: String = PaymentChannelType.MPESA_PAYBILL.name,
    val roleScope: String = PaymentRoleScope.ALL.name,
    val paybillNumber: String? = null,
    val accountReference: String? = null,
    val tillNumber: String? = null,
    val phoneNumber: String? = null,
    val bankName: String? = null,
    val bankAccountNumber: String? = null,
    val defaultMethod: Boolean = false,
    val smsVerified: Boolean = false,
    val smsSampleRef: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun channelTypeEnum(): PaymentChannelType = safeEnum(channelType, PaymentChannelType.MPESA_PAYBILL)
    fun roleScopeEnum(): PaymentRoleScope = safeEnum(roleScope, PaymentRoleScope.ALL)

    fun primaryShortcodeOrIdentifier(): String? = when (channelTypeEnum()) {
        PaymentChannelType.MPESA_PAYBILL -> paybillNumber?.takeIf { it.isNotBlank() }
        PaymentChannelType.MPESA_TILL -> tillNumber?.takeIf { it.isNotBlank() }
        PaymentChannelType.MPESA_STK -> phoneNumber?.takeIf { it.isNotBlank() }
        PaymentChannelType.BANK_TRANSFER -> bankAccountNumber?.takeIf { it.isNotBlank() }
        PaymentChannelType.CASH -> "CASH"
    }

    fun summarySubtitle(): String = when (channelTypeEnum()) {
        PaymentChannelType.MPESA_PAYBILL -> {
            val pb = paybillNumber.orEmpty()
            val acct = accountReference.orEmpty()
            if (acct.isNotBlank()) "Paybill $pb • Acct $acct" else "Paybill $pb"
        }
        PaymentChannelType.MPESA_TILL -> "Buy Goods Till ${tillNumber.orEmpty()}"
        PaymentChannelType.MPESA_STK -> "STK Phone ${phoneNumber.orEmpty()}"
        PaymentChannelType.BANK_TRANSFER -> {
            val masked = bankAccountNumber?.let { acct ->
                if (acct.length > 4) "••••${acct.takeLast(4)}" else acct
            }.orEmpty()
            "${bankName.orEmpty()} $masked".trim()
        }
        PaymentChannelType.CASH -> "Cash collection / receipt"
    }

    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "label" to label,
        "channelType" to channelType,
        "roleScope" to roleScope,
        "paybillNumber" to paybillNumber?.takeIf { it.isNotBlank() },
        "accountReference" to accountReference?.takeIf { it.isNotBlank() },
        "tillNumber" to tillNumber?.takeIf { it.isNotBlank() },
        "phoneNumber" to phoneNumber?.takeIf { it.isNotBlank() },
        "bankName" to bankName?.takeIf { it.isNotBlank() },
        "bankAccountNumber" to bankAccountNumber?.takeIf { it.isNotBlank() },
        "defaultMethod" to defaultMethod,
        "smsVerified" to smsVerified,
        "smsSampleRef" to smsSampleRef?.takeIf { it.isNotBlank() },
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
    val notifyRentDue: Boolean = true,
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
        "notifyRentDue" to notifyRentDue,
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

@Keep
@IgnoreExtraProperties
data class ChecklistItem(
    val itemId: String = "",
    val area: String = "",
    val label: String = "",
    val condition: String = ChecklistCondition.GOOD.name,
    val notes: String = ""
) {
    fun conditionEnum(): ChecklistCondition = safeEnum(condition, ChecklistCondition.GOOD)

    fun toMap(): Map<String, Any> = mapOf(
        "itemId" to itemId.trim().take(64),
        "area" to area.trim().take(60),
        "label" to label.trim().take(100),
        "condition" to conditionEnum().name,
        "notes" to notes.trim().take(300)
    )
}

@Keep
@IgnoreExtraProperties
data class MoveInChecklist(
    @DocumentId val id: String = "",
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
    val status: String = MoveInChecklistStatus.DRAFT.name,
    val inspectionDateEpochMs: Long = 0L,
    val waterMeterReading: String? = null,
    val electricityMeterReading: String? = null,
    val keysHandedOverCount: Long = 0L,
    val items: List<ChecklistItem> = emptyList(),
    val imageRefs: List<String> = emptyList(),
    val landlordNotes: String? = null,
    val tenantResponseNote: String? = null,
    val acknowledgedAtEpochMs: Long? = null,
    val acknowledgedByUid: String? = null,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): MoveInChecklistStatus = safeEnum(status, MoveInChecklistStatus.DRAFT)

    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
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
        "status" to status,
        "inspectionDateEpochMs" to inspectionDateEpochMs,
        "waterMeterReading" to waterMeterReading?.trim()?.take(40)?.takeIf { it.isNotBlank() },
        "electricityMeterReading" to electricityMeterReading?.trim()?.take(40)?.takeIf { it.isNotBlank() },
        "keysHandedOverCount" to keysHandedOverCount.coerceIn(0L, 50L),
        "items" to items.map { it.toMap() },
        "imageRefs" to imageRefs,
        "landlordNotes" to landlordNotes?.trim()?.take(800)?.takeIf { it.isNotBlank() },
        "tenantResponseNote" to tenantResponseNote?.trim()?.take(800)?.takeIf { it.isNotBlank() },
        "acknowledgedAtEpochMs" to acknowledgedAtEpochMs,
        "acknowledgedByUid" to acknowledgedByUid?.takeIf { it.isNotBlank() },
        "createdByUid" to createdByUid,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>

    companion object {
        fun defaultKenyanItems(): List<ChecklistItem> = listOf(
            ChecklistItem("living_walls", "Living Room", "Walls, Paint & Ceiling", ChecklistCondition.GOOD.name, ""),
            ChecklistItem("living_floor", "Living Room", "Flooring & Tiles", ChecklistCondition.GOOD.name, ""),
            ChecklistItem("living_windows", "Living Room", "Windows, Curtains & Security Grilles", ChecklistCondition.GOOD.name, ""),
            ChecklistItem("kitchen_sink", "Kitchen", "Sink, Taps & Plumbing Trap", ChecklistCondition.GOOD.name, ""),
            ChecklistItem("kitchen_cabinets", "Kitchen", "Cabinets, Worktop & Hinges", ChecklistCondition.GOOD.name, ""),
            ChecklistItem("bath_plumbing", "Bathroom", "Shower Head, Drainage & Water Pressure", ChecklistCondition.GOOD.name, ""),
            ChecklistItem("bath_toilet", "Bathroom", "Toilet Cistern, Flush & Seat", ChecklistCondition.GOOD.name, ""),
            ChecklistItem("bedroom_wardrobe", "Bedroom", "Wardrobes, Doors & Handles", ChecklistCondition.GOOD.name, ""),
            ChecklistItem("utilities_power", "Utilities", "KPLC / Token Meter & Sockets", ChecklistCondition.GOOD.name, ""),
            ChecklistItem("security_locks", "Security", "Main Door Lock, Padlock Latch & Keys", ChecklistCondition.GOOD.name, "")
        )
    }
}

@Keep
@IgnoreExtraProperties
data class RoomInspectionItem(
    val itemId: String = "",
    val label: String = "",
    val status: String = InspectionItemStatus.GOOD.name,
    val imageRefs: List<String> = emptyList(),
    val landlordRemarks: String = "",
    val tenantRemarks: String = ""
) {
    fun statusEnum(): InspectionItemStatus = safeEnum(status, InspectionItemStatus.GOOD)

    fun toMap(): Map<String, Any> = mapOf(
        "itemId" to itemId.trim().take(64),
        "label" to label.trim().take(100),
        "status" to statusEnum().name,
        "imageRefs" to imageRefs.map { it.trim() }.filter { it.isNotBlank() }.take(MAX_PHOTOS_PER_ITEM),
        "landlordRemarks" to landlordRemarks.trim().take(400),
        "tenantRemarks" to tenantRemarks.trim().take(400)
    )

    companion object {
        const val MAX_PHOTOS_PER_ITEM = 2
    }
}

@Keep
@IgnoreExtraProperties
data class InspectionRoom(
    val roomId: String = "",
    val roomName: String = "",
    val roomRemarks: String = "",
    val items: List<RoomInspectionItem> = emptyList()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "roomId" to roomId.trim().take(64),
        "roomName" to roomName.trim().take(60),
        "roomRemarks" to roomRemarks.trim().take(400),
        "items" to items.map { it.toMap() }
    )
}

@Keep
@IgnoreExtraProperties
data class MoveInConditionChecklist(
    @DocumentId val id: String = "",
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
    val status: String = MoveInChecklistStatus.DRAFT.name,
    val inspectionDateEpochMs: Long = 0L,
    val waterMeterReading: String? = null,
    val electricityMeterReading: String? = null,
    val keysHandedOverCount: Long = 0L,
    val rooms: List<InspectionRoom> = emptyList(),
    val landlordSummary: String? = null,
    val tenantSummary: String? = null,
    val acknowledgedAtEpochMs: Long? = null,
    val acknowledgedByUid: String? = null,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): MoveInChecklistStatus = safeEnum(status, MoveInChecklistStatus.DRAFT)

    fun totalItemsCount(): Int = rooms.sumOf { it.items.size }

    fun totalPhotoCount(): Int = rooms.sumOf { room -> room.items.sumOf { it.imageRefs.size } }

    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
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
        "status" to status,
        "inspectionDateEpochMs" to inspectionDateEpochMs,
        "waterMeterReading" to waterMeterReading?.trim()?.take(40)?.takeIf { it.isNotBlank() },
        "electricityMeterReading" to electricityMeterReading?.trim()?.take(40)?.takeIf { it.isNotBlank() },
        "keysHandedOverCount" to keysHandedOverCount.coerceIn(0L, 50L),
        "rooms" to rooms.map { it.toMap() },
        "landlordSummary" to landlordSummary?.trim()?.take(800)?.takeIf { it.isNotBlank() },
        "tenantSummary" to tenantSummary?.trim()?.take(800)?.takeIf { it.isNotBlank() },
        "acknowledgedAtEpochMs" to acknowledgedAtEpochMs,
        "acknowledgedByUid" to acknowledgedByUid?.takeIf { it.isNotBlank() },
        "createdByUid" to createdByUid,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>

    companion object {
        const val MAX_ROOMS = 15
        const val MAX_ITEMS_PER_ROOM = 15

        fun defaultKenyanRooms(): List<InspectionRoom> = listOf(
            InspectionRoom(
                roomId = "room_living",
                roomName = "Living Room",
                items = listOf(
                    RoomInspectionItem("living_walls", "Walls, Paint & Ceiling", InspectionItemStatus.GOOD.name),
                    RoomInspectionItem("living_floor", "Flooring, Tiles & Skirting", InspectionItemStatus.GOOD.name),
                    RoomInspectionItem("living_windows", "Windows, Latches & Security Grilles", InspectionItemStatus.GOOD.name)
                )
            ),
            InspectionRoom(
                roomId = "room_kitchen",
                roomName = "Kitchen",
                items = listOf(
                    RoomInspectionItem("kitchen_sink", "Sink, Taps & Drainage Trap", InspectionItemStatus.GOOD.name),
                    RoomInspectionItem("kitchen_cabinets", "Cabinets, Worktop & Hinges", InspectionItemStatus.GOOD.name)
                )
            ),
            InspectionRoom(
                roomId = "room_bathroom",
                roomName = "Bathroom & Toilet",
                items = listOf(
                    RoomInspectionItem("bath_plumbing", "Shower Head, Mixer & Drainage", InspectionItemStatus.GOOD.name),
                    RoomInspectionItem("bath_toilet", "Toilet Cistern, Flush & Seat", InspectionItemStatus.GOOD.name)
                )
            ),
            InspectionRoom(
                roomId = "room_bedroom",
                roomName = "Bedroom",
                items = listOf(
                    RoomInspectionItem("bedroom_wardrobe", "Wardrobes, Doors & Handles", InspectionItemStatus.GOOD.name),
                    RoomInspectionItem("bedroom_lighting", "Lighting Fixtures & Wall Sockets", InspectionItemStatus.GOOD.name)
                )
            ),
            InspectionRoom(
                roomId = "room_utilities",
                roomName = "Utilities & Security",
                items = listOf(
                    RoomInspectionItem("utilities_power", "KPLC / Token Meter & Main Switch", InspectionItemStatus.GOOD.name),
                    RoomInspectionItem("security_locks", "Main Door Lock, Padlock Latch & Keys", InspectionItemStatus.GOOD.name)
                )
            )
        )
    }
}

@Keep
@IgnoreExtraProperties
data class DepositDeductionItem(
    val id: String = "",
    val category: String = DeductionCategory.DAMAGE_REPAIR.name,
    val description: String = "",
    val amountCents: Long = 0L
) {
    fun categoryEnum(): DeductionCategory = safeEnum(category, DeductionCategory.DAMAGE_REPAIR)

    fun toMap(): Map<String, Any> = mapOf(
        "id" to id.trim().take(64),
        "category" to categoryEnum().name,
        "description" to description.trim().take(200),
        "amountCents" to amountCents.coerceAtLeast(0L)
    )
}

@Keep
@IgnoreExtraProperties
data class DepositSettlement(
    @DocumentId val id: String = "",
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
    val depositHeldCents: Long = 0L,
    val arrearsIncludedCents: Long = 0L,
    val creditAppliedCents: Long = 0L,
    val deductions: List<DepositDeductionItem> = emptyList(),
    val deductionsTotalCents: Long = 0L,
    val netRefundCents: Long = 0L,
    val balanceOwedCents: Long = 0L,
    val moveOutDateEpochMs: Long = 0L,
    val waterMeterFinal: String? = null,
    val electricityMeterFinal: String? = null,
    val keysReturnedCount: Long = 0L,
    val imageRefs: List<String> = emptyList(),
    val status: String = DepositSettlementStatus.DRAFT.name,
    val landlordNotes: String? = null,
    val tenantResponseNote: String? = null,
    val refundMethod: String = RefundMethod.MPESA.name,
    val refundReferenceCode: String? = null,
    val settledAtEpochMs: Long? = null,
    val settledByUid: String? = null,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): DepositSettlementStatus = safeEnum(status, DepositSettlementStatus.DRAFT)
    fun refundMethodEnum(): RefundMethod = safeEnum(refundMethod, RefundMethod.MPESA)

    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
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
        "depositHeldCents" to depositHeldCents.coerceAtLeast(0L),
        "arrearsIncludedCents" to arrearsIncludedCents.coerceAtLeast(0L),
        "creditAppliedCents" to creditAppliedCents.coerceAtLeast(0L),
        "deductions" to deductions.map { it.toMap() },
        "deductionsTotalCents" to deductionsTotalCents.coerceAtLeast(0L),
        "netRefundCents" to netRefundCents.coerceAtLeast(0L),
        "balanceOwedCents" to balanceOwedCents.coerceAtLeast(0L),
        "moveOutDateEpochMs" to moveOutDateEpochMs,
        "waterMeterFinal" to waterMeterFinal?.trim()?.take(40)?.takeIf { it.isNotBlank() },
        "electricityMeterFinal" to electricityMeterFinal?.trim()?.take(40)?.takeIf { it.isNotBlank() },
        "keysReturnedCount" to keysReturnedCount.coerceIn(0L, 50L),
        "imageRefs" to imageRefs,
        "status" to status,
        "landlordNotes" to landlordNotes?.trim()?.take(800)?.takeIf { it.isNotBlank() },
        "tenantResponseNote" to tenantResponseNote?.trim()?.take(800)?.takeIf { it.isNotBlank() },
        "refundMethod" to refundMethod,
        "refundReferenceCode" to refundReferenceCode?.trim()?.uppercase()?.take(60)?.takeIf { it.isNotBlank() },
        "settledAtEpochMs" to settledAtEpochMs,
        "settledByUid" to settledByUid?.takeIf { it.isNotBlank() },
        "createdByUid" to createdByUid,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
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

