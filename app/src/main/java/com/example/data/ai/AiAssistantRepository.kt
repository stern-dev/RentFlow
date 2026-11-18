package com.example.data.ai

import android.util.Log
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.PortfolioSummary
import com.example.domain.model.Invoice
import com.example.domain.model.MaintenanceCategory
import com.example.domain.model.MaintenancePriority
import com.example.domain.model.Property
import com.example.domain.model.RentalUnit
import com.example.domain.model.UnitStatus
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiAssistantRepo"
private const val AI_TIMEOUT_MS = 60_000L

sealed interface AiTaskState {
    data object Idle : AiTaskState
    data object Loading : AiTaskState
    data class Streaming(val text: String) : AiTaskState
    data class Success(val text: String) : AiTaskState
    data class Error(val message: String) : AiTaskState
}

data class MaintenanceTriageSuggestion(
    val suggestedPriority: MaintenancePriority,
    val suggestedCategory: MaintenanceCategory,
    val tenantReplyDraft: String,
    val technicianActionNote: String
)

class AiAssistantRepository {

    private val defaultModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    private val triageSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "priority" to Schema.enumeration(
                    values = MaintenancePriority.entries.map { it.name },
                    description = "Recommended maintenance urgency level"
                ),
                "category" to Schema.enumeration(
                    values = MaintenanceCategory.entries.map { it.name },
                    description = "Best matching repair category"
                ),
                "tenantReply" to Schema.string(
                    description = "Polite, clear status reply to send to the tenant"
                ),
                "technicianNote" to Schema.string(
                    description = "Concise inspection checklist or action note for the technician"
                )
            )
        )
    }

    private val triageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = triageSchema
            }
        )
    }

    fun streamPortfolioInsights(
        summary: PortfolioSummary,
        properties: List<Property>,
        units: List<RentalUnit>,
        overdueInvoices: List<Invoice>
    ): Flow<AiTaskState> = streamTextPrompt(
        prompt = buildPortfolioInsightsPrompt(summary, properties, units, overdueInvoices),
        timeoutMessage = "Insights generation timed out after 60 seconds. Please try again.",
        fallbackError = "Unable to generate portfolio insights right now."
    )

    fun streamUnitListingDraft(
        propertyName: String,
        city: String,
        neighborhood: String,
        unitNumber: String,
        unitTypeLabel: String,
        rentCents: Long,
        depositCents: Long,
        amenities: List<String>
    ): Flow<AiTaskState> {
        val rentDisplay = if (rentCents > 0L) CurrencyUtils.formatKes(rentCents) else "competitive monthly rent"
        val depositDisplay = if (depositCents > 0L) CurrencyUtils.formatKes(depositCents) else "standard deposit"
        val amenityText = if (amenities.isEmpty()) "standard utilities" else amenities.joinToString(", ")
        val prompt = """
            Write a concise, trustworthy Kenyan rental listing description (2-3 short sentences, max 55 words) for a property portal.
            Do not invent amenities or claims not listed below.
            Property: $propertyName in $neighborhood, $city
            Unit: $unitNumber ($unitTypeLabel)
            Monthly Rent: $rentDisplay | Security Deposit: $depositDisplay
            Amenities: $amenityText
            Return plain text only without markdown headings or bullet points.
        """.trimIndent()
        return streamTextPrompt(
            prompt = prompt,
            timeoutMessage = "Listing draft timed out. Please try again.",
            fallbackError = "Unable to draft unit description right now."
        )
    }

    fun streamAnnouncementDraft(
        propertyName: String,
        topic: String,
        extraContext: String
    ): Flow<AiTaskState> {
        val cleanTopic = topic.trim().ifBlank { "scheduled estate maintenance and utility update" }
        val prompt = """
            Draft a polite, clear property notice for tenants at $propertyName in Kenya.
            Topic: $cleanTopic
            Additional context: ${extraContext.trim().ifBlank { "General tenant notice" }}
            Requirements: 2-4 crisp sentences, respectful professional tone, include clear next steps or contact guidance if relevant.
            Return plain text only (no markdown, no subject prefix).
        """.trimIndent()
        return streamTextPrompt(
            prompt = prompt,
            timeoutMessage = "Notice draft timed out. Please try again.",
            fallbackError = "Unable to draft announcement right now."
        )
    }

    suspend fun triageMaintenanceRequest(
        propertyName: String,
        unitNumber: String,
        title: String,
        description: String,
        currentCategory: MaintenanceCategory,
        currentPriority: MaintenancePriority
    ): Result<MaintenanceTriageSuggestion> {
        val prompt = """
            You are a Kenyan residential property operations coordinator. Triage this maintenance ticket.
            Property: $propertyName • Unit $unitNumber
            Current Category: ${currentCategory.name}
            Current Priority: ${currentPriority.name}
            Ticket Title: ${title.trim()}
            Tenant Description: ${description.trim()}
            Provide the most accurate priority, category, a short polite tenantReply (1-2 sentences), and a practical technicianNote (1-2 sentences).
        """.trimIndent()

        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                triageModel.generateContent(prompt)
            }
            val jsonText = response.text
                ?: return Result.failure(IllegalStateException("AI returned an empty triage response."))
            Result.success(parseMaintenanceTriageJson(jsonText))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Maintenance triage timed out", e)
            Result.failure(IllegalStateException("AI triage timed out after 60 seconds. Please try again.", e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Maintenance triage failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "AI triage failed.", e))
        }
    }

    private fun streamTextPrompt(
        prompt: String,
        timeoutMessage: String,
        fallbackError: String
    ): Flow<AiTaskState> = flow {
        emit(AiTaskState.Loading)
        val accumulated = StringBuilder()
        try {
            withTimeout(AI_TIMEOUT_MS) {
                defaultModel.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(AiTaskState.Streaming(accumulated.toString()))
                    }
                }
            }
            val finalOutput = accumulated.toString().trim()
            if (finalOutput.isNotEmpty()) {
                emit(AiTaskState.Success(finalOutput))
            } else {
                emit(AiTaskState.Error("Gemini returned an empty response. Please try again."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI streaming timed out", e)
            emit(AiTaskState.Error(timeoutMessage))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI streaming failed", e)
            emit(AiTaskState.Error(e.localizedMessage?.takeIf { it.isNotBlank() } ?: fallbackError))
        }
    }

    companion object {
        internal fun buildPortfolioInsightsPrompt(
            summary: PortfolioSummary,
            properties: List<Property>,
            units: List<RentalUnit>,
            overdueInvoices: List<Invoice>
        ): String {
            val vacantUnits = units.count { it.statusEnum() == UnitStatus.AVAILABLE }
            val maintUnits = units.count { it.statusEnum() == UnitStatus.UNDER_MAINTENANCE }
            val topCities = properties.map { it.city }.filter { it.isNotBlank() }.distinct().joinToString(", ").ifBlank { "Kenya" }
            val overdueTotal = overdueInvoices.sumOf { it.outstandingCents }
            return """
                You are a concise property portfolio advisor for a Kenyan landlord using RentFlow (currency: KES).
                Analyze ONLY the real metrics below and give 3 crisp, actionable bullet points (using '-' prefix, max 110 words total) covering collection efficiency, vacancy recovery, and expense/maintenance focus.
                Portfolio Locations: $topCities
                Properties: ${summary.totalProperties} | Total Units: ${summary.totalUnits} | Occupied: ${summary.occupiedUnits} | Vacant: $vacantUnits | Under Maintenance: $maintUnits
                Expected Monthly Rent: ${CurrencyUtils.formatKes(summary.expectedMonthlyRentCents)}
                Collected This Portfolio View: ${CurrencyUtils.formatKes(summary.totalCollectedCents)}
                Outstanding Arrears: ${CurrencyUtils.formatKes(summary.outstandingRentCents)} (${overdueInvoices.size} overdue invoices totaling ${CurrencyUtils.formatKes(overdueTotal)})
                Expenses Logged: ${CurrencyUtils.formatKes(summary.totalExpensesCents)} | Net Income: ${CurrencyUtils.formatKes(summary.netRentalIncomeCents)}
                Pending Payment Verifications: ${summary.pendingPaymentsCount} | Open Repairs: ${summary.pendingMaintenanceCount}
            """.trimIndent()
        }

        internal fun parseMaintenanceTriageJson(jsonString: String): MaintenanceTriageSuggestion {
            val json = JSONObject(jsonString)
            val priorityName = json.optString("priority", MaintenancePriority.MEDIUM.name)
            val categoryName = json.optString("category", MaintenanceCategory.GENERAL_REPAIR.name)
            val priority = MaintenancePriority.entries.firstOrNull { it.name.equals(priorityName, ignoreCase = true) }
                ?: MaintenancePriority.MEDIUM
            val category = MaintenanceCategory.entries.firstOrNull { it.name.equals(categoryName, ignoreCase = true) }
                ?: MaintenanceCategory.GENERAL_REPAIR
            val reply = json.optString("tenantReply", "").trim()
                .ifBlank { "We have logged your maintenance request and are scheduling an inspection." }
            val techNote = json.optString("technicianNote", "").trim()
                .ifBlank { "Inspect issue on site and confirm required replacement parts before repair." }
            return MaintenanceTriageSuggestion(
                suggestedPriority = priority,
                suggestedCategory = category,
                tenantReplyDraft = reply,
                technicianActionNote = techNote
            )
        }
    }
}
