package com.unicornwhodev.visiondatasetstudio.releaseqa;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Shell-only test driver support. Never packaged in the application under test. */
public final class UiDriverService extends Service {
    @Override public IBinder onBind(Intent intent) {return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null && intent.getBooleanExtra("dumpStacks",false)) {
            try(var out=new java.io.PrintWriter(openFileOutput("driver-threads.txt",MODE_PRIVATE))) {
                for(var entry:Thread.getAllStackTraces().entrySet()) {
                    out.println(entry.getKey().getName()+" "+entry.getKey().getState());
                    for(var frame:entry.getValue())out.println("  at "+frame);
                }
            } catch(java.io.IOException error) {throw new IllegalStateException(error);}
        }
        getSystemService(NotificationManager.class).createNotificationChannel(
            new NotificationChannel("release-ui-qa","Android UI QA",NotificationManager.IMPORTANCE_LOW));
        var notification=new Notification.Builder(this,"release-ui-qa")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Release UI QA in progress")
            .setContentText("Dedicated instrumentation driver").setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=34)startForeground(9285,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(9285,notification);
        return START_NOT_STICKY;
    }
}
