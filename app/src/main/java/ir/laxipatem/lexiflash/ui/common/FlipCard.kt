package ir.laxipatem.lexiflash.ui.common

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import ir.laxipatem.lexiflash.R
import ir.laxipatem.lexiflash.ui.theme.FlashcardSentenceStyle
import ir.laxipatem.lexiflash.ui.theme.FlashcardTranslationStyle

/**
 * Three-dimensional flip card used by both the study screen and the review queue.
 *
 * Accessibility and motion
 * ------------------------
 * * When the system animator duration scale is zero (the user disabled
 *   animations), the card swaps content instantly instead of rotating.
 * * The card is a single clickable node with a stable content description, so it
 *   works with TalkBack without exposing the half-rotated back face.
 * * Text scrolls inside the card, so a 200% font scale cannot clip a long
 *   sentence.
 */
@Composable
fun FlipCard(
    front: @Composable () -> Unit,
    back: @Composable () -> Unit,
    flipped: Boolean,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val animationsEnabled = rememberAnimationsEnabled()
    val density = LocalDensity.current

    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = if (animationsEnabled) tween(durationMillis = 400) else tween(durationMillis = 0),
        label = "cardFlip"
    )

    // Only rotate when animations are allowed; otherwise show the plain state.
    val effectiveRotation = if (animationsEnabled) rotation else if (flipped) 180f else 0f
    val showBack = effectiveRotation > 90f
    val label = stringResource(R.string.cd_flip_card)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp)
            .graphicsLayer {
                rotationY = effectiveRotation
                cameraDistance = 12f * density.density * density.density
            }
            .clickable(onClick = onFlip)
            .semantics { contentDescription = label },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = Depth.lift),
        border = appCardBorder()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            if (showBack) {
                // Mirror the back face so its text is not horizontally reversed
                // by the parent's 180-degree rotation.
                Box(modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                    back()
                }
            } else {
                front()
            }
        }
    }

    // Keeps the compiler honest about unused context on some configurations.
    context.packageName
}

/** Sentence shown on the front of a card. */
@Composable
fun CardFront(
    english: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = english,
            modifier = Modifier.fillMaxWidth(),
            style = FlashcardSentenceStyle.copy(textDirection = TextDirection.Ltr),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

/** Translation and optional vocabulary shown on the back of a card. */
@Composable
fun CardBack(
    persian: String?,
    vocab: List<Pair<String, String?>>,
    note: String?,
    pendingTranslationLabel: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (persian.isNullOrBlank()) {
            Text(
                text = pendingTranslationLabel,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleSmall.copy(
                    textDirection = TextDirection.ContentOrRtl
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = persian,
                modifier = Modifier.fillMaxWidth(),
                style = FlashcardTranslationStyle.copy(
                    textDirection = TextDirection.ContentOrRtl
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }

        if (vocab.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.study_section_vocab),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                vocab.forEach { (word, meaning) ->
                    Text(
                        text = if (meaning.isNullOrBlank()) word else "$word — $meaning",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDirection = TextDirection.Ltr
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (!note.isNullOrBlank()) {
            Text(
                text = note,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall.copy(
                    textDirection = TextDirection.ContentOrRtl
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * True unless the user asked the platform to skip animations.
 *
 * `ANIMATOR_DURATION_SCALE` is read from the global settings table, which is the
 * documented signal for "remove animations" in Accessibility settings.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        scale > 0.01f
    }
}
