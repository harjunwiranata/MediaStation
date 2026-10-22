package id.storia.media.data.local

import android.content.Context
import java.util.UUID

actual class SettingsManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("storia_settings", Context.MODE_PRIVATE)

    actual fun getClientId(): String {
        var id = prefs.getString("client_id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString("client_id", id).apply()
        }
        return id
    }
}
