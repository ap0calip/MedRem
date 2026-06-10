package com.example.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("BootReceiver", "Boot completed. Restoring active alarms.")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val activeMeds = db.dao().getAllActiveMedications()
                    for (med in activeMeds) {
                        ReminderScheduler.scheduleAlarm(context, med)
                    }
                    Log.d("BootReceiver", "Restored ${activeMeds.size} active medication alarms.")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to restore alarms: ${e.message}", e)
                }
            }
        }
    }
}
