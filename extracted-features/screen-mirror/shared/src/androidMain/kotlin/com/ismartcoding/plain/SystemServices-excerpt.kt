// Relevant excerpt from SystemServices.kt
// MediaProjectionManager is the key dependency for screen mirror

import android.media.projection.MediaProjectionManager
import com.ismartcoding.plain.appContext
import com.ismartcoding.plain.lib.extensions.getSystemServiceCompat

val mediaProjectionManager: MediaProjectionManager by lazy {
    appContext.getSystemServiceCompat(MediaProjectionManager::class.java)
}
