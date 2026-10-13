package ch.coachapp.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Max content width so forms and lists don't stretch across a desktop browser. */
val ContentMaxWidth = 840.dp

/** The page gutter. */
val PagePadding = 16.dp

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(14.dp))
            NeutralButton("Retry", onRetry, size = ButtonSize.Sm)
        }
    }
}

@Composable
fun EmptyState(title: String, body: String? = null, action: (@Composable () -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            color = LocalAppColors.current.label2,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.labelMedium,
                color = LocalAppColors.current.label3,
                textAlign = TextAlign.Center,
            )
        }
        action?.invoke()
    }
}

/** Centers content and caps its width on large screens. */
@Composable
fun Centered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize()) { content() }
    }
}

/**
 * Section headers are deliberately quiet: small, regular weight, secondary colour and no
 * uppercase. The only uppercase labels in the app are the 10-12sp micro-labels.
 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 22.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = LocalAppColors.current.label2,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** A flat card: height is the surface ramp, so there is no elevation and no border. */
@Composable
fun InfoCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(LocalAppColors.current.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
fun BackTopBar(
    title: String,
    onBack: () -> Unit,
    actions: List<BarAction> = emptyList(),
    primary: BarAction? = null,
    interceptBack: Boolean = false,
) {
    ScreenTopBar(title = title, onBack = onBack, actions = actions, primary = primary, interceptBack = interceptBack)
}

// ---------------------------------------------------------------------------- buttons --

enum class ButtonSize { Default, Sm, Xs }

private fun ButtonSize.shape() = when (this) {
    ButtonSize.Default -> RoundedCornerShape(12.dp)
    ButtonSize.Sm -> RoundedCornerShape(8.dp)
    ButtonSize.Xs -> RoundedCornerShape(7.dp)
}

private fun ButtonSize.padding() = when (this) {
    ButtonSize.Default -> PaddingValues(horizontal = 18.dp, vertical = 14.dp)
    ButtonSize.Sm -> PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ButtonSize.Xs -> PaddingValues(horizontal = 10.dp, vertical = 5.dp)
}

private fun ButtonSize.gap() = when (this) {
    ButtonSize.Default -> 7.dp
    ButtonSize.Sm -> 5.dp
    ButtonSize.Xs -> 4.dp
}

private fun ButtonSize.iconSize() = when (this) {
    ButtonSize.Default -> 19.dp
    ButtonSize.Sm -> 16.dp
    ButtonSize.Xs -> 14.dp
}

/**
 * The shared button body. A button fills its row at the default size and shrinks to its content at
 * [ButtonSize.Sm] and below; a press scales it rather than rippling.
 */
@Composable
private fun AppButton(
    text: String,
    onClick: () -> Unit,
    container: Color,
    contentColor: Color,
    modifier: Modifier,
    enabled: Boolean,
    loading: Boolean,
    size: ButtonSize,
    weight: FontWeight,
    icon: ImageVector?,
    trailingIcon: ImageVector?,
) {
    val interaction = rememberPressInteraction()
    val style = when (size) {
        ButtonSize.Default -> MaterialTheme.typography.bodyLarge
        ButtonSize.Sm -> MaterialTheme.typography.bodySmall
        ButtonSize.Xs -> MaterialTheme.typography.labelMedium
    }
    Row(
        modifier
            .then(if (size == ButtonSize.Default) Modifier.fillMaxWidth() else Modifier)
            .alpha(if (enabled && !loading) 1f else 0.32f)
            .pressScale(interaction)
            .clip(size.shape())
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick,
            )
            .padding(size.padding()),
        horizontalArrangement = Arrangement.spacedBy(size.gap(), Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(size.iconSize()), color = contentColor, strokeWidth = 2.dp)
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(size.iconSize()))
        }
        Text(text, style = style, fontWeight = weight, color = contentColor)
        if (trailingIcon != null) {
            Icon(trailingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(size.iconSize()))
        }
    }
}

/** Filled accent: the one call to action on a screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    size: ButtonSize = ButtonSize.Default,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    val colors = LocalAppColors.current
    AppButton(text, onClick, colors.acc, colors.onAcc, modifier, enabled, loading, size, FontWeight.SemiBold, icon, trailingIcon)
}

/** The neutral default: a raised control that is not the call to action. */
@Composable
fun NeutralButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    size: ButtonSize = ButtonSize.Default,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    val colors = LocalAppColors.current
    AppButton(text, onClick, colors.surface2, colors.label, modifier, enabled, loading, size, FontWeight.SemiBold, icon, trailingIcon)
}

/** Accent at 16% behind accent text: a secondary action that still reads as the accent. */
@Composable
fun TintedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    size: ButtonSize = ButtonSize.Default,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    val colors = LocalAppColors.current
    AppButton(text, onClick, colors.accSoft, colors.acc, modifier, enabled, loading, size, FontWeight.SemiBold, icon, trailingIcon)
}

/** No fill and regular weight: the quiet "see all" or "cancel" affordance. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    size: ButtonSize = ButtonSize.Default,
    dim: Boolean = false,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    val colors = LocalAppColors.current
    AppButton(
        text, onClick, Color.Transparent, if (dim) colors.label2 else colors.acc,
        modifier, enabled, loading, size, FontWeight.Normal, icon, trailingIcon,
    )
}

@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    size: ButtonSize = ButtonSize.Default,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    val colors = LocalAppColors.current
    AppButton(text, onClick, colors.red.copy(alpha = 0.15f), colors.red, modifier, enabled, loading, size, FontWeight.SemiBold, icon, trailingIcon)
}

/** A circular icon button on the raised surface; the painted size stays small, the target is 44dp. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val colors = LocalAppColors.current
    val interaction = rememberPressInteraction()
    Box(
        modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else 0.32f)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .pressScale(interaction, IconPressScale)
                .clip(CircleShape)
                .background(colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription, tint = tint ?: colors.label, modifier = Modifier.size(18.dp))
        }
    }
}

// ------------------------------------------------------------------------ chips & tags --

/** A horizontally scrolling strip of [Chip]s. */
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val interaction = rememberPressInteraction()
    Box(
        modifier
            .pressScale(interaction, TilePressScale)
            .clip(CircleShape)
            .background(if (selected) colors.acc else colors.surface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) colors.onAcc else colors.label,
            maxLines = 1,
        )
    }
}

/** A small metadata badge: muscle group, equipment, a personal best. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    val colors = LocalAppColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (accent) colors.accSoft else colors.surface2)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (accent) colors.acc else colors.label2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ------------------------------------------------------------------------- list rows --

/** The rounded tinted glyph square that opens a list row. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, tint: Color? = null, contentDescription: String? = null) {
    val colors = LocalAppColors.current
    Box(
        modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(tint ?: colors.acc),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(17.dp))
    }
}

/** Draws the hairline that separates a row from the one above it, inset past the icon rail. */
private fun Modifier.topHairline(color: Color, inset: Dp) = drawBehind {
    val start = inset.toPx()
    val thickness = HairlineWidth.toPx()
    drawLine(color, Offset(start, 0f), Offset(size.width, 0f), strokeWidth = thickness)
}

/**
 * A row in a grouped list. [showDivider] draws the hairline above the row, so a caller sets it on
 * every row but the first; [dividerInset] moves it past a leading icon rail.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = false,
    dividerInset: Dp = 14.dp,
    danger: Boolean = false,
) {
    val colors = LocalAppColors.current
    val interaction = rememberPressInteraction()
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .background(if (pressed && onClick != null) colors.surface2 else Color.Transparent)
            .then(if (showDivider) Modifier.topHairline(colors.sep, dividerInset) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .heightIn(min = 46.dp)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (danger) colors.red else colors.label,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = colors.label2)
            }
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.label2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

/** The `surface` block a group of [ListRow]s sits in. */
@Composable
fun GroupedList(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(LocalAppColors.current.surface),
    ) { content() }
}

// ----------------------------------------------------------------------------- fields --

@Composable
private fun filledFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = LocalAppColors.current.surface2,
    unfocusedContainerColor = LocalAppColors.current.surface2,
    disabledContainerColor = LocalAppColors.current.surface2,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
)

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = true,
    suffix: String? = null,
) {
    TextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }) },
        label = { Text(label) },
        singleLine = true,
        suffix = suffix?.let { { Text(it) } },
        shape = MaterialTheme.shapes.small,
        colors = filledFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
fun <T> DropdownField(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(colors.surface2)
                .clickable(indication = null, interactionSource = rememberPressInteraction()) { expanded = true }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = colors.label2)
                Text(optionLabel(selected), style = MaterialTheme.typography.bodyLarge, color = colors.label)
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = colors.label2)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String = "Delete", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalAppColors.current.bgEl,
        shape = MaterialTheme.shapes.large,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(text, style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.label2) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = LocalAppColors.current.label2) } },
    )
}

// ------------------------------------------------------------------------ stats & bars --

/** A stat tile: a quiet label row with a brighter glyph, over a large tabular value. */
@Composable
fun StatChip(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val colors = LocalAppColors.current
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(colors.surface).padding(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = colors.label, modifier = Modifier.size(14.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.label2)
        }
        Spacer(Modifier.height(5.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.label,
        )
    }
}

/** Progress of [current] against [target], e.g. planned kcal vs. target kcal. */
@Composable
fun TargetBar(
    label: String,
    current: Double,
    target: Double?,
    unit: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(
                if (target != null && target > 0) "${current.toInt()} / ${target.toInt()} $unit" else "${current.toInt()} $unit",
                style = MaterialTheme.typography.labelMedium,
                color = LocalAppColors.current.label2,
            )
        }
        if (target != null && target > 0) {
            LinearProgressIndicator(
                progress = { (current / target).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 4.dp).clip(CircleShape),
                color = color,
                trackColor = LocalChartColors.current.track,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

data class NavItem(val label: String, val icon: ImageVector)
