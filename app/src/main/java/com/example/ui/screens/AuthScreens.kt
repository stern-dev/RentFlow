package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.firebase.AuthManager
import com.example.data.firebase.AuthSignInResult
import com.example.data.repository.UserRepository
import com.example.domain.finance.CurrencyUtils
import com.example.domain.model.InviteStatus
import com.example.domain.model.RoleInvite
import com.example.domain.model.UserRole
import com.example.ui.components.ErrorBannerCard
import com.example.ui.theme.BrushedSilver
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkPrimaryContainer
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.MidnightInk
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.MutedCobalt
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusSuccessBgDark
import com.example.ui.theme.StatusSuccessLight
import com.example.ui.theme.StatusWarning
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onBrowsePublicListings: () -> Unit,
    isSigningIn: Boolean = false,
    externalError: String? = null,
    infoMessage: String? = null,
    onSignInClick: (() -> Unit)? = null,
    onEmailSignIn: ((email: String, password: String) -> Unit)? = null,
    onEmailRegister: ((fullName: String, email: String, password: String) -> Unit)? = null,
    onSendPasswordReset: ((email: String) -> Unit)? = null,
    onClearMessages: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localLoading by remember { mutableStateOf(false) }
    var localErrorMsg by remember { mutableStateOf<String?>(null) }
    var localInfoMsg by remember { mutableStateOf<String?>(null) }

    var isRegisterMode by remember { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    val effectiveLoading = isSigningIn || localLoading
    val displayedError = localErrorMsg ?: externalError
    val displayedInfo = localInfoMsg ?: infoMessage

    fun clearTransientFeedback() {
        localErrorMsg = null
        localInfoMsg = null
        onClearMessages?.invoke()
    }

    fun submitEmailForm() {
        clearTransientFeedback()
        val trimmedEmail = email.trim()
        val trimmedName = fullName.trim()
        if (isRegisterMode && trimmedName.length < 2) {
            localErrorMsg = "Please enter your full name (at least 2 characters)."
            return
        }
        if (!UserRepository.isValidEmailFormat(trimmedEmail)) {
            localErrorMsg = "Please enter a valid email address."
            return
        }
        if (password.isBlank()) {
            localErrorMsg = "Please enter your password."
            return
        }
        if (isRegisterMode) {
            if (!UserRepository.isStrongPassword(password)) {
                localErrorMsg = "Password must be at least 8 characters with both letters and numbers."
                return
            }
            if (password != confirmPassword) {
                localErrorMsg = "Passwords do not match."
                return
            }
            if (onEmailRegister != null) {
                onEmailRegister(trimmedName, trimmedEmail, password)
            } else {
                localLoading = true
                scope.launch {
                    when (val res = UserRepository(context).registerWithEmail(trimmedName, trimmedEmail, password)) {
                        is AuthSignInResult.Success -> {
                            localLoading = false
                            onAuthSuccess()
                        }
                        is AuthSignInResult.Cancelled -> localLoading = false
                        is AuthSignInResult.Error -> {
                            localLoading = false
                            localErrorMsg = res.message
                        }
                    }
                }
            }
        } else {
            if (onEmailSignIn != null) {
                onEmailSignIn(trimmedEmail, password)
            } else {
                localLoading = true
                scope.launch {
                    when (val res = UserRepository(context).signInWithEmail(trimmedEmail, password)) {
                        is AuthSignInResult.Success -> {
                            localLoading = false
                            onAuthSuccess()
                        }
                        is AuthSignInResult.Cancelled -> localLoading = false
                        is AuthSignInResult.Error -> {
                            localLoading = false
                            localErrorMsg = res.message
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
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
                    .height(156.dp)
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
                                listOf(MidnightInk.copy(alpha = 0.24f), MidnightInk.copy(alpha = 0.93f))
                            )
                        )
                        .padding(20.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column {
                        Surface(
                            color = MutedCobalt.copy(alpha = 0.34f),
                            border = BorderStroke(1.dp, BrushedSilver.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Apartment,
                                    contentDescription = "RentFlow Logo",
                                    tint = BrushedSilver,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "RENTFLOW",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = BrushedSilver
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Unified Rental Operations & Tenant Home",
                            style = MaterialTheme.typography.headlineSmall,
                            color = DarkTextPrimary
                        )
                        Text(
                            text = "Built for landlords, property managers & tenants in Kenya (KES • M-Pesa).",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkTextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = if (isRegisterMode) "Create Your RentFlow Account" else "Sign In to RentFlow",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !isRegisterMode,
                            onClick = {
                                if (isRegisterMode) {
                                    isRegisterMode = false
                                    clearTransientFeedback()
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            enabled = !effectiveLoading,
                            modifier = Modifier.testTag("auth_mode_sign_in_tab")
                        ) {
                            Text("Sign In")
                        }
                        SegmentedButton(
                            selected = isRegisterMode,
                            onClick = {
                                if (!isRegisterMode) {
                                    isRegisterMode = true
                                    clearTransientFeedback()
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            enabled = !effectiveLoading,
                            modifier = Modifier.testTag("auth_mode_register_tab")
                        ) {
                            Text("Create Account")
                        }
                    }

                    if (!displayedError.isNullOrBlank()) {
                        ErrorBannerCard(displayedError)
                    }

                    if (!displayedInfo.isNullOrBlank()) {
                        InfoBannerCard(displayedInfo)
                    }

                    AnimatedVisibility(visible = isRegisterMode) {
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = {
                                fullName = it
                                clearTransientFeedback()
                            },
                            label = { Text("Full Name") },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                            singleLine = true,
                            enabled = !effectiveLoading,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_name_input")
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            clearTransientFeedback()
                        },
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                        singleLine = true,
                        enabled = !effectiveLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_input")
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            clearTransientFeedback()
                        },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !effectiveLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (isRegisterMode) ImeAction.Next else ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { if (!isRegisterMode) submitEmailForm() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input")
                    )

                    AnimatedVisibility(visible = isRegisterMode) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            PasswordStrengthMeter(password = password)
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = {
                                    confirmPassword = it
                                    clearTransientFeedback()
                                },
                                label = { Text("Confirm Password") },
                                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (confirmPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = if (confirmPasswordVisible) "Hide confirmation password" else "Show confirmation password"
                                        )
                                    }
                                },
                                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                enabled = !effectiveLoading,
                                isError = confirmPassword.isNotEmpty() && confirmPassword != password,
                                supportingText = if (confirmPassword.isNotEmpty() && confirmPassword != password) {
                                    { Text("Passwords do not match") }
                                } else null,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { submitEmailForm() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("confirm_password_input")
                            )
                        }
                    }

                    AnimatedVisibility(visible = !isRegisterMode) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            TextButton(
                                onClick = {
                                    clearTransientFeedback()
                                    showForgotPasswordDialog = true
                                },
                                enabled = !effectiveLoading,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("forgot_password_button")
                            ) {
                                Text("Forgot Password?")
                            }
                        }
                    }

                    Button(
                        onClick = { submitEmailForm() },
                        enabled = !effectiveLoading,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("email_auth_submit_button")
                    ) {
                        if (effectiveLoading) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isRegisterMode) "Create Account & Verify Email" else "Sign In with Email",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            text = "OR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    OutlinedButton(
                        onClick = {
                            clearTransientFeedback()
                            if (onSignInClick != null) {
                                onSignInClick()
                            } else {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        Icon(
                            Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Continue with Google", fontWeight = FontWeight.SemiBold)
                    }

                    TextButton(
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

    if (showForgotPasswordDialog) {
        var resetEmail by remember { mutableStateOf(email.trim()) }
        var dialogError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = { Text("Reset Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter the email registered with your RentFlow account to receive a password reset link.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!dialogError.isNullOrBlank()) {
                        ErrorBannerCard(dialogError!!)
                    }
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = {
                            resetEmail = it
                            dialogError = null
                        },
                        label = { Text("Email Address") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_password_email_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = resetEmail.trim()
                        if (!UserRepository.isValidEmailFormat(clean)) {
                            dialogError = "Please enter a valid email address."
                        } else {
                            showForgotPasswordDialog = false
                            if (onSendPasswordReset != null) {
                                onSendPasswordReset(clean)
                            } else {
                                localLoading = true
                                scope.launch {
                                    val res = UserRepository(context).sendPasswordReset(clean)
                                    localLoading = false
                                    res.fold(
                                        onSuccess = {
                                            localInfoMsg = "Password reset link sent to ${clean.lowercase()}. Check your inbox."
                                        },
                                        onFailure = {
                                            localErrorMsg = it.localizedMessage ?: "Failed to send reset link."
                                        }
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("forgot_password_send_button")
                ) {
                    Text("Send Reset Link")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showForgotPasswordDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PasswordStrengthMeter(password: String) {
    val hasMinLen = password.length >= 8
    val hasLetter = password.any { it.isLetter() }
    val hasDigit = password.any { it.isDigit() }
    val score = listOf(hasMinLen, hasLetter, hasDigit).count { it }
    val progress = score / 3f
    val color = when (score) {
        3 -> StatusSuccess
        2 -> StatusWarning
        else -> MaterialTheme.colorScheme.error
    }
    val label = when {
        password.isEmpty() -> "Use 8+ characters with letters and numbers"
        score == 3 -> "Strong password"
        !hasMinLen -> "Add at least ${8 - password.length} more character(s)"
        !hasLetter -> "Include at least one letter"
        else -> "Include at least one number"
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (password.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else color
        )
    }
}

@Composable
private fun InfoBannerCard(message: String) {
    val isDark = LocalIsDarkTheme.current
    val bg = if (isDark) StatusSuccessBgDark else StatusSuccessBg
    val fg = if (isDark) StatusSuccessLight else StatusSuccess
    Surface(
        color = bg,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = fg
            )
        }
    }
}

@Composable
fun EmailVerificationScreen(
    email: String,
    isBusy: Boolean,
    errorText: String?,
    infoText: String?,
    onCheckVerified: () -> Unit,
    onResendEmail: () -> Unit,
    onSignOut: () -> Unit
) {
    var resendCooldownSec by remember { mutableIntStateOf(0) }

    LaunchedEffect(resendCooldownSec) {
        if (resendCooldownSec > 0) {
            delay(1000L)
            resendCooldownSec -= 1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MarkEmailRead,
                        contentDescription = "Email Verification Required",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(12.dp).size(32.dp)
                    )
                }

                Text(
                    text = "Verify Your Email Address",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "To protect your account and satisfy RentFlow Firestore security rules, please open the verification link sent to ${email.ifBlank { "your email" }}, then tap Continue below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!errorText.isNullOrBlank()) {
                    ErrorBannerCard(errorText)
                }

                if (!infoText.isNullOrBlank()) {
                    InfoBannerCard(infoText)
                }

                Button(
                    onClick = onCheckVerified,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("check_verification_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("I've Verified — Continue")
                    }
                }

                OutlinedButton(
                    onClick = {
                        resendCooldownSec = 60
                        onResendEmail()
                    },
                    enabled = !isBusy && resendCooldownSec == 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("resend_verification_button")
                ) {
                    Text(
                        if (resendCooldownSec > 0) "Resend Email (${resendCooldownSec}s)"
                        else "Resend Verification Email"
                    )
                }

                TextButton(
                    onClick = onSignOut,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text("Sign Out / Use Another Account")
                }
            }
        }
    }
}

@Composable
private fun FeatureBadgeCard(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RoleOnboardingScreen(
    isBusy: Boolean,
    errorText: String?,
    pendingInvites: List<RoleInvite> = emptyList(),
    onSubmit: (
        fullName: String,
        email: String,
        phoneNumber: String?,
        roles: List<UserRole>,
        activeRole: UserRole,
        organizationName: String?,
        invitesToAccept: List<RoleInvite>
    ) -> Unit,
    onSignOut: () -> Unit
) {
    val firebaseUser = Firebase.auth.currentUser
    var fullName by remember { mutableStateOf(firebaseUser?.displayName ?: "") }
    val email = firebaseUser?.email ?: ""
    var phone by remember { mutableStateOf("+254") }
    var orgName by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val activePendingInvites = remember(pendingInvites) {
        pendingInvites.filter { it.statusEnum() == InviteStatus.PENDING }
    }
    val acceptedInviteIds = remember { mutableStateListOf<String>() }
    val selectedRoles = remember { mutableStateListOf(UserRole.LANDLORD) }
    var primaryRole by remember { mutableStateOf(UserRole.LANDLORD) }
    var initializedFromInvites by remember { mutableStateOf(false) }

    LaunchedEffect(activePendingInvites) {
        if (activePendingInvites.isNotEmpty() && !initializedFromInvites) {
            initializedFromInvites = true
            acceptedInviteIds.clear()
            activePendingInvites.forEach { invite ->
                if (invite.id.isNotBlank()) acceptedInviteIds.add(invite.id)
                val invitedRole = invite.roleEnum()
                if (invitedRole != UserRole.ADMIN && invitedRole !in selectedRoles) {
                    selectedRoles.add(invitedRole)
                }
            }
            val firstInvitedRole = activePendingInvites.first().roleEnum()
            if (firstInvitedRole != UserRole.ADMIN) {
                if (selectedRoles.size > 1 && UserRole.LANDLORD in selectedRoles && UserRole.LANDLORD !in activePendingInvites.map { it.roleEnum() }) {
                    selectedRoles.remove(UserRole.LANDLORD)
                }
                primaryRole = firstInvitedRole
            }
        }
    }

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
        val isDark = LocalIsDarkTheme.current
        Surface(
            shape = MaterialTheme.shapes.large,
            color = Color.Transparent,
            border = BorderStroke(1.dp, if (isDark) DarkOutline else BrushedSilver.copy(alpha = 0.24f)),
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        if (isDark) listOf(DarkSurfaceVariant, DarkPrimaryContainer)
                        else listOf(MidnightInk, MidnightSlate)
                    ),
                    shape = MaterialTheme.shapes.large
                )
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                Surface(
                    color = MutedCobalt.copy(alpha = 0.34f),
                    border = BorderStroke(1.dp, BrushedSilver.copy(alpha = 0.32f)),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "ONBOARDING",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = BrushedSilver,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Text("Complete Your RentFlow Profile", style = MaterialTheme.typography.headlineSmall, color = DarkTextPrimary)
                Text(
                    "Your selected roles are locked after setup. New roles later require an organization invite or Administrator grant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkTextSecondary
                )
            }
        }

        if (!validationError.isNullOrBlank()) ErrorBannerCard(validationError!!)
        if (!errorText.isNullOrBlank()) ErrorBannerCard(errorText)

        if (activePendingInvites.isNotEmpty()) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth().testTag("onboarding_pending_invites_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Pending Organization Invitations Found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Accepting an invitation adds that role and joins the organization immediately upon workspace launch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    activePendingInvites.forEach { invite ->
                        val inviteChecked = invite.id in acceptedInviteIds
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    if (inviteChecked) {
                                        acceptedInviteIds.remove(invite.id)
                                    } else {
                                        acceptedInviteIds.add(invite.id)
                                        val r = invite.roleEnum()
                                        if (r != UserRole.ADMIN && r !in selectedRoles) {
                                            selectedRoles.add(r)
                                            primaryRole = r
                                        }
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = inviteChecked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked && invite.id !in acceptedInviteIds) {
                                        acceptedInviteIds.add(invite.id)
                                        val r = invite.roleEnum()
                                        if (r != UserRole.ADMIN && r !in selectedRoles) {
                                            selectedRoles.add(r)
                                            primaryRole = r
                                        }
                                    } else if (!isChecked) {
                                        acceptedInviteIds.remove(invite.id)
                                    }
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${invite.orgName} • ${invite.roleEnum().label}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Invited by ${invite.inviterName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

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
                    label = { Text("Verified Account Email") },
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
                Text("Select Your Initial Roles & Primary Dashboard", style = MaterialTheme.typography.titleMedium)
                Text(
                    "For security, you will only be able to switch between the roles chosen here unless invited to an organization or granted a role by an Admin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
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
                if (UserRole.LANDLORD in selectedRoles) {
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
                val chosenInvites = activePendingInvites.filter { it.id in acceptedInviteIds }
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
                            orgName.trim().ifBlank { null },
                            chosenInvites
                        )
                    }
                }
            },
            enabled = !isBusy,
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("submit_onboarding_button")
        ) {
            if (isBusy) CircularProgressIndicator(color = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("Launch RentFlow Workspace")
        }

        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text("Use Another Account")
        }
    }
}
