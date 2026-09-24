package com.unicornwhodev.visiondatasetstudio.qa

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView

/** Foreground acceptance conditions, without opening a second product ViewModel. Debug/shell only. */
class QaPresenceActivity:Activity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(TextView(this).apply {
            text="Cadryl\nAndroid QA in progress"
            textSize=22f
            gravity=android.view.Gravity.CENTER
            setTextColor(0xff55ddff.toInt())
            setBackgroundColor(0xff09111c.toInt())
        })
    }
}
