/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.utils

import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString
import java.net.InetAddress
import java.util.Locale

object IranBypassUtils {

    val IRAN_IPV4_CIDR_STRINGS = listOf(
        "2.144.0.0/14",
        "2.176.0.0/12",
        "5.56.0.0/14",
        "5.160.0.0/12",
        "5.200.0.0/13",
        "31.2.128.0/17",
        "31.7.0.0/16",
        "37.98.0.0/15",
        "37.114.0.0/15",
        "37.152.0.0/14",
        "37.254.0.0/15",
        "46.100.0.0/15",
        "46.143.0.0/16",
        "46.209.0.0/16",
        "46.224.0.0/14",
        "77.36.128.0/17",
        "78.38.0.0/15",
        "79.127.0.0/17",
        "79.175.128.0/17",
        "80.191.0.0/16",
        "80.253.144.0/20",
        "81.12.0.0/16",
        "81.16.96.0/20",
        "81.90.144.0/20",
        "82.99.192.0/18",
        "83.120.0.0/14",
        "84.47.192.0/18",
        "84.241.0.0/17",
        "85.9.64.0/18",
        "85.133.128.0/17",
        "85.185.0.0/16",
        "86.55.0.0/16",
        "86.57.0.0/16",
        "87.107.0.0/16",
        "87.247.160.0/19",
        "87.248.128.0/19",
        "88.135.32.0/19",
        "89.165.0.0/16",
        "89.196.0.0/16",
        "89.198.0.0/15",
        "91.98.0.0/16",
        "91.99.0.0/16",
        "91.108.0.0/16",
        "91.184.0.0/15",
        "91.240.60.0/22",
        "92.42.48.0/20",
        "92.50.0.0/16",
        "93.110.0.0/15",
        "94.74.128.0/17",
        "94.101.128.0/18",
        "94.182.0.0/15",
        "94.232.168.0/21",
        "95.38.0.0/16",
        "95.80.0.0/16",
        "95.81.0.0/16",
        "95.82.0.0/17",
        "95.130.64.0/19",
        "109.125.128.0/17",
        "109.162.128.0/17",
        "109.201.0.0/17",
        "151.232.0.0/14",
        "151.242.0.0/15",
        "158.58.0.0/16",
        "164.138.128.0/17",
        "176.101.32.0/19",
        "176.102.192.0/18",
        "176.216.0.0/13",
        "178.131.0.0/16",
        "178.236.144.0/20",
        "178.239.144.0/20",
        "178.248.176.0/20",
        "178.252.128.0/17",
        "185.8.172.0/22",
        "185.12.240.0/22",
        "185.24.4.0/22",
        "185.55.224.0/22",
        "185.73.112.0/22",
        "185.88.152.0/22",
        "185.105.236.0/22",
        "185.112.112.0/22",
        "185.120.240.0/22",
        "185.128.80.0/22",
        "185.135.24.0/22",
        "185.141.104.0/22",
        "185.143.232.0/22",
        "185.161.112.0/22",
        "185.165.116.0/22",
        "185.186.240.0/22",
        "185.190.152.0/22",
        "185.204.196.0/22",
        "185.208.180.0/22",
        "185.211.56.0/22",
        "185.213.192.0/22",
        "185.226.164.0/22",
        "185.231.112.0/22",
        "185.239.104.0/22",
        "185.255.88.0/22",
        "188.121.96.0/19",
        "188.136.128.0/17",
        "188.158.0.0/15",
        "188.208.192.0/18",
        "188.210.0.0/15",
        "188.214.160.0/19",
        "188.229.0.0/16",
        "188.253.0.0/16",
        "193.18.22.0/23",
        "194.147.216.0/22",
        "194.225.0.0/16",
        "195.146.32.0/19"
    )

    val IRAN_IPV4_RANGES: List<IPAddress> by lazy {
        IRAN_IPV4_CIDR_STRINGS.mapNotNull {
            try {
                IPAddressString(it).address
            } catch (_: Exception) {
                null
            }
        }
    }

    fun cleanHost(rawUrlOrHost: String?): String {
        if (rawUrlOrHost.isNullOrBlank()) return ""
        var cleaned = rawUrlOrHost.trim().lowercase(Locale.ROOT)
        if (cleaned.startsWith("http://")) cleaned = cleaned.removePrefix("http://")
        if (cleaned.startsWith("https://")) cleaned = cleaned.removePrefix("https://")
        if (cleaned.contains("/")) cleaned = cleaned.substringBefore("/")
        if (cleaned.contains(":")) cleaned = cleaned.substringBefore(":")
        return cleaned.trim()
    }

    fun isIrDomain(host: String): Boolean {
        val clean = cleanHost(host)
        return clean.endsWith(".ir") || clean == "ir"
    }

    fun matchesCustomUrl(host: String, customUrl: String?): Boolean {
        val targetHost = cleanHost(host)
        val targetCustom = cleanHost(customUrl)
        if (targetHost.isBlank() || targetCustom.isBlank()) return false
        return targetHost == targetCustom || targetHost.endsWith(".$targetCustom")
    }

    fun shouldBypass(host: String, bypassIr: Boolean, customUrl: String?): Boolean {
        if (bypassIr && isIrDomain(host)) return true
        if (!customUrl.isNullOrBlank() && matchesCustomUrl(host, customUrl)) return true
        return false
    }

    fun resolveHostToIps(hostOrUrl: String): List<IPAddress> {
        val host = cleanHost(hostOrUrl)
        if (host.isBlank()) return emptyList()
        return try {
            InetAddress.getAllByName(host).mapNotNull {
                try {
                    IPAddressString(it.hostAddress).address
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
