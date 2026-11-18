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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DiscoveryFilters(
    val searchQuery: String = "",
    val selectedCity: String = "All",
    val selectedCategory: String = "All",
    val maxRentKes: Long? = null
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

    private val _filters = MutableStateFlow(DiscoveryFilters())
    val filters: StateFlow<DiscoveryFilters> = _filters.asStateFlow()

    val filteredUnits: StateFlow<List<RentalUnit>> = combine(publishedUnits, _filters) { unitsState, f ->
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
            matchQuery && matchCity && matchCategory && matchRent
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
