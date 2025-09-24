package com.exposiguard.app.viewmodel

import androidx.lifecycle.ViewModel
import com.exposiguard.app.managers.HealthManager
import com.exposiguard.app.managers.NoiseManager
import com.exposiguard.app.managers.SARManager
import com.exposiguard.app.managers.TrendsAnalysisManager
import com.exposiguard.app.managers.UserProfileManager
import com.exposiguard.app.managers.WiFiManager
import com.exposiguard.app.repository.ExposureRepository
import com.exposiguard.app.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class GeneralViewModel @Inject constructor(
    private val wiFiManager: WiFiManager,
    private val sarManager: SARManager,
    private val noiseManager: NoiseManager,
    private val healthManager: HealthManager,
    private val trendsManager: TrendsAnalysisManager,
    private val exposureRepository: ExposureRepository,
    private val userProfileManager: UserProfileManager
) : ViewModel() {

    fun getWiFiNetworks() = wiFiManager.getWiFiNetworks()

    fun getCarrierNetworks() = wiFiManager.carrierNetworks

    fun getBluetoothDevices() = wiFiManager.getBluetoothDevices()

    fun getExposureReadings() = wiFiManager.getExposureReadings()

    fun getExposureStats() = wiFiManager.getExposureStats()

    fun isBluetoothEnabled() = wiFiManager.isBluetoothEnabled()

    fun hasBluetoothPermission() = wiFiManager.hasBluetoothPermission()

    fun getSARLevel() = sarManager.getCurrentSARLevel()

    fun getCombinedExposureData() = trendsManager.getCombinedExposureData(exposureRepository.getAllReadings())

    fun getExposureTrendData(timeWindowMinutes: Int = 60) = trendsManager.getExposureTrendData(exposureRepository.getAllReadings(), timeWindowMinutes)

    fun startNoiseMonitoring() = noiseManager.startNoiseMonitoring()

    fun getNoiseLevel() = noiseManager.getCurrentNoiseLevel()

    suspend fun getHealthData(context: android.content.Context): String {
        val heartRate = healthManager.getHeartRate()
        return context.getString(R.string.health_heart_rate_label, heartRate)
    }

    fun analyzeTrends() = trendsManager.analyzeTrends(exposureRepository.getAllReadings())

    // Métodos para manejar el perfil del usuario
    fun getUserWeight(): Double = userProfileManager.getWeight()

    fun getUserHeight(): Double = userProfileManager.getHeight()

    fun saveUserWeight(weight: Double) = userProfileManager.saveWeight(weight)

    fun saveUserHeight(height: Double) = userProfileManager.saveHeight(height)

    fun saveUserProfile(weight: Double, height: Double) = userProfileManager.saveUserProfile(weight, height)

    fun isUserProfileConfigured(): Boolean = userProfileManager.isProfileConfigured()

    fun getUserProfile() = userProfileManager.getUserProfile()
}
