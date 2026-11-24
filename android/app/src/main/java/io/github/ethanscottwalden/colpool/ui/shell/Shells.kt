package io.github.ethanscottwalden.colpool.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ethanscottwalden.colpool.R

/** Test tag of the Hosting tab's pending-requests badge. */
const val HOSTING_BADGE_TAG = "hosting_tab_badge"

/** Prototype green for the staff header (26-export-carpool-data.png); dark text on it has strong contrast. */
private val StaffGreen = Color(0xFFCDE5C4)

// Letter glyphs stand in for icons: material-icons is not an approved dependency yet.
private enum class StudentTab(val label: Int, val glyph: String) {
    Search(R.string.tab_search, "S"),
    Hosting(R.string.tab_hosting, "H"),
    Riding(R.string.tab_riding, "R"),
    Notifications(R.string.tab_notifications, "N"),
    Profile(R.string.tab_profile, "P"),
}

/** Student footer with five tabs. Search, Profile and Hosting are real; the other tabs are placeholders until their Issues. */
@Composable
fun StudentShell(searchTab: @Composable (Modifier) -> Unit, profileTab: @Composable (Modifier) -> Unit, hostingTab: @Composable (Modifier) -> Unit, initialTab: Int = 0, modifier: Modifier = Modifier, hostingBadge: Int = 0) {
    var selected by rememberSaveable { mutableIntStateOf(initialTab) }
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                StudentTab.entries.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = selected == i,
                        onClick = { selected = i },
                        icon = {
                            val glyph = @Composable { Text(tab.glyph, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleMedium) }
                            if (tab == StudentTab.Hosting && hostingBadge > 0) {
                                val cd = pluralStringResource(R.plurals.hosting_tab_badge_cd, hostingBadge, hostingBadge)
                                BadgedBox(badge = { Badge(Modifier.offset(x = 10.dp, y = (-4).dp).testTag(HOSTING_BADGE_TAG).semantics { contentDescription = cd }) { Text(hostingBadge.toString(), Modifier.clearAndSetSemantics {}) } }) { glyph() }
                            } else {
                                glyph()
                            }
                        },
                        label = { Text(stringResource(tab.label), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp)) },
                    )
                }
            }
        },
    ) { padding ->
        val tab = StudentTab.entries[selected]
        if (tab == StudentTab.Search) {
            searchTab(Modifier.padding(padding))
        } else if (tab == StudentTab.Profile) {
            profileTab(Modifier.padding(padding))
        } else if (tab == StudentTab.Hosting) {
            hostingTab(Modifier.padding(padding))
        } else {
            Centered(Modifier.padding(padding)) {
                Text(stringResource(tab.label), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.placeholder_coming_soon), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** University staff: Export Carpool Data placeholder. A header with Log out, no footer and no tabs. */
@Composable
fun StaffExportPlaceholder(onLogOut: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(StaffGreen).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.staff_kicker), style = MaterialTheme.typography.labelMedium)
                    Text(stringResource(R.string.staff_title), style = MaterialTheme.typography.headlineSmall)
                }
                OutlinedButton(
                    onClick = onLogOut,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                ) { Text(stringResource(R.string.log_out)) }
            }
            Text(stringResource(R.string.staff_body), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Routing couldn't finish: retry, or log out. Never a student or staff screen. */
@Composable
fun SessionErrorScreen(onRetry: () -> Unit, onLogOut: () -> Unit, modifier: Modifier = Modifier, detail: String? = null) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Centered {
            Text(stringResource(R.string.session_error_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(stringResource(R.string.session_error_body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            // Debug builds only (null in release).
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Button(onClick = onRetry) { Text(stringResource(R.string.session_retry)) }
            OutlinedButton(onClick = onLogOut) { Text(stringResource(R.string.log_out)) }
        }
    }
}

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Centered { CircularProgressIndicator() }
    }
}

@Composable
private fun Centered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
