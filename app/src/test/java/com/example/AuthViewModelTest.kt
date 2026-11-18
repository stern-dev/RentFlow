package com.example

import android.app.Activity
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.AuthSignInResult
import com.example.data.repository.GoogleCredentialGateway
import com.example.data.repository.GoogleCredentialResult
import com.example.data.repository.UserRepository
import com.example.ui.screens.AuthScreen
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

    private fun createRepository(gateway: GoogleCredentialGateway): UserRepository {
        val db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
        val auth = FirebaseAuth.getInstance()
        return UserRepository(db = db, auth = auth, credentialGateway = gateway)
    }

    @Test
    fun `signInWithGoogle requires Activity context and fails safely`() = runTest {
        val repo = createRepository(object : GoogleCredentialGateway {
            override suspend fun getAutoSignInCredential(context: Context, serverClientId: String) =
                GoogleCredentialResult.NoCredential

            override suspend fun getInteractiveSignInCredential(activity: Activity, serverClientId: String) =
                GoogleCredentialResult.Cancelled

            override suspend fun clearCredentialState(context: Context) = Unit
        })

        val result = repo.signInWithGoogle(context)
        assertTrue(result is AuthSignInResult.Error)
    }

    @Test
    fun `signInWithGoogle returns Cancelled when Credential Manager sheet is dismissed`() = runTest {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val repo = createRepository(object : GoogleCredentialGateway {
            override suspend fun getAutoSignInCredential(context: Context, serverClientId: String) =
                GoogleCredentialResult.NoCredential

            override suspend fun getInteractiveSignInCredential(activity: Activity, serverClientId: String) =
                GoogleCredentialResult.Cancelled

            override suspend fun clearCredentialState(context: Context) = Unit
        })

        val result = repo.signInWithGoogle(activity)
        assertEquals(AuthSignInResult.Cancelled, result)
    }

    @Test
    fun `attemptAutoSignIn returns false when no authorized credentials exist`() = runTest {
        val repo = createRepository(object : GoogleCredentialGateway {
            override suspend fun getAutoSignInCredential(context: Context, serverClientId: String) =
                GoogleCredentialResult.NoCredential

            override suspend fun getInteractiveSignInCredential(activity: Activity, serverClientId: String) =
                GoogleCredentialResult.Cancelled

            override suspend fun clearCredentialState(context: Context) = Unit
        })

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
        composeTestRule.onNodeWithTag("google_sign_in_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("google_sign_in_button").performClick()
        assertFalse(clicked)
    }
}
