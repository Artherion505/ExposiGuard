package com.exposiguard.app.di

import android.content.Context
import com.exposiguard.app.managers.*
import com.exposiguard.app.repository.ExposureRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkerModule {

    @Provides @Singleton
    fun provideUserProfileManager(@ApplicationContext context: Context) = UserProfileManager(context)

    @Provides @Singleton
    fun provideAppUsageManager(@ApplicationContext context: Context) = AppUsageManager(context)

    @Provides @Singleton
    fun provideBluetoothManager(
        @ApplicationContext context: Context,
        appUsageManager: AppUsageManager,
        userProfileManager: UserProfileManager
    ) = BluetoothManager(context, appUsageManager, userProfileManager)

    @Provides @Singleton
    fun provideAmbientExposureManager(@ApplicationContext context: Context) = AmbientExposureManager(context)

    @Provides @Singleton
    fun providePhysicalSensorManager(@ApplicationContext context: Context) = PhysicalSensorManager(context)

    @Provides @Singleton
    fun provideHealthManager(@ApplicationContext context: Context) = HealthManager(context)

    @Provides @Singleton
    fun provideEmfManager() = EMFManager()

    @Provides @Singleton
    fun providePhoneUseManager(@ApplicationContext context: Context) = PhoneUseManager(context)

    @Provides @Singleton
    fun provideWiFiManager(
        @ApplicationContext context: Context,
        userProfileManager: UserProfileManager
    ) = WiFiManager(context, userProfileManager)

    @Provides @Singleton
    fun provideSarManager(
        @ApplicationContext context: Context,
        wifiManager: WiFiManager,
        bluetoothManager: BluetoothManager,
        ambientExposureManager: AmbientExposureManager,
        physicalSensorManager: PhysicalSensorManager,
        healthManager: HealthManager,
        emfManager: EMFManager,
        userProfileManager: UserProfileManager,
        phoneUseManager: PhoneUseManager
    ) = SARManager(
        context,
        wifiManager,
        bluetoothManager,
        ambientExposureManager,
        physicalSensorManager,
        healthManager,
        emfManager,
        userProfileManager,
        phoneUseManager
    )
}
