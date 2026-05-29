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

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_TIME_TICK) {
                checkAndTriggerNotifications()
            }
        }
    }

    private var backupCheckJob: Job? = null

    companion object {
        private const val TAG = "RoutineService"
        const val CHANNEL_SERVICE_ID = "routine_service_channel"
        const val CHANNEL_ALARM_ID = "routine_alarm_channel"
        const val SERVICE_NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, RoutineNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RoutineNotificationService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created")
        createNotificationChannels()
        registerReceiver(timeTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        
        // Start backup checkout loop
        startBackupCheckLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        
        // Load active routine from Database
        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val active = db.routineDao().getActiveRoutine()
            
            withContext(Dispatchers.Main) {
                if (active != null) {
                    activeRoutine = active
                    val notification = buildServiceNotification(active.name, active.getTasks().size)
                    startForeground(SERVICE_NOTIFICATION_ID, notification)
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

    private fun startBackupCheckLoop() {
        backupCheckJob?.cancel()
        backupCheckJob = serviceScope.launch {
            while (isActive) {
                checkAndTriggerNotifications()
                // Sleep for 30 seconds before checking again
                delay(30000)
            }
        }
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

        if (soundUri != null) {
            notificationBuilder.setSound(soundUri)
        }
        
        // Vibration pattern
        notificationBuilder.setVibrate(longArrayOf(0, 500, 250, 500))

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Unique notification ID per task match using task.id
        notificationManager.notify(task.id + 2000, notificationBuilder.build())
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
        unregisterReceiver(timeTickReceiver)
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
