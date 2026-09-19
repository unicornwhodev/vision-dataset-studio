package com.unicornwhodev.visiondatasetstudio.data.db

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.unicornwhodev.visiondatasetstudio.data.model.SourceEntryEntity
import com.unicornwhodev.visiondatasetstudio.data.model.ModelProfileEntity
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.unicornwhodev.visiondatasetstudio.data.model.AnnotationRecord
import com.unicornwhodev.visiondatasetstudio.data.model.AuditLogEntity
import com.unicornwhodev.visiondatasetstudio.data.model.BatchEntity
import com.unicornwhodev.visiondatasetstudio.data.model.ProjectEntity
import com.unicornwhodev.visiondatasetstudio.data.model.SampleEntity

@Database(
    entities = [
        ProjectEntity::class,
        BatchEntity::class,
        SampleEntity::class,
        AnnotationRecord::class,
        AuditLogEntity::class,
        SourceEntryEntity::class,
        ModelProfileEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun batchDao(): BatchDao
    abstract fun sampleDao(): SampleDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun auditDao(): AuditDao
    abstract fun sourceEntryDao(): SourceEntryDao
    abstract fun modelProfileDao(): ModelProfileDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db:SupportSQLiteDatabase) { SchemaMigrations.from1to2.forEach { db.execSQL(it) } }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db:SupportSQLiteDatabase) { SchemaMigrations.from2to3.forEach { db.execSQL(it) } }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vision_dataset_studio.db"
                )

                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
