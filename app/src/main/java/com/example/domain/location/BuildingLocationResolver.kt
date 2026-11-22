package com.example.domain.location

import com.example.domain.model.Property
import com.example.domain.model.RentalUnit
import java.util.Locale

data class ResolvedBuildingLocation(
    val latitude: Double,
    val longitude: Double,
    val isExactPin: Boolean,
    val addressSummary: String,
    val sourceLabel: String
)

data class BuildingPinGroup(
    val propertyId: String,
    val propertyName: String,
    val city: String,
    val neighborhood: String,
    val addressLine: String,
    val location: ResolvedBuildingLocation,
    val landlordVerified: Boolean,
    val units: List<RentalUnit>,
    val minRentCents: Long,
    val maxRentCents: Long
)

data class KenyaLocationPreset(
    val label: String,
    val city: String,
    val county: String,
    val neighborhood: String,
    val latitude: Double,
    val longitude: Double
)

object BuildingLocationResolver {

    val DEFAULT_NAIROBI = -1.2921 to 36.8219

    private val CITY_AND_COUNTY_CENTROIDS: Map<String, Pair<Double, Double>> = mapOf(
        "nairobi" to (-1.2921 to 36.8219),
        "westlands" to (-1.2673 to 36.8111),
        "kilimani" to (-1.2896 to 36.7831),
        "kileleshwa" to (-1.2810 to 36.7798),
        "karen" to (-1.3189 to 36.7115),
        "langata" to (-1.3563 to 36.7429),
        "kasarani" to (-1.2219 to 36.8967),
        "roysambu" to (-1.2186 to 36.8866),
        "embakasi" to (-1.3219 to 36.9001),
        "south b" to (-1.3113 to 36.8342),
        "south c" to (-1.3193 to 36.8257),
        "mombasa" to (-4.0435 to 39.6682),
        "nyali" to (-4.0273 to 39.7087),
        "bamburi" to (-3.9977 to 39.7235),
        "likoni" to (-4.0846 to 39.6641),
        "kisumu" to (-0.0917 to 34.7680),
        "milimani" to (-0.1055 to 34.7568),
        "nakuru" to (-0.3031 to 36.0800),
        "eldoret" to (0.5143 to 35.2698),
        "uasin gishu" to (0.5143 to 35.2698),
        "kiambu" to (-1.1714 to 36.8356),
        "thika" to (-1.0332 to 37.0693),
        "ruiru" to (-1.1459 to 36.9597),
        "juja" to (-1.1026 to 37.0144),
        "kikuyu" to (-1.2464 to 36.6634),
        "machakos" to (-1.5177 to 37.2634),
        "syokimau" to (-1.3578 to 36.9437),
        "athi river" to (-1.4564 to 36.9783),
        "mlolongo" to (-1.3910 to 36.9438),
        "kajiado" to (-1.8524 to 36.7768),
        "kitengela" to (-1.4744 to 36.9598),
        "rongai" to (-1.3970 to 36.7443),
        "ongata rongai" to (-1.3970 to 36.7443),
        "ngong" to (-1.3618 to 36.6565),
        "kilifi" to (-3.6305 to 39.8499),
        "malindi" to (-3.2192 to 40.1169),
        "watamu" to (-3.3547 to 40.0176),
        "kwale" to (-4.1737 to 39.4521),
        "diani" to (-4.3168 to 39.5754),
        "nyeri" to (-0.4197 to 36.9553),
        "meru" to (0.0470 to 37.6559),
        "laikipia" to (0.0167 to 37.0740),
        "nanyuki" to (0.0167 to 37.0740),
        "naivasha" to (-0.7172 to 36.4310),
        "kakamega" to (0.2827 to 34.7519),
        "bungoma" to (0.5635 to 34.5606),
        "kisii" to (-0.6817 to 34.7667),
        "kericho" to (-0.3677 to 35.2831),
        "embu" to (-0.5389 to 37.4583),
        "garissa" to (-0.4532 to 39.6460),
        "kitale" to (1.0187 to 35.0020),
        "narok" to (-1.0875 to 35.8771),
        "voi" to (-3.3961 to 38.5561),
        "lamu" to (-2.2696 to 40.9006)
    )

    val KENYA_PICKER_PRESETS: List<KenyaLocationPreset> = listOf(
        KenyaLocationPreset("Nairobi • Kilimani", "Nairobi", "Nairobi", "Kilimani", -1.2896, 36.7831),
        KenyaLocationPreset("Nairobi • Westlands", "Nairobi", "Nairobi", "Westlands", -1.2673, 36.8111),
        KenyaLocationPreset("Nairobi • CBD", "Nairobi", "Nairobi", "CBD", -1.2864, 36.8172),
        KenyaLocationPreset("Mombasa • Nyali", "Mombasa", "Mombasa", "Nyali", -4.0273, 39.7087),
        KenyaLocationPreset("Kisumu • Milimani", "Kisumu", "Kisumu", "Milimani", -0.1055, 34.7568),
        KenyaLocationPreset("Nakuru • CBD", "Nakuru", "Nakuru", "Nakuru Town", -0.3031, 36.0800),
        KenyaLocationPreset("Eldoret • CBD", "Eldoret", "Uasin Gishu", "Eldoret Town", 0.5143, 35.2698),
        KenyaLocationPreset("Kiambu • Ruiru", "Ruiru", "Kiambu", "Ruiru", -1.1459, 36.9597),
        KenyaLocationPreset("Kajiado • Kitengela", "Kitengela", "Kajiado", "Kitengela", -1.4744, 36.9598)
    )

    fun hasValidCoordinates(latitude: Double?, longitude: Double?): Boolean {
        if (latitude == null || longitude == null) return false
        if (!latitude.isFinite() || !longitude.isFinite()) return false
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return false
        if (latitude == 0.0 && longitude == 0.0) return false
        return true
    }

    fun resolveCityCentroid(
        city: String,
        county: String = "",
        neighborhood: String = ""
    ): Pair<Double, Double> {
        val candidates = listOf(neighborhood, city, county)
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }

        for (candidate in candidates) {
            CITY_AND_COUNTY_CENTROIDS[candidate]?.let { return it }
        }
        for (candidate in candidates) {
            val matched = CITY_AND_COUNTY_CENTROIDS.entries.firstOrNull { (key, _) ->
                candidate.contains(key) || key.contains(candidate)
            }
            if (matched != null) return matched.value
        }
        return DEFAULT_NAIROBI
    }

    fun resolve(
        city: String,
        county: String = "",
        neighborhood: String = "",
        addressLine: String = "",
        latitude: Double? = null,
        longitude: Double? = null,
        formattedAddress: String? = null,
        locationVerified: Boolean = false
    ): ResolvedBuildingLocation {
        val fallbackAddress = listOf(addressLine, neighborhood, city, county.takeIf { it.isNotBlank() }?.let { "$it County" })
            .mapNotNull { it?.trim()?.takeIf { s -> s.isNotBlank() } }
            .distinct()
            .joinToString(", ")
            .ifBlank { "Kenya" }

        val cleanFormatted = formattedAddress?.trim()?.takeIf { it.isNotBlank() } ?: fallbackAddress

        return if (hasValidCoordinates(latitude, longitude)) {
            val exact = locationVerified || latitude != null
            ResolvedBuildingLocation(
                latitude = latitude!!,
                longitude = longitude!!,
                isExactPin = exact,
                addressSummary = cleanFormatted,
                sourceLabel = if (locationVerified) "Exact Map Pin" else "Saved Coordinates"
            )
        } else {
            val (lat, lng) = resolveCityCentroid(city = city, county = county, neighborhood = neighborhood)
            val areaLabel = city.trim().ifBlank { county.trim() }.ifBlank { "Nairobi" }
            ResolvedBuildingLocation(
                latitude = lat,
                longitude = lng,
                isExactPin = false,
                addressSummary = cleanFormatted,
                sourceLabel = "Approximate ($areaLabel Center)"
            )
        }
    }

    fun resolve(property: Property): ResolvedBuildingLocation = resolve(
        city = property.city,
        county = property.county,
        neighborhood = property.neighborhood,
        addressLine = property.addressLine,
        latitude = property.latitude,
        longitude = property.longitude,
        formattedAddress = property.formattedAddress,
        locationVerified = property.locationVerified
    )

    fun resolve(unit: RentalUnit, parentProperty: Property? = null): ResolvedBuildingLocation {
        val useUnitCoords = hasValidCoordinates(unit.latitude, unit.longitude)
        val lat = if (useUnitCoords) unit.latitude else parentProperty?.latitude
        val lng = if (useUnitCoords) unit.longitude else parentProperty?.longitude
        val formatted = unit.formattedAddress?.takeIf { it.isNotBlank() }
            ?: parentProperty?.formattedAddress
        val verified = if (useUnitCoords) unit.locationVerified else (parentProperty?.locationVerified == true)
        return resolve(
            city = unit.propertyCity.ifBlank { parentProperty?.city.orEmpty() },
            county = parentProperty?.county.orEmpty(),
            neighborhood = parentProperty?.neighborhood.orEmpty(),
            addressLine = parentProperty?.addressLine.orEmpty(),
            latitude = lat,
            longitude = lng,
            formattedAddress = formatted,
            locationVerified = verified
        )
    }

    fun groupUnitsByBuilding(
        units: List<RentalUnit>,
        propertiesById: Map<String, Property> = emptyMap()
    ): List<BuildingPinGroup> {
        if (units.isEmpty()) return emptyList()
        return units
            .groupBy { it.propertyId.ifBlank { "unit_${it.id}" } }
            .map { (propId, groupUnits) ->
                val sample = groupUnits.first()
                val parent = propertiesById[propId]
                val resolved = resolve(sample, parent)
                val sortedUnits = groupUnits.sortedBy { it.rentCents }
                BuildingPinGroup(
                    propertyId = propId,
                    propertyName = sample.propertyName.ifBlank { parent?.name.orEmpty() }.ifBlank { "Rental Building" },
                    city = sample.propertyCity.ifBlank { parent?.city.orEmpty() }.ifBlank { "Nairobi" },
                    neighborhood = parent?.neighborhood.orEmpty(),
                    addressLine = parent?.addressLine.orEmpty(),
                    location = resolved,
                    landlordVerified = groupUnits.any { it.landlordVerified } || (parent?.landlordVerified == true),
                    units = sortedUnits,
                    minRentCents = sortedUnits.minOf { it.rentCents },
                    maxRentCents = sortedUnits.maxOf { it.rentCents }
                )
            }
            .sortedWith(
                compareByDescending<BuildingPinGroup> { it.location.isExactPin }
                    .thenBy { it.minRentCents }
            )
    }

    fun formatCoordinates(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
}
