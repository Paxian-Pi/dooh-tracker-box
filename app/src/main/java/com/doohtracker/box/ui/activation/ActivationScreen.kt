package com.doohtracker.box.ui.activation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.doohtracker.box.ui.dashboard.DashboardViewModel
import com.doohtracker.box.ui.theme.StatusCyan
import com.doohtracker.box.ui.theme.StatusGreen
import com.doohtracker.box.ui.theme.StatusRed

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ActivationScreen(viewModel: ActivationViewModel, clearHub: DashboardViewModel) {
    val activationCode by viewModel.activationCode.collectAsState()
    val username by viewModel.username.collectAsState()
    val installationNotes by viewModel.installationNotes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val stagedIdentities by viewModel.stagedIdentities.collectAsState()

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val addBtnFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    // Auto-focus the Add button after a successful validation
    LaunchedEffect(stagedIdentities.size) {
        if (stagedIdentities.isNotEmpty()) {
            addBtnFocusRequester.requestFocus()
        }
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side: Entry Form
            Column(
                modifier = Modifier
                    .width(450.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Device Activation Hub",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    modifier = Modifier.padding(bottom = 16.dp),
                    text = "Add all client Site IDs that will run on this billboard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                TvInputField(
                    value = activationCode,
                    onValueChange = { viewModel.onActivationCodeChange(it) },
                    label = "Site Activation Code",
                    placeholder = "e.g. #Retin-1234",
                    imeAction = ImeAction.Next
                )

                TvInputField(
                    value = username,
                    onValueChange = { viewModel.onUsernameChange(it) },
                    label = "Client Username",
                    placeholder = "Enter username",
                    imeAction = if (stagedIdentities.isEmpty()) ImeAction.Next else ImeAction.Done,
                    onDone = {
                        if (stagedIdentities.isNotEmpty()) viewModel.validateAndAddIdentity()
                    }
                )

                if (stagedIdentities.isEmpty()) {
                    TvInputField(
                        value = installationNotes,
                        onValueChange = { viewModel.onInstallationNotesChange(it) },
                        label = "Global Location Details",
                        placeholder = "e.g. LOC_AIRPORT_LAGOS",
                        imeAction = ImeAction.Done,
                        onDone = { viewModel.validateAndAddIdentity() },
                        modifier = Modifier.height(80.dp)
                    )
                }

                if (errorMessage != null) {
                    Text(text = errorMessage!!, color = StatusRed, style = MaterialTheme.typography.bodySmall)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { viewModel.validateAndAddIdentity() },
                        enabled = !isLoading,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(addBtnFocusRequester)
                            .clickable { if (!isLoading) viewModel.validateAndAddIdentity() }
                    ) {
                        Text(
                            if (isLoading) "Validating..." else "Add Site ID",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Button(
                        onClick = { viewModel.finishActivation() },
                        enabled = stagedIdentities.isNotEmpty() && !isLoading,
                        colors = ButtonDefaults.colors(containerColor = Color(0xFF3DAB76), contentColor = Color.Black),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { if (stagedIdentities.isNotEmpty() && !isLoading) viewModel.finishActivation() }
                    ) {
                        Text("Start Hub", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // Right Side: Staged Identities List
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Activated Client Hub (${stagedIdentities.size})",
                    style = MaterialTheme.typography.headlineSmall,
                    color = StatusCyan
                )

                if (stagedIdentities.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { clearHub.clearData() },
                        enabled = stagedIdentities.isNotEmpty() && !isLoading,
                        colors = ButtonDefaults.colors(containerColor = Color(0xFF3DAB76), contentColor = Color.Black),
                        modifier = Modifier
                            .clickable { clearHub.clearData() }
                    ) {
                        Text("Clear Hub", textAlign = TextAlign.Center)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = MaterialTheme.shapes.medium,
                    colors = SurfaceDefaults.colors(containerColor = Color(0xFF1E2136))
                ) {
                    if (stagedIdentities.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No Site IDs added yet", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(stagedIdentities) { identity ->
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    colors = SurfaceDefaults.colors(containerColor = Color(0xFF2A2D4A))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(text = identity.siteId, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text(text = "User: ${identity.username}", fontSize = 12.sp, color = Color.Gray)
                                        }
                                        Text(text = "✓ Validated", color = StatusGreen, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Default,
    onDone: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            onClick = {
                focusRequester.requestFocus()
                keyboardController?.show()
            },
            shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF1E2136),
                contentColor = Color.White,
                focusedContainerColor = Color(0xFF2A2D4A)
            ),
            modifier = Modifier
                .onFocusChanged {
                    if (it.isFocused) {
                        focusRequester.requestFocus()
                    }
                }
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                cursorBrush = SolidColor(Color.White),
                singleLine = singleLine,
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    onDone = {
                        keyboardController?.hide()
                        onDone()
                    }
                ),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}
