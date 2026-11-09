package com.programmersbox.claudemanager.data.db

import androidx.room3.AutoMigration
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Upsert
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.programmersbox.claudemanager.ClaudePaths
import com.programmersbox.claudemanager.data.settings.AppSettings
import com.programmersbox.claudemanager.data.settings.SettingsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlin.io.path.createDirectories

/** User-owned labels for a session: a custom name and a pin. */
@Entity(tableName = "session_label")
data class SessionLabelEntity(
    @PrimaryKey val sessionId: String,
    val customName: String? = null,
    val pinned: Boolean = false,
)

@Dao
interface SessionLabelDao {
    @Query("SELECT * FROM session_label")
    fun observeAll(): Flow<List<SessionLabelEntity>>

    @Query("SELECT * FROM session_label WHERE sessionId = :sessionId")
    suspend fun get(sessionId: String): SessionLabelEntity?

    @Upsert
    suspend fun upsert(label: SessionLabelEntity)
}

@Database(
    entities = [SessionLabelEntity::class, SessionRecordEntity::class, StatusSegmentEntity::class, AppSettings::class],
    version = 7,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7)],
)
abstract class ClaudeManagerDatabase : RoomDatabase() {
    abstract fun sessionLabelDao(): SessionLabelDao
    abstract fun historyDao(): HistoryDao
    abstract fun settingsDao(): SettingsDao
}

fun buildDatabase(paths: ClaudePaths): ClaudeManagerDatabase {
    paths.appDataDir.createDirectories()
    return Room.databaseBuilder(
        name = paths.appDataDir.resolve("claude-manager.db").toString(),
        factory = { ClaudeManagerDatabase_Impl() },
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

fun sessionLabelDao(database: ClaudeManagerDatabase): SessionLabelDao = database.sessionLabelDao()

fun historyDao(database: ClaudeManagerDatabase): HistoryDao = database.historyDao()

fun settingsDao(database: ClaudeManagerDatabase): SettingsDao = database.settingsDao()
