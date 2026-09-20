package com.unicornwhodev.visiondatasetstudio.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: Long = 1L,
    val name: String = "Vision Dataset Studio Project",
    val hfSourceRepo: String = "",
    val hfDestRepo: String = "",
    val sourceConfig: String = "default",
    val sourceSplit: String = "train",
    val imageColumn: String = "image",
    val idColumn: String = "id",
    val targetSplit: String = "train",
    val activeTasksCsv: String = "POINTING,DETECTION,CAPTIONING,CLASSIFICATION,VQA",
    val classesCsv: String = "person,car,dog,cat,sign,object",
    val diskBudgetMb: Long = 500L,
    val modelPath: String? = null,
    val modelConfigJson: String? = null,
    val prefetchEnabled: Boolean = false,
    @ColumnInfo(defaultValue="'{}'") val settingsJson: String = "{}",
    val lastRowCursor: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "batches", primaryKeys = ["projectId", "batchNumber"])
data class BatchEntity(
    val projectId: Long = 1L,
    val batchNumber: Int,
    val status: String, // DISCOVERED, DOWNLOADING, READY, IN_PROGRESS, VALIDATED, PUBLISHING, PUBLISHED, VERIFIED, PURGED
    val totalCases: Int = 100,
    val validatedCases: Int = 0,
    val rejectedCases: Int = 0,
    val deferredCases: Int = 0,
    val hfCommitSha: String? = null,
    val archivePath: String? = null,
    val archiveSnapshot: String? = null,
    val verifiedArchiveUri: String? = null,
    val verifiedArchiveSha256: String? = null,
    val verificationKind: String? = null,
    val remotePrefix: String? = null,
    val remoteBranch: String? = null,
    val remoteRepoId: String? = null,
    val remoteParentCommit: String? = null,
    val preparedManifestSha256: String? = null,
    val lastTransferError: String? = null,
    val remoteReceiptJson: String? = null,
    val archiveSizeBytes: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "samples", indices = [Index(value = ["projectId", "batchNumber", "sourceOrdinal"]), Index(value = ["projectId", "annotationStatus"]), Index(value = ["projectId", "sha256"])])
data class SampleEntity(
    @PrimaryKey val sampleId: String,
    val projectId: Long = 1L,
    val batchNumber: Int,
    val assetId: String,
    val sourceRowIndex: Long,
    val sourceOrdinal: Long? = null,
    val sourceFileUrl: String?,
    val localImagePath: String?,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val sha256: String? = null,
    val phash: Long? = null,
    val sourceSha256: String? = null,
    @ColumnInfo(defaultValue="'identity'") val imageTransform: String = "identity",
    val groupId: String? = null,
    val split: String = "train",
    val acquisitionStatus: String, // AcquisitionStatus enum name
    val annotationStatus: String,  // AnnotationStatus enum name
    val syncStatus: String,        // SyncStatus enum name
    val auditReason: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "annotations")
data class AnnotationRecord(
    @PrimaryKey val sampleId: String,
    val dataJson: String, // serialized SampleAnnotations
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs", indices = [Index(value = ["projectId", "timestamp"])])
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sampleId: String?,
    val batchNumber: Int,
    val action: String, // e.g. "VALIDATE", "REJECT", "DEFER", "INFERENCE_APPLIED", "PUBLISHED", "PURGED"
    val details: String,
    @ColumnInfo(defaultValue="1") val projectId: Long = 1L,
    val timestamp: Long = System.currentTimeMillis()
)

/** Metadata-only index. Images are copied into cache only for the active batch. */
@Entity(tableName = "source_entries", primaryKeys = ["projectId", "ordinal"])
data class SourceEntryEntity(
    val projectId: Long,
    val ordinal: Long,
    val assetId: String,
    val imageRef: String,
    val sourceRowIndex: Long? = null,
    val groupId: String? = null,
    val annotationJson: String? = null
)

@Entity(tableName = "model_profiles")
data class ModelProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val modelPath: String,
    val sha256: String,
    val configJson: String,
    val tensorReport: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** Persistent across cache purges; one canonical owner per project and content fingerprint. */
@Entity(tableName = "image_identities", primaryKeys = ["projectId", "kind", "digest"])
data class ImageIdentityEntity(
    val projectId: Long,
    val kind: String,
    val digest: String,
    val firstSampleId: String,
    val firstBatchNumber: Int
)
