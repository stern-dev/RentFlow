package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.FirestoreService
import com.example.domain.model.Property
import com.example.domain.model.PropertyCategory
import com.example.domain.model.RentalUnit
import com.example.domain.model.UnitStatus
import com.example.domain.model.UnitType
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RentalUnitAndServiceTest {

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

    private fun createService(): FirestoreService {
        val db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
        val auth = FirebaseAuth.getInstance()
        return FirestoreService(db = db, auth = auth)
    }

    @Test
    fun `RentalUnit toCreateMap includes unit number, type, rent, deposit, status, and amenities while omitting id and blank optionals`() {
        val unit = RentalUnit(
            id = "ignored_doc_id",
            propertyId = "prop_01",
            propertyName = "Kilimani Heights",
            propertyCity = "Nairobi",
            propertyCategory = PropertyCategory.APARTMENTS.name,
            orgId = "org_01",
            ownerUid = "owner_01",
            authorizedUids = listOf("owner_01"),
            unitNumber = "A-204",
            unitType = UnitType.TWO_BEDROOM.name,
            rentCents = 4500000L,
            depositCents = 4500000L,
            status = UnitStatus.AVAILABLE.name,
            amenities = listOf("Wi-Fi", "Borehole Water", "Backup Generator"),
            description = "Corner 2BR unit",
            published = true,
            currentTenantUid = "   ",
            currentLeaseId = null
        )

        val map = unit.toCreateMap()
        assertFalse(map.containsKey("id"))
        assertFalse(map.containsKey("currentTenantUid"))
        assertFalse(map.containsKey("currentLeaseId"))
        assertEquals("A-204", map["unitNumber"])
        assertEquals("TWO_BEDROOM", map["unitType"])
        assertEquals(4500000L, map["rentCents"])
        assertEquals(4500000L, map["depositCents"])
        assertEquals("AVAILABLE", map["status"])
        assertEquals(listOf("Wi-Fi", "Borehole Water", "Backup Generator"), map["amenities"])
        assertEquals(FieldValue.serverTimestamp(), map["createdAt"])
        assertEquals(FieldValue.serverTimestamp(), map["updatedAt"])
        assertEquals(45000L, unit.monthlyRentKes())
        assertEquals(45000L, unit.depositKes())
    }

    @Test
    fun `FirestoreService unit CRUD fails safely when unauthenticated or parent property invalid`() = runTest {
        val service = createService()
        val parentProp = Property(
            id = "prop_1",
            orgId = "org_1",
            ownerUid = "owner_1",
            authorizedUids = listOf("owner_1"),
            name = "Savannah Court"
        )
        val unit = RentalUnit(
            unitNumber = "B1",
            unitType = UnitType.BEDSITTER.name,
            rentCents = 1500000L,
            depositCents = 1500000L,
            status = UnitStatus.AVAILABLE.name,
            amenities = listOf("Water", "Security")
        )

        assertTrue(service.createUnit(parentProp, unit).isFailure)
        assertTrue(service.createUnit("", unit).isFailure)
        assertTrue(service.getUnit("prop_1", "unit_1").isFailure)
        assertTrue(service.getUnitsForProperty("prop_1").isFailure)
        assertTrue(service.getUnitsForProperty("").isFailure)
        assertTrue(
            service.updateUnit(
                propertyId = "prop_1",
                unitId = "unit_1",
                unitNumber = "B1",
                unitType = UnitType.BEDSITTER,
                rentCents = 1600000L,
                depositCents = 1500000L,
                status = UnitStatus.AVAILABLE,
                amenities = listOf("Water")
            ).isFailure
        )
        assertTrue(service.updateUnit("prop_1", unit.copy(id = "")).isFailure)
        val mismatchedUnit = unit.copy(id = "unit_1", propertyId = "prop_other")
        val mismatchResult = service.updateUnit("prop_1", mismatchedUnit)
        assertTrue(mismatchResult.isFailure)
        assertTrue(mismatchResult.exceptionOrNull() is SecurityException)
        assertTrue(service.deleteUnit("prop_1", "unit_1").isFailure)
        assertTrue(service.deleteUnit("prop_1", unit.copy(id = "")).isFailure)
        val deleteMismatch = service.deleteUnit("prop_1", mismatchedUnit)
        assertTrue(deleteMismatch.isFailure)
        assertTrue(deleteMismatch.exceptionOrNull() is SecurityException)
    }

    @Test
    fun `observeUnitsForProperty returns empty flow for blank propertyId or blank userId`() = runTest {
        val service = createService()
        assertTrue(service.observeUnitsForProperty("", "user_1").first().isEmpty())
        assertTrue(service.observeUnitsForProperty("prop_1", "").first().isEmpty())
        assertTrue(service.observeUnitsForProperty("prop_1", null).first().isEmpty())
    }
}
