package com.myagent.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.myagent.app.data.db.dao.ConversationDao
import com.myagent.app.data.db.dao.MessageDao
import com.myagent.app.data.db.dao.PresetDao
import com.myagent.app.data.db.dao.WorldBookDao
import com.myagent.app.data.db.entity.ConversationEntity
import com.myagent.app.data.db.entity.MessageEntity
import com.myagent.app.data.db.entity.ModelPresetEntity
import com.myagent.app.data.db.entity.WorldBookEntryEntity

/**
 * 应用本地数据库（Room）。
 *
 * 需在 Application 中调用 [init] 完成初始化，之后通过 [instance] 获取单例。
 * 已提供 1→2 的显式迁移（模型预设新增思考强度字段）；
 * 其余版本变化仍采用重建表策略（[RoomDatabase.Builder.fallbackToDestructiveMigration]）。
 */
@Database(
    entities = [
        ModelPresetEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        WorldBookEntryEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun presetDao(): PresetDao

    abstract fun conversationDao(): ConversationDao

    abstract fun messageDao(): MessageDao

    abstract fun worldBookDao(): WorldBookDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** 1 → 2：模型预设新增思考强度（reasoning_effort）字段。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE model_preset ADD COLUMN reasoning_effort TEXT")
            }
        }

        /** 使用 Application Context 创建（或复用）数据库单例。 */
        fun init(context: Context) {
            synchronized(this) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "myagent.db",
                    )
                        .addMigrations(MIGRATION_1_2)
                        .fallbackToDestructiveMigration(dropAllTables = true)
                        .build()
                }
            }
        }

        /** 已初始化的数据库单例；未初始化时抛出明确错误。 */
        val instance: AppDatabase
            get() = INSTANCE ?: error("AppDatabase 未初始化")
    }
}
