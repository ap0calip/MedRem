package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AppDao
import com.example.data.entity.DoseRecord
import com.example.data.entity.FamilyMember
import com.example.data.entity.Medication

@Database(
    entities = [FamilyMember::class, Medication::class, DoseRecord::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE medications ADD COLUMN autoReset INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    // Ignore if column already exists
                }
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE family_members ADD COLUMN soundUri TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE medications ADD COLUMN soundUri TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    // Ignore if column already exists
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medrem_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
