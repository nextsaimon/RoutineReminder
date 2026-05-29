package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device booted! Re-scheduling routine task background service.")
            val appContext = context.applicationContext
            
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(appContext)
                val activeRoutine = db.routineDao().getActiveRoutine()
                if (activeRoutine != null) {
                    Log.d("BootReceiver", "Active routine found: ${activeRoutine.name}. Starting service...")
                    RoutineNotificationService.start(appContext)
                } else {
                    Log.d("BootReceiver", "No active routine to restore.")
                }
            }
        }
    }
}
