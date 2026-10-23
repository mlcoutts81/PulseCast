package com.example.data.models

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class PodcastTypeConverters {
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus =
        runCatching { DownloadStatus.valueOf(value) }.getOrDefault(DownloadStatus.NONE)
}

@Database(
    entities = [PodcastEntity::class, EpisodeEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(PodcastTypeConverters::class)
abstract class PulseCastDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao

    companion object {
        @Volatile
        private var INSTANCE: PulseCastDatabase? = null

        fun getInstance(context: Context): PulseCastDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PulseCastDatabase::class.java,
                    "pulsecast_db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
