package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.location.DirectionsLauncher
import com.example.domain.finance.CurrencyUtils
import com.example.domain.location.BuildingLocationResolver
import com.example.domain.model.ApplicationStatus
import com.example.domain.model.PropertyCategory
import com.example.domain.model.RentalApplication
import com.example.domain.model.RentalUnit
import com.example.domain.model.UserProfile
import com.example.ui.components.BuildingMapPreviewCard
import com.example.ui.components.DiscoveryMapView
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.PillTone
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import com.example.ui.theme.TabularMoneyStyle
import com.example.ui.viewmodel.DiscoveryViewModel
import com.example.ui.viewmodel.UiState

private val kenyanCities = listOf("All", "Nairobi", "Mombasa", "Kisumu", "Nakuru", "Eldoret")

private val scamReportReasons = listOf(
    "Upfront Viewing Fee Demand",
    "Fake or Stolen Photos",
    "Misleading KES Rent / Deposit",
    "Impersonation / Suspicious Contact"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PropertyDiscoveryScreen(
    viewModel: DiscoveryViewModel,
    currentUser: UserProfile?,
    isPublicMode: Boolean,
    onBackToSignIn: (() -> Unit)? = null,
    onRequireAuthToApply: () -> Unit = {}
) {
    val context = LocalContext.current
    val unitsState by viewModel.publishedUnits.collectAsStateWithLifecycle()
    val filteredUnits by viewModel.filteredUnits.collectAsStateWithLifecycle()
    val publishedPropertiesById by viewModel.publishedPropertiesById.collectAsStateWithLifecycle()
    val mapPinGroups by viewModel.mapPinGroups.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val myAppsState by viewModel.myApplications.collectAsStateWithLifecycle()
    val savedUnitIds by viewModel.savedUnitIds.collectAsStateWithLifecycle()
    val compareUnitIds by viewModel.compareUnitIds.collectAsStateWithLifecycle()
    val compareUnits by viewModel.compareUnits.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    var showMapView by remember { mutableStateOf(false) }
    var selectedUnit by remember { mutableStateOf<RentalUnit?>(null) }
    var unitForApplication by remember { mutableStateOf<RentalUnit?>(null) }
    var unitToReport by remember { mutableStateOf<RentalUnit?>(null) }
    var showCompareSheet by remember { mutableStateOf(false) }
    var maxRentInput by remember { mutableStateOf("") }

    val content: @Composable (PaddingValues) -> Unit = { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                FilterChip(
                    selected = !showMapView,
                    onClick = { showMapView = false },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Filled.ViewList,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("List (${filteredUnits.size})") },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("discovery_view_list_button")
                )
                FilterChip(
                    selected = showMapView,
                    onClick = { showMapView = true },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Map,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Map (${mapPinGroups.size})") },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("discovery_view_map_button")
                )
                Spacer(Modifier.weight(1f))
                if (isPublicMode && onBackToSignIn != null && showMapView) {
                    TextButton(onClick = onBackToSignIn, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Sign In")
                    }
                }
            }

            if (showMapView) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    items(kenyanCities) { city ->
                        FilterChip(
                            selected = filters.selectedCity == city,
                            onClick = { viewModel.updateCity(city) },
                            label = { Text(city) }
                        )
                    }
                }

                when (val st = unitsState) {
                    is UiState.Loading -> LoadingStateBox("Loading Kenyan rental buildings...")
                    is UiState.Error -> ErrorBannerCard(st.message, modifier = Modifier.padding(16.dp))
                    is UiState.Success -> {
                        DiscoveryMapView(
                            pinGroups = mapPinGroups,
                            selectedCityFilter = filters.selectedCity,
                            onOpenUnitDetails = { unit -> selectedUnit = unit },
                            onApplyUnit = { unit ->
                                if (isPublicMode || currentUser == null) {
                                    onRequireAuthToApply()
                                } else {
                                    unitForApplication = unit
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }
                }
            } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                item {
                    OutlinedTextField(
                        value = filters.searchQuery,
                        onValueChange = viewModel::updateSearch,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                        placeholder = { Text("Search property, city, neighborhood, unit...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("discovery_search_input")
                    )
                }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(kenyanCities) { city ->
                            FilterChip(
                                selected = filters.selectedCity == city,
                                onClick = { viewModel.updateCity(city) },
                                label = { Text(city) }
                            )
                        }
                    }
                }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = filters.verifiedOnly,
                                onClick = viewModel::toggleVerifiedOnly,
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.VerifiedUser,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("Verified Only") },
                                modifier = Modifier.testTag("filter_verified_only")
                            )
                        }
                        item {
                            FilterChip(
                                selected = filters.savedOnly,
                                onClick = viewModel::toggleSavedOnly,
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Bookmark,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("Saved Only (${savedUnitIds.size})") },
                                modifier = Modifier.testTag("filter_saved_only")
                            )
                        }
                        item {
                            FilterChip(
                                selected = filters.selectedCategory == "All",
                                onClick = { viewModel.updateCategory("All") },
                                label = { Text("All Types") }
                            )
                        }
                        items(PropertyCategory.entries) { cat ->
                            FilterChip(
                                selected = filters.selectedCategory == cat.name,
                                onClick = { viewModel.updateCategory(cat.name) },
                                label = { Text(cat.label) }
                            )
                        }
                    }
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = maxRentInput,
                            onValueChange = {
                                maxRentInput = it
                                viewModel.updateMaxRent(it.filter { c -> c.isDigit() }.toLongOrNull())
                            },
                            label = { Text("Max Rent (KES)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        if (isPublicMode && onBackToSignIn != null) {
                            Button(
                                onClick = onBackToSignIn,
                                modifier = Modifier.heightIn(min = 52.dp)
                            ) {
                                Text("Sign In")
                            }
                        }
                    }
                }

                if (!isPublicMode) {
                    val apps = (myAppsState as? UiState.Success)?.data ?: emptyList()
                    if (apps.isNotEmpty()) {
                        item {
                            SectionHeader("My Rental Applications (${apps.size})")
                        }
                        items(apps, key = { it.id }) { app ->
                            ApplicationStatusCard(
                                application = app,
                                onWithdraw = { viewModel.withdrawApplication(app.id) }
                            )
                        }
                    }
                }

                item {
                    SectionHeader("Available Units (${filteredUnits.size})")
                }

                when (val st = unitsState) {
                    is UiState.Loading -> item { LoadingStateBox("Loading published Kenyan rentals...") }
                    is UiState.Error -> item { ErrorBannerCard(st.message) }
                    is UiState.Success -> {
                        if (filteredUnits.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = if (filters.savedOnly || filters.verifiedOnly) {
                                        "No Matching Rentals"
                                    } else {
                                        "No Published Rentals Yet"
                                    },
                                    message = if (filters.savedOnly) {
                                        "Tap the bookmark icon on any unit to save and compare it later."
                                    } else if (filters.verifiedOnly) {
                                        "No verified landlord listings match your current filters."
                                    } else {
                                        "Landlords can publish vacant units from their portfolio dashboard, and they will appear here immediately for prospective tenants."
                                    },
                                    ctaLabel = if (filters.savedOnly || filters.verifiedOnly) {
                                        "Reset Filters"
                                    } else if (isPublicMode) {
                                        "Sign In to List or Apply"
                                    } else {
                                        null
                                    },
                                    onCtaClick = if (filters.savedOnly) {
                                        viewModel::toggleSavedOnly
                                    } else if (filters.verifiedOnly) {
                                        viewModel::toggleVerifiedOnly
                                    } else {
                                        onBackToSignIn
                                    }
                                )
                            }
                        } else {
                            items(filteredUnits, key = { it.id }) { unit ->
                                DiscoveryUnitCard(
                                    unit = unit,
                                    isSaved = unit.id in savedUnitIds,
                                    isCompared = unit.id in compareUnitIds,
                                    onToggleSave = { viewModel.toggleSaveUnit(unit) },
                                    onToggleCompare = { viewModel.toggleCompareUnit(unit.id) },
                                    onViewDetails = { selectedUnit = unit },
                                    onApply = {
                                        if (isPublicMode || currentUser == null) {
                                            onRequireAuthToApply()
                                        } else {
                                            unitForApplication = unit
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            }

            AnimatedVisibility(visible = compareUnits.isNotEmpty()) {
                CompareUnitsDockBar(
                    selectedCount = compareUnits.size,
                    onClear = viewModel::clearCompareUnits,
                    onCompareNow = { showCompareSheet = true }
                )
            }
        }
    }

    if (isPublicMode) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("RentFlow Listings") },
                    navigationIcon = {
                        if (onBackToSignIn != null) {
                            IconButton(onClick = onBackToSignIn) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to sign in")
                            }
                        }
                    },
                    modifier = Modifier.statusBarsPadding()
                )
            },
            content = content
        )
    } else {
        content(PaddingValues(0.dp))
    }

    selectedUnit?.let { unit ->
        val isSaved = unit.id in savedUnitIds
        ModalBottomSheet(onDismissRequest = { selectedUnit = null }) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                if (unit.imageRefs.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(unit.imageRefs) { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = "${unit.propertyName} photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 220.dp, height = 140.dp)
                                    .clip(MaterialTheme.shapes.medium)
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${unit.propertyName} • Unit ${unit.unitNumber}", style = MaterialTheme.typography.headlineSmall)
                        Text("${unit.propertyCity} • ${unit.unitTypeEnum().label}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { viewModel.toggleSaveUnit(unit) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = if (isSaved) "Remove bookmark" else "Save listing",
                            tint = if (isSaved) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (unit.landlordVerified) {
                    StatusPill("Verified Landlord", PillTone.SUCCESS)
                } else {
                    Surface(
                        color = StatusWarningBg,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Unverified landlord — always view the property in person and avoid paying inspection fees before verifying ownership.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusWarning,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Monthly Rent", style = MaterialTheme.typography.labelSmall)
                        Text(CurrencyUtils.formatKes(unit.rentCents), style = TabularMoneyStyle, color = MaterialTheme.colorScheme.secondary)
                    }
                    Column {
                        Text("Security Deposit", style = MaterialTheme.typography.labelSmall)
                        Text(CurrencyUtils.formatKes(unit.depositCents), style = TabularMoneyStyle)
                    }
                }
                if (!unit.mpesaPaybill.isNullOrBlank() || !unit.mpesaTill.isNullOrBlank()) {
                    val mpesaDetails = listOfNotNull(
                        unit.mpesaPaybill?.let { "M-Pesa Paybill: $it" },
                        unit.mpesaTill?.let { "M-Pesa Till: $it" }
                    ).joinToString(" • ")
                    Text(
                        text = mpesaDetails,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (unit.description.isNotBlank()) {
                    Text(unit.description, style = MaterialTheme.typography.bodyMedium)
                }
                if (unit.amenities.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        unit.amenities.forEach { amenity ->
                            StatusPill(amenity, PillTone.INFO)
                        }
                    }
                }
                val resolvedLocation = BuildingLocationResolver.resolve(unit, publishedPropertiesById[unit.propertyId])
                BuildingMapPreviewCard(
                    buildingName = unit.propertyName,
                    location = resolvedLocation,
                    onDirectionsClick = {
                        DirectionsLauncher.openGoogleMapsDirections(
                            context = context,
                            latitude = resolvedLocation.latitude,
                            longitude = resolvedLocation.longitude,
                            label = unit.propertyName
                        )
                    },
                    testTag = "discovery_unit_location_card"
                )
                Button(
                    onClick = {
                        selectedUnit = null
                        if (isPublicMode || currentUser == null) onRequireAuthToApply()
                        else unitForApplication = unit
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                ) {
                    Text(if (isPublicMode || currentUser == null) "Sign In with Google to Apply" else "Apply for This Unit")
                }
                TextButton(
                    onClick = {
                        selectedUnit = null
                        if (isPublicMode || currentUser == null) {
                            onRequireAuthToApply()
                        } else {
                            unitToReport = unit
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("report_listing_button")
                ) {
                    Icon(Icons.Filled.Flag, contentDescription = null, tint = StatusError, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Report Suspicious Listing", color = StatusError)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showCompareSheet && compareUnits.isNotEmpty()) {
        UnitComparisonBottomSheet(
            units = compareUnits,
            onDismiss = { showCompareSheet = false },
            onRemoveUnit = { viewModel.toggleCompareUnit(it.id) },
            onApplyUnit = { unit ->
                showCompareSheet = false
                if (isPublicMode || currentUser == null) {
                    onRequireAuthToApply()
                } else {
                    unitForApplication = unit
                }
            }
        )
    }

    unitToReport?.let { unit ->
        ReportListingDialog(
            unit = unit,
            isBusy = busy,
            onDismiss = { unitToReport = null },
            onSubmit = { reason, details ->
                viewModel.reportListingScam(
                    unit = unit,
                    reason = reason,
                    details = details,
                    reporterName = currentUser?.fullName ?: "RentFlow Tenant"
                ) {
                    unitToReport = null
                }
            }
        )
    }

    unitForApplication?.let { unit ->
        ApplyForUnitDialog(
            unit = unit,
            user = currentUser,
            isBusy = busy,
            onDismiss = { unitForApplication = null },
            onSubmit = { name, phone, occupation, moveInDate, notes ->
                viewModel.submitApplication(
                    unit = unit,
                    applicantName = name,
                    applicantPhone = phone,
                    applicantEmail = currentUser?.email,
                    occupation = occupation,
                    desiredMoveInDate = moveInDate,
                    notes = notes
                ) {
                    unitForApplication = null
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoveryUnitCard(
    unit: RentalUnit,
    isSaved: Boolean,
    isCompared: Boolean,
    onToggleSave: () -> Unit,
    onToggleCompare: () -> Unit,
    onViewDetails: () -> Unit,
    onApply: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (unit.imageRefs.isNotEmpty()) {
                AsyncImage(
                    model = unit.imageRefs.first(),
                    contentDescription = "${unit.propertyName} Unit ${unit.unitNumber}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(MaterialTheme.shapes.small)
                )
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${unit.propertyName} • Unit ${unit.unitNumber}", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${unit.propertyCity} • ${unit.unitTypeEnum().label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(
                    onClick = onToggleSave,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("save_unit_${unit.id}")
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (isSaved) "Remove saved listing" else "Save listing",
                        tint = if (isSaved) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusPill("Available", PillTone.SUCCESS)
                if (unit.landlordVerified) {
                    StatusPill("Verified Landlord", PillTone.SUCCESS)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${CurrencyUtils.formatKes(unit.rentCents)} / mo",
                        style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "Deposit: ${CurrencyUtils.formatKes(unit.depositCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = isCompared,
                    onClick = onToggleCompare,
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text(if (isCompared) "Compared" else "Compare") },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("compare_unit_${unit.id}")
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onViewDetails, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Details")
                }
                Button(
                    onClick = onApply,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Apply")
                }
            }
            if (unit.amenities.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    unit.amenities.take(4).forEach { amenity ->
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                            Text(amenity, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareUnitsDockBar(
    selectedCount: Int,
    onClear: () -> Unit,
    onCompareNow: () -> Unit
) {
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("compare_dock")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Compare ($selectedCount/3)",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = if (selectedCount < 2) "Select 1 more unit to compare side by side" else "Ready for KES move-in & amenity comparison",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onClear, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Clear")
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = onCompareNow,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("open_compare_sheet_button")
            ) {
                Text("Compare Now")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitComparisonBottomSheet(
    units: List<RentalUnit>,
    onDismiss: () -> Unit,
    onRemoveUnit: (RentalUnit) -> Unit,
    onApplyUnit: (RentalUnit) -> Unit
) {
    val minRentCents = units.minOfOrNull { it.rentCents } ?: 0L
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        UnitComparisonContent(
            units = units,
            minRentCents = minRentCents,
            onRemoveUnit = onRemoveUnit,
            onApplyUnit = onApplyUnit
        )
    }
}

@Composable
fun UnitComparisonContent(
    units: List<RentalUnit>,
    minRentCents: Long = units.minOfOrNull { it.rentCents } ?: 0L,
    onRemoveUnit: (RentalUnit) -> Unit,
    onApplyUnit: (RentalUnit) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("unit_comparison_sheet")
    ) {
        Text(
            text = "Side-by-Side Unit Comparison (${units.size})",
            style = MaterialTheme.typography.titleLarge
        )
        if (units.size == 1) {
            Text(
                text = "Tip: Select up to 3 units from listings to compare KES rent, move-in cash, and amenities side by side.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            units.forEach { unit ->
                val moveInTotal = unit.rentCents + unit.depositCents
                val isLowestRent = units.size >= 2 && unit.rentCents == minRentCents
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    border = BorderStroke(
                        width = if (isLowestRent) 2.dp else 1.dp,
                        color = if (isLowestRent) StatusSuccess else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.width(188.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(unit.propertyName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text("Unit ${unit.unitNumber}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        if (isLowestRent) {
                            StatusPill("Best Price", PillTone.SUCCESS)
                        }
                        if (unit.landlordVerified) {
                            StatusPill("Verified Landlord", PillTone.SUCCESS)
                        } else {
                            StatusPill("Unverified", PillTone.NEUTRAL)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        ComparisonRow("Monthly Rent", CurrencyUtils.formatKes(unit.rentCents), highlight = isLowestRent)
                        ComparisonRow("Deposit", CurrencyUtils.formatKes(unit.depositCents))
                        ComparisonRow("Move-In Cash", CurrencyUtils.formatKes(moveInTotal))
                        ComparisonRow("Location", unit.propertyCity)
                        ComparisonRow("Unit Type", unit.unitTypeEnum().label)
                        ComparisonRow(
                            "Amenities",
                            if (unit.amenities.isEmpty()) "Standard" else unit.amenities.take(4).joinToString(", ")
                        )
                        Button(
                            onClick = { onApplyUnit(unit) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("compare_apply_${unit.id}")
                        ) {
                            Text("Apply")
                        }
                        TextButton(
                            onClick = { onRemoveUnit(unit) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text("Remove")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonRow(label: String, value: String, highlight: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (highlight) StatusSuccess else MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportListingDialog(
    unit: RentalUnit,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, details: String) -> Unit
) {
    var selectedReason by remember { mutableStateOf(scamReportReasons.first()) }
    var details by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report Suspicious Listing") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "${unit.propertyName} • Unit ${unit.unitNumber} (${unit.propertyCity})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!err.isNullOrBlank()) ErrorBannerCard(err!!)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    scamReportReasons.forEach { reason ->
                        FilterChip(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason },
                            label = { Text(reason) }
                        )
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it; err = null },
                    label = { Text("What seemed suspicious?") },
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_listing_details_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (details.trim().length < 5) {
                        err = "Please describe what seemed suspicious (at least 5 characters)"
                    } else {
                        onSubmit(selectedReason, details.trim())
                    }
                },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                modifier = Modifier.testTag("submit_listing_report_button")
            ) {
                Text("Submit Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ApplicationStatusCard(application: RentalApplication, onWithdraw: () -> Unit) {
    val tone = when (application.statusEnum()) {
        ApplicationStatus.APPROVED -> PillTone.SUCCESS
        ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN -> PillTone.ERROR
        ApplicationStatus.UNDER_REVIEW -> PillTone.WARNING
        ApplicationStatus.SUBMITTED -> PillTone.INFO
    }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(14.dp).fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("${application.propertyName} • Unit ${application.unitNumber}", style = MaterialTheme.typography.titleSmall)
                Text("Move-in: ${application.desiredMoveInDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!application.reviewerNotes.isNullOrBlank()) {
                    Text("Note: ${application.reviewerNotes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(application.statusEnum().label, tone)
                if (application.statusEnum() in listOf(ApplicationStatus.SUBMITTED, ApplicationStatus.UNDER_REVIEW)) {
                    TextButton(onClick = onWithdraw) {
                        Text("Withdraw")
                    }
                }
            }
        }
    }
}

@Composable
private fun ApplyForUnitDialog(
    unit: RentalUnit,
    user: UserProfile?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String?, String, String?) -> Unit
) {
    var name by remember { mutableStateOf(user?.fullName ?: "") }
    var phone by remember { mutableStateOf(user?.phoneNumber ?: "+254") }
    var occupation by remember { mutableStateOf("") }
    var moveInDate by remember { mutableStateOf("2026-12-01") }
    var notes by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apply: ${unit.propertyName} (${unit.unitNumber})") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (!err.isNullOrBlank()) ErrorBannerCard(err!!)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Kenyan Phone (+254...)") }, singleLine = true)
                OutlinedTextField(value = occupation, onValueChange = { occupation = it }, label = { Text("Occupation / Employer (Optional)") }, singleLine = true)
                OutlinedTextField(value = moveInDate, onValueChange = { moveInDate = it }, label = { Text("Desired Move-in Date") }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Message to Landlord (Optional)") }, maxLines = 3)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().length < 2) err = "Enter your full name"
                    else if (!CurrencyUtils.isValidKenyanPhone(phone)) err = "Enter a valid Kenyan phone number"
                    else if (moveInDate.isBlank()) err = "Enter desired move-in date"
                    else {
                        onSubmit(name, CurrencyUtils.normalizeKenyanPhone(phone), occupation.ifBlank { null }, moveInDate, notes.ifBlank { null })
                    }
                },
                enabled = !isBusy
            ) {
                Text("Submit Application")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
