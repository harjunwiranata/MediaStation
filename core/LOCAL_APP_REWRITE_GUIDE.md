# MediaStation Local Core Engine Rewrite Guide (AI-Friendly Specification)

This specification provides a complete, framework-agnostic architectural and technical guide to build or port the **MediaStation Local Core Engine**—a UI-decoupled, local-first media database, scanning engine, metadata extractor, and streaming provider.

It is designed for building standalone embedded cores, native engines, or backend-decoupled local media management apps in **Kotlin Multiplatform (KMP)**, **Rust**, **C++**, **Go**, **Swift**, **C#/.NET**, or **TypeScript/Node.js**.

---

## Table of Contents
1. [Architecture Overview & Design Principles](#1-architecture-overview--design-principles)
2. [Data Models & Schema Specifications](#2-data-models--schema-specifications)
3. [Database Schema (SQLite / SQL Equivalent)](#3-database-schema-sqlite--sql-equivalent)
4. [Media Scanning & Metadata Extraction Engine](#4-media-scanning--metadata-extraction-engine)
5. [Artwork & Asset Pipeline](#5-artwork--asset-pipeline)
6. [Local Streaming & Asset Provider Engine](#6-local-streaming--asset-provider-engine)
7. [Playback State & Progress Engine](#7-playback-state--progress-engine)
8. [Background Watcher & Event Bus](#8-background-watcher--event-bus)
9. [AI Implementation Checklist](#9-ai-implementation-checklist)

---

## 1. Architecture Overview & Design Principles

The **MediaStation Local Core Engine** operates completely independent of any UI layer. It manages disk scanning, metadata extraction, local database indexing, local file caching, byte-range streaming, and playback progress persistence.

```
 +-------------------------------------------------------------------------+
 |                      MediaStation Local Core Engine                     |
 |                                                                         |
 |  +-------------------------------------------------------------------+  |
 |  |                          Service Facade                           |  |
 |  |   (LibraryService, ScannerService, PlaybackService, AssetService) |  |
 |  +-------------------------------------------------------------------+  |
 |            |                                           |                |
 |  +-------------------+                       +--------------------+     |
 |  | Local Event Bus / |                       | Direct Byte-Stream |     |
 |  | Flow State Stream |                       | Provider Engine    |     |
 |  +-------------------+                       +--------------------+     |
 |            |                                           |                |
 |  +-------------------------------------------------------------------+  |
 |  |                    Database & Repository Layer                    |  |
 |  |            (SQLite / SQLDelight / Room / Embedded DB)             |  |
 |  +-------------------------------------------------------------------+  |
 |            |                                           |                |
 |  +-------------------+                       +--------------------+     |
 |  |  Scanner Engine   |                       | Asset & Thumbnail  |     |
 |  | (Directory Crawl) |                       | Disk Cache         |     |
 |  +-------------------+                       +--------------------+     |
 |            |                                           |                |
 |  +-------------------------------------------------------------------+  |
 |  |                   Format-Specific Parsers                         |  |
 |  | (NFO, ID3/FLAC, ComicInfo.xml, EPUB OPF, EXIF, FFprobe/Extractor)  |  |
 |  +-------------------------------------------------------------------+  |
 |                                    |                                    |
 |  +-------------------------------------------------------------------+  |
 |  |                     Platform File Abstraction                     |  |
 |  |        (POSIX File IO, Java NIO, Win32, Android SAF / Storage)    |  |
 |  +-------------------------------------------------------------------+  |
 +-------------------------------------------------------------------------+
```

### Core Design Principles
1. **Source of Truth is the Disk**: The filesystem is the authority. Files deleted or moved on disk are automatically cleaned up or re-indexed in the local database.
2. **Zero UI Coupling**: The core engine emits reactive streams/events (`ScanStatus`, `LibraryUpdate`, `PlaybackProgressState`) and provides data methods. It contains no Android `View`, Compose, or UI code.
3. **Local-First & Offline First**: Operates 100% locally with zero internet dependencies required for baseline scanning, metadata extraction, and media serving.
4. **Byte-Range Streaming Support**: Serves raw media bytes directly via stream channels or an optional lightweight local loopback HTTP server (`127.0.0.1`) for player frameworks (e.g. ExoPlayer, VLC, MPV) that require URL endpoints.
5. **Incremental & Non-Blocking**: Scans happen asynchronously in background workers/coroutines. Incremental scans skip unchanged files using modification timestamps (`mtime`) and file sizes.

---

## 2. Data Models & Schema Specifications

### 2.1 Taxonomies & Enums

#### `LibraryType`
Defines standard media categories supported by the engine.

| Enum Key | Display Name | Category | Standard Extensions |
| :--- | :--- | :--- | :--- |
| `FILM` | Film | Managed | `.mp4`, `.mkv`, `.avi`, `.mov`, `.m2ts`, `.webm` |
| `SERIES` | Series | Managed | `.mp4`, `.mkv`, `.avi`, `.mov`, `.webm` |
| `MUSIC_ALBUM` | Music Album | Managed | `.mp3`, `.flac`, `.m4a`, `.aac`, `.ogg`, `.opus`, `.wav` |
| `MUSIC_VIDEO` | Music Videos | Managed | `.mp4`, `.mkv`, `.avi` |
| `PODCAST` | Podcast | Managed | `.mp3`, `.m4a`, `.aac`, `.opus` |
| `BOOK` | Book | Managed | `.epub`, `.pdf`, `.mobi`, `.azw3`, `.txt` |
| `COMIC` | Comic | Managed | `.cbz`, `.cbr`, `.cb7`, `.pdf` |
| `PHOTOBOOK` | Photobook | Managed | `.zip`, `.cbz`, `.jpg`, `.jpeg`, `.png`, `.webp` |
| `WEB_VOD` | Web VoD | Managed | `.mp4`, `.mkv`, `.webm` |
| `BROADCAST` | Broadcast | Managed | `.ts`, `.m2ts`, `.mp4`, `.mkv` |
| `VIDEO` | Video | Loose | Any video file |
| `AUDIO` | Audio | Loose | Any audio file |
| `IMAGE` | Image | Loose | Any image file |
| `DOCUMENT` | Document | Loose | Any document file |
| `MISC` | Misc | Loose | Unclassified files |

---

### 2.2 Data Structures

#### `Library`
```json
{
  "id": "UUID String",
  "name": "String",
  "type": "LibraryType Enum",
  "path": "String (Primary folder path)",
  "folders": ["Array of Strings (All watched directory paths)"],
  "createdAt": "ISO-8601 Timestamp",
  "lastScannedAt": "ISO-8601 Timestamp"
}
```

#### `Media` (MediaItem)
```json
{
  "id": "UUID String",
  "libraryId": "UUID String",
  "type": "LibraryType Enum",
  "title": "String",
  "originalTitle": "String (Nullable)",
  "sortTitle": "String (Nullable)",
  "releaseYear": "Integer (Nullable)",
  "plot": "String (Nullable)",
  "rating": "Double (Nullable, 0.0 - 10.0)",
  "genres": ["Array of Strings"],
  "studio": "String (Nullable)",
  "director": "String (Nullable)",
  "artist": "String (Nullable)",
  "album": "String (Nullable)",
  "albumArtist": "String (Nullable)",
  "albumArtistSortOrder": "String (Nullable)",
  "artistSortOrder": "String (Nullable)",
  "posterPath": "String (Relative path to cached artwork)",
  "fanartPath": "String (Relative path to cached background art)",
  "totalDurationMs": "Long (Nullable)",
  "isHidden": "Boolean (Default false)",
  "hiddenAt": "ISO-8601 Timestamp (Nullable)",
  "createdAt": "ISO-8601 Timestamp",
  "updatedAt": "ISO-8601 Timestamp",
  "lastPlayedAt": "ISO-8601 Timestamp (Nullable)",
  "files": ["Array of MediaFile objects"]
}
```

#### `MediaFile`
```json
{
  "id": "UUID String",
  "mediaItemId": "UUID String",
  "fileName": "String (e.g., 'Episode 01.mkv')",
  "filePath": "String (Absolute local file path)",
  "fileSize": "Long (Bytes)",
  "title": "String (Nullable)",
  "category": "String ('MAIN' or 'EXTRA')",
  "index": "Integer (Nullable, Track/Episode number)",
  "isHidden": "Boolean (Default false)",
  "createdAt": "ISO-8601 Timestamp",
  "progress": "PlaybackProgress Object (Nullable)",
  "streamInfo": "MediaStreamInfo Object (Nullable)",
  "seriesInfo": "SeriesInfo Object (Nullable)",
  "musicInfo": "MusicInfo Object (Nullable)"
}
```

#### Specialized Sub-Structures

##### `MediaStreamInfo`
```json
{
  "durationMs": "Long (Nullable)",
  "resolution": "String (e.g., '1080p', '4K')",
  "containerFormat": "String (e.g., 'matroska', 'mp4')",
  "videoCodec": "String (e.g., 'h264', 'hevc', 'vp9', 'av1')",
  "audioCodec": "String (e.g., 'aac', 'flac', 'opus', 'mp3')",
  "width": "Integer (Nullable)",
  "height": "Integer (Nullable)",
  "bitrate": "Long (Nullable, bits per sec)",
  "sampleRate": "Integer (Nullable, Hz)"
}
```

##### `SeriesInfo`
```json
{
  "seasonNumber": "Integer (Nullable)",
  "episodeNumber": "Integer (Nullable)",
  "episodeTitle": "String (Nullable)",
  "plot": "String (Nullable)",
  "airDate": "String (Nullable, YYYY-MM-DD)"
}
```

##### `MusicInfo`
```json
{
  "album": "String (Nullable)",
  "albumArtist": "String (Nullable)",
  "albumArtistSortOrder": "String (Nullable)",
  "artist": "String (Nullable)",
  "artistSortOrder": "String (Nullable)",
  "artists": ["Array of Strings"],
  "trackNumber": "Integer (Nullable)",
  "discNumber": "Integer (Nullable)",
  "totalTracks": "Integer (Nullable)",
  "totalDiscs": "Integer (Nullable)"
}
```

##### `PlaybackProgress`
```json
{
  "mediaFileId": "UUID String",
  "clientId": "String (Default 'local')",
  "positionSeconds": "Integer",
  "durationSeconds": "Integer (Nullable)",
  "pageNumber": "Integer (Nullable, for books/comics)",
  "totalPages": "Integer (Nullable)",
  "updatedAt": "ISO-8601 Timestamp"
}
```

##### `ScanStatus`
```json
{
  "isScanning": "Boolean",
  "lastScanAt": "ISO-8601 Timestamp (Nullable)",
  "filesFound": "Integer",
  "filesProcessed": "Integer",
  "currentLibraryId": "UUID String (Nullable)",
  "libraryName": "String (Nullable)",
  "currentFile": "String (Nullable)",
  "error": "String (Nullable)"
}
```

---

## 3. Database Schema (SQLite / SQL Equivalent)

The local engine uses SQLite as its storage and search engine.

```sql
-- 1. Libraries Table
CREATE TABLE IF NOT EXISTS libraries (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    path TEXT,
    folders TEXT NOT NULL DEFAULT '[]', -- Stored as JSON array or comma-separated string
    created_at TEXT NOT NULL,
    last_scanned_at TEXT
);

-- 2. Media Items Table
CREATE TABLE IF NOT EXISTS media_items (
    id TEXT PRIMARY KEY NOT NULL,
    library_id TEXT NOT NULL,
    type TEXT NOT NULL,
    title TEXT NOT NULL,
    original_title TEXT,
    sort_title TEXT,
    release_year INTEGER,
    plot TEXT,
    rating REAL,
    genres TEXT DEFAULT '[]', -- Stored as JSON array
    studio TEXT,
    director TEXT,
    artist TEXT,
    album TEXT,
    album_artist TEXT,
    album_artist_sort_order TEXT,
    artist_sort_order TEXT,
    poster_path TEXT,
    fanart_path TEXT,
    total_duration_ms INTEGER,
    is_hidden INTEGER DEFAULT 0,
    hidden_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    last_played_at TEXT,
    FOREIGN KEY (library_id) REFERENCES libraries(id) ON DELETE CASCADE
);

-- 3. Media Files Table
CREATE TABLE IF NOT EXISTS media_files (
    id TEXT PRIMARY KEY NOT NULL,
    media_item_id TEXT NOT NULL,
    file_name TEXT NOT NULL,
    file_path TEXT NOT NULL UNIQUE,
    file_size INTEGER,
    title TEXT,
    category TEXT DEFAULT 'MAIN', -- 'MAIN' or 'EXTRA'
    file_index INTEGER,
    is_hidden INTEGER DEFAULT 0,
    hidden_at TEXT,
    created_at TEXT NOT NULL,
    
    -- Embedded Stream Info
    duration_ms INTEGER,
    resolution TEXT,
    container_format TEXT,
    video_codec TEXT,
    audio_codec TEXT,
    width INTEGER,
    height INTEGER,
    bitrate INTEGER,
    sample_rate INTEGER,

    -- Embedded Series Metadata
    season_number INTEGER,
    episode_number INTEGER,
    episode_title TEXT,
    episode_plot TEXT,
    air_date TEXT,

    -- Embedded Music Metadata
    music_album TEXT,
    music_album_artist TEXT,
    music_artist TEXT,
    music_track_number INTEGER,
    music_disc_number INTEGER,

    FOREIGN KEY (media_item_id) REFERENCES media_items(id) ON DELETE CASCADE
);

-- 4. Playback Progress Table
CREATE TABLE IF NOT EXISTS playback_progress (
    media_file_id TEXT NOT NULL,
    client_id TEXT NOT NULL DEFAULT 'local',
    position_seconds INTEGER NOT NULL,
    duration_seconds INTEGER,
    page_number INTEGER,
    total_pages INTEGER,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (media_file_id, client_id),
    FOREIGN KEY (media_file_id) REFERENCES media_files(id) ON DELETE CASCADE
);

-- 5. Playlists Table
CREATE TABLE IF NOT EXISTS playlists (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

-- 6. Playlist Items Table
CREATE TABLE IF NOT EXISTS playlist_items (
    playlist_id TEXT NOT NULL,
    media_file_id TEXT NOT NULL,
    position INTEGER NOT NULL,
    PRIMARY KEY (playlist_id, media_file_id),
    FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE,
    FOREIGN KEY (media_file_id) REFERENCES media_files(id) ON DELETE CASCADE
);

-- 7. Full-Text Search (FTS5 Index)
CREATE VIRTUAL TABLE IF NOT EXISTS media_fts USING fts5(
    media_item_id UNINDEXED,
    title,
    original_title,
    artist,
    album,
    genres,
    plot
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_media_items_library_id ON media_items(library_id);
CREATE INDEX IF NOT EXISTS idx_media_items_type ON media_items(type);
CREATE INDEX IF NOT EXISTS idx_media_files_item_id ON media_files(media_item_id);
CREATE INDEX IF NOT EXISTS idx_media_files_path ON media_files(file_path);
```

---

## 4. Media Scanning & Metadata Extraction Engine

### 4.1 Base Scanner Lifecycle

Every library scan follows a deterministic 4-phase lifecycle:

```
+-------------------------------------------------------------------+
|                        1. Discovery Phase                         |
|  Recursively crawl library folder(s). Collect candidate files.    |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                        2. Extraction Phase                        |
|  Filter candidate files. Read NFO/ID3/ComicInfo/EXIF/FFprobe tags |
|  Grouping files into parent MediaItems (Albums, Series, Movies).  |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                     3. Persistence Phase (DB)                     |
|  Atomic transaction: UPSERT MediaItems, MediaFiles, and FTS index.|
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                     4. Cleanup Phase (Reconcile)                  |
|  Delete DB files missing from disk. Remove empty parent items.    |
+-------------------------------------------------------------------+
```

---

### 4.2 Specialized Scanner Algorithms

#### 1. Film Scanner (`FILM`)
- **Folder Structure**: Top-level subfolders or loose files inside library folders.
- **NFO Metadata**: Looks for `movie.nfo` inside the movie folder, or `<filename>.nfo` adjacent to the video file.
- **Filename Regex Fallback**:
  - `(?i)^(.+?)[._\s]\(?(\d{4})\)?[._\s]` -> Title: Group 1, Year: Group 2.
- **Artwork Search**: `poster.jpg`/`poster.png`, `cover.jpg`, `fanart.jpg`/`fanart.png`, `backdrop.jpg`.

#### 2. Series Scanner (`SERIES`)
- **Grouping Unit**: TV Series directory containing Season folders or episode files.
- **NFO Metadata**: Looks for `tvshow.nfo` in the series root directory. Episode NFOs `<episodename>.nfo`.
- **Episode Filename Regex**:
  - `(?i)[sS](\d{1,2})[eE](\d{1,2})` -> Season: Group 1, Episode: Group 2.
  - `(?i)(\d{1,2})x(\d{1,2})` -> Season: Group 1, Episode: Group 2.
- **Main vs Extra Categorization**:
  - Files containing "sample", "trailer", "featurette", "behindthescenes" in path -> category = `EXTRA`.
  - Matched episodes -> category = `MAIN`.

#### 3. Music Scanner (`MUSIC_ALBUM`)
- **Grouping Unit**: Album (grouped by `albumArtist` + `album` or parent directory).
- **Embedded Tag Extraction** (ID3v2, FLAC Vorbis, MP4 Atoms):
  - `TRCK` / `TRACKNUMBER` -> `trackNumber` & `totalTracks`
  - `TPOS` / `DISCNUMBER` -> `discNumber` & `totalDiscs`
  - `TPE1` / `ARTIST` -> `artist`
  - `TPE2` / `ALBUMARTIST` -> `albumArtist`
  - `TALB` / `ALBUM` -> `album`
  - `TDRC` / `DATE` / `YEAR` -> `releaseYear`
  - `APIC` / `PICTURE` -> Embedded Album Cover Art.
- **Fallback Directory Cover**: `cover.jpg`, `folder.jpg`, `album.jpg`.

#### 4. Book & Comic Scanner (`BOOK`, `COMIC`)
- **Comic Archives (`.cbz`, `.cbr`, `.zip`)**:
  - Extract `ComicInfo.xml` from root of archive.
  - Parse XML fields: `<Title>`, `<Series>`, `<Number>`, `<Volume>`, `<Summary>`, `<Year>`, `<Writer>`, `<Pencil>`.
  - **Cover Extraction**: Extract page 0 (first image file alphabetically inside archive) to cached artwork directory.
- **EPUB Files (`.epub`)**:
  - Read `META-INF/container.xml` to locate `.opf` path.
  - Parse `.opf` XML: `<dc:title>`, `<dc:creator>`, `<dc:description>`, `<dc:date>`.
  - Extract cover image referenced in manifest meta tag `cover`.
- **PDF Files (`.pdf`)**:
  - Read PDF Document Catalog dictionary (Title, Author, Subject).
  - Render page 1 thumbnail if PDF renderer library is available.

#### 5. Photobook Scanner (`PHOTOBOOK`)
- **Image Archives & Directories**:
  - Groups image sets by directory or `.cbz`/`.zip` container.
  - Extract EXIF/IPTC metadata: Date Taken, Camera Model, Width, Height.
  - Poster: First image in directory/archive.

#### 6. Generic Media Scanners (`VIDEO`, `AUDIO`, `IMAGE`, `DOCUMENT`, `MISC`)
- Loose file scanners without complex NFO/Series grouping.
- One `MediaItem` created per file.
- Uses `FFprobe` / native extractor for duration, resolution, codecs.

---

### 4.3 FFprobe / Native Stream Info Parser Logic

For audio and video files, extract metadata using native media inspection tools or `ffprobe`:

```json
{
  "format": {
    "format_name": "matroska,webm",
    "duration": "7200.500",
    "bit_rate": "5500000"
  },
  "streams": [
    {
      "codec_type": "video",
      "codec_name": "hevc",
      "width": 1920,
      "height": 1080
    },
    {
      "codec_type": "audio",
      "codec_name": "aac",
      "sample_rate": "48000"
    }
  ]
}
```

**Derived Resolution Mapping**:
- Width >= 3840 OR Height >= 2160 -> `"4K"`
- Width >= 1920 OR Height >= 1080 -> `"1080p"`
- Width >= 1280 OR Height >= 720  -> `"720p"`
- Width >= 848  OR Height >= 480  -> `"480p"`
- Otherwise -> `"{Height}p"`

---

## 5. Artwork & Asset Pipeline

### 5.1 Storage Directory Structure

The engine manages a deterministic local asset cache directory:

```
<App_Data_Dir>/
 ├── db/
 │    └── mediastation.db
 ├── cache/
 │    ├── posters/
 │    │    ├── <media_item_id>.webp
 │    │    └── <media_item_id>_thumb.webp
 │    ├── fanart/
 │    │    └── <media_item_id>.webp
 │    └── extracted_pages/
 │         └── <media_file_id>/
 │              ├── page_001.webp
 │              └── page_002.webp
```

### 5.2 Asset Processing Rules
1. **Format Normalization**: Downscale and convert extracted cover images to **WebP** (or JPEG) to optimize disk footprint and memory loading speed.
2. **Standard Poster Size**: Max width **600px** (aspect ratio ~ 1:1.5).
3. **Standard Fanart Size**: Max width **1920px** (aspect ratio ~ 16:9).
4. **Cache Lookup Rule**:
   - Primary: Check local cache file `<cache/posters/<media_id>.webp>`.
   - Secondary: Read embedded artwork from file on disk.
   - Tertiary: Fallback to placeholder/category default icon.

---

## 6. Local Streaming & Asset Provider Engine

### 6.1 Direct Stream Provider Interface

The engine provides a direct file stream interface to supply audio, video, and image byte streams to players:

```kotlin
interface DirectStreamProvider {
    suspend fun openInputStream(mediaFileId: String): InputStream
    suspend fun openRandomAccessFile(mediaFileId: String): RandomAccessFileChannel
    suspend fun getMediaFilePath(mediaFileId: String): String
}
```

### 6.2 HTTP Byte-Range Server Engine (Optional Player Interoperability)

For media player components that only accept HTTP URLs (e.g., standard ExoPlayer or webview elements), the engine runs a tiny local HTTP server bound strictly to `127.0.0.1`:

#### Endpoint: `GET /api/v1/stream/{mediaFileId}`
- **Header Support**: Parses `Range: bytes=start-end`.
- **Response Status**:
  - Full stream: `200 OK`
  - Range request: `206 Partial Content`
  - Invalid range: `416 Range Not Satisfiable`
- **Response Headers**:
  - `Content-Type`: Derived MIME type (e.g. `video/mp4`, `audio/flac`, `video/x-matroska`).
  - `Content-Length`: Chunk byte length.
  - `Content-Range`: `bytes start-end/total`.
  - `Accept-Ranges`: `bytes`.

---

## 7. Playback State & Progress Engine

### 7.1 Progress State Machine & Rules

The playback engine manages real-time watching/reading position with automated debouncing and completion calculations:

```
 [Playback Start] ---> (Position Updates every 5 sec) ---> [Progress DB Saved]
                                                                  |
                                                                  v
                                                        [Completion Check]
                                                        /                \
                                           Pos / Duration >= 90%    Pos / Duration < 5%
                                                 /                          \
                                       [Mark Completed]                 [Reset to 0]
```

#### Progress Rules:
1. **Debounce Delay**: Write position to local DB at most once every **5 seconds**.
2. **Mark as Watched Threshold**: If `positionSeconds / durationSeconds >= 0.90` (90%), mark item as fully watched (set position to 0, update `lastPlayedAt` timestamp).
3. **Ignore Initial Skips**: If `positionSeconds / durationSeconds < 0.05` (5%) when stopping, do not save resume progress.
4. **Book/Comic Page Progress**:
   - `pageNumber`: Currently viewed page index.
   - `totalPages`: Total page count inside comic archive / PDF / EPUB.

---

## 8. Background Watcher & Event Bus

### 8.1 Local Event Bus (`Flow` / Reactive Stream)

The engine exposes a thread-safe event bus emitting engine state transitions:

```kotlin
sealed class CoreEngineEvent {
    data class LibraryAdded(val library: Library) : CoreEngineEvent()
    data class LibraryDeleted(val libraryId: String) : CoreEngineEvent()
    data class ScanStateChanged(val status: ScanStatus) : CoreEngineEvent()
    data class MediaProgressUpdated(val progress: PlaybackProgress) : CoreEngineEvent()
    data class FileSystemChanged(val path: String) : CoreEngineEvent()
}
```

### 8.2 FileSystem Watcher Engine
- Monitors library directory roots using OS-native file observers (`java.nio.file.WatchService`, Android `FileObserver`, Linux `inotify`, Windows `ReadDirectoryChangesW`).
- **Debounced Auto-Scan**: When file create/modify/delete events are detected, waits 3 seconds (debounce) before triggering an incremental scan on the impacted directory.

---

## 9. AI Implementation Checklist

Use this checklist when implementing or auditing the local core engine:

- [ ] **1. Engine Architecture & Decoupling**
  - [ ] Ensure 0 imports from Android UI / Compose / Swing / HTML DOM.
  - [ ] Implement abstract storage & file system interface.

- [ ] **2. Database & Persistence Layer**
  - [ ] Execute DDL script to create `libraries`, `media_items`, `media_files`, `playback_progress`, `playlists`, and `media_fts`.
  - [ ] Implement thread-safe database transaction wrapper.
  - [ ] Set up FTS5 search query logic.

- [ ] **3. Scanner Pipeline**
  - [ ] Implement `FilmScanner` (NFO + regex fallback + poster extraction).
  - [ ] Implement `SeriesScanner` (tvshow.nfo + SxxExx regex + main/extra split).
  - [ ] Implement `MusicScanner` (ID3v2 / FLAC / MP4 atom tag parsing + cover art).
  - [ ] Implement `BookScanner` & `ComicScanner` (ComicInfo.xml + EPUB OPF + page 0 cover).
  - [ ] Implement `PhotobookScanner` (EXIF parsing + directory grouping).
  - [ ] Implement Reconcile / Cleanup phase (delete DB records missing on disk).

- [ ] **4. Asset & Cache Manager**
  - [ ] Create `/cache/posters/` and `/cache/fanart/` directories.
  - [ ] Implement image downscaling and WebP conversion.

- [ ] **5. Streaming & Progress Engine**
  - [ ] Build random-access byte-range stream reader.
  - [ ] Build 5-second debounced `PlaybackProgress` tracker.
  - [ ] Implement 90% completion & 5% reset rule.

- [ ] **6. Observability & Reactive Events**
  - [ ] Expose `ScanStatus` flow/callback stream.
  - [ ] Build filesystem watcher engine with 3s event debouncing.
