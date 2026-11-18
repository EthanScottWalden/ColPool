package io.github.ethanscottwalden.colpool.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ethanscottwalden.colpool.R
import io.github.ethanscottwalden.colpool.data.session.AuthFailureKind
import io.github.ethanscottwalden.colpool.ui.session.SignedOutNotice

/** Callbacks for [AuthContent]. */
class AuthActions(
    val onMode: (AuthMode) -> Unit = {},
    val onFirstName: (String) -> Unit = {},
    val onLastName: (String) -> Unit = {},
    val onEmail: (String) -> Unit = {},
    val onPassword: (String) -> Unit = {},
    val onConsent: (Boolean) -> Unit = {},
    val onSubmit: () -> Unit = {},
)

@Composable
fun AuthContent(
    state: AuthUiState,
    notice: SignedOutNotice?,
    actions: AuthActions,
    modifier: Modifier = Modifier,
) {
    val signUp = state.mode == AuthMode.SignUp
    val errors = state.fieldErrors
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("ColPool", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(if (signUp) R.string.auth_tagline_sign_up else R.string.auth_tagline_sign_in),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !signUp,
                        onClick = { actions.onMode(AuthMode.SignIn) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text(stringResource(R.string.auth_tab_sign_in)) }
                    SegmentedButton(
                        selected = signUp,
                        onClick = { actions.onMode(AuthMode.SignUp) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text(stringResource(R.string.auth_tab_sign_up)) }
                }

                if (notice == SignedOutNotice.NoConsent && !signUp) {
                    Banner(stringResource(R.string.auth_notice_no_consent))
                }

                if (signUp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Field(R.string.auth_first_name, state.firstName, actions.onFirstName, Modifier.weight(1f),
                            error = SignUpFieldError.FirstName in errors, errorRes = R.string.auth_error_first_name)
                        Field(R.string.auth_last_name, state.lastName, actions.onLastName, Modifier.weight(1f),
                            error = SignUpFieldError.LastName in errors, errorRes = R.string.auth_error_last_name)
                    }
                }
                Field(R.string.auth_email, state.email, actions.onEmail, Modifier.fillMaxWidth(),
                    hint = R.string.auth_email_hint, keyboard = KeyboardType.Email,
                    error = SignUpFieldError.Email in errors, errorRes = R.string.auth_error_email_domain)
                Field(R.string.auth_password, state.password, actions.onPassword, Modifier.fillMaxWidth(),
                    password = true, error = SignUpFieldError.Password in errors, errorRes = R.string.auth_error_password_short)

                if (signUp) {
                    Row(
                        Modifier.fillMaxWidth().toggleable(value = state.consent, role = Role.Checkbox, onValueChange = actions.onConsent),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Checkbox(checked = state.consent, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        Text(consentText(), Modifier.padding(top = 12.dp, end = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (SignUpFieldError.Consent in errors) ErrorText(stringResource(R.string.auth_error_consent))
                }

                state.formError?.let { Banner(stringResource(formErrorRes(it)), state.formErrorDetail) }

                Button(onClick = actions.onSubmit, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            when {
                                state.busy -> R.string.auth_working
                                signUp -> R.string.auth_submit_sign_up
                                else -> R.string.auth_submit_sign_in
                            },
                        ),
                    )
                }
                Text(
                    stringResource(R.string.auth_no_password_reset),
                    Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun consentText(): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(R.string.auth_consent_title)) }
    append(" ")
    append(stringResource(R.string.auth_consent_body))
}

@Composable
private fun Field(
    label: Int,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier,
    hint: Int? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    error: Boolean = false,
    errorRes: Int = 0,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        label = { Text(stringResource(label)) },
        placeholder = hint?.let { { Text(stringResource(it)) } },
        singleLine = true,
        isError = error,
        supportingText = if (error) ({ Text(stringResource(errorRes)) }) else null,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(text, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Banner(text: String, detail: String? = null) {
    Box(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium).padding(12.dp),
    ) {
        Column {
            Text(text, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            // Debug builds only (null in release).
            if (detail != null) Text(detail, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formErrorRes(kind: AuthFailureKind): Int = when (kind) {
    AuthFailureKind.InvalidCredentials -> R.string.auth_error_invalid_credentials
    AuthFailureKind.FieldsRequired -> R.string.auth_error_fields_required
    AuthFailureKind.AccountCreatedSignIn -> R.string.auth_error_account_created
    AuthFailureKind.CouldNotConfirm -> R.string.auth_error_could_not_confirm
    AuthFailureKind.Network -> R.string.auth_error_network
    AuthFailureKind.EmailDomain -> R.string.auth_error_email_domain
    AuthFailureKind.PasswordTooShort -> R.string.auth_error_password_short
    AuthFailureKind.FirstNameInvalid -> R.string.auth_error_first_name
    AuthFailureKind.LastNameInvalid -> R.string.auth_error_last_name
    AuthFailureKind.ConsentRequired -> R.string.auth_error_consent
    AuthFailureKind.EmailInUse -> R.string.auth_error_email_in_use
    AuthFailureKind.Unknown, AuthFailureKind.Internal -> R.string.auth_error_unknown
}
