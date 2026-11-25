package ir.laxipatem.lexiflash.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A deck of flashcards: either the bundled core deck or an imported one. */
@Entity(
    tableName = "decks",
    indices = [Index(value = ["key"], unique = true)]
)
data class DeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** Stable identifier: "core" for the bundled deck, otherwise a uuid. */
    val key: String,
    val title: String,
    val sourceLang: String,
    val targetLang: String,
    val license: String,
    val version: Int,
    val partCount: Int,
    val isCore: Boolean,
    val createdAt: Long
)

/** One sentence card belonging to a deck part. */
@Entity(
    tableName = "cards",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["deckId", "partIndex"]),
        Index(value = ["deckId"])
    ]
)
data class CardEntity(
    @PrimaryKey val id: String,
    val deckId: Long,
    val partIndex: Int,
    val orderInPart: Int,
    val startMs: Long?,
    val english: String,
    val persian: String?,
    val ipa: String?,
    /** Serialised `[{word, meaning?}]` written with the in-repo JSON writer. */
    val vocabJson: String,
    val note: String?,
    val difficulty: Double?,
    val reviewed: Boolean,
    val source: String?,
    val license: String?,
    /** True when the card still needs a human or machine translation. */
    val translatePending: Boolean
)

/** FSRS memory state for one card. Absent until the card is answered once. */
@Entity(
    tableName = "card_states",
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["dueAt"])]
)
data class CardStateEntity(
    @PrimaryKey val cardId: String,
    val stability: Double,
    val difficulty: Double,
    /** 0 new, 1 learning, 2 review, 3 relearning/forgotten. */
    val state: Int,
    val lastReviewAt: Long?,
    val dueAt: Long,
    val reps: Int,
    val lapses: Int,
    val seen: Boolean
)

/** Immutable log of every rating, used for statistics and auditing. */
@Entity(
    tableName = "review_logs",
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["reviewedAt"]), Index(value = ["cardId"])]
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val cardId: String,
    /** 1 Again, 2 Hard, 3 Good, 4 Easy. */
    val rating: Int,
    val reviewedAt: Long,
    val elapsedDays: Double,
    val scheduledDays: Double,
    val stateBefore: Int
)

/** One completed quiz attempt. */
@Entity(
    tableName = "quiz_results",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["deckId", "partIndex"])]
)
data class QuizResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val deckId: Long,
    val partIndex: Int,
    val score: Int,
    val total: Int,
    val takenAt: Long
)
