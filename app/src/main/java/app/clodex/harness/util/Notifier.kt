package app.clodex.harness.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.clodex.harness.BuildConfig
import app.clodex.harness.MainActivity
import app.clodex.harness.data.notifications.NotifyEvent

/** Shows opt-in event notifications; a no-op when Android permission was not granted. */
class Notifier(private val context: Context) {
    fun show(event: NotifyEvent, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = "clodex_events"
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(channel) == null) {
            manager.createNotificationChannel(
                NotificationChannel(channel, "События Clodex", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val open = PendingIntent.getActivity(
            context, event.ordinal + 100,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(app.clodex.harness.R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        manager.notify(100 + event.ordinal, notification)
    }
}
