package id.storia.media.data.local

import java.util.UUID
import java.util.prefs.Preferences

actual class SettingsManager {
    private val prefs = Preferences.userNodeForPackage(SettingsManager::class.java)

    actual fun getClientId(): String {
        var id = prefs.get("client_id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.put("client_id", id)
            prefs.flush()
        }
        return id
    }
}
