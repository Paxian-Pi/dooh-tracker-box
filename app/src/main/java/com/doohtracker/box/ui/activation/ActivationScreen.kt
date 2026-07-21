package com.doohtracker.box.ui.activation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.doohtracker.box.ui.theme.StatusRed

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ActivationScreen(viewModel: ActivationViewModel) {
    val activationCode by viewModel.activationCode.collectAsState()
    val username by viewModel.username.collectAsState()
    val installationNotes by viewModel.installationNotes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Force keyboard dismissal when loading starts
    LaunchedEffect(isLoading) {
        if (isLoading) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(500.dp)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Device Activation",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            TvInputField(
                value = activationCode,
                onValueChange = { viewModel.onActivationCodeChange(it) },
                label = "Activation Code",
                placeholder = "Enter the site activation code",
                imeAction = ImeAction.Next
            )

            TvInputField(
                value = username,
                onValueChange = { viewModel.onUsernameChange(it) },
                label = "Username",
                placeholder = "Enter your username",
                imeAction = ImeAction.Next
            )

            TvInputField(
                value = installationNotes,
                onValueChange = { viewModel.onInstallationNotesChange(it) },
                label = "Location Details",
                placeholder = "e.g. LOC_MMA_AIRPORT_LAGOS",
                singleLine = false,
                imeAction = ImeAction.Done,
                modifier = Modifier.height(100.dp)
            ) {
                if (!isLoading) {
                    keyboardController?.hide()
                    focusManager.clearFocus(force = true)
                    viewModel.activateDevice()
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = StatusRed,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = { 
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    viewModel.activateDevice() 
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                val buttonText = if (isLoading) "Activating..." else "Activate Device"
                Text(
                    text = buttonText,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
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
    var isFocused by remember { mutableStateOf(value = false) }
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
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF1E2136),
                contentColor = Color.White,
                focusedContainerColor = Color(0xFF2A2D4A)
            ),
            modifier = Modifier
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (it.isFocused) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
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
                keyboardActions = KeyboardActions(onDone = { onDone() }),
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
