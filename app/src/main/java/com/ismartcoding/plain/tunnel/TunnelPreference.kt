package com.ismartcoding.plain.tunnel

import com.ismartcoding.plain.preferences.BasePreference
import androidx.datastore.preferences.core.booleanPreferencesKey

object TunnelEnabledPreference : BasePreference<Boolean>() {
    override val default = false
    override val key = booleanPreferencesKey("tunnel_enabled")
}