package com.example.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.RoutineEntity
import com.example.data.Task
import kotlinx.coroutines.*
import java.util.*

class RoutineNotificationService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var activeRoutine: RoutineEntity? = null
    private var lastCheckedTime: String = ""
    private var activeRingtone: android.media.Ringtone? = null

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_TIME_TICK) {
                checkAndTriggerNotifications()
            }
        }
    }

    private val volumeButtonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                Log.d(TAG, "Volume changed/button pressed, stopping alarm ringtone")
                stopActiveRingtone()
            }
        }
    }

    private fun stopActiveRingtone() {
        try {
            activeRingtone?.let { ringtone ->
                if (ringtone.isPlaying) {
                    ringtone.stop()
                    Log.d(TAG, "Ringtone stopped successfully")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping active ringtone: ${e.message}", e)
        } finally {
            activeRingtone = null
        }
    }

    companion object {
        private const val TAG = "RoutineService"
        const val CHANNEL_SERVICE_ID = "routine_service_channel"
        const val CHANNEL_ALARM_ID = "routine_alarm_channel"
        const val SERVICE_NOTIFICATION_ID = 1001
        const val ACTION_STOP_ALARM = "com.example.service.ACTION_STOP_ALARM"

        fun start(context: Context) {
            try {
                val intent = Intent(context, RoutineNotificationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed starting foreground service: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, RoutineNotificationService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed stopping foreground service: ${e.message}", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created")
        createNotificationChannels()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timeTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK), RECEIVER_NOT_EXPORTED)
            registerReceiver(volumeButtonReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), RECEIVER_EXPORTED)
        } else {
            registerReceiver(timeTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
            registerReceiver(volumeButtonReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        
        // Immediately start foreground with a placeholder to prevent OS crash ("Context.startForegroundService() did not then call Service.startForeground()")
        try {
            val initialNotification = buildServiceNotification("Syncing routine schedule...", 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    SERVICE_NOTIFICATION_ID, 
                    initialNotification, 
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(SERVICE_NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call startForeground inside service: ${e.message}", e)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_STOP_ALARM) {
            Log.d(TAG, "onStartCommand received ACTION_STOP_ALARM")
            val notificationId = intent.getIntExtra("NOTIFICATION_ID", -1)
            if (notificationId != -1) {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)
            }
            stopActiveRingtone()
            return START_STICKY
        }
        
        // Load active routine from Database
        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val active = db.routineDao().getActiveRoutine()
            
            withContext(Dispatchers.Main) {
                if (active != null) {
                    activeRoutine = active
                    val notification = buildServiceNotification(active.name, active.getTasks().size)
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(SERVICE_NOTIFICATION_ID, notification)
                    Log.d(TAG, "Started foreground with routine: ${active.name}")
                    // Immediate tick check when service starts
                    checkAndTriggerNotifications()
                } else {
                    Log.d(TAG, "No active routine found, stopping service")
                    stopSelf()
                }
            }
        }

        return START_STICKY
    }

    private fun isNotificationEnabled(): Boolean {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("notifications_enabled", true)
    }

    private fun isSoundEnabled(): Boolean {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("sound_enabled", true)
    }

    private fun checkAndTriggerNotifications() {
        val calendar = Calendar.getInstance()
        val rawDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        // JS Convention: 0 = Sun, 1 = Mon ... 6 = Sat
        val jsDayOfWeek = rawDayOfWeek - 1
        
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val currentTime = String.format("%02d:%02d", hour, minute)

        // Avoid multiple triggers in the same minute
        if (currentTime == lastCheckedTime) {
            return
        }
        lastCheckedTime = currentTime

        Log.d(TAG, "Checking: Time $currentTime, Day index $jsDayOfWeek")

        if (!isNotificationEnabled()) {
            Log.d(TAG, "Notifications are globally disabled in settings.")
            return
        }

        val routine = activeRoutine ?: return
        val tasks = routine.getTasks()

        for (task in tasks) {
            // Check if day is scheduled and time matches
            if (task.time == currentTime && task.days.contains(jsDayOfWeek)) {
                triggerNotification(task)
            }
        }
    }

    private fun triggerNotification(task: Task) {
        Log.d(TAG, "Triggering notification for task: ${task.title}")
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            task.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = if (isSoundEnabled()) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        } else {
            null
        }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ALARM_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(task.title.ifEmpty { "Routine Reminder" })
            .setContentText(task.message.ifEmpty { "Time for your scheduled task!" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Create the stop alarm action button
        val stopNotificationId = task.id + 2000
        val stopIntent = Intent(this, RoutineNotificationService::class.java).apply {
            action = ACTION_STOP_ALARM
            putExtra("NOTIFICATION_ID", stopNotificationId)
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            task.id + 5000,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notificationBuilder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop Alarm",
            stopPendingIntent
        )

        if (soundUri != null) {
            notificationBuilder.setSound(soundUri)
        }
        
        // Vibration pattern
        notificationBuilder.setVibrate(longArrayOf(0, 500, 250, 500))

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Unique notification ID per task match using task.id
        notificationManager.notify(stopNotificationId, notificationBuilder.build())

        // Play alarm ringtone if global routine alarm OR individual task card alarm is enabled
        val routine = activeRoutine
        if (routine != null && (routine.playRingtone == true || task.playRingtone) && isSoundEnabled()) {
            serviceScope.launch {
                try {
                    val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    val ringtone = RingtoneManager.getRingtone(applicationContext, ringtoneUri)
                    if (ringtone != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            ringtone.audioAttributes = android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        }
                        activeRingtone?.stop()
                        activeRingtone = ringtone
                        ringtone.play()
                        val durationMs = ((routine.ringtoneDuration ?: 20).coerceAtLeast(1) * 1000L)
                        delay(durationMs)
                        if (activeRingtone == ringtone && ringtone.isPlaying) {
                            ringtone.stop()
                            activeRingtone = null
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed playing alarm ringtone: ${e.message}", e)
                }
            }
        }
    }

    private fun buildServiceNotification(routineName: String, taskCount: Int): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_SERVICE_ID)
            .setContentTitle("Active Routine: $routineName")
            .setContentText("$taskCount tasks scheduled")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Service Notification Channel (Persistent)
            if (notificationManager.getNotificationChannel(CHANNEL_SERVICE_ID) == null) {
                val serviceChannel = NotificationChannel(
                    CHANNEL_SERVICE_ID,
                    "Routine Background Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Used to display persistent active routine notification"
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(serviceChannel)
            }

            // 2. Alarm Reminder Notification Channel (High Importance, heads-up)
            if (notificationManager.getNotificationChannel(CHANNEL_ALARM_ID) == null) {
                val alarmChannel = NotificationChannel(
                    CHANNEL_ALARM_ID,
                    "Routine Tasks Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Delivers task alerts similar to instant messages"
                    enableLights(true)
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(alarmChannel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service Destroyed")
        try {
            stopActiveRingtone()
        } catch (e: Exception) {
            // ignore
        }
        try {
            unregisterReceiver(timeTickReceiver)
        } catch (e: Exception) {
            // ignore
        }
        try {
            unregisterReceiver(volumeButtonReceiver)
        } catch (e: Exception) {
            // ignore
        }
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
