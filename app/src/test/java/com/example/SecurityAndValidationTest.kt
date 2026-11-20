package com.example

import android.app.Activity
import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.GoogleCredentialGateway
import com.example.data.repository.GoogleCredentialResult
import com.example.data.repository.UserRepository
import com.example.domain.model.Expense
import com.example.domain.model.Lease
import com.example.domain.model.Organization
import com.example.domain.model.Payment
import com.example.domain.model.Property
import com.example.domain.model.PropertyCategory
import com.example.domain.model.RentalUnit
import com.example.domain.model.UnitStatus
import com.example.domain.model.UnitType
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.example.domain.security.PermissionPolicy
import com.example.domain.validation.PropertyUnitSchemas
import com.example.domain.validation.ValidationException
import com.example.domain.validation.ValidationResult
import com.example.ui.components.CriticalActionConfirmationDialog
import com.example.ui.theme.RentFlowTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurityAndValidationTest {

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
    fun `propertyCreateSchema normalizes valid input and applies city fallback for blank address fields`() {
        val raw = Property(
            orgId = "org_01",
            ownerUid = "owner_1",
            managerUids = listOf(" mgr_1 ", "mgr_1", ""),
            authorizedUids = listOf("owner_1", "mgr_1"),
            name = "  Acacia Heights  ",
            category = PropertyCategory.APARTMENTS.name,
            city = "  Nairobi ",
            county = " Nairobi ",
            neighborhood = "   ",
            addressLine = "",
            waterMeterNumber = " NWSC-101/A ",
            electricityMeterNumber = "  ",
            totalUnitsCount = 10L,
            occupiedUnitsCount = 4L
        )

        val result = PropertyUnitSchemas.propertyCreateSchema.parse(raw)
        assertTrue(result is ValidationResult.Valid)
        val valid = (result as ValidationResult.Valid).value
        assertEquals("Acacia Heights", valid.name)
        assertEquals("Nairobi", valid.city)
        assertEquals("Nairobi", valid.neighborhood)
        assertEquals("Nairobi", valid.addressLine)
        assertEquals(listOf("mgr_1"), valid.managerUids)
        assertEquals("NWSC-101/A", valid.waterMeterNumber)
        assertEquals(null, valid.electricityMeterNumber)
    }

    @Test
    fun `propertyCreateSchema rejects overlong names, bad meter chars, missing owner auth, and count mismatch without truncation`() {
        val invalid = Property(
            orgId = "org bad space",
            ownerUid = "owner_1",
            authorizedUids = listOf("mgr_only"),
            name = "X".repeat(121),
            category = "INVALID_CAT",
            city = "A",
            waterMeterNumber = "MTR#999!",
            totalUnitsCount = 3L,
            occupiedUnitsCount = 5L
        )

        val result = PropertyUnitSchemas.propertyCreateSchema.parse(invalid)
        assertTrue(result is ValidationResult.Invalid)
        val fields = (result as ValidationResult.Invalid).fieldMessages()
        assertTrue(fields.containsKey("orgId"))
        assertTrue(fields.containsKey("name"))
        assertTrue(fields.containsKey("category"))
        assertTrue(fields.containsKey("city"))
        assertTrue(fields.containsKey("waterMeterNumber"))
        assertTrue(fields.containsKey("authorizedUids"))
        assertTrue(fields.containsKey("occupiedUnitsCount"))

        val asEx = PropertyUnitSchemas.propertyCreateSchema.parseAsResult(invalid).exceptionOrNull()
        assertTrue(asEx is ValidationException)
    }

    @Test
    fun `unitCreateSchema and unitUpdateSchema enforce rent bounds, regex, and published status invariant`() {
        val invalidUnit = RentalUnit(
            propertyId = "prop_1",
            propertyName = "Acacia Heights",
            propertyCity = "Nairobi",
            propertyCategory = PropertyCategory.APARTMENTS.name,
            orgId = "org_1",
            ownerUid = "owner_1",
            authorizedUids = listOf("owner_1"),
            unitNumber = "A#101",
            unitType = UnitType.ONE_BEDROOM.name,
            rentCents = 0L,
            depositCents = -500L,
            status = UnitStatus.OCCUPIED.name,
            published = true
        )

        val createRes = PropertyUnitSchemas.unitCreateSchema.parse(invalidUnit)
        assertTrue(createRes is ValidationResult.Invalid)
        val createErrors = (createRes as ValidationResult.Invalid).fieldMessages()
        assertTrue(createErrors.containsKey("unitNumber"))
        assertTrue(createErrors.containsKey("rentCents"))
        assertTrue(createErrors.containsKey("depositCents"))
        assertTrue(createErrors.containsKey("published"))

        val updateRes = PropertyUnitSchemas.unitUpdateSchema.parse(
            PropertyUnitSchemas.RentalUnitUpdateInput(
                propertyId = "prop_1",
                unitId = "unit_1",
                unitNumber = "  A-101 ",
                unitType = UnitType.ONE_BEDROOM.name,
                rentCents = 3_500_000L,
                depositCents = 3_500_000L,
                status = UnitStatus.AVAILABLE.name,
                amenities = listOf(" Wi-Fi ", "Wi-Fi", "Borehole"),
                published = true
            )
        )
        assertTrue(updateRes is ValidationResult.Valid)
        val cleaned = (updateRes as ValidationResult.Valid).value
        assertEquals("A-101", cleaned.unitNumber)
        assertEquals(listOf("Wi-Fi", "Borehole"), cleaned.amenities)
        assertTrue(cleaned.published)
    }

    @Test
    fun `PermissionPolicy locks assignable roles and separates Owner from Property Manager privileges`() {
        val landlordProfile = UserProfile(
            userId = "owner_1",
            roles = listOf(UserRole.LANDLORD.name, UserRole.PROPERTY_MANAGER.name),
            activeRole = UserRole.LANDLORD.name
        )
        val escalatedProfile = UserProfile(
            userId = "user_2",
            roles = listOf(UserRole.TENANT.name, UserRole.ADMIN.name),
            activeRole = UserRole.TENANT.name
        )

        assertEquals(
            listOf(UserRole.LANDLORD, UserRole.PROPERTY_MANAGER),
            PermissionPolicy.assignableRolesForProfile(landlordProfile, isBootstrappedAdmin = false)
        )
        assertEquals(
            listOf(UserRole.TENANT),
            PermissionPolicy.assignableRolesForProfile(escalatedProfile, isBootstrappedAdmin = false)
        )
        assertEquals(
            listOf(UserRole.TENANT, UserRole.ADMIN),
            PermissionPolicy.assignableRolesForProfile(escalatedProfile, isBootstrappedAdmin = true)
        )
        assertFalse(PermissionPolicy.canSwitchToRole(landlordProfile, UserRole.TENANT, isBootstrappedAdmin = false))
        assertFalse(PermissionPolicy.canSwitchToRole(escalatedProfile, UserRole.ADMIN, isBootstrappedAdmin = false))
        assertTrue(PermissionPolicy.canSwitchToRole(landlordProfile, UserRole.PROPERTY_MANAGER, isBootstrappedAdmin = false))

        val org = Organization(id = "org_1", ownerUid = "owner_1", memberUids = listOf("owner_1", "mgr_1"))
        val prop = Property(
            id = "prop_1",
            orgId = "org_1",
            ownerUid = "owner_1",
            managerUids = listOf("mgr_1"),
            authorizedUids = listOf("owner_1", "mgr_1")
        )
        val unit = RentalUnit(id = "unit_1", propertyId = "prop_1", ownerUid = "owner_1", authorizedUids = listOf("owner_1", "mgr_1"))
        val lease = Lease(id = "lease_1", ownerUid = "owner_1", authorizedUids = listOf("owner_1", "mgr_1", "tenant_1"))
        val payment = Payment(id = "pay_1", ownerUid = "owner_1", authorizedUids = listOf("owner_1", "mgr_1", "tenant_1"))
        val expense = Expense(id = "exp_1", ownerUid = "owner_1", authorizedUids = listOf("owner_1", "mgr_1"))

        // Owner acting as LANDLORD has structural, pricing, lease termination, and reversal rights
        assertTrue(PermissionPolicy.canInviteTeamMembers("owner_1", UserRole.LANDLORD, org))
        assertTrue(PermissionPolicy.canManagePropertyStructure("owner_1", UserRole.LANDLORD, prop))
        assertTrue(PermissionPolicy.canAssignPropertyManager("owner_1", UserRole.LANDLORD, prop))
        assertTrue(PermissionPolicy.canDeleteProperty("owner_1", UserRole.LANDLORD, prop))
        assertTrue(PermissionPolicy.canCreateUnit("owner_1", UserRole.LANDLORD, prop))
        assertTrue(PermissionPolicy.canEditUnitPricingOrDelete("owner_1", UserRole.LANDLORD, unit))
        assertTrue(PermissionPolicy.canTerminateLease("owner_1", UserRole.LANDLORD, lease))
        assertTrue(PermissionPolicy.canReversePayment("owner_1", UserRole.LANDLORD, payment))
        assertTrue(PermissionPolicy.canDeleteExpense("owner_1", UserRole.LANDLORD, expense))

        // Manager cannot perform Owner-only operations even when authorized on the property
        assertFalse(PermissionPolicy.canInviteTeamMembers("mgr_1", UserRole.PROPERTY_MANAGER, org))
        assertFalse(PermissionPolicy.canManagePropertyStructure("mgr_1", UserRole.PROPERTY_MANAGER, prop))
        assertFalse(PermissionPolicy.canAssignPropertyManager("mgr_1", UserRole.PROPERTY_MANAGER, prop))
        assertFalse(PermissionPolicy.canDeleteProperty("mgr_1", UserRole.PROPERTY_MANAGER, prop))
        assertFalse(PermissionPolicy.canCreateUnit("mgr_1", UserRole.PROPERTY_MANAGER, prop))
        assertFalse(PermissionPolicy.canEditUnitPricingOrDelete("mgr_1", UserRole.PROPERTY_MANAGER, unit))
        assertFalse(PermissionPolicy.canTerminateLease("mgr_1", UserRole.PROPERTY_MANAGER, lease))
        assertFalse(PermissionPolicy.canReversePayment("mgr_1", UserRole.PROPERTY_MANAGER, payment))
        assertFalse(PermissionPolicy.canDeleteExpense("mgr_1", UserRole.PROPERTY_MANAGER, expense))

        // Even an owner currently switched into PROPERTY_MANAGER workspace is constrained by activeRole
        assertFalse(PermissionPolicy.canReversePayment("owner_1", UserRole.PROPERTY_MANAGER, payment))
    }

    @Test
    fun `UserRepository isRecentlySignedIn and reauthenticateWithGoogle fail safely when unauthenticated`() = runTest {
        val db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
        val auth = FirebaseAuth.getInstance()
        val repo = UserRepository(
            db = db,
            auth = auth,
            credentialGateway = object : GoogleCredentialGateway {
                override suspend fun getAutoSignInCredential(context: Context, serverClientId: String) =
                    GoogleCredentialResult.NoCredential

                override suspend fun getInteractiveSignInCredential(activity: Activity, serverClientId: String) =
                    GoogleCredentialResult.Cancelled

                override suspend fun clearCredentialState(context: Context) = Unit
            }
        )

        assertFalse(repo.isRecentlySignedIn())
        assertTrue(repo.reauthenticateWithGoogle(context).isFailure)
    }

    @Test
    fun `CriticalActionConfirmationDialog enforces fresh re-authentication, reason, and confirmation phrase`() {
        var confirmedReason: String? = null
        var reauthCalled = false

        composeTestRule.setContent {
            RentFlowTheme {
                CriticalActionConfirmationDialog(
                    title = "Reverse Payment",
                    impactSummary = "Reverses verified KES 25,000 payment",
                    confirmButtonText = "Reverse Payment",
                    requiredPhrase = "REVERSE",
                    reasonLabel = "Audit Reason",
                    requireReasonMinLen = 3,
                    isRecentlyAuthenticated = false,
                    onReauthenticate = {
                        reauthCalled = true
                        Result.success(Unit)
                    },
                    onDismiss = {},
                    onConfirm = { reason -> confirmedReason = reason }
                )
            }
        }

        // Clicking confirm without reason shows audit reason validation error and blocks reauth/confirm
        composeTestRule.onNodeWithTag("critical_action_confirm_button").performClick()
        composeTestRule.onNodeWithText("Enter at least 3 characters for the audit reason.").assertExists()
        assertFalse(reauthCalled)
        assertEquals(null, confirmedReason)

        // Providing reason without phrase shows typed confirmation error
        composeTestRule.onNodeWithTag("critical_action_reason_input").performTextInput("Duplicate M-Pesa entry")
        composeTestRule.onNodeWithTag("critical_action_confirm_button").performClick()
        composeTestRule.onNodeWithText("Type REVERSE exactly to continue.").assertExists()
        assertFalse(reauthCalled)

        // Providing phrase triggers re-authentication and completes confirmation on success
        composeTestRule.onNodeWithTag("critical_action_phrase_input").performTextInput("REVERSE")
        composeTestRule.onNodeWithTag("critical_action_confirm_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(reauthCalled)
        assertEquals("Duplicate M-Pesa entry", confirmedReason)
    }
}
