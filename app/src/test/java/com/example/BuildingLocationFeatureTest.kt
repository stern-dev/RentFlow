package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.location.DirectionsLauncher
import com.example.data.location.MapsApiKeyStatus
import com.example.data.location.ResolvedPickedLocation
import com.example.domain.location.BuildingLocationResolver
import com.example.domain.model.Organization
import com.example.domain.model.Property
import com.example.domain.model.PropertyCategory
import com.example.domain.model.RentalUnit
import com.example.domain.model.UnitStatus
import com.example.domain.model.UnitType
import com.example.domain.validation.PropertyUnitSchemas
import com.example.domain.validation.ValidationResult
import com.example.ui.components.BuildingLocationPickerDialog
import com.example.ui.components.BuildingMapPreviewCard
import com.example.ui.components.DiscoveryMapView
import com.example.ui.screens.PropertyEditorDialog
import com.example.ui.theme.RentFlowTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BuildingLocationFeatureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("1:1234567890:android:abcdef123456")
                    .setApiKey("AIzaSyDummyApiKeyForUnitTestingOnly")
                    .setProjectId("rentflow-test-project")
                    .build()
            )
        }
    }

    @Test
    fun `BuildingLocationResolver resolves exact pins, Kenya equator, and city fallbacks`() {
        assertFalse(BuildingLocationResolver.hasValidCoordinates(null, null))
        assertFalse(BuildingLocationResolver.hasValidCoordinates(-1.29, null))
        assertFalse(BuildingLocationResolver.hasValidCoordinates(0.0, 0.0))
        assertFalse(BuildingLocationResolver.hasValidCoordinates(95.0, 36.8))
        assertFalse(BuildingLocationResolver.hasValidCoordinates(Double.NaN, 36.8))

        // Kenya Equator (Nanyuki) has latitude 0.0 and valid longitude 37.0740
        assertTrue(BuildingLocationResolver.hasValidCoordinates(0.0, 37.0740))

        val unpinnedKisumu = Property(
            id = "prop_ksm",
            name = "Milimani Court",
            city = "Kisumu",
            county = "Kisumu",
            neighborhood = "Milimani",
            addressLine = "Tom Mboya Dr"
        )
        val kisumuResolved = BuildingLocationResolver.resolve(unpinnedKisumu)
        assertFalse(kisumuResolved.isExactPin)
        assertEquals(-0.1055, kisumuResolved.latitude, 0.001)
        assertEquals(34.7568, kisumuResolved.longitude, 0.001)
        assertTrue(kisumuResolved.sourceLabel.contains("Approximate"))

        val pinnedKilimani = Property(
            id = "prop_nbo",
            name = "Kilimani Ridge",
            city = "Nairobi",
            latitude = -1.2896,
            longitude = 36.7831,
            formattedAddress = "Argwings Kodhek Rd, Kilimani, Nairobi",
            locationVerified = true
        )
        val nboResolved = BuildingLocationResolver.resolve(pinnedKilimani)
        assertTrue(nboResolved.isExactPin)
        assertEquals(-1.2896, nboResolved.latitude, 0.0001)
        assertEquals(36.7831, nboResolved.longitude, 0.0001)
        assertEquals("Argwings Kodhek Rd, Kilimani, Nairobi", nboResolved.addressSummary)
    }

    @Test
    fun `BuildingLocationResolver groups published units by building and prioritizes exact pins`() {
        val approxUnit = RentalUnit(
            id = "u1",
            propertyId = "prop_approx",
            propertyName = "Nyali Breeze",
            propertyCity = "Mombasa",
            unitNumber = "A1",
            rentCents = 2200000L,
            published = true
        )
        val exactUnit1 = RentalUnit(
            id = "u2",
            propertyId = "prop_exact",
            propertyName = "Kilimani Heights",
            propertyCity = "Nairobi",
            unitNumber = "B1",
            rentCents = 4200000L,
            published = true,
            latitude = -1.2896,
            longitude = 36.7831,
            formattedAddress = "Kilimani, Nairobi",
            locationVerified = true
        )
        val exactUnit2 = RentalUnit(
            id = "u3",
            propertyId = "prop_exact",
            propertyName = "Kilimani Heights",
            propertyCity = "Nairobi",
            unitNumber = "B2",
            rentCents = 3500000L,
            published = true,
            latitude = -1.2896,
            longitude = 36.7831,
            formattedAddress = "Kilimani, Nairobi",
            locationVerified = true
        )

        val groups = BuildingLocationResolver.groupUnitsByBuilding(listOf(approxUnit, exactUnit1, exactUnit2))
        assertEquals(2, groups.size)
        // Exact pin group appears first
        assertEquals("prop_exact", groups[0].propertyId)
        assertEquals(2, groups[0].units.size)
        assertEquals(3500000L, groups[0].minRentCents)
        assertEquals(4200000L, groups[0].maxRentCents)
        assertTrue(groups[0].location.isExactPin)

        assertEquals("prop_approx", groups[1].propertyId)
        assertFalse(groups[1].location.isExactPin)
        assertEquals(-4.0435, groups[1].location.latitude, 0.01)
    }

    @Test
    fun `PropertyUnitSchemas and toCreateMap enforce coordinate rules and omit nulls`() {
        val baseProp = Property(
            orgId = "org_1",
            ownerUid = "owner_1",
            authorizedUids = listOf("owner_1"),
            name = "Westlands Suites",
            category = PropertyCategory.APARTMENTS.name,
            city = "Nairobi",
            county = "Nairobi",
            neighborhood = "Westlands",
            addressLine = "Waiyaki Way",
            description = "Apartments"
        )
        val noPinMap = baseProp.toCreateMap()
        assertFalse(noPinMap.containsKey("latitude"))
        assertFalse(noPinMap.containsKey("longitude"))
        assertFalse(noPinMap.containsKey("formattedAddress"))
        assertFalse(noPinMap.containsKey("placeId"))
        assertEquals(false, noPinMap["locationVerified"])

        val unpairedResult = PropertyUnitSchemas.propertyCreateSchema.parse(
            baseProp.copy(latitude = -1.2673, longitude = null)
        )
        assertTrue(unpairedResult is ValidationResult.Invalid)

        val nullIslandResult = PropertyUnitSchemas.propertyCreateSchema.parse(
            baseProp.copy(latitude = 0.0, longitude = 0.0)
        )
        assertTrue(nullIslandResult is ValidationResult.Invalid)

        val validResult = PropertyUnitSchemas.propertyCreateSchema.parse(
            baseProp.copy(
                latitude = -1.2673,
                longitude = 36.8111,
                formattedAddress = "Waiyaki Way, Westlands, Nairobi",
                locationVerified = true
            )
        )
        assertTrue(validResult is ValidationResult.Valid)

        val unitMap = RentalUnit(
            propertyId = "p1",
            propertyName = "Westlands Suites",
            propertyCity = "Nairobi",
            orgId = "org_1",
            ownerUid = "owner_1",
            authorizedUids = listOf("owner_1"),
            unitNumber = "C4",
            unitType = UnitType.ONE_BEDROOM.name,
            rentCents = 3000000L,
            depositCents = 3000000L,
            status = UnitStatus.AVAILABLE.name,
            latitude = -1.2673,
            longitude = 36.8111,
            formattedAddress = "Waiyaki Way, Westlands",
            locationVerified = true
        ).toCreateMap()
        assertEquals(-1.2673, unitMap["latitude"])
        assertEquals(36.8111, unitMap["longitude"])
        assertEquals(true, unitMap["locationVerified"])
    }

    @Test
    fun `DirectionsLauncher URIs and MapsApiKeyStatus placeholder check behave safely`() {
        val webUri = DirectionsLauncher.buildGoogleMapsWebUri(-1.2896, 36.7831).toString()
        assertTrue(webUri.startsWith("https://www.google.com/maps/dir/?api=1&destination=-1.289600,36.783100"))

        val geoUri = DirectionsLauncher.buildGeoPinUri(-1.2896, 36.7831, "Kilimani Heights").toString()
        assertTrue(geoUri.startsWith("geo:-1.289600,36.783100?q=-1.289600,36.783100("))

        // In default workspace builds without Secrets panel key, BuildConfig.MAPS_API_KEY is placeholder
        assertFalse(MapsApiKeyStatus.isConfigured())
    }

    @Test
    fun `BuildingMapPreviewCard renders exact vs approximate status and triggers directions`() {
        var directionsClicked = false
        val approxLoc = BuildingLocationResolver.resolve(
            city = "Mombasa",
            neighborhood = "Nyali",
            addressLine = "Links Rd"
        )

        composeTestRule.setContent {
            RentFlowTheme {
                BuildingMapPreviewCard(
                    buildingName = "Nyali Court",
                    location = approxLoc,
                    onDirectionsClick = { directionsClicked = true },
                    testTag = "test_building_card"
                )
            }
        }

        composeTestRule.onNodeWithTag("test_building_card").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Nyali Court").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("Approximate City Center").assertIsDisplayed()
        composeTestRule.onNodeWithTag("test_building_card_directions_button").performClick()
        assertTrue(directionsClicked)
    }

    @Test
    fun `DiscoveryMapView displays building pins preview and forwards unit actions`() {
        val unit = RentalUnit(
            id = "u_ksm",
            propertyId = "prop_ksm",
            propertyName = "Milimani Palms",
            propertyCity = "Kisumu",
            unitNumber = "B2",
            rentCents = 2500000L,
            published = true,
            landlordVerified = true,
            latitude = -0.1055,
            longitude = 34.7568,
            formattedAddress = "Milimani, Kisumu",
            locationVerified = true
        )
        val groups = BuildingLocationResolver.groupUnitsByBuilding(listOf(unit))
        var openedUnitId: String? = null

        composeTestRule.setContent {
            RentFlowTheme {
                DiscoveryMapView(
                    pinGroups = groups,
                    selectedCityFilter = "All",
                    onOpenUnitDetails = { openedUnitId = it.id },
                    onApplyUnit = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("discovery_map_view").assertIsDisplayed()
        composeTestRule.onNodeWithTag("discovery_map_pin_preview").assertIsDisplayed()
        composeTestRule.onNodeWithText("Exact Map Pin").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_pin_view_details_button").performClick()
        assertEquals("u_ksm", openedUnitId)
    }

    @Test
    fun `BuildingLocationPickerDialog allows selecting Kenyan preset and confirming exact pin`() {
        var confirmed: ResolvedPickedLocation? = null

        composeTestRule.setContent {
            RentFlowTheme {
                BuildingLocationPickerDialog(
                    initialPropertyName = "Kileleshwa Heights",
                    initialCity = "Nairobi",
                    initialCounty = "Nairobi",
                    initialNeighborhood = "",
                    initialAddressLine = "",
                    initialLatitude = null,
                    initialLongitude = null,
                    initialFormattedAddress = null,
                    initialPlaceId = null,
                    onDismiss = {},
                    onConfirmLocation = { confirmed = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("building_location_picker_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("location_preset_0").performClick()
        composeTestRule.onNodeWithTag("confirm_building_pin_button").performScrollTo().performClick()

        assertNotNull(confirmed)
        assertEquals(-1.2896, confirmed!!.latitude, 0.0001)
        assertEquals(36.7831, confirmed!!.longitude, 0.0001)
        assertEquals("Kilimani", confirmed!!.neighborhood)
    }

    @Test
    fun `PropertyEditorDialog opens BuildingLocationPickerDialog and displays unpinned state`() {
        val org = Organization(id = "org_1", name = "Savannah Homes", ownerUid = "owner_1")

        composeTestRule.setContent {
            RentFlowTheme {
                PropertyEditorDialog(
                    organizations = listOf(org),
                    initialOrgId = "org_1",
                    ownerUid = "owner_1",
                    existingProperty = null,
                    isBusy = false,
                    onDismiss = {},
                    onSave = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithTag("open_location_picker_button").performScrollTo().performClick()
        composeTestRule.waitUntil(3_000) {
            composeTestRule.onAllNodesWithTag("building_location_picker_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("building_location_picker_dialog").assertIsDisplayed()
    }
}
