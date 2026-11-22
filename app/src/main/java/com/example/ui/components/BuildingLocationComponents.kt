package com.example.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.location.DeviceLocationHelper
import com.example.data.location.DirectionsLauncher
import com.example.data.location.MapsApiKeyStatus
import com.example.data.location.MapsSearchHelper
import com.example.data.location.PlaceSuggestion
import com.example.data.location.ResolvedPickedLocation
import com.example.domain.finance.CurrencyUtils
import com.example.domain.location.BuildingLocationResolver
import com.example.domain.location.BuildingPinGroup
import com.example.domain.location.ResolvedBuildingLocation
import com.example.domain.model.RentalUnit
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.NavySecondary
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TabularMoneyStyle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BuildingMapPreviewCard(
    buildingName: String,
    location: ResolvedBuildingLocation,
    onDirectionsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onConfirmPinClick: (() -> Unit)? = null,
    testTag: String = "building_map_preview_card"
) {
    val exactLabel = stringResource(R.string.location_exact_pin_label)
    val approxLabel = stringResource(R.string.location_approximate_label)
    val directionsLabel = stringResource(R.string.directions_in_google_maps)

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = "Building Location",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = buildingName.ifBlank { "Building Location" },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = location.addressSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                StatusPill(
                    label = if (location.isExactPin) exactLabel else approxLabel,
                    tone = if (location.isExactPin) PillTone.SUCCESS else PillTone.WARNING
                )
            }

            BuildingMapViewport(
                latitude = location.latitude,
                longitude = location.longitude,
                title = buildingName,
                snippet = location.addressSummary,
                isExactPin = location.isExactPin,
                interactive = false,
                heightDp = 156
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "GPS: ${BuildingLocationResolver.formatCoordinates(location.latitude, location.longitude)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = location.sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (location.isExactPin) MaterialTheme.colorScheme.secondary else StatusWarning
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onDirectionsClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("${testTag}_directions_button")
                ) {
                    Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(directionsLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (onConfirmPinClick != null) {
                    OutlinedButton(
                        onClick = onConfirmPinClick,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("${testTag}_confirm_pin_button")
                    ) {
                        Icon(Icons.Filled.EditLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit Pin")
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildingMapViewport(
    latitude: Double,
    longitude: Double,
    title: String,
    snippet: String,
    isExactPin: Boolean,
    interactive: Boolean,
    heightDp: Int,
    onCoordinatePicked: ((Double, Double) -> Unit)? = null
) {
    val targetLatLng = remember(latitude, longitude) { LatLng(latitude, longitude) }
    val mapsReady = remember { MapsApiKeyStatus.isConfigured() }

    if (mapsReady) {
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(targetLatLng, if (isExactPin) 15.5f else 12.5f)
        }
        val markerState = rememberUpdatedMarkerState(position = targetLatLng)

        LaunchedEffect(targetLatLng) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(targetLatLng, if (isExactPin) 15.5f else 12.8f)
            )
        }

        var wasDragging by remember { mutableStateOf(false) }
        LaunchedEffect(markerState.isDragging) {
            if (interactive) {
                if (markerState.isDragging) {
                    wasDragging = true
                } else if (wasDragging) {
                    wasDragging = false
                    val p = markerState.position
                    onCoordinatePicked?.invoke(p.latitude, p.longitude)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .clip(MaterialTheme.shapes.medium)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = interactive,
                    scrollGesturesEnabled = interactive,
                    zoomGesturesEnabled = interactive,
                    tiltGesturesEnabled = false,
                    mapToolbarEnabled = false
                ),
                onMapClick = if (interactive) {
                    { clicked -> onCoordinatePicked?.invoke(clicked.latitude, clicked.longitude) }
                } else {
                    {}
                }
            ) {
                Marker(
                    state = markerState,
                    title = title,
                    snippet = snippet,
                    draggable = interactive
                )
            }
        }
    } else {
        StyledKenyaMapFallbackSurface(
            latitude = latitude,
            longitude = longitude,
            title = title,
            isExactPin = isExactPin,
            heightDp = heightDp
        )
    }
}

@Composable
private fun StyledKenyaMapFallbackSurface(
    latitude: Double,
    longitude: Double,
    title: String,
    isExactPin: Boolean,
    heightDp: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.linearGradient(
                    listOf(
                        NavyPrimary.copy(alpha = 0.92f),
                        NavySecondary.copy(alpha = 0.88f)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridColor = Color(0xFF5EEAD4).copy(alpha = 0.14f)
            val stepX = size.width / 8f
            val stepY = size.height / 5f
            for (i in 1..7) {
                drawLine(
                    color = gridColor,
                    start = Offset(stepX * i, 0f),
                    end = Offset(stepX * i, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            for (j in 1..4) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, stepY * j),
                    end = Offset(size.width, stepY * j),
                    strokeWidth = 1.dp.toPx()
                )
            }
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = (if (isExactPin) EmeraldAccent else StatusWarning).copy(alpha = 0.22f),
                radius = 34.dp.toPx(),
                center = center
            )
            drawCircle(
                color = (if (isExactPin) EmeraldAccent else StatusWarning).copy(alpha = 0.45f),
                radius = 16.dp.toPx(),
                center = center
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isExactPin) EmeraldAccent else StatusWarning)
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = "Map Pin",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = title.ifBlank { "Kenya Building Pin" },
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = BuildingLocationResolver.formatCoordinates(latitude, longitude),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFCCFBF1)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoveryMapView(
    pinGroups: List<BuildingPinGroup>,
    selectedCityFilter: String,
    onOpenUnitDetails: (RentalUnit) -> Unit,
    onApplyUnit: (RentalUnit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapsReady = remember { MapsApiKeyStatus.isConfigured() }
    val exactLabel = stringResource(R.string.location_exact_pin_label)
    val approxLabel = stringResource(R.string.location_approximate_label)

    var selectedPropertyId by remember(pinGroups) {
        mutableStateOf(
            pinGroups.firstOrNull { it.propertyId == it.propertyId }?.propertyId
        )
    }
    val selectedGroup = remember(pinGroups, selectedPropertyId) {
        pinGroups.firstOrNull { it.propertyId == selectedPropertyId } ?: pinGroups.firstOrNull()
    }
    var selectedUnitInGroup by remember(selectedGroup) {
        mutableStateOf(selectedGroup?.units?.firstOrNull())
    }

    if (pinGroups.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
            EmptyStateCard(
                title = "No Buildings Match Filters",
                message = "Adjust your city, category, or max rent filters to view Kenyan rental buildings on the map."
            )
        }
        return
    }

    val centerCoord = remember(selectedGroup, selectedCityFilter, pinGroups) {
        selectedGroup?.let { it.location.latitude to it.location.longitude }
            ?: BuildingLocationResolver.resolveCityCentroid(
                city = selectedCityFilter.takeIf { it != "All" }.orEmpty()
            )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("discovery_map_view")
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pinGroups, key = { it.propertyId }) { group ->
                val isSelected = selectedGroup?.propertyId == group.propertyId
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedPropertyId = group.propertyId
                        selectedUnitInGroup = group.units.firstOrNull()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            "${group.propertyName} (${group.units.size})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.testTag("map_building_chip_${group.propertyId}")
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(MaterialTheme.shapes.large)
        ) {
            if (mapsReady) {
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(
                        LatLng(centerCoord.first, centerCoord.second),
                        12.5f
                    )
                }
                LaunchedEffect(centerCoord) {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(centerCoord.first, centerCoord.second),
                            13.8f
                        )
                    )
                }
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = true,
                        mapToolbarEnabled = false
                    )
                ) {
                    pinGroups.forEach { group ->
                        androidx.compose.runtime.key(group.propertyId) {
                            val markerState = rememberUpdatedMarkerState(
                                position = LatLng(group.location.latitude, group.location.longitude)
                            )
                            Marker(
                                state = markerState,
                                title = group.propertyName,
                                snippet = "From ${CurrencyUtils.formatKes(group.minRentCents)}/mo • ${group.units.size} unit(s)",
                                onClick = {
                                    selectedPropertyId = group.propertyId
                                    selectedUnitInGroup = group.units.firstOrNull()
                                    false
                                }
                            )
                        }
                    }
                }
            } else {
                StyledKenyaMapFallbackSurface(
                    latitude = centerCoord.first,
                    longitude = centerCoord.second,
                    title = selectedGroup?.propertyName ?: "Kenyan Rental Buildings",
                    isExactPin = selectedGroup?.location?.isExactPin == true,
                    heightDp = 240
                )
            }
        }

        selectedGroup?.let { group ->
            val activeUnit = selectedUnitInGroup ?: group.units.first()
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("discovery_map_pin_preview")
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = group.propertyName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${group.location.addressSummary} • ${group.city}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            StatusPill(
                                label = if (group.location.isExactPin) exactLabel else approxLabel,
                                tone = if (group.location.isExactPin) PillTone.SUCCESS else PillTone.WARNING
                            )
                            if (group.landlordVerified) {
                                StatusPill("Verified", PillTone.SUCCESS)
                            }
                        }
                    }

                    Text(
                        text = if (group.minRentCents == group.maxRentCents) {
                            "${CurrencyUtils.formatKes(group.minRentCents)} / mo • ${group.units.size} available unit(s)"
                        } else {
                            "From ${CurrencyUtils.formatKes(group.minRentCents)} / mo • ${group.units.size} available units"
                        },
                        style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize),
                        color = MaterialTheme.colorScheme.secondary
                    )

                    if (group.units.size > 1) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(group.units, key = { it.id }) { unit ->
                                FilterChip(
                                    selected = activeUnit.id == unit.id,
                                    onClick = { selectedUnitInGroup = unit },
                                    label = {
                                        Text("Unit ${unit.unitNumber} • ${CurrencyUtils.formatKesCompact(unit.rentCents)}")
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                DirectionsLauncher.openGoogleMapsDirections(
                                    context = context,
                                    latitude = group.location.latitude,
                                    longitude = group.location.longitude,
                                    label = group.propertyName
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("map_pin_directions_button")
                        ) {
                            Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Directions", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { onOpenUnitDetails(activeUnit) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("map_pin_view_details_button")
                        ) {
                            Text("Unit ${activeUnit.unitNumber}", maxLines = 1)
                        }
                        Button(
                            onClick = { onApplyUnit(activeUnit) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("map_pin_apply_button")
                        ) {
                            Text("Apply")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BuildingLocationPickerDialog(
    initialPropertyName: String,
    initialCity: String,
    initialCounty: String,
    initialNeighborhood: String,
    initialAddressLine: String,
    initialLatitude: Double?,
    initialLongitude: Double?,
    initialFormattedAddress: String?,
    initialPlaceId: String?,
    onDismiss: () -> Unit,
    onConfirmLocation: (ResolvedPickedLocation) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val searchHelper = remember(context) { MapsSearchHelper(context) }

    val startingLocation = remember(
        initialCity,
        initialCounty,
        initialNeighborhood,
        initialAddressLine,
        initialLatitude,
        initialLongitude,
        initialFormattedAddress
    ) {
        BuildingLocationResolver.resolve(
            city = initialCity,
            county = initialCounty,
            neighborhood = initialNeighborhood,
            addressLine = initialAddressLine,
            latitude = initialLatitude,
            longitude = initialLongitude,
            formattedAddress = initialFormattedAddress,
            locationVerified = initialLatitude != null && initialLongitude != null
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var capturingGps by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    var pickedLat by remember { mutableStateOf(startingLocation.latitude) }
    var pickedLng by remember { mutableStateOf(startingLocation.longitude) }
    var formattedAddress by remember { mutableStateOf(startingLocation.addressSummary) }
    var pickedPlaceId by remember { mutableStateOf(initialPlaceId) }
    var pickedCity by remember { mutableStateOf(initialCity.takeIf { it.isNotBlank() }) }
    var pickedCounty by remember { mutableStateOf(initialCounty.takeIf { it.isNotBlank() }) }
    var pickedNeighborhood by remember { mutableStateOf(initialNeighborhood.takeIf { it.isNotBlank() }) }
    var pickedAddressLine by remember { mutableStateOf(initialAddressLine.takeIf { it.isNotBlank() }) }
    var pinConfirmedByAction by remember {
        mutableStateOf(BuildingLocationResolver.hasValidCoordinates(initialLatitude, initialLongitude))
    }

    fun applyCoordinateMove(lat: Double, lng: Double, sourceNote: String) {
        pickedLat = lat
        pickedLng = lng
        pickedPlaceId = null
        pinConfirmedByAction = true
        statusBanner = sourceNote
        scope.launch {
            val rev = searchHelper.reverseGeocode(lat, lng)
            if (rev != null) {
                formattedAddress = rev.formattedAddress
                pickedCity = rev.city ?: pickedCity
                pickedCounty = rev.county ?: pickedCounty
                pickedNeighborhood = rev.neighborhood ?: pickedNeighborhood
                pickedAddressLine = rev.addressLine ?: pickedAddressLine
            }
        }
    }

    fun fetchDeviceGps() {
        capturingGps = true
        statusBanner = "Acquiring GPS coordinates..."
        scope.launch {
            val res = DeviceLocationHelper.fetchCurrentLocation(context)
            capturingGps = false
            res.fold(
                onSuccess = { (lat, lng) ->
                    applyCoordinateMove(lat, lng, "GPS coordinates captured accurately.")
                },
                onFailure = { err ->
                    statusBanner = err.localizedMessage
                        ?: "Could not acquire GPS fix. Search an estate or pick a pin on the map."
                }
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchDeviceGps()
        } else {
            statusBanner = "Location permission denied. Use Kenyan address search or tap the map to pin the building."
        }
    }

    LaunchedEffect(searchQuery) {
        val clean = searchQuery.trim()
        if (clean.length < 2) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        delay(280)
        searching = true
        searchResults = searchHelper.searchKenyanAddresses(clean)
        searching = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("building_location_picker_dialog")
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pin Building Location",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = initialPropertyName.ifBlank { "Set exact Kenyan building coordinates" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close location picker")
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search Kenyan road, estate, or landmark") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("location_picker_search_input")
                    )
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                searching = true
                                searchResults = searchHelper.searchKenyanAddresses(searchQuery)
                                searching = false
                            }
                        },
                        enabled = searchQuery.trim().length >= 2 && !searching,
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .testTag("location_picker_search_button")
                    ) {
                        Text("Find")
                    }
                }

                if (searching) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Searching Kenyan places...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (searchResults.isNotEmpty()) {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            searchResults.take(5).forEachIndexed { idx, suggestion ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clickable {
                                            scope.launch {
                                                val resolved = searchHelper.resolveSuggestion(suggestion)
                                                if (resolved != null) {
                                                    pickedLat = resolved.latitude
                                                    pickedLng = resolved.longitude
                                                    formattedAddress = resolved.formattedAddress
                                                    pickedPlaceId = resolved.placeId
                                                    pickedCity = resolved.city ?: pickedCity
                                                    pickedCounty = resolved.county ?: pickedCounty
                                                    pickedNeighborhood = resolved.neighborhood ?: pickedNeighborhood
                                                    pickedAddressLine = resolved.addressLine ?: pickedAddressLine
                                                    pinConfirmedByAction = true
                                                    searchResults = emptyList()
                                                    searchQuery = suggestion.primaryText
                                                    statusBanner = "Pinned from address search: ${resolved.formattedAddress}"
                                                }
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .testTag("location_suggestion_item_$idx")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(suggestion.primaryText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        if (suggestion.secondaryText.isNotBlank()) {
                                            Text(suggestion.secondaryText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                if (idx < searchResults.take(5).lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                }
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            if (DeviceLocationHelper.hasLocationPermission(context)) {
                                fetchDeviceGps()
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        enabled = !capturingGps,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("location_picker_gps_button")
                    ) {
                        if (capturingGps) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Capturing GPS...")
                        } else {
                            Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Use Current Device GPS")
                        }
                    }
                }

                Text(
                    text = "Quick Kenyan Areas",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(BuildingLocationResolver.KENYA_PICKER_PRESETS) { idx, preset ->
                        FilterChip(
                            selected = kotlin.math.abs(pickedLat - preset.latitude) < 0.002 &&
                                kotlin.math.abs(pickedLng - preset.longitude) < 0.002,
                            onClick = {
                                pickedLat = preset.latitude
                                pickedLng = preset.longitude
                                formattedAddress = "${preset.neighborhood}, ${preset.city}, ${preset.county} County, Kenya"
                                pickedPlaceId = null
                                pickedCity = preset.city
                                pickedCounty = preset.county
                                pickedNeighborhood = preset.neighborhood
                                pickedAddressLine = preset.neighborhood
                                pinConfirmedByAction = true
                                statusBanner = "Centered on ${preset.label}. Tap map to refine pin."
                            },
                            label = { Text(preset.label) },
                            modifier = Modifier.testTag("location_preset_$idx")
                        )
                    }
                }

                BuildingMapViewport(
                    latitude = pickedLat,
                    longitude = pickedLng,
                    title = initialPropertyName.ifBlank { "Building Pin" },
                    snippet = formattedAddress,
                    isExactPin = pinConfirmedByAction,
                    interactive = true,
                    heightDp = 210,
                    onCoordinatePicked = { lat, lng ->
                        applyCoordinateMove(lat, lng, "Pin moved on map.")
                    }
                )

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (pinConfirmedByAction) Icons.Filled.GpsFixed else Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = if (pinConfirmedByAction) MaterialTheme.colorScheme.secondary else StatusInfo,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Coordinates: ${BuildingLocationResolver.formatCoordinates(pickedLat, pickedLng)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = formattedAddress.ifBlank { "Tap the map, use GPS, or pick a Kenyan area above" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!statusBanner.isNullOrBlank()) {
                            Text(
                                text = statusBanner!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirmLocation(
                                ResolvedPickedLocation(
                                    latitude = pickedLat,
                                    longitude = pickedLng,
                                    formattedAddress = formattedAddress.ifBlank {
                                        BuildingLocationResolver.formatCoordinates(pickedLat, pickedLng)
                                    }.take(200),
                                    placeId = pickedPlaceId,
                                    city = pickedCity,
                                    county = pickedCounty,
                                    neighborhood = pickedNeighborhood,
                                    addressLine = pickedAddressLine
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("confirm_building_pin_button")
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Confirm Building Pin")
                    }
                }
            }
        }
    }
}
