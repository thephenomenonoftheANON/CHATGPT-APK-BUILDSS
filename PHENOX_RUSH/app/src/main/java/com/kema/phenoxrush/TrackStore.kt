package com.kema.phenoxrush

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sessions")
data class WalkSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val distanceMeters: Double = 0.0,
    val durationMillis: Long = 0L
)

@Entity(tableName = "points", indices = [Index("sessionId")])
data class TrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float,
    val timestamp: Long
)

@Dao
interface TrackDao {
    @Insert suspend fun addSession(session: WalkSession): Long
    @Update suspend fun updateSession(session: WalkSession)
    @Insert suspend fun addPoint(point: TrackPoint)
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC") fun sessions(): Flow<List<WalkSession>>
    @Query("SELECT * FROM points WHERE sessionId=:id ORDER BY timestamp") suspend fun points(id: Long): List<TrackPoint>
    @Query("SELECT * FROM sessions WHERE id=:id") suspend fun session(id: Long): WalkSession?
}

@Database(entities = [WalkSession::class, TrackPoint::class], version = 1, exportSchema = true)
abstract class TrackDatabase : RoomDatabase() {
    abstract fun dao(): TrackDao
    companion object {
        @Volatile private var INSTANCE: TrackDatabase? = null
        fun get(context: Context): TrackDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, TrackDatabase::class.java, "phenox-rush.db")
                .build().also { INSTANCE = it }
        }
    }
}
