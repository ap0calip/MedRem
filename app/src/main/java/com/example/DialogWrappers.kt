package com.example

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ThemedDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        ProvideTextScale {
            content()
        }
    }
}

@Composable
fun ThemedAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false)
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { ProvideTextScale { confirmButton() } },
        modifier = modifier
            .fillMaxWidth(if (isLandscape) 0.55f else 0.92f)
            .widthIn(max = 420.dp),
        dismissButton = dismissButton?.let { { ProvideTextScale { it() } } },
        title = title?.let { { ProvideTextScale { it() } } },
        text = text?.let { { ProvideTextScale { it() } } },
        properties = properties
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemedDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { ProvideTextScale { confirmButton() } },
        modifier = modifier,
        dismissButton = dismissButton?.let { { ProvideTextScale { it() } } }
    ) {
        ProvideTextScale {
            content()
        }
    }
}
