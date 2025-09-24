package com.exposiguard.app.data

data class NetworkData(
    var carrierName: String = "",
    var isAirplaneEnabled: Boolean = false,
    var networkType: String = "",
    var signalStrength: String = "",
    var operatorCode: String = ""
)
