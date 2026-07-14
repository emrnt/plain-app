package com.ismartcoding.plain.tunnel

import com.ismartcoding.plain.preferences.BasePreference
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object TunnelEnabledPreference : BasePreference<Boolean>() {
    override val default = true
    override val key = booleanPreferencesKey("tunnel_enabled")
}

object NgrokAuthTokenPreference : BasePreference<String>() {
    override val default = ""
    override val key = stringPreferencesKey("ngrok_auth_token")
}