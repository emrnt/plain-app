// Relevant excerpt from Preferences.kt (shared/.../preferences/)
// Persists screen mirror quality setting in DataStore

object ScreenMirrorQualityPreference : BasePreference<String>() {
    override val default = ""
    override val key = stringPreferencesKey("screen_mirror_quality")

    suspend fun getValueAsync(): DScreenMirrorQuality {
        val str = getAsync()
        if (str.isEmpty()) return DScreenMirrorQuality()
        return try {
            preferencesJson.decodeFromString(str)
        } catch (_: Exception) {
            DScreenMirrorQuality()
        }
    }

    suspend fun putAsync(value: DScreenMirrorQuality) {
        putAsync(preferencesJson.encodeToString(value))
    }
}
