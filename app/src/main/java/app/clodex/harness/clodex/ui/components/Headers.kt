package app.clodex.harness.clodex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.clodex.harness.R
import app.clodex.harness.clodex.theme.LocalClodexStyle

/** Standard screen header: leading circle button, centred title, trailing circle button. */
@Composable
fun ScreenHeader(
    title: String,
    leadingIcon: ImageVector,
    leadingDescription: String,
    onLeading: () -> Unit,
    trailingIcon: ImageVector? = null,
    trailingDescription: String? = null,
    onTrailing: () -> Unit = {},
    modifier: Modifier = Modifier,
    horizontalPadding: Int = 16,
    centerTitle: Boolean = true,
    leadingSize: Int = 44,
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = horizontalPadding.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircleButton(leadingIcon, leadingDescription, onLeading, size = leadingSize.dp)
        if (centerTitle) {
            Text(
                title,
                style = style.type.subtitle,
                color = colors.textPrimary,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            if (trailingIcon != null) {
                IconCircleButton(trailingIcon, trailingDescription, onTrailing, size = leadingSize.dp)
            } else {
                Box(Modifier.size(leadingSize.dp))
            }
        } else {
            Text(
                title,
                style = style.type.display,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            if (trailingIcon != null) {
                IconCircleButton(trailingIcon, trailingDescription, onTrailing, size = leadingSize.dp)
            }
        }
    }
}


/** Header of the settings/appearance screens: back arrow, 20 sp title, optional trailing action. */
@Composable
fun BackHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null,
    trailingDescription: String? = null,
    onTrailing: () -> Unit = {},
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircleButton(
            app.clodex.harness.clodex.ui.icons.ClodexIcons.ChevronLeft,
            stringResource(R.string.cd_back),
            onBack,
        )
        Text(
            title,
            style = style.type.heading,
            color = colors.textPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        if (trailingIcon != null) {
            IconCircleButton(trailingIcon, trailingDescription, onTrailing)
        } else {
            Box(Modifier.size(44.dp))
        }
    }
}

/** A settings row: icon or title with caption on the left, an accessory on the right. */
@Composable
fun SettingsRow(
    title: String,
    caption: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = style.type.rowTitle.copy(fontWeight = FontWeight.Normal), color = colors.textSecondary)
            if (caption != null) {
                Text(caption, style = style.type.captionSmall, color = colors.textMuted)
            }
        }
        if (value != null) {
            Text(value, style = style.type.caption, color = colors.textMuted)
        }
        if (trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
        if (chevron) {
            Icon(
                app.clodex.harness.clodex.ui.icons.ClodexIcons.ChevronRight,
                contentDescription = null,
                tint = colors.textMuted2,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Small square tile used by the project list (44 dp, 12 dp radius). */
@Composable
fun SquareIcon(icon: ImageVector, background: Color, tint: Color, size: Int = 44) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

private fun unusedColumnScope(): ColumnScope? = null
