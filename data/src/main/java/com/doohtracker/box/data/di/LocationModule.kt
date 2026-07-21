package com.doohtracker.box.data.di

import com.doohtracker.box.data.location.LocationMonitor
import com.doohtracker.box.data.location.LocationMonitorImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {

    @Binds
    @Singleton
    abstract fun bindLocationMonitor(
        impl: LocationMonitorImpl
    ): LocationMonitor
}
