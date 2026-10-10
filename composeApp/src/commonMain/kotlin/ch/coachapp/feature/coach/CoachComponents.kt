package ch.coachapp.feature.coach

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ch.coachapp.core.fmt
import ch.coachapp.core.toDoubleOrNullLenient
import ch.coachapp.core.ui.BarAction
import ch.coachapp.core.ui.PrimaryFab
import ch.coachapp.core.ui.ScreenTopBar

/**
 * Scaffold for the top-level tabs. The outer AdaptiveScaffold already handles the
 * navigation bar insets, so this one only lets the top bar consume the status bar.
 */
@Composable
fun TabScaffold(
    title: String,
    actions: List<BarAction> = emptyList(),
    primary: BarAction? = null,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = { ScreenTopBar(title = title, actions = actions, primary = primary) },
        floatingActionButton = { PrimaryFab(primary) },
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        content = content,
    )
}

/** Integer input that keeps its own text so the field can be cleared while typing. */
@Composable
fun IntField(
    value: Int?,
    onValueChange: (Int?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
) {
    // Re-keyed on value: external changes reset the text, while a cleared field stays empty.
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.filter { it.isDigit() }.take(6)
            onValueChange(text.toIntOrNull())
        },
        label = { Text(label) },
        singleLine = true,
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** Decimal input; accepts both "." and "," as separator. */
@Composable
fun DecimalField(
    value: Double?,
    onValueChange: (Double?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
) {
    var text by remember(value) { mutableStateOf(value?.fmt(2).orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.filter { it.isDigit() || it == '.' || it == ',' }.take(8)
            onValueChange(text.toDoubleOrNullLenient())
        },
        label = { Text(label) },
        singleLine = true,
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** Searchable single-choice picker for library items. */
@Composable
fun <T> LibraryPickerDialog(
    title: String,
    items: List<T>,
    itemLabel: (T) -> String,
    itemSubtitle: (T) -> String?,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(items, query) {
        items.filter { item ->
            query.isBlank() || itemLabel(item).contains(query.trim(), ignoreCase = true) ||
                itemSubtitle(item)?.contains(query.trim(), ignoreCase = true) == true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (filtered.isEmpty()) {
                    Text(
                        "Nothing found. Add items in the Library tab.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                LazyColumn(Modifier.heightIn(max = 380.dp).padding(top = 8.dp)) {
                    items(filtered) { item ->
                        ListItem(
                            headlineContent = { Text(itemLabel(item)) },
                            supportingContent = itemSubtitle(item)?.let { { Text(it) } },
                            modifier = Modifier.clickable { onPick(item) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
