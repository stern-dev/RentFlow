package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.PropertyRepository
import com.example.domain.model.Property
import com.example.domain.model.PropertyCategory
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
class PropertyRepositoryTest {

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

    private fun createRepository(): PropertyRepository {
        val db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
        val auth = FirebaseAuth.getInstance()
        return PropertyRepository(db = db, auth = auth)
    }

    @Test
    fun `toCreateMap includes extended metadata when present and strips blank optional fields and id`() {
        val fullProperty = Property(
            id = "local_ignored_id",
            orgId = "org_nairobi_01",
            ownerUid = "owner_01",
            managerUids = listOf("mgr_01"),
            authorizedUids = listOf("owner_01", "mgr_01"),
            name = "Acacia Heights",
            category = PropertyCategory.APARTMENTS.name,
            city = "Nairobi",
            county = "Nairobi",
            neighborhood = "Kilimani",
            addressLine = "Argwings Kodhek Rd",
            description = "Borehole water & CCTV",
            waterMeterNumber = "NWSC-90123",
            electricityMeterNumber = "KPLC-77412",
            notes = "Caretaker phone on gate noticeboard"
        )

        val fullMap = fullProperty.toCreateMap()
        assertFalse(fullMap.containsKey("id"))
        assertEquals("org_nairobi_01", fullMap["orgId"])
        assertEquals("Nairobi", fullMap["county"])
        assertEquals("NWSC-90123", fullMap["waterMeterNumber"])
        assertEquals("KPLC-77412", fullMap["electricityMeterNumber"])
        assertEquals("Caretaker phone on gate noticeboard", fullMap["notes"])
        assertEquals(FieldValue.serverTimestamp(), fullMap["createdAt"])
        assertEquals(FieldValue.serverTimestamp(), fullMap["updatedAt"])

        val minimalProperty = Property(
            id = "should_be_omitted",
            orgId = "org_02",
            ownerUid = "owner_01",
            authorizedUids = listOf("owner_01"),
            name = "Nyali Court",
            city = "Mombasa",
            county = "   ",
            neighborhood = "Nyali",
            addressLine = "Links Rd",
            waterMeterNumber = "",
            electricityMeterNumber = null,
            notes = "  "
        )
        val minimalMap = minimalProperty.toCreateMap()
        assertFalse(minimalMap.containsKey("id"))
        assertFalse(minimalMap.containsKey("county"))
        assertFalse(minimalMap.containsKey("waterMeterNumber"))
        assertFalse(minimalMap.containsKey("electricityMeterNumber"))
        assertFalse(minimalMap.containsKey("notes"))
    }

    @Test
    fun `createProperty fails when orgId is blank or user is unauthenticated`() = runTest {
        val repo = createRepository()
        val result = repo.createProperty(
            orgId = "   ",
            name = "Kilimani Court",
            category = PropertyCategory.APARTMENTS,
            city = "Nairobi",
            county = "Nairobi"
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `observePropertiesForOrganization and observeUnitsForOrganization return empty flow for blank inputs`() = runTest {
        val repo = createRepository()
        assertEquals(emptyList<Property>(), repo.observePropertiesForOrganization("", "user_1").first())
        assertEquals(emptyList<Property>(), repo.observePropertiesForOrganization("org_1", "   ").first())
        assertTrue(repo.observeUnitsForOrganization("", "user_1").first().isEmpty())
        assertTrue(repo.observeUnitsForOrganization("org_1", "").first().isEmpty())
    }
}
