package com.exposiguard.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exposiguard.app.managers.BluetoothManager
import com.exposiguard.app.managers.SARManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val bluetoothManager: BluetoothManager,
    private val sarManager: SARManager
) : ViewModel() {

    // Bluetooth Data
    val bluetoothDevices = bluetoothManager.bluetoothDevices
    val bluetoothExposure = bluetoothManager.bluetoothExposure

    // SAR Data
    val sarValue = flow {
        while (true) {
            emit(sarManager.getCurrentSARLevel())
            kotlinx.coroutines.delay(2000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Total exposure calculation
    val totalExposure = combine(bluetoothExposure, sarValue) { bluetooth, sar ->
        bluetooth + sar
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun startScanning() {
        viewModelScope.launch {
            // Implementar lógica de escaneo
        }
    }

    fun stopScanning() {
        viewModelScope.launch {
            // Implementar lógica de parada
        }
    }
}
