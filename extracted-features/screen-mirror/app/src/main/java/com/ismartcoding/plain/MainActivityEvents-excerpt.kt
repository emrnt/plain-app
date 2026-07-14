// Relevant excerpts from MainActivityEvents.kt for screen mirror integration

import com.ismartcoding.plain.events.HRequestScreenMirrorAudioEvent
import com.ismartcoding.plain.events.HStartScreenMirrorEvent
import com.ismartcoding.plain.mediaProjectionManager

internal fun MainActivity.initEvents() {
    lifecycleScope.launch {
        Channel.sharedFlow.collect { event ->
            when (event) {
                is HStartScreenMirrorEvent -> {
                    try {
                        if (event.audio && !Permission.RECORD_AUDIO.can(this@initEvents))
                            recordAudioForMirror.launch(android.Manifest.permission.RECORD_AUDIO)
                        else
                            screenCapture.launch(mediaProjectionManager.createScreenCaptureIntent())
                    } catch (e: IllegalStateException) {
                        LogCat.e("Error launching screen capture: ${e.message}")
                    }
                }

                is HRequestScreenMirrorAudioEvent -> {
                    try {
                        if (Permission.RECORD_AUDIO.can(this@initEvents))
                            sendScreenMirrorAudioStatus(true)
                        else
                            recordAudioForMirrorLate.launch(android.Manifest.permission.RECORD_AUDIO)
                    } catch (e: IllegalStateException) {
                        LogCat.e("Error requesting RECORD_AUDIO: ${e.message}")
                    }
                }
            }
        }
    }
}
