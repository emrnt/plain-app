package com.ismartcoding.plain.tunnel

import com.ismartcoding.plain.preferences.BasePreference
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object TunnelEnabledPreference : BasePreference<Boolean>() {
    override val default = true
    override val key = booleanPreferencesKey("tunnel_enabled")
}

object FrpServerPreference : BasePreference<String>() {
    override val default = ""
    override val key = stringPreferencesKey("frp_server")
}

object FrpPortPreference : BasePreference<String>() {
    override val default = "7000"
    override val key = stringPreferencesKey("frp_port")
}

object FrpDomainPreference : BasePreference<String>() {
    override val default = ""
    override val key = stringPreferencesKey("frp_domain")
}