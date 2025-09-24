package com.exposiguard.app.di

import android.content.Context
import com.exposiguard.app.repository.ExposureRepository
import com.exposiguard.app.repository.MonthlyStatsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideExposureRepository(@ApplicationContext context: Context): ExposureRepository {
        return ExposureRepository(context)
    }

    @Provides
    @Singleton
    fun provideMonthlyStatsRepository(@ApplicationContext context: Context): MonthlyStatsRepository {
        return MonthlyStatsRepository(context)
    }
}