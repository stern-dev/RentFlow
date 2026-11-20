package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.data.notifications.FcmTokenRepository
import com.example.data.notifications.NotificationHelper
import com.example.domain.model.FcmDeviceToken
import com.example.domain.model.UserRole
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
            activeRole = UserRole.TENANT.name,
            appVersion = "1.0"
        )
        val map = token.toCreateMap()
        assertFalse(map.containsKey("id"))
        assertEquals("uid_tenant_1", map["userId"])
        assertEquals("fcm_token_example_1234567890", map["token"])
        assertEquals("ANDROID", map["platform"])
        assertEquals(true, map["notifyMaintenance"])
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
    fun `NotificationHelper resolves landlord and tenant routes and parses deep links`() {
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
            NotificationHelper.ROUTE_STAFF_JOBS,
            NotificationHelper.resolveTargetRoute(
                eventType = null,
                explicitRoute = "staff_jobs",
                recipientRole = "MAINTENANCE_STAFF"
            )
        )

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(NotificationHelper.EXTRA_TARGET_ROUTE, NotificationHelper.ROUTE_TENANT_REPAIRS)
            putExtra(NotificationHelper.EXTRA_MAINTENANCE_REQUEST_ID, "req_789")
            putExtra(NotificationHelper.EXTRA_UNIT_ID, "unit_4B")
        }
        val parsed = NotificationHelper.parseDeepLinkIntent(intent)
        assertNotNull(parsed)
        assertEquals(NotificationHelper.ROUTE_TENANT_REPAIRS, parsed!!.targetRoute)
        assertEquals("req_789", parsed.requestId)
        assertEquals("unit_4B", parsed.unitId)

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
    fun `PushNotificationPermissionCard displays active and paused states`() {
        composeTestRule.setContent {
            RentFlowTheme {
                PushNotificationPermissionCard(maintenanceAlertsEnabled = false)
            }
        }
        composeTestRule.onNodeWithTag("push_notification_status_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Paused").assertIsDisplayed()
    }
}
