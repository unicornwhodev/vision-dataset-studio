package com.unicornwhodev.visiondatasetstudio.qa

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/** Debug-only, shell-protected foreground process for opt-in device instrumentation.
 * It never opens a product ViewModel or changes battery settings. Absent from Release. */
class QaKeepAliveService:Service() {
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("vds-instrumentation","Android QA",NotificationManager.IMPORTANCE_LOW))
        val notification=Notification.Builder(this,"vds-instrumentation")
            .setSmallIcon(android.R.drawable.ic_menu_info_details).setContentTitle("Android QA in progress")
            .setContentText("Dedicated instrumentation session").setOngoing(true).build()
        if(Build.VERSION.SDK_INT>=34) startForeground(9284,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(9284,notification)
        return START_NOT_STICKY
    }
}
