package com.unicornwhodev.visiondatasetstudio.domain.training

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import com.unicornwhodev.visiondatasetstudio.MainActivity
import com.unicornwhodev.visiondatasetstudio.R
import java.util.UUID

/** User-visible local computation; cancellation keeps the worker's durable checkpoint. */
internal object TrainingForeground {
    private const val CHANNEL = "local-training"
    private const val NOTIFICATION = 4102

    fun info(context: Context, workerId: UUID, run: DeviceTrainingRun): ForegroundInfo {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.training_notification_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = if (run.phase == "evaluating") context.getString(R.string.training_phase_evaluating)
            else context.getString(R.string.training_notification_progress, run.completedSteps, run.totalSteps)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_training_notification)
            .setContentTitle(context.getString(R.string.training_notification_title))
            .setContentText(text).setContentIntent(open)
            .setProgress(run.totalSteps, run.completedSteps, run.phase == "evaluating")
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, context.getString(R.string.training_stop),
                WorkManager.getInstance(context).createCancelPendingIntent(workerId))
            .build()
        return if (Build.VERSION.SDK_INT >= 29)
            ForegroundInfo(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION, notification)
    }
}
