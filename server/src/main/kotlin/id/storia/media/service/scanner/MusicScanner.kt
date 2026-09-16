package id.storia.media.service.scanner

import id.storia.media.db.MediaItems
import id.storia.media.db.currentLocalDateTime
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.io.File
import java.util.*

class MusicScanner(onStatusUpdate: (String?, Int?, Int?) -> Unit) : BaseScanner(onStatusUpdate) {
    override suspend fun scan(libraryId: String, root: File) {
        val audioFiles = root.walkTopDown().filter { it.isFile && isAudioFile(it) }.toList()
        onStatusUpdate(null, null, audioFiles.size)
        
        audioFiles.forEach { file ->
            onStatusUpdate(file.name, null, null)
            try {
                val audioFile = AudioFileIO.read(file)
                val tag = audioFile.tag
                val audioHeader = audioFile.audioHeader

                val album = tag?.getFirst(FieldKey.ALBUM)?.ifBlank { "Unknown Album" } ?: "Unknown Album"
                val artist = tag?.getFirst(FieldKey.ARTIST)?.ifBlank { "Unknown Artist" } ?: "Unknown Artist"
                val albumArtist = tag?.getFirst(FieldKey.ALBUM_ARTIST)?.ifBlank { artist } ?: artist
                val songTitle = tag?.getFirst(FieldKey.TITLE)?.ifBlank { file.nameWithoutExtension } ?: file.nameWithoutExtension
                val year = tag?.getFirst(FieldKey.YEAR)?.toIntOrNull()
                val trackNum = tag?.getFirst(FieldKey.TRACK)?.toIntOrNull()
                val discNum = tag?.getFirst(FieldKey.DISC_NO)?.toIntOrNull()
                
                val albumArtistSort = tag?.getFirst(FieldKey.ALBUM_ARTIST_SORT)?.ifBlank { null }
                val artistSort = tag?.getFirst(FieldKey.ARTIST_SORT)?.ifBlank { null }
                val artists = tag?.getAll(FieldKey.ARTIST) ?: emptyList()
                val totalTracks = tag?.getFirst(FieldKey.TRACK_TOTAL)?.toIntOrNull()
                val totalDiscs = tag?.getFirst(FieldKey.DISC_TOTAL)?.toIntOrNull()

                val durationMs = runCatching { audioHeader?.preciseTrackLength?.let { (it * 1000).toLong() } }.getOrNull()
                val bitrate = runCatching { audioHeader?.bitRateAsNumber }.getOrNull()?.takeIf { it > 0 }
                val sampleRate = runCatching { audioHeader?.sampleRateAsNumber }.getOrNull()?.takeIf { it > 0 }
                val audioCodec = runCatching { audioHeader?.encodingType }.getOrNull()

                val artwork = tag?.firstArtwork
                var coverFile = artwork?.let { saveArtwork(it) }

                if (coverFile == null) {
                    val dir = file.parentFile
                    coverFile = dir.listFiles { f -> isPosterFile(f) }?.firstOrNull()
                }

                val mediaItemId = processMusicAlbum(
                    libraryId = libraryId,
                    album = album,
                    albumArtist = albumArtist,
                    albumArtistSort = albumArtistSort,
                    artistSort = artistSort,
                    year = year,
                    posterPath = coverFile?.absolutePath
                )
                
                addMediaFile(
                    mediaItemId = mediaItemId,
                    file = file,
                    category = "MAIN",
                    thumbnail = coverFile,
                    title = songTitle,
                    episodeTitle = null,
                    trackArtist = artist,
                    trackNum = trackNum,
                    album = album,
                    albumArtist = albumArtist,
                    albumArtistSort = albumArtistSort,
                    artist = artist,
                    artistSort = artistSort,
                    artists = artists,
                    totalTracks = totalTracks,
                    totalDiscs = totalDiscs,
                    discNumber = discNum,
                    durationMs = durationMs,
                    bitrate = bitrate,
                    sampleRate = sampleRate,
                    audioCodec = audioCodec
                )
            } catch (e: Exception) {
                println("Jaudiotagger error for ${file.name}: ${e.message}")
                val coverFile = file.parentFile.listFiles { f -> isPosterFile(f) }?.firstOrNull()
                val mediaItemId = processMusicAlbum(
                    libraryId = libraryId,
                    album = "Unknown Album",
                    albumArtist = "Unknown Artist",
                    albumArtistSort = null,
                    artistSort = null,
                    year = null,
                    posterPath = coverFile?.absolutePath
                )
                addMediaFile(mediaItemId, file, category = "MAIN", thumbnail = coverFile)
            }
        }
    }

    private fun processMusicAlbum(
        libraryId: String,
        album: String,
        albumArtist: String,
        albumArtistSort: String?,
        artistSort: String?,
        year: Int?,
        posterPath: String?
    ): String = transaction {
        val existing = MediaItems.selectAll().where {
            (MediaItems.libraryId eq libraryId) and (MediaItems.albumArtist eq albumArtist) and (MediaItems.album eq album)
        }.singleOrNull()

        if (existing != null) {
            val id = existing[MediaItems.id]
            val oldPoster = existing[MediaItems.posterPath]

            MediaItems.update({ MediaItems.id eq id }) {
                it[MediaItems.albumArtist] = albumArtist
                it[MediaItems.albumArtistSortOrder] = albumArtistSort
                it[MediaItems.artistSortOrder] = artistSort
                it[MediaItems.artist] = albumArtist
                it[MediaItems.album] = album
                it[MediaItems.title] = album
                if (year != null) it[MediaItems.releaseYear] = year
                
                if (posterPath != null) {
                    it[MediaItems.posterPath] = posterPath
                } else if (oldPoster != null && !File(oldPoster).exists()) {
                    it[MediaItems.posterPath] = null
                }
                it[MediaItems.updatedAt] = currentLocalDateTime()
            }
            id
        } else {
            val id = UUID.randomUUID().toString()
            MediaItems.insert {
                it[MediaItems.id] = id
                it[MediaItems.libraryId] = libraryId
                it[MediaItems.type] = "music_album"
                it[MediaItems.title] = album
                it[MediaItems.artist] = albumArtist
                it[MediaItems.album] = album
                it[MediaItems.albumArtist] = albumArtist
                it[MediaItems.albumArtistSortOrder] = albumArtistSort
                it[MediaItems.artistSortOrder] = artistSort
                it[MediaItems.releaseYear] = year
                it[MediaItems.posterPath] = posterPath
            }
            id
        }
    }
}
