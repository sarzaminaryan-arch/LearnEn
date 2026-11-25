package ir.laxipatem.lexiflash.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Local store for every deck, card, review and quiz result.
 *
 * Schema export is enabled and the resulting JSON is committed under
 * `app/schemas`, which is what makes a future migration reviewable. There is
 * exactly one schema version so far; migrations will be added with `Migration`
 * objects rather than destructive fallbacks.
 */
@Database(
    entities = [
        DeckEntity::class,
        CardEntity::class,
        CardStateEntity::class,
        ReviewLogEntity::class,
        QuizResultEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun deckDao(): DeckDao

    abstract fun reviewDao(): ReviewDao

    abstract fun quizDao(): QuizDao

    companion object {
        const val DATABASE_NAME = "lexiflash.db"

        fun build(context: Context, inMemory: Boolean = false): AppDatabase {
            val builder = if (inMemory) {
                Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            } else {
                Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            }
            return builder
                .allowMainThreadQueries()
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        }
    }
}
