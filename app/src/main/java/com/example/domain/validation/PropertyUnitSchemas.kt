package com.example.domain.validation

import com.example.domain.model.Property
import com.example.domain.model.PropertyCategory
import com.example.domain.model.RentalUnit
import com.example.domain.model.UnitStatus
import com.example.domain.model.UnitType

object PropertyUnitSchemas {

    private val FIRESTORE_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")
    private val METER_NUMBER_REGEX = Regex("^[a-zA-Z0-9\\-_./ ]+$")
    private val UNIT_NUMBER_REGEX = Regex("^[a-zA-Z0-9\\-_./ ]+$")

    private val ALLOWED_PROPERTY_CATEGORIES = PropertyCategory.entries.map { it.name }.toSet()
    private val ALLOWED_UNIT_TYPES = UnitType.entries.map { it.name }.toSet()
    private val ALLOWED_UNIT_STATUSES = UnitStatus.entries.map { it.name }.toSet()

    const val MAX_MONEY_CENTS = 1_000_000_000L

    data class PropertyUpdateInput(
        val propertyId: String,
        val name: String,
        val category: String,
        val city: String,
        val county: String = "",
        val neighborhood: String = "",
        val addressLine: String = "",
        val description: String = "",
        val waterMeterNumber: String? = null,
        val electricityMeterNumber: String? = null,
        val notes: String? = null,
        val imageRefs: List<String> = emptyList(),
        val archived: Boolean = false
    )

    data class RentalUnitUpdateInput(
        val propertyId: String,
        val unitId: String,
        val unitNumber: String,
        val unitType: String,
        val rentCents: Long,
        val depositCents: Long,
        val status: String,
        val amenities: List<String> = emptyList(),
        val description: String = "",
        val imageRefs: List<String> = emptyList(),
        val published: Boolean = false
    )

    /**
     * Validates a [Property] write payload against Firestore security rule bounds and invariants.
     */
    val propertyCreateSchema: Schema<Property> = buildSchema { prop ->
        val cleanOrgId = stringField(
            field = "orgId",
            value = prop.orgId,
            minLen = 1,
            maxLen = 128,
            label = "Organization ID",
            pattern = FIRESTORE_ID_REGEX,
            patternMessage = "Organization ID contains invalid characters"
        )
        val cleanOwnerUid = stringField(
            field = "ownerUid",
            value = prop.ownerUid,
            minLen = 1,
            maxLen = 128,
            label = "Owner UID",
            pattern = FIRESTORE_ID_REGEX,
            patternMessage = "Owner UID contains invalid characters"
        )
        val cleanName = stringField("name", prop.name, minLen = 2, maxLen = 120, label = "Property name")
        val cleanCategory = oneOf("category", prop.category, ALLOWED_PROPERTY_CATEGORIES, label = "Property category")
        val cleanCity = stringField("city", prop.city, minLen = 2, maxLen = 60, label = "City")
        val cleanCounty = boundedZeroOrMoreString("county", prop.county, maxLen = 60, label = "County")

        val rawNeighborhood = prop.neighborhood.trim().ifBlank { cleanCity }
        val cleanNeighborhood = stringField("neighborhood", rawNeighborhood, minLen = 1, maxLen = 80, label = "Neighborhood")

        val rawAddress = prop.addressLine.trim().ifBlank { cleanCity }
        val cleanAddress = stringField("addressLine", rawAddress, minLen = 1, maxLen = 160, label = "Address line")

        val cleanDescription = boundedZeroOrMoreString("description", prop.description, maxLen = 1000, label = "Description")
        val cleanWaterMeter = optionalStringField(
            field = "waterMeterNumber",
            value = prop.waterMeterNumber,
            maxLen = 60,
            label = "Water meter number",
            pattern = METER_NUMBER_REGEX,
            patternMessage = "Water meter number may only contain letters, digits, spaces, '-', '_', '/', '.'"
        )
        val cleanElecMeter = optionalStringField(
            field = "electricityMeterNumber",
            value = prop.electricityMeterNumber,
            maxLen = 60,
            label = "Electricity meter number",
            pattern = METER_NUMBER_REGEX,
            patternMessage = "Electricity meter number may only contain letters, digits, spaces, '-', '_', '/', '.'"
        )
        val cleanNotes = optionalStringField("notes", prop.notes, maxLen = 1000, label = "Notes")

        val cleanManagers = stringList("managerUids", prop.managerUids, minSize = 0, maxSize = 15, itemMaxLen = 128, label = "Managers")
        val cleanAuthorized = stringList(
            field = "authorizedUids",
            values = prop.authorizedUids,
            minSize = 1,
            maxSize = 20,
            itemMaxLen = 128,
            label = "Authorized users"
        )
        val cleanImages = stringList("imageRefs", prop.imageRefs, minSize = 0, maxSize = 6, itemMaxLen = 500, label = "Images")

        refine(
            field = "authorizedUids",
            condition = cleanOwnerUid.isBlank() || cleanOwnerUid in cleanAuthorized,
            message = "Property owner must be included in authorized users"
        )
        longRange("totalUnitsCount", prop.totalUnitsCount, 0L, 10_000L, label = "Total units count")
        longRange("occupiedUnitsCount", prop.occupiedUnitsCount, 0L, 10_000L, label = "Occupied units count")
        refine(
            field = "occupiedUnitsCount",
            condition = prop.occupiedUnitsCount <= prop.totalUnitsCount,
            message = "Occupied units count cannot exceed total units count"
        )

        prop.copy(
            orgId = cleanOrgId,
            ownerUid = cleanOwnerUid,
            managerUids = cleanManagers,
            authorizedUids = cleanAuthorized,
            name = cleanName,
            category = cleanCategory,
            city = cleanCity,
            county = cleanCounty,
            neighborhood = cleanNeighborhood,
            addressLine = cleanAddress,
            description = cleanDescription,
            waterMeterNumber = cleanWaterMeter,
            electricityMeterNumber = cleanElecMeter,
            notes = cleanNotes,
            imageRefs = cleanImages
        )
    }

    val propertyUpdateSchema: Schema<PropertyUpdateInput> = buildSchema { input ->
        val cleanId = stringField(
            field = "propertyId",
            value = input.propertyId,
            minLen = 1,
            maxLen = 128,
            label = "Property ID",
            pattern = FIRESTORE_ID_REGEX,
            patternMessage = "Property ID contains invalid characters"
        )
        val cleanName = stringField("name", input.name, minLen = 2, maxLen = 120, label = "Property name")
        val cleanCategory = oneOf("category", input.category, ALLOWED_PROPERTY_CATEGORIES, label = "Property category")
        val cleanCity = stringField("city", input.city, minLen = 2, maxLen = 60, label = "City")
        val cleanCounty = boundedZeroOrMoreString("county", input.county, maxLen = 60, label = "County")

        val rawNeighborhood = input.neighborhood.trim().ifBlank { cleanCity }
        val cleanNeighborhood = stringField("neighborhood", rawNeighborhood, minLen = 1, maxLen = 80, label = "Neighborhood")

        val rawAddress = input.addressLine.trim().ifBlank { cleanCity }
        val cleanAddress = stringField("addressLine", rawAddress, minLen = 1, maxLen = 160, label = "Address line")

        val cleanDescription = boundedZeroOrMoreString("description", input.description, maxLen = 1000, label = "Description")
        val cleanWaterMeter = optionalStringField(
            field = "waterMeterNumber",
            value = input.waterMeterNumber,
            maxLen = 60,
            label = "Water meter number",
            pattern = METER_NUMBER_REGEX,
            patternMessage = "Water meter number contains invalid characters"
        )
        val cleanElecMeter = optionalStringField(
            field = "electricityMeterNumber",
            value = input.electricityMeterNumber,
            maxLen = 60,
            label = "Electricity meter number",
            pattern = METER_NUMBER_REGEX,
            patternMessage = "Electricity meter number contains invalid characters"
        )
        val cleanNotes = optionalStringField("notes", input.notes, maxLen = 1000, label = "Notes")
        val cleanImages = stringList("imageRefs", input.imageRefs, minSize = 0, maxSize = 6, itemMaxLen = 500, label = "Images")

        input.copy(
            propertyId = cleanId,
            name = cleanName,
            category = cleanCategory,
            city = cleanCity,
            county = cleanCounty,
            neighborhood = cleanNeighborhood,
            addressLine = cleanAddress,
            description = cleanDescription,
            waterMeterNumber = cleanWaterMeter,
            electricityMeterNumber = cleanElecMeter,
            notes = cleanNotes,
            imageRefs = cleanImages
        )
    }

    /**
     * Validates a [RentalUnit] create payload scoped to its parent property.
     */
    val unitCreateSchema: Schema<RentalUnit> = buildSchema { unit ->
        val cleanPropId = stringField(
            field = "propertyId",
            value = unit.propertyId,
            minLen = 1,
            maxLen = 128,
            label = "Parent property ID",
            pattern = FIRESTORE_ID_REGEX,
            patternMessage = "Parent property ID contains invalid characters"
        )
        val cleanPropName = stringField("propertyName", unit.propertyName, minLen = 1, maxLen = 120, label = "Property name")
        val cleanPropCity = stringField("propertyCity", unit.propertyCity, minLen = 1, maxLen = 60, label = "Property city")
        val cleanPropCat = oneOf("propertyCategory", unit.propertyCategory, ALLOWED_PROPERTY_CATEGORIES, label = "Property category")
        val cleanOrgId = stringField(
            field = "orgId",
            value = unit.orgId,
            minLen = 1,
            maxLen = 128,
            label = "Organization ID",
            pattern = FIRESTORE_ID_REGEX,
            patternMessage = "Organization ID contains invalid characters"
        )
        val cleanOwnerUid = stringField(
            field = "ownerUid",
            value = unit.ownerUid,
            minLen = 1,
            maxLen = 128,
            label = "Owner UID",
            pattern = FIRESTORE_ID_REGEX,
            patternMessage = "Owner UID contains invalid characters"
        )
        val cleanManagers = stringList("managerUids", unit.managerUids, minSize = 0, maxSize = 15, itemMaxLen = 128, label = "Managers")
        val cleanAuthorized = stringList("authorizedUids", unit.authorizedUids, minSize = 1, maxSize = 20, itemMaxLen = 128, label = "Authorized users")

        refine(
            field = "authorizedUids",
            condition = cleanOwnerUid.isBlank() || cleanOwnerUid in cleanAuthorized,
            message = "Owner UID must be included in authorized users"
        )

        val cleanUnitNumber = stringField(
            field = "unitNumber",
            value = unit.unitNumber,
            minLen = 1,
            maxLen = 40,
            label = "Unit number",
            pattern = UNIT_NUMBER_REGEX,
            patternMessage = "Unit number may only contain letters, digits, spaces, '-', '_', '/', '.'"
        )
        val cleanUnitType = oneOf("unitType", unit.unitType, ALLOWED_UNIT_TYPES, label = "Unit type")
        longRange("rentCents", unit.rentCents, 1L, MAX_MONEY_CENTS, label = "Monthly rent")
        longRange("depositCents", unit.depositCents, 0L, MAX_MONEY_CENTS, label = "Security deposit")
        val cleanStatus = oneOf("status", unit.status, ALLOWED_UNIT_STATUSES, label = "Unit status")
        val cleanAmenities = stringList("amenities", unit.amenities, minSize = 0, maxSize = 15, itemMaxLen = 60, label = "Amenities")
        val cleanDescription = boundedZeroOrMoreString("description", unit.description, maxLen = 1000, label = "Description")
        val cleanImages = stringList("imageRefs", unit.imageRefs, minSize = 0, maxSize = 6, itemMaxLen = 500, label = "Images")
        val cleanTenantUid = optionalStringField("currentTenantUid", unit.currentTenantUid, maxLen = 128, label = "Current tenant UID")
        val cleanLeaseId = optionalStringField("currentLeaseId", unit.currentLeaseId, maxLen = 128, label = "Current lease ID")

        val sanitizedPublished = unit.published && cleanStatus == UnitStatus.AVAILABLE.name
        refine(
            field = "published",
            condition = !(unit.published && cleanStatus != UnitStatus.AVAILABLE.name),
            message = "Only AVAILABLE units can be published to public listings"
        )

        unit.copy(
            propertyId = cleanPropId,
            propertyName = cleanPropName,
            propertyCity = cleanPropCity,
            propertyCategory = cleanPropCat,
            orgId = cleanOrgId,
            ownerUid = cleanOwnerUid,
            managerUids = cleanManagers,
            authorizedUids = cleanAuthorized,
            unitNumber = cleanUnitNumber,
            unitType = cleanUnitType,
            status = cleanStatus,
            amenities = cleanAmenities,
            description = cleanDescription,
            imageRefs = cleanImages,
            published = sanitizedPublished,
            currentTenantUid = cleanTenantUid,
            currentLeaseId = cleanLeaseId
        )
    }

    val unitUpdateSchema: Schema<RentalUnitUpdateInput> = buildSchema { input ->
        val cleanPropId = stringField(
            field = "propertyId",
            value = input.propertyId,
            minLen = 1,
            maxLen = 128,
            label = "Parent property ID",
            pattern = FIRESTORE_ID_REGEX,
            patternMessage = "Parent property ID contains invalid characters"
        )
        val cleanUnitId = stringField(
            field = "unitId",
            value = input.unitId,
            minLen = 1,
            maxLen = 128,
            label = "Unit ID",
            pattern = FIRESTORE_ID_REGEX,
            patternMessage = "Unit ID contains invalid characters"
        )
        val cleanUnitNumber = stringField(
            field = "unitNumber",
            value = input.unitNumber,
            minLen = 1,
            maxLen = 40,
            label = "Unit number",
            pattern = UNIT_NUMBER_REGEX,
            patternMessage = "Unit number may only contain letters, digits, spaces, '-', '_', '/', '.'"
        )
        val cleanUnitType = oneOf("unitType", input.unitType, ALLOWED_UNIT_TYPES, label = "Unit type")
        longRange("rentCents", input.rentCents, 1L, MAX_MONEY_CENTS, label = "Monthly rent")
        longRange("depositCents", input.depositCents, 0L, MAX_MONEY_CENTS, label = "Security deposit")
        val cleanStatus = oneOf("status", input.status, ALLOWED_UNIT_STATUSES, label = "Unit status")
        val cleanAmenities = stringList("amenities", input.amenities, minSize = 0, maxSize = 15, itemMaxLen = 60, label = "Amenities")
        val cleanDescription = boundedZeroOrMoreString("description", input.description, maxLen = 1000, label = "Description")
        val cleanImages = stringList("imageRefs", input.imageRefs, minSize = 0, maxSize = 6, itemMaxLen = 500, label = "Images")

        refine(
            field = "published",
            condition = !(input.published && cleanStatus != UnitStatus.AVAILABLE.name),
            message = "Only AVAILABLE units can be published to public listings"
        )

        input.copy(
            propertyId = cleanPropId,
            unitId = cleanUnitId,
            unitNumber = cleanUnitNumber,
            unitType = cleanUnitType,
            status = cleanStatus,
            amenities = cleanAmenities,
            description = cleanDescription,
            imageRefs = cleanImages,
            published = input.published && cleanStatus == UnitStatus.AVAILABLE.name
        )
    }
}
