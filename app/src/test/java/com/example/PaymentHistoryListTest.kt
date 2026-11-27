package com.example

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.example.domain.finance.FinancialEngine
import com.example.domain.finance.TenantPaymentHistorySummary
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceStatus
import com.example.ui.components.PaymentHistoryList
import com.example.ui.theme.RentFlowTheme
import com.example.ui.viewmodel.UiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PaymentHistoryListTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun nairobiDateMs(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("Africa/Nairobi")).apply {
            set(year, month, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun `renders annual KES summary, Paid Late Partial badges, and year filter switching`() {
        val summary = FinancialEngine.computeTenantPaymentHistory(
            invoices = listOf(
                Invoice(
                    id = "inv_mar_26",
                    invoiceNumber = "INV-2026-03",
                    propertyName = "Kilimani Palms",
                    unitNumber = "A102",
                    billingPeriod = "2026-03",
                    rentCents = 2_800_000L,
                    totalDueCents = 2_800_000L,
                    paidCents = 2_800_000L,
                    outstandingCents = 0L,
                    dueDateEpochMs = nairobiDateMs(2026, Calendar.MARCH, 5),
                    paidAtEpochMs = nairobiDateMs(2026, Calendar.MARCH, 3),
                    status = InvoiceStatus.PAID.name
                ),
                Invoice(
                    id = "inv_feb_26",
                    invoiceNumber = "INV-2026-02",
                    propertyName = "Kilimani Palms",
                    unitNumber = "A102",
                    billingPeriod = "2026-02",
                    rentCents = 2_800_000L,
                    lateFeeCents = 140_000L,
                    lateFeeRatePercent = 5.0,
                    totalDueCents = 2_940_000L,
                    paidCents = 2_940_000L,
                    outstandingCents = 0L,
                    dueDateEpochMs = nairobiDateMs(2026, Calendar.FEBRUARY, 5),
                    paidAtEpochMs = nairobiDateMs(2026, Calendar.FEBRUARY, 8),
                    status = InvoiceStatus.PAID.name
                ),
                Invoice(
                    id = "inv_apr_26",
                    invoiceNumber = "INV-2026-04",
                    propertyName = "Kilimani Palms",
                    unitNumber = "A102",
                    billingPeriod = "2026-04",
                    rentCents = 2_800_000L,
                    totalDueCents = 2_800_000L,
                    paidCents = 1_000_000L,
                    outstandingCents = 1_800_000L,
                    dueDateEpochMs = nairobiDateMs(2026, Calendar.APRIL, 5),
                    status = InvoiceStatus.PARTIALLY_PAID.name
                ),
                Invoice(
                    id = "inv_dec_25",
                    invoiceNumber = "INV-2025-12",
                    propertyName = "Kilimani Palms",
                    unitNumber = "A102",
                    billingPeriod = "2025-12",
                    rentCents = 2_500_000L,
                    totalDueCents = 2_500_000L,
                    paidCents = 2_500_000L,
                    outstandingCents = 0L,
                    dueDateEpochMs = nairobiDateMs(2025, Calendar.DECEMBER, 5),
                    paidAtEpochMs = nairobiDateMs(2025, Calendar.DECEMBER, 2),
                    status = InvoiceStatus.PAID.name
                )
            ),
            nowMs = nairobiDateMs(2026, Calendar.APRIL, 10)
        )

        composeTestRule.setContent {
            RentFlowTheme {
                PaymentHistoryList(historyState = UiState.Success(summary))
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("payment_history_summary_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("payment_history_total_kes").assertIsDisplayed()
        composeTestRule.onNodeWithText("KES 92,400.00").assertIsDisplayed()
        composeTestRule.onNodeWithText("Paid: 2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Late: 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Partial: 1").assertIsDisplayed()

        composeTestRule.onNodeWithTag("payment_history_year_summary_2026").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("payment_history_year_summary_2025").performScrollTo().assertIsDisplayed()

        // Filter to 2025 only
        composeTestRule.onNodeWithTag("payment_history_year_chip_2025")
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Total rent paid in 2025 (1 invoices)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("payment_history_item_inv_dec_25").assertExists()
        composeTestRule.onNodeWithTag("payment_history_item_inv_mar_26").assertDoesNotExist()

        // Switch back to All Years and expand an item breakdown
        composeTestRule.onNodeWithTag("payment_history_year_chip_all")
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("payment_history_list")
            .performScrollToNode(hasTestTag("payment_history_item_inv_feb_26"))
        composeTestRule.onNodeWithTag("payment_history_item_inv_feb_26").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Late", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Late Fee (5%)", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `renders empty and error states gracefully`() {
        composeTestRule.setContent {
            RentFlowTheme(darkTheme = true) {
                PaymentHistoryList(historyState = UiState.Success(TenantPaymentHistorySummary()))
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("No Payment History Yet").assertIsDisplayed()
    }
}
