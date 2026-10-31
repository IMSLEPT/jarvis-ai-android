package it.jarvis.assistant.memory

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import it.jarvis.assistant.core.Settings
import kotlinx.coroutines.flow.Flow

/** Fatto/preferenza duraturo scelto da te o salvato su tua richiesta (es. «ricorda che…»). Mai schermate intere. */
@Entity(tableName = "memory")
data class MemoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Storico conversazione (utente / Jarvis / azioni). */
@Entity(tableName = "turns")
data class Turn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long = System.currentTimeMillis(),
    val role: String, // user | jarvis | action
    val text: String,
)

@Dao
interface MemoryDao {
    @Insert suspend fun insert(m: MemoryItem): Long
    @Update suspend fun update(m: MemoryItem)
    @Query("DELETE FROM memory WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM memory") suspend fun clear()
    @Query("SELECT * FROM memory ORDER BY createdAt DESC") fun all(): Flow<List<MemoryItem>>
    @Query("SELECT * FROM memory WHERE text LIKE '%' || :w || '%' ORDER BY createdAt DESC LIMIT 8")
    suspend fun search(w: String): List<MemoryItem>
    @Query("SELECT * FROM memory ORDER BY createdAt DESC LIMIT :n") suspend fun recent(n: Int): List<MemoryItem>
}

@Dao
interface TurnDao {
    @Insert suspend fun insert(t: Turn)
    @Query("SELECT * FROM turns ORDER BY ts DESC LIMIT :n") suspend fun last(n: Int): List<Turn>
    @Query("SELECT * FROM turns ORDER BY ts DESC") fun all(): Flow<List<Turn>>
    @Query("DELETE FROM turns") suspend fun clear()
    @Query("DELETE FROM turns WHERE ts < :before") suspend fun purgeOlder(before: Long)
}

@Database(entities = [MemoryItem::class, Turn::class], version = 1, exportSchema = false)
abstract class JarvisDb : RoomDatabase() {
    abstract fun memory(): MemoryDao
    abstract fun turns(): TurnDao
    companion object {
        fun create(ctx: Context) = Room.databaseBuilder(ctx, JarvisDb::class.java, "jarvis.db").build()
    }
}

class MemoryRepository(private val db: JarvisDb, private val settings: Settings) {
    val items: Flow<List<MemoryItem>> get() = db.memory().all()
    val history: Flow<List<Turn>> get() = db.turns().all()

    suspend fun remember(text: String): Boolean {
        if (!settings.memoryEnabled) return false
        val t = text.trim().take(300)
        if (t.isBlank()) return false
        db.memory().insert(MemoryItem(text = t)); return true
    }
    suspend fun edit(item: MemoryItem, newText: String) = db.memory().update(item.copy(text = newText.trim().take(300)))
    suspend fun delete(id: Long) = db.memory().delete(id)
    suspend fun clearMemory() = db.memory().clear()
    suspend fun clearHistory() = db.turns().clear()

    suspend fun log(role: String, text: String) {
        if (!settings.memoryEnabled) return
        db.turns().insert(Turn(role = role, text = text.take(600)))
        db.turns().purgeOlder(System.currentTimeMillis() - 90L * 24 * 3600 * 1000)
    }

    suspend fun recentTurns(n: Int = 8): List<Turn> = if (settings.memoryEnabled) db.turns().last(n).reversed() else emptyList()

    /** Ricordi pertinenti alla richiesta (ricerca per parole) + ultimi inseriti. */
    suspend fun relevant(query: String): List<MemoryItem> {
        if (!settings.memoryEnabled) return emptyList()
        val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 4 }.distinct().take(8)
        val found = LinkedHashMap<Long, MemoryItem>()
        for (w in words) db.memory().search(w).forEach { found[it.id] = it }
        db.memory().recent(5).forEach { found.putIfAbsent(it.id, it) }
        return found.values.take(10)
    }
}
