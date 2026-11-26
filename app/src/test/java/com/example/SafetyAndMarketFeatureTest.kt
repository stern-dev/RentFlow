package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.example.data.notifications.NotificationHelper
import com.example.data.repository.PropertyRepository
import com.example.data.repository.SecurityPreferences
import com.example.data.repository.SecurityPreferencesRepository
import com.example.data.repository.SessionTimeoutOption
import com.example.data.security.BiometricAvailability
import com.example.domain.finance.MpesaSmsParser
import com.example.domain.finance.RentReminderEngine
import com.example.domain.finance.RentReminderUrgency
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.KenyaCounties
import com.example.domain.model.Lease
import com.example.domain.model.LeaseStatus
import com.example.domain.model.RentalUnit
import com.example.domain.model.SavedUnit
import com.example.domain.model.UnitStatus
import com.example.domain.model.UnitType
import com.example.ui.BiometricAppLockOverlay
import com.example.ui.screens.AppSecurityPrivacyCard
import com.example.ui.screens.KenyaMriTaxEstimateCard
import com.example.ui.screens.TenantPaymentSubmissionDialog
import com.example.ui.screens.UnitComparisonContent
import com.example.ui.theme.RentFlowTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SafetyAndMarketFeatureTest {

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
    fun `MpesaSmsParser parses Paybill Send Money and decimals accurately`() {
        val paybillSms = "UBK7X92M10 Confirmed. Ksh25,500.00 sent to Kilimani Homes for account KH-A101 on 20/11/26 at 9:15 AM."
        val parsedPaybill = MpesaSmsParser.parse(paybillSms)
        assertNotNull(parsedPaybill)
        assertEquals("UBK7X92M10", parsedPaybill!!.transactionCode)
        assertEquals(2_550_000L, parsedPaybill.amountCents)
        assertTrue(parsedPaybill.recipientOrAccount?.contains("Kilimani Homes") == true)

        val tillSms = "SJK45AB89C Confirmed. KES 18,000 paid to NYALI ESTATES TILL 882211 on 19/11/26."
        val parsedTill = MpesaSmsParser.parse(tillSms)
        assertNotNull(parsedTill)
        assertEquals("SJK45AB89C", parsedTill!!.transactionCode)
        assertEquals(1_800_000L, parsedTill.amountCents)

        assertNull(MpesaSmsParser.parse("Hello landlord I paid rent today"))
        assertNull(MpesaSmsParser.parse(""))
    }

    @Test
    fun `MpesaSmsParser computes 7_5 percent Kenya MRI tax and builds account references`() {
        // Gross collected: KES 100,000 -> 10,000,000 cents -> 7.5% = KES 7,500 (750,000 cents)
        assertEquals(750_000L, MpesaSmsParser.computeKenyaMriTaxCents(10_000_000L))
        assertEquals(0L, MpesaSmsParser.computeKenyaMriTaxCents(0L))
        assertEquals(0L, MpesaSmsParser.computeKenyaMriTaxCents(-500L))

        assertEquals("KH-A101", MpesaSmsParser.buildMpesaAccountReference("KH-", "A 101"))
        assertEquals("UNIT-B4", MpesaSmsParser.buildMpesaAccountReference(null, "B4"))
        assertTrue(KenyaCounties.MAJOR_COUNTIES.contains("Nairobi"))
        assertTrue(KenyaCounties.MAJOR_COUNTIES.contains("Mombasa"))
    }

    @Test
    fun `RentReminderEngine evaluates due windows and enforces 20h cooldown`() {
        val now = 1_763_630_400_000L // fixed mid-day timestamp
        val dayMs = TimeUnit.DAYS.toMillis(1)

        val overdueInv = Invoice(
            id = "inv_overdue",
            unitNumber = "A1",
            propertyName = "Kilimani Palms",
            billingPeriod = "2026-11",
            dueDateEpochMs = now - 2 * dayMs,
            outstandingCents = 2_500_000L,
            status = InvoiceStatus.OVERDUE.name
        )
        val dueTodayInv = Invoice(
            id = "inv_today",
            unitNumber = "B2",
            propertyName = "Kilimani Palms",
            billingPeriod = "2026-11",
            dueDateEpochMs = now,
            outstandingCents = 3_000_000L,
            status = InvoiceStatus.UNPAID.name
        )
        val dueSoonInv = Invoice(
            id = "inv_soon",
            unitNumber = "C3",
            propertyName = "Kilimani Palms",
            billingPeriod = "2026-11",
            dueDateEpochMs = now + 2 * dayMs,
            outstandingCents = 2_000_000L,
            status = InvoiceStatus.PARTIALLY_PAID.name
        )
        val farFutureInv = Invoice(
            id = "inv_far",
            unitNumber = "D4",
            dueDateEpochMs = now + 10 * dayMs,
            outstandingCents = 2_000_000L,
            status = InvoiceStatus.UNPAID.name
        )
        val paidInv = Invoice(
            id = "inv_paid",
            unitNumber = "E5",
            dueDateEpochMs = now - dayMs,
            outstandingCents = 0L,
            status = InvoiceStatus.PAID.name
        )
        val cooledDownInv = overdueInv.copy(
            id = "inv_cooldown",
            lastReminderSentEpochMs = now - TimeUnit.HOURS.toMillis(4)
        )

        val evaluated = RentReminderEngine.evaluateInvoices(
            invoices = listOf(farFutureInv, paidInv, dueSoonInv, dueTodayInv, overdueInv, cooledDownInv),
            nowMs = now,
            respectCooldown = true
        )
        assertEquals(3, evaluated.size)
        assertEquals("inv_overdue", evaluated[0].invoice.id)
        assertEquals(RentReminderUrgency.OVERDUE, evaluated[0].urgency)
        assertEquals("inv_today", evaluated[1].invoice.id)
        assertEquals(RentReminderUrgency.DUE_TODAY, evaluated[1].urgency)
        assertEquals("inv_soon", evaluated[2].invoice.id)
        assertEquals(RentReminderUrgency.DUE_SOON, evaluated[2].urgency)

        // When ignoring cooldown (e.g. Tenant Dashboard banner), cooledDownInv is still surfaced
        val dashboardBannerList = RentReminderEngine.evaluateInvoices(
            invoices = listOf(cooledDownInv),
            nowMs = now,
            respectCooldown = false
        )
        assertEquals(1, dashboardBannerList.size)
    }

    @Test
    fun `SecurityPreferencesRepository shouldRequireUnlock handles timeouts correctly`() {
        val now = 1_700_000_000_000L
        assertFalse(
            SecurityPreferencesRepository.shouldRequireUnlock(
                SecurityPreferences(biometricLockEnabled = false, sessionTimeoutMinutes = 0),
                isAuthenticated = true,
                nowEpochMs = now
            )
        )
        assertFalse(
            SecurityPreferencesRepository.shouldRequireUnlock(
                SecurityPreferences(biometricLockEnabled = true, sessionTimeoutMinutes = 0),
                isAuthenticated = false,
                nowEpochMs = now
            )
        )
        // Cold start (no background timestamp recorded yet)
        assertTrue(
            SecurityPreferencesRepository.shouldRequireUnlock(
                SecurityPreferences(biometricLockEnabled = true, sessionTimeoutMinutes = 5, lastBackgroundEpochMs = 0L),
                isAuthenticated = true,
                nowEpochMs = now
            )
        )
        // Immediate timeout locks even after 100ms
        assertTrue(
            SecurityPreferencesRepository.shouldRequireUnlock(
                SecurityPreferences(biometricLockEnabled = true, sessionTimeoutMinutes = 0, lastBackgroundEpochMs = now - 100L),
                isAuthenticated = true,
                nowEpochMs = now
            )
        )
        // 5-min timeout does not lock at 4m 59s, does lock at 5m 00s
        assertFalse(
            SecurityPreferencesRepository.shouldRequireUnlock(
                SecurityPreferences(biometricLockEnabled = true, sessionTimeoutMinutes = 5, lastBackgroundEpochMs = now - 299_000L),
                isAuthenticated = true,
                nowEpochMs = now
            )
        )
        assertTrue(
            SecurityPreferencesRepository.shouldRequireUnlock(
                SecurityPreferences(biometricLockEnabled = true, sessionTimeoutMinutes = 5, lastBackgroundEpochMs = now - 300_000L),
                isAuthenticated = true,
                nowEpochMs = now
            )
        )
    }

    @Test
    fun `SavedUnit toCreateMap matches Firestore schema and NotificationHelper resolves rent route`() {
        val saved = SavedUnit(
            unitId = "unit_101",
            propertyId = "prop_1",
            propertyName = "Kilimani Palms",
            unitNumber = "A101",
            rentCents = 3_500_000L
        )
        val map = saved.toCreateMap()
        assertEquals("unit_101", map["unitId"])
        assertEquals("prop_1", map["propertyId"])
        assertEquals("Kilimani Palms", map["propertyName"])
        assertEquals("A101", map["unitNumber"])
        assertEquals(3_500_000L, map["rentCents"])
        assertTrue(map["savedAt"] is FieldValue)

        assertEquals(
            NotificationHelper.ROUTE_TENANT_PAY,
            NotificationHelper.resolveTargetRoute(
                eventType = NotificationHelper.EVENT_RENT_REMINDER,
                explicitRoute = null,
                recipientRole = "TENANT"
            )
        )
    }

    @Test
    fun `PropertyRepository saved units and scam report fail safely when signed out`() = runTest {
        val db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val repo = PropertyRepository(db, auth)

        val unit = RentalUnit(id = "u1", propertyId = "p1", propertyName = "Test", unitNumber = "A1", rentCents = 100_000L)
        assertTrue(repo.toggleSavedUnit("uid_1", unit, currentlySaved = false).isFailure)
        assertTrue(repo.submitListingScamReport(unit, "Fake photos", "Asked for fee before viewing", "Kamau").isFailure)
    }

    @Test
    fun `AppSecurityPrivacyCard toggles biometric lock and updates session timeout`() {
        var toggledTo: Boolean? = null
        var timeoutSelected: Int? = null

        composeTestRule.setContent {
            RentFlowTheme {
                AppSecurityPrivacyCard(
                    securityPrefs = SecurityPreferences(
                        biometricLockEnabled = false,
                        sessionTimeoutMinutes = SessionTimeoutOption.FIVE_MINUTES.minutes
                    ),
                    biometricAvailability = BiometricAvailability.AVAILABLE,
                    onToggleBiometricLock = { toggledTo = it },
                    onSessionTimeoutChange = { timeoutSelected = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("security_privacy_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("switch_biometric_lock").performClick()
        assertEquals(true, toggledTo)

        composeTestRule.onNodeWithTag("timeout_chip_1").performClick()
        assertEquals(1, timeoutSelected)
    }

    @Test
    fun `UnitComparisonBottomSheet displays Best Price Verified Landlord and triggers Apply`() {
        val cheapVerifiedUnit = RentalUnit(
            id = "u_cheap",
            propertyName = "Kilimani Palms",
            propertyCity = "Nairobi",
            unitNumber = "A1",
            unitType = UnitType.ONE_BEDROOM.name,
            rentCents = 2_800_000L,
            depositCents = 2_500_000L,
            status = UnitStatus.AVAILABLE.name,
            landlordVerified = true,
            amenities = listOf("Wi-Fi", "Borehole Water")
        )
        val priceyUnit = RentalUnit(
            id = "u_pricey",
            propertyName = "Westlands Loft",
            propertyCity = "Nairobi",
            unitNumber = "B2",
            unitType = UnitType.TWO_BEDROOM.name,
            rentCents = 4_500_000L,
            depositCents = 4_500_000L,
            status = UnitStatus.AVAILABLE.name,
            landlordVerified = false,
            amenities = listOf("Parking")
        )
        var appliedUnitId: String? = null

        composeTestRule.setContent {
            RentFlowTheme {
                UnitComparisonContent(
                    units = listOf(cheapVerifiedUnit, priceyUnit),
                    onRemoveUnit = {},
                    onApplyUnit = { appliedUnitId = it.id }
                )
            }
        }

        composeTestRule.onNodeWithTag("unit_comparison_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Best Price").assertIsDisplayed()
        composeTestRule.onNodeWithText("Verified Landlord").assertIsDisplayed()
        composeTestRule.onNodeWithText("KES 28,000").assertIsDisplayed()
        composeTestRule.onNodeWithText("KES 53,000").assertIsDisplayed()

        composeTestRule.onNodeWithText("Kilimani Palms").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unit_comparison_sheet").performTouchInput { swipeUp() }
        composeTestRule.onNodeWithTag("compare_apply_u_cheap").performClick()
        assertEquals("u_cheap", appliedUnitId)
    }

    @Test
    fun `KenyaMriTaxEstimateCard renders verified rent and 7_5 percent tax`() {
        composeTestRule.setContent {
            RentFlowTheme {
                KenyaMriTaxEstimateCard(grossCollectedCents = 20_000_000L) // KES 200,000 -> 15,000 tax
            }
        }
        composeTestRule.onNodeWithTag("kenya_mri_tax_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kenya KRA 7.5% MRI Tax Estimate").assertIsDisplayed()
        composeTestRule.onNodeWithText("KES 200,000.00").assertIsDisplayed()
        composeTestRule.onNodeWithText("KES 15,000.00").assertIsDisplayed()
    }

    @Test
    fun `TenantPaymentSubmissionDialog displays Paybill instructions and auto-fills from pasted M-Pesa SMS`() {
        val lease = Lease(
            id = "lease_1",
            unitId = "unit_1",
            unitNumber = "A101",
            propertyName = "Kilimani Palms",
            tenantUid = "tenant_1",
            tenantName = "Amina Otieno",
            tenantPhone = "+254712345678",
            rentCents = 3_000_000L,
            status = LeaseStatus.ACTIVE.name
        )
        val unit = RentalUnit(
            id = "unit_1",
            unitNumber = "A101",
            mpesaPaybill = "522522",
            mpesaAccountPrefix = "KP-"
        )

        composeTestRule.setContent {
            RentFlowTheme {
                TenantPaymentSubmissionDialog(
                    lease = lease,
                    unit = unit,
                    targetInvoice = null,
                    defaultPhone = "+254712345678",
                    isBusy = false,
                    onDismiss = {},
                    onSubmit = { _, _, _, _, _, _, _, _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithTag("mpesa_instructions_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Paybill: 522522 • Account: KP-A101").assertIsDisplayed()

        composeTestRule.onNodeWithTag("mpesa_sms_paste_input").performScrollTo()
            .performTextInput("UBK7X92M10 Confirmed. Ksh28,500.00 sent to Kilimani Palms on 20/11/26.")

        composeTestRule.onNodeWithTag("mpesa_sms_parsed_status").assertTextContains("UBK7X92M10", substring = true)
        composeTestRule.onNodeWithTag("payment_reference_input").assertTextContains("UBK7X92M10", substring = true)
        composeTestRule.onNodeWithTag("payment_amount_input").assertTextContains("28500", substring = true)
    }

    @Test
    fun `BiometricAppLockOverlay invokes unlock and sign out callbacks`() {
        var unlockCount = 0
        var signedOut = false

        composeTestRule.setContent {
            RentFlowTheme {
                BiometricAppLockOverlay(
                    userName = "Wanjiku Mwangi",
                    lockError = null,
                    onUnlockClick = { unlockCount += 1 },
                    onSignOutClick = { signedOut = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("biometric_lock_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("biometric_unlock_button").performClick()
        assertTrue(unlockCount >= 2) // initial LaunchedEffect + manual click

        composeTestRule.onNodeWithTag("biometric_sign_out_button").performClick()
        assertTrue(signedOut)
    }
}
