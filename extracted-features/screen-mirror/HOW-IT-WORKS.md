# Screen Mirror Feature — Architecture & Implementation Guide

## Overview

This feature streams the Android device screen in **real-time** over WebSocket to web clients. It captures screen content using Android's `MediaProjection` API, encodes it as **H.264 video + Opus audio** via hardware `MediaCodec`, and pushes encoded packets to connected web clients through the embedded Ktor server's WebSocket.

There is **no screenshot capture** (single-frame Bitmap capture). The only "screenshot" related code is a metadata detection flag (`DImageMeta.isScreenshot`) that identifies whether an existing image file is a screenshot based on missing EXIF exposure time.

---

## High-Level Data Flow

```
Web Browser                    Android Device
─────────────                  ─────────────────────────────────
                                     │
  GraphQL ──startScreenMirror──► ScreenMirrorGraphQL.kt
                                     │
                              HStartScreenMirrorEvent
                                     │
                              MainActivity.screenCapture
                              (MediaProjection consent dialog)
                                     │
                              ScreenMirrorService
                              (Foreground Service)
                                     │
   ◄── WebSocket Event ────    ScreenMirrorPipeline.kt
   "SCREEN_MIRRORING"                │
                                     ├── VirtualDisplay
                                     │   (captures screen → Surface)
                                     │
                                     ├── MediaCodecVideoEncoder
                                     │   (H.264 hardware encode)
                                     │   → SCREEN_MIRROR_VIDEO (31)
                                     │   → SCREEN_MIRROR_VIDEO_CODEC (32)
                                     │
                                     └── MediaCodecAudioEncoder
                                         (Opus hardware encode)
                                         → SCREEN_MIRROR_AUDIO (33)
```

---

## File Map

### Service Layer

| File | Lines | Purpose |
|------|-------|---------|
| `services/ScreenMirrorService.kt` | 178 | Foreground service, manages lifecycle & MediaProjection token |
| `services/screenmirror/ScreenMirrorPipeline.kt` | 231 | Owns VirtualDisplay + encoders, coordinates capture pipeline |
| `services/screenmirror/MediaCodecVideoEncoder.kt` | 158 | H.264 hardware encoder via MediaCodec |
| `services/screenmirror/MediaCodecAudioEncoder.kt` | 193 | Opus audio encoder via MediaCodec + AudioRecord |
| `services/screenmirror/H264AnnexB.kt` | 99 | H.264 bitstream conversion (AVCC → Annex-B) |
| `services/screenmirror/ScreenMirrorScreenSize.kt` | 55 | Physical display size detection |

### GraphQL / Web API

| File | Lines | Purpose |
|------|-------|---------|
| `web/schemas/ScreenMirrorGraphQL.kt` | 89 | 8 GraphQL endpoints for control |
| `web/models/ScreenMirrorVideoCodec.kt` | 10 | Codec config sent to web client |

### UI / Launcher

| File | Lines | Purpose |
|------|-------|---------|
| `ui/MainActivityScreenMirror.kt` | 42 | Audio permission dialogs, settings redirect |
| `ui/MainActivity.kt` (relevant lines 84-112) | 29 | ActivityResult launchers for MediaProjection + permissions |
| `ui/MainActivityEvents.kt` (relevant lines 70-86) | 17 | Event handling for HStartScreenMirrorEvent |

### Broadcast Receiver

| File | Lines | Purpose |
|------|-------|---------|
| `receivers/ServiceStopBroadcastReceiver.kt` | 57 | Handles STOP_SCREEN_MIRROR intent |

### Shared Data Models

| File | Lines | Purpose |
|------|-------|---------|
| `shared/.../data/DScreenMirrorQuality.kt` | 12 | Quality settings data class |
| `shared/.../data/ScreenMirrorControlInput.kt` | 21 | Remote touch/gesture input model |
| `shared/.../enums/ScreenMirrorMode.kt` | 5 | HD or SMOOTH mode |
| `shared/.../enums/ScreenMirrorControlAction.kt` | 13 | Tap, swipe, scroll, back, home, etc. |
| `shared/.../web/models/ScreenMirrorQuality.kt` | 15 | GraphQL quality model |

### Shared Events

| File | Lines | Purpose |
|------|-------|---------|
| `shared/.../events/WebSocketEvents.kt` | 79 | EventType enum: SCREEN_MIRRORING(5), SCREEN_MIRROR_VIDEO(31), VIDEO_CODEC(32), AUDIO(33), AUDIO_GRANTED(14) |
| `shared/.../events/HttpApiEvents.kt` | 43 | HStartScreenMirrorEvent, HRequestScreenMirrorAudioEvent |

### Integration Points

| File | Lines | Purpose |
|------|-------|---------|
| `shared/.../SystemServices.kt` | 3 (53-55) | `mediaProjectionManager` global lazy singleton |
| `shared/.../preferences/Preferences.kt` | 18 (179-196) | `ScreenMirrorQualityPreference` DataStore persistence |
| `app/.../AndroidManifest.xml` | 4 lines | RECORD_AUDIO, SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE_MEDIA_PROJECTION permissions + service declaration |

---

## Component Details

### 1. ScreenMirrorService.kt

A `LifecycleService` that:
- Registers an `OrientationEventListener` to detect rotation changes
- Obtains a `MediaProjection` token from the consent dialog result
- Handles OEM-specific workarounds (Honor/Oppo/Samsung/Xiaomi on Android 16)
- Creates a `ScreenMirrorPipeline` and calls `start()`
- Sends `SCREEN_MIRRORING` WebSocket event on start
- Exposes `stop()`, `getPipeline()`, `onQualityChanged()`

**Key fields:**
- `companion.object.instance` — singleton reference
- `companion.object.qualityData` — current quality settings

### 2. ScreenMirrorPipeline.kt

The core orchestration class that:
- Creates a `VirtualDisplay` via `projection.createVirtualDisplay()` with the video encoder's input `Surface`
- Starts `MediaCodecVideoEncoder` (H.264) and `MediaCodecAudioEncoder` (Opus)
- Handles orientation changes by rebuilding encoders at new resolution
- Handles quality changes (HD 1080p ↔ SMOOTH 720p)
- Delivers encoded NAL units and Opus packets via `sendEvent()` → WebSocket

**Key methods:**
- `start()` — computes capture size, starts encoders
- `rebuildEncoderAndResize(reason)` — swaps encoder when orientation/quality changes
- `stop()` — releases VirtualDisplay, encoders, and projection
- `getScreenMirrorVideoCodec()` — returns SPS+PPS for web client decoder init

### 3. MediaCodecVideoEncoder.kt

Hardware H.264 encoder wrapping `MediaCodec`:
- **Input:** A `Surface` (fed by VirtualDisplay — GPU-direct, no readback)
- **Output:** Annex-B NAL units via `onEncoded` callback
- Handles `INFO_OUTPUT_FORMAT_CHANGED` to capture SPS/PPS codec config
- Supports dynamic bitrate/fps changes via `setParameters`
- Supports key frame requests
- Format: `video/avc`, Baseline profile, 30fps

### 4. MediaCodecAudioEncoder.kt

System audio capture + Opus encoding:
- Uses `AudioPlaybackCaptureConfiguration` with MediaProjection to capture system audio
- Captures `USAGE_MEDIA`, `USAGE_GAME`, `USAGE_UNKNOWN` audio
- Encodes PCM frames to Opus via `MediaCodec`
- Requires `RECORD_AUDIO` permission
- Min SDK: Android 10 (API 29)

### 5. H264AnnexB.kt

Pure bitstream utilities:
- `avccToAnnexB()` — converts length-prefixed H.264 to start-code-delimited format
- `ensureStartCode()` — normalizes SPS/PPS buffers to start with `00 00 00 01`
- `joinSpsPps()` — concatenates SPS + PPS with start codes for decoder init

---

## WebSocket Events

| Event | Code | Payload | Direction |
|-------|------|---------|-----------|
| `SCREEN_MIRRORING` | 5 | String status | Server → Client |
| `SCREEN_MIRROR_VIDEO` | 31 | Binary (H.264 NAL unit) | Server → Client |
| `SCREEN_MIRROR_VIDEO_CODEC` | 32 | JSON (annex-B base64, keyFrame base64) | Server → Client |
| `SCREEN_MIRROR_AUDIO` | 33 | Binary (Opus packet) | Server → Client |
| `SCREEN_MIRROR_AUDIO_GRANTED` | 14 | Boolean JSON | Server → Client |

---

## GraphQL API

| Operation | Params | Returns | Description |
|-----------|--------|---------|-------------|
| `query screenMirrorState` | — | Boolean | Is mirror running? |
| `query screenMirrorVideoCodec` | — | ScreenMirrorVideoCodec | H.264 codec config |
| `query screenMirrorControlEnabled` | — | Boolean | Accessibility on? |
| `query screenMirrorQuality` | — | ScreenMirrorQuality | Current quality |
| `mutation startScreenMirror` | audio: Boolean | Boolean | Start mirroring |
| `mutation requestScreenMirrorAudio` | — | Boolean | Request audio permission |
| `mutation stopScreenMirror` | — | Boolean | Stop mirroring |
| `mutation updateScreenMirrorQuality` | mode: ScreenMirrorMode | Boolean | HD or SMOOTH |
| `mutation sendScreenMirrorControl` | input: ScreenMirrorControlInput | Boolean | Remote touch |

---

## Required Permissions (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />

<service
    android:name=".services.ScreenMirrorService"
    android:exported="false"
    android:foregroundServiceType="mediaProjection" />
```

---

## Remote Control

The accessibility service (`PlainAccessibilityService`) injects touch/gesture events into the screen mirror stream. Coordinates from the web client are normalized [0,1] and mapped to actual screen coordinates. Actions: TAP, LONG_PRESS, SWIPE, SCROLL, BACK, HOME, RECENTS, LOCK_SCREEN, KEY.

---

## Quality Modes

| Mode | Resolution | Bitrate |
|------|-----------|---------|
| `HD` | Up to 1080p | 4 Mbps |
| `SMOOTH` | Up to 720p | 2 Mbps |

Bitrate adjusts dynamically: 4 Mbps (1080p), 2 Mbps (720p), 1 Mbps (below 720p).

---

## Dependencies

All platform APIs — **no external libraries required**:
- `android.media.MediaCodec` — H.264 + Opus encoding
- `android.media.projection.MediaProjection` — screen capture
- `android.hardware.display.VirtualDisplay` — capture → encoder Surface
- `android.media.AudioRecord` + `AudioPlaybackCaptureConfiguration` — system audio capture
- `android.accessibilityservice.AccessibilityService` — remote touch injection
