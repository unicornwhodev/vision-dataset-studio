package com.unicornwhodev.visiondatasetstudio.domain.batch

import com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase

object BatchSnapshot {
    suspend fun compute(db:AppDatabase,projectId:Long,batchNumber:Int):String {
        val p=db.projectDao().getProjectSync(projectId) ?: error("Projet absent")
        val digest=java.security.MessageDigest.getInstance("SHA-256")
        fun add(v:String){val b=v.toByteArray();digest.update(java.nio.ByteBuffer.allocate(4).putInt(b.size).array());digest.update(b)}
        add(p.hfSourceRepo);add(p.sourceConfig);add(p.sourceSplit);add(p.targetSplit)
        for(s in db.sampleDao().getSamplesForBatchSync(projectId,batchNumber).sortedBy{it.sampleId}) {
            add(s.sampleId);add(s.sha256 ?: "");add(s.annotationStatus);add(s.auditReason ?: "")
            add(db.annotationDao().getAnnotationSync(s.sampleId)?.dataJson ?: "{}")
        }
        return digest.digest().joinToString(""){"%02x".format(it)}
    }
}
