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
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.MpesaReceiptRepository
import com.example.domain.finance.MpesaReceiptMatchEvaluation
import com.example.domain.finance.MpesaReceiptMatcher
import com.example.domain.finance.MpesaSmsParser
import com.example.domain.finance.ReceiptMatchOutcome
import com.example.domain.model.Lease
import com.example.domain.model.LeaseStatus
import com.example.domain.model.MpesaCaptureSource
import com.example.domain.model.MpesaReceipt
import com.example.domain.model.MpesaReceiptDirection
import com.example.domain.model.MpesaReceiptStatus
import com.example.domain.model.Payment
import com.example.domain.model.PaymentStatus
import com.example.ui.screens.DetectedMpesaReceiptCard
import com.example.ui.screens.MpesaReconcileScreen
import com.example.ui.screens.TenantPaymentSubmissionDialog
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MpesaReceiptAutomationTest {

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
    fun `MpesaSmsParser parses incoming outgoing paybill till and multi-SMS batches`() {
        val incoming = MpesaSmsParser.parse(
            "UAF29KL30M Confirmed. You have received Ksh28,500.00 from WANJIKU MWANGI 0712345678 on 20/11/26 at 10:15 AM."
        )
        assertNotNull(incoming)
        assertEquals("UAF29KL30M", incoming!!.transactionCode)
        assertEquals(2_850_000L, incoming.amountCents)
        assertEquals(MpesaReceiptDirection.INCOMING_RECEIVED, incoming.direction)
        assertEquals("+254712345678", incoming.counterpartyPhone)
        assertTrue(incoming.counterpartyName!!.contains("WANJIKU"))
        assertTrue(incoming.transactionTimestampMs > 0L)

        val outgoingPaybill = MpesaSmsParser.parse(
            "UBK7X92M10 Confirmed. Ksh32,000.00 sent to KCB Rent 522522 for account KP-A101 on 20/11/26 at 2:30 PM."
        )
        assertNotNull(outgoingPaybill)
        assertEquals("UBK7X92M10", outgoingPaybill!!.transactionCode)
        assertEquals(3_200_000L, outgoingPaybill.amountCents)
        assertEquals(MpesaReceiptDirection.OUTGOING_SENT, outgoingPaybill.direction)
        assertEquals("522522", outgoingPaybill.shortcode)
        assertEquals("KP-A101", outgoingPaybill.accountReference)

        val outgoingTill = MpesaSmsParser.parse(
            "SJK45AB89C Confirmed. KES 18,000 paid to NYALI ESTATES TILL 882211 on 19/11/26."
        )
        assertNotNull(outgoingTill)
        assertEquals("SJK45AB89C", outgoingTill!!.transactionCode)
        assertEquals(1_800_000L, outgoingTill.amountCents)
        assertEquals("882211", outgoingTill.shortcode)

        assertNull(MpesaSmsParser.parse("Hi landlord, please send invoice"))

        assertTrue(
            MpesaSmsParser.looksLikeMpesaNotification(
                senderOrTitle = "MPESA",
                body = "UAF29KL30M Confirmed. You have received Ksh25,000.00 from JOHN KAMAU 0722112233 on 20/11/26."
            )
        )
        assertFalse(
            MpesaSmsParser.looksLikeMpesaNotification(
                senderOrTitle = "WhatsApp",
                body = "Let's meet tomorrow at 10am"
            )
        )

        val batch = """
            UAF29KL30M Confirmed. You have received Ksh28,500.00 from WANJIKU MWANGI 0712345678 on 20/11/26.
            UBK7X92M10 Confirmed. Ksh15,000.00 sent to KILIMANI TILL 554433 on 20/11/26.
        """.trimIndent()
        val parsedBatch = MpesaSmsParser.parseAll(batch)
        assertEquals(2, parsedBatch.size)
        assertEquals("UAF29KL30M", parsedBatch[0].transactionCode)
        assertEquals("UBK7X92M10", parsedBatch[1].transactionCode)
    }

    @Test
    fun `MpesaReceiptMatcher evaluates exact discrepancy lease suggestion already verified and tenant autofill`() {
        val lease1 = Lease(
            id = "lease_1",
            propertyName = "Kilimani Palms",
            unitNumber = "A101",
            tenantUid = "t1",
            tenantName = "Wanjiku Mwangi",
            tenantPhone = "+254712345678",
            rentCents = 2_850_000L,
            status = LeaseStatus.ACTIVE.name
        )
        val lease2 = Lease(
            id = "lease_2",
            propertyName = "Nyali Breeze",
            unitNumber = "B204",
            tenantUid = "t2",
            tenantName = "Brian Ochieng",
            tenantPhone = "+254722998877",
            rentCents = 3_500_000L,
            status = LeaseStatus.ACTIVE.name
        )
        val pendingExact = Payment(
            id = "pay_1",
            leaseId = "lease_1",
            referenceCode = "UAF29KL30M",
            tenantName = "Wanjiku Mwangi",
            propertyName = "Kilimani Palms",
            unitNumber = "A101",
            amountCents = 2_850_000L,
            status = PaymentStatus.PENDING_VERIFICATION.name
        )
        val verifiedExisting = Payment(
            id = "pay_2",
            leaseId = "lease_2",
            referenceCode = "SJK45AB89C",
            tenantName = "Brian Ochieng",
            unitNumber = "B204",
            amountCents = 3_500_000L,
            status = PaymentStatus.VERIFIED.name
        )

        val exactReceipt = MpesaReceipt(
            id = "mpesa_UAF29KL30M",
            transactionCode = "UAF29KL30M",
            amountCents = 2_850_000L,
            direction = MpesaReceiptDirection.INCOMING_RECEIVED.name,
            counterpartyPhone = "+254712345678"
        )
        val exactEval = MpesaReceiptMatcher.evaluateForLandlord(exactReceipt, listOf(pendingExact), listOf(lease1, lease2))
        assertEquals(ReceiptMatchOutcome.EXACT_MATCH, exactEval.outcome)
        assertEquals("pay_1", exactEval.matchedPayment?.id)

        val diffReceipt = exactReceipt.copy(amountCents = 2_500_000L)
        val diffEval = MpesaReceiptMatcher.evaluateForLandlord(diffReceipt, listOf(pendingExact), listOf(lease1))
        assertEquals(ReceiptMatchOutcome.DISCREPANCY_REVIEW, diffEval.outcome)
        assertNotNull(diffEval.discrepancyDetails)

        val leaseHintReceipt = MpesaReceipt(
            id = "mpesa_NEW1234567",
            transactionCode = "NEW1234567",
            amountCents = 3_500_000L,
            direction = MpesaReceiptDirection.INCOMING_RECEIVED.name,
            counterpartyPhone = "+254722998877",
            counterpartyName = "BRIAN OCHIENG"
        )
        val hintEval = MpesaReceiptMatcher.evaluateForLandlord(leaseHintReceipt, listOf(pendingExact), listOf(lease1, lease2))
        assertEquals(ReceiptMatchOutcome.UNMATCHED_LEASE_SUGGESTION, hintEval.outcome)
        assertEquals("lease_2", hintEval.suggestedLease?.id)

        val unmatchedReceipt = MpesaReceipt(
            id = "mpesa_UNK9988776",
            transactionCode = "UNK9988776",
            amountCents = 1_100_000L,
            direction = MpesaReceiptDirection.INCOMING_RECEIVED.name,
            counterpartyName = "UNKNOWN SENDER"
        )
        val unmatchedEval = MpesaReceiptMatcher.evaluateForLandlord(unmatchedReceipt, listOf(pendingExact), listOf(lease1, lease2))
        assertEquals(ReceiptMatchOutcome.UNMATCHED, unmatchedEval.outcome)

        val alreadyVerifiedEval = MpesaReceiptMatcher.evaluateForLandlord(
            MpesaReceipt(id = "mpesa_SJK45AB89C", transactionCode = "SJK45AB89C", amountCents = 3_500_000L),
            listOf(verifiedExisting),
            listOf(lease2)
        )
        assertEquals(ReceiptMatchOutcome.ALREADY_VERIFIED, alreadyVerifiedEval.outcome)

        val outgoingForTenant = MpesaReceipt(
            id = "mpesa_TEN8877665",
            transactionCode = "TEN8877665",
            amountCents = 2_850_000L,
            direction = MpesaReceiptDirection.OUTGOING_SENT.name,
            status = MpesaReceiptStatus.STAGED.name
        )
        val candidates = MpesaReceiptMatcher.filterTenantAutofillCandidates(
            receipts = listOf(exactReceipt, outgoingForTenant),
            existingTenantPayments = listOf(pendingExact)
        )
        assertEquals(1, candidates.size)
        assertEquals("TEN8877665", candidates.first().transactionCode)
    }

    @Test
    fun `MpesaReceipt toCreateMap omits nulls and repository fails safely when signed out or invalid`() = runTest {
        val receipt = MpesaReceipt(
            id = "mpesa_UBK7X92M10",
            userId = "u1",
            transactionCode = "ubk7x92m10",
            amountCents = 2_850_000L,
            direction = MpesaReceiptDirection.OUTGOING_SENT.name,
            captureSource = MpesaCaptureSource.SHARE_INTENT.name,
            status = MpesaReceiptStatus.STAGED.name,
            rawSnippet = "UBK7X92M10 Confirmed. Ksh28,500.00 sent to Kilimani Palms",
            transactionTimestampMs = 1_763_650_000_000L
        )
        val map = receipt.toCreateMap()
        assertEquals("UBK7X92M10", map["transactionCode"])
        assertFalse(map.containsKey("counterpartyName"))
        assertFalse(map.containsKey("matchedPaymentId"))
        assertTrue(map["createdAt"] is FieldValue)

        val db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val repo = MpesaReceiptRepository(db, auth)

        assertTrue(
            repo.stageParsedSmsBatch("u1", "No valid code here", MpesaCaptureSource.MANUAL_PASTE).isFailure
        )
        assertTrue(
            repo.stageParsedSmsBatch(
                "u1",
                "UBK7X92M10 Confirmed. Ksh28,500.00 sent to Kilimani Palms.",
                MpesaCaptureSource.MANUAL_PASTE
            ).isFailure
        )
        assertTrue(repo.markReconciled("u1", "mpesa_UBK7X92M10", "pay_1", "lease_1").isFailure)
        assertTrue(repo.dismissReceipt("u1", "mpesa_UBK7X92M10").isFailure)
        assertTrue(repo.reopenReceipt("u1", "mpesa_UBK7X92M10").isFailure)
    }

    @Test
    fun `MpesaReconcileScreen supports 1-tap approval unmatched lease linking and batch SMS paste`() {
        val lease1 = Lease(
            id = "lease_1",
            propertyName = "Kilimani Palms",
            unitNumber = "A101",
            tenantName = "Wanjiku Mwangi",
            rentCents = 2_850_000L,
            status = LeaseStatus.ACTIVE.name
        )
        val pendingPay = Payment(
            id = "pay_1",
            leaseId = "lease_1",
            referenceCode = "UAF29KL30M",
            tenantName = "Wanjiku Mwangi",
            propertyName = "Kilimani Palms",
            unitNumber = "A101",
            amountCents = 2_850_000L,
            status = PaymentStatus.PENDING_VERIFICATION.name
        )
        val exactReceipt = MpesaReceipt(
            id = "mpesa_UAF29KL30M",
            transactionCode = "UAF29KL30M",
            amountCents = 2_850_000L,
            direction = MpesaReceiptDirection.INCOMING_RECEIVED.name,
            counterpartyName = "WANJIKU MWANGI",
            captureSource = MpesaCaptureSource.NOTIFICATION.name,
            status = MpesaReceiptStatus.STAGED.name,
            rawSnippet = "UAF29KL30M Confirmed. You have received Ksh28,500.00 from WANJIKU MWANGI.",
            transactionTimestampMs = 1_763_650_000_000L
        )
        val unmatchedReceipt = MpesaReceipt(
            id = "mpesa_UNK4455667",
            transactionCode = "UNK4455667",
            amountCents = 2_850_000L,
            direction = MpesaReceiptDirection.INCOMING_RECEIVED.name,
            counterpartyName = "JANE DOE",
            captureSource = MpesaCaptureSource.MANUAL_PASTE.name,
            status = MpesaReceiptStatus.STAGED.name,
            rawSnippet = "UNK4455667 Confirmed. You have received Ksh28,500.00 from JANE DOE.",
            transactionTimestampMs = 1_763_650_000_000L
        )

        val evals = listOf(
            MpesaReceiptMatchEvaluation(
                receipt = exactReceipt,
                outcome = ReceiptMatchOutcome.EXACT_MATCH,
                matchedPayment = pendingPay,
                suggestedLease = lease1,
                matchSummary = "Exact Tx & KES match: Wanjiku Mwangi • Kilimani Palms (A101)"
            ),
            MpesaReceiptMatchEvaluation(
                receipt = unmatchedReceipt,
                outcome = ReceiptMatchOutcome.UNMATCHED,
                matchSummary = "No matching pending payment or active lease found"
            )
        )

        var approvedCode: String? = null
        var linkedLeaseId: String? = null
        var pastedBatch: String? = null

        composeTestRule.setContent {
            RentFlowTheme {
                MpesaReconcileScreen(
                    evaluations = evals,
                    leases = listOf(lease1),
                    autoCaptureEnabled = true,
                    listenerEnabled = true,
                    isBusy = false,
                    onToggleAutoCapture = {},
                    onOpenNotificationSettings = {},
                    onStageSmsBatch = { raw, done ->
                        pastedBatch = raw
                        done()
                    },
                    onApproveMatched = { approvedCode = it.receipt.transactionCode },
                    onReconcileToLease = { _, lease -> linkedLeaseId = lease.id },
                    onMarkAlreadyVerified = {},
                    onDismissReceipt = {},
                    onReopenReceipt = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("mpesa_reconcile_list")
            .performScrollToNode(hasTestTag("ready_receipt_card_mpesa_UAF29KL30M"))
        composeTestRule.onNodeWithTag("ready_receipt_card_mpesa_UAF29KL30M").assertIsDisplayed()
        composeTestRule.onNodeWithTag("approve_receipt_btn_mpesa_UAF29KL30M")
            .performScrollTo()
            .performClick()
        assertEquals("UAF29KL30M", approvedCode)

        composeTestRule.onNodeWithTag("reconcile_tab_unmatched").performClick()
        composeTestRule.onNodeWithTag("mpesa_reconcile_list")
            .performScrollToNode(hasTestTag("unmatched_receipt_card_mpesa_UNK4455667"))
        composeTestRule.onNodeWithTag("unmatched_receipt_card_mpesa_UNK4455667").assertIsDisplayed()
        composeTestRule.onNodeWithTag("match_lease_btn_mpesa_UNK4455667")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("lease_pick_option_lease_1").performClick()
        composeTestRule.onNodeWithTag("confirm_link_lease_button").performClick()
        assertEquals("lease_1", linkedLeaseId)

        composeTestRule.onNodeWithTag("mpesa_open_paste_dialog_button").performClick()
        composeTestRule.onNodeWithTag("mpesa_paste_sms_input").performTextInput(
            "AAA1112223 Confirmed. You have received Ksh10,000.00 from JOHN.\n" +
                "BBB4445556 Confirmed. You have received Ksh12,000.00 from MARY."
        )
        composeTestRule.onNodeWithText("Detected 2 confirmation(s):").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mpesa_stage_sms_button").performClick()
        assertTrue(pastedBatch!!.contains("AAA1112223"))
    }

    @Test
    fun `Tenant detected M-Pesa receipt card triggers 1-tap auto-fill into payment submission dialog`() {
        val detectedReceipt = MpesaReceipt(
            id = "mpesa_UBK7X92M10",
            transactionCode = "UBK7X92M10",
            amountCents = 2_850_000L,
            direction = MpesaReceiptDirection.OUTGOING_SENT.name,
            counterpartyName = "Kilimani Palms",
            rawSnippet = "UBK7X92M10 Confirmed. Ksh28,500.00 sent to Kilimani Palms on 20/11/26."
        )
        val lease = Lease(
            id = "lease_1",
            propertyName = "Kilimani Palms",
            unitNumber = "A101",
            rentCents = 3_000_000L
        )
        var usedReceipt = false

        composeTestRule.setContent {
            RentFlowTheme {
                DetectedMpesaReceiptCard(
                    receipt = detectedReceipt,
                    onUseReceipt = { usedReceipt = true },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("detected_mpesa_receipt_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("use_detected_receipt_button").performClick()
        assertTrue(usedReceipt)
    }

    @Test
    fun `TenantPaymentSubmissionDialog pre-fills transaction code and amount from initialPrefilledReceipt`() {
        val detectedReceipt = MpesaReceipt(
            id = "mpesa_UBK7X92M10",
            transactionCode = "UBK7X92M10",
            amountCents = 2_850_000L,
            direction = MpesaReceiptDirection.OUTGOING_SENT.name,
            counterpartyName = "Kilimani Palms",
            rawSnippet = "UBK7X92M10 Confirmed. Ksh28,500.00 sent to Kilimani Palms on 20/11/26."
        )
        val lease = Lease(
            id = "lease_1",
            propertyName = "Kilimani Palms",
            unitNumber = "A101",
            rentCents = 3_000_000L
        )

        composeTestRule.setContent {
            RentFlowTheme {
                TenantPaymentSubmissionDialog(
                    lease = lease,
                    targetInvoice = null,
                    defaultPhone = "+254712345678",
                    isBusy = false,
                    initialPrefilledReceipt = detectedReceipt,
                    onDismiss = {},
                    onSubmit = { _, _, _, _, _, _, _, _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithTag("mpesa_sms_parsed_status").assertTextContains("UBK7X92M10", substring = true)
        composeTestRule.onNodeWithTag("payment_reference_input").assertTextContains("UBK7X92M10", substring = true)
        composeTestRule.onNodeWithTag("payment_amount_input").assertTextContains("28500", substring = true)
    }
}
