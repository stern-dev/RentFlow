package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.notifications.FcmTokenRepository
import com.example.data.notifications.NotificationHelper
import com.example.domain.finance.TenantMonthTrendStatus
import com.example.domain.finance.TenantPaymentTrendPoint
import com.example.domain.finance.TenantPaymentTrendsSummary
import com.example.domain.model.FcmDeviceToken
import com.example.domain.model.UserRole
import com.example.ui.components.RentPaymentTrendsCard
import com.example.ui.screens.PushNotificationPermissionCard
import com.example.ui.theme.RentFlowTheme
import com.google.firebase.firestore.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PushNotificationFeatureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        NotificationHelper.clearDedupCacheForTesting()
        Shadows.shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `FcmDeviceToken toCreateMap matches Firestore blueprint and rules`() {
        val token = FcmDeviceToken(
            id = "doc_ignored",
            userId = "uid_tenant_1",
            token = "fcm_token_example_1234567890",
            platform = "ANDROID",
            notifyMaintenance = true,
            notifyRentDue = false,
            activeRole = UserRole.TENANT.name,
            appVersion = "1.0"
        )
        val map = token.toCreateMap()
        assertFalse(map.containsKey("id"))
        assertEquals("uid_tenant_1", map["userId"])
        assertEquals("fcm_token_example_1234567890", map["token"])
        assertEquals("ANDROID", map["platform"])
        assertEquals(true, map["notifyMaintenance"])
        assertEquals(false, map["notifyRentDue"])
        assertEquals("TENANT", map["activeRole"])
        assertEquals("1.0", map["appVersion"])
        assertTrue(map["createdAt"] is FieldValue)
        assertTrue(map["updatedAt"] is FieldValue)

        val noVersion = token.copy(appVersion = " ").toCreateMap()
        assertFalse(noVersion.containsKey("appVersion"))
    }

    @Test
    fun `FcmTokenRepository tokenDocId produces deterministic 32-char hex Firestore IDs`() {
        val idA1 = FcmTokenRepository.tokenDocId("sample_fcm_token_alpha_123")
        val idA2 = FcmTokenRepository.tokenDocId("  sample_fcm_token_alpha_123  ")
        val idB = FcmTokenRepository.tokenDocId("sample_fcm_token_beta_456")

        assertEquals(idA1, idA2)
        assertNotEquals(idA1, idB)
        assertTrue(idA1.matches(Regex("^[a-f0-9]{32}$")))
        assertTrue(idB.matches(Regex("^[a-f0-9]{32}$")))
    }

    @Test
    fun `NotificationHelper resolves landlord and tenant routes tabs and deep links`() {
        assertEquals(
            NotificationHelper.ROUTE_LANDLORD_OPERATIONS,
            NotificationHelper.resolveTargetRoute(
                eventType = NotificationHelper.EVENT_MAINTENANCE_CREATED,
                explicitRoute = null,
                recipientRole = "LANDLORD"
            )
        )
        assertEquals(
            NotificationHelper.ROUTE_TENANT_REPAIRS,
            NotificationHelper.resolveTargetRoute(
                eventType = NotificationHelper.EVENT_MAINTENANCE_STATUS_CHANGED,
                explicitRoute = null,
                recipientRole = "TENANT"
            )
        )
        assertEquals(
            NotificationHelper.ROUTE_TENANT_PAY,
            NotificationHelper.resolveTargetRoute(
                eventType = NotificationHelper.EVENT_INVOICE_GENERATED,
                explicitRoute = null,
                recipientRole = "TENANT"
            )
        )
        assertEquals(
            NotificationHelper.TAB_TENANT_INVOICES,
            NotificationHelper.resolveTargetTab(NotificationHelper.EVENT_INVOICE_GENERATED, null)
        )
        assertEquals(
            NotificationHelper.TAB_TENANT_PAYMENTS,
            NotificationHelper.resolveTargetTab(NotificationHelper.EVENT_PAYMENT_CONFIRMED, null)
        )
        assertEquals(
            NotificationHelper.ROUTE_STAFF_JOBS,
            NotificationHelper.resolveTargetRoute(
                eventType = null,
                explicitRoute = "staff_jobs",
                recipientRole = "MAINTENANCE_STAFF"
            )
        )

        val invoiceIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(NotificationHelper.EXTRA_TARGET_ROUTE, NotificationHelper.ROUTE_TENANT_PAY)
            putExtra(NotificationHelper.EXTRA_TARGET_TAB, NotificationHelper.TAB_TENANT_INVOICES)
            putExtra(NotificationHelper.EXTRA_INVOICE_ID, "inv_202611")
            putExtra(NotificationHelper.EXTRA_UNIT_ID, "unit_4B")
        }
        val parsedInvoice = NotificationHelper.parseDeepLinkIntent(invoiceIntent)
        assertNotNull(parsedInvoice)
        assertEquals(NotificationHelper.ROUTE_TENANT_PAY, parsedInvoice!!.targetRoute)
        assertEquals(NotificationHelper.TAB_TENANT_INVOICES, parsedInvoice.targetTab)
        assertEquals("inv_202611", parsedInvoice.invoiceId)
        assertNull(parsedInvoice.paymentId)
        assertEquals("unit_4B", parsedInvoice.unitId)

        val paymentIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(NotificationHelper.EXTRA_TARGET_ROUTE, NotificationHelper.ROUTE_TENANT_PAY)
            putExtra(NotificationHelper.EXTRA_TARGET_TAB, NotificationHelper.TAB_TENANT_PAYMENTS)
            putExtra(NotificationHelper.EXTRA_PAYMENT_ID, "pay_991")
        }
        val parsedPayment = NotificationHelper.parseDeepLinkIntent(paymentIntent)
        assertNotNull(parsedPayment)
        assertEquals(NotificationHelper.ROUTE_TENANT_PAY, parsedPayment!!.targetRoute)
        assertEquals(NotificationHelper.TAB_TENANT_PAYMENTS, parsedPayment.targetTab)
        assertEquals("pay_991", parsedPayment.paymentId)

        assertNull(NotificationHelper.parseDeepLinkIntent(Intent(context, MainActivity::class.java)))
    }

    @Test
    fun `NotificationHelper builds matching dedup keys across FCM and Firestore alerts`() {
        val fcmNewTicketKey = NotificationHelper.buildMaintenanceDedupKey(
            requestId = "req_101",
            status = "SUBMITTED",
            eventType = NotificationHelper.EVENT_MAINTENANCE_CREATED,
            title = "New Maintenance Ticket (Urgent)"
        )
        val firestoreNewTicketKey = NotificationHelper.buildMaintenanceDedupKey(
            requestId = "req_101",
            title = "New Maintenance Ticket (Urgent)"
        )
        assertEquals(fcmNewTicketKey, firestoreNewTicketKey)

        val fcmStatusKey = NotificationHelper.buildMaintenanceDedupKey(
            requestId = "req_101",
            status = "IN_PROGRESS",
            eventType = NotificationHelper.EVENT_MAINTENANCE_STATUS_CHANGED,
            title = "Maintenance Status: In Progress"
        )
        val firestoreStatusKey = NotificationHelper.buildMaintenanceDedupKey(
            requestId = "req_101",
            title = "Maintenance Status: In Progress"
        )
        assertEquals(fcmStatusKey, firestoreStatusKey)

        assertTrue(NotificationHelper.shouldDeliverAndMark(fcmStatusKey, nowMs = 1_000L))
        assertFalse(NotificationHelper.shouldDeliverAndMark(firestoreStatusKey, nowMs = 5_000L))
        assertTrue(NotificationHelper.shouldDeliverAndMark("req_101_COMPLETED", nowMs = 6_000L))
        assertTrue(NotificationHelper.shouldDeliverAndMark(fcmStatusKey, nowMs = 35_000L))

        val invKey = NotificationHelper.buildInvoiceGeneratedDedupKey("inv_abc")
        val payKey = NotificationHelper.buildPaymentConfirmedDedupKey("pay_xyz")
        assertEquals("invoice_generated_inv_abc", invKey)
        assertEquals("payment_confirmed_pay_xyz", payKey)
        assertTrue(NotificationHelper.shouldDeliverAndMark(invKey, nowMs = 1_000L))
        assertFalse(NotificationHelper.shouldDeliverAndMark(invKey, nowMs = 2_000L))
        assertTrue(NotificationHelper.shouldDeliverAndMark(payKey, nowMs = 2_500L))
    }

    @Test
    fun `NotificationHelper creates channels and posts maintenance push notification`() {
        val posted = NotificationHelper.showMaintenancePushNotification(
            context = context,
            title = "Maintenance Status: In Progress",
            body = "Kilimani Heights • Unit B4 (Kitchen sink leak) is now In Progress",
            requestId = "req_555",
            unitId = "unit_b4",
            targetRoute = NotificationHelper.ROUTE_TENANT_REPAIRS,
            dedupKey = "req_555_IN_PROGRESS"
        )
        assertTrue(posted)

        // Duplicate delivery inside dedup window is suppressed
        val duplicatePosted = NotificationHelper.showMaintenancePushNotification(
            context = context,
            title = "Maintenance Status: In Progress",
            body = "Kilimani Heights • Unit B4 (Kitchen sink leak) is now In Progress",
            requestId = "req_555",
            unitId = "unit_b4",
            targetRoute = NotificationHelper.ROUTE_TENANT_REPAIRS,
            dedupKey = "req_555_IN_PROGRESS"
        )
        assertFalse(duplicatePosted)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = Shadows.shadowOf(nm)
        assertNotNull(nm.getNotificationChannel(NotificationHelper.CHANNEL_MAINTENANCE))
        assertEquals(1, shadowNm.allNotifications.size)
        val postedNotif = shadowNm.allNotifications.first()
        assertEquals("Maintenance Status: In Progress", postedNotif.extras.getString("android.title"))
    }

    @Test
    fun `NotificationHelper posts billing push notifications for invoice and verified payment with dedup`() {
        val invoicePosted = NotificationHelper.showBillingPushNotification(
            context = context,
            title = "New Rent Invoice • Unit B4",
            body = "INV-202611-B4: KES 25,000 due 5 Nov 2026.",
            targetRoute = NotificationHelper.ROUTE_TENANT_PAY,
            targetTab = NotificationHelper.TAB_TENANT_INVOICES,
            invoiceId = "inv_b4_202611",
            dedupKey = NotificationHelper.buildInvoiceGeneratedDedupKey("inv_b4_202611")
        )
        assertTrue(invoicePosted)

        val duplicateInvoice = NotificationHelper.showBillingPushNotification(
            context = context,
            title = "New Rent Invoice • Unit B4",
            body = "Duplicate fallback notification",
            targetRoute = NotificationHelper.ROUTE_TENANT_PAY,
            targetTab = NotificationHelper.TAB_TENANT_INVOICES,
            invoiceId = "inv_b4_202611",
            dedupKey = NotificationHelper.buildInvoiceGeneratedDedupKey("inv_b4_202611")
        )
        assertFalse(duplicateInvoice)

        val paymentPosted = NotificationHelper.showBillingPushNotification(
            context = context,
            title = "Rent Payment Confirmed • RCP-A1B2",
            body = "KES 25,000 for Kilimani Heights • Unit B4 verified.",
            targetRoute = NotificationHelper.ROUTE_TENANT_PAY,
            targetTab = NotificationHelper.TAB_TENANT_PAYMENTS,
            paymentId = "pay_b4_001",
            dedupKey = NotificationHelper.buildPaymentConfirmedDedupKey("pay_b4_001")
        )
        assertTrue(paymentPosted)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = Shadows.shadowOf(nm)
        assertNotNull(nm.getNotificationChannel(NotificationHelper.CHANNEL_RENT))
        assertEquals(2, shadowNm.allNotifications.size)
    }

    @Test
    fun `PushNotificationPermissionCard displays active and paused states`() {
        composeTestRule.setContent {
            RentFlowTheme {
                PushNotificationPermissionCard(
                    maintenanceAlertsEnabled = false,
                    rentAlertsEnabled = false
                )
            }
        }
        composeTestRule.onNodeWithTag("push_notification_status_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Paused").assertIsDisplayed()
    }

    @Test
    fun `RentPaymentTrendsCard renders empty state and interactive 12-month bars`() {
        var summary by androidx.compose.runtime.mutableStateOf(
            TenantPaymentTrendsSummary(
                points = emptyList(),
                hasHistoricalActivity = false
            )
        )
        composeTestRule.setContent {
            RentFlowTheme {
                RentPaymentTrendsCard(summary = summary)
            }
        }
        composeTestRule.onNodeWithTag("tenant_payment_trends_empty").assertIsDisplayed()

        val points = (1..12).map { idx ->
            val month = idx.toString().padStart(2, '0')
            val shortNames = listOf("Dec", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov")
            val isPartial = idx == 10
            TenantPaymentTrendPoint(
                billingPeriod = if (idx == 1) "2025-12" else "2026-${(idx - 1).toString().padStart(2, '0')}",
                shortLabel = shortNames[idx - 1],
                fullLabel = "${shortNames[idx - 1]} ${if (idx == 1) 2025 else 2026}",
                billedCents = 2500000L,
                paidCents = if (isPartial) 1500000L else 2500000L,
                outstandingCents = if (isPartial) 1000000L else 0L,
                verifiedPaymentsCount = 1,
                status = if (isPartial) TenantMonthTrendStatus.PARTIAL else TenantMonthTrendStatus.PAID_ON_TIME
            )
        }
        summary = TenantPaymentTrendsSummary(
            points = points,
            currentStreakMonths = 2,
            bestStreakMonths = 9,
            consistencyPercent = 92,
            billedMonthsCount = 12,
            settledMonthsCount = 11,
            totalBilled12MonthsCents = 30000000L,
            totalPaid12MonthsCents = 29000000L,
            hasHistoricalActivity = true
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tenant_payment_trends_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tenant_payment_streak_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 mo streak").assertIsDisplayed()
        composeTestRule.onNodeWithText("92%").assertIsDisplayed()

        composeTestRule.onNodeWithTag("trend_month_label_2026-09").performClick()
        composeTestRule.onNodeWithTag("tenant_trend_selected_detail").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sep 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("Partial").assertIsDisplayed()
    }
}
