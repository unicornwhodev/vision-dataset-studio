package com.unicornwhodev.visiondatasetstudio.data.db

/** Versioned SQL shared by Room and host SQLite tests. Never use destructive fallback. */
object SchemaMigrations {
    val from1to2=listOf(
        "ALTER TABLE projects ADD COLUMN settingsJson TEXT NOT NULL DEFAULT '{}'",
        "ALTER TABLE audit_logs ADD COLUMN projectId INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE samples ADD COLUMN sourceOrdinal INTEGER",
        "ALTER TABLE samples ADD COLUMN sourceSha256 TEXT",
        "ALTER TABLE samples ADD COLUMN imageTransform TEXT NOT NULL DEFAULT 'identity'",
        "ALTER TABLE batches ADD COLUMN archivePath TEXT",
        "ALTER TABLE batches ADD COLUMN archiveSnapshot TEXT",
        "ALTER TABLE batches ADD COLUMN verifiedArchiveUri TEXT",
        "ALTER TABLE batches ADD COLUMN verifiedArchiveSha256 TEXT",
        "ALTER TABLE batches ADD COLUMN verificationKind TEXT",
        "ALTER TABLE batches ADD COLUMN remotePrefix TEXT",
        "ALTER TABLE batches ADD COLUMN remoteBranch TEXT",
        "CREATE TABLE IF NOT EXISTS source_entries (projectId INTEGER NOT NULL, ordinal INTEGER NOT NULL, assetId TEXT NOT NULL, imageRef TEXT NOT NULL, sourceRowIndex INTEGER, groupId TEXT, annotationJson TEXT, PRIMARY KEY(projectId, ordinal))",
        "CREATE TABLE IF NOT EXISTS model_profiles (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, modelPath TEXT NOT NULL, sha256 TEXT NOT NULL, configJson TEXT NOT NULL, tensorReport TEXT NOT NULL, createdAt INTEGER NOT NULL)"
    )
    val from2to3=listOf(
        "ALTER TABLE batches ADD COLUMN remoteRepoId TEXT",
        "ALTER TABLE batches ADD COLUMN remoteParentCommit TEXT",
        "ALTER TABLE batches ADD COLUMN preparedManifestSha256 TEXT",
        "ALTER TABLE batches ADD COLUMN lastTransferError TEXT",
        "ALTER TABLE batches ADD COLUMN remoteReceiptJson TEXT",
        "ALTER TABLE batches ADD COLUMN archiveSizeBytes INTEGER",
        "CREATE INDEX IF NOT EXISTS index_samples_projectId_batchNumber_sourceOrdinal ON samples(projectId, batchNumber, sourceOrdinal)",
        "CREATE INDEX IF NOT EXISTS index_samples_projectId_annotationStatus ON samples(projectId, annotationStatus)",
        "CREATE INDEX IF NOT EXISTS index_samples_projectId_sha256 ON samples(projectId, sha256)",
        "CREATE INDEX IF NOT EXISTS index_audit_logs_projectId_timestamp ON audit_logs(projectId, timestamp)",
        "UPDATE batches SET status='CONFLICT', lastTransferError='Ancien transfert sans parent persistant : réconciliation manuelle requise' WHERE status='PUBLISHING' AND hfCommitSha IS NULL"
    )
}
