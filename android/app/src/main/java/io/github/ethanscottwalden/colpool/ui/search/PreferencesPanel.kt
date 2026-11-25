package io.github.ethanscottwalden.colpool.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ethanscottwalden.colpool.R
import io.github.ethanscottwalden.colpool.data.model.OptionItem

/** The "Host preferences (N)" row, and the panel under it when open. Preferences never filter or reorder results (RC-20). */
@Composable
fun PreferencesRow(state: SearchUiState, actions: SearchActions) {
    val n = state.prefs.count
    val cd = stringResource(if (state.panelOpen) R.string.prefs_row_close_cd else R.string.prefs_row_open_cd, n)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = if (state.panelOpen) actions.onClosePanel else actions.onOpenPanel,
            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp).testTag(SearchTags.PrefsRow).semantics { contentDescription = cd; selected = state.panelOpen },
        ) { Text(stringResource(R.string.prefs_row, n), fontWeight = FontWeight.Bold) }
        if (state.panelOpen) PreferencesPanel(state, actions)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreferencesPanel(state: SearchUiState, actions: SearchActions) {
    val o = state.options
    Surface(Modifier.fillMaxWidth().testTag(SearchTags.PrefsPanel), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.prefs_intro), style = MaterialTheme.typography.bodyMedium)
            if (state.optionsFailed) {
                Text(stringResource(R.string.prefs_options_failed), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = actions.onRetryOptions, modifier = Modifier.sizeIn(minHeight = 48.dp).testTag(SearchTags.PrefsRetry)) { Text(stringResource(R.string.search_retry)) }
            } else if (o.majors.isEmpty() && o.interests.isEmpty()) {
                Text(stringResource(R.string.prefs_options_loading), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
            } else {
                Section(R.string.prefs_interests) { ChipRow(o.interests, state.prefs.interests::contains, actions.onInterest, SearchTags::interest) }
                Section(R.string.prefs_traits) { ChipRow(o.traits, state.prefs.traits::contains, actions.onTrait, SearchTags::trait) }
                Section(R.string.prefs_major) { MajorPicker(o.majors, state.prefs.major, actions.onMajor) }
                Section(R.string.prefs_year) { ChipRow(o.years, { it == state.prefs.year }, actions.onYear, SearchTags::year) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = actions.onClearPrefs, enabled = !state.prefs.isEmpty, modifier = Modifier.sizeIn(minHeight = 48.dp).testTag(SearchTags.PrefsClear)) { Text(stringResource(R.string.prefs_clear)) }
                Button(onClick = actions.onClosePanel, modifier = Modifier.sizeIn(minHeight = 48.dp).testTag(SearchTags.PrefsDone)) { Text(stringResource(R.string.prefs_done)) }
            }
        }
    }
}

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(items: List<OptionItem>, isOn: (String) -> Boolean, onToggle: (String) -> Unit, tag: (String) -> String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { PrefChip(it.label, isOn(it.id), { onToggle(it.id) }, Modifier.testTag(tag(it.id))) }
    }
}

/** A checkbox-style chip: at least 48dp tall, announced as a checkbox with its checked state. */
@Composable
private fun PrefChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        modifier = modifier.heightIn(min = 48.dp).semantics { role = Role.Checkbox },
    )
}

/**
 * A filter box for the major (one can be chosen). The match list stays hidden while the box is empty. A chosen major shows
 * as a card with a Change button. Typing shows at most [MAJOR_MATCHES_SHOWN] matches in a bordered list, with a
 * "Keep typing to narrow (N more)" line when there are more, so it never needs hidden scrolling.
 */
@Composable
private fun MajorPicker(majors: List<OptionItem>, chosen: String?, onPick: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("") }
    val query = filter.trim()
    val chosenLabel = majors.firstOrNull { it.id == chosen }?.label
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (chosen != null) {
            Surface(
                shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.prefs_major_chosen, chosenLabel ?: chosen), Modifier.weight(1f).testTag(SearchTags.PrefsMajorChosen), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { onPick(chosen) }, Modifier.sizeIn(minHeight = 48.dp).testTag(SearchTags.PrefsMajorClear)) { Text(stringResource(R.string.prefs_major_clear)) }
                }
            }
        }
        OutlinedTextField(
            value = filter, onValueChange = { filter = it }, singleLine = true,
            label = { Text(stringResource(if (chosen != null) R.string.prefs_major_filter_change else R.string.prefs_major_filter)) },
            modifier = Modifier.fillMaxWidth().testTag(SearchTags.PrefsMajorFilter),
        )
        if (query.isNotEmpty()) {
            val matches = majors.filter { it.label.contains(query, ignoreCase = true) }
            val shown = matches.take(MAJOR_MATCHES_SHOWN)
            Surface(
                shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth().testTag(SearchTags.PrefsMajorMatches),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    if (shown.isEmpty()) Text(stringResource(R.string.prefs_major_none), Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
                    shown.forEachIndexed { i, m ->
                        if (i > 0) HorizontalDivider()
                        val on = m.id == chosen
                        Surface(
                            onClick = { onPick(m.id); filter = "" }, selected = on,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(SearchTags.major(m.id)).semantics { role = Role.Checkbox },
                            color = if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
                        ) {
                            Text((if (on) "✓ " else "") + m.label, Modifier.padding(horizontal = 12.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                    val more = matches.size - shown.size
                    if (more > 0) Text(
                        pluralStringResource(R.plurals.prefs_major_more, more, more), Modifier.padding(12.dp).testTag(SearchTags.PrefsMajorMore),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** How many matching majors show at once; the rest are reached by typing more. */
internal const val MAJOR_MATCHES_SHOWN = 5
