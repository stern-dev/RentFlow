package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.PropertyRepository
import com.example.domain.model.Property
import com.example.domain.model.RentalApplication
import com.example.domain.model.RentalUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DiscoveryFilters(
    val searchQuery: String = "",
    val selectedCity: String = "All",
    val selectedCategory: String = "All",
    val maxRentKes: Long? = null,
    val verifiedOnly: Boolean = false,
    val savedOnly: Boolean = false
)

class DiscoveryViewModel(
    private val propertyRepo: PropertyRepository,
    private val currentUserId: String?
) : ViewModel() {

    val publishedProperties: StateFlow<UiState<List<Property>>> = propertyRepo.observePublishedProperties()
        .asUiState("DiscoveryVM", "Failed to load properties")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val publishedUnits: StateFlow<UiState<List<RentalUnit>>> = propertyRepo.observePublishedUnits()
        .asUiState("DiscoveryVM", "Failed to load available units")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val myApplications: StateFlow<UiState<List<RentalApplication>>> =
        if (!currentUserId.isNullOrBlank()) {
            propertyRepo.observeMyApplications(currentUserId)
                .asUiState("DiscoveryVM", "Failed to load your applications")
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
        } else {
            MutableStateFlow(UiState.Success(emptyList()))
        }

    private val _localSavedIds = MutableStateFlow<Set<String>>(emptySet())
    val savedUnitIds: StateFlow<Set<String>> =
        if (!currentUserId.isNullOrBlank()) {
            propertyRepo.observeSavedUnits(currentUserId)
                .map { list -> list.map { it.unitId }.toSet() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())
        } else {
            _localSavedIds.asStateFlow()
        }

    private val _compareUnitIds = MutableStateFlow<List<String>>(emptyList())
    val compareUnitIds: StateFlow<List<String>> = _compareUnitIds.asStateFlow()

    val compareUnits: StateFlow<List<RentalUnit>> = combine(publishedUnits, _compareUnitIds) { unitsState, ids ->
        val all = (unitsState as? UiState.Success)?.data ?: emptyList()
        ids.mapNotNull { id -> all.firstOrNull { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _filters = MutableStateFlow(DiscoveryFilters())
    val filters: StateFlow<DiscoveryFilters> = _filters.asStateFlow()

    val filteredUnits: StateFlow<List<RentalUnit>> = combine(
        publishedUnits,
        _filters,
        savedUnitIds
    ) { unitsState, f, savedIds ->
        val list = (unitsState as? UiState.Success)?.data ?: emptyList()
        list.filter { u ->
            val matchQuery = f.searchQuery.isBlank() ||
                u.propertyName.contains(f.searchQuery, ignoreCase = true) ||
                u.propertyCity.contains(f.searchQuery, ignoreCase = true) ||
                u.unitNumber.contains(f.searchQuery, ignoreCase = true) ||
                u.description.contains(f.searchQuery, ignoreCase = true)
            val matchCity = f.selectedCity == "All" || u.propertyCity.equals(f.selectedCity, ignoreCase = true)
            val matchCategory = f.selectedCategory == "All" || u.propertyCategory == f.selectedCategory
            val matchRent = f.maxRentKes == null || u.rentCents <= (f.maxRentKes * 100L)
            val matchVerified = !f.verifiedOnly || u.landlordVerified
            val matchSaved = !f.savedOnly || u.id in savedIds
            matchQuery && matchCity && matchCategory && matchRent && matchVerified && matchSaved
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    fun updateSearch(q: String) {
        _filters.value = _filters.value.copy(searchQuery = q)
    }

    fun updateCity(city: String) {
        _filters.value = _filters.value.copy(selectedCity = city)
    }

    fun updateCategory(cat: String) {
        _filters.value = _filters.value.copy(selectedCategory = cat)
    }

    fun updateMaxRent(maxKes: Long?) {
        _filters.value = _filters.value.copy(maxRentKes = maxKes)
    }

    fun toggleVerifiedOnly() {
        _filters.value = _filters.value.copy(verifiedOnly = !_filters.value.verifiedOnly)
    }

    fun toggleSavedOnly() {
        _filters.value = _filters.value.copy(savedOnly = !_filters.value.savedOnly)
    }

    fun toggleSaveUnit(unit: RentalUnit) {
        if (currentUserId.isNullOrBlank()) {
            val current = _localSavedIds.value
            val nowSaved = unit.id !in current
            _localSavedIds.value = if (nowSaved) current + unit.id else current - unit.id
            _feedback.value = if (nowSaved) {
                "Saved for this session — sign in to sync bookmarks"
            } else {
                "Removed from saved listings"
            }
            return
        }
        viewModelScope.launch {
            val currentlySaved = unit.id in savedUnitIds.value
            val res = propertyRepo.toggleSavedUnit(currentUserId, unit, currentlySaved)
            _feedback.value = res.fold(
                onSuccess = { saved ->
                    if (saved) "Saved ${unit.propertyName} • ${unit.unitNumber}" else "Removed from saved"
                },
                onFailure = { it.localizedMessage ?: "Could not update saved listing" }
            )
        }
    }

    fun toggleCompareUnit(unitId: String) {
        val current = _compareUnitIds.value
        if (unitId in current) {
            _compareUnitIds.value = current - unitId
        } else if (current.size >= 3) {
            _feedback.value = "You can compare up to 3 units side by side"
        } else {
            _compareUnitIds.value = current + unitId
        }
    }

    fun clearCompareUnits() {
        _compareUnitIds.value = emptyList()
    }

    fun reportListingScam(
        unit: RentalUnit,
        reason: String,
        details: String,
        reporterName: String,
        onDone: () -> Unit
    ) {
        if (currentUserId.isNullOrBlank()) {
            _feedback.value = "Sign in with Google to submit a listing safety report"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            val res = propertyRepo.submitListingScamReport(unit, reason, details, reporterName)
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = "Safety report submitted to RentFlow Trust & Admin review"
                    onDone()
                },
                onFailure = { _feedback.value = it.localizedMessage ?: "Failed to submit report" }
            )
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun submitApplication(
        unit: RentalUnit,
        applicantName: String,
        applicantPhone: String,
        applicantEmail: String?,
        occupation: String?,
        desiredMoveInDate: String,
        notes: String?,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _busy.value = true
            val res = propertyRepo.submitApplication(
                unit, applicantName, applicantPhone, applicantEmail, occupation, desiredMoveInDate, notes
            )
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = "Application submitted for Unit ${unit.unitNumber}"
                    onDone()
                },
                onFailure = {
                    _feedback.value = it.localizedMessage ?: "Failed to submit application"
                }
            )
        }
    }

    fun withdrawApplication(applicationId: String) {
        viewModelScope.launch {
            _busy.value = true
            val res = propertyRepo.withdrawApplication(applicationId)
            _busy.value = false
            _feedback.value = res.fold(
                onSuccess = { "Application withdrawn" },
                onFailure = { it.localizedMessage ?: "Could not withdraw application" }
            )
        }
    }
}
