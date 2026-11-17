package io.github.ethanscottwalden.colpool.ui.address

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ethanscottwalden.colpool.R
import io.github.ethanscottwalden.colpool.data.places.AddressSuggestion
import io.github.ethanscottwalden.colpool.data.places.SelectedAddress
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme

object AddressSearchTags {
    const val Field = "address_search_field"
    const val Confirm = "address_search_confirm"
    const val Error = "address_search_error"
}

/**
 * Address search sheet. Returns a [SelectedAddress] through [onConfirm]; nothing else can be submitted.
 * Use a fresh [viewModel] per address entry. [title] is supplied by the caller (for example
 * "Starting address"). [notice] is an optional slot above the field, e.g. the "recommend public
 * places" note for pickup spots and stops; nothing is shown by default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressSearchSheet(
    title: String,
    viewModel: AddressSearchViewModel,
    onConfirm: (SelectedAddress) -> Unit,
    onDismiss: () -> Unit,
    notice: (@Composable () -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        AddressSearchContent(
            title = title,
            state = state,
            onQueryChange = viewModel::onQueryChange,
            onSuggestionClick = viewModel::onSuggestionChosen,
            onConfirm = { state.selected?.let(onConfirm) },
            onClose = onDismiss,
            notice = notice,
        )
    }
}

/** Stateless content of the sheet. */
@Composable
fun AddressSearchContent(
    title: String,
    state: AddressSearchState,
    onQueryChange: (String) -> Unit,
    onSuggestionClick: (AddressSuggestion) -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    notice: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        notice?.let {
            Column(modifier = Modifier.padding(top = 8.dp)) { it() }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.address_search_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag(AddressSearchTags.Field),
        )
        when {
            state.error != null -> Text(
                stringResource(
                    if (state.error == AddressSearchError.SearchFailed) R.string.address_search_error_search
                    else R.string.address_search_error_resolve,
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp).testTag(AddressSearchTags.Error),
            )
            state.searching || state.resolving -> Text(
                stringResource(R.string.address_search_searching),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            state.suggestions.isNotEmpty() -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp).padding(top = 4.dp)) {
                items(state.suggestions, key = { it.placeId }) { s ->
                    Column(modifier = Modifier.fillMaxWidth().clickable { onSuggestionClick(s) }.padding(vertical = 10.dp)) {
                        Text(s.primaryText, style = MaterialTheme.typography.titleMedium)
                        if (s.secondaryText.isNotBlank()) {
                            Text(s.secondaryText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                }
            }
            state.selected == null && state.query.trim().length >= AddressSearchViewModel.MIN_CHARS -> Text(
                stringResource(R.string.address_search_no_results),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Text(
            stringResource(R.string.address_search_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
        ) {
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.address_search_close))
            }
            Button(onClick = onConfirm, enabled = state.canConfirm, modifier = Modifier.weight(1f).testTag(AddressSearchTags.Confirm)) {
                Text(stringResource(R.string.address_search_confirm))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AddressSearchPreview() {
    ColPoolTheme {
        AddressSearchContent(
            title = "Starting address",
            state = AddressSearchState(
                query = "100 Example",
                suggestions = listOf(AddressSuggestion("p1", "100 Example Street", "Exampleville, CA")),
            ),
            onQueryChange = {}, onSuggestionClick = {}, onConfirm = {}, onClose = {},
        )
    }
}
