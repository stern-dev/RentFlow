package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.domain.finance.CurrencyUtils
import com.example.domain.model.ApplicationStatus
import com.example.domain.model.PropertyCategory
import com.example.domain.model.RentalApplication
import com.example.domain.model.RentalUnit
import com.example.domain.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.PillTone
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.TabularMoneyStyle
import com.example.ui.viewmodel.DiscoveryViewModel
import com.example.ui.viewmodel.UiState

private val kenyanCities = listOf("All", "Nairobi", "Mombasa", "Kisumu", "Nakuru", "Eldoret")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PropertyDiscoveryScreen(
    viewModel: DiscoveryViewModel,
    currentUser: UserProfile?,
    isPublicMode: Boolean,
    onBackToSignIn: (() -> Unit)? = null,
    onRequireAuthToApply: () -> Unit = {}
) {
    val unitsState by viewModel.publishedUnits.collectAsStateWithLifecycle()
    val filteredUnits by viewModel.filteredUnits.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val myAppsState by viewModel.myApplications.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    var selectedUnit by remember { mutableStateOf<RentalUnit?>(null) }
    var unitForApplication by remember { mutableStateOf<RentalUnit?>(null) }
    var maxRentInput by remember { mutableStateOf("") }

    val content: @Composable (PaddingValues) -> Unit = { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                OutlinedTextField(
                    value = filters.searchQuery,
                    onValueChange = viewModel::updateSearch,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                    placeholder = { Text("Search property, city, neighborhood, unit...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("discovery_search_input")
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
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
                                title = "No Published Rentals Yet",
                                message = "Landlords can publish vacant units from their portfolio dashboard, and they will appear here immediately for prospective tenants.",
                                ctaLabel = if (isPublicMode) "Sign In to List or Apply" else null,
                                onCtaClick = onBackToSignIn
                            )
                        }
                    } else {
                        items(filteredUnits, key = { it.id }) { unit ->
                            DiscoveryUnitCard(
                                unit = unit,
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
                Text("${unit.propertyName} • Unit ${unit.unitNumber}", style = MaterialTheme.typography.headlineSmall)
                Text("${unit.propertyCity} • ${unit.unitTypeEnum().label}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Spacer(Modifier.height(16.dp))
            }
        }
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
                StatusPill("Available", PillTone.SUCCESS)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
