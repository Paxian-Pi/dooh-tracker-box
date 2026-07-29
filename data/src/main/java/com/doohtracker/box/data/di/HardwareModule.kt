package com.doohtracker.box.data.di

import com.doohtracker.box.data.hardware.SignalMonitor
import com.doohtracker.box.data.hardware.SignalMonitorImpl
import com.doohtracker.box.data.hardware.UsbConnectionMonitor
import com.doohtracker.box.data.hardware.UsbConnectionMonitorImpl
import com.doohtracker.box.data.system.SystemHealthMonitor
import com.doohtracker.box.data.system.SystemHealthMonitorImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HardwareModule {

    @Binds
    @Singleton
    abstract fun bindHdmiSignalMonitor(
        impl: SignalMonitorImpl
    ): SignalMonitor

    @Binds
    @Singleton
    abstract fun bindUsbConnectionMonitor(
        impl: UsbConnectionMonitorImpl
    ): UsbConnectionMonitor

    @Binds
    @Singleton
    abstract fun bindSystemHealthMonitor(
        impl: SystemHealthMonitorImpl
    ): SystemHealthMonitor
}
