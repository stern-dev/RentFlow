package com.example

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.PaymentInstructionCard
import com.example.ui.theme.RentFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PaymentInstructionCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `renders dual Paybill and Till display boxes with grouped digits and inline steps`() {
        composeTestRule.setContent {
            RentFlowTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    PaymentInstructionCard(
                        paybill = "522522",
                        till = "5643210",
                        accountReference = "KP-A102",
                        amountCents = 2_800_000L,
                        recipientName = "Kilimani Palms"
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("payment_instruction_card").assertExists()
        composeTestRule.onNodeWithText("LIPA NA M-PESA").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Recipient: Kilimani Palms").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("payment_instruction_paybill_box").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("522 522").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("KP-A102").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("payment_instruction_till_box").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("564 3210").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("payment_instruction_steps_paybill").assertExists()
        composeTestRule.onNodeWithText("Open M-Pesa > Lipa na M-Pesa > Pay Bill").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("payment_instruction_qr_paybill").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("PAYBILL QR").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("payment_instruction_steps_till").assertExists()
        composeTestRule.onNodeWithText("Open M-Pesa > Lipa na M-Pesa > Buy Goods and Services").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("payment_instruction_qr_till").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("TILL QR").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `tapping inline QR opens enlarged scan dialog with compact M-Pesa URI copy action`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        composeTestRule.setContent {
            RentFlowTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    PaymentInstructionCard(
                        paybill = "522522",
                        till = "5643210",
                        accountReference = "KP-A102",
                        amountCents = 2_800_000L,
                        recipientName = "Kilimani Palms"
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("payment_instruction_qr_paybill").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("payment_instruction_qr_zoom_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scan Paybill QR").assertIsDisplayed()
        composeTestRule.onNodeWithText("mpesa://paybill?shortcode=522522&account=KP-A102&amount=28000").assertIsDisplayed()

        composeTestRule.onNodeWithTag("payment_instruction_copy_uri_button")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(
            "mpesa://paybill?shortcode=522522&account=KP-A102&amount=28000",
            clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        )

        composeTestRule.onNodeWithTag("payment_instruction_qr_zoom_close")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("payment_instruction_qr_zoom_dialog").assertDoesNotExist()
    }

    @Test
    fun `copy buttons meet 48dp minimum touch target and write clean unspaced values to clipboard`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        composeTestRule.setContent {
            RentFlowTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    PaymentInstructionCard(
                        paybill = "522 522",
                        till = "5643210",
                        accountReference = "kp a102",
                        amountCents = 2_800_000L,
                        recipientName = "Kilimani Palms"
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("payment_instruction_copy_paybill")
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals("522522", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        composeTestRule.onNodeWithText("Copied").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("payment_instruction_copy_account")
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals("KPA102", clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        composeTestRule.onNodeWithTag("payment_instruction_copy_till")
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals("5643210", clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        composeTestRule.onNodeWithTag("payment_instruction_copy_all")
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        val copiedAll = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        assertTrue(copiedAll.contains("522 522"))
        assertTrue(copiedAll.contains("KPA102"))
        assertTrue(copiedAll.contains("564 3210"))
    }

    @Test
    fun `tapping directly on formatted Paybill Till and Account values copies raw text to clipboard`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        composeTestRule.setContent {
            RentFlowTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    PaymentInstructionCard(
                        paybill = "522522",
                        till = "5643210",
                        accountReference = "KP-A102",
                        amountCents = 2_800_000L,
                        recipientName = "Kilimani Palms"
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("522 522").performScrollTo().performClick()
        assertEquals("522522", clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        composeTestRule.onNodeWithText("KP-A102").performScrollTo().performClick()
        assertEquals("KP-A102", clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        composeTestRule.onNodeWithText("564 3210").performScrollTo().performClick()
        assertEquals("5643210", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun `preview mode displays TENANT PREVIEW badge and inline QR in dark theme`() {
        composeTestRule.setContent {
            RentFlowTheme(darkTheme = true) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    PaymentInstructionCard(
                        paybill = "247247",
                        till = null,
                        accountReference = "KH-A101",
                        recipientName = "Kileleshwa Heights",
                        title = "Tenant Payment Preview",
                        isPreview = true
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("payment_instruction_card").assertExists()
        composeTestRule.onNodeWithTag("payment_instruction_preview_badge").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("TENANT PREVIEW").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Tenant Payment Preview").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("247 247").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("KH-A101").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("payment_instruction_qr_paybill").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `invalid or empty shortcodes render nothing safely`() {
        composeTestRule.setContent {
            RentFlowTheme {
                PaymentInstructionCard(
                    paybill = "0123",
                    till = "",
                    accountReference = "A101"
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("payment_instruction_card").assertDoesNotExist()
    }
}
