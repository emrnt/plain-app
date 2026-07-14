package com.ismartcoding.plain.tunnel

import com.ismartcoding.plain.preferences.BasePreference
import kotlinx.serialization.json.Json
import androidx.datastore.preferences.core.stringPreferencesKey

private val preferencesJson = Json { ignoreUnknownKeys = true }

object TunnelEnabledPreference : BasePreference<Boolean>() {
    override val default = false
    override val key = booleanPreferencesKey("tunnel_enabled")
}

object TunnelTokenPreference : BasePreference<String>() {
    override val default = ""
    override val key = stringPreferencesKey("tunnel_token")
}

object TunnelDomainPreference : BasePreference<String>() {
    override val default = ""
    override val key = stringPreferencesKey("tunnel_domain")
}
