package com.wahyuzero.replyforge.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wahyuzero.replyforge.data.model.AiProvider
import com.wahyuzero.replyforge.data.model.AiProviderType
import com.wahyuzero.replyforge.data.model.AiUsage
import com.wahyuzero.replyforge.data.model.ContactFilter
import com.wahyuzero.replyforge.data.model.ConversationMessage
import com.wahyuzero.replyforge.data.model.Holiday
import com.wahyuzero.replyforge.data.model.MessageRole
// RateLimitEntry is in same package (data.db)
import com.wahyuzero.replyforge.data.model.ReplyHistory
import com.wahyuzero.replyforge.data.model.ResponseMode
import com.wahyuzero.replyforge.data.model.Rule
import com.wahyuzero.replyforge.ui.rule.MatchType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Converters {

    @androidx.room.TypeConverter
    fun fromMatchType(matchType: MatchType): String {
        return matchType.name
    }

    @androidx.room.TypeConverter
    fun toMatchType(value: String): MatchType {
        return try {
            MatchType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            MatchType.CONTAINS
        }
    }

    @androidx.room.TypeConverter
    fun fromContactFilter(filter: ContactFilter): String {
        return filter.name
    }

    @androidx.room.TypeConverter
    fun toContactFilter(value: String): ContactFilter {
        return try {
            ContactFilter.valueOf(value)
        } catch (e: IllegalArgumentException) {
            ContactFilter.ALL
        }
    }

    @androidx.room.TypeConverter
    fun fromResponseMode(mode: ResponseMode): String {
        return mode.name
    }

    @androidx.room.TypeConverter
    fun toResponseMode(value: String): ResponseMode {
        return try {
            ResponseMode.valueOf(value)
        } catch (e: IllegalArgumentException) {
            ResponseMode.SINGLE
        }
    }

    // Phase 4: AI Provider type converter
    @androidx.room.TypeConverter
    fun fromAiProviderType(type: AiProviderType): String {
        return type.name
    }

    @androidx.room.TypeConverter
    fun toAiProviderType(value: String): AiProviderType {
        return try {
            AiProviderType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            AiProviderType.OPENAI
        }
    }

    // Phase 4: MessageRole type converter
    @androidx.room.TypeConverter
    fun fromMessageRole(role: MessageRole): String {
        return role.name
    }

    @androidx.room.TypeConverter
    fun toMessageRole(value: String): MessageRole {
        return try {
            MessageRole.valueOf(value)
        } catch (e: IllegalArgumentException) {
            MessageRole.USER
        }
    }
}

val HOLIDAY_DATA = listOf(
    Triple("Tahun Baru 2026", "2026-01-01", true),
    Triple("Idul Fitri 1447 H", "2026-03-30", false),
    Triple("Idul Fitri 1447 H (Day 2)", "2026-03-31", false),
    Triple("Hari Buruh Internasional", "2026-05-01", true),
    Triple("Hari Waisak 2570 BE", "2026-05-12", false),
    Triple("Hari Lahir Pancasila", "2026-06-01", true),
    Triple("Idul Adha 1447 H", "2026-06-06", false),
    Triple("Kemerdekaan RI", "2026-08-17", true),
    Triple("Maulid Nabi Muhammad SAW", "2026-09-05", false),
    Triple("Hari Natal", "2026-12-25", true),
    Triple("Tahun Baru 2027", "2026-12-31", false)
)

private fun insertHolidays(db: SupportSQLiteDatabase) {
    for ((name, date, recurring) in HOLIDAY_DATA) {
        db.execSQL(
            "INSERT OR IGNORE INTO holidays (name, date, isRecurringAnnual) VALUES (?, ?, ?)",
            arrayOf(name, date, if (recurring) 1 else 0)
        )
    }
}

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE rules ADD COLUMN responseMode TEXT NOT NULL DEFAULT 'SINGLE'")
        db.execSQL("ALTER TABLE rules ADD COLUMN sequentialIndex INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE rules ADD COLUMN ignorePattern TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE rules ADD COLUMN ignoreGroups INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE rules ADD COLUMN ignoreIndividuals INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add new columns to rules table
        db.execSQL("ALTER TABLE rules ADD COLUMN startTime TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE rules ADD COLUMN endTime TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE rules ADD COLUMN activeDays TEXT NOT NULL DEFAULT '1,2,3,4,5,6,7'")
        db.execSQL("ALTER TABLE rules ADD COLUMN minDelaySeconds INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE rules ADD COLUMN maxRepliesPerContact INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE rules ADD COLUMN ignoreHolidays INTEGER NOT NULL DEFAULT 0")

        // Create holidays table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS holidays (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                date TEXT NOT NULL,
                isRecurringAnnual INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_holidays_date ON holidays(date)")

        // Create rate_limits table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS rate_limits (
                ruleId INTEGER NOT NULL,
                contactName TEXT NOT NULL,
                lastReplyTime INTEGER NOT NULL DEFAULT 0,
                replyCountToday INTEGER NOT NULL DEFAULT 0,
                lastResetDate TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(ruleId, contactName)
            )
        """)

        // Pre-populate Indonesian national holidays 2026
        insertHolidays(db)
    }
}

val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Phase 4: Add AI columns to rules table
        db.execSQL("ALTER TABLE rules ADD COLUMN useAi INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE rules ADD COLUMN aiProviderId INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE rules ADD COLUMN systemPrompt TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE rules ADD COLUMN aiTemperature REAL DEFAULT NULL")

        // Create ai_providers table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS ai_providers (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                type TEXT NOT NULL DEFAULT 'OPENAI',
                baseUrl TEXT NOT NULL,
                apiKey TEXT NOT NULL,
                modelName TEXT NOT NULL,
                isActive INTEGER NOT NULL DEFAULT 1,
                maxTokens INTEGER NOT NULL DEFAULT 1024,
                temperature REAL NOT NULL DEFAULT 0.7
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_providers_isActive ON ai_providers(isActive)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_providers_type ON ai_providers(type)")

        // Create conversation_messages table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS conversation_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                contactName TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                timestamp INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_messages_contactName ON conversation_messages(contactName)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_messages_timestamp ON conversation_messages(timestamp)")

        // Create ai_usage table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS ai_usage (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                date TEXT NOT NULL,
                providerId INTEGER NOT NULL,
                promptTokens INTEGER NOT NULL DEFAULT 0,
                completionTokens INTEGER NOT NULL DEFAULT 0,
                totalTokens INTEGER NOT NULL DEFAULT 0,
                estimatedCost REAL NOT NULL DEFAULT 0.0
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_usage_date ON ai_usage(date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_usage_providerId ON ai_usage(providerId)")
    }
}

val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Reply system fields
        db.execSQL("ALTER TABLE rules ADD COLUMN replyDelayMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE rules ADD COLUMN replyHeader TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE rules ADD COLUMN replyFooter TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE rules ADD COLUMN replyPrefix TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE rules ADD COLUMN probability INTEGER NOT NULL DEFAULT 100")
        db.execSQL("ALTER TABLE rules ADD COLUMN lineBreaks INTEGER NOT NULL DEFAULT 1")

        // Contacts/Groups fields
        db.execSQL("ALTER TABLE rules ADD COLUMN specificContacts TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE rules ADD COLUMN specificGroups TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE rules ADD COLUMN receiverType INTEGER NOT NULL DEFAULT 0")

        // Pattern matching fields
        db.execSQL("ALTER TABLE rules ADD COLUMN caseInsensitive INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE rules ADD COLUMN ignoreAccents INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE rules ADD COLUMN similarityThreshold INTEGER NOT NULL DEFAULT 0")

        // Rate limiting fields
        db.execSQL("ALTER TABLE rules ADD COLUMN dailyReplyLimit INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE rules ADD COLUMN preventRepeatingMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE rules ADD COLUMN prevRuleTimeoutMs INTEGER NOT NULL DEFAULT 0")

        // Reply history: add processTimeMs column
        db.execSQL("ALTER TABLE reply_history ADD COLUMN processTimeMs INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [
        Rule::class,
        ReplyHistory::class,
        Holiday::class,
        RateLimitEntry::class,
        AiProvider::class,
        ConversationMessage::class,
        AiUsage::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ruleDao(): RuleDao
    abstract fun historyDao(): HistoryDao
    abstract fun holidayDao(): HolidayDao
    abstract fun rateLimitDao(): RateLimitDao
    abstract fun aiProviderDao(): AiProviderDao
    abstract fun conversationDao(): ConversationDao
    abstract fun aiUsageDao(): AiUsageDao

    companion object {
        const val DATABASE_NAME = "replyforge_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Pre-populate holidays on fresh install
                            insertHolidays(db)
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
