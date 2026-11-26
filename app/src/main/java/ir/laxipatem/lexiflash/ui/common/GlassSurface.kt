package ir.laxipatem.lexiflash.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Fine, low-contrast outline used to separate translucent glass-like surfaces. */
@Composable
fun Modifier.glassOutline(shape: Shape = RoundedCornerShape(18.dp)): Modifier =
    border(
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
        shape
    )
