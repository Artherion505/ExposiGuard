package com.exposiguard.app.data

data class WiFiNetwork(
    val ssid: String,
    val bssid: String,
    val frequency: Int,
    val level: Int,
    val capabilities: String
)
