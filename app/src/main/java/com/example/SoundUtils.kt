package com.example

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

object SoundUtils {
    /**
     * Resolves the human-readable sound name / ringtone title for a given sound URI string.
     * Fallbacks to profileSoundUri or system default alarm sound if soundUri is empty.
     */
    fun getSoundName(context: Context, soundUri: String?, profileSoundUri: String? = null): String {
        val effectiveUriStr = soundUri?.ifEmpty { null } ?: profileSoundUri?.ifEmpty { null }
        if (effectiveUriStr.isNullOrEmpty()) {
            return try {
                val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                val ringtone = RingtoneManager.getRingtone(context, defaultUri)
                val title = ringtone?.getTitle(context)
                if (!title.isNullOrEmpty()) "Default ($title)" else "Default Alarm Sound"
            } catch (e: Exception) {
                "Default Alarm Sound"
            }
        }
        return try {
            val uri = Uri.parse(effectiveUriStr)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            val title = ringtone?.getTitle(context)
            if (!title.isNullOrEmpty()) title else "Custom Sound"
        } catch (e: Exception) {
            "Custom Sound"
        }
    }
}
