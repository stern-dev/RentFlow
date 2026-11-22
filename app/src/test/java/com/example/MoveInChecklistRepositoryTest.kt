package com.example

import com.example.data.repository.MoveInChecklistRepository
import com.example.domain.model.InspectionItemStatus
import com.example.domain.model.InspectionRoom
import com.example.domain.model.MoveInChecklistStatus
import com.example.domain.model.MoveInConditionChecklist
import com.example.domain.model.RoomInspectionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveInChecklistRepositoryTest {

    private val samplePhotoDataUri = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8U"

    @Test
    fun `defaultKenyanRooms provides structured grouped rooms and default GOOD statuses`() {
        val rooms = MoveInConditionChecklist.defaultKenyanRooms()
        assertEquals(5, rooms.size)
        assertTrue(rooms.any { it.roomName == "Living Room" })
        assertTrue(rooms.any { it.roomName == "Kitchen" })
        assertTrue(rooms.any { it.roomName == "Bathroom & Toilet" })
        assertTrue(rooms.any { it.roomName == "Bedroom" })
        assertTrue(rooms.any { it.roomName == "Utilities & Security" })

        val totalItems = rooms.sumOf { it.items.size }
        assertTrue(totalItems >= 10)
        assertTrue(rooms.flatMap { it.items }.all { it.statusEnum() == InspectionItemStatus.GOOD })
    }

    @Test
    fun `toCreateMap aligns with Firestore schema fields and excludes null optionals`() {
        val checklist = MoveInConditionChecklist(
            id = "mcc_lease_1",
            leaseId = "lease_1",
            orgId = "org_1",
            propertyId = "prop_1",
            propertyName = "Kilimani Ridge",
            unitId = "unit_1",
            unitNumber = "A1",
            ownerUid = "owner_1",
            managerUids = listOf("mgr_1"),
            authorizedUids = listOf("owner_1", "mgr_1"),
            tenantUid = "tenant_1",
            tenantName = "Brian Ochieng",
            status = MoveInChecklistStatus.DRAFT.name,
            inspectionDateEpochMs = 1_760_000_000_000L,
            waterMeterReading = "WM-2201",
            electricityMeterReading = null,
            keysHandedOverCount = 3L,
            rooms = listOf(
                InspectionRoom(
                    roomId = "room_living",
                    roomName = "Living Room",
                    roomRemarks = "Fresh coat of paint",
                    items = listOf(
                        RoomInspectionItem(
                            itemId = "living_walls",
                            label = "Walls & Ceiling",
                            status = InspectionItemStatus.EXCELLENT.name,
                            imageRefs = listOf(samplePhotoDataUri),
                            landlordRemarks = "No marks",
                            tenantRemarks = ""
                        )
                    )
                )
            ),
            landlordSummary = "Ready for handover",
            createdByUid = "owner_1"
        )

        assertEquals("mcc_lease_1", MoveInChecklistRepository.checklistDocIdForLease("lease_1"))
        assertEquals(1, checklist.totalItemsCount())
        assertEquals(1, checklist.totalPhotoCount())

        val map = checklist.toCreateMap()
        assertEquals("lease_1", map["leaseId"])
        assertEquals("WM-2201", map["waterMeterReading"])
        assertFalse(map.containsKey("electricityMeterReading"))
        assertFalse(map.containsKey("acknowledgedAtEpochMs"))
        assertFalse(map.containsKey("id"))

        @Suppress("UNCHECKED_CAST")
        val roomsList = map["rooms"] as List<Map<String, Any>>
        assertEquals(1, roomsList.size)
        assertEquals("Living Room", roomsList[0]["roomName"])
        @Suppress("UNCHECKED_CAST")
        val itemsList = roomsList[0]["items"] as List<Map<String, Any>>
        assertEquals("EXCELLENT", itemsList[0]["status"])
        assertEquals(listOf(samplePhotoDataUri), itemsList[0]["imageRefs"])
    }

    @Test
    fun `sanitizeAndValidateRooms enforces max 2 photos per item, data URIs, and total payload budget`() {
        val validRooms = listOf(
            InspectionRoom(
                roomId = "room_1",
                roomName = "Kitchen",
                items = listOf(
                    RoomInspectionItem(
                        itemId = "sink_1",
                        label = "Kitchen Sink & Taps",
                        status = InspectionItemStatus.FAIR.name,
                        imageRefs = listOf(samplePhotoDataUri, samplePhotoDataUri),
                        landlordRemarks = "Minor scratch on drainer"
                    )
                )
            )
        )
        val okResult = MoveInChecklistRepository.sanitizeAndValidateRooms(validRooms)
        assertTrue(okResult.isSuccess)
        assertEquals(2, okResult.getOrThrow()[0].items[0].imageRefs.size)

        // >2 photos per item must fail
        val tooManyItemPhotos = listOf(
            InspectionRoom(
                roomId = "room_1",
                roomName = "Kitchen",
                items = listOf(
                    RoomInspectionItem(
                        itemId = "sink_1",
                        label = "Kitchen Sink & Taps",
                        imageRefs = listOf(samplePhotoDataUri, samplePhotoDataUri, samplePhotoDataUri)
                    )
                )
            )
        )
        assertTrue(MoveInChecklistRepository.sanitizeAndValidateRooms(tooManyItemPhotos).isFailure)

        // Non-data-URI ref must fail
        val nonDataUri = listOf(
            InspectionRoom(
                roomId = "room_1",
                roomName = "Kitchen",
                items = listOf(
                    RoomInspectionItem(
                        itemId = "sink_1",
                        label = "Kitchen Sink & Taps",
                        imageRefs = listOf("https://example.com/photo.jpg")
                    )
                )
            )
        )
        assertTrue(MoveInChecklistRepository.sanitizeAndValidateRooms(nonDataUri).isFailure)

        // Total payload > MAX_TOTAL_DOCUMENT_PHOTO_CHARS across rooms must fail
        val largePhoto = "data:image/jpeg;base64," + "A".repeat(220_000)
        val oversizeChecklist = listOf(
            InspectionRoom(
                roomId = "r1",
                roomName = "Living Room",
                items = listOf(
                    RoomInspectionItem("i1", "Walls", imageRefs = listOf(largePhoto, largePhoto)),
                    RoomInspectionItem("i2", "Floor", imageRefs = listOf(largePhoto, largePhoto))
                )
            )
        )
        assertTrue(MoveInChecklistRepository.sanitizeAndValidateRooms(oversizeChecklist).isFailure)

        // Empty rooms or blank labels must fail
        assertTrue(MoveInChecklistRepository.sanitizeAndValidateRooms(emptyList()).isFailure)
        assertTrue(
            MoveInChecklistRepository.sanitizeAndValidateRooms(
                listOf(InspectionRoom("r1", "", items = listOf(RoomInspectionItem("i1", "Door"))))
            ).isFailure
        )
    }

    @Test
    fun `mergeTenantRemarksPreservingLandlordFields updates only tenantRemarks`() {
        val existing = listOf(
            InspectionRoom(
                roomId = "room_bath",
                roomName = "Bathroom",
                roomRemarks = "Checked water pressure",
                items = listOf(
                    RoomInspectionItem(
                        itemId = "bath_mirror",
                        label = "Wall Mirror",
                        status = InspectionItemStatus.GOOD.name,
                        imageRefs = listOf(samplePhotoDataUri),
                        landlordRemarks = "Clean glass",
                        tenantRemarks = ""
                    ),
                    RoomInspectionItem(
                        itemId = "bath_tap",
                        label = "Basin Tap",
                        status = InspectionItemStatus.FAIR.name,
                        imageRefs = emptyList(),
                        landlordRemarks = "Slight drip when loose",
                        tenantRemarks = ""
                    )
                )
            )
        )

        val merged = MoveInChecklistRepository.mergeTenantRemarksPreservingLandlordFields(
            existingRooms = existing,
            tenantRemarksByItemId = mapOf(
                "bath_mirror" to "  Small hairline crack at bottom right corner.  "
            )
        )

        assertEquals(1, merged.size)
        val mirror = merged[0].items[0]
        assertEquals("Wall Mirror", mirror.label)
        assertEquals(InspectionItemStatus.GOOD.name, mirror.status)
        assertEquals(listOf(samplePhotoDataUri), mirror.imageRefs)
        assertEquals("Clean glass", mirror.landlordRemarks)
        assertEquals("Small hairline crack at bottom right corner.", mirror.tenantRemarks)

        val tap = merged[0].items[1]
        assertEquals("Slight drip when loose", tap.landlordRemarks)
        assertEquals("", tap.tenantRemarks)
    }
}
