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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Depth tokens for the raised surfaces used across the app.
 *
 * The look is a soft extrusion rather than a flat card: a lit top face, a
 * darker bottom edge peeking out, and a warm shadow underneath. Two numbers
 * control it everywhere, so every raised element in the app agrees.
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

/**
 * A glass-inspired card with a translucent lit face, a fine outline, a coloured
 * lower edge and a soft shadow. The layered tint keeps text crisp while giving
 * the surface a frosted-glass impression without expensive backdrop blur.
 *
 * The edge is drawn by placing the card face inside a coloured container that
 * is taller by [Depth.edge]; the container shows only at the bottom, which
 * reads as thickness. That is cheaper and sharper than a real 3D transform,
 * and it survives RTL layout and font scaling unchanged.
 *
 * When [onClick] is null the card is static. When it is set the card presses
 * in: the face drops onto its edge, so the whole control feels physical
 * without any animation library.
 */
@Composable
fun RaisedCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Depth.corner),
    faceColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
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
    val glassBorder = border ?: BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = if (isPressed) 2.dp else Depth.lift,
                shape = shape,
                ambientColor = shadowColor.copy(alpha = 0.30f),
                spotColor = shadowColor.copy(alpha = 0.45f)
            )
            .clip(shape)
            .background(if (enabled) edgeColor.copy(alpha = 0.76f) else edgeColor.copy(alpha = 0.36f))
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
                            faceColor.copy(alpha = 0.88f),
                            // Let a little of the page tint show through at the
                            // lower edge for a frosted, layered glass surface.
                            faceColor.blendTowards(edgeColor, 0.16f).copy(alpha = 0.72f)
                        )
                    )
                )
                .border(glassBorder, shape)
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
