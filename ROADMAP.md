# Ableton Remote — Roadmap

## Known Bugs
- [ ] **Tracks don't refresh when a new track is added** — "Add Audio Track" sends the OSC command and Ableton creates the track, but the app's mixer doesn't re-fetch `num_tracks` or subscribe to the new track. Need to re-query `/live/song/get/num_tracks` after creating a track and re-initialize track state.

## Phase 2 — Session View + Device Control

### Session View Screen
- [ ] 2D clip grid (tracks × scenes), scrollable both axes
- [ ] Each cell shows clip color, name, and state (empty, stopped, playing, triggered)
- [ ] Tap clip slot to fire (`/live/clip_slot/fire`)
- [ ] Scene launch buttons on right edge (`/live/scene/fire`)
- [ ] Stop track buttons at bottom (`/live/track/stop_all_clips`)
- [ ] Playing position indicator on active clips (`start_listen/playing_position`)
- [ ] Selection tracking via `/live/view/get/selected_clip`
- [ ] Initial state via bulk `/live/song/get/track_data` with `clip.name`, `clip.color`, `clip_slot.has_clip`

### Device Control Screen
- [ ] Show device chain for selected track (`/live/track/get/devices/name`, `/live/track/get/devices/type`)
- [ ] Tap device to see parameters as sliders/knobs
- [ ] Parameter names, values, min/max, value strings (`/live/device/get/parameter/value_string`)
- [ ] Real-time parameter adjustment (`/live/device/set/parameter/value`)
- [ ] Track device selection via `/live/view/set/selected_device`

### Push 2 Emulation Mode
- [ ] Full-screen 8×8 pad grid mapped to session clip grid
- [ ] Color-coded pads matching clip colors (dim=has clip, bright=playing, blink=triggered)
- [ ] 8 track encoders (top row) for volume/pan/send — mode buttons to switch assignment
- [ ] Transport row: Play, Stop, Record, Metronome, Loop
- [ ] Scene launch column (right side, 8 buttons)
- [ ] Navigation: up/down/left/right to scroll the visible 8×8 window
- [ ] Track select row (8 buttons)
- [ ] Display strip showing track names and parameter values
- [ ] MIDI CC mapping via `/live/midimap/map_cc`

### Navigation Update
- [ ] Bottom nav expands: Servers | Transport | Mixer | Session | Push
- [ ] Device control accessible from Mixer or Session (slide-up panel or tab)

## Phase 3 — Docker / Web

### Docker Containerization
- [ ] Extract repository + OSC networking into shared Kotlin Multiplatform module
- [ ] Ktor backend managing OSC connections, exposing WebSocket API (WebSocket ↔ OSC bridge)
- [ ] Web frontend (Compose for Web or React) replicating the Android UI
- [ ] Dockerfile: multi-stage build (Gradle → slim JRE runtime)
- [ ] docker-compose.yml with config volume for saved servers
- [ ] Network mode: `host` or explicit UDP port mapping for LAN access

## UI Polish (Ongoing)
- [ ] Remove debug log panel for production builds (keep for debug builds only)
- [ ] Auto-reconnect with exponential backoff on connection loss
- [ ] Haptic feedback on button presses
- [ ] App icon refresh (custom Ableton-themed icon)
- [ ] Signed release APK for Google Play or direct distribution
