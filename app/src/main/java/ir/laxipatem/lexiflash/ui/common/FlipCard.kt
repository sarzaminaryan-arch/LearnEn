package ir.laxipatem.lexiflash.ui.common

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
        animationSpec = if (animationsEnabled) {
            tween(durationMillis = 460, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 0)
        },
        label = "cardFlip"
    )
    val scale by animateFloatAsState(
        targetValue = if (animationsEnabled && flipped) 0.985f else 1f,
        animationSpec = if (animationsEnabled) {
            tween(durationMillis = 460, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 0)
        },
        label = "cardFlipScale"
    )
    val cardShape = RoundedCornerShape(24.dp)

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
                scaleX = scale
                scaleY = scale
                cameraDistance = 12f * density.density * density.density
            }
            .clip(cardShape)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
                cardShape
            )
            .clickable(onClick = onFlip)
            .semantics { contentDescription = label },
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
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
    speaking: Boolean = false,
    modifier: Modifier = Modifier
) {
    val sentenceColor by animateColorAsState(
        targetValue = if (speaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 240),
        label = "spokenSentenceColor"
    )
    val highlightColor by animateColorAsState(
        targetValue = if (speaking) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 240),
        label = "spokenSentenceHighlight"
    )
    val sentenceShape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = english,
            modifier = Modifier
                .clip(sentenceShape)
                .background(highlightColor)
                .border(
                    BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = if (speaking) 0.42f else 0f)
                    ),
                    sentenceShape
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = FlashcardSentenceStyle,
            color = sentenceColor
        )
    }
}

/** Translation and keyword meanings shown on the back of a card. */
@Composable
fun CardBack(
    persian: String?,
    vocab: List<Pair<String, String?>>,
    note: String?,
    pendingTranslationLabel: String,
    onSpeak: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (persian.isNullOrBlank()) {
            Text(
                text = pendingTranslationLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = persian,
                style = FlashcardTranslationStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (vocab.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.study_section_vocab),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                vocab.forEach { (word, meaning) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.56f))
                            .border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = word,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (!meaning.isNullOrBlank()) {
                            Text(
                                text = meaning,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        if (!note.isNullOrBlank()) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        onSpeak?.let { speak -> speak() }
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
