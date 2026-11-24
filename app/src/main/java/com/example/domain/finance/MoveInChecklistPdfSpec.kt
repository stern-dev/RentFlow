package com.example.domain.finance

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.domain.model.InspectionItemStatus
import com.example.domain.model.Lease
import com.example.domain.model.MoveInChecklistStatus
import com.example.domain.model.MoveInConditionChecklist
import com.example.domain.model.RoomInspectionItem
import java.io.File

data class GeneratedChecklistPdf(
    val file: File,
    val title: String,
    val subtitle: String,
    val isFinalizedSigned: Boolean,
    val statusLabel: String,
    val pageCount: Int,
    val generatedAtEpochMs: Long = System.currentTimeMillis()
)

data class ChecklistPdfMetadataPair(
    val label: String,
    val value: String
)

data class ChecklistPdfItemRow(
    val itemId: String,
    val label: String,
    val status: InspectionItemStatus,
    val landlordRemarks: String,
    val tenantRemarks: String,
    val photoDataUris: List<String>
)

data class ChecklistPdfRoomSection(
    val roomId: String,
    val roomName: String,
    val roomRemarks: String,
    val items: List<ChecklistPdfItemRow>
)

data class ChecklistPdfDocumentSpec(
    val title: String,
    val subtitle: String,
    val isFinalizedSigned: Boolean,
    val watermarkText: String?,
    val statusLabel: String,
    val suggestedFileName: String,
    val totalItems: Int,
    val flaggedIssues: Int,
    val totalPhotos: Int,
    val metadataRows: List<ChecklistPdfMetadataPair>,
    val leaseSummaryLine: String?,
    val roomSections: List<ChecklistPdfRoomSection>,
    val overallLandlordSummary: String?,
    val overallTenantSummary: String?,
    val signatureBlocks: List<PdfSignatureBlock>
)

object MoveInChecklistPdfSpecBuilder {

    const val DRAFT_WATERMARK_TEXT = "DRAFT / UNSIGNED PREVIEW"

    fun buildSpec(
        checklist: MoveInConditionChecklist,
        lease: Lease? = null
    ): ChecklistPdfDocumentSpec {
        val landlordSig = checklist.landlordSignature ?: lease?.landlordSignature
        val tenantSig = checklist.tenantSignature ?: lease?.tenantSignature
        val statusEnum = checklist.statusEnum()
        val isFinalizedSigned =
            statusEnum == MoveInChecklistStatus.ACKNOWLEDGED &&
                landlordSig != null &&
                tenantSig != null

        val watermarkText = if (isFinalizedSigned) null else DRAFT_WATERMARK_TEXT
        val statusBadgeLabel = if (isFinalizedSigned) {
            "FINALIZED & SIGNED"
        } else {
            "DRAFT • ${statusEnum.label.uppercase()}"
        }

        val propertyDisplay = checklist.propertyName.ifBlank { lease?.propertyName.orEmpty() }.ifBlank { "Property" }
        val unitDisplay = checklist.unitNumber.ifBlank { lease?.unitNumber.orEmpty() }.ifBlank { "Unit" }
        val tenantDisplay = checklist.tenantName.ifBlank { lease?.tenantName.orEmpty() }.ifBlank { "Tenant" }

        val metadata = buildList {
            add(ChecklistPdfMetadataPair("Property & Unit", "$propertyDisplay • Unit $unitDisplay"))
            add(ChecklistPdfMetadataPair("Tenant", tenantDisplay))
            add(
                ChecklistPdfMetadataPair(
                    "Inspection Date",
                    checklist.inspectionDateEpochMs.takeIf { it > 0L }?.let { CurrencyUtils.formatDate(it) }
                        ?: "Pending Inspection Date"
                )
            )
            add(ChecklistPdfMetadataPair("Checklist Status", statusEnum.label))
            add(ChecklistPdfMetadataPair("Keys Handed Over", "${checklist.keysHandedOverCount} key(s)"))
            add(
                ChecklistPdfMetadataPair(
                    "Water Meter",
                    checklist.waterMeterReading?.takeIf { it.isNotBlank() } ?: "Not Recorded"
                )
            )
            add(
                ChecklistPdfMetadataPair(
                    "Power / KPLC Meter",
                    checklist.electricityMeterReading?.takeIf { it.isNotBlank() } ?: "Not Recorded"
                )
            )
            val ackEpoch = checklist.acknowledgedAtEpochMs
            if (ackEpoch != null && ackEpoch > 0L) {
                add(ChecklistPdfMetadataPair("Tenant Executed", CurrencyUtils.formatDateTime(ackEpoch)))
            }
        }

        val leaseSummary = lease?.let { linked ->
            buildString {
                append("Linked Lease: ")
                append("${CurrencyUtils.formatKes(linked.rentCents, false)}/mo")
                append(" • Deposit ${CurrencyUtils.formatKes(linked.depositCents, false)}")
                append(" • Due Day ${linked.dueDayOfMonth}")
                if (linked.startDateEpochMs > 0L && linked.endDateEpochMs > 0L) {
                    append(" • Term ${CurrencyUtils.formatDate(linked.startDateEpochMs)} – ${CurrencyUtils.formatDate(linked.endDateEpochMs)}")
                }
            }
        }

        val roomSections = checklist.rooms.map { room ->
            ChecklistPdfRoomSection(
                roomId = room.roomId,
                roomName = room.roomName.ifBlank { "Room" },
                roomRemarks = room.roomRemarks.trim(),
                items = room.items.map { item ->
                    ChecklistPdfItemRow(
                        itemId = item.itemId,
                        label = item.label.ifBlank { "Inspection Item" },
                        status = item.statusEnum(),
                        landlordRemarks = item.landlordRemarks.trim(),
                        tenantRemarks = item.tenantRemarks.trim(),
                        photoDataUris = item.imageRefs
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .take(RoomInspectionItem.MAX_PHOTOS_PER_ITEM)
                    )
                }
            )
        }

        val allItems = roomSections.flatMap { it.items }
        val flaggedCount = allItems.count { item ->
            item.status == InspectionItemStatus.NEEDS_REPAIR ||
                item.status == InspectionItemStatus.DAMAGED ||
                item.status == InspectionItemStatus.MISSING
        }
        val totalPhotos = allItems.sumOf { it.photoDataUris.size }

        val safeUnitSlug = unitDisplay
            .lowercase()
            .replace(Regex("[^a-z0-9_-]"), "_")
            .trim('_')
            .ifBlank { "unit" }
        val fileSuffix = if (isFinalizedSigned) "signed" else "draft"

        return ChecklistPdfDocumentSpec(
            title = "RentFlow Move-In Condition Report",
            subtitle = "$propertyDisplay • Unit $unitDisplay",
            isFinalizedSigned = isFinalizedSigned,
            watermarkText = watermarkText,
            statusLabel = statusBadgeLabel,
            suggestedFileName = "room_move_in_checklist_${safeUnitSlug}_$fileSuffix.pdf",
            totalItems = allItems.size,
            flaggedIssues = flaggedCount,
            totalPhotos = totalPhotos,
            metadataRows = metadata,
            leaseSummaryLine = leaseSummary,
            roomSections = roomSections,
            overallLandlordSummary = checklist.landlordSummary?.trim()?.takeIf { it.isNotBlank() },
            overallTenantSummary = checklist.tenantSummary?.trim()?.takeIf { it.isNotBlank() },
            signatureBlocks = listOf(
                PdfSignatureBlock("LANDLORD / AUTHORIZED AGENT", landlordSig),
                PdfSignatureBlock("TENANT", tenantSig)
            )
        )
    }

    internal fun decodeInlineImageDataUri(
        dataUri: String,
        maxDimensionPx: Int = 420
    ): Bitmap? {
        val trimmed = dataUri.trim()
        val marker = ";base64,"
        val idx = trimmed.indexOf(marker, ignoreCase = true)
        if (!trimmed.startsWith("data:image/", ignoreCase = true) || idx < 0) return null
        val payload = trimmed.substring(idx + marker.length)
        if (payload.isBlank()) return null
        return runCatching {
            val bytes = Base64.decode(payload, Base64.DEFAULT)
            if (bytes.isEmpty()) return null
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            val width = boundsOpts.outWidth
            val height = boundsOpts.outHeight
            if (width <= 0 || height <= 0) {
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            var sampleSize = 1
            while ((width / sampleSize) > maxDimensionPx * 2 || (height / sampleSize) > maxDimensionPx * 2) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        }.getOrNull()
    }
}
