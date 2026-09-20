package com.unicornwhodev.visiondatasetstudio
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android SQLite + Room, fixtures reconstructed from the supplied historical entity declarations. */
@RunWith(AndroidJUnit4::class)
class RoomMigrationOnDeviceTest {
    private fun loadFixture(version:Int):String=InstrumentationRegistry.getInstrumentation().context.assets.open("legacy-v$version.sql").bufferedReader().use{it.readText()}
    @Test fun version1To4(){exercise(1)}
    @Test fun version2To4(){exercise(2)}
    @Test fun version3To4(){exercise(3)}
    private fun seed(raw:android.database.sqlite.SQLiteDatabase,table:String,overrides:Map<String,Any?>) {
        val values=android.content.ContentValues()
        raw.rawQuery("PRAGMA table_info($table)",null).use { cursor -> while(cursor.moveToNext()) {
            val name=cursor.getString(1)
            val value=if(overrides.containsKey(name))overrides[name] else if(!cursor.isNull(4))continue
                else if(cursor.getInt(3)==1) { if(cursor.getString(2)=="TEXT") "" else 0L } else null
            when(value) { null->values.putNull(name);is String->values.put(name,value);is Long->values.put(name,value);is Int->values.put(name,value);else->error("Unsupported fixture value") }
        } }
        check(raw.insertOrThrow(table,null,values)>=0)
    }
    private fun exercise(version:Int) {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="v4-migration-${version}-${java.util.UUID.randomUUID()}.db"
        val json="{\"points\":[{\"id\":\"p1\",\"x\":0.25,\"y\":0.75,\"label\":\"object\"}]}"
        val raw=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null)
        val sql=loadFixture(version)
        sql.lineSequence().filterNot{it.trimStart().startsWith("--")}.joinToString("\n").split(';').filter{it.isNotBlank()}.forEach{raw.execSQL(it)}
        seed(raw,"projects",mapOf("id" to 7L,"name" to "legacy","lastRowCursor" to 123456L,"settingsJson" to "{}"))
        seed(raw,"batches",mapOf("projectId" to 7L,"batchNumber" to 1,"status" to "PUBLISHING","totalCases" to 1))
        seed(raw,"samples",mapOf("sampleId" to "sample","projectId" to 7L,"batchNumber" to 1,"phash" to Long.MIN_VALUE,"sha256" to "legacy-content-hash","annotationStatus" to "VALIDATED","acquisitionStatus" to "AVAILABLE","syncStatus" to "NOT_EXPORTED"))
        seed(raw,"annotations",mapOf("sampleId" to "sample","dataJson" to json))
        raw.version=version;raw.close()
        var database:AppDatabase?=null
        try {
            val opened=Room.databaseBuilder(context,AppDatabase::class.java,name).allowMainThreadQueries()
                .addMigrations(AppDatabase.MIGRATION_1_2,AppDatabase.MIGRATION_2_3,AppDatabase.MIGRATION_3_4).build()
            database=opened
            assertEquals(4,opened.openHelper.writableDatabase.version) // KSP-generated Room schema validation is executed here.
            runBlocking {
                assertEquals(123456L,opened.projectDao().getProjectSync(7)!!.lastRowCursor)
                assertEquals(json,opened.annotationDao().getAnnotationSync("sample")!!.dataJson)
                assertEquals(Long.MIN_VALUE,opened.sampleDao().getSampleSync("sample")!!.phash)
                assertEquals("sample",opened.imageIdentityDao().owner(7,"file_sha256","legacy-content-hash")!!.firstSampleId)
                val batch=opened.batchDao().getBatchSync(7,1)!!
                assertEquals(if(version<3)"CONFLICT" else "PUBLISHING",batch.status)
                assertNull(batch.hfCommitSha);assertNull(batch.remoteParentCommit);assertNull(batch.remoteReceiptJson)
            }
        } finally {database?.close();context.deleteDatabase(name)}
    }
}
