package com.programmersbox.claudemanager.data.settings

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Upsert
import com.programmersbox.claudemanager.AppScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The user's preferences; a single row with id 1. */
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    /** Show estimated API-equivalent cost next to token counts. */
    val showCost: Boolean = false,
    val notifyWaiting: Boolean = true,
    val notifyFinished: Boolean = true,
    val notifyOrphanShells: Boolean = true,
    /** A finished turn shorter than this does not notify. */
    val minTurnSeconds: Int = 20,
    /** Status history older than this is deleted at startup. */
    val historyDays: Int = 7,
)

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observe(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun get(): AppSettings?

    @Upsert
    suspend fun upsert(settings: AppSettings)
}

class SettingsRepository(private val dao: SettingsDao, scope: AppScope) {
    val settings: StateFlow<AppSettings> =
        dao.observe().map { it ?: AppSettings() }.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    /** Current settings straight from the database, for one-off reads at startup. */
    suspend fun current(): AppSettings = dao.get() ?: AppSettings()

    suspend fun update(change: (AppSettings) -> AppSettings) {
        dao.upsert(change(current()))
    }
}
