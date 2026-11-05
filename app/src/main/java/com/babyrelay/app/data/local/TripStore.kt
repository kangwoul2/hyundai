package com.babyrelay.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.babyrelay.app.domain.model.TripSession
import kotlinx.serialization.json.Json

/** 여정 저장소. 기기 안에만 저장하며 서버로 전송하지 않는다. */
interface TripStore {
    suspend fun save(session: TripSession)

    /** 종료되지 않은 가장 최근 여정. 앱 재시작 후 복구에 쓴다. */
    suspend fun loadActive(): TripSession?

    /** cutoff(ms) 이전에 갱신된 종료 여정을 삭제한다. 개인정보 보관 최소화. */
    suspend fun purgeClosedBefore(cutoff: Long)
}

object SessionCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(s: TripSession): String = json.encodeToString(TripSession.serializer(), s)

    fun decode(text: String): TripSession? = try {
        json.decodeFromString(TripSession.serializer(), text)
    } catch (t: Throwable) {
        null
    }
}

class InMemoryTripStore : TripStore {
    private val rows = linkedMapOf<String, String>()

    override suspend fun save(session: TripSession) {
        rows[session.id] = SessionCodec.encode(session)
    }

    override suspend fun loadActive(): TripSession? =
        rows.values.mapNotNull(SessionCodec::decode)
            .filter { it.state != com.babyrelay.app.domain.model.TripState.CLOSED }
            .maxByOrNull { it.updatedAt }

    override suspend fun purgeClosedBefore(cutoff: Long) {
        rows.entries.removeAll { (_, v) ->
            val s = SessionCodec.decode(v)
            s == null || (s.state == com.babyrelay.app.domain.model.TripState.CLOSED && s.updatedAt < cutoff)
        }
    }

    fun rowCount() = rows.size
}

@Entity(tableName = "trip")
data class TripRow(
    @PrimaryKey val id: String,
    val closed: Boolean,
    val updatedAt: Long,
    val json: String,
)

@Dao
interface TripDao {
    @Upsert
    suspend fun upsert(row: TripRow)

    @Query("SELECT * FROM trip WHERE closed = 0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestActive(): TripRow?

    @Query("DELETE FROM trip WHERE closed = 1 AND updatedAt < :cutoff")
    suspend fun purgeClosedBefore(cutoff: Long)

    @Query("DELETE FROM trip")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM trip")
    suspend fun count(): Int
}

@Database(entities = [TripRow::class], version = 1, exportSchema = false)
abstract class BabyRelayDb : RoomDatabase() {
    abstract fun tripDao(): TripDao
}

class RoomTripStore(private val dao: TripDao) : TripStore {
    override suspend fun save(session: TripSession) {
        dao.upsert(
            TripRow(
                id = session.id,
                closed = session.state == com.babyrelay.app.domain.model.TripState.CLOSED,
                updatedAt = session.updatedAt,
                json = SessionCodec.encode(session),
            ),
        )
    }

    override suspend fun loadActive(): TripSession? = dao.latestActive()?.let { SessionCodec.decode(it.json) }

    override suspend fun purgeClosedBefore(cutoff: Long) = dao.purgeClosedBefore(cutoff)
}
