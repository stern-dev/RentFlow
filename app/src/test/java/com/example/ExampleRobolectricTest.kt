package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.AuthScreen
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
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `rentflow branding and firestore database id are configured`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("RentFlow", context.getString(R.string.app_name))
        assertTrue(context.getString(R.string.firestore_database_id).isNotBlank())
    }

    @Test
    fun `auth screen renders primary actions and handles browse listings click`() {
        var browsed = false
        composeTestRule.setContent {
            RentFlowTheme {
                AuthScreen(
                    onAuthSuccess = {},
                    onBrowsePublicListings = { browsed = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("browse_listings_button").assertIsDisplayed().performClick()
        assertTrue(browsed)
    }
}
