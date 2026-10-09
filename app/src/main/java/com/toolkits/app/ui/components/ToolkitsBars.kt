package com.toolkits.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.toolkits.app.R

// Mirrors Widget.ToolKits.Toolbar: surface background, TitleLarge, 0 elevation,
// back arrow, optional actions (e.g. settings gear on Home).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolkitsTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

// Section label mirrors TextAppearance.ToolKits.SectionLabel: LabelMedium in primary.
@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

// Tool row card mirrors activity_main / activity_text_tools rows:
// 16dp corners, 0 elevation, 16dp padding, min 88dp height,
// title TitleMedium onSurface, desc BodySmall onSurfaceVariant,
// trailing 24dp arrow_forward in onSurfaceVariant.
// Uses Card(onClick=) so the M3 ripple is bounded to the card shape
// (a bare Modifier.clickable on a Card lets the ripple bleed outside).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolRowCard(
    title: String,
    subtitle: String,
    @DrawableRes iconRes: Int,
    circledIcon: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 88.dp).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (circledIcon) {
                // 52dp oval container (bg_tool_icon) tinted primaryContainer + 28dp icon in primary.
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(iconRes), contentDescription = null,
                        modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                // Home-style direct 48dp icon in primary.
                Icon(
                    painterResource(iconRes), contentDescription = null,
                    modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Icon(
                painterResource(R.drawable.ic_arrow_forward), contentDescription = null,
                modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Outlined section card mirrors Widget.ToolKits.CardView.Outlined:
// 16dp corners, 0 elevation, 1dp outlineVariant stroke.
@Composable
fun OutlinedSectionCard(content: @Composable () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        content()
    }
}

// Section header row inside outlined cards: 20dp icon + LabelLarge primary title + expand chevron.
// Clip + bounded clickable keeps the ripple inside the row.
@Composable
fun CardHeaderRow(
    @DrawableRes iconRes: Int,
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onToggle).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(iconRes), contentDescription = null,
            modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary
        )
        Text(
            title,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
        )
        Icon(
            painterResource(if (expanded) R.drawable.ic_chevron_up else R.drawable.ic_expand_more),
            contentDescription = null,
            modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CardDivider() {
    HorizontalDivider(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.outlineVariant)
}
