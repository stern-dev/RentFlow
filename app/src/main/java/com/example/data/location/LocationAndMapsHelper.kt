package com.example.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.domain.location.BuildingLocationResolver
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

object MapsApiKeyStatus {
    private val PLACEHOLDER_KEYS = setOf(
        "",
        "MAPS_API_KEY_DEFAULT_VALUE",
        "YOUR_API_KEY",
        "DEFAULT_API_KEY"
    )

    fun currentKey(): String = runCatching { BuildConfig.MAPS_API_KEY.trim() }.getOrDefault("")

    fun isConfigured(): Boolean {
        val key = currentKey()
        return key.isNotBlank() && key !in PLACEHOLDER_KEYS
    }
}

data class PlaceSuggestion(
    val id: String,
    val primaryText: String,
    val secondaryText: String,
    val fullText: String,
    val placeId: String? = null,
    val resolvedLatLng: Pair<Double, Double>? = null,
    val city: String? = null,
    val county: String? = null,
    val neighborhood: String? = null,
    val addressLine: String? = null
)

data class ResolvedPickedLocation(
    val latitude: Double,
    val longitude: Double,
    val formattedAddress: String,
    val placeId: String?,
    val city: String?,
    val county: String?,
    val neighborhood: String?,
    val addressLine: String?
)

class MapsSearchHelper(private val context: Context) {
    private val appContext = context.applicationContext
    private var sessionToken: AutocompleteSessionToken? = null

    private fun getOrInitPlacesClient(): PlacesClient? {
        if (!MapsApiKeyStatus.isConfigured()) return null
        return runCatching {
            if (!Places.isInitialized()) {
                Places.initializeWithNewPlacesApiEnabled(appContext, MapsApiKeyStatus.currentKey())
            }
            Places.createClient(appContext)
        }.onFailure {
            Log.w(TAG, "Places SDK unavailable, using Geocoder/preset fallback", it)
        }.getOrNull()
    }

    suspend fun searchKenyanAddresses(query: String): List<PlaceSuggestion> {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return emptyList()

        val placesResults = searchViaPlaces(cleanQuery)
        if (placesResults.isNotEmpty()) return placesResults

        val geocoderResults = searchViaGeocoder(cleanQuery)
        if (geocoderResults.isNotEmpty()) return geocoderResults

        return BuildingLocationResolver.KENYA_PICKER_PRESETS
            .filter {
                it.label.contains(cleanQuery, ignoreCase = true) ||
                    it.city.contains(cleanQuery, ignoreCase = true) ||
                    it.neighborhood.contains(cleanQuery, ignoreCase = true)
            }
            .map { preset ->
                PlaceSuggestion(
                    id = "preset_${preset.city}_${preset.neighborhood}",
                    primaryText = preset.label,
                    secondaryText = "${preset.county} County, Kenya",
                    fullText = "${preset.neighborhood}, ${preset.city}, Kenya",
                    resolvedLatLng = preset.latitude to preset.longitude,
                    city = preset.city,
                    county = preset.county,
                    neighborhood = preset.neighborhood,
                    addressLine = preset.neighborhood
                )
            }
    }

    private suspend fun searchViaPlaces(query: String): List<PlaceSuggestion> {
        val client = getOrInitPlacesClient() ?: return emptyList()
        return try {
            val token = sessionToken ?: AutocompleteSessionToken.newInstance().also { sessionToken = it }
            val request = FindAutocompletePredictionsRequest.builder()
                .setCountries(listOf("KE"))
                .setSessionToken(token)
                .setQuery(query)
                .build()
            val response = client.findAutocompletePredictions(request).await()
            response.autocompletePredictions.take(6).map { prediction ->
                PlaceSuggestion(
                    id = prediction.placeId,
                    primaryText = prediction.getPrimaryText(null).toString(),
                    secondaryText = prediction.getSecondaryText(null).toString(),
                    fullText = prediction.getFullText(null).toString(),
                    placeId = prediction.placeId
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Places autocomplete query failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun searchViaGeocoder(query: String): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        runCatching {
            if (!Geocoder.isPresent()) return@runCatching emptyList()
            val geocoder = Geocoder(appContext, Locale.getDefault())
            val kenyaQuery = if (query.contains("kenya", ignoreCase = true)) query else "$query, Kenya"
            val addresses = geocodeFromName(geocoder, kenyaQuery, maxResults = 5)
            addresses.mapIndexedNotNull { idx, addr ->
                val lat = addr.latitude
                val lng = addr.longitude
                if (!BuildingLocationResolver.hasValidCoordinates(lat, lng)) return@mapIndexedNotNull null
                val full = buildAddressLine(addr)
                val primary = addr.featureName?.takeIf { it.isNotBlank() }
                    ?: addr.subLocality?.takeIf { it.isNotBlank() }
                    ?: addr.locality?.takeIf { it.isNotBlank() }
                    ?: full
                val secondary = listOfNotNull(
                    addr.subLocality?.takeIf { it.isNotBlank() && it != primary },
                    addr.locality?.takeIf { it.isNotBlank() && it != primary },
                    addr.adminArea?.takeIf { it.isNotBlank() }
                ).joinToString(", ")
                PlaceSuggestion(
                    id = "geo_${idx}_${lat}_${lng}",
                    primaryText = primary,
                    secondaryText = secondary.ifBlank { "Kenya" },
                    fullText = full,
                    resolvedLatLng = lat to lng,
                    city = addr.locality ?: addr.subAdminArea,
                    county = cleanCountyName(addr.adminArea),
                    neighborhood = addr.subLocality,
                    addressLine = addr.thoroughfare ?: addr.featureName
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun resolveSuggestion(suggestion: PlaceSuggestion): ResolvedPickedLocation? {
        val presetCoords = suggestion.resolvedLatLng
        if (presetCoords != null) {
            return ResolvedPickedLocation(
                latitude = presetCoords.first,
                longitude = presetCoords.second,
                formattedAddress = suggestion.fullText,
                placeId = suggestion.placeId,
                city = suggestion.city,
                county = suggestion.county,
                neighborhood = suggestion.neighborhood,
                addressLine = suggestion.addressLine
            )
        }
        val pid = suggestion.placeId ?: return null
        val client = getOrInitPlacesClient() ?: return null
        return try {
            val fields = listOf(
                Place.Field.ID,
                Place.Field.DISPLAY_NAME,
                Place.Field.FORMATTED_ADDRESS,
                Place.Field.LOCATION,
                Place.Field.ADDRESS_COMPONENTS
            )
            val request = FetchPlaceRequest.builder(pid, fields)
                .apply { sessionToken?.let { setSessionToken(it) } }
                .build()
            val response = client.fetchPlace(request).await()
            sessionToken = null
            val place = response.place
            val latLng: LatLng = place.location ?: return null
            val components = place.addressComponents?.asList().orEmpty()
            val locality = components.firstOrNull { "locality" in it.types }?.name
            val sublocality = components.firstOrNull {
                "sublocality_level_1" in it.types || "sublocality" in it.types || "neighborhood" in it.types
            }?.name
            val adminArea = components.firstOrNull { "administrative_area_level_1" in it.types }?.name
            val route = components.firstOrNull { "route" in it.types }?.name

            ResolvedPickedLocation(
                latitude = latLng.latitude,
                longitude = latLng.longitude,
                formattedAddress = place.formattedAddress ?: suggestion.fullText,
                placeId = place.id ?: pid,
                city = locality ?: suggestion.city,
                county = cleanCountyName(adminArea) ?: suggestion.county,
                neighborhood = sublocality ?: suggestion.primaryText,
                addressLine = route ?: place.displayName ?: suggestion.primaryText
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "FetchPlace failed: ${e.message}")
            null
        }
    }

    suspend fun reverseGeocode(latitude: Double, longitude: Double): ResolvedPickedLocation? = withContext(Dispatchers.IO) {
        runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            val geocoder = Geocoder(appContext, Locale.getDefault())
            val addresses = geocodeFromLocation(geocoder, latitude, longitude, maxResults = 1)
            val addr = addresses.firstOrNull() ?: return@runCatching null
            ResolvedPickedLocation(
                latitude = latitude,
                longitude = longitude,
                formattedAddress = buildAddressLine(addr),
                placeId = null,
                city = addr.locality ?: addr.subAdminArea,
                county = cleanCountyName(addr.adminArea),
                neighborhood = addr.subLocality ?: addr.featureName,
                addressLine = addr.thoroughfare ?: addr.featureName
            )
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private suspend fun geocodeFromName(
        geocoder: Geocoder,
        locationName: String,
        maxResults: Int
    ): List<Address> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(
                    locationName,
                    maxResults,
                    -4.7,
                    33.9,
                    4.6,
                    41.9
                ) { list ->
                    if (cont.isActive) cont.resume(list)
                }
            }
        } else {
            geocoder.getFromLocationName(locationName, maxResults, -4.7, 33.9, 4.6, 41.9) ?: emptyList()
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun geocodeFromLocation(
        geocoder: Geocoder,
        lat: Double,
        lng: Double,
        maxResults: Int
    ): List<Address> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(lat, lng, maxResults) { list ->
                    if (cont.isActive) cont.resume(list)
                }
            }
        } else {
            geocoder.getFromLocation(lat, lng, maxResults) ?: emptyList()
        }
    }

    private fun buildAddressLine(addr: Address): String {
        val line = (0..addr.maxAddressLineIndex).mapNotNull { idx ->
            addr.getAddressLine(idx)?.trim()?.takeIf { it.isNotBlank() }
        }.joinToString(", ")
        return line.ifBlank {
            listOfNotNull(addr.thoroughfare, addr.subLocality, addr.locality, addr.countryName)
                .joinToString(", ")
        }.take(200)
    }

    private fun cleanCountyName(raw: String?): String? {
        val clean = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return clean.removeSuffix(" County").removeSuffix(" county").trim()
    }

    companion object {
        private const val TAG = "MapsSearchHelper"
    }
}

object DeviceLocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(context: Context): Result<Pair<Double, Double>> {
        if (!hasLocationPermission(context)) {
            return Result.failure(SecurityException("Location permission is required to capture GPS coordinates."))
        }
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            val fresh = runCatching {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
            }.getOrNull()
            val location = fresh ?: runCatching { client.lastLocation.await() }.getOrNull()
            if (location != null && BuildingLocationResolver.hasValidCoordinates(location.latitude, location.longitude)) {
                Result.success(location.latitude to location.longitude)
            } else {
                Result.failure(IllegalStateException("Could not acquire GPS fix. Ensure location services are turned on or tap the map pin directly."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

object DirectionsLauncher {

    fun buildGoogleMapsWebUri(latitude: Double, longitude: Double): Uri =
        Uri.parse(
            String.format(
                Locale.US,
                "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f&travelmode=driving",
                latitude,
                longitude
            )
        )

    fun buildGeoPinUri(latitude: Double, longitude: Double, label: String): Uri {
        val cleanLabel = label.trim().ifBlank { "Building Location" }.take(80)
        val encoded = Uri.encode(cleanLabel)
        return Uri.parse(String.format(Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f(%s)", latitude, longitude, latitude, longitude, encoded))
    }

    fun openGoogleMapsDirections(
        context: Context,
        latitude: Double,
        longitude: Double,
        label: String
    ) {
        val navUri = Uri.parse(String.format(Locale.US, "google.navigation:q=%.6f,%.6f", latitude, longitude))
        val mapsIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        val geoIntent = Intent(Intent.ACTION_VIEW, buildGeoPinUri(latitude, longitude, label))
        val webIntent = Intent(Intent.ACTION_VIEW, buildGoogleMapsWebUri(latitude, longitude))

        val launched = runCatching {
            if (mapsIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapsIntent)
                true
            } else if (geoIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(geoIntent)
                true
            } else {
                context.startActivity(webIntent)
                true
            }
        }.getOrElse { false }

        if (!launched) {
            Toast.makeText(context, "No map or browser app available for directions", Toast.LENGTH_SHORT).show()
        }
    }
}
