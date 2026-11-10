package com.programmersbox.claudemanager.data.settings

import androidx.room3.ColumnInfo
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
    /** "system", "light" or "dark". */
    @ColumnInfo(defaultValue = "system") val themeMode: String = "system",
    /** A [com.programmersbox.claudemanager.ui.theme.Palette] id. */
    @ColumnInfo(defaultValue = "harbor") val themePalette: String = "harbor",
    /** How often sessions, shells and transcripts are re-read. */
    @ColumnInfo(defaultValue = "2") val refreshSeconds: Int = 2,
    /** Check GitHub Releases for a new version at startup and every 6 hours. */
    @ColumnInfo(defaultValue = "1") val autoCheckUpdates: Boolean = true,
    /** Session view layout: sessions rail and details panel widths in dp, and whether details is open. */
    @ColumnInfo(defaultValue = "220") val railWidthDp: Int = 220,
    @ColumnInfo(defaultValue = "340") val inspectorWidthDp: Int = 340,
    @ColumnInfo(defaultValue = "1") val inspectorOpen: Boolean = true,
    /** How a status dot shows background work: "orbit" (spinning ring) or "satellites" (one orbiting dot per child). */
    @ColumnInfo(defaultValue = "orbit") val backgroundIndicator: String = "orbit",
    /** Status dot diameter in dp. */
    @ColumnInfo(defaultValue = "8") val statusDotSizeDp: Int = 8,
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
