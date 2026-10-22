# MediaStation Client UX Specification & Porting Guide

This guide details the **User Experience (UX)** flows, screen layouts, interaction models, and media viewing behaviors of the **MediaStation Client Application**.

It is written in a framework-agnostic manner so that a developer or AI can faithfully implement a MediaStation client in any UI framework or target language (e.g. **Flutter**, **React Native**, **SwiftUI** for iOS/macOS, **Kotlin/Compose Multiplatform**, **Kotlin/Android XML**, **Web/React/Vue/Svelte**, **C#/.NET MAUI/Avalonia**, **Qt/C++**).

---

## Table of Contents
1. [General UX Architecture & Navigation](#1-general-ux-architecture--navigation)
2. [Library Overview Screen](#2-library-overview-screen)
3. [Library Detail Screen & Auto-Forwarding](#3-library-detail-screen--auto-forwarding)
4. [Media Detail Screens (Per Library Type)](#4-media-detail-screens-per-library-type)
   - [4.1 Film / Movie Detail](#41-film--movie-detail)
   - [4.2 TV Series / Show Detail](#42-tv-series--show-detail)
   - [4.3 Music Album Detail](#43-music-album-detail)
   - [4.4 Book / Comic Series Detail](#44-book--comic-series-detail)
   - [4.5 Photobook / Gallery Detail](#45-photobook--gallery-detail)
   - [4.6 Generic Audio & Video Media Detail](#46-generic-audio--video-media-detail)
5. [Player & Viewer Experiences](#5-player--viewer-experiences)
   - [5.1 Music Player & Persistent Mini-Player](#51-music-player--persistent-mini-player)
   - [5.2 Comic & Manga Reader](#52-comic--manga-reader)
   - [5.3 Book & PDF Viewer](#53-book--pdf-viewer)
   - [5.4 Image Gallery Viewer](#54-image-gallery-viewer)
   - [5.5 Video Player & Overlays](#55-video-player--overlays)
6. [Platform Dependencies & Hints for Other Stacks](#6-platform-dependencies--hints-for-other-stacks)

---

## 1. General UX Architecture & Navigation

### 1.1 Screen Flow Diagram
```
                    +------------------------+
                    | Library Overview Screen|
                    +------------------------+
                                |
                                v
                    +------------------------+
                    | Library Detail Grid    |
                    | (Auto-forwards if 1    |
                    |  item present)         |
                    +------------------------+
                                |
                                v
                    +------------------------+
                    | Media Item Detail      |
                    | (Layout varies by type)|
                    +------------------------+
                                |
      +-------------------------+-------------------------+-------------------------+
      |                         |                         |                         |
      v                         v                         v                         v
+-----------+             +-----------+             +-----------+             +-----------+
|   Video   |             |   Music   |             |   Comic/  |             |  Image/   |
|   Player  |             |   Player  |             |Book Reader|             |Photo View |
+-----------+             +-----------+             +-----------+             +-----------+
                                |
                                v
                     +--------------------+
                     | Persistent Mini-   |
                     | Player Bar         |
                     +--------------------+
```

### 1.2 Layout Responsiveness Guidelines
- **Compact View (Phones / Narrow windows):** Single-column vertical layouts, 2–3 item grid columns, bottom navigation/bars.
- **Expanded View (Tablets / Desktop / Wide windows):** Side-by-side split layouts (e.g. Artwork left 40%, details right 60%), 4–5 item grid columns, hover actions.

---

## 2. Library Overview Screen

The root home screen lists all media libraries configured on the connected server.

### 2.1 UX Elements & Features
1. **Top Bar:**
   - App Title ("Libraries").
   - **Refresh Action Button:** Triggers server library reload and clears local image memory/disk cache.
2. **Library Section Headers:**
   - **Quick Access:** Lists synthetic/internal libraries (e.g., "Recently Played" library with ID `recent_internal`).
   - **All Libraries:** Displays user-configured standard libraries.
3. **Library Cards:**
   - Displays **Library Name** (Bold) and **Type Name** (e.g. "Film", "Series", "Music Album", "Book", "Photobook").
   - Right-aligned item count badge (`"{X} items"`).
   - Overflow Menu Button (`...`):
     - **Scan Library:** Fires `POST /api/v1/libraries/{id}/scan` in background.
     - **Delete Library:** Displays a confirm dialog before calling `DELETE /api/v1/libraries/{id}` (hidden for internal/special libraries).
4. **Add Library Button / Card:**
   - Displays a prominent button at the end of the list.
   - Opens **Add Library Dialog**:
     - Library Name text field.
     - Folder Path text field.
     - Horizontal Filter Chip selector for `LibraryType` (Film, Series, Music, Book, Comic, Photobook, etc.).

---

## 3. Library Detail Screen & Auto-Forwarding

Displays all media items contained within a selected library.

### 3.1 UX Elements & Features
1. **Top Bar:**
   - Library Name title.
   - **Search Action:** Toggles top-bar inline text search field.
   - **Clear / Close Search:** Resets query and restores default library view.
   - **Refresh Action:** Forces network reload and clears image cache.
2. **Media Grid:**
   - **Grid Columns:** 3 columns on compact devices, 5 columns on expanded screens.
   - **Media Cards:** Aspect-ratio vertical poster image, item title (1-line truncated), subtitle (Artist name, Release year, or Library Type name).
   - **Infinite Pagination:** Requests items in pages of 40 (`limit=40, offset=X`). Automatically triggers next page request when user scrolls within 10 items of the bottom.
3. **Smart UX Feature - Single-Item Auto-Forwarding:**
   - If a library contains **exactly 1 media item** and no search filter is active, the app automatically skips the Library Detail Grid screen and forwards directly to the `MediaDetailScreen`.
   - When pressing Back from `MediaDetailScreen` in this case, the app pops back to `LibraryListScreen` directly (removing intermediate loop).

---

## 4. Media Detail Screens (Per Library Type)

Depending on `media.type`, the `MediaScreen` picks a dedicated layout tailored to that specific content type.

---

### 4.1 Film / Movie Detail
- **Compact Layout:** Single column vertical scroll.
  - Header: Poster thumbnail (left) + Title, Release Year, Genres (right).
  - Plot summary text block.
  - **Primary Play Button:** Prominent full-width button displaying `Play (Resolution)` (e.g., `"Play (1080p)"` or `"Play (4K)"`).
  - **Alternative Versions (if main files > 1):** Outlined buttons for additional video file versions.
- **Expanded Layout:** Side-by-side split.
  - Left column: Large 240dp elevated poster card.
  - Right column: Headline title, year, genres, plot description, and play buttons.

---

### 4.2 TV Series / Show Detail
- **Hero Backdrop:** Top 200dp fanart image header with crop overlay.
- **Header Info:** Series Title, Release Year, Genres, Plot summary (3-line truncated).
- **Season Rows (Horizontal Scrolling):**
  - Grouped into rows by Season (`"Season 1"`, `"Season 2"`, etc.) plus an `"Extras"` row if extra files exist.
  - Each row is a `LazyRow` with horizontal chevron scroll overlay buttons on left/right edges for mouse/desktop pointer navigation.
- **Episode Card:**
  - 16:9 aspect ratio video thumbnail image.
  - Episode badge overlay at bottom-right (`"E1"`, `"E2"`).
  - Episode display title and derived resolution label (`"1080p"`).
  - Tapping an episode card opens `VideoPlayerScreen`.

---

### 4.3 Music Album Detail
- **Compact Layout:**
  - Header: 140dp square album cover art (left) + Title, Artist (primary color), Album, Year, Song Count (right).
  - Horizontal divider line.
  - Track list below.
- **Expanded Layout:**
  - Left Column (40%): Album cover art & metadata summary.
  - Right Column (60%): Track list.
- **Track List Items:**
  - Leading: Track number or index (right-aligned).
  - Title: Song display name.
  - Subtitle: Track artist (if different from album artist) + Formatted Duration (`M:SS`).
  - Tapping a track plays song in full `MusicPlayer` / `MusicMiniPlayer`.

---

### 4.4 Book / Comic Series Detail
- **Header:** Poster image, Series title, Year, Genres, Plot summary.
- **Volume / Issue Grid:**
  - Responsive vertical grid (3 columns compact, 5 expanded).
  - Each card displays issue thumbnail/cover image and Volume name/index.
  - Tapping a volume opens `ComicPlayerScreen` (for `.cbz` archives) or `BookPlayerScreen` (for `.pdf` documents).

---

### 4.5 Photobook / Gallery Detail
- **Staggered Grid:**
  - Vertical staggered grid (3 columns compact, 5 expanded).
  - Preserves native image/video aspect ratio (calculated from `streamInfo.width` and `height`).
- **Video Items:**
  - Video items display a semi-transparent dark overlay with a white Play arrow icon.
- Tapping an item opens `ImagePlayerScreen` or `VideoPlayerScreen`.

---

### 4.6 Generic Audio & Video Media Detail
- **Audio Files:** Clean vertical list displaying track icon, display name, and formatted duration (`M:SS`).
- **Video Files:** 16:9 staggered thumbnail grid with play overlay icons.

---

## 5. Player & Viewer Experiences

---

### 5.1 Music Player & Persistent Mini-Player

#### Persistent Mini-Player Bar
- Stays pinned at the bottom of the screen across all non-player navigation screens whenever music is playing or loaded.
- Displays track artwork thumbnail, track title, artist name, Play/Pause button, and Next track button.
- Tapping the mini-player expands the full `MusicPlayerScreen`.

#### Fullscreen Music Player Screen
- **Artwork:** Large 360dp elevated square cover card.
- **Track Info:** Headline Title, Artist name (primary accent color), Favorite toggle button.
- **Progress Slider:** Scrubbable timeline with elapsed time (left) and total track duration (right) in `M:SS`.
- **Playback Controls:**
  - **Repeat Mode Toggle:** Cycles `OFF` -> `ONE` (Repeat 1) -> `ALL` (Repeat Queue).
  - **Skip Previous Button**
  - **Play / Pause Primary Button** (72dp large button)
  - **Skip Next Button**
  - **Shuffle Toggle:** Highlights primary color when active.

---

### 5.2 Comic & Manga Reader (`ComicPlayerScreen`)

Designed for reading `.cbz` comic archives and image page lists.

#### 3 Reading Modes
1. **Horizontal (LTR):** Standard left-to-right page flipping.
2. **Manga (RTL):** Right-to-left page flipping for Japanese manga.
   - *Auto-Detection:* Automatically defaults to RTL if media genres include `"manga"`.
3. **Vertical (Manhwa):** Continuous vertical scrolling list for webtoons.

#### Dual-Page Spread Mode (Expanded / Tablet / Desktop)
- On wide screens in horizontal modes, pages automatically display in **dual-page spreads** (Page 1 single, Pages 2–3 paired, etc.).

#### Interaction Controls
- **Tap Navigation:**
  - Tap Left 1/3 of screen -> Previous Page (or Next in RTL manga mode).
  - Tap Right 2/3 of screen -> Next Page (or Previous in RTL manga mode).
- **Zooming:** `ZoomableBox` enables double-tap zoom and multi-touch pinch-to-zoom/pan.
- **Progress Syncing:** Page state automatically posts progress updates to `/api/v1/playback/progress` (`pageNumber`, `totalPages`).

---

### 5.3 Book & PDF Viewer (`BookPlayerScreen`)

Designed for reading `.pdf` documents and books.

- Integrated PDF renderer with horizontal pager or continuous vertical scroll.
- Top bar with reading mode switcher (LTR, RTL Manga, Vertical).
- Automatic page progress persistence.

---

### 5.4 Image Gallery Viewer (`ImagePlayerScreen`)

Full-screen high-resolution image viewer for photobook galleries.

- Swipe horizontal pager or side tap gestures (Left 1/3 = Prev, Right 2/3 = Next).
- Full double-tap zoom and pan gesture support.

---

### 5.5 Video Player & Overlays (`VideoPlayerScreen`)

Full-screen video playback engine with native hardware decoding.

#### Overlay Controls (Transient)
- Controls automatically fade out after **3 seconds** of inactivity during playback. Tapping anywhere toggles controls visibility.
- **Top Bar:** Back button, Media Info modal button (displays codec, bitrate, resolution, container), Orientation rotation lock button.
- **Center:** Large 80dp Play/Pause button.
- **Center Buffering:** Circular progress indicator during buffering states.
- **Bottom Bar:**
  - Scrubbable progress slider.
  - **Dual-Layer Progress Track:** Indicates both current position and network buffer progress.
  - Time labels: Elapsed time (left) and Total duration (right) in `MM:SS`.
- **System Integration:**
  - Hides system status bars and navigation bars during playback.
  - Acquires `FLAG_KEEP_SCREEN_ON` wake lock while playing video.

---

## 6. Platform Dependencies & Hints for Other Stacks

If implementing this client in another technology stack, ensure the following core capabilities are available:

| Feature / Requirement | Technical Needs & Hints for Other Stacks |
| :--- | :--- |
| **State Management & Navigation** | Hierarchical stack navigator supporting route parameters (e.g., Flutter `go_router`, React Navigation, SwiftUI `NavigationStack`). |
| **Image Loading & Disk Caching** | Fast image loader with disk/memory caching and crossfade support (e.g., Flutter `cached_network_image`, React Native `react-native-fast-image`, SwiftUI `Kingfisher`). Must allow cache clearing. |
| **Pinch-to-Zoom & Pan Gesture** | Gesture container supporting double-tap zoom, pinch scale, and pan bounds constraint (e.g., Flutter `InteractiveViewer`, React Native `PinchGestureHandler`). |
| **PDF Rendering Engine** | Native or multiplatform PDF rendering library capable of streaming page images by URL or file stream. |
| **Video Player Engine** | High-performance video player supporting HTTP Byte-Range streaming / Partial Content (HTTP 206), H.264/H.265/VP9/AV1 codecs, and surface rendering (e.g., Flutter `video_player` / `media_kit`, React Native `react-native-video`, SwiftUI `AVPlayer`). |
| **Audio Background Service** | Background audio playback service with OS notification controls / media session integration (e.g., Flutter `just_audio` + `audio_service`, React Native `react-native-track-player`). |
| **Screen Orientation & Wake Lock** | Platform APIs to programmatically switch device orientation and prevent screen sleep during video playback. |
