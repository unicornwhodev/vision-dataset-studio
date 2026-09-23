package com.unicornwhodev.visiondatasetstudio

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Uses platform APIs only so the actual minified, non-debuggable Release can be tested.
 * Run after adb install -r over the prepared same-certificate Debug installation. */
class ReleaseUpdateContinuityTest {
    private fun hash(file:File):String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;digest.update(b,0,n)} }
        return digest.digest().joinToString(""){"%02x".format(it)}
    }
    @Test fun actualSignedReleaseRetainsHumanAnnotationAndOriginalModel() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val case=requireNotNull(InstrumentationRegistry.getArguments().getString("preservationCase"))
        require(case.matches(Regex("[a-f0-9]{12}")))
        assertEquals(0,context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        val expected=JSONObject(File(context.filesDir,"qa-evidence/preservation/$case.json").readText())
        val info=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
        val certificate=MessageDigest.getInstance("SHA-256").digest(info.signingInfo!!.apkContentsSigners.single().toByteArray()).joinToString(""){"%02x".format(it)}
        assertEquals("51ef3e4abf953c62c8427deaecf4349aefffc2270c593229315722457f126895",certificate)
        val db=SQLiteDatabase.openDatabase(context.getDatabasePath("vision_dataset_studio.db").path,null,SQLiteDatabase.OPEN_READONLY)
        db.use {
            assertEquals(4,db.version)
            db.rawQuery("SELECT lastRowCursor FROM projects WHERE id=?",arrayOf(expected.getLong("project_id").toString())).use { c ->
                assertTrue(c.moveToFirst());assertEquals(expected.getLong("cursor"),c.getLong(0))
            }
            db.rawQuery("SELECT dataJson FROM annotations WHERE sampleId=?",arrayOf(expected.getString("sample_id"))).use { c ->
                assertTrue(c.moveToFirst());assertEquals(expected.getString("annotation_json"),c.getString(0))
            }
            db.rawQuery("SELECT localImagePath,annotationStatus,syncStatus FROM samples WHERE sampleId=?",arrayOf(expected.getString("sample_id"))).use { c ->
                assertTrue(c.moveToFirst());assertEquals(expected.getString("image_sha256"),hash(File(c.getString(0))))
                assertEquals("IN_PROGRESS",c.getString(1));assertEquals("NOT_EXPORTED",c.getString(2))
            }
        }
        val workflow=JSONObject(File(context.filesDir,"training-fixture/android-workflow-evidence.json").readText())
        assertEquals(workflow.getString("original_sha256_before"),hash(File(context.filesDir,"training-fixture/trainable-vision-fixture.tflite")))
        assertEquals(workflow.getString("first_final_weights"),workflow.getString("second_initial_weights"))
        val evidence=JSONObject().put("non_debuggable_release",true).put("certificate_sha256",certificate)
            .put("human_annotation_and_image_retained",true).put("cursor_retained",true).put("original_model_retained",true)
        instrumentation.sendStatus(0,Bundle().apply{putString("release_update_evidence",evidence.toString())})
    }
}
