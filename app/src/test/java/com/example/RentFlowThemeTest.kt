package com.example

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.ThemeMode
import com.example.data.repository.ThemePreferencesRepository
import com.example.ui.components.PillTone
import com.example.ui.components.StatusPill
import com.example.ui.theme.AlabasterBg
import com.example.ui.theme.ChampagneAccent
import com.example.ui.theme.ChampagneLight
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.ObsidianPrimary
import com.example.ui.theme.RentFlowTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RentFlowThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `theme preferences repository defaults to light mode and persists updates`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ThemePreferencesRepository(context)

        assertEquals(ThemeMode.LIGHT, repository.themeMode.first())

        repository.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repository.themeMode.first())

        repository.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repository.themeMode.first())
    }

    @Test
    fun `light theme applies obsidian and champagne palette with local flag false`() {
        var capturedPrimary: androidx.compose.ui.graphics.Color? = null
        var capturedSecondary: androidx.compose.ui.graphics.Color? = null
        var capturedBackground: androidx.compose.ui.graphics.Color? = null
        var capturedSurface: androidx.compose.ui.graphics.Color? = null
        var capturedIsDark: Boolean? = null

        composeTestRule.setContent {
            RentFlowTheme(darkTheme = false) {
                capturedPrimary = MaterialTheme.colorScheme.primary
                capturedSecondary = MaterialTheme.colorScheme.secondary
                capturedBackground = MaterialTheme.colorScheme.background
                capturedSurface = MaterialTheme.colorScheme.surface
                capturedIsDark = LocalIsDarkTheme.current
                Column {
                    StatusPill(label = "VERIFIED", tone = PillTone.SUCCESS)
                    StatusPill(label = "PENDING", tone = PillTone.WARNING)
                }
            }
        }

        assertEquals(ObsidianPrimary, capturedPrimary)
        assertEquals(ChampagneAccent, capturedSecondary)
        assertEquals(AlabasterBg, capturedBackground)
        assertEquals(CrispWhite, capturedSurface)
        assertFalse(capturedIsDark ?: true)
        composeTestRule.onNodeWithText("VERIFIED").assertIsDisplayed()
        composeTestRule.onNodeWithText("PENDING").assertIsDisplayed()
    }

    @Test
    fun `dark theme applies obsidian night palette with champagne gold accent and local flag true`() {
        var capturedBackground: androidx.compose.ui.graphics.Color? = null
        var capturedSurface: androidx.compose.ui.graphics.Color? = null
        var capturedSecondary: androidx.compose.ui.graphics.Color? = null
        var capturedOnSecondary: androidx.compose.ui.graphics.Color? = null
        var capturedIsDark: Boolean? = null

        composeTestRule.setContent {
            RentFlowTheme(darkTheme = true) {
                capturedBackground = MaterialTheme.colorScheme.background
                capturedSurface = MaterialTheme.colorScheme.surface
                capturedSecondary = MaterialTheme.colorScheme.secondary
                capturedOnSecondary = MaterialTheme.colorScheme.onSecondary
                capturedIsDark = LocalIsDarkTheme.current
                Column {
                    StatusPill(label = "SIGNED", tone = PillTone.SUCCESS)
                    StatusPill(label = "OVERDUE", tone = PillTone.ERROR)
                }
            }
        }

        assertEquals(DarkBg, capturedBackground)
        assertEquals(DarkSurface, capturedSurface)
        assertEquals(ChampagneLight, capturedSecondary)
        assertEquals(ObsidianPrimary, capturedOnSecondary)
        assertTrue(capturedIsDark ?: false)
        composeTestRule.onNodeWithText("SIGNED").assertIsDisplayed()
        composeTestRule.onNodeWithText("OVERDUE").assertIsDisplayed()
    }
}
