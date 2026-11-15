# Audible APK Comparison Checklist

This document tracks the comparison between our Audiobook Clone app and the official Audible APK.

## UI & Layout

### Header/Navigation
- [ ] App bar logo and title positioning
- [ ] Back button behavior
- [ ] Refresh icon placement and behavior
- [ ] Search icon in library
- [ ] Sort/filter icon in library

### Library Screen
- [ ] Book cover grid/list layout
- [ ] Cover image aspect ratio (1:1.414 - 3:4)
- [ ] Book title font size and weight
- [ ] Author/narrator text styling
- [ ] Duration display format (HH:MM or MM:SS)
- [ ] Progress bar styling and animation
- [ ] Progress percentage display
- [ ] Completed badge/styling
- [ ] Play button overlay position
- [ ] Empty state illustration
- [ ] "Your library is empty" message styling

### Player Screen
- [ ] Full-screen cover art with gradient overlay
- [ ] Play/pause button size and position
- [ ] Skip forward/backward (15s) button styling
- [ ] Skip forward/backward (30s) button styling
- [ ] Progress bar with thumb draggable
- [ ] Current position / duration display format
- [ ] Playback speed menu
- [ ] Sleep timer button
- [ ] Chapters button/expansion
- [ ] Speed control button
- [ ] Share button
- [ ] Queue/add to library button

### Chapter List
- [ ] Expandable/collapsible chapters section
- [ ] Current playing chapter indicator
- [ ] Chapter title font size
- [ ] Chapter duration display
- [ ] Tap to play chapter feedback
- [ ] Animation for expanding chapters

### Discover Screen
- [ ] Tab layout for LibriVox/AudioBookBay/TTS
- [ ] Search bar styling
- [ ] Search icon placement
- [ ] Clear button when text entered
- [ ] Book card layout
- [ ] Download/get button styling
- [ ] Voice preview cards for TTS
- [ ] Upload area for TTS

### Settings Screen
- [ ] Section headers (Server, Sync, Playback, Appearance, TTS, Storage, Updates, About, Danger Zone)
- [ ] List item styling (icon, title, subtitle, trailing)
- [ ] Switch styling and position
- [ ] Button height and width
- [ ] Toggle behavior feedback
- [ ] "About" section layout
- [ ] Version display format
- [ ] "Danger Zone" section styling

## Colors & Styling

### Color Palette
- [ ] Primary color (Audible green: #007B33 or similar)
- [ ] Secondary color
- [ ] Background color (dark/light mode)
- [ ] Surface colors
- [ ] Text colors (onSurface, onSurfaceVariant)
- [ ] Error colors
- [ ] Progress bar colors
- [ ] Button colors (primary, secondary, etc.)

### Typography
- [ ] Title font sizes (H1, H2, H3 hierarchy)
- [ ] Author/narrator typography
- [ ] Chapter title styling
- [ ] Settings row typography
- [ ] Search bar text styling

## Animations & Interactions

### Transitions
- [ ] Screen transition animations
- [ ] Button ripple/touch feedback
- [ ] Progress bar fill animation
- [ ] Chapter expansion animation
- [ ] Fade in/out for loading states
- [ ] Swipe to dismiss gestures

### Feedback
- [ ] Haptic feedback on playback controls
- [ ] Visual feedback on button presses
- [ ] Loading indicators

## Playback Controls

### Core Controls
- [ ] Play/Pause toggle immediate feedback
- [ ] Seek bar thumbDragging state
- [ ] Double tap skip (15s forward/backward)
- [ ] Long press on progress bar for precise seek
- [ ] Playback speed selection menu (0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x)

### Sleep Timer
- [ ] Sleep timer button opens timer dialog
- [ ] Timer options (15 min, 30 min, 45 min, 60 min, 90 min, Custom)
- [ ] Timer display in notification
- [ ] Timer auto-cancels on completion

### Chapter Navigation
- [ ] Table of contents access from player
- [ ] Jump to chapter by tap
- [ ] Current chapter indicator
- [ ] Chapter duration calculation

## Notifications

### Media Session
- [ ] Lock screen media controls
- [ ] Notification with album art
- [ ] Play/Pause button in notification
- [ ] Skip next/previous buttons
- [ ] Notification tap opens app
- [ ] Notification swipe to dismiss

## Sync & Offline

### Offline Mode
- [ ] Download button for books
- [ ] Download progress indicator
- [ ] Offline books filter/sort
- [ ] Resume playback offline
- [ ] Download location setting
- [ ] Storage space indicator

### Sync
- [ ] Automatic sync setting
- [ ] Manual sync option
- [ ] Sync frequency options
- [ ] Sync status indicator
- [ ] Highlight sync across devices
- [ ] Last sync time display

## Settings Persistence

### User Preferences
- [ ] Playback speed saved
- [ ] Sleep timer default value
- [ ] Skip interval setting
- [ ] Remember position setting
- [ ] Theme preference
- [ ] TTS voice preference
- [ ] Auto sync preference
- [ ] Sync highlight preference

## Search Functionality

### Library Search
- [ ] Search by title
- [ ] Search by author
- [ ] Search by narrator
- [ ] Recent search history
- [ ] Clear search option

### Discover Search
- [ ] LibriVox search
- [ ] AudioBookBay search
- [ ] Search results display
- [ ] No results placeholder

## Performance Metrics

### Startup Time
- [ ] Cold start < 2 seconds
- [ ] Warm start < 1 second
- [ ] Animation delay < 100ms

### Memory Usage
- [ ] Baseline memory < 50MB
- [ ] Peak memory < 150MB
- [ ] No memory leaks on navigation

### Frame Rate
- [ ] 60 FPS scrolling
- [ ] No dropped frames during transitions
- [ ] Smooth progress bar updates

## Accessibility

### Screen Reader
- [ ] Content descriptions for all images
- [ ] Semantic structure for lists
- [ ] Focus order for keyboard navigation
- [ ] Announcement of playback state changes

### Visual
- [ ] Text scaling support (0.85x to 1.5x)
- [ ] High contrast mode
- [ ] Color blind friendly colors
- [ ] Tap target sizes >= 48dp

## Data Store Migration

### Migration Strategy
- [ ] Version 1 to Version 2: Add sync frequency, TTS settings
- [ ] Default values for missing keys
- [ ] Data preservation during update
- [ ] Clear migration path documentation

### Settings Schema
- [ ] server_url (String)
- [ ] is_connected (Boolean)
- [ ] auto_sync (Boolean)
- [ ] sync_frequency (String)
- [ ] sync_highlights (Boolean)
- [ ] default_playback_speed (Float)
- [ ] skip_interval_seconds (Int)
- [ ] sleep_timer_default_minutes (Int)
- [ ] remember_position (Boolean)
- [ ] theme (Int: 0=System, 1=Light, 2=Dark)
- [ ] cover_quality (String)
- [ ] tts_engine (String)
- [ ] tts_default_voice (String)
- [ ] auto_assign_voices (Boolean)
- [ ] cache_size_bytes (Long)
- [ ] download_path (String)
- [ ] current_version (String)
- [ ] build_number (Int)
- [ ] is_checking_updates (Boolean)
- [ ] update_status (String)
- [ ] update_available (Boolean)
- [ ] latest_version (String)
- [ ] auto_update (Boolean)

## Implementation Notes

### Known Differences
1. Our app uses LibriVox as primary free source (Audible is paid)
2. TTS upload feature is unique (not in Audible)
3. AudioBookBay integration for community audiobooks
4. Offline playback requires download (same as Audible)

### Test Status
- [ ] UI Tests: All 4 screens covered
- [ ] Navigation Tests: All routes tested
- [ ] Edge Cases: Empty states, errors, loading
- [ ] Performance: Benchmarks created
- [ ] DataStore: Migration tested
- [ ] Room: DAO tests complete

### Device Testing Required
- [ ] Pixel 4a (Phone portrait)
- [ ] Pixel 4a (Phone landscape)
- [ ] Pixel Tablet (Tablet mode)
- [ ] Galaxy Z Fold 4 (Foldable)
- [ ] Emulated devices with different densities

---

**Last Updated:** 2024
**Next Review:** After UI implementation complete