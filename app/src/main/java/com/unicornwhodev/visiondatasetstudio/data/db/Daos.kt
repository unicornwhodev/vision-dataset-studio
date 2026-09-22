package com.unicornwhodev.visiondatasetstudio.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.unicornwhodev.visiondatasetstudio.data.model.SourceEntryEntity
import com.unicornwhodev.visiondatasetstudio.data.model.ModelProfileEntity
import com.unicornwhodev.visiondatasetstudio.data.model.AnnotationRecord
import com.unicornwhodev.visiondatasetstudio.data.model.AuditLogEntity
import com.unicornwhodev.visiondatasetstudio.data.model.BatchEntity
import com.unicornwhodev.visiondatasetstudio.data.model.ProjectEntity
import com.unicornwhodev.visiondatasetstudio.data.model.SampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getProjects(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM projects")
    suspend fun getProjectsSync(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    fun getProject(id: Long = 1L): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectSync(id: Long = 1L): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id:Long)
}

@Dao
interface BatchDao {
    @Query("SELECT * FROM batches WHERE projectId = :projectId ORDER BY batchNumber ASC")
    fun getBatches(projectId: Long = 1L): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batches WHERE projectId = :projectId AND batchNumber = :batchNumber LIMIT 1")
    suspend fun getBatchSync(projectId: Long, batchNumber: Int): BatchEntity?

    @Query("SELECT * FROM batches WHERE projectId = :projectId ORDER BY batchNumber DESC LIMIT 1")
    suspend fun getLatestBatchSync(projectId: Long = 1L): BatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(batch: BatchEntity)

    @Update
    suspend fun updateBatch(batch: BatchEntity)

    @Query("UPDATE batches SET status = :status, updatedAt = :updatedAt WHERE projectId = :projectId AND batchNumber = :batchNumber")
    suspend fun updateStatus(projectId: Long, batchNumber: Int, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM batches WHERE projectId=:projectId AND batchNumber=:batchNumber")
    suspend fun deleteBatch(projectId:Long,batchNumber:Int)
    @Query("DELETE FROM batches WHERE projectId=:projectId")
    suspend fun deleteProjectBatches(projectId:Long)
}

@Dao
interface SampleDao {
    @Query("SELECT * FROM samples WHERE projectId = :projectId AND batchNumber = :batchNumber ORDER BY COALESCE(sourceOrdinal, sourceRowIndex) ASC")
    fun getSamplesForBatch(projectId: Long, batchNumber: Int): Flow<List<SampleEntity>>

    @Query("SELECT * FROM samples WHERE projectId = :projectId AND batchNumber = :batchNumber ORDER BY COALESCE(sourceOrdinal, sourceRowIndex) ASC")
    suspend fun getSamplesForBatchSync(projectId: Long, batchNumber: Int): List<SampleEntity>

    @Query("SELECT * FROM samples WHERE sampleId = :sampleId LIMIT 1")
    fun getSample(sampleId: String): Flow<SampleEntity?>

    @Query("SELECT * FROM samples WHERE sampleId = :sampleId LIMIT 1")
    suspend fun getSampleSync(sampleId: String): SampleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSamples(samples: List<SampleEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNewSamples(samples: List<SampleEntity>)

    @Update
    suspend fun updateSample(sample: SampleEntity)

    @Query("SELECT COUNT(*) FROM samples WHERE projectId = :projectId AND batchNumber = :batchNumber AND annotationStatus = :status")
    suspend fun countByAnnotationStatus(projectId: Long, batchNumber: Int, status: String): Int

    @Query("SELECT COUNT(*) FROM samples WHERE projectId = :projectId AND batchNumber = :batchNumber")
    suspend fun countBatchSamples(projectId: Long, batchNumber: Int): Int

    @Query("SELECT * FROM samples WHERE projectId = :projectId AND annotationStatus = 'DEFERRED'")
    suspend fun getDeferredSamples(projectId: Long = 1L): List<SampleEntity>

    @Query("SELECT * FROM samples WHERE projectId = :projectId")
    suspend fun getAllSamples(projectId: Long = 1L): List<SampleEntity>

    @Query("DELETE FROM samples WHERE projectId=:projectId AND batchNumber=:batchNumber")
    suspend fun deleteBatchSamples(projectId:Long,batchNumber:Int)
    @Query("DELETE FROM samples WHERE projectId=:projectId")
    suspend fun deleteProjectSamples(projectId:Long)
}

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE sampleId = :sampleId LIMIT 1")
    fun getAnnotation(sampleId: String): Flow<AnnotationRecord?>

    @Query("SELECT * FROM annotations WHERE sampleId = :sampleId LIMIT 1")
    suspend fun getAnnotationSync(sampleId: String): AnnotationRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(record: AnnotationRecord)

    @Query("DELETE FROM annotations WHERE sampleId = :sampleId")
    suspend fun delete(sampleId: String)

    @Query("DELETE FROM annotations WHERE sampleId IN (SELECT sampleId FROM samples WHERE projectId=:projectId AND batchNumber=:batchNumber)")
    suspend fun deleteBatchAnnotations(projectId:Long,batchNumber:Int)
    @Query("DELETE FROM annotations WHERE sampleId IN (SELECT sampleId FROM samples WHERE projectId=:projectId)")
    suspend fun deleteProjectAnnotations(projectId:Long)
}

@Dao
interface AuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLogEntity)

    @Query("SELECT * FROM audit_logs WHERE projectId = :projectId AND batchNumber = :batchNumber ORDER BY timestamp DESC")
    fun getLogsForBatch(batchNumber: Int, projectId: Long = 1L): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE projectId = :projectId ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogs(projectId: Long = 1L): Flow<List<AuditLogEntity>>

    @Query("DELETE FROM audit_logs WHERE projectId=:projectId AND batchNumber=:batchNumber")
    suspend fun deleteBatchLogs(projectId:Long,batchNumber:Int)
    @Query("DELETE FROM audit_logs WHERE projectId=:projectId")
    suspend fun deleteProjectLogs(projectId:Long)
}

@Dao
interface SourceEntryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rows: List<SourceEntryEntity>)
    @Query("SELECT * FROM source_entries WHERE projectId = :projectId AND ordinal >= :offset ORDER BY ordinal LIMIT :limit")
    suspend fun page(projectId: Long, offset: Long, limit: Int): List<SourceEntryEntity>
    @Query("DELETE FROM source_entries WHERE projectId = :projectId")
    suspend fun clear(projectId: Long)
    @Query("SELECT COUNT(*) FROM source_entries WHERE projectId = :projectId")
    suspend fun count(projectId: Long): Long
}
@Dao
interface ModelProfileDao {
    @Query("SELECT * FROM model_profiles ORDER BY createdAt DESC")
    fun observe(): Flow<List<ModelProfileEntity>>
    @Query("SELECT * FROM model_profiles")
    suspend fun getAllSync(): List<ModelProfileEntity>
    @Query("SELECT * FROM model_profiles WHERE id = :id")
    suspend fun get(id: String): ModelProfileEntity?
    @Query("SELECT * FROM model_profiles WHERE modelPath = :path LIMIT 1")
    suspend fun getByPath(path:String): ModelProfileEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(profile: ModelProfileEntity)
    @Query("DELETE FROM model_profiles WHERE id = :id")
    suspend fun delete(id:String)
}

@Dao
interface ImageIdentityDao {
    @Query("SELECT * FROM image_identities WHERE projectId=:projectId AND kind=:kind AND digest=:digest")
    suspend fun owner(projectId:Long, kind:String, digest:String): com.unicornwhodev.visiondatasetstudio.data.model.ImageIdentityEntity?
    @Insert(onConflict=OnConflictStrategy.ABORT)
    suspend fun insert(identity:com.unicornwhodev.visiondatasetstudio.data.model.ImageIdentityEntity)
    @Query("DELETE FROM image_identities WHERE projectId=:projectId")
    suspend fun deleteProject(projectId:Long)
    @Query("DELETE FROM image_identities WHERE projectId=:projectId AND firstBatchNumber=:batchNumber")
    suspend fun deleteBatch(projectId:Long,batchNumber:Int)
}
