package com.example

import android.app.Activity
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.AuthSignInResult
import com.example.data.repository.GoogleCredentialGateway
import com.example.data.repository.GoogleCredentialResult
import com.example.data.repository.UserRepository
import com.example.domain.model.InviteStatus
import com.example.domain.model.RoleInvite
import com.example.domain.model.UserRole
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.EmailVerificationScreen
import com.example.ui.screens.RoleOnboardingScreen
import com.example.ui.theme.RentFlowTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthViewModelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    private val noopGateway = object : GoogleCredentialGateway {
        override suspend fun getAutoSignInCredential(context: Context, serverClientId: String) =
            GoogleCredentialResult.NoCredential

        override suspend fun getInteractiveSignInCredential(activity: Activity, serverClientId: String) =
            GoogleCredentialResult.Cancelled

        override suspend fun clearCredentialState(context: Context) = Unit
    }

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

    private fun createRepository(gateway: GoogleCredentialGateway = noopGateway): UserRepository {
        val db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
        val auth = FirebaseAuth.getInstance()
        return UserRepository(db = db, auth = auth, credentialGateway = gateway)
    }

    @Test
    fun `signInWithGoogle requires Activity context and fails safely`() = runTest {
        val repo = createRepository()

        val result = repo.signInWithGoogle(context)
        assertTrue(result is AuthSignInResult.Error)
    }

    @Test
    fun `signInWithGoogle returns Cancelled when Credential Manager sheet is dismissed`() = runTest {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val repo = createRepository()

        val result = repo.signInWithGoogle(activity)
        assertEquals(AuthSignInResult.Cancelled, result)
    }

    @Test
    fun `attemptAutoSignIn returns false when no authorized credentials exist`() = runTest {
        val repo = createRepository()

        assertFalse(repo.attemptAutoSignIn(context))
    }

    @Test
    fun `signOut invokes credential state cleanup without throwing`() = runTest {
        var cleared = false
        val repo = createRepository(object : GoogleCredentialGateway {
            override suspend fun getAutoSignInCredential(context: Context, serverClientId: String) =
                GoogleCredentialResult.NoCredential

            override suspend fun getInteractiveSignInCredential(activity: Activity, serverClientId: String) =
                GoogleCredentialResult.Cancelled

            override suspend fun clearCredentialState(context: Context) {
                cleared = true
            }
        })

        repo.signOut(context)
        assertTrue(cleared)
    }

    @Test
    fun `email and password validators enforce strong credentials`() {
        assertTrue(UserRepository.isValidEmailFormat("landlord@acacia.co.ke"))
        assertFalse(UserRepository.isValidEmailFormat("invalid-email"))
        assertFalse(UserRepository.isValidEmailFormat("user@"))

        assertTrue(UserRepository.isStrongPassword("RentFlow2025"))
        assertFalse(UserRepository.isStrongPassword("short1"))
        assertFalse(UserRepository.isStrongPassword("onlyletters"))
        assertFalse(UserRepository.isStrongPassword("12345678"))
    }

    @Test
    fun `email repository methods fail fast on invalid input or missing session`() = runTest {
        val repo = createRepository()
        repo.signOut(context)

        assertTrue(repo.signInWithEmail("bad-email", "Pass1234") is AuthSignInResult.Error)
        assertTrue(repo.signInWithEmail("user@example.com", "") is AuthSignInResult.Error)
        assertTrue(repo.registerWithEmail("A", "user@example.com", "Pass1234") is AuthSignInResult.Error)
        assertTrue(repo.registerWithEmail("Jane Doe", "bad", "Pass1234") is AuthSignInResult.Error)
        assertTrue(repo.registerWithEmail("Jane Doe", "user@example.com", "weak") is AuthSignInResult.Error)

        assertTrue(repo.sendPasswordReset("bad-email").isFailure)
        assertTrue(repo.resendVerificationEmail().isFailure)
        assertTrue(repo.refreshEmailVerificationStatus().isFailure)
        assertTrue(repo.reauthenticateWithPassword("Pass1234").isFailure)
    }

    @Test
    fun `authScreen renders signing-in disabled state and external error banner`() {
        var clicked = false
        composeTestRule.setContent {
            RentFlowTheme {
                AuthScreen(
                    onAuthSuccess = {},
                    onBrowsePublicListings = {},
                    isSigningIn = true,
                    externalError = "No Google account found",
                    onSignInClick = { clicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("No Google account found").assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("google_sign_in_button").performClick()
        assertFalse(clicked)
    }

    @Test
    fun `authScreen registration tab validates inputs and submits via callback`() {
        var submittedName = ""
        var submittedEmail = ""
        composeTestRule.setContent {
            RentFlowTheme {
                AuthScreen(
                    onAuthSuccess = {},
                    onBrowsePublicListings = {},
                    onEmailRegister = { name, email, _ ->
                        submittedName = name
                        submittedEmail = email
                    }
                )
            }
        }

        composeTestRule.onNodeWithTag("auth_mode_register_tab").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("register_name_input").performScrollTo().performTextInput("Amina Wanjiku")
        composeTestRule.onNodeWithTag("email_input").performScrollTo().performTextInput("invalid-email")
        composeTestRule.onNodeWithTag("password_input").performScrollTo().performTextInput("RentFlow2025")
        composeTestRule.onNodeWithTag("confirm_password_input").performScrollTo().performTextInput("RentFlow2025")
        composeTestRule.onNodeWithTag("email_auth_submit_button").performScrollTo().performClick()

        composeTestRule.onNodeWithText("Please enter a valid email address.").assertIsDisplayed()
        assertEquals("", submittedEmail)

        composeTestRule.onNodeWithTag("email_input").performScrollTo().performTextReplacement("amina@rentflow.co.ke")
        composeTestRule.onNodeWithTag("email_auth_submit_button").performScrollTo().performClick()
        assertEquals("Amina Wanjiku", submittedName)
        assertEquals("amina@rentflow.co.ke", submittedEmail)
    }

    @Test
    fun `authScreen forgot password dialog sends reset request`() {
        var resetTarget = ""
        composeTestRule.setContent {
            RentFlowTheme {
                AuthScreen(
                    onAuthSuccess = {},
                    onBrowsePublicListings = {},
                    onSendPasswordReset = { resetTarget = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("forgot_password_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("forgot_password_email_input").performTextInput("tenant@acacia.co.ke")
        composeTestRule.onNodeWithTag("forgot_password_send_button").performClick()

        assertEquals("tenant@acacia.co.ke", resetTarget)
    }

    @Test
    fun `emailVerificationScreen triggers verify check and resend cooldown`() {
        var checked = false
        var resent = false
        composeTestRule.setContent {
            RentFlowTheme {
                EmailVerificationScreen(
                    email = "amina@rentflow.co.ke",
                    isBusy = false,
                    errorText = null,
                    infoText = null,
                    onCheckVerified = { checked = true },
                    onResendEmail = { resent = true },
                    onSignOut = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("check_verification_button").performClick()
        assertTrue(checked)

        composeTestRule.onNodeWithTag("resend_verification_button").performClick()
        assertTrue(resent)
        composeTestRule.onNodeWithTag("resend_verification_button").assertIsNotEnabled()
    }

    @Test
    fun `roleOnboardingScreen auto-detects pending invites and forwards chosen invites`() {
        val pendingInvite = RoleInvite(
            id = "inv_1",
            orgId = "org_acacia",
            orgName = "Acacia Estates Ltd",
            inviterUid = "owner_1",
            inviterName = "David Kamau",
            inviteeEmail = "manager@acacia.co.ke",
            role = UserRole.PROPERTY_MANAGER.name,
            status = InviteStatus.PENDING.name
        )
        var capturedInvites: List<RoleInvite> = emptyList()
        var capturedRoles: List<UserRole> = emptyList()

        composeTestRule.setContent {
            RentFlowTheme {
                RoleOnboardingScreen(
                    isBusy = false,
                    errorText = null,
                    pendingInvites = listOf(pendingInvite),
                    onSubmit = { _, _, _, roles, _, _, invites ->
                        capturedRoles = roles
                        capturedInvites = invites
                    },
                    onSignOut = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("onboarding_pending_invites_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_name_input").performScrollTo().performTextInput("Grace Muthoni")
        composeTestRule.onNodeWithTag("submit_onboarding_button").performScrollTo().performClick()

        assertEquals(listOf(pendingInvite), capturedInvites)
        assertTrue(UserRole.PROPERTY_MANAGER in capturedRoles)
    }
}
