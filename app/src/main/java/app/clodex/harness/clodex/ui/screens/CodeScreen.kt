package app.clodex.harness.clodex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.clodex.harness.R
import app.clodex.harness.clodex.settings.AppearanceSettings
import app.clodex.harness.clodex.theme.ClodexTheme
import app.clodex.harness.clodex.theme.LocalClodexStyle
import app.clodex.harness.clodex.ui.components.ChipRow
import app.clodex.harness.clodex.ui.components.ChipSpec
import app.clodex.harness.clodex.ui.components.ComposerBar
import app.clodex.harness.clodex.ui.components.IconCircleButton
import app.clodex.harness.clodex.ui.components.SegmentedControl
import app.clodex.harness.clodex.ui.components.Status
import app.clodex.harness.clodex.ui.components.StatusDot
import app.clodex.harness.clodex.ui.components.clodexBackground
import app.clodex.harness.clodex.ui.icons.ClodexIcons

data class CodeSession(
    val branch: String,
    val age: String,
    val title: String,
    val status: String,
    val statusKind: Status,
    val diff: String? = null,
)

/** ЭКРАН 6. CODE */
@Composable
fun CodeScreen(
    onMenu: () -> Unit,
    onPlus: () -> Unit,
    sessions: List<CodeSession> = emptyList(),
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    var segment by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().clodexBackground()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(ClodexIcons.Menu, stringResource(R.string.cd_menu), onMenu)
            Spacer(Modifier.weight(1f))
            IconCircleButton(ClodexIcons.Plus, stringResource(R.string.cd_add), onPlus)
        }
        Text(
            stringResource(R.string.code_title),
            style = style.type.display,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        SegmentedControl(
            options = listOf(stringResource(R.string.code_sessions), stringResource(R.string.code_repos)),
            selectedIndex = segment,
            onSelect = { segment = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            sessions.forEach { session -> item { SessionCard(session) } }
        }
        ChipRow(
            chips = listOf(
                ChipSpec(stringResource(R.string.code_chip_repo), ClodexIcons.Folder),
                ChipSpec(stringResource(R.string.code_chip_ask), ClodexIcons.Shield),
            ),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(10.dp))
        ComposerBar(
            hint = stringResource(R.string.code_hint),
            value = text,
            onValueChange = { text = it },
            onSend = { text = "" },
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 28.dp),
        )
    }
}

@Composable
private fun SessionCard(session: CodeSession) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    val shape = RoundedCornerShape(style.radius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ClodexIcons.Branch, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(6.dp))
            Text(session.branch, style = style.type.monoSmall, color = colors.textMuted)
            Spacer(Modifier.weight(1f))
            Text(session.age, style = style.type.monoSmall, color = colors.textMuted)
        }
        Text(session.title, style = style.type.cardTitle, color = colors.textPrimary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (session.statusKind) {
                Status.Running -> StatusDot(Status.Running)
                Status.Waiting -> Icon(ClodexIcons.Shield, contentDescription = null, tint = colors.warning, modifier = Modifier.size(14.dp))
                Status.Done -> Icon(ClodexIcons.Check, contentDescription = null, tint = colors.successStrong, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.size(8.dp))
            Text(
                session.status,
                style = style.type.caption,
                color = if (session.statusKind == Status.Waiting) colors.warning else colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            if (session.diff != null) {
                Text(session.diff, style = style.type.monoSmall, color = colors.successStrong)
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CodeModernPreview() {
    ClodexTheme(AppearanceSettings()) {
        CodeScreen({}, {}, demoCodeSessions())
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CodeGlassPreview() {
    ClodexTheme(AppearanceSettings(liquidGlass = true)) {
        CodeScreen({}, {}, demoCodeSessions())
    }
}

@Composable
fun demoCodeSessions(): List<CodeSession> = listOf(
    CodeSession(
        branch = stringResource(R.string.code_s1_repo),
        age = stringResource(R.string.code_age_2m),
        title = stringResource(R.string.code_s1_project),
        status = stringResource(R.string.code_s1_status),
        statusKind = Status.Running,
        diff = stringResource(R.string.code_diff_small),
    ),
    CodeSession(
        branch = stringResource(R.string.code_s2_repo),
        age = stringResource(R.string.code_age_11m),
        title = stringResource(R.string.code_s2_project),
        status = stringResource(R.string.code_s2_status),
        statusKind = Status.Waiting,
    ),
    CodeSession(
        branch = stringResource(R.string.code_s3_repo),
        age = stringResource(R.string.code_age_1h),
        title = stringResource(R.string.code_s3_project),
        status = stringResource(R.string.code_s3_status),
        statusKind = Status.Done,
        diff = stringResource(R.string.code_diff_large),
    ),
)
