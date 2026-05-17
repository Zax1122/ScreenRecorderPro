# Screen Recorder Pro — AIDE Project

## What's included
- Full screen recording (MediaProjection, HD 1080p/720p/480p, up to 60fps)
- Microphone audio recording
- Floating control bubble (stop / pause / screenshot)
- Shake-to-stop recording
- FaceCam toggle (camera overlay)
- Screenshot tool
- Video Editor: trim, merge, rotate, compress, GIF export
- Settings: resolution, FPS, bitrate, save path
- No watermark, no time limit

---

## How to build in AIDE

### Step 1 — Install AIDE
- Download **AIDE** from the Play Store (free version works)
- OR use **AIDE Pro** (paid) for faster builds and no ads

### Step 2 — Copy this project to your phone
- Extract this ZIP to your phone storage, e.g.:
  `/sdcard/ScreenRecorderPro/`

### Step 3 — Open in AIDE
1. Open AIDE
2. Tap the folder icon → navigate to `/sdcard/ScreenRecorderPro/`
3. Tap `app.aide` OR tap `build.gradle`
4. AIDE will recognize it as an Android project

### Step 4 — Build
1. Tap the **Run** (▶) button
2. AIDE will download dependencies the first time (~5 min, needs internet)
3. After build succeeds, it auto-installs on your device

### Step 5 — Grant permissions on first launch
The app will ask for:
- **Screen capture** (MediaProjection dialog — tap "Start now")
- **Microphone** 
- **Camera** (for FaceCam)
- **Storage** (to save recordings)
- **Display over other apps** (for floating bubble — grant in Settings)

---

## Project structure

```
app/
  src/main/
    AndroidManifest.xml          ← permissions + service declarations
    java/com/recorder/pro/
      MainActivity.java          ← main hub UI
      ScreenRecordService.java   ← MediaProjection recorder + shake detection
      FloatingBubbleService.java ← overlay stop/pause/screenshot bubble
      VideoEditorActivity.java   ← trim, merge, GIF, compress, rotate
      AnimatedGifEncoder.java    ← pure-Java GIF encoder (no external libs)
      SettingsActivity.java      ← resolution, FPS, bitrate, save path
    res/
      layout/
        activity_main.xml
        activity_editor.xml
        activity_settings.xml
      values/
        strings.xml
        styles.xml
```

---

## Known limitations

| Feature | Status |
|---|---|
| Screen recording | ✅ Full HD via MediaProjection |
| Microphone audio | ✅ Works |
| **Internal audio** | ⚠️ Requires Android 10+ AND the recording app must have privileged access. Mic recording is used instead. |
| Floating bubble | ✅ Works (needs "Draw over apps" permission) |
| Shake to stop | ✅ Works |
| FaceCam overlay | ⚠️ Toggle available; full camera-in-camera overlay requires additional SurfaceView work in v2 |
| Selected region recording | ⚠️ Not supported by MediaProjection API directly |
| Draw/annotate | ⚠️ Planned for v2 |
| Video trim | ✅ MediaExtractor/Muxer based |
| GIF export | ✅ Pure Java encoder |
| Compress | ✅ Re-mux |
| Merge | ✅ Concatenation |
| Rotate | ✅ Flag-based |

---

## Troubleshooting

**"Class not found" error in AIDE**
→ Make sure all .java files are in `app/src/main/java/com/recorder/pro/`

**Build fails on first run**
→ AIDE needs internet to download `androidx.appcompat`. Connect to WiFi first.

**Floating bubble doesn't appear**
→ Go to Settings → Apps → Screen Recorder Pro → Display over other apps → Enable

**Recording stops immediately**
→ Check that "Foreground service" permission is granted in App Settings

**No sound in recording**
→ Enable Mic toggle on main screen before starting recording
