package com.eurowindow.cadviewer.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.eurowindow.cadviewer.data.dao.DrawingDao
import com.eurowindow.cadviewer.data.dao.MeasurementDao
import com.eurowindow.cadviewer.data.entity.DrawingMetadata
import com.eurowindow.cadviewer.data.entity.MeasurementSidecar

@Database(
    entities = [DrawingMetadata::class, MeasurementSidecar::class],
    version = 1,
    exportSchema = false
)
abstract class CadDatabase : RoomDatabase() {
    abstract fun drawingDao(): DrawingDao
    abstract fun measurementDao(): MeasurementDao

    companion object {
        @Volatile
        private var INSTANCE: CadDatabase? = null

        fun getInstance(context: Context): CadDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CadDatabase::class.java,
                    "eurowindow_cad_viewer.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
