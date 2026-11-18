package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.firebase.AuthManager
import com.example.data.firebase.AuthSignInResult
import com.example.domain.finance.CurrencyUtils
import com.example.domain.model.UserRole
import com.example.ui.components.ErrorBannerCard
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.NavySecondary
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onBrowsePublicListings: () -> Unit,
    isSigningIn: Boolean = false,
    externalError: String? = null,
    onSignInClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localLoading by remember { mutableStateOf(false) }
    var localErrorMsg by remember { mutableStateOf<String?>(null) }
    val effectiveLoading = isSigningIn || localLoading
    val displayedError = externalError ?: localErrorMsg

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .widthIn(max = 560.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_auth_hero),
                    contentDescription = "RentFlow modern Kenyan urban properties",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(NavyPrimary.copy(alpha = 0.25f), NavyPrimary.copy(alpha = 0.88f))
                            )
                        )
                        .padding(20.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(color = EmeraldAccent, shape = RoundedCornerShape(10.dp)) {
                                Icon(
                                    imageVector = Icons.Filled.Apartment,
                                    contentDescription = "RentFlow Logo",
                                    tint = Color.White,
                                    modifier = Modifier.padding(6.dp).size(20.dp)
                                )
                            }
                            Text(
                                text = "RENTFLOW",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFFCCFBF1)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Unified Rental Operations & Tenant Home",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Text(
                            text = "Built for landlords, property managers & tenants in Kenya (KES • M-Pesa).",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "One Platform. Every Role.",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Sign in once with Google to manage your portfolio, collect rent, verify M-Pesa references, submit maintenance tickets, or track your tenancy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!displayedError.isNullOrBlank()) {
                        ErrorBannerCard(displayedError)
                    }

                    Button(
                        onClick = {
                            if (onSignInClick != null) {
                                onSignInClick()
                            } else {
                                localErrorMsg = null
                                localLoading = true
                                scope.launch {
                                    when (val res = AuthManager.signInInteractive(context)) {
                                        is AuthSignInResult.Success -> {
                                            localLoading = false
                                            onAuthSuccess()
                                        }
                                        is AuthSignInResult.Cancelled -> {
                                            localLoading = false
                                        }
                                        is AuthSignInResult.Error -> {
                                            localLoading = false
                                            localErrorMsg = res.message
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !effectiveLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        if (effectiveLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sign in with Google", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    OutlinedButton(
                        onClick = onBrowsePublicListings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("browse_listings_button")
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Browse Available Kenyan Rentals")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FeatureBadgeCard(Icons.Filled.Business, "Landlords", "Portfolios & KES Reports", Modifier.weight(1f))
                FeatureBadgeCard(Icons.Filled.Key, "Tenants", "Rent, Receipts & Repairs", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FeatureBadgeCard(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = title, tint = EmeraldAccent, modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RoleOnboardingScreen(
    isBusy: Boolean,
    errorText: String?,
    onSubmit: (
        fullName: String,
        email: String,
        phoneNumber: String?,
        roles: List<UserRole>,
        activeRole: UserRole,
        organizationName: String?
    ) -> Unit,
    onSignOut: () -> Unit
) {
    val firebaseUser = Firebase.auth.currentUser
    var fullName by remember { mutableStateOf(firebaseUser?.displayName ?: "") }
    val email = firebaseUser?.email ?: ""
    var phone by remember { mutableStateOf("+254") }
    var orgName by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val selectedRoles = remember { mutableStateListOf(UserRole.LANDLORD) }
    var primaryRole by remember { mutableStateOf(UserRole.LANDLORD) }

    val availableRoles = listOf(
        Triple(UserRole.LANDLORD, "Landlord / Property Owner", Icons.Filled.Business),
        Triple(UserRole.PROPERTY_MANAGER, "Property Manager", Icons.Filled.ManageAccounts),
        Triple(UserRole.TENANT, "Tenant / Prospective Renter", Icons.Filled.Home),
        Triple(UserRole.MAINTENANCE_STAFF, "Maintenance Staff", Icons.Filled.Build)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .widthIn(max = 600.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(Brush.horizontalGradient(listOf(NavyPrimary, NavySecondary)))
                .padding(20.dp)
        ) {
            Column {
                Text("Complete Your RentFlow Profile", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Choose one or more roles for your workspace. You can switch roles anytime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCCFBF1)
                )
            }
        }

        if (!validationError.isNullOrBlank()) ErrorBannerCard(validationError!!)
        if (!errorText.isNullOrBlank()) ErrorBannerCard(errorText)

        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Account Details", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("onboarding_name_input")
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = {},
                    enabled = false,
                    label = { Text("Verified Google Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Kenyan Phone (+2547... or 07...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("onboarding_phone_input")
                )
            }
        }

        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select Your Roles & Primary Dashboard", style = MaterialTheme.typography.titleMedium)
                availableRoles.forEach { (role, title, icon) ->
                    val checked = role in selectedRoles
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                if (checked && selectedRoles.size > 1) {
                                    selectedRoles.remove(role)
                                    if (primaryRole == role) primaryRole = selectedRoles.first()
                                } else if (!checked) {
                                    selectedRoles.add(role)
                                }
                            }
                            .padding(vertical = 6.dp)
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                if (isChecked && role !in selectedRoles) selectedRoles.add(role)
                                else if (!isChecked && selectedRoles.size > 1) {
                                    selectedRoles.remove(role)
                                    if (primaryRole == role) primaryRole = selectedRoles.first()
                                }
                            }
                        )
                        Icon(icon, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (checked) {
                            RadioButton(
                                selected = primaryRole == role,
                                onClick = { primaryRole = role }
                            )
                        }
                    }
                }
                if (UserRole.LANDLORD in selectedRoles || UserRole.PROPERTY_MANAGER in selectedRoles) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = orgName,
                        onValueChange = { orgName = it },
                        label = { Text("Organization / Portfolio Name (e.g. Acacia Estates Ltd)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("onboarding_org_input")
                    )
                }
            }
        }

        Button(
            onClick = {
                val trimmedName = fullName.trim()
                val normalizedPhone = CurrencyUtils.normalizeKenyanPhone(phone)
                when {
                    trimmedName.length < 2 -> validationError = "Please enter your full name."
                    phone.isNotBlank() && phone != "+254" && !CurrencyUtils.isValidKenyanPhone(phone) ->
                        validationError = "Enter a valid Kenyan phone number (e.g. +254712345678)."
                    (UserRole.LANDLORD in selectedRoles) && orgName.trim().length < 2 ->
                        validationError = "Enter an organization or portfolio name for your properties."
                    else -> {
                        validationError = null
                        onSubmit(
                            trimmedName,
                            email,
                            if (phone == "+254") null else normalizedPhone,
                            selectedRoles.toList(),
                            primaryRole,
                            orgName.trim().ifBlank { null }
                        )
                    }
                }
            },
            enabled = !isBusy,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("submit_onboarding_button")
        ) {
            if (isBusy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("Launch RentFlow Workspace")
        }

        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text("Use Another Google Account")
        }
    }
}
