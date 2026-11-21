package io.github.ethanscottwalden.colpool.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ethanscottwalden.colpool.R
import io.github.ethanscottwalden.colpool.data.model.OptionItem
import io.github.ethanscottwalden.colpool.data.model.OwnProfile
import io.github.ethanscottwalden.colpool.ui.address.AddressSearchViewModel

object ProfileTags {
    const val Name = "profile_name"
    const val Address = "profile_address"
    const val Email = "profile_email"
    const val Edit = "profile_edit"
    const val LogOut = "profile_log_out"
    const val Retry = "profile_retry"
}

class ProfileActions(
    val onEdit: () -> Unit = {},
    val onLogOut: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** The student's own profile (US-02): everything they set, plus the starting address and email only they can see. */
@Composable
fun ProfileContent(state: ProfileUiState, actions: ProfileActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.profile_title),
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        val profile = state.profile
        when {
            state.status == OptionsStatus.Loading && profile == null ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            profile == null || state.status == OptionsStatus.Failed -> LoadError(actions.onRetry, actions.onLogOut)
            else -> Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                About(profile, state)
                PrivateCard(R.string.profile_address_label, profile.address.text, R.string.profile_address_note, ProfileTags.Address)
                PrivateCard(R.string.profile_email_label, profile.email, R.string.profile_email_note, ProfileTags.Email)
                Button(onClick = actions.onEdit, Modifier.fillMaxWidth().testTag(ProfileTags.Edit)) { Text(stringResource(R.string.profile_edit)) }
                OutlinedButton(onClick = actions.onLogOut, Modifier.fillMaxWidth().testTag(ProfileTags.LogOut)) { Text(stringResource(R.string.log_out)) }
            }
        }
    }
}

@Composable
private fun LoadError(onRetry: () -> Unit, onLogOut: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.profile_load_error), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Button(onClick = onRetry, Modifier.testTag(ProfileTags.Retry)) { Text(stringResource(R.string.session_retry)) }
        // A failed load must not trap a student on the wrong account.
        OutlinedButton(onClick = onLogOut) { Text(stringResource(R.string.log_out)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun About(p: OwnProfile, state: ProfileUiState) {
    val o = state.options
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${p.firstName} ${p.lastName}", Modifier.testTag(ProfileTags.Name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Field(R.string.profile_year, label(o.years, p.year))
            Field(R.string.profile_major, label(o.majors, p.major))
            Field(R.string.profile_gender, label(o.genders, p.gender))
            Field(R.string.profile_age, p.age?.toString())
            Chips(R.string.profile_interests, p.interests.mapNotNull { label(o.interests, it) })
            Chips(R.string.profile_traits, p.traits.mapNotNull { label(o.traits, it) })
            Field(R.string.profile_about_me, p.aboutMe)
        }
    }
}

private fun label(list: List<OptionItem>, id: String?): String? = id?.let { x -> list.firstOrNull { it.id == x }?.label }

/** Shown only when set (the optional fields can be absent). */
@Composable
private fun Field(title: Int, value: String?) {
    if (value == null) return
    Column {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(title: Int, labels: List<String>) {
    if (labels.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEach {
                // A plain label, not a disabled chip: normal contrast, and nothing announces "disabled".
                Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = Color.Transparent) {
                    Text(it, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun PrivateCard(title: Int, value: String, note: Int, tag: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, Modifier.testTag(tag), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The Profile tab: the profile, or Edit Profile while the edit form is open. */
@Composable
fun ProfileTab(
    profile: ProfileViewModel,
    editProfile: EditProfileViewModel,
    addressSearch: AddressSearchViewModel,
    onLogOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by profile.state.collectAsState()
    val edit by editProfile.state.collectAsState()
    if (edit.ready) {
        EditProfileScreen(editProfile, addressSearch, onCancel = editProfile::reset, modifier = modifier)
    } else {
        ProfileContent(
            state = state,
            actions = ProfileActions(
                onEdit = { state.profile?.let { editProfile.start(it, state.options) } },
                onLogOut = onLogOut,
                onRetry = profile::load,
            ),
            modifier = modifier,
        )
    }
}
