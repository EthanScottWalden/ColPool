package io.github.ethanscottwalden.colpool.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ethanscottwalden.colpool.R
import io.github.ethanscottwalden.colpool.data.model.OptionItem
import io.github.ethanscottwalden.colpool.data.places.SelectedAddress
import io.github.ethanscottwalden.colpool.ui.address.AddressSearchSheet
import io.github.ethanscottwalden.colpool.ui.address.AddressSearchViewModel

object OnboardingTags {
    const val AddressField = "onboarding_address_field"
    const val Age = "onboarding_age"
    const val AboutMe = "onboarding_about_me"
    const val Finish = "onboarding_finish"
}

class OnboardingActions(
    val onChooseAddress: () -> Unit = {},
    val onAge: (String) -> Unit = {},
    val onGender: (String?) -> Unit = {},
    val onYear: (String?) -> Unit = {},
    val onMajor: (String?) -> Unit = {},
    val onInterest: (String) -> Unit = {},
    val onTrait: (String) -> Unit = {},
    val onAboutMe: (String) -> Unit = {},
    val onFinish: () -> Unit = {},
    val onRetryOptions: () -> Unit = {},
    val onLogOut: () -> Unit = {},
)

/** Stateful wrapper: owns the address sheet. A fresh address entry starts each time the sheet opens. */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, addressSearch: AddressSearchViewModel, onLogOut: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    OnboardingContent(
        state = state,
        actions = OnboardingActions(
            onChooseAddress = { addressSearch.startNewEntry(); sheetOpen = true },
            onAge = viewModel::setAge,
            onGender = viewModel::setGender,
            onYear = viewModel::setYear,
            onMajor = viewModel::setMajor,
            onInterest = viewModel::toggleInterest,
            onTrait = viewModel::toggleTrait,
            onAboutMe = viewModel::setAboutMe,
            onFinish = viewModel::finish,
            onRetryOptions = viewModel::loadOptions,
            onLogOut = onLogOut,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingContent(state: OnboardingUiState, actions: OnboardingActions, modifier: Modifier = Modifier) {
    val ageError = state.ageInvalid
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding()) {
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.onboarding_welcome), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(stringResource(R.string.onboarding_intro), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(R.string.onboarding_address_label)
                OutlinedButton(
                    onClick = actions.onChooseAddress,
                    modifier = Modifier.fillMaxWidth().testTag(OnboardingTags.AddressField),
                    border = BorderStroke(if (state.showErrors && state.addressMissing) 2.dp else 1.dp, if (state.showErrors && state.addressMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        state.address?.text ?: stringResource(R.string.onboarding_address_placeholder),
                        Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                        color = if (state.address == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (state.showErrors && state.addressMissing) ErrorText(stringResource(R.string.onboarding_address_required))
                Text(stringResource(R.string.onboarding_address_private), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.onboarding_optional_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))

                Label(R.string.onboarding_age)
                OutlinedTextField(
                    value = state.age,
                    onValueChange = actions.onAge,
                    modifier = Modifier.width(160.dp).testTag(OnboardingTags.Age),
                    placeholder = { Text(stringResource(R.string.onboarding_optional)) },
                    singleLine = true,
                    isError = ageError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (ageError) ErrorText(stringResource(R.string.onboarding_age_error))

                when (state.optionsStatus) {
                    OptionsStatus.Loading -> Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    OptionsStatus.Failed -> {
                        ErrorText(stringResource(R.string.onboarding_options_error))
                        OutlinedButton(onClick = actions.onRetryOptions) { Text(stringResource(R.string.session_retry)) }
                    }
                    OptionsStatus.Loaded -> {
                        SingleChoice(R.string.onboarding_gender, state.options.genders, state.gender, actions.onGender)
                        SingleChoice(R.string.onboarding_year, state.options.years, state.year, actions.onYear)
                        MajorPicker(state.options.majors, state.major, actions.onMajor)
                        MultiChoice(R.string.onboarding_interests, state.options.interests, state.interests, actions.onInterest)
                        MultiChoice(R.string.onboarding_traits, state.options.traits, state.traits, actions.onTrait)
                    }
                }

                Label(R.string.onboarding_about_me)
                OutlinedTextField(
                    value = state.aboutMe,
                    onValueChange = actions.onAboutMe,
                    modifier = Modifier.fillMaxWidth().testTag(OnboardingTags.AboutMe),
                    placeholder = { Text(stringResource(R.string.onboarding_about_me_hint)) },
                    minLines = 3,
                    supportingText = {
                        Text(
                            stringResource(R.string.onboarding_about_me_count, state.aboutMe.length, OnboardingLimits.ABOUT_ME_MAX),
                            Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                        )
                    },
                )

                if (state.saveFailed) ErrorText(stringResource(R.string.onboarding_save_error))
                Button(
                    onClick = actions.onFinish,
                    // Disabled until a starting address is chosen and every entered value is valid.
                    enabled = state.canFinish,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(OnboardingTags.Finish),
                ) {
                    Text(stringResource(if (state.saving) R.string.onboarding_saving else if (state.saveFailed) R.string.session_retry else R.string.onboarding_finish))
                }
                // TODO(#27): Log out moves to the real Profile screen; here it keeps a wrong-account sign-in from being a dead end.
                TextButton(
                    onClick = actions.onLogOut,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) { Text(stringResource(R.string.log_out)) }
            }
        }
    }
}

@Composable
private fun Label(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ErrorText(text: String) {
    Text(text, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SingleChoice(label: Int, items: List<OptionItem>, selected: String?, onSelect: (String?) -> Unit) {
    Label(label)
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
    Label(label)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            FilterChip(selected = item.id in selected, onClick = { onToggle(item.id) }, label = { Text(item.label) })
        }
    }
}

/** The program list is long (127 entries), so it is a dropdown instead of chips. */
@Composable
private fun MajorPicker(items: List<OptionItem>, selected: String?, onSelect: (String?) -> Unit) {
    Label(R.string.onboarding_major)
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                items.firstOrNull { it.id == selected }?.label ?: stringResource(R.string.onboarding_optional),
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
                color = if (selected == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.onboarding_no_selection)) }, onClick = { onSelect(null); open = false })
            items.forEach { item ->
                DropdownMenuItem(text = { Text(item.label) }, onClick = { onSelect(item.id); open = false })
            }
        }
    }
}
