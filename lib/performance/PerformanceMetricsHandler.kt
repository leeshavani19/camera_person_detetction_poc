package com.odigo.v3.odigo_offline

import android.app.ActivityManager
import android.content.Context
import android.net.TrafficStats
import android.os.Debug
import android.os.Process
import android.os.SystemClock

class PerformanceMetricsHandler(
    private val context: Context,
) {
    private var previousElapsedRealtimeMs = SystemClock.elapsedRealtime()
    private var previousProcessCpuTimeMs = Process.getElapsedCpuTime()

    fun getMetrics(): Map<String, Any> {
        val runtime = Runtime.getRuntime()

        val currentElapsedRealtimeMs = SystemClock.elapsedRealtime()
        val currentProcessCpuTimeMs = Process.getElapsedCpuTime()

        val elapsedWallTimeMs =
            currentElapsedRealtimeMs - previousElapsedRealtimeMs

        val elapsedProcessCpuTimeMs =
            currentProcessCpuTimeMs - previousProcessCpuTimeMs

        val processorCount =
            runtime.availableProcessors().coerceAtLeast(1)

        /*
         * CPU usage relative to a single CPU core.
         *
         * Example:
         * 100% means approximately one complete CPU core.
         * It can exceed 100% when multiple cores are used.
         */
        val processCpuPercentPerCore =
            if (elapsedWallTimeMs > 0) {
                (elapsedProcessCpuTimeMs.toDouble() /
                        elapsedWallTimeMs.toDouble()) * 100.0
            } else {
                0.0
            }

        /*
         * CPU usage normalized against the device's total CPU capacity.
         *
         * Example:
         * On an 8-core device, one fully used core is approximately 12.5%.
         */
        val processCpuPercentNormalized =
            (processCpuPercentPerCore / processorCount)
                .coerceIn(0.0, 100.0)

        previousElapsedRealtimeMs = currentElapsedRealtimeMs
        previousProcessCpuTimeMs = currentProcessCpuTimeMs

        val processMemoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(processMemoryInfo)

        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE)
                    as ActivityManager

        val systemMemoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(systemMemoryInfo)

        val uid = Process.myUid()

        val receivedBytes =
            TrafficStats.getUidRxBytes(uid).takeIf {
                it != TrafficStats.UNSUPPORTED.toLong()
            } ?: 0L

        val transmittedBytes =
            TrafficStats.getUidTxBytes(uid).takeIf {
                it != TrafficStats.UNSUPPORTED.toLong()
            } ?: 0L

        val javaHeapUsedBytes =
            runtime.totalMemory() - runtime.freeMemory()

        val javaHeapAllocatedBytes =
            runtime.totalMemory()

        val javaHeapMaxBytes =
            runtime.maxMemory()

        return mapOf(
            "timestamp" to System.currentTimeMillis(),

            "cpuPercentPerCore" to processCpuPercentPerCore,
            "cpuPercentNormalized" to processCpuPercentNormalized,
            "processorCount" to processorCount,

            // Debug.MemoryInfo values are reported in KB.
            "totalPssMb" to kbToMb(processMemoryInfo.totalPss),
            "totalPrivateDirtyMb" to
                    kbToMb(processMemoryInfo.totalPrivateDirty),
            "dalvikPssMb" to
                    kbToMb(processMemoryInfo.dalvikPss),
            "nativePssMb" to
                    kbToMb(processMemoryInfo.nativePss),
            "otherPssMb" to
                    kbToMb(processMemoryInfo.otherPss),

            "javaHeapUsedMb" to bytesToMb(javaHeapUsedBytes),
            "javaHeapAllocatedMb" to
                    bytesToMb(javaHeapAllocatedBytes),
            "javaHeapMaxMb" to bytesToMb(javaHeapMaxBytes),

            "systemTotalMemoryMb" to
                    bytesToMb(systemMemoryInfo.totalMem),
            "systemAvailableMemoryMb" to
                    bytesToMb(systemMemoryInfo.availMem),
            "systemLowMemory" to systemMemoryInfo.lowMemory,
            "systemLowMemoryThresholdMb" to
                    bytesToMb(systemMemoryInfo.threshold),

            "networkReceivedBytes" to receivedBytes,
            "networkTransmittedBytes" to transmittedBytes,
        )
    }

    private fun kbToMb(valueKb: Int): Double {
        return valueKb.toDouble() / 1024.0
    }

    private fun bytesToMb(valueBytes: Long): Double {
        return valueBytes.toDouble() / (1024.0 * 1024.0)
    }
}
