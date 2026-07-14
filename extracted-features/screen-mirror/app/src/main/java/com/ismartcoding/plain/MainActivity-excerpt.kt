// Relevant excerpts from MainActivity.kt for screen mirror integration

import com.ismartcoding.plain.services.ScreenMirrorService
import com.ismartcoding.plain.mediaProjectionManager

class MainActivity : AppCompatActivity() {

    // Launches MediaProjection consent dialog → starts ScreenMirrorService on success
    internal val screenCapture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null
            && ScreenMirrorService.instance == null
        ) {
            ContextCompat.startForegroundService(
                this, Intent(this, ScreenMirrorService::class.java)
                    .putExtra("code", result.resultCode)
                    .putExtra("data", result.data)
            )
        }
    }

    // Requests RECORD_AUDIO permission, then launches screenCapture
    internal val recordAudioForMirror = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        screenCapture.launch(
            mediaProjectionManager.createScreenCaptureIntent()
        )
    }

    // Late audio permission request (for toggling audio on/off mid-stream)
    internal val recordAudioForMirrorLate = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            sendScreenMirrorAudioStatus(true)
        } else if (!shouldShowRequestPermissionRationale(
                android.Manifest.permission.RECORD_AUDIO
            ) && !Permission.RECORD_AUDIO.can(this)
        ) {
            showRecordAudioPermissionSettingsGuide()
        } else {
            sendScreenMirrorAudioStatus(false)
        }
    }

    // After user toggles audio permission in system settings
    internal val appDetailsSettingsForAudioLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        sendScreenMirrorAudioStatus(Permission.RECORD_AUDIO.can(this))
    }
}
