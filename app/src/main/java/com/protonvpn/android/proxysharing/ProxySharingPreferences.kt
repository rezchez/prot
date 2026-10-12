/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.proxysharing

import android.content.Context
import android.content.SharedPreferences

class ProxySharingPreferences(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isSharingEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHARING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SHARING_ENABLED, value).apply()

    var isSocksEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOCKS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOCKS_ENABLED, value).apply()

    var isHttpEnabled: Boolean
        get() = prefs.getBoolean(KEY_HTTP_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HTTP_ENABLED, value).apply()

    var isPacEnabled: Boolean
        get() = prefs.getBoolean(KEY_PAC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PAC_ENABLED, value).apply()

    var socksPort: Int
        get() = prefs.getInt(KEY_SOCKS_PORT, DEFAULT_SOCKS_PORT)
        set(value) = prefs.edit().putInt(KEY_SOCKS_PORT, value).apply()

    var httpPort: Int
        get() = prefs.getInt(KEY_HTTP_PORT, DEFAULT_HTTP_PORT)
        set(value) = prefs.edit().putInt(KEY_HTTP_PORT, value).apply()

    var pacPort: Int
        get() = prefs.getInt(KEY_PAC_PORT, DEFAULT_PAC_PORT)
        set(value) = prefs.edit().putInt(KEY_PAC_PORT, value).apply()

    var bypassIrEnabled: Boolean
        get() = prefs.getBoolean(KEY_BYPASS_IR, false)
        set(value) = prefs.edit().putBoolean(KEY_BYPASS_IR, value).apply()

    var customBypassUrl: String?
        get() = prefs.getString(KEY_CUSTOM_BYPASS_URL, null)
        set(value) = prefs.edit().putString(KEY_CUSTOM_BYPASS_URL, value).apply()

    companion object {
        private const val PREFS_NAME = "proton_vpn_proxy_sharing_prefs"
        private const val KEY_SHARING_ENABLED = "proxy_sharing_enabled"
        private const val KEY_SOCKS_ENABLED = "proxy_socks_enabled"
        private const val KEY_HTTP_ENABLED = "proxy_http_enabled"
        private const val KEY_PAC_ENABLED = "proxy_pac_enabled"
        private const val KEY_SOCKS_PORT = "proxy_socks_port"
        private const val KEY_HTTP_PORT = "proxy_http_port"
        private const val KEY_PAC_PORT = "proxy_pac_port"
        private const val KEY_BYPASS_IR = "proxy_bypass_ir"
        private const val KEY_CUSTOM_BYPASS_URL = "proxy_custom_bypass_url"

        const val DEFAULT_SOCKS_PORT = 10808
        const val DEFAULT_HTTP_PORT = 10809
        const val DEFAULT_PAC_PORT = 10810
    }
}
