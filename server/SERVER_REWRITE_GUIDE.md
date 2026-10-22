# MediaStation Server Rewrite Guide (AI-Friendly Specification)

This guide provides a complete, framework-agnostic specification to recreate or port the **MediaStation Server** to any programming language or technology stack (e.g., **Go**, **Rust**, **Python**, **TypeScript/Node.js**, **C#/.NET**, **C++**).

It contains exact schemas, database table definitions, API route contracts, business logic algorithms, and scanner rules required for an AI agent or developer to build a 100% compatible server implementation.

---

## Table of Contents
1. [System Architecture Overview](#1-system-architecture-overview)
2. [Data Models & Schema Specifications](#2-data-models--schema-specifications)
3. [Database Table Schemas (SQL Equivalents)](#3-database-table-schemas-sql-equivalents)
4. [Storage Paths & Directory Structure](#4-storage-paths--directory-structure)
5. [REST API Specification](#5-rest-api-specification)
6. [Media Scanners & Extraction Algorithms](#6-media-scanners--extraction-algorithms)
7. [External Tool Integrations & Helpers](#7-external-tool-integrations--helpers)
8. [Network & Service Discovery (mDNS)](#8-network--service-discovery-mdns)
9. [AI Implementation Checklist](#9-ai-implementation-checklist)

---

## 1. System Architecture Overview

MediaStation Server is a lightweight media streaming, cataloging, and metadata server designed to manage multi-type media libraries (Movies, TV Series, Music Albums, Books/Comics, Photobooks, Generic Files).

```
 +-------------------------------------------------------------------+
 |                       MediaStation Server                         |
 |                                                                   |
 |  +------------------+   +-------------------+   +--------------+  |
 |  |  mDNS Discovery  |   |    HTTP REST API  |   |  Media Stream|  |
 |  | (_mediastation)  |   |     (Port 9090)   |   | (Range Req.) |  |
 |  +------------------+   +-------------------+   +--------------+  |
 |                                |                                  |
 |  +-------------------------------------------------------------+  |
 |  |                      Library Service                        |  |
 |  +-------------------------------------------------------------+  |
 |         |                              |              |           |
 |  +--------------+              +---------------+  +------------+  |
 |  | DB (SQLite/  |              |    Scanner    |  | File Cache |  |
 |  | H2/Postgres) |              |    Engine     |  |   (CBZ)    |  |
 |  +--------------+              +---------------+  +------------+  |
 |                                        |                          |
 |            +---------------------------+-------------------+      |
 |            |            |           |          |           |      |
 |        +-------+    +--------+  +-------+  +-------+  +--------+  |
 |        | Film  |    | Series |  | Music |  | Book  |  | Photo  |  |
 |        |Scan   |    | Scan   |  | Scan  |  | Scan  |  | book   |  |
 |        +-------+    +--------+  +-------+  +-------+  +--------+  |
 |            |            |           |          |           |      |
 |         (NFO /       (NFO /      (ID3/      (Comic     (ImageIO / |
 |         FFmpeg)      FFmpeg)     Audio)     Info XML)   FFmpeg)   |
 +-------------------------------------------------------------------+
```

### Core Design Principles
1. **Source of Truth is the Disk**: Scanner scans folders and syncs media metadata to the database. Missing files on disk are removed from DB during cleanup.
2. **Direct Play First**: Media streams are served as-is (`PartialContent` / HTTP 206 Byte Ranges). No required live video transcoding.
3. **Flexible Serialization**: Serialized lists (such as `genres`, `artists`, `folders`) are stored as JSON arrays or comma-separated strings.
4. **Resilient Data Parsing**: Standard fallback logic when metadata (NFO files, ID3 tags, ComicInfo) is missing.

---

## 2. Data Models & Schema Specifications

### 2.1 Enums

#### `LibraryType`
Enumerates library categories. String values are normalized to lowercase.

| Enum Value | Display Name | Standard Aliases / Mappings |
| :--- | :--- | :--- |
| `FILM` | "Film" | `films`, `movies` |
| `SERIES` | "Series" | `shows`, `tv` |
| `MUSIC_ALBUM` | "Music Album" | `music`, `music_albums` |
| `MUSIC_VIDEO` | "Music Videos" | `music_videos` |
| `PODCAST` | "Podcast" | `podcasts` |
| `BOOK` | "Book" | `books` |
| `COMIC` | "Comic" | `comics` |
| `PHOTOBOOK` | "Photobook" | `photobooks`, `gallery` |
| `WEB_VOD` | "Web VoD" | `web_vod` |
| `BROADCAST` | "Broadcast" | `broadcast` |
| `VIDEO` | "Video" | `videos` |
| `AUDIO` | "Audio" | `audio` |
| `IMAGE` | "Image" | `images` |
| `DOCUMENT` | "Document" | `documents` |
| `MISC` | "Misc" | `special`, `generic` |

*Fallback rule:* Any unrecognized string defaults to `MISC`.

---

### 2.2 Data Structures

#### `Library`
Representing a media library root.
```json
{
  "id": "UUID String",
  "name": "String",
  "type": "LibraryType (e.g. film, series, music_album, book, photobook, misc)",
  "path": "String (Primary root folder path, optional)",
  "folders": ["String (List of folder paths)"],
  "createdAt": "ISO-8601 DateTime String",
  "lastScannedAt": "ISO-8601 DateTime String (nullable)"
}
```

#### `CreateLibraryRequest`
```json
{
  "name": "String",
  "type": "LibraryType",
  "paths": ["String"]
}
```

#### `Media` (MediaItem)
Top-level group object (e.g. Movie, TV Show, Music Album, Book Series, Photo Gallery).
```json
{
  "id": "UUID String",
  "libraryId": "UUID String",
  "type": "LibraryType",
  "title": "String",
  "originalTitle": "String (nullable)",
  "sortTitle": "String (nullable)",
  "releaseYear": "Integer (nullable)",
  "plot": "String (nullable)",
  "rating": "Double (nullable)",
  "genres": ["String"],
  "studio": "String (nullable)",
  "director": "String (nullable)",
  "artist": "String (nullable)",
  "album": "String (nullable)",
  "albumArtist": "String (nullable)",
  "albumArtistSortOrder": "String (nullable)",
  "artistSortOrder": "String (nullable)",
  "posterPath": "String (nullable)",
  "fanartPath": "String (nullable)",
  "totalDurationMs": "Long (nullable)",
  "isHidden": "Boolean",
  "hiddenAt": "ISO-8601 DateTime String (nullable)",
  "createdAt": "ISO-8601 DateTime String",
  "updatedAt": "ISO-8601 DateTime String",
  "lastPlayedAt": "ISO-8601 DateTime String (nullable)",
  "files": ["List of MediaFile objects"]
}
```

#### Computed Properties on `Media`:
- `mainFiles`: Filter `files` where `category == "MAIN" || category == null`.
- `extraFiles`: Filter `files` where `category == "EXTRA"`.

#### `MediaFile`
A physical file belonging to a `Media` item.
```json
{
  "id": "UUID String",
  "mediaItemId": "UUID String",
  "fileName": "String",
  "fileSize": "Long (bytes)",
  "title": "String (nullable)",
  "category": "String (MAIN or EXTRA, default MAIN)",
  "index": "Integer (track or episode index, nullable)",
  "filePath": "String",
  "isHidden": "Boolean",
  "hiddenAt": "ISO-8601 DateTime String (nullable)",
  "createdAt": "ISO-8601 DateTime String",
  "progress": "PlaybackProgress (nullable)",
  "streamInfo": "MediaStreamInfo (nullable)",
  "seriesInfo": "SeriesInfo (nullable)",
  "musicInfo": "MusicInfo (nullable)"
}
```

#### Computed Properties on `MediaFile`:
- `displayName`: `title` -> `seriesInfo.episodeTitle` -> `fileName` with extension stripped and regex `^\d+\s*-\s*` and underscores removed.
- `derivedResolution`: 
  - If `streamInfo.resolution` is present, use it.
  - Else if `width >= 3840` or `height >= 2160` -> `"4K"`
  - Else if `width >= 1920` or `height >= 1080` -> `"1080p"`
  - Else if `width >= 1280` or `height >= 720` -> `"720p"`
  - Else if `width >= 848` or `height >= 480` -> `"480p"`
  - Else -> `"${height}p"`

#### Embedded Sub-Objects
```json
// MediaStreamInfo
{
  "durationMs": "Long",
  "resolution": "String",
  "containerFormat": "String",
  "videoCodec": "String",
  "audioCodec": "String",
  "width": "Integer",
  "height": "Integer",
  "bitrate": "Long",
  "sampleRate": "Integer"
}

// SeriesInfo
{
  "seasonNumber": "Integer",
  "episodeNumber": "Integer",
  "episodeTitle": "String",
  "plot": "String",
  "airDate": "String"
}

// MusicInfo
{
  "album": "String",
  "albumArtist": "String",
  "albumArtistSortOrder": "String",
  "artist": "String",
  "artistSortOrder": "String",
  "artists": ["String"],
  "trackNumber": "Integer",
  "discNumber": "Integer",
  "totalTracks": "Integer",
  "totalDiscs": "Integer"
}

// PlaybackProgress
{
  "mediaFileId": "UUID String",
  "clientId": "String (default 'default')",
  "positionSeconds": "Integer",
  "durationSeconds": "Integer",
  "pageNumber": "Integer (for books/comics)",
  "totalPages": "Integer (for books/comics)"
}

// PageInfo (for CBZ / gallery page listing)
{
  "name": "String",
  "size": "Long"
}

// ScanStatus
{
  "isScanning": "Boolean",
  "lastScanAt": "ISO-8601 DateTime String (nullable)",
  "filesFound": "Integer",
  "filesProcessed": "Integer",
  "currentLibraryId": "String (nullable)",
  "libraryName": "String (nullable)",
  "currentFile": "String (nullable)",
  "error": "String (nullable)"
}

// SystemInfo
{
  "name": "MediaStation Server",
  "version": "1.0.0",
  "directPlayOnly": true,
  "videoTranscodingAllowed": false
}
```

---

## 3. Database Table Schemas (SQL Equivalents)

The database can be **SQLite**, **PostgreSQL**, **MySQL**, **H2**, or any relational DB.

```sql
CREATE TABLE libraries (
    id VARCHAR(36) PRIMARY KEY,
    name TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    path TEXT,
    created_at TIMESTAMP NOT NULL,
    last_scanned_at TIMESTAMP
);

CREATE TABLE library_folders (
    id VARCHAR(36) PRIMARY KEY,
    library_id VARCHAR(36) NOT NULL REFERENCES libraries(id) ON DELETE CASCADE,
    path TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE media_items (
    id VARCHAR(36) PRIMARY KEY,
    library_id VARCHAR(36) NOT NULL REFERENCES libraries(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    title TEXT NOT NULL,
    original_title TEXT,
    sort_title TEXT,
    release_year INTEGER,
    plot TEXT,
    rating DOUBLE PRECISION,
    genres TEXT, -- Stored as comma-separated string or JSON string array
    studio TEXT,
    director TEXT,
    artist TEXT,
    album TEXT,
    album_artist TEXT,
    album_artist_sort_order TEXT,
    artist_sort_order TEXT,
    poster_path TEXT,
    fanart_path TEXT,
    total_duration_ms BIGINT,
    is_hidden BOOLEAN DEFAULT FALSE,
    hidden_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    last_played_at TIMESTAMP
);

CREATE TABLE media_files (
    id VARCHAR(36) PRIMARY KEY,
    media_item_id VARCHAR(36) NOT NULL REFERENCES media_items(id) ON DELETE CASCADE,
    file_path TEXT NOT NULL UNIQUE,
    file_name TEXT NOT NULL,
    title TEXT,
    file_size BIGINT NOT NULL,
    container_format TEXT,
    video_codec TEXT,
    audio_codec TEXT,
    resolution TEXT,
    width INTEGER,
    height INTEGER,
    bitrate BIGINT,
    duration_ms BIGINT,
    is_hidden BOOLEAN DEFAULT FALSE,
    hidden_at TIMESTAMP,
    season_number INTEGER,
    episode_number INTEGER,
    episode_title TEXT,
    plot TEXT,
    air_date TEXT,
    track_number INTEGER,
    disc_number INTEGER,
    track_artist TEXT,
    album TEXT,
    album_artist TEXT,
    album_artist_sort_order TEXT,
    artist TEXT,
    artist_sort_order TEXT,
    artists TEXT, -- Stored as comma-separated or JSON
    total_tracks INTEGER,
    total_discs INTEGER,
    sample_rate INTEGER,
    thumbnail_path TEXT,
    category VARCHAR(20) DEFAULT 'MAIN',
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE subtitles (
    id VARCHAR(36) PRIMARY KEY,
    media_file_id VARCHAR(36) NOT NULL REFERENCES media_files(id) ON DELETE CASCADE,
    language VARCHAR(10) DEFAULT 'und',
    title TEXT,
    format VARCHAR(20) NOT NULL,
    file_path TEXT,
    is_embedded BOOLEAN DEFAULT FALSE,
    stream_index INTEGER
);

CREATE TABLE playback_progress (
    id VARCHAR(36) PRIMARY KEY,
    media_file_id VARCHAR(36) NOT NULL REFERENCES media_files(id) ON DELETE CASCADE,
    client_id VARCHAR(100) DEFAULT 'default',
    position_seconds INTEGER DEFAULT 0,
    duration_seconds INTEGER DEFAULT 0,
    page_number INTEGER,
    total_pages INTEGER,
    is_finished BOOLEAN DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE server_settings (
    key VARCHAR(100) PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

---

## 4. Storage Paths & Directory Structure

All runtime application data (database, metadata assets, cache) reside inside the user's home directory:

- Root App Dir: `~/.mediastation/`
- Database: `~/.mediastation/mediastation` (or `~/.mediastation/db.sqlite`)
- Metadata Covers (Audio Artwork): `~/.mediastation/metadata/covers/`
- Metadata Video Thumbnails: `~/.mediastation/metadata/thumbnails/`
- Metadata Posters: `~/.mediastation/metadata/posters/`
- Cache Directory (Extracted CBZ pages, etc.): `~/.mediastation/cache/`

---

## 5. REST API Specification

Base URL path: `/api/v1`

### 5.1 Root & Health
- **`GET /`**
  - **Response:** `200 OK`
  - **Body:** `{"status": "MediaStation Server is running"}`

### 5.2 Libraries API
- **`GET /api/v1/libraries`**
  - **Response:** `200 OK` -> `List<Library>`
- **`POST /api/v1/libraries`**
  - **Request Body:** `CreateLibraryRequest`
  - **Behavior:** Generates UUID for Library, inserts `libraries` record and `library_folders` records for each path.
  - **Response:** `201 Created` -> `Library`
- **`GET /api/v1/libraries/{id}`**
  - **Response:** `200 OK` -> `Library` or `404 Not Found`
- **`DELETE /api/v1/libraries/{id}`**
  - **Behavior:** Deletes library (cascades via foreign keys).
  - **Response:** `200 OK` -> `{"success": true}` or `400 Bad Request`
- **`POST /api/v1/libraries/{id}/scan`**
  - **Behavior:** Triggers library scan asynchronously in a background worker/thread.
  - **Response:** `200 OK` -> `{"message": "Scan initiated"}`
- **`GET /api/v1/libraries/{id}/media`**
  - **Query Parameters:**
    - `search` (string, optional): Case-insensitive match on title, originalTitle, artist, album, albumArtist.
    - `offset` (long, default 0)
    - `limit` (int, default 40)
  - **Ordering:**
    `albumArtistSortOrder ASC NULLS LAST`, `artistSortOrder ASC NULLS LAST`, `albumArtist ASC NULLS LAST`, `artist ASC NULLS LAST`, `sortTitle ASC NULLS LAST`, `title ASC NULLS LAST`.
  - **Response:** `200 OK` -> `List<Media>` (excluding files list for list view efficiency).

### 5.3 Media API
- **`GET /api/v1/media/recent`**
  - **Query Parameters:** `offset` (default 0), `limit` (default 40).
  - **Ordering:** `lastPlayedAt DESC`.
  - **Response:** `200 OK` -> `List<Media>`
- **`GET /api/v1/media/{id}`**
  - **Response:** `200 OK` -> Detailed `Media` object with populated `files` list (sorted by `seasonNumber`, `episodeNumber`, `trackNumber`, `fileName`). `404 Not Found` if missing.

### 5.4 Files & Pages API
- **`GET /api/v1/files/{id}`**
  - **Response:** `200 OK` -> `MediaFile` details (with `progress`, `streamInfo`, `seriesInfo`, `musicInfo`) or `404 Not Found`.
- **`GET /api/v1/files/{id}/pages`**
  - **Behavior:** If file is a directory, returns file list of image files. If file is a `.cbz` ZIP archive, reads ZIP entry list and returns image file names and uncompressed sizes sorted alphabetically.
  - **Response:** `200 OK` -> `List<PageInfo>`
- **`GET /api/v1/files/{id}/raw`**
- **`GET /api/v1/files/{id}/raw/{filename}`**
  - **Behavior:**
    - If `{filename}` is given: Serves page file inside directory or CBZ cache.
    - Else: Serves raw physical file with `Content-Disposition: inline` and matching MIME type (`image/jpeg`, `image/png`, `application/pdf`, `application/x-cbz`, etc.).

### 5.5 Playback Progress API
- **`POST /api/v1/playback/progress`**
  - **Request Body:** `PlaybackProgress`
  - **Finished Logic:**
    - For Books/Comics (`totalPages` and `pageNumber` present): `isFinished = (totalPages > 0 && pageNumber >= totalPages - 1)`
    - For Video/Audio: `isFinished = (durationSeconds > 0 && positionSeconds >= durationSeconds * 0.92)`
  - **Behavior:** Upserts `playback_progress` table by `(media_file_id, client_id)` key. Also updates parent `media_items.last_played_at = CURRENT_TIMESTAMP`.
  - **Response:** `200 OK` -> `{"success": true}`

### 5.6 Streaming & Assets API
- **`GET /api/v1/stream/{id}`**
  - **Behavior:** Serves physical media file supports HTTP Partial Content (HTTP 206 `Range` header).
- **`GET /api/v1/images/{id}/poster`**
  - **Response:** Image file response (`Content-Type: image/jpeg`, `image/png`, `image/webp`). `404 Not Found` with `Cache-Control: no-store` if poster missing.
- **`GET /api/v1/images/{id}/fanart`**
  - **Response:** Fanart image file or `404 Not Found`.
- **`GET /api/v1/images/{id}/thumbnail`**
  - **Response:** Thumbnail image file. Fallback: serves first page of CBZ/gallery if thumbnail unavailable.

### 5.7 System & Scan API
- **`GET /api/v1/system/info`**
  - **Response:** `200 OK` -> `SystemInfo`
- **`GET /api/v1/scan/status`**
  - **Response:** `200 OK` -> `ScanStatus`

---

## 6. Media Scanners & Extraction Algorithms

The scanner engine operates on a per-library basis.

### 6.1 General Scanner Lifecycle
1. Retrieve library type and configured folder paths.
2. Initialize `processedFiles = Set<String>()`.
3. Instantiate appropriate scanner based on `LibraryType`.
4. Run `scanner.scan(libraryId, folder)` for each path.
5. Execute `scanner.cleanup(libraryId)`:
   - Query DB for all files recorded under this library.
   - For any DB file path not in `processedFiles` and not existing on disk, delete record from `media_files`.
   - Delete any `media_items` that no longer have any `media_files`.
6. Update `libraries.last_scanned_at = CURRENT_TIMESTAMP`.

---

### 6.2 Supported File Extensions

```
Video: mp4, mkv, avi, mov, webm, wmv, flv, ogv, 3gp
Audio: mp3, flac, wav, ogg, opus, aac, wma, m4b, aiff
Book: pdf, epub, mobi, azw, azw3, djvu
Comic: cbz, cbr, cb7, cbt, cba
Image: jpg, jpeg, png, webp, gif, bmp, tiff, tbn, heic, heif
Document: txt, md, doc, docx, xls, xlsx, ppt, pptx, rtf, odt, ods, odp
Subtitle: srt, ass, ssa, vtt, sub, idx
```

---

### 6.3 Film Scanner (`FILM`)
- **Folder Mode** (if folder contains video):
  - Check for `movie.nfo` or `<foldername>.nfo` -> parse XML.
  - Create/Update `MediaItem` for the folder.
  - Find posters (`poster.jpg`, `cover.jpg`, `folder.jpg`) and fanart (`fanart.jpg`, `backdrop.jpg`).
  - First video file = `MAIN` category. Subsequent videos = `EXTRA` category.
  - Look for matching subtitles (`<videoname>.<lang>.<ext>`) and parse language.
- **Single File Mode** (loose video files directly under root):
  - Check for `<filename>.nfo`.
  - Create/Update `MediaItem` from filename.

---

### 6.4 Series Scanner (`SERIES`)
1. Top-level folders under root represent TV Shows.
2. Parse `tvshow.nfo` inside show directory for show-level metadata.
3. Process root show files (e.g. extras/featurettes) into `MediaFiles` with `category = "EXTRA"`.
4. Iterate season subdirectories.
5. Process video files as `MAIN` episodes.
6. Check for episode NFO (`<episodesource>.nfo`) for season, episode number, episode title, plot, air date.
7. Detect matching subtitle files.

---

### 6.5 Music Scanner (`MUSIC_ALBUM`)
1. Walk directory tree recursively for audio files.
2. Extract ID3 / FLAC metadata:
   - `album` (fallback: "Unknown Album")
   - `artist` (fallback: "Unknown Artist")
   - `albumArtist` (fallback: `artist`)
   - `title` (fallback: filename)
   - `year`, `trackNumber`, `discNumber`, `totalTracks`, `totalDiscs`
   - `durationMs`, `bitrate`, `sampleRate`, `audioCodec`
3. Embedded artwork extraction:
   - Extract image bytes from tag. Compute MD5 hash of image bytes. Save to `~/.mediastation/metadata/covers/{hash}.jpg`.
   - Fallback: Look for sidecar poster file in parent directory.
4. Create/Update `MediaItem` grouped by `(albumArtist, album)`.

---

### 6.6 Book / Comic Scanner (`BOOK`, `COMIC`)
1. Walk directory tree. Folders containing `.cbz` or `.pdf` files are scanned.
2. Flatten directory relative path for series title (e.g., `Author - Series Name`).
3. Parse `ComicInfo.xml` inside `.cbz` zip archive if present:
   - XML Fields: `<Title>`, `<Series>`, `<Number>`, `<Volume>`, `<Summary>`, `<Year>`, `<Month>`, `<Day>`.
4. Series Poster generation:
   - Pick first volume/chapter file.
   - Extract first image entry from `.cbz` archive.
   - Save extracted image to `~/.mediastation/metadata/posters/{mediaItemId}.jpg`.

---

### 6.7 Photobook Scanner (`PHOTOBOOK`)
1. Recursive gallery scan.
2. Construct gallery title from relative directory path (`Parent - Subfolder`).
3. For image files: Extract image width and height.
4. For video files: Generate video thumbnail using FFmpeg and extract video dimensions.

---

### 6.8 Generic Scanner (`VIDEO`, `AUDIO`, `IMAGE`, `MISC`)
1. Create a single `MediaItem` for the entire library root directory.
2. Recursively add all media files (`MAIN` category).
3. Extract video dimensions and generate thumbnails for video files.

---

## 7. External Tool Integrations & Helpers

### 7.1 NFO XML Parser
Supported elements under root `<movie>`, `<tvshow>`, or `<episodedetails>` tags:
- `<title>`
- `<originaltitle>`
- `<year>`
- `<plot>`
- `<rating>` or `<ratings><rating><value>`
- `<studio>`
- `<director>`
- `<season>`
- `<episode>`
- `<aired>` or `<premiered>`
- Multiple `<genre>` tags

---

### 7.2 FFmpeg & FFprobe CLI Integration
If system binaries `ffmpeg` and `ffprobe` are present in PATH:

1. **Extract Video Dimensions:**
   ```bash
   ffprobe -v error -select_streams v:0 -show_entries stream=width,height -of csv=p=0 "<filepath>"
   ```
   *Returns:* `1920,1080`

2. **Generate Video Thumbnail:**
   ```bash
   ffmpeg -y -i "<filepath>" -ss 00:00:01 -vframes 1 -q:v 2 "<outputpath.jpg>"
   ```
   *Cache Key:* MD5 hash of video file absolute path saved in `~/.mediastation/metadata/thumbnails/{md5}.jpg`.

---

### 7.3 CBZ Cache & Page Extraction
1. `.cbz` files are ZIP archives.
2. Page list: Read entries, filter out directories, filter image extension (`jpg`, `png`, `webp`), sort alphabetically.
3. Cache location: `~/.mediastation/cache/cbz/{fileId}/{pageName}`
4. Cache Cleanup: When cache size exceeds **100 MB**, delete oldest cached files (by `lastModified`) until total size falls below **70 MB**.

---

## 8. Network & Service Discovery (mDNS)

To allow client applications to automatically discover the server on the local network:

- **Protocol:** mDNS / Zeroconf
- **Service Name:** `MediaStation Server`
- **Service Type:** `_mediastation._tcp.local.`
- **Default Port:** `9090`
- **Text Record:** `MediaStation Server`

---

## 9. AI Implementation Checklist

When generating code to rewrite MediaStation Server in your target language:

- [ ] **1. Models & Database**: Define database schema with Foreign Key CASCADE constraints and unique index on `media_files.file_path`.
- [ ] **2. Configuration & Paths**: Ensure app data directory `~/.mediastation` and subdirectories (`covers`, `thumbnails`, `posters`, `cache`) are auto-created at startup.
- [ ] **3. REST Framework**: Set up HTTP routes matching `/api/v1/*` contracts. Enable CORS and HTTP Range support (`206 Partial Content`).
- [ ] **4. Media Scanners**: Implement the strategy pattern for Scanners (`FilmScanner`, `SeriesScanner`, `MusicScanner`, `BookScanner`, `PhotobookScanner`, `GenericScanner`).
- [ ] **5. External Helpers**:
  - FFmpeg CLI wrapper (`ffprobe` dimensions & `ffmpeg` thumbnail generator).
  - Audio tag reader (ID3v2, Vorbis, MP4 tags).
  - ZIP/CBZ image extractor & ComicInfo.xml parser.
  - NFO XML metadata parser.
- [ ] **6. Playback Progress**: Upsert playback progress and compute `isFinished` state (>= 92% completion or last page).
- [ ] **7. Cache Cleanup**: Implement LRU cache eviction routine for extracted book/comic pages.
- [ ] **8. Local Network Discovery**: Register `_mediastation._tcp.local.` on port 9090 via mDNS.
