package com.doohtracker.box.data.time

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun getCurrentTimeMillis(): Long = System.currentTimeMillis()
}
