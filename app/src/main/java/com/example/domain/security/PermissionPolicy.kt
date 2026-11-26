package com.example.domain.security

import com.example.domain.model.Expense
import com.example.domain.model.Lease
import com.example.domain.model.Organization
import com.example.domain.model.Payment
import com.example.domain.model.Property
import com.example.domain.model.RentalUnit
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole

/**
 * Centralized Role-Based Access Control (RBAC) policy matching Firestore security rules.
 */
object PermissionPolicy {

    fun assignableRolesForProfile(
        profile: UserProfile?,
        isBootstrappedAdmin: Boolean
    ): List<UserRole> {
        if (profile == null) return emptyList()
        val granted = profile.roleEnums().distinct()
        return granted.filter { role ->
            role != UserRole.ADMIN || isBootstrappedAdmin
        }
    }

    fun canSwitchToRole(
        profile: UserProfile?,
        targetRole: UserRole,
        isBootstrappedAdmin: Boolean
    ): Boolean {
        if (profile == null) return false
        if (targetRole == UserRole.ADMIN && !isBootstrappedAdmin) return false
        return targetRole.name in profile.roles
    }

    fun isOrgOwner(uid: String, org: Organization?): Boolean =
        uid.isNotBlank() && org != null && org.ownerUid == uid

    fun isPropertyOwner(uid: String, property: Property?): Boolean =
        uid.isNotBlank() && property != null && property.ownerUid == uid

    fun canInviteTeamMembers(uid: String, activeRole: UserRole, org: Organization?): Boolean =
        activeRole == UserRole.ADMIN || (activeRole == UserRole.LANDLORD && isOrgOwner(uid, org))

    fun canManagePropertyStructure(uid: String, activeRole: UserRole, property: Property): Boolean =
        activeRole == UserRole.ADMIN || (activeRole == UserRole.LANDLORD && property.ownerUid == uid)

    fun canAssignPropertyManager(uid: String, activeRole: UserRole, property: Property): Boolean =
        canManagePropertyStructure(uid, activeRole, property)

    fun canDeleteProperty(uid: String, activeRole: UserRole, property: Property): Boolean =
        canManagePropertyStructure(uid, activeRole, property)

    fun canEditUnitPricingOrDelete(uid: String, activeRole: UserRole, unit: RentalUnit): Boolean =
        activeRole == UserRole.ADMIN || (activeRole == UserRole.LANDLORD && unit.ownerUid == uid)

    fun canCreateUnit(uid: String, activeRole: UserRole, property: Property): Boolean =
        activeRole == UserRole.ADMIN || (activeRole == UserRole.LANDLORD && property.ownerUid == uid)

    fun canTerminateLease(uid: String, activeRole: UserRole, lease: Lease): Boolean =
        activeRole == UserRole.ADMIN || (activeRole == UserRole.LANDLORD && lease.ownerUid == uid)

    fun canEditLeaseTerms(uid: String, activeRole: UserRole, lease: Lease): Boolean =
        activeRole == UserRole.ADMIN || (activeRole == UserRole.LANDLORD && lease.ownerUid == uid)

    fun canReversePayment(uid: String, activeRole: UserRole, payment: Payment): Boolean =
        activeRole == UserRole.ADMIN || (activeRole == UserRole.LANDLORD && payment.ownerUid == uid)

    fun canDeleteExpense(uid: String, activeRole: UserRole, expense: Expense): Boolean =
        activeRole == UserRole.ADMIN || (activeRole == UserRole.LANDLORD && expense.ownerUid == uid)

    fun canManageLeaseChecklist(uid: String, activeRole: UserRole, lease: Lease): Boolean =
        activeRole == UserRole.ADMIN ||
            (activeRole in listOf(UserRole.LANDLORD, UserRole.PROPERTY_MANAGER) && uid in lease.authorizedUids)

    fun canManageDepositSettlement(uid: String, activeRole: UserRole, lease: Lease): Boolean =
        activeRole == UserRole.ADMIN ||
            (activeRole in listOf(UserRole.LANDLORD, UserRole.PROPERTY_MANAGER) && uid in lease.authorizedUids)

    fun canFinalizeDepositSettlement(uid: String, activeRole: UserRole, lease: Lease): Boolean =
        activeRole == UserRole.ADMIN ||
            (activeRole in listOf(UserRole.LANDLORD, UserRole.PROPERTY_MANAGER) && uid in lease.authorizedUids)

    fun canEditLeaseAutomationPricing(uid: String, activeRole: UserRole, lease: Lease): Boolean =
        activeRole == UserRole.ADMIN || (activeRole == UserRole.LANDLORD && lease.ownerUid == uid)

    fun canRunLeaseRenewalSweep(uid: String, activeRole: UserRole, lease: Lease): Boolean =
        activeRole == UserRole.ADMIN ||
            (activeRole in listOf(UserRole.LANDLORD, UserRole.PROPERTY_MANAGER) && uid in lease.authorizedUids)

    fun canRunLateFeeSweep(uid: String, activeRole: UserRole, lease: Lease): Boolean =
        activeRole == UserRole.ADMIN ||
            (activeRole in listOf(UserRole.LANDLORD, UserRole.PROPERTY_MANAGER) && uid in lease.authorizedUids)

    fun canWaiveLateFee(uid: String, activeRole: UserRole, ownerUid: String): Boolean =
        activeRole == UserRole.ADMIN || (activeRole == UserRole.LANDLORD && ownerUid == uid)
}
