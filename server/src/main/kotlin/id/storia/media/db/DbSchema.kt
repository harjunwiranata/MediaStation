package id.storia.media.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.core.ReferenceOption
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.h2.util.DateTimeUtils
import kotlin.time.Clock

fun currentLocalDateTime(): LocalDateTime =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

object Libraries : Table("libraries") {
    val id = text("id")
    val name = text("name")
    val type = text("type")
    val path = text("path").nullable()
    val createdAt = datetime("created_at").clientDefault { currentLocalDateTime() }
    val lastScannedAt = datetime("last_scanned_at").nullable()
    
    override val primaryKey = PrimaryKey(id)
}

object LibraryFolders : Table("library_folders") {
    val id = text("id")
    val libraryId = text("library_id").references(Libraries.id, onDelete = ReferenceOption.CASCADE)
    val path = text("path")
    val createdAt = datetime("created_at").clientDefault { currentLocalDateTime() }
    
    override val primaryKey = PrimaryKey(id)
}

object MediaItems : Table("media_items") {
    val id = text("id")
    val libraryId = text("library_id").references(Libraries.id, onDelete = ReferenceOption.CASCADE)
    val type = text("type")
    val title = text("title")
    val originalTitle = text("original_title").nullable()
    val sortTitle = text("sort_title").nullable()
    val releaseYear = integer("release_year").nullable()
    val plot = text("plot").nullable()
    val rating = double("rating").nullable()
    val genres = text("genres").nullable()
    val studio = text("studio").nullable()
    val director = text("director").nullable()
    val artist = text("artist").nullable()
    val album = text("album").nullable()
    val albumArtist = text("album_artist").nullable()
    val albumArtistSortOrder = text("album_artist_sort_order").nullable()
    val artistSortOrder = text("artist_sort_order").nullable()
    val posterPath = text("poster_path").nullable()
    val fanartPath = text("fanart_path").nullable()
    val totalDurationMs = long("total_duration_ms").nullable()
    val isHidden = bool("is_hidden").default(false)
    val hiddenAt = datetime("hidden_at").nullable()
    val createdAt = datetime("created_at").clientDefault { currentLocalDateTime() }
    val updatedAt = datetime("updated_at").clientDefault { currentLocalDateTime() }
    val lastPlayedAt = datetime("last_played_at").nullable()
    
    override val primaryKey = PrimaryKey(id)
}

object MediaFiles : Table("media_files") {
    val id = text("id")
    val mediaItemId = text("media_item_id").references(MediaItems.id, onDelete = ReferenceOption.CASCADE)
    val filePath = text("file_path").uniqueIndex()
    val fileName = text("file_name")
    val title = text("title").nullable()
    val fileSize = long("file_size")
    val containerFormat = text("container_format").nullable()
    val videoCodec = text("video_codec").nullable()
    val audioCodec = text("audio_codec").nullable()
    val resolution = text("resolution").nullable()
    val width = integer("width").nullable()
    val height = integer("height").nullable()
    val bitrate = long("bitrate").nullable()
    val durationMs = long("duration_ms").nullable()
    val isHidden = bool("is_hidden").default(false)
    val hiddenAt = datetime("hidden_at").nullable()
    val seasonNumber = integer("season_number").nullable()
    val episodeNumber = integer("episode_number").nullable()
    val episodeTitle = text("episode_title").nullable()
    val plot = text("plot").nullable()
    val airDate = text("air_date").nullable()
    val trackNumber = integer("track_number").nullable()
    val discNumber = integer("disc_number").nullable()
    val trackArtist = text("track_artist").nullable()
    val album = text("album").nullable()
    val albumArtist = text("album_artist").nullable()
    val albumArtistSortOrder = text("album_artist_sort_order").nullable()
    val artist = text("artist").nullable()
    val artistSortOrder = text("artist_sort_order").nullable()
    val artists = text("artists").nullable()
    val totalTracks = integer("total_tracks").nullable()
    val totalDiscs = integer("total_discs").nullable()
    val sampleRate = integer("sample_rate").nullable()
    val thumbnailPath = text("thumbnail_path").nullable()
    val category = text("category").default("MAIN")
    val createdAt = datetime("created_at").clientDefault { currentLocalDateTime() }
    
    override val primaryKey = PrimaryKey(id)
}

object Subtitles : Table("subtitles") {
    val id = text("id")
    val mediaFileId = text("media_file_id").references(MediaFiles.id, onDelete = ReferenceOption.CASCADE)
    val language = text("language").default("und")
    val title = text("title").nullable()
    val format = text("format")
    val filePath = text("file_path").nullable()
    val isEmbedded = bool("is_embedded").default(false)
    val streamIndex = integer("stream_index").nullable()
    
    override val primaryKey = PrimaryKey(id)
}

object PlaybackProgressTable : Table("playback_progress") {
    val id = text("id")
    val mediaFileId = text("media_file_id").references(MediaFiles.id, onDelete = ReferenceOption.CASCADE)
    val clientId = text("client_id").default("default")
    val positionSeconds = integer("position_seconds").default(0)
    val durationSeconds = integer("duration_seconds").default(0)
    val pageNumber = integer("page_number").nullable()
    val totalPages = integer("total_pages").nullable()
    val isFinished = bool("is_finished").default(false)
    val updatedAt = datetime("updated_at").clientDefault { currentLocalDateTime() }
    
    override val primaryKey = PrimaryKey(id)
}

object ServerSettings : Table("server_settings") {
    val key = text("key")
    val value = text("value")
    val updatedAt = datetime("updated_at").clientDefault { currentLocalDateTime() }
    
    override val primaryKey = PrimaryKey(key)
}
