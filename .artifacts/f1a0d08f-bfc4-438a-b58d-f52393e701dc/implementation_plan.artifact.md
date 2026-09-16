# Improve Poster/Thumbnail Visual Quality and Responsiveness

## Proposed Changes

### [app:shared] UI Refinement

#### [MODIFY] [LibraryScreen.kt](file:///C:/Projects/MediaStation/app/shared/src/commonMain/kotlin/id/storia/media/ui/library/LibraryScreen.kt)
- Use `BoxWithConstraints` in `MediaGrid` to enforce 2-4 columns based on screen width.
- Move `aspectRatio(0.7f)` from the `Card` to the `AsyncImage` in `MediaCard` to ensure consistent poster proportions regardless of text height.
- Update `AsyncImage` in `MediaCard` to use an `ImageRequest` with `crossfade(true)` for smoother loading transitions.

## Verification Plan
### Manual Verification
- Deploy to Android (Phone) -> Verify 2 columns in portrait mode.
- Deploy to Desktop/Tablet or rotate Phone to landscape -> Verify 3 or 4 columns with larger, sharp cards.
- Verify posters have consistent cropping even with long titles that wrap or subtitles.
