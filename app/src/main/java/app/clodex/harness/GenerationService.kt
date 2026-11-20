package app.clodex.harness

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** Keeps user-started streaming requests alive while the UI is closed; never replays requests. */
class GenerationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null
    private val container get() = (application as ClodexApp).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel = "generation"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(channel, "Ответы моделей", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, channel)
            .setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("Clodex работает")
            .setContentText("Ответ модели продолжается в фоне").setContentIntent(open).setOngoing(true).build()
        try { startForeground(42, notification) } catch (_: IllegalStateException) { stopSelf(startId); return START_NOT_STICKY }
        watcher?.cancel()
        watcher = scope.launch {
            combine(container.chatVm.activeReply, container.codeVm.running) { chat, code -> chat || code.isNotEmpty() }.first { !it }
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf(startId) }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
