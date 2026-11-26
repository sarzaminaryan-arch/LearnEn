package ir.laxipatem.lexiflash.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.laxipatem.lexiflash.ui.theme.BrandColors

/**
 * Depth tokens for the raised surfaces used across the app.
 *
 * The look is a soft extrusion rather than a flat card: a lit top face, a
 * darker bottom edge peeking out, and a shadow underneath.
 */
object Depth {
    /** How far the bottom edge sits below the face. */
    val edge: Dp = 4.dp

    /** How far the whole card floats above the page. */
    val lift: Dp = 6.dp

    val corner: Dp = 18.dp

    /** The corner used inside the card, slightly tighter than the card itself. */
    val innerCorner: Dp = 12.dp
}

/** A card outline tuned for each mode: clear in light mode, softly luminous in dark mode. */
@Composable
fun appCardOutlineColor(enabled: Boolean = true): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) {
        BrandColors.GlowDark.copy(alpha = if (enabled) 0.72f else 0.28f)
    } else {
        BrandColors.CardOutlineLight
    }
}

/** Reusable border for cards that use Material 3's [androidx.compose.material3.Card]. */
@Composable
fun appCardBorder(enabled: Boolean = true, width: Dp = 1.dp): BorderStroke =
    BorderStroke(width, appCardOutlineColor(enabled))

/**
 * A raised card with a lit face, a coloured bottom edge and a soft shadow.
 *
 * The edge is drawn by placing the card face inside a coloured container that
 * is taller by [Depth.edge]; the container shows only at the bottom, which
 * reads as thickness. In dark mode a slim amber outline and tinted shadow create
 * the requested glow without changing any content or touch behaviour.
 *
 * When [onClick] is null the card is static. When it is set the face sinks onto
 * its edge while pressed, keeping the same physical feedback in both themes.
 */
@Composable
fun RaisedCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Depth.corner),
    faceColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    edgeColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    shadowColor: Color = MaterialTheme.colorScheme.outline,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val isPressed = onClick != null && pressed
    val sink = if (isPressed) Depth.edge else 0.dp
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val effectiveShadowColor = if (isDark) BrandColors.GlowDark else shadowColor
    val effectiveBorder = border ?: appCardBorder(enabled)

    Box(
        modifier = modifier
            .shadow(
                elevation = when {
                    isPressed -> 2.dp
                    isDark -> Depth.lift + 2.dp
                    else -> Depth.lift
                },
                shape = shape,
                ambientColor = effectiveShadowColor.copy(alpha = if (isDark) 0.14f else 0.18f),
                spotColor = effectiveShadowColor.copy(alpha = if (isDark) 0.25f else 0.28f)
            )
            .clip(shape)
            .background(if (enabled) edgeColor else edgeColor.copy(alpha = 0.5f))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        enabled = enabled,
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(bottom = Depth.edge)
    ) {
        Column(
            modifier = Modifier
                .offset(y = sink)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            faceColor,
                            // A whisper of the edge colour at the bottom of the
                            // face, so the surface curves into its own shadow.
                            faceColor.blendTowards(edgeColor, 0.16f)
                        )
                    )
                )
                .border(effectiveBorder, shape)
                .padding(contentPadding),
            content = content
        )
    }
}

/** Blends [this] towards [other] by [fraction], keeping the result opaque. */
private fun Color.blendTowards(other: Color, fraction: Float): Color = Color(
    red = red + (other.red - red) * fraction,
    green = green + (other.green - green) * fraction,
    blue = blue + (other.blue - blue) * fraction,
    alpha = 1f
)
