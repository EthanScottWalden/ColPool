package io.github.ethanscottwalden.colpool.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import io.github.ethanscottwalden.colpool.data.places.SelectedAddress
import io.github.ethanscottwalden.colpool.ui.address.AddressSearchSheet
import io.github.ethanscottwalden.colpool.ui.address.AddressSearchViewModel
import io.github.ethanscottwalden.colpool.ui.profile.FormErrorText
import io.github.ethanscottwalden.colpool.ui.profile.FormLabel
import io.github.ethanscottwalden.colpool.ui.profile.OptionalFieldsActions
import io.github.ethanscottwalden.colpool.ui.profile.OptionalFieldsForm
import io.github.ethanscottwalden.colpool.ui.profile.OptionalFieldsInput

object OnboardingTags {
    const val AddressField = "onboarding_address_field"
    const val Age = "onboarding_age"
    const val AboutMe = "onboarding_about_me"
    const val Finish = "onboarding_finish"
    const val FinishHint = "onboarding_finish_hint"
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

@Composable
fun OnboardingContent(state: OnboardingUiState, actions: OnboardingActions, modifier: Modifier = Modifier) {
    val ageError = state.ageInvalid
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding()) {
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).statusBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.onboarding_welcome), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(stringResource(R.string.onboarding_intro), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormLabel(R.string.onboarding_address_label)
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
                if (state.showErrors && state.addressMissing) FormErrorText(stringResource(R.string.onboarding_address_required))
                Text(stringResource(R.string.onboarding_address_private), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.onboarding_optional_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))

                OptionalFieldsForm(
                    input = OptionalFieldsInput(
                        optionsStatus = state.optionsStatus, options = state.options, age = state.age, ageInvalid = ageError,
                        gender = state.gender, year = state.year, major = state.major,
                        interests = state.interests, traits = state.traits, aboutMe = state.aboutMe,
                    ),
                    actions = OptionalFieldsActions(
                        onAge = actions.onAge, onGender = actions.onGender, onYear = actions.onYear, onMajor = actions.onMajor,
                        onInterest = actions.onInterest, onTrait = actions.onTrait, onAboutMe = actions.onAboutMe,
                        onRetryOptions = actions.onRetryOptions,
                    ),
                    optionsErrorText = stringResource(R.string.onboarding_options_error),
                    ageTag = OnboardingTags.Age,
                    aboutMeTag = OnboardingTags.AboutMe,
                )

                if (state.saveFailed) FormErrorText(stringResource(R.string.onboarding_save_error))
                Button(
                    onClick = actions.onFinish,
                    // Disabled until a starting address is chosen and every entered value is valid.
                    enabled = state.canFinish,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(OnboardingTags.Finish),
                ) {
                    Text(stringResource(if (state.saving) R.string.onboarding_saving else if (state.saveFailed) R.string.session_retry else R.string.onboarding_finish))
                }
                // Finish is disabled without an address; say why instead of leaving a silent disabled button.
                if (state.addressMissing && !state.showErrors) {
                    Text(
                        stringResource(R.string.onboarding_address_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally).testTag(OnboardingTags.FinishHint),
                    )
                }
                // Log out here keeps a wrong-account sign-in from being a dead end (the Profile tab is not reachable yet).
                TextButton(
                    onClick = actions.onLogOut,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) { Text(stringResource(R.string.log_out)) }
            }
        }
    }
}
