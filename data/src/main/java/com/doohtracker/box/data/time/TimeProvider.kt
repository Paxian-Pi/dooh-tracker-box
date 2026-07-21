package com.doohtracker.box.data.time

interface TimeProvider {
    fun getCurrentTimeMillis(): Long
}
