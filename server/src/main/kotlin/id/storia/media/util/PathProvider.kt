package id.storia.media.util

import java.io.File

object PathProvider {
    /**
     * The root directory for all application data in the user's HOME folder.
     * Using File constructor ensures cross-OS compatibility with paths.
     */
    val appDataDir: File by lazy {
        val home = System.getProperty("user.home")
        File(home, ".mediastation").also {
            if (!it.exists()) it.mkdirs()
        }
    }

    /**
     * Returns the JDBC URL for the H2 database, located in the app data directory.
     */
    fun getDatabaseJdbcUrl(): String {
        // H2 expects the path without the .mv.db extension.
        // We use forward slashes for the JDBC URL to avoid backslash issues on Windows.
        // We removed the double quotes as they can cause "illegal character" errors in some H2 versions.
        val dbPath = File(appDataDir, "mediastation").absolutePath.replace("\\", "/")
        return "jdbc:h2:$dbPath;DB_CLOSE_DELAY=-1;"
    }

    /**
     * Returns the directory where cover images are stored, ensuring it exists.
     */
    fun getCoverDir(): File {
        return File(appDataDir, "metadata/covers").also {
            if (!it.exists()) it.mkdirs()
        }
    }

    /**
     * Returns the directory where video thumbnails are stored, ensuring it exists.
     */
    fun getThumbnailDir(): File {
        return File(appDataDir, "metadata/thumbnails").also {
            if (!it.exists()) it.mkdirs()
        }
    }

    /**
     * Returns the directory where media posters are stored, ensuring it exists.
     */
    fun getPosterDir(): File {
        return File(appDataDir, "metadata/posters").also {
            if (!it.exists()) it.mkdirs()
        }
    }

    /**
     * Returns the directory where cached files (e.g. extracted CBZ pages) are stored.
     */
    fun getCacheDir(): File {
        return File(appDataDir, "cache").also {
            if (!it.exists()) it.mkdirs()
        }
    }
}
