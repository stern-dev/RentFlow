package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.PaymentMethodRepository
import com.example.domain.finance.KenyanPaymentValidator
import com.example.domain.finance.MpesaSmsVerificationResult
import com.example.domain.model.PaymentChannelType
import com.example.domain.model.PaymentRoleScope
import com.example.domain.model.SavedPaymentMethod
import com.example.domain.model.UserRole
import com.example.ui.screens.AddEditPaymentMethodDialog
import com.example.ui.screens.PaymentMethodSelectionScreen
import com.example.ui.screens.PropertyMpesaSuggestion
import com.example.ui.theme.RentFlowTheme
import com.example.ui.viewmodel.UiState
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PaymentMethodFeatureTest {

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
    fun `KenyanPaymentValidator enforces strict 5 to 7 digit Paybill and Till rules`() {
        assertTrue(KenyanPaymentValidator.isValidShortcode("522522"))
        assertTrue(KenyanPaymentValidator.isValidShortcode("12345"))
        assertTrue(KenyanPaymentValidator.isValidShortcode("5643210"))
        assertTrue(KenyanPaymentValidator.isValidShortcode(" 522 522 "))

        assertFalse(KenyanPaymentValidator.isValidShortcode("052252"))
        assertFalse(KenyanPaymentValidator.isValidShortcode("1234"))
        assertFalse(KenyanPaymentValidator.isValidShortcode("12345678"))
        assertFalse(KenyanPaymentValidator.isValidShortcode("52A522"))

        val validPaybill = KenyanPaymentValidator.validatePaymentMethod(
            channelType = PaymentChannelType.MPESA_PAYBILL,
            label = "Kilimani Rent Paybill",
            paybillNumber = "522522",
            accountReference = "KP-A101",
            tillNumber = null,
            phoneNumber = null,
            bankName = null,
            bankAccountNumber = null
        )
        assertTrue(validPaybill.isValid)

        val missingAccountRef = KenyanPaymentValidator.validatePaymentMethod(
            channelType = PaymentChannelType.MPESA_PAYBILL,
            label = "Kilimani Rent Paybill",
            paybillNumber = "522522",
            accountReference = " ",
            tillNumber = null,
            phoneNumber = null,
            bankName = null,
            bankAccountNumber = null
        )
        assertFalse(missingAccountRef.isValid)
        assertNotNull(missingAccountRef.accountReferenceError)

        val invalidLeadingZeroTill = KenyanPaymentValidator.validatePaymentMethod(
            channelType = PaymentChannelType.MPESA_TILL,
            label = "Westlands Till",
            paybillNumber = null,
            accountReference = null,
            tillNumber = "088221",
            phoneNumber = null,
            bankName = null,
            bankAccountNumber = null
        )
        assertFalse(invalidLeadingZeroTill.isValid)
        assertNotNull(invalidLeadingZeroTill.tillError)

        val validStk = KenyanPaymentValidator.validatePaymentMethod(
            channelType = PaymentChannelType.MPESA_STK,
            label = "My Safaricom Line",
            paybillNumber = null,
            accountReference = null,
            tillNumber = null,
            phoneNumber = "0712345678",
            bankName = null,
            bankAccountNumber = null
        )
        assertTrue(validStk.isValid)

        val validBank = KenyanPaymentValidator.validatePaymentMethod(
            channelType = PaymentChannelType.BANK_TRANSFER,
            label = "KCB Rent Collection",
            paybillNumber = null,
            accountReference = null,
            tillNumber = null,
            phoneNumber = null,
            bankName = "KCB Kenya",
            bankAccountNumber = "1102938475"
        )
        assertTrue(validBank.isValid)

        val validCash = KenyanPaymentValidator.validatePaymentMethod(
            channelType = PaymentChannelType.CASH,
            label = "Caretaker Cash Receipt",
            paybillNumber = null,
            accountReference = null,
            tillNumber = null,
            phoneNumber = null,
            bankName = null,
            bankAccountNumber = null
        )
        assertTrue(validCash.isValid)
    }

    @Test
    fun `KenyanPaymentValidator verifyWithMpesaSms checks Paybill Till and mismatches`() {
        assertEquals(
            MpesaSmsVerificationResult.NotProvided,
            KenyanPaymentValidator.verifyWithMpesaSms("", PaymentChannelType.MPESA_PAYBILL, paybillNumber = "522522")
        )

        val badFormat = KenyanPaymentValidator.verifyWithMpesaSms(
            rawSms = "Hello I sent the rent",
            channelType = PaymentChannelType.MPESA_PAYBILL,
            paybillNumber = "522522",
            accountReference = "KP-A101"
        )
        assertTrue(badFormat is MpesaSmsVerificationResult.InvalidFormat)

        val verifiedPaybill = KenyanPaymentValidator.verifyWithMpesaSms(
            rawSms = "UAF29KL30M Confirmed. Ksh28,000.00 sent to KCB Paybill 522522 for account KP-A101 on 20/11/26.",
            channelType = PaymentChannelType.MPESA_PAYBILL,
            paybillNumber = "522522",
            accountReference = "KP-A101"
        )
        assertTrue(verifiedPaybill is MpesaSmsVerificationResult.Verified)
        assertEquals("UAF29KL30M", (verifiedPaybill as MpesaSmsVerificationResult.Verified).transactionCode)

        val mismatchTill = KenyanPaymentValidator.verifyWithMpesaSms(
            rawSms = "SJK45AB89C Confirmed. KES 18,000 paid to NYALI ESTATES TILL 882211 on 19/11/26.",
            channelType = PaymentChannelType.MPESA_TILL,
            tillNumber = "999111"
        )
        assertTrue(mismatchTill is MpesaSmsVerificationResult.MismatchWarning)

        val verifiedTill = KenyanPaymentValidator.verifyWithMpesaSms(
            rawSms = "SJK45AB89C Confirmed. KES 18,000 paid to NYALI ESTATES TILL 882211 on 19/11/26.",
            channelType = PaymentChannelType.MPESA_TILL,
            tillNumber = "882211"
        )
        assertTrue(verifiedTill is MpesaSmsVerificationResult.Verified)
    }

    @Test
    fun `SavedPaymentMethod toCreateMap omits nulls and formats subtitles accurately`() {
        val paybillMethod = SavedPaymentMethod(
            id = "pm_1",
            userId = "uid_1",
            label = "Kilimani Paybill",
            channelType = PaymentChannelType.MPESA_PAYBILL.name,
            roleScope = PaymentRoleScope.TENANT.name,
            paybillNumber = "522522",
            accountReference = "UNIT-A1",
            defaultMethod = true,
            smsVerified = true,
            smsSampleRef = "UAF29KL30M"
        )
        val createMap = paybillMethod.toCreateMap()
        assertFalse(createMap.containsKey("id"))
        assertFalse(createMap.containsKey("tillNumber"))
        assertFalse(createMap.containsKey("phoneNumber"))
        assertFalse(createMap.containsKey("bankName"))
        assertEquals("uid_1", createMap["userId"])
        assertEquals("522522", createMap["paybillNumber"])
        assertEquals("UNIT-A1", createMap["accountReference"])
        assertEquals(true, createMap["defaultMethod"])
        assertEquals(true, createMap["smsVerified"])
        assertTrue(createMap["createdAt"] is FieldValue)
        assertEquals("Paybill 522522 • Acct UNIT-A1", paybillMethod.summarySubtitle())
        assertEquals("522522", paybillMethod.primaryShortcodeOrIdentifier())

        val bankMethod = SavedPaymentMethod(
            id = "pm_2",
            label = "Equity Rent",
            channelType = PaymentChannelType.BANK_TRANSFER.name,
            bankName = "Equity Bank",
            bankAccountNumber = "0123456789012"
        )
        assertEquals("Equity Bank ••••9012", bankMethod.summarySubtitle())
    }

    @Test
    fun `PaymentMethodRepository fails safely when invalid or signed out`() = runTest {
        val db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val repo = PaymentMethodRepository(db, auth)

        val invalidShortcode = repo.savePaymentMethod(
            label = "Bad Paybill",
            channelType = PaymentChannelType.MPESA_PAYBILL,
            paybillNumber = "0123",
            accountReference = "A1"
        )
        assertTrue(invalidShortcode.isFailure)

        val signedOutSave = repo.savePaymentMethod(
            label = "Valid Paybill",
            channelType = PaymentChannelType.MPESA_PAYBILL,
            paybillNumber = "522522",
            accountReference = "A101"
        )
        assertTrue(signedOutSave.isFailure)
        assertTrue(repo.setDefaultPaymentMethod("uid_1", "pm_1").isFailure)
        assertTrue(repo.deletePaymentMethod("uid_1", "pm_1").isFailure)
    }

    @Test
    fun `PaymentMethodSelectionScreen filters channels and handles selection default and use`() {
        val paybill = SavedPaymentMethod(
            id = "pm_pb",
            userId = "u1",
            label = "Kilimani Paybill",
            channelType = PaymentChannelType.MPESA_PAYBILL.name,
            roleScope = PaymentRoleScope.TENANT.name,
            paybillNumber = "522522",
            accountReference = "KP-A101",
            defaultMethod = true,
            smsVerified = true,
            smsSampleRef = "UAF29KL30M"
        )
        val till = SavedPaymentMethod(
            id = "pm_till",
            userId = "u1",
            label = "Westlands Buy Goods",
            channelType = PaymentChannelType.MPESA_TILL.name,
            roleScope = PaymentRoleScope.LANDLORD.name,
            tillNumber = "882211",
            defaultMethod = false
        )

        var selectedId: String? = null
        var defaultedId: String? = null
        var confirmedId: String? = null

        composeTestRule.setContent {
            RentFlowTheme {
                PaymentMethodSelectionScreen(
                    methodsState = UiState.Success(listOf(paybill, till)),
                    selectedMethodId = paybill.id,
                    userRole = UserRole.TENANT,
                    propertySuggestions = listOf(
                        PropertyMpesaSuggestion(
                            title = "Kilimani Palms Paybill",
                            channelType = PaymentChannelType.MPESA_PAYBILL,
                            paybillNumber = "400200",
                            accountReference = "KP-B2"
                        )
                    ),
                    onBack = {},
                    onSelectMethod = { selectedId = it.id },
                    onConfirmUseMethod = { confirmedId = it.id },
                    onSaveMethod = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    onSetDefaultMethod = { defaultedId = it },
                    onDeleteMethod = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("payment_methods_list")
            .performScrollToNode(hasTestTag("saved_payment_method_card_pm_pb"))
        composeTestRule.onNodeWithTag("saved_payment_method_card_pm_pb").assertIsDisplayed()

        composeTestRule.onNodeWithTag("payment_methods_list")
            .performScrollToNode(hasTestTag("saved_payment_method_card_pm_till"))
        composeTestRule.onNodeWithTag("saved_payment_method_card_pm_till").assertIsDisplayed()

        composeTestRule.onNodeWithTag("payment_methods_list")
            .performScrollToNode(hasTestTag("payment_channel_filters"))
        composeTestRule.onNodeWithTag("payment_channel_filters")
            .performScrollToNode(hasTestTag("filter_channel_till"))
        composeTestRule.onNodeWithTag("filter_channel_till").performClick()

        composeTestRule.onNodeWithTag("payment_methods_list")
            .performScrollToNode(hasTestTag("saved_payment_method_card_pm_till"))
        composeTestRule.onNodeWithTag("saved_payment_method_card_pm_till").assertIsDisplayed()

        composeTestRule.onNodeWithTag("saved_payment_method_card_pm_till").performClick()
        assertEquals("pm_till", selectedId)

        composeTestRule.onNodeWithTag("payment_methods_list")
            .performScrollToNode(hasTestTag("set_default_method_pm_till"))
        composeTestRule.onNodeWithTag("set_default_method_pm_till").performClick()
        assertEquals("pm_till", defaultedId)

        composeTestRule.onNodeWithTag("use_selected_payment_method_button").performClick()
        assertEquals("pm_pb", confirmedId)
    }

    @Test
    fun `AddEditPaymentMethodDialog validates Kenya Paybill Till and runs M-Pesa SMS test check`() {
        var savedLabel: String? = null
        var savedChannel: PaymentChannelType? = null
        var savedPaybill: String? = null
        var savedAccountRef: String? = null
        var savedSmsVerified = false
        var savedSmsCode: String? = null

        composeTestRule.setContent {
            RentFlowTheme {
                AddEditPaymentMethodDialog(
                    defaultScope = PaymentRoleScope.TENANT,
                    onDismiss = {},
                    onSave = { label, channel, _, paybill, acctRef, _, _, _, _, _, smsVerified, smsSampleRef ->
                        savedLabel = label
                        savedChannel = channel
                        savedPaybill = paybill
                        savedAccountRef = acctRef
                        savedSmsVerified = smsVerified
                        savedSmsCode = smsSampleRef
                    }
                )
            }
        }

        // Switch to Till and test validation error on leading zero
        composeTestRule.onNodeWithTag("channel_chip_mpesa_till").performClick()
        composeTestRule.onNodeWithTag("pm_label_input").performTextInput("Kileleshwa Till")
        composeTestRule.onNodeWithTag("pm_till_input").performTextInput("012345")
        composeTestRule.onNodeWithTag("pm_save_button").performClick()
        composeTestRule.onNodeWithText("Till number must be 5–7 digits and cannot start with 0 (e.g. 5643210).").assertIsDisplayed()
        assertNull(savedLabel)

        // Switch back to Paybill, fill valid shortcode + account reference, and verify SMS check
        composeTestRule.onNodeWithTag("channel_chip_mpesa_paybill").performClick()
        composeTestRule.onNodeWithTag("pm_label_input").performTextClearance()
        composeTestRule.onNodeWithTag("pm_label_input").performTextInput("Kilimani Rent Paybill")
        composeTestRule.onNodeWithTag("pm_paybill_input").performTextInput("522522")
        composeTestRule.onNodeWithTag("pm_account_ref_input").performTextInput("KP-A101")

        composeTestRule.onNodeWithTag("pm_toggle_sms_check").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("pm_sms_test_input").performScrollTo()
            .performTextInput("UAF29KL30M Confirmed. Ksh25,000.00 paid to 522522 for account KP-A101 on 20/11/26.")
        composeTestRule.onNodeWithTag("pm_run_sms_check_button").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("pm_sms_verified_banner")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Verified UAF29KL30M", substring = true)
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("pm_save_button").performClick()
        assertEquals("Kilimani Rent Paybill", savedLabel)
        assertEquals(PaymentChannelType.MPESA_PAYBILL, savedChannel)
        assertEquals("522522", savedPaybill)
        assertEquals("KP-A101", savedAccountRef)
        assertTrue(savedSmsVerified)
        assertEquals("UAF29KL30M", savedSmsCode)
    }
}
