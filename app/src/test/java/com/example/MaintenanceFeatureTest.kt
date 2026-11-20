package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.OperationsRepository
import com.example.domain.model.Lease
import com.example.domain.model.LeaseStatus
import com.example.domain.model.MaintenanceCategory
import com.example.domain.model.MaintenancePriority
import com.example.domain.model.MaintenanceRequest
import com.example.domain.model.MaintenanceStatus
import com.example.domain.model.RentalUnit
import com.example.domain.model.UnitStatus
import com.example.ui.media.MaintenancePhotoProcessor
import com.example.ui.screens.EnhancedMaintenanceCard
import com.example.ui.screens.MaintenanceStatusTimeline
import com.example.ui.screens.MaintenanceSubmissionScreenDialog
import com.example.ui.screens.MaintenanceTicketFilter
import com.example.ui.screens.MaintenanceUnitTarget
import com.example.ui.screens.TenantFollowUpDialog
import com.example.ui.theme.RentFlowTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MaintenanceFeatureTest {

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

    private fun sampleDataUri(): String {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(AndroidColor.BLUE)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
        bitmap.recycle()
        return MaintenancePhotoProcessor.encodeJpegBytesToDataUri(out.toByteArray())
    }

    @Test
    fun `MaintenancePhotoProcessor encodes decodes and validates Firestore size bounds`() {
        val dataUri = sampleDataUri()
        assertTrue(MaintenancePhotoProcessor.isDataImageUri(dataUri))
        val decodedBytes = MaintenancePhotoProcessor.decodeDataUriBytes(dataUri)
        assertNotNull(decodedBytes)
        assertTrue(decodedBytes!!.isNotEmpty())
        assertTrue((MaintenancePhotoProcessor.estimateDataUriKb(dataUri) ?: 0) >= 1)
        assertNotNull(MaintenancePhotoProcessor.decodeDataUriToImageBitmap(dataUri))

        val valid = MaintenancePhotoProcessor.validateImageRefsPayload(listOf(dataUri, "  ", dataUri))
        assertTrue(valid.isSuccess)
        assertEquals(2, valid.getOrThrow().size)

        val tooMany = MaintenancePhotoProcessor.validateImageRefsPayload(List(5) { dataUri })
        assertTrue(tooMany.isFailure)

        val oversized = "data:image/jpeg;base64," + "A".repeat(MaintenancePhotoProcessor.MAX_TOTAL_IMAGE_REFS_CHARS + 10)
        val tooLarge = MaintenancePhotoProcessor.validateImageRefsPayload(listOf(oversized))
        assertTrue(tooLarge.isFailure)
    }

    @Test
    fun `MaintenanceTicketFilter matches status buckets accurately`() {
        val reqs = MaintenanceStatus.entries.mapIndexed { i, st ->
            MaintenanceRequest(id = "req_$i", status = st.name)
        }
        assertEquals(6, reqs.count { MaintenanceTicketFilter.ALL.matches(it) })
        assertEquals(2, reqs.count { MaintenanceTicketFilter.OPEN.matches(it) })
        assertEquals(2, reqs.count { MaintenanceTicketFilter.IN_PROGRESS.matches(it) })
        assertEquals(1, reqs.count { MaintenanceTicketFilter.COMPLETED.matches(it) })
        assertEquals(1, reqs.count { MaintenanceTicketFilter.REJECTED.matches(it) })
    }

    @Test
    fun `OperationsRepository maintenance calls fail fast when user is unauthenticated`() = runTest {
        val db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val repo = OperationsRepository(db = db, auth = auth)

        val submitRes = repo.submitMaintenanceRequestForUnit(
            unitId = "unit_a1",
            title = "Leaking tap",
            description = "Kitchen mixer tap dripping constantly",
            category = MaintenanceCategory.PLUMBING,
            priority = MaintenancePriority.HIGH,
            imageRefs = emptyList(),
            submitterName = "Wanjiku"
        )
        assertTrue(submitRes.isFailure)

        val updateRes = repo.appendTenantMaintenanceUpdate(
            request = MaintenanceRequest(
                id = "req_1",
                unitId = "unit_a1",
                tenantUid = "t1",
                status = MaintenanceStatus.SUBMITTED.name,
                description = "Initial issue"
            ),
            appendedNote = "Water reached cabinet",
            updatedImageRefs = emptyList(),
            actorName = "Wanjiku"
        )
        assertTrue(updateRes.isFailure)
    }

    @Test
    fun `MaintenanceSubmissionScreenDialog switches linked unit validates fields and submits`() {
        val target1 = MaintenanceUnitTarget.fromLease(
            Lease(
                id = "lease_1",
                unitId = "unit_doc_101",
                unitNumber = "A-101",
                propertyId = "prop_1",
                propertyName = "Kilimani Palms",
                tenantUid = "tenant_1",
                tenantName = "Amina Otieno",
                rentCents = 4_500_000L,
                status = LeaseStatus.ACTIVE.name
            )
        )
        val target2 = MaintenanceUnitTarget.fromRentalUnit(
            RentalUnit(
                id = "unit_doc_204",
                propertyId = "prop_2",
                propertyName = "Nyali Breeze",
                unitNumber = "B-204",
                status = UnitStatus.AVAILABLE.name
            )
        )

        var submittedTarget: MaintenanceUnitTarget? = null
        var submittedTitle = ""
        var submittedDesc = ""
        var submittedPriority: MaintenancePriority? = null

        composeTestRule.setContent {
            RentFlowTheme {
                MaintenanceSubmissionScreenDialog(
                    unitTargets = listOf(target1, target2),
                    initialUnitId = "unit_doc_101",
                    isBusy = false,
                    onDismiss = {},
                    onSubmit = { target, title, desc, _, prio, _ ->
                        submittedTarget = target
                        submittedTitle = title
                        submittedDesc = desc
                        submittedPriority = prio
                    }
                )
            }
        }

        // Verify linked unit summary displays Firestore unit doc ID
        composeTestRule.onNodeWithTag("linked_unit_summary_badge").assertTextContains("unit_doc_101", substring = true)

        // Try empty submit -> shows validation error and does not invoke callback
        composeTestRule.onNodeWithTag("submit_maintenance_button").performClick()
        assertEquals(null, submittedTarget)
        composeTestRule.onNodeWithText("Enter an issue title of at least 2 characters.").assertIsDisplayed()

        // Switch linked rental unit via dropdown
        composeTestRule.onNodeWithTag("unit_selector_dropdown").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("unit_option_unit_doc_204").performClick()
        composeTestRule.onNodeWithTag("linked_unit_summary_badge").assertTextContains("unit_doc_204", substring = true)

        // Fill title, priority, and description
        composeTestRule.onNodeWithTag("maintenance_title_input").performScrollTo().performTextInput("Shower head broken")
        composeTestRule.onNodeWithTag("maintenance_priority_chip_urgent").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("maintenance_description_input").performScrollTo()
            .performTextInput("Main bathroom shower valve snapped and leaks continuously.")

        composeTestRule.onNodeWithTag("submit_maintenance_button").performClick()

        assertEquals("unit_doc_204", submittedTarget?.unitId)
        assertEquals("Shower head broken", submittedTitle)
        assertEquals(MaintenancePriority.URGENT, submittedPriority)
        assertTrue(submittedDesc.contains("bathroom shower valve"))
    }

    @Test
    fun `EnhancedMaintenanceCard displays timeline photos unit link and lightbox callback`() {
        val photoUri = sampleDataUri()
        val req = MaintenanceRequest(
            id = "req_99",
            propertyName = "Kilimani Palms",
            unitId = "unit_doc_101",
            unitNumber = "A-101",
            tenantUid = "tenant_1",
            tenantName = "Amina Otieno",
            title = "Bedroom socket sparking",
            description = "Wall outlet near wardrobe sparks when plugged in.",
            category = MaintenanceCategory.ELECTRICAL.name,
            priority = MaintenancePriority.HIGH.name,
            status = MaintenanceStatus.IN_PROGRESS.name,
            assignedStaffName = "Peter Kamau",
            latestUpdateNote = "Breaker isolated; replacing outlet module.",
            imageRefs = listOf(photoUri)
        )

        var clickedPhotoIndex = -1
        composeTestRule.setContent {
            RentFlowTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    EnhancedMaintenanceCard(
                        req = req,
                        onPhotoClick = { idx -> clickedPhotoIndex = idx }
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("maintenance_card_req_99").assertIsDisplayed()
        composeTestRule.onNodeWithTag("maintenance_status_timeline").assertIsDisplayed()
        composeTestRule.onNodeWithTag("timeline_step_submitted").assertIsDisplayed()
        composeTestRule.onNodeWithTag("timeline_step_in_progress").assertIsDisplayed()
        composeTestRule.onNodeWithTag("timeline_step_completed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Linked Unit ID: unit_doc_101").assertIsDisplayed()
        composeTestRule.onNodeWithTag("maintenance_photo_thumb_0").performScrollTo().performClick()
        assertEquals(0, clickedPhotoIndex)
    }

    @Test
    fun `MaintenanceStatusTimeline displays rejected banner for rejected tickets`() {
        composeTestRule.setContent {
            RentFlowTheme {
                MaintenanceStatusTimeline(status = MaintenanceStatus.REJECTED)
            }
        }
        composeTestRule.onNodeWithTag("timeline_rejected_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ticket Closed as Rejected").assertIsDisplayed()
    }

    @Test
    fun `TenantFollowUpDialog validates empty updates and submits follow-up note`() {
        val req = MaintenanceRequest(
            id = "req_open_1",
            propertyName = "Kilimani Palms",
            unitId = "unit_doc_101",
            unitNumber = "A-101",
            tenantUid = "tenant_1",
            tenantName = "Amina Otieno",
            title = "Sink trap leak",
            description = "Slow drip under kitchen sink.",
            status = MaintenanceStatus.UNDER_REVIEW.name
        )

        var submittedNote = ""
        composeTestRule.setContent {
            RentFlowTheme {
                TenantFollowUpDialog(
                    request = req,
                    isBusy = false,
                    onDismiss = {},
                    onSubmit = { note, _ -> submittedNote = note }
                )
            }
        }

        composeTestRule.onNodeWithTag("submit_followup_button").performClick()
        assertFalse(submittedNote.isNotBlank())
        composeTestRule.onNodeWithText("Enter a follow-up note or attach new photos.").assertIsDisplayed()

        composeTestRule.onNodeWithTag("followup_note_input")
            .performTextInput("Key available with caretaker after 2pm.")
        composeTestRule.onNodeWithTag("submit_followup_button").performClick()
        assertEquals("Key available with caretaker after 2pm.", submittedNote)
    }
}
