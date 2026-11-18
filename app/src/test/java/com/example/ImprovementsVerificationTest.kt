package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiAssistantRepository
import com.example.data.repository.FinanceRepository
import com.example.data.repository.OperationsRepository
import com.example.data.repository.PropertyRepository
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.PortfolioSummary
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.MaintenanceCategory
import com.example.domain.model.MaintenancePriority
import com.example.domain.model.Property
import com.example.domain.model.RentalUnit
import com.example.domain.model.UnitStatus
import com.example.ui.viewmodel.LandlordViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ImprovementsVerificationTest {

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
    fun `CurrencyUtils validates billing periods and M-Pesa references accurately`() {
        assertTrue(CurrencyUtils.isValidBillingPeriod("2026-01"))
        assertTrue(CurrencyUtils.isValidBillingPeriod("2026-12"))
        assertTrue(CurrencyUtils.isValidBillingPeriod(" 2026-11 "))
        assertFalse(CurrencyUtils.isValidBillingPeriod("2026-13"))
        assertFalse(CurrencyUtils.isValidBillingPeriod("2026-00"))
        assertFalse(CurrencyUtils.isValidBillingPeriod("26-11"))
        assertFalse(CurrencyUtils.isValidBillingPeriod("Nov 2026"))

        assertTrue(CurrencyUtils.isValidMpesaReference("UBK7X92M10"))
        assertTrue(CurrencyUtils.isValidMpesaReference("SIM-1234ABCD"))
        assertTrue(CurrencyUtils.isValidMpesaReference("ABCD"))
        assertFalse(CurrencyUtils.isValidMpesaReference("ABC"))
        assertFalse(CurrencyUtils.isValidMpesaReference("REF#1234"))
        assertFalse(CurrencyUtils.isValidMpesaReference(""))
    }

    @Test
    fun `AiAssistantRepository buildPortfolioInsightsPrompt includes metrics and overdue totals`() {
        val summary = PortfolioSummary(
            totalProperties = 2,
            totalUnits = 10,
            occupiedUnits = 8,
            vacantUnits = 2,
            expectedMonthlyRentCents = 20_000_000L,
            totalCollectedCents = 15_000_000L,
            outstandingRentCents = 5_000_000L,
            totalExpensesCents = 1_200_000L,
            netRentalIncomeCents = 13_800_000L,
            pendingPaymentsCount = 1,
            pendingMaintenanceCount = 2
        )
        val props = listOf(
            Property(id = "p1", name = "Kilimani Heights", city = "Nairobi"),
            Property(id = "p2", name = "Nyali Breeze", city = "Mombasa")
        )
        val units = listOf(
            RentalUnit(id = "u1", status = UnitStatus.AVAILABLE.name),
            RentalUnit(id = "u2", status = UnitStatus.UNDER_MAINTENANCE.name)
        )
        val overdue = listOf(
            Invoice(id = "i1", outstandingCents = 2_500_000L, status = InvoiceStatus.OVERDUE.name)
        )

        val prompt = AiAssistantRepository.buildPortfolioInsightsPrompt(summary, props, units, overdue)
        assertTrue(prompt.contains("Nairobi, Mombasa"))
        assertTrue(prompt.contains("Occupied: 8"))
        assertTrue(prompt.contains("Vacant: 1"))
        assertTrue(prompt.contains("Under Maintenance: 1"))
        assertTrue(prompt.contains("1 overdue invoices totaling KES 25,000"))
    }

    @Test
    fun `AiAssistantRepository parseMaintenanceTriageJson handles valid lowercase and missing fields`() {
        val validJson = """
            {
              "priority": "urgent",
              "category": "plumbing",
              "tenantReply": "A plumber is scheduled for 2pm today.",
              "technicianNote": "Inspect main riser shut-off valve."
            }
        """.trimIndent()
        val parsed = AiAssistantRepository.parseMaintenanceTriageJson(validJson)
        assertEquals(MaintenancePriority.URGENT, parsed.suggestedPriority)
        assertEquals(MaintenanceCategory.PLUMBING, parsed.suggestedCategory)
        assertEquals("A plumber is scheduled for 2pm today.", parsed.tenantReplyDraft)
        assertEquals("Inspect main riser shut-off valve.", parsed.technicianActionNote)

        val fallbackJson = """{"priority":"unknown","category":"other"}"""
        val fallback = AiAssistantRepository.parseMaintenanceTriageJson(fallbackJson)
        assertEquals(MaintenancePriority.MEDIUM, fallback.suggestedPriority)
        assertEquals(MaintenanceCategory.GENERAL_REPAIR, fallback.suggestedCategory)
        assertTrue(fallback.tenantReplyDraft.isNotBlank())
        assertTrue(fallback.technicianActionNote.isNotBlank())
    }

    @Test
    fun `LandlordViewModel unit filter toggles and clears state properly`() {
        val db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
        val auth = FirebaseAuth.getInstance()
        val propRepo = PropertyRepository(db, auth)
        val finRepo = FinanceRepository(db, auth)
        val opsRepo = OperationsRepository(db, auth)
        val vm = LandlordViewModel(propRepo, finRepo, opsRepo, "user_test_1")

        assertNull(vm.unitFilterState.value.status)
        assertNull(vm.unitFilterState.value.propertyId)
        assertEquals("", vm.unitFilterState.value.searchQuery)

        vm.setUnitStatusFilter(UnitStatus.AVAILABLE)
        assertEquals(UnitStatus.AVAILABLE, vm.unitFilterState.value.status)
        // Toggling same status clears it
        vm.setUnitStatusFilter(UnitStatus.AVAILABLE)
        assertNull(vm.unitFilterState.value.status)

        vm.setUnitPropertyFilter("prop_1")
        assertEquals("prop_1", vm.unitFilterState.value.propertyId)
        vm.setUnitSearchQuery("A-102")
        assertEquals("A-102", vm.unitFilterState.value.searchQuery)

        vm.clearUnitFilters()
        assertNull(vm.unitFilterState.value.status)
        assertNull(vm.unitFilterState.value.propertyId)
        assertEquals("", vm.unitFilterState.value.searchQuery)
    }

    @Test
    fun `FinanceRepository and OperationsRepository blank-ID observers emit empty lists safely`() = runTest {
        val db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
        val auth = FirebaseAuth.getInstance()
        val finRepo = FinanceRepository(db, auth)
        val opsRepo = OperationsRepository(db, auth)

        assertTrue(finRepo.observeInvoicesForLandlord("").first().isEmpty())
        assertTrue(finRepo.observeInvoicesForTenant("   ").first().isEmpty())
        assertTrue(finRepo.observePaymentsForLandlord("").first().isEmpty())
        assertTrue(finRepo.observePaymentsForTenant("").first().isEmpty())
        assertTrue(finRepo.observeExpensesForLandlord("").first().isEmpty())

        assertTrue(opsRepo.observeMaintenanceForLandlord("").first().isEmpty())
        assertTrue(opsRepo.observeMaintenanceForTenant("").first().isEmpty())
        assertTrue(opsRepo.observeMaintenanceForStaff("").first().isEmpty())
        assertTrue(opsRepo.observeAnnouncementsForLandlord("").first().isEmpty())
        assertTrue(opsRepo.observeAnnouncementsForTenant("").first().isEmpty())
        assertTrue(opsRepo.observeConversations("").first().isEmpty())
        assertTrue(opsRepo.observeMessages("").first().isEmpty())
        assertTrue(opsRepo.observeNotifications("").first().isEmpty())
    }
}
