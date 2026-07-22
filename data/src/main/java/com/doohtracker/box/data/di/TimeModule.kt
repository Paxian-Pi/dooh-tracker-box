package com.doohtracker.box.data.di

import com.doohtracker.box.data.time.NetworkTimeProvider
import com.doohtracker.box.data.time.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {

    @Binds
    @Singleton
    abstract fun bindTimeProvider(
        impl: NetworkTimeProvider
    ): TimeProvider
}
