package id.storia.media.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object StringListSerializer : KSerializer<List<String>> {
    private val delegateSerializer = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = delegateSerializer.descriptor

    override fun serialize(encoder: Encoder, value: List<String>) {
        delegateSerializer.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): List<String> {
        val input = decoder as? JsonDecoder ?: return delegateSerializer.deserialize(decoder)
        val element = input.decodeJsonElement()
        if (element is JsonNull) return emptyList()
        return if (element is JsonPrimitive && element.isString) {
            val content = element.contentOrNull ?: return emptyList()
            if (content.startsWith("[") && content.endsWith("]")) {
                try {
                    Json.decodeFromString(ListSerializer(String.serializer()), content)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                content.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
        } else {
            try {
                Json.decodeFromJsonElement(ListSerializer(String.serializer()), element)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}

@Serializable
enum class LibraryType(val displayName: String) {
    // Managed Types
    @SerialName("film") FILM("Film"),
    @SerialName("series") SERIES("Series"),
    @SerialName("music_album") MUSIC_ALBUM("Music Album"),
    @SerialName("music_video") MUSIC_VIDEO("Music Videos"),
    @SerialName("podcast") PODCAST("Podcast"),
    @SerialName("book") BOOK("Book"),
    @SerialName("comic") COMIC("Comic"),
    @SerialName("photobook") PHOTOBOOK("Photobook"),
    @SerialName("web_vod") WEB_VOD("Web VoD"),
    @SerialName("broadcast") BROADCAST("Broadcast"),

    // Loose Types
    @SerialName("video") VIDEO("Video"),
    @SerialName("audio") AUDIO("Audio"),
    @SerialName("image") IMAGE("Image"),
    @SerialName("document") DOCUMENT("Document"),
    @SerialName("misc") MISC("Misc");

    companion object {
        fun fromString(value: String): LibraryType {
            val normalized = value.lowercase().trim()
            return entries.find { it.name.lowercase() == normalized }
                ?: entries.find { it.displayName.lowercase() == normalized }
                ?: MISC
        }
    }
}

@Serializable
data class Library(
    val id: String,
    val name: String,
    val type: LibraryType,
    val path: String? = null,
    @Serializable(with = StringListSerializer::class)
    val folders: List<String> = emptyList(),
    val createdAt: String? = null,
    val lastScannedAt: String? = null
)

@Serializable
data class CreateLibraryRequest(
    val name: String,
    val type: LibraryType,
    val paths: List<String> = emptyList()
)

@Serializable
data class MediaStreamInfo(
    val durationMs: Long? = null,
    val resolution: String? = null,
    val containerFormat: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bitrate: Long? = null,
    val sampleRate: Int? = null
)

@Serializable
data class SeriesInfo(
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val plot: String? = null,
    val airDate: String? = null
)

@Serializable
data class MusicInfo(
    val album: String? = null,
    val albumArtist: String? = null,
    val albumArtistSortOrder: String? = null,
    val artist: String? = null,
    val artistSortOrder: String? = null,
    @Serializable(with = StringListSerializer::class)
    val artists: List<String> = emptyList(),
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val totalTracks: Int? = null,
    val totalDiscs: Int? = null
)

@Serializable
data class Media(
    val id: String,
    val libraryId: String,
    val type: LibraryType,
    val title: String,
    val originalTitle: String? = null,
    val sortTitle: String? = null,
    val releaseYear: Int? = null,
    val plot: String? = null,
    val rating: Double? = null,
    @Serializable(with = StringListSerializer::class)
    val genres: List<String> = emptyList(),
    val studio: String? = null,
    val director: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val albumArtistSortOrder: String? = null,
    val artistSortOrder: String? = null,
    val posterPath: String? = null,
    val fanartPath: String? = null,
    val totalDurationMs: Long? = null,
    val isHidden: Boolean? = null,
    val hiddenAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val lastPlayedAt: String? = null,
    val files: List<MediaFile> = emptyList()
) {
    val mainFiles: List<MediaFile>
        get() = files.filter { it.category == "MAIN" || it.category == null }

    val extraFiles: List<MediaFile>
        get() = files.filter { it.category == "EXTRA" }
}

@Serializable
data class MediaFile(
    val id: String,
    val mediaItemId: String,
    val fileName: String,
    val fileSize: Long? = null,
    val title: String? = null,
    val category: String? = null, // MAIN or EXTRA
    val index: Int? = null, // track number or episode number
    val filePath: String? = null,
    val isHidden: Boolean? = null,
    val hiddenAt: String? = null,
    val createdAt: String? = null,
    val progress: PlaybackProgress? = null,
    val streamInfo: MediaStreamInfo? = null,
    val seriesInfo: SeriesInfo? = null,
    val musicInfo: MusicInfo? = null
) {
    val displayName: String
        get() = title ?: seriesInfo?.episodeTitle ?: fileName.substringBeforeLast(".")
            .replace(Regex("^\\d+\\s*-\\s*"), "")
            .replace("_", " ")
            .trim()

    val derivedResolution: String?
        get() {
            val si = streamInfo
            if (si != null && !si.resolution.isNullOrBlank()) return si.resolution
            val w = si?.width ?: return null
            val h = si.height ?: return null
            return when {
                w >= 3840 || h >= 2160 -> "4K"
                w >= 1920 || h >= 1080 -> "1080p"
                w >= 1280 || h >= 720 -> "720p"
                w >= 848 || h >= 480 -> "480p"
                else -> "${h}p"
            }
        }
}

@Serializable
data class PlaybackProgress(
    val mediaFileId: String,
    val clientId: String? = "default",
    val positionSeconds: Int,
    val durationSeconds: Int? = null,
    val pageNumber: Int? = null,
    val totalPages: Int? = null
)

@Serializable
data class PageInfo(
    val name: String,
    val size: Long
)

@Serializable
data class GenericResponse(val success: Boolean)

@Serializable
data class SystemInfo(
    val name: String,
    val version: String,
    val directPlayOnly: Boolean = true,
    val videoTranscodingAllowed: Boolean = false
)

@Serializable
data class ScanStatus(
    val isScanning: Boolean,
    val lastScanAt: String? = null,
    val filesFound: Int = 0,
    val filesProcessed: Int = 0,
    val currentLibraryId: String? = null,
    val libraryName: String? = null,
    val currentFile: String? = null,
    val error: String? = null
)

@Serializable
data class ScanResponse(
    val message: String,
    val status: ScanStatus
)
