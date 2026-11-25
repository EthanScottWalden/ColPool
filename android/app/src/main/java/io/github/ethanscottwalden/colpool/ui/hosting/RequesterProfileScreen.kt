package io.github.ethanscottwalden.colpool.ui.hosting

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ethanscottwalden.colpool.R
import io.github.ethanscottwalden.colpool.ui.details.DetailsRules
import io.github.ethanscottwalden.colpool.ui.details.ProfileCard

object RequesterTags {
    const val Card = "requester_card"
    const val Unavailable = "requester_unavailable"
    const val Loading = "requester_loading"
}

/** Stateful Requester Profile, opened from a request row. */
@Composable
fun RequesterProfileScreen(requests: HostRequestsViewModel, modifier: Modifier = Modifier) {
    val state by requests.state.collectAsStateWithLifecycle()
    BackHandler { requests.closeProfile() }
    state.opened?.let { RequesterProfileContent(it, state.options, requests::closeProfile, modifier) }
}

/**
 * A requester's profile (US-07, RC-01 case 3): the name always, optional fields only when filled. Never the email or starting address.
 * When the profile can't be read (access ended) it says so instead of showing anything.
 */
@Composable
fun RequesterProfileContent(opened: RequesterProfileState, options: io.github.ethanscottwalden.colpool.data.model.ProfileOptions, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Header(stringResource(R.string.requester_title), onBack)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val p = opened.profile
                when {
                    p != null -> ProfileCard("${p.firstName} ${p.lastName}", DetailsRules.hostFields(p, options), Modifier.testTag(RequesterTags.Card))
                    !opened.loaded -> Text(stringResource(R.string.requester_loading), Modifier.testTag(RequesterTags.Loading).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyLarge)
                    else -> Text(
                        stringResource(R.string.requester_unavailable),
                        Modifier.fillMaxWidth().testTag(RequesterTags.Unavailable).semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
