package com.example.domain.model

enum class ChecklistOverviewBucket(val label: String) {
    PENDING("Pending"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed")
}

enum class ChecklistWorkflowStage(
    val label: String,
    val bucket: ChecklistOverviewBucket,
    val priorityOrder: Int
) {
    DISPUTED("Discrepancy Flagged", ChecklistOverviewBucket.IN_PROGRESS, 0),
    PENDING_START("Pending Start", ChecklistOverviewBucket.PENDING, 1),
    DRAFT("Draft", ChecklistOverviewBucket.IN_PROGRESS, 2),
    PENDING_TENANT_SIGNOFF("Pending Sign-Off", ChecklistOverviewBucket.PENDING, 3),
    COMPLETED("Signed & Locked", ChecklistOverviewBucket.COMPLETED, 4)
}

data class ChecklistOverviewItem(
    val lease: Lease,
    val checklist: MoveInConditionChecklist?,
    val stage: ChecklistWorkflowStage,
    val bucket: ChecklistOverviewBucket,
    val propertyId: String,
    val propertyName: String,
    val unitNumber: String,
    val tenantName: String,
    val roomCount: Int,
    val totalItems: Int,
    val flaggedItems: Int,
    val photoCount: Int,
    val landlordSigned: Boolean,
    val tenantSigned: Boolean,
    val sortTimestampEpochMs: Long
)

data class ChecklistOverviewSummary(
    val items: List<ChecklistOverviewItem> = emptyList(),
    val pendingCount: Int = 0,
    val inProgressCount: Int = 0,
    val completedCount: Int = 0,
    val pendingStartCount: Int = 0,
    val awaitingTenantCount: Int = 0,
    val draftCount: Int = 0,
    val disputedCount: Int = 0,
    val completionRatePercent: Int = 0,
    val propertiesWithItems: List<Pair<String, String>> = emptyList()
) {
    val totalCount: Int get() = items.size
    val attentionCount: Int get() = pendingCount + inProgressCount
}

object ChecklistOverviewEngine {

    fun resolveStage(
        checklist: MoveInConditionChecklist?,
        lease: Lease? = null
    ): ChecklistWorkflowStage {
        if (checklist == null) return ChecklistWorkflowStage.PENDING_START
        val landlordSigned = checklist.landlordSignature != null || lease?.landlordSignature != null
        val tenantSigned = checklist.tenantSignature != null || lease?.tenantSignature != null
        if (checklist.statusEnum() == MoveInChecklistStatus.ACKNOWLEDGED || (landlordSigned && tenantSigned)) {
            return ChecklistWorkflowStage.COMPLETED
        }
        return when (checklist.statusEnum()) {
            MoveInChecklistStatus.DISPUTED -> ChecklistWorkflowStage.DISPUTED
            MoveInChecklistStatus.DRAFT -> ChecklistWorkflowStage.DRAFT
            MoveInChecklistStatus.PENDING_TENANT_REVIEW -> ChecklistWorkflowStage.PENDING_TENANT_SIGNOFF
            MoveInChecklistStatus.ACKNOWLEDGED -> ChecklistWorkflowStage.COMPLETED
        }
    }

    fun buildSummary(
        leases: List<Lease>,
        checklists: List<MoveInConditionChecklist>
    ): ChecklistOverviewSummary {
        val checklistsByLeaseId = checklists
            .filter { it.leaseId.isNotBlank() }
            .sortedByDescending { it.updatedAt?.seconds ?: it.inspectionDateEpochMs }
            .associateBy { it.leaseId }

        val coveredLeaseIds = mutableSetOf<String>()
        val overviewItems = mutableListOf<ChecklistOverviewItem>()

        leases.forEach { lease ->
            val matchedChecklist = checklistsByLeaseId[lease.id]
            val leaseStatus = lease.statusEnum()
            val isActiveOrRenewed = leaseStatus == LeaseStatus.ACTIVE || leaseStatus == LeaseStatus.RENEWED
            if (isActiveOrRenewed || matchedChecklist != null) {
                coveredLeaseIds += lease.id
                overviewItems += createOverviewItem(lease = lease, checklist = matchedChecklist)
            }
        }

        // Include any checklist whose lease isn't currently in the leases collection snapshot
        checklists.forEach { checklist ->
            if (checklist.leaseId !in coveredLeaseIds) {
                val syntheticLease = Lease(
                    id = checklist.leaseId.ifBlank { checklist.id },
                    orgId = checklist.orgId,
                    propertyId = checklist.propertyId,
                    propertyName = checklist.propertyName,
                    unitId = checklist.unitId,
                    unitNumber = checklist.unitNumber,
                    ownerUid = checklist.ownerUid,
                    managerUids = checklist.managerUids,
                    authorizedUids = checklist.authorizedUids,
                    tenantUid = checklist.tenantUid,
                    tenantName = checklist.tenantName,
                    landlordSignature = checklist.landlordSignature,
                    tenantSignature = checklist.tenantSignature,
                    signedChecklistId = checklist.id
                )
                overviewItems += createOverviewItem(lease = syntheticLease, checklist = checklist)
            }
        }

        val sorted = overviewItems.sortedWith(
            compareBy<ChecklistOverviewItem> { it.stage.priorityOrder }
                .thenByDescending { it.sortTimestampEpochMs }
                .thenBy { it.propertyName.lowercase() }
                .thenBy { it.unitNumber.lowercase() }
        )

        val pendingCount = sorted.count { it.bucket == ChecklistOverviewBucket.PENDING }
        val inProgressCount = sorted.count { it.bucket == ChecklistOverviewBucket.IN_PROGRESS }
        val completedCount = sorted.count { it.bucket == ChecklistOverviewBucket.COMPLETED }
        val completionPercent = if (sorted.isNotEmpty()) {
            ((completedCount * 100f) / sorted.size.toFloat()).toInt().coerceIn(0, 100)
        } else {
            0
        }

        val properties = sorted
            .filter { it.propertyId.isNotBlank() && it.propertyName.isNotBlank() }
            .distinctBy { it.propertyId }
            .map { it.propertyId to it.propertyName }
            .sortedBy { it.second.lowercase() }

        return ChecklistOverviewSummary(
            items = sorted,
            pendingCount = pendingCount,
            inProgressCount = inProgressCount,
            completedCount = completedCount,
            pendingStartCount = sorted.count { it.stage == ChecklistWorkflowStage.PENDING_START },
            awaitingTenantCount = sorted.count { it.stage == ChecklistWorkflowStage.PENDING_TENANT_SIGNOFF },
            draftCount = sorted.count { it.stage == ChecklistWorkflowStage.DRAFT },
            disputedCount = sorted.count { it.stage == ChecklistWorkflowStage.DISPUTED },
            completionRatePercent = completionPercent,
            propertiesWithItems = properties
        )
    }

    fun filterItems(
        summary: ChecklistOverviewSummary,
        bucketFilter: ChecklistOverviewBucket? = null,
        propertyIdFilter: String? = null,
        searchQuery: String = ""
    ): List<ChecklistOverviewItem> {
        val cleanQuery = searchQuery.trim().lowercase()
        return summary.items.filter { item ->
            val matchesBucket = bucketFilter == null || item.bucket == bucketFilter
            val matchesProp = propertyIdFilter.isNullOrBlank() || item.propertyId == propertyIdFilter
            val matchesQuery = cleanQuery.isEmpty() ||
                item.propertyName.lowercase().contains(cleanQuery) ||
                item.unitNumber.lowercase().contains(cleanQuery) ||
                item.tenantName.lowercase().contains(cleanQuery) ||
                item.stage.label.lowercase().contains(cleanQuery)
            matchesBucket && matchesProp && matchesQuery
        }
    }

    private fun createOverviewItem(
        lease: Lease,
        checklist: MoveInConditionChecklist?
    ): ChecklistOverviewItem {
        val stage = resolveStage(checklist, lease)
        val allItems = checklist?.rooms?.flatMap { it.items }.orEmpty()
        val flaggedCount = allItems.count {
            val st = it.statusEnum()
            st == InspectionItemStatus.NEEDS_REPAIR ||
                st == InspectionItemStatus.DAMAGED ||
                st == InspectionItemStatus.MISSING
        }
        val landlordSigned = checklist?.landlordSignature != null || lease.landlordSignature != null
        val tenantSigned = checklist?.tenantSignature != null || lease.tenantSignature != null
        val sortTs = checklist?.acknowledgedAtEpochMs
            ?: checklist?.updatedAt?.let { it.seconds * 1000L }
            ?: checklist?.inspectionDateEpochMs?.takeIf { it > 0L }
            ?: lease.startDateEpochMs

        return ChecklistOverviewItem(
            lease = lease,
            checklist = checklist,
            stage = stage,
            bucket = stage.bucket,
            propertyId = checklist?.propertyId?.ifBlank { lease.propertyId } ?: lease.propertyId,
            propertyName = checklist?.propertyName?.ifBlank { lease.propertyName } ?: lease.propertyName,
            unitNumber = checklist?.unitNumber?.ifBlank { lease.unitNumber } ?: lease.unitNumber,
            tenantName = checklist?.tenantName?.ifBlank { lease.tenantName } ?: lease.tenantName,
            roomCount = checklist?.rooms?.size ?: 0,
            totalItems = allItems.size,
            flaggedItems = flaggedCount,
            photoCount = checklist?.totalPhotoCount() ?: 0,
            landlordSigned = landlordSigned,
            tenantSigned = tenantSigned,
            sortTimestampEpochMs = sortTs
        )
    }
}
