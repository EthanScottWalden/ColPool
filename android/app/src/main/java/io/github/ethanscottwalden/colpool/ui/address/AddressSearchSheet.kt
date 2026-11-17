package io.github.ethanscottwalden.colpool.ui.address

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
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
    const val RowProgress = "address_search_row_progress"
    const val Error = "address_search_error"
}

/**
 * Address search sheet. Tapping a suggestion resolves it and returns the [SelectedAddress] through [onConfirm]; nothing else can be returned.
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
    // A resolved suggestion is returned and the sheet closes.
    LaunchedEffect(state.selected) { state.selected?.let(onConfirm) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        AddressSearchContent(
            title = title,
            state = state,
            onQueryChange = viewModel::onQueryChange,
            onSuggestionClick = viewModel::onSuggestionChosen,
            onClose = onDismiss,
            notice = notice,
        )
    }
}

/** Stateless content of the sheet. Tapping a suggestion chooses it; the only button is Close. */
@Composable
fun AddressSearchContent(
    title: String,
    state: AddressSearchState,
    onQueryChange: (String) -> Unit,
    onSuggestionClick: (AddressSuggestion) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    notice: (@Composable () -> Unit)? = null,
) {
    val chooseLabel = stringResource(R.string.address_search_choose)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
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
        state.error?.let { error ->
            Text(
                stringResource(
                    if (error == AddressSearchError.SearchFailed) R.string.address_search_error_search
                    else R.string.address_search_error_resolve,
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag(AddressSearchTags.Error),
            )
        }
        if (state.searching) {
            Text(
                stringResource(R.string.address_search_searching),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else if (state.suggestions.isEmpty() && state.error == null && state.query.trim().length >= AddressSearchViewModel.MIN_CHARS) {
            Text(
                stringResource(R.string.address_search_no_results),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Column(modifier = Modifier.padding(top = 4.dp)) {
            state.suggestions.forEach { s ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = chooseLabel, role = Role.Button) { onSuggestionClick(s) }
                        .padding(vertical = 10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(s.primaryText, style = MaterialTheme.typography.titleMedium)
                        if (s.secondaryText.isNotBlank()) {
                            Text(s.secondaryText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (state.resolvingPlaceId == s.placeId) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).testTag(AddressSearchTags.RowProgress), strokeWidth = 2.dp)
                    }
                }
                HorizontalDivider()
            }
        }
        Text(
            stringResource(R.string.address_search_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp)) {
            Text(stringResource(R.string.address_search_close))
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
            onQueryChange = {}, onSuggestionClick = {}, onClose = {},
        )
    }
}
