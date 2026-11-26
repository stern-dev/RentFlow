package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.finance.KenyanPaymentValidator
import com.example.domain.finance.MpesaSmsVerificationResult
import com.example.domain.model.PaymentChannelType
import com.example.domain.model.PaymentRoleScope
import com.example.domain.model.SavedPaymentMethod
import com.example.domain.model.UserRole
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.PillTone
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorBg
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import com.example.ui.viewmodel.UiState

data class PropertyMpesaSuggestion(
    val title: String,
    val channelType: PaymentChannelType,
    val paybillNumber: String? = null,
    val accountReference: String? = null,
    val tillNumber: String? = null
)

private enum class ChannelFilter(val label: String, val type: PaymentChannelType?) {
    ALL("All", null),
    PAYBILL("Paybill", PaymentChannelType.MPESA_PAYBILL),
    TILL("Till", PaymentChannelType.MPESA_TILL),
    STK("STK Phone", PaymentChannelType.MPESA_STK),
    BANK("Bank", PaymentChannelType.BANK_TRANSFER),
    CASH("Cash", PaymentChannelType.CASH)
}

@Composable
fun PaymentMethodSelectionScreen(
    methodsState: UiState<List<SavedPaymentMethod>>,
    selectedMethodId: String?,
    userRole: UserRole,
    propertySuggestions: List<PropertyMpesaSuggestion> = emptyList(),
    isBusy: Boolean = false,
    onBack: () -> Unit,
    onSelectMethod: (SavedPaymentMethod) -> Unit,
    onConfirmUseMethod: (SavedPaymentMethod) -> Unit,
    onSaveMethod: (
        existingMethodId: String?,
        label: String,
        channelType: PaymentChannelType,
        roleScope: PaymentRoleScope,
        paybillNumber: String?,
        accountReference: String?,
        tillNumber: String?,
        phoneNumber: String?,
        bankName: String?,
        bankAccountNumber: String?,
        defaultMethod: Boolean,
        smsVerified: Boolean,
        smsSampleRef: String?,
        onDone: () -> Unit
    ) -> Unit,
    onSetDefaultMethod: (String) -> Unit,
    onDeleteMethod: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val allMethods = (methodsState as? UiState.Success)?.data ?: emptyList()
    val effectiveSelected = remember(allMethods, selectedMethodId) {
        allMethods.firstOrNull { it.id == selectedMethodId }
            ?: allMethods.firstOrNull { it.defaultMethod }
            ?: allMethods.firstOrNull()
    }

    var activeFilter by remember { mutableStateOf(ChannelFilter.ALL) }
    var showAddDialog by remember { mutableStateOf(false) }
    var methodToEdit by remember { mutableStateOf<SavedPaymentMethod?>(null) }
    var prefillSuggestion by remember { mutableStateOf<PropertyMpesaSuggestion?>(null) }
    var methodToDelete by remember { mutableStateOf<SavedPaymentMethod?>(null) }

    val roleDisplayScope = remember(userRole) {
        when (userRole) {
            UserRole.LANDLORD, UserRole.PROPERTY_MANAGER -> PaymentRoleScope.LANDLORD
            UserRole.TENANT -> PaymentRoleScope.TENANT
            else -> PaymentRoleScope.ALL
        }
    }

    val roleLabel = if (userRole == UserRole.LANDLORD || userRole == UserRole.PROPERTY_MANAGER) {
        "Collection & Rent Payment Methods"
    } else {
        "Select Rent Payment Method"
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    methodToEdit = null
                    prefillSuggestion = null
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.testTag("add_payment_method_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Payment Method")
            }
        },
        bottomBar = {
            if (effectiveSelected != null) {
                Surface(
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Selected: ${effectiveSelected.label}",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = effectiveSelected.summarySubtitle(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (effectiveSelected.smsVerified) {
                                StatusPill("SMS Verified", PillTone.INFO)
                            }
                        }
                        Button(
                            onClick = { onConfirmUseMethod(effectiveSelected) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("use_selected_payment_method_button")
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Use ${effectiveSelected.channelTypeEnum().label}")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("payment_method_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(roleLabel, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Kenya KES • Safaricom Paybill, Till, STK, Bank & Cash",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            methodToEdit = null
                            prefillSuggestion = null
                            showAddDialog = true
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("header_add_payment_method_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("payment_methods_list")
            ) {
                if (propertySuggestions.isNotEmpty()) {
                    item {
                        SectionHeader("Property M-Pesa Channels")
                    }
                    itemsIndexed(propertySuggestions) { idx, suggestion ->
                        val isAlreadySaved = allMethods.any { saved ->
                            when (suggestion.channelType) {
                                PaymentChannelType.MPESA_PAYBILL ->
                                    saved.channelTypeEnum() == PaymentChannelType.MPESA_PAYBILL &&
                                        saved.paybillNumber == suggestion.paybillNumber &&
                                        saved.accountReference == suggestion.accountReference
                                PaymentChannelType.MPESA_TILL ->
                                    saved.channelTypeEnum() == PaymentChannelType.MPESA_TILL &&
                                        saved.tillNumber == suggestion.tillNumber
                                else -> false
                            }
                        }
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(suggestion.title, style = MaterialTheme.typography.titleSmall)
                                    val detail = when (suggestion.channelType) {
                                        PaymentChannelType.MPESA_PAYBILL ->
                                            "Paybill ${suggestion.paybillNumber.orEmpty()} • Acct ${suggestion.accountReference.orEmpty()}"
                                        PaymentChannelType.MPESA_TILL ->
                                            "Buy Goods Till ${suggestion.tillNumber.orEmpty()}"
                                        else -> suggestion.channelType.label
                                    }
                                    Text(
                                        text = detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (isAlreadySaved) {
                                            val matching = allMethods.firstOrNull { saved ->
                                                saved.paybillNumber == suggestion.paybillNumber &&
                                                    saved.tillNumber == suggestion.tillNumber
                                            }
                                            matching?.let {
                                                onSelectMethod(it)
                                                onConfirmUseMethod(it)
                                            }
                                        } else {
                                            prefillSuggestion = suggestion
                                            methodToEdit = null
                                            showAddDialog = true
                                        }
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("save_suggested_channel_$idx")
                                ) {
                                    Text(if (isAlreadySaved) "Use Saved" else "Save & Validate")
                                }
                            }
                        }
                    }
                }

                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payment_channel_filters")
                    ) {
                        items(ChannelFilter.entries) { filter ->
                            val count = if (filter.type == null) {
                                allMethods.size
                            } else {
                                allMethods.count { it.channelTypeEnum() == filter.type }
                            }
                            FilterChip(
                                selected = activeFilter == filter,
                                onClick = { activeFilter = filter },
                                label = { Text("${filter.label} ($count)") },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("filter_channel_${filter.name.lowercase()}")
                            )
                        }
                    }
                }

                when (methodsState) {
                    is UiState.Loading -> item { LoadingStateBox("Loading saved Kenya payment methods...") }
                    is UiState.Error -> item { ErrorBannerCard(methodsState.message) }
                    is UiState.Success -> {
                        val filtered = if (activeFilter.type == null) {
                            allMethods
                        } else {
                            allMethods.filter { it.channelTypeEnum() == activeFilter.type }
                        }
                        if (allMethods.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = "No Saved Payment Methods",
                                    message = "Add an M-Pesa Paybill (5–7 digits + Account Reference), Buy Goods Till, M-Pesa Express phone, Bank Transfer, or Cash channel.",
                                    ctaLabel = "Add Payment Method",
                                    onCtaClick = {
                                        prefillSuggestion = null
                                        methodToEdit = null
                                        showAddDialog = true
                                    }
                                )
                            }
                        } else if (filtered.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = "No ${activeFilter.label} Methods",
                                    message = "You have no saved payment methods in this category yet.",
                                    ctaLabel = "Show All",
                                    onCtaClick = { activeFilter = ChannelFilter.ALL },
                                    showIllustration = false
                                )
                            }
                        } else {
                            items(filtered, key = { it.id }) { method ->
                                val isSelected = effectiveSelected?.id == method.id
                                SavedPaymentMethodCard(
                                    method = method,
                                    isSelected = isSelected,
                                    isBusy = isBusy,
                                    onSelect = { onSelectMethod(method) },
                                    onEdit = {
                                        prefillSuggestion = null
                                        methodToEdit = method
                                        showAddDialog = true
                                    },
                                    onSetDefault = { onSetDefaultMethod(method.id) },
                                    onDelete = { methodToDelete = method }
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (showAddDialog) {
        AddEditPaymentMethodDialog(
            existingMethod = methodToEdit,
            prefillSuggestion = prefillSuggestion,
            defaultScope = roleDisplayScope,
            isBusy = isBusy,
            onDismiss = {
                showAddDialog = false
                methodToEdit = null
                prefillSuggestion = null
            },
            onSave = { label, channel, scope, paybill, acctRef, till, phone, bankName, bankAcct, makeDefault, smsVerified, smsSampleRef ->
                onSaveMethod(
                    methodToEdit?.id,
                    label,
                    channel,
                    scope,
                    paybill,
                    acctRef,
                    till,
                    phone,
                    bankName,
                    bankAcct,
                    makeDefault,
                    smsVerified,
                    smsSampleRef
                ) {
                    showAddDialog = false
                    methodToEdit = null
                    prefillSuggestion = null
                }
            }
        )
    }

    methodToDelete?.let { target ->
        ConfirmationDialog(
            title = "Remove Payment Method?",
            message = "Delete '${target.label}' (${target.summarySubtitle()}) from your saved RentFlow payment methods?",
            confirmLabel = "Delete",
            isDestructive = true,
            onConfirm = {
                onDeleteMethod(target.id)
                methodToDelete = null
            },
            onDismiss = { methodToDelete = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedPaymentMethodCard(
    method: SavedPaymentMethod,
    isSelected: Boolean,
    isBusy: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    val channel = method.channelTypeEnum()
    val icon = channelIcon(channel)
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("saved_payment_method_card_${method.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Selected" else "Select ${method.label}",
                    tint = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = channel.label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = method.label,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${channel.label} • ${method.summarySubtitle()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (method.defaultMethod) {
                    StatusPill("Default", PillTone.SUCCESS)
                }
                if (method.smsVerified) {
                    StatusPill(
                        label = method.smsSampleRef?.let { "SMS Verified ($it)" } ?: "SMS Verified",
                        tone = PillTone.INFO
                    )
                }
                StatusPill(method.roleScopeEnum().label, PillTone.NEUTRAL)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!method.defaultMethod) {
                    OutlinedButton(
                        onClick = onSetDefault,
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("set_default_method_${method.id}")
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Set Default", maxLines = 1)
                    }
                }
                OutlinedButton(
                    onClick = onEdit,
                    enabled = !isBusy,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("edit_method_${method.id}")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${method.label}", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }
                IconButton(
                    onClick = onDelete,
                    enabled = !isBusy,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("delete_method_${method.id}")
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete ${method.label}",
                        tint = StatusError
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditPaymentMethodDialog(
    existingMethod: SavedPaymentMethod? = null,
    prefillSuggestion: PropertyMpesaSuggestion? = null,
    defaultScope: PaymentRoleScope = PaymentRoleScope.ALL,
    isBusy: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (
        label: String,
        channelType: PaymentChannelType,
        roleScope: PaymentRoleScope,
        paybillNumber: String?,
        accountReference: String?,
        tillNumber: String?,
        phoneNumber: String?,
        bankName: String?,
        bankAccountNumber: String?,
        defaultMethod: Boolean,
        smsVerified: Boolean,
        smsSampleRef: String?
    ) -> Unit
) {
    val initialChannel = existingMethod?.channelTypeEnum()
        ?: prefillSuggestion?.channelType
        ?: PaymentChannelType.MPESA_PAYBILL

    var channelType by remember(existingMethod, prefillSuggestion) { mutableStateOf(initialChannel) }
    var label by remember(existingMethod, prefillSuggestion) {
        mutableStateOf(
            existingMethod?.label
                ?: prefillSuggestion?.title
                ?: ""
        )
    }
    var paybillNumber by remember(existingMethod, prefillSuggestion) {
        mutableStateOf(existingMethod?.paybillNumber ?: prefillSuggestion?.paybillNumber.orEmpty())
    }
    var accountReference by remember(existingMethod, prefillSuggestion) {
        mutableStateOf(existingMethod?.accountReference ?: prefillSuggestion?.accountReference.orEmpty())
    }
    var tillNumber by remember(existingMethod, prefillSuggestion) {
        mutableStateOf(existingMethod?.tillNumber ?: prefillSuggestion?.tillNumber.orEmpty())
    }
    var phoneNumber by remember(existingMethod) {
        mutableStateOf(existingMethod?.phoneNumber.orEmpty())
    }
    var bankName by remember(existingMethod) {
        mutableStateOf(existingMethod?.bankName.orEmpty())
    }
    var bankAccountNumber by remember(existingMethod) {
        mutableStateOf(existingMethod?.bankAccountNumber.orEmpty())
    }
    var makeDefault by remember(existingMethod) {
        mutableStateOf(existingMethod?.defaultMethod ?: false)
    }
    var roleScope by remember(existingMethod, defaultScope) {
        mutableStateOf(existingMethod?.roleScopeEnum() ?: defaultScope)
    }

    var showSmsSection by remember { mutableStateOf(existingMethod?.smsVerified == true) }
    var rawSmsInput by remember { mutableStateOf("") }
    var smsResult by remember(existingMethod) {
        mutableStateOf<MpesaSmsVerificationResult>(
            if (existingMethod?.smsVerified == true) {
                MpesaSmsVerificationResult.Verified(
                    transactionCode = existingMethod.smsSampleRef ?: "VERIFIED",
                    amountCents = 0L,
                    recipientOrAccount = null,
                    detailMessage = "Previously verified via confirmation SMS (${existingMethod.smsSampleRef ?: "OK"})."
                )
            } else {
                MpesaSmsVerificationResult.NotProvided
            }
        )
    }
    var showValidationErrors by remember { mutableStateOf(false) }

    val validation = remember(
        channelType,
        label,
        paybillNumber,
        accountReference,
        tillNumber,
        phoneNumber,
        bankName,
        bankAccountNumber
    ) {
        KenyanPaymentValidator.validatePaymentMethod(
            channelType = channelType,
            label = label,
            paybillNumber = paybillNumber,
            accountReference = accountReference,
            tillNumber = tillNumber,
            phoneNumber = phoneNumber,
            bankName = bankName,
            bankAccountNumber = bankAccountNumber
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existingMethod == null) "Add Kenya Payment Method" else "Edit Payment Method")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text("Payment Channel", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaymentChannelType.entries.forEach { ch ->
                        FilterChip(
                            selected = channelType == ch,
                            onClick = {
                                channelType = ch
                                showValidationErrors = false
                                smsResult = MpesaSmsVerificationResult.NotProvided
                            },
                            label = { Text(ch.label) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("channel_chip_${ch.name.lowercase()}")
                        )
                    }
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Method Nickname (e.g. Kilimani Rent Paybill)") },
                    isError = showValidationErrors && validation.labelError != null,
                    supportingText = if (showValidationErrors && validation.labelError != null) {
                        { Text(validation.labelError) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pm_label_input")
                )

                AnimatedVisibility(visible = channelType == PaymentChannelType.MPESA_PAYBILL) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = paybillNumber,
                            onValueChange = { paybillNumber = it.filter { ch -> ch.isDigit() }.take(7) },
                            label = { Text("Safaricom Paybill Number (5–7 digits)") },
                            placeholder = { Text("e.g. 522522 or 400200") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = showValidationErrors && validation.paybillError != null,
                            supportingText = {
                                if (showValidationErrors && validation.paybillError != null) {
                                    Text(validation.paybillError)
                                } else {
                                    Text("Must be 5–7 digits, cannot start with 0.")
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pm_paybill_input")
                        )
                        OutlinedTextField(
                            value = accountReference,
                            onValueChange = { accountReference = it.take(24) },
                            label = { Text("Paybill Account Reference (Required)") },
                            placeholder = { Text("e.g. UNIT-A102 or HSE-04") },
                            isError = showValidationErrors && validation.accountReferenceError != null,
                            supportingText = {
                                if (showValidationErrors && validation.accountReferenceError != null) {
                                    Text(validation.accountReferenceError)
                                } else {
                                    Text("Shown during M-Pesa Paybill checkout (1–24 chars).")
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pm_account_ref_input")
                        )
                    }
                }

                AnimatedVisibility(visible = channelType == PaymentChannelType.MPESA_TILL) {
                    OutlinedTextField(
                        value = tillNumber,
                        onValueChange = { tillNumber = it.filter { ch -> ch.isDigit() }.take(7) },
                        label = { Text("Buy Goods Till Number (5–7 digits)") },
                        placeholder = { Text("e.g. 5643210") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = showValidationErrors && validation.tillError != null,
                        supportingText = {
                            if (showValidationErrors && validation.tillError != null) {
                                Text(validation.tillError)
                            } else {
                                Text("Safaricom Lipa na M-Pesa Till (5–7 digits, non-zero start).")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pm_till_input")
                    )
                }

                AnimatedVisibility(visible = channelType == PaymentChannelType.MPESA_STK) {
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it.take(20) },
                        label = { Text("M-Pesa Mobile Number (+2547... or 07...)") },
                        placeholder = { Text("+254712345678") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = showValidationErrors && validation.phoneError != null,
                        supportingText = if (showValidationErrors && validation.phoneError != null) {
                            { Text(validation.phoneError) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pm_phone_input")
                    )
                }

                AnimatedVisibility(visible = channelType == PaymentChannelType.BANK_TRANSFER) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = bankName,
                            onValueChange = { bankName = it.take(60) },
                            label = { Text("Kenyan Bank Name") },
                            placeholder = { Text("e.g. KCB Kenya, Equity Bank, NCBA") },
                            isError = showValidationErrors && validation.bankNameError != null,
                            supportingText = if (showValidationErrors && validation.bankNameError != null) {
                                { Text(validation.bankNameError) }
                            } else null,
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pm_bank_name_input")
                        )
                        OutlinedTextField(
                            value = bankAccountNumber,
                            onValueChange = { bankAccountNumber = it.take(30) },
                            label = { Text("Bank Account Number") },
                            isError = showValidationErrors && validation.bankAccountError != null,
                            supportingText = if (showValidationErrors && validation.bankAccountError != null) {
                                { Text(validation.bankAccountError) }
                            } else null,
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pm_bank_account_input")
                        )
                    }
                }

                AnimatedVisibility(visible = channelType == PaymentChannelType.CASH) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Tracks cash rent payments and receipts recorded by landlord or property management.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Set as default method", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Pre-filled automatically when paying or recording rent",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = makeDefault,
                        onCheckedChange = { makeDefault = it },
                        modifier = Modifier.testTag("pm_default_switch")
                    )
                }

                if (channelType == PaymentChannelType.MPESA_PAYBILL ||
                    channelType == PaymentChannelType.MPESA_TILL ||
                    channelType == PaymentChannelType.MPESA_STK
                ) {
                    Card(
                        shape = MaterialTheme.shapes.small,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable { showSmsSection = !showSmsSection }
                                    .testTag("pm_toggle_sms_check")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Sms, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                    Column {
                                        Text("Test with M-Pesa SMS (Optional)", style = MaterialTheme.typography.labelLarge)
                                        Text(
                                            "Zero SMS permission — paste a confirmation message",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (showSmsSection) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (showSmsSection) "Collapse SMS check" else "Expand SMS check"
                                )
                            }

                            AnimatedVisibility(visible = showSmsSection) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = rawSmsInput,
                                        onValueChange = { rawSmsInput = it },
                                        label = { Text("Paste M-Pesa Confirmation SMS") },
                                        placeholder = { Text("e.g. UAF29KL30M Confirmed. Ksh25,000.00 paid to 522522...") },
                                        maxLines = 3,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("pm_sms_test_input")
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            smsResult = KenyanPaymentValidator.verifyWithMpesaSms(
                                                rawSms = rawSmsInput,
                                                channelType = channelType,
                                                paybillNumber = paybillNumber,
                                                tillNumber = tillNumber,
                                                accountReference = accountReference,
                                                phoneNumber = phoneNumber
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("pm_run_sms_check_button")
                                    ) {
                                        Text("Run M-Pesa SMS Check")
                                    }
                                    when (val res = smsResult) {
                                        MpesaSmsVerificationResult.NotProvided -> Unit
                                        is MpesaSmsVerificationResult.InvalidFormat -> {
                                            Surface(
                                                color = StatusErrorBg,
                                                shape = MaterialTheme.shapes.small,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("pm_sms_error_banner")
                                            ) {
                                                Text(
                                                    text = res.message,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = StatusError,
                                                    modifier = Modifier.padding(10.dp)
                                                )
                                            }
                                        }
                                        is MpesaSmsVerificationResult.MismatchWarning -> {
                                            Surface(
                                                color = StatusWarningBg,
                                                shape = MaterialTheme.shapes.small,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("pm_sms_warning_banner")
                                            ) {
                                                Text(
                                                    text = res.reason,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = StatusWarning,
                                                    modifier = Modifier.padding(10.dp)
                                                )
                                            }
                                        }
                                        is MpesaSmsVerificationResult.Verified -> {
                                            Surface(
                                                color = StatusSuccessBg,
                                                shape = MaterialTheme.shapes.small,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("pm_sms_verified_banner")
                                            ) {
                                                Text(
                                                    text = res.detailMessage,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = StatusSuccess,
                                                    modifier = Modifier.padding(10.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!validation.isValid) {
                        showValidationErrors = true
                    } else {
                        val verifiedResult = smsResult as? MpesaSmsVerificationResult.Verified
                        onSave(
                            label.trim(),
                            channelType,
                            roleScope,
                            paybillNumber.takeIf { channelType == PaymentChannelType.MPESA_PAYBILL },
                            accountReference.takeIf { channelType == PaymentChannelType.MPESA_PAYBILL },
                            tillNumber.takeIf { channelType == PaymentChannelType.MPESA_TILL },
                            phoneNumber.takeIf { channelType == PaymentChannelType.MPESA_STK },
                            bankName.takeIf { channelType == PaymentChannelType.BANK_TRANSFER },
                            bankAccountNumber.takeIf { channelType == PaymentChannelType.BANK_TRANSFER },
                            makeDefault,
                            verifiedResult != null,
                            verifiedResult?.transactionCode ?: existingMethod?.smsSampleRef
                        )
                    }
                },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("pm_save_button")
            ) {
                Text(if (existingMethod == null) "Save Method" else "Update Method")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

private fun channelIcon(channelType: PaymentChannelType): ImageVector = when (channelType) {
    PaymentChannelType.MPESA_PAYBILL -> Icons.Filled.AccountBalanceWallet
    PaymentChannelType.MPESA_TILL -> Icons.Filled.Store
    PaymentChannelType.MPESA_STK -> Icons.Filled.PhoneAndroid
    PaymentChannelType.BANK_TRANSFER -> Icons.Filled.AccountBalance
    PaymentChannelType.CASH -> Icons.Filled.Payments
}
