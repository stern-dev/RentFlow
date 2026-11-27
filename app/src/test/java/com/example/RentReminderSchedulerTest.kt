package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.example.data.notifications.NotificationHelper
import com.example.data.notifications.RentReminderDeliveryLedger
import com.example.data.notifications.RentReminderScheduler
import com.example.data.notifications.ScheduledRentReminderWorker
import com.example.domain.finance.RentReminderEngine
import com.example.domain.finance.RentReminderUrgency
import com.example.domain.finance.ScheduledReminderStage
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceStatus
import com.example.ui.screens.PushNotificationPermissionCard
import com.example.ui.theme.RentFlowTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RentReminderSchedulerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        NotificationHelper.clearDedupCacheForTesting()
        RentReminderDeliveryLedger.clearAll(context)
        Shadows.shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun epochAt(daysFromBase: Int, hour: Int = 8, minute: Int = 0): Long {
        return Calendar.getInstance().apply {
            set(2026, Calendar.DECEMBER, 1, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, daysFromBase)
        }.timeInMillis
    }

    private fun sampleInvoice(
        id: String = "inv_dec_01",
        dueDaysOffset: Int = 10,
        status: InvoiceStatus = InvoiceStatus.ISSUED,
        totalDueCents: Long = 28_000_00L,
        paidCents: Long = 0L
    ): Invoice = Invoice(
        id = id,
        invoiceNumber = "INV-2026-1201",
        leaseId = "lease_1",
        tenantUid = "tenant_uid_1",
        tenantName = "Amina Wanjiku",
        propertyId = "prop_1",
        propertyName = "Kilimani Heights",
        unitId = "unit_4B",
        unitNumber = "4B",
        billingPeriod = "2026-12",
        rentCents = totalDueCents,
        totalDueCents = totalDueCents,
        paidCents = paidCents,
        outstandingCents = (totalDueCents - paidCents).coerceAtLeast(0L),
        dueDateEpochMs = epochAt(dueDaysOffset, hour = 0, minute = 0),
        status = status.name
    )

    @Test
    fun `buildSchedulePlans computes 3-day-prior and due-day triggers at 9 AM local time`() {
        val nowMs = epochAt(0, hour = 8, minute = 0)
        val upcomingInv = sampleInvoice(id = "inv_10d", dueDaysOffset = 10, status = InvoiceStatus.ISSUED)
        val paidInv = sampleInvoice(id = "inv_paid", dueDaysOffset = 4, status = InvoiceStatus.PAID, paidCents = 28_000_00L)
        val draftInv = sampleInvoice(id = "inv_draft", dueDaysOffset = 5, status = InvoiceStatus.DRAFT)

        val plans = RentReminderEngine.buildSchedulePlans(
            invoices = listOf(upcomingInv, paidInv, draftInv),
            nowMs = nowMs
        )

        assertEquals(2, plans.size)
        val threeDayPlan = plans.first { it.stage == ScheduledReminderStage.THREE_DAYS_BEFORE }
        val dueDayPlan = plans.first { it.stage == ScheduledReminderStage.DUE_TODAY }

        assertEquals("inv_10d", threeDayPlan.invoiceId)
        assertEquals(epochAt(7, hour = 9, minute = 0), threeDayPlan.triggerAtEpochMs)
        assertTrue(threeDayPlan.delayMs > TimeUnit.DAYS.toMillis(6))
        assertTrue(threeDayPlan.title.contains("3 Day(s)"))

        assertEquals(epochAt(10, hour = 9, minute = 0), dueDayPlan.triggerAtEpochMs)
        assertEquals(threeDayPlan, RentReminderEngine.nextScheduledReminder(plans, nowMs))
    }

    @Test
    fun `RentReminderDeliveryLedger prevents duplicate stage alerts while permitting escalation across stages`() {
        val dueDate = epochAt(10, hour = 0)
        val dayMinus3 = epochAt(7, hour = 9)
        val dayMinus2 = epochAt(8, hour = 9)
        val dueDay = epochAt(10, hour = 9)
        val overdueDay1 = epochAt(11, hour = 9)
        val overdueDay2 = epochAt(12, hour = 9)

        assertTrue(RentReminderDeliveryLedger.shouldSend(context, "inv_1", RentReminderUrgency.DUE_SOON, dueDate, nowMs = dayMinus3))
        RentReminderDeliveryLedger.markSent(context, "inv_1", RentReminderUrgency.DUE_SOON, dueDate, nowMs = dayMinus3)

        // Same stage next day should NOT re-fire for DUE_SOON
        assertFalse(RentReminderDeliveryLedger.shouldSend(context, "inv_1", RentReminderUrgency.DUE_SOON, dueDate, nowMs = dayMinus2))

        // Due day fires cleanly
        assertTrue(RentReminderDeliveryLedger.shouldSend(context, "inv_1", RentReminderUrgency.DUE_TODAY, dueDate, nowMs = dueDay))
        RentReminderDeliveryLedger.markSent(context, "inv_1", RentReminderUrgency.DUE_TODAY, dueDate, nowMs = dueDay)
        assertFalse(RentReminderDeliveryLedger.shouldSend(context, "inv_1", RentReminderUrgency.DUE_TODAY, dueDate, nowMs = dueDay + 3_600_000L))

        // Overdue escalates daily respecting 20h cooldown
        assertTrue(RentReminderDeliveryLedger.shouldSend(context, "inv_1", RentReminderUrgency.OVERDUE, dueDate, nowMs = overdueDay1))
        RentReminderDeliveryLedger.markSent(context, "inv_1", RentReminderUrgency.OVERDUE, dueDate, nowMs = overdueDay1)
        assertFalse(RentReminderDeliveryLedger.shouldSend(context, "inv_1", RentReminderUrgency.OVERDUE, dueDate, nowMs = overdueDay1 + 5_000L))
        assertTrue(RentReminderDeliveryLedger.shouldSend(context, "inv_1", RentReminderUrgency.OVERDUE, dueDate, nowMs = overdueDay2))
    }

    @Test
    fun `RentReminderScheduler enqueues per-invoice WorkManager jobs and cancels stale ones when paid or disabled`() {
        val nowMs = epochAt(0, hour = 8)
        val invA = sampleInvoice(id = "inv_a", dueDaysOffset = 6)
        val invB = sampleInvoice(id = "inv_b", dueDaysOffset = 8)

        val scheduled = RentReminderScheduler.syncTenantSchedule(
            context = context,
            userId = "tenant_uid_1",
            notifyRentDue = true,
            invoices = listOf(invA, invB),
            nowMs = nowMs
        )
        assertEquals(4, scheduled.size)

        val workManager = WorkManager.getInstance(context)
        val enqueuedForA = workManager.getWorkInfosByTag(RentReminderScheduler.invoiceTag("inv_a")).get()
        assertEquals(2, enqueuedForA.count { it.state == WorkInfo.State.ENQUEUED })

        // Mark invA paid -> its jobs should be cancelled while invB stays enqueued
        val paidInvA = invA.copy(
            status = InvoiceStatus.PAID.name,
            paidCents = invA.totalDueCents,
            outstandingCents = 0L
        )
        RentReminderScheduler.syncTenantSchedule(
            context = context,
            userId = "tenant_uid_1",
            notifyRentDue = true,
            invoices = listOf(paidInvA, invB),
            nowMs = nowMs
        )

        val afterPaidA = workManager.getWorkInfosByTag(RentReminderScheduler.invoiceTag("inv_a")).get()
        assertTrue(afterPaidA.all { it.state == WorkInfo.State.CANCELLED })

        val stillActiveB = workManager.getWorkInfosByTag(RentReminderScheduler.invoiceTag("inv_b")).get()
        assertEquals(2, stillActiveB.count { it.state == WorkInfo.State.ENQUEUED })

        // Disabling notifications cancels all remaining scheduled jobs
        RentReminderScheduler.syncTenantSchedule(
            context = context,
            userId = "tenant_uid_1",
            notifyRentDue = false,
            invoices = listOf(paidInvA, invB),
            nowMs = nowMs
        )
        val afterDisable = workManager.getWorkInfosByTag(RentReminderScheduler.TAG_SCHEDULED_RENT_REMINDER).get()
        assertTrue(afterDisable.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `ScheduledRentReminderWorker posts notification deep-linking directly to Payment tab and target invoice`() = runTest {
        val dueDateMs = epochAt(10, hour = 0)
        val input = workDataOf(
            ScheduledRentReminderWorker.KEY_USER_ID to "tenant_uid_1",
            ScheduledRentReminderWorker.KEY_INVOICE_ID to "inv_3day_target",
            ScheduledRentReminderWorker.KEY_UNIT_ID to "unit_4B",
            ScheduledRentReminderWorker.KEY_TITLE to "Rent Due in 3 Day(s) • Unit 4B",
            ScheduledRentReminderWorker.KEY_BODY to "Kilimani Heights (2026-12): KES 28,000 is due on Dec 11, 2026. Tap to pay via M-Pesa.",
            ScheduledRentReminderWorker.KEY_DUE_DATE_EPOCH_MS to dueDateMs,
            ScheduledRentReminderWorker.KEY_STAGE to ScheduledReminderStage.THREE_DAYS_BEFORE.name,
            ScheduledRentReminderWorker.KEY_BYPASS_REMOTE_CHECK_FOR_TEST to true
        )

        val worker = TestListenableWorkerBuilder<ScheduledRentReminderWorker>(context)
            .setInputData(input)
            .build()

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = Shadows.shadowOf(nm).allNotifications
        assertEquals(1, posted.size)

        val notification = posted.first()
        val shadowPendingIntent = Shadows.shadowOf(notification.contentIntent)
        val launchIntent = shadowPendingIntent.savedIntent
        val parsedDeepLink = NotificationHelper.parseDeepLinkIntent(launchIntent)

        assertNotNull(parsedDeepLink)
        assertEquals(NotificationHelper.ROUTE_TENANT_PAY, parsedDeepLink!!.targetRoute)
        assertEquals(NotificationHelper.TAB_TENANT_INVOICES, parsedDeepLink.targetTab)
        assertEquals("inv_3day_target", parsedDeepLink.invoiceId)
        assertEquals("unit_4B", parsedDeepLink.unitId)

        // Running the same stage a second time is suppressed by persistent delivery ledger
        NotificationHelper.clearDedupCacheForTesting()
        val secondRun = TestListenableWorkerBuilder<ScheduledRentReminderWorker>(context)
            .setInputData(input)
            .build()
            .doWork()
        assertEquals(ListenableWorker.Result.success(), secondRun)
        assertEquals(1, Shadows.shadowOf(nm).allNotifications.size)
    }

    @Test
    fun `PushNotificationPermissionCard displays 3-day rent reminder schedule summary`() {
        composeTestRule.setContent {
            RentFlowTheme {
                PushNotificationPermissionCard(
                    maintenanceAlertsEnabled = true,
                    rentAlertsEnabled = true,
                    rentReminderScheduleSummary = "Next 3 days before due alert on Dec 08, 2026 (Unit 4B)"
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rent_reminder_schedule_summary").assertIsDisplayed()
        composeTestRule.onNodeWithText("Next 3 days before due alert on Dec 08, 2026 (Unit 4B)").assertIsDisplayed()
    }
}
