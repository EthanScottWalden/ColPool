package io.github.ethanscottwalden.colpool.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ethanscottwalden.colpool.R
import io.github.ethanscottwalden.colpool.data.model.OptionItem
import io.github.ethanscottwalden.colpool.data.model.ProfileOptions

/** Current values of the optional profile fields, as a form shows them. */
class OptionalFieldsInput(
    val optionsStatus: OptionsStatus,
    val options: ProfileOptions,
    val age: String,
    val ageInvalid: Boolean,
    val gender: String?,
    val year: String?,
    val major: String?,
    val interests: Set<String>,
    val traits: Set<String>,
    val aboutMe: String,
)

class OptionalFieldsActions(
    val onAge: (String) -> Unit = {},
    val onGender: (String?) -> Unit = {},
    val onYear: (String?) -> Unit = {},
    val onMajor: (String?) -> Unit = {},
    val onInterest: (String) -> Unit = {},
    val onTrait: (String) -> Unit = {},
    val onAboutMe: (String) -> Unit = {},
    val onRetryOptions: () -> Unit = {},
)

/**
 * The optional profile fields (RC-22, RC-30): age, the predefined lists, about me. Shared by Onboarding and
 * Edit Profile. Emit inside a column with 8dp spacing. Tapping a chosen chip again clears it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptionalFieldsForm(
    input: OptionalFieldsInput,
    actions: OptionalFieldsActions,
    optionsErrorText: String,
    ageTag: String,
    aboutMeTag: String,
) {
    FormLabel(R.string.profile_age)
    OutlinedTextField(
        value = input.age,
        onValueChange = actions.onAge,
        modifier = Modifier.width(160.dp).testTag(ageTag),
        placeholder = { Text(stringResource(R.string.profile_optional)) },
        singleLine = true,
        isError = input.ageInvalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    if (input.ageInvalid) FormErrorText(stringResource(R.string.profile_age_error))

    when (input.optionsStatus) {
        OptionsStatus.Loading -> Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        OptionsStatus.Failed -> {
            FormErrorText(optionsErrorText)
            OutlinedButton(onClick = actions.onRetryOptions) { Text(stringResource(R.string.session_retry)) }
        }
        OptionsStatus.Loaded -> {
            SingleChoice(R.string.profile_gender, input.options.genders, input.gender, actions.onGender)
            SingleChoice(R.string.profile_year, input.options.years, input.year, actions.onYear)
            MajorPicker(input.options.majors, input.major, actions.onMajor)
            MultiChoice(R.string.profile_interests, input.options.interests, input.interests, actions.onInterest)
            MultiChoice(R.string.profile_traits, input.options.traits, input.traits, actions.onTrait)
        }
    }

    FormLabel(R.string.profile_about_me)
    OutlinedTextField(
        value = input.aboutMe,
        onValueChange = actions.onAboutMe,
        modifier = Modifier.fillMaxWidth().testTag(aboutMeTag),
        placeholder = { Text(stringResource(R.string.profile_about_me_hint)) },
        minLines = 3,
        supportingText = {
            Text(
                stringResource(R.string.profile_about_me_count, input.aboutMe.length, ProfileLimits.ABOUT_ME_MAX),
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
        },
    )
}

@Composable
fun FormLabel(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
fun FormErrorText(text: String) {
    Text(text, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SingleChoice(label: Int, items: List<OptionItem>, selected: String?, onSelect: (String?) -> Unit) {
    FormLabel(label)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val on = item.id == selected
            // Tapping the chosen chip again clears it: every field here is optional.
            FilterChip(selected = on, onClick = { onSelect(if (on) null else item.id) }, label = { Text(item.label) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiChoice(label: Int, items: List<OptionItem>, selected: Set<String>, onToggle: (String) -> Unit) {
    FormLabel(label)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            FilterChip(selected = item.id in selected, onClick = { onToggle(item.id) }, label = { Text(item.label) })
        }
    }
}

/** The program list is long (127 entries), so it is a dropdown instead of chips. */
@Composable
private fun MajorPicker(items: List<OptionItem>, selected: String?, onSelect: (String?) -> Unit) {
    FormLabel(R.string.profile_major)
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                items.firstOrNull { it.id == selected }?.label ?: stringResource(R.string.profile_optional),
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
                color = if (selected == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.profile_no_selection)) }, onClick = { onSelect(null); open = false })
            items.forEach { item ->
                DropdownMenuItem(text = { Text(item.label) }, onClick = { onSelect(item.id); open = false })
            }
        }
    }
}
