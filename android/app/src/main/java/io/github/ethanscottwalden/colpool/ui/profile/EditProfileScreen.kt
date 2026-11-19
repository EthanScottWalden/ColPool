package io.github.ethanscottwalden.colpool.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ethanscottwalden.colpool.R
import io.github.ethanscottwalden.colpool.ui.address.AddressSearchSheet
import io.github.ethanscottwalden.colpool.ui.address.AddressSearchViewModel

object EditProfileTags {
    const val FirstName = "edit_first_name"
    const val LastName = "edit_last_name"
    const val Address = "edit_address"
    const val Age = "edit_age"
    const val AboutMe = "edit_about_me"
    const val Save = "edit_save"
    const val SaveHint = "edit_save_hint"
    const val Cancel = "edit_cancel"
}

class EditProfileActions(
    val onFirstName: (String) -> Unit = {},
    val onLastName: (String) -> Unit = {},
    val onChooseAddress: () -> Unit = {},
    val fields: OptionalFieldsActions = OptionalFieldsActions(),
    val onSave: () -> Unit = {},
    val onCancel: () -> Unit = {},
)

/** Stateful wrapper: owns the address sheet. A fresh address entry starts each time the sheet opens. */
@Composable
fun EditProfileScreen(viewModel: EditProfileViewModel, addressSearch: AddressSearchViewModel, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onCancel)
    EditProfileContent(
        state = state,
        modifier = modifier,
        actions = EditProfileActions(
            onFirstName = viewModel::setFirstName,
            onLastName = viewModel::setLastName,
            onChooseAddress = { addressSearch.startNewEntry(); sheetOpen = true },
            fields = OptionalFieldsActions(
                onAge = viewModel::setAge,
                onGender = viewModel::setGender,
                onYear = viewModel::setYear,
                onMajor = viewModel::setMajor,
                onInterest = viewModel::toggleInterest,
                onTrait = viewModel::toggleTrait,
                onAboutMe = viewModel::setAboutMe,
            ),
            onSave = viewModel::save,
            onCancel = onCancel,
        ),
    )
    if (sheetOpen) {
        AddressSearchSheet(
            title = stringResource(R.string.onboarding_address_sheet_title),
            viewModel = addressSearch,
            onConfirm = { viewModel.setAddress(it); sheetOpen = false },
            onDismiss = { sheetOpen = false },
        )
    }
}

@Composable
fun EditProfileContent(state: EditProfileUiState, actions: EditProfileActions, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding()) {
            Text(
                stringResource(R.string.edit_profile_title),
                Modifier.fillMaxWidth().padding(16.dp),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.edit_profile_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                FormLabel(R.string.edit_first_name)
                NameField(state.firstName, state.firstNameInvalid, actions.onFirstName, EditProfileTags.FirstName)
                FormLabel(R.string.edit_last_name)
                NameField(state.lastName, state.lastNameInvalid, actions.onLastName, EditProfileTags.LastName)

                FormLabel(R.string.edit_address_label)
                OutlinedButton(onClick = actions.onChooseAddress, modifier = Modifier.fillMaxWidth().testTag(EditProfileTags.Address)) {
                    Text(state.address?.text ?: stringResource(R.string.onboarding_address_placeholder), Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                }
                Text(stringResource(R.string.onboarding_address_private), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                OptionalFieldsForm(
                    input = OptionalFieldsInput(
                        optionsStatus = OptionsStatus.Loaded, options = state.options, age = state.age, ageInvalid = state.ageInvalid,
                        gender = state.gender, year = state.year, major = state.major,
                        interests = state.interests, traits = state.traits, aboutMe = state.aboutMe,
                    ),
                    actions = actions.fields,
                    optionsErrorText = "",
                    ageTag = EditProfileTags.Age,
                    aboutMeTag = EditProfileTags.AboutMe,
                )

                if (state.saveFailed) FormErrorText(stringResource(R.string.edit_save_error))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = actions.onCancel, Modifier.weight(1f).testTag(EditProfileTags.Cancel)) { Text(stringResource(R.string.edit_cancel)) }
                    // Disabled while the form is invalid, nothing changed, or a save is running.
                    Button(onClick = actions.onSave, enabled = state.canSave, modifier = Modifier.weight(1f).testTag(EditProfileTags.Save)) {
                        Text(stringResource(if (state.saving) R.string.edit_saving else if (state.saveFailed) R.string.session_retry else R.string.edit_save))
                    }
                }
                val hint = when {
                    state.saving || state.canSave -> null
                    state.invalid -> R.string.edit_hint_invalid
                    else -> R.string.edit_hint_unchanged
                }
                if (hint != null) {
                    Text(
                        stringResource(hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally).testTag(EditProfileTags.SaveHint),
                    )
                }
            }
        }
    }
}

@Composable
private fun NameField(value: String, invalid: Boolean, onChange: (String) -> Unit, tag: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        singleLine = true,
        isError = invalid,
        supportingText = if (invalid) ({ Text(stringResource(R.string.edit_name_error, ProfileLimits.NAME_MAX)) }) else null,
    )
}
