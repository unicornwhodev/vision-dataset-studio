package com.unicornwhodev.visiondatasetstudio.qa

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import org.json.JSONObject
import java.io.File

/** Receives a genuine DocumentsUI grant for explicit, synthetic fault qualification. */
class QaDocumentActivity : Activity() {
    private lateinit var marker: File
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val case = requireNotNull(intent.getStringExtra("faultCase"))
        require(case.matches(Regex("[a-f0-9]{12}")))
        marker = File(filesDir, "qa-evidence/external-faults/$case/document.json")
        require(marker.parentFile!!.isDirectory && !marker.exists())
        if (state == null) {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
                putExtra(Intent.EXTRA_TITLE, "vds-qa-$case.zip")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, 1)
        }
    }

    @Deprecated("DocumentsUI test bridge")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        val result = JSONObject().put("result_code", resultCode)
        if (resultCode == RESULT_OK && uri != null) {
            val flags = requireNotNull(data).flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            check(flags == (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            result.put("uri", uri.toString()).put("granted_flags", flags)
        }
        marker.writeText(result.toString(2))
        finish()
    }
}
