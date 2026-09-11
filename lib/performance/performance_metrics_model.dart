class PerformanceMetricsModel {
  const PerformanceMetricsModel({
    required this.timestamp,
    required this.cpuPercentPerCore,
    required this.cpuPercentNormalized,
    required this.processorCount,
    required this.currentRssMb,
    required this.peakRssMb,
    required this.totalPssMb,
    required this.totalPrivateDirtyMb,
    required this.dalvikPssMb,
    required this.nativePssMb,
    required this.otherPssMb,
    required this.javaHeapUsedMb,
    required this.javaHeapAllocatedMb,
    required this.javaHeapMaxMb,
    required this.systemTotalMemoryMb,
    required this.systemAvailableMemoryMb,
    required this.systemLowMemory,
    required this.networkReceivedBytes,
    required this.networkTransmittedBytes,
    required this.receivedBytesPerSecond,
    required this.transmittedBytesPerSecond,
    required this.fps,
    required this.averageBuildMs,
    required this.averageRasterMs,
    required this.jankyFrames,
    required this.scenario,
  });

  final int timestamp;

  final double cpuPercentPerCore;
  final double cpuPercentNormalized;
  final int processorCount;

  final double currentRssMb;
  final double peakRssMb;

  final double totalPssMb;
  final double totalPrivateDirtyMb;
  final double dalvikPssMb;
  final double nativePssMb;
  final double otherPssMb;

  final double javaHeapUsedMb;
  final double javaHeapAllocatedMb;
  final double javaHeapMaxMb;

  final double systemTotalMemoryMb;
  final double systemAvailableMemoryMb;
  final bool systemLowMemory;

  final int networkReceivedBytes;
  final int networkTransmittedBytes;

  final double receivedBytesPerSecond;
  final double transmittedBytesPerSecond;

  final double fps;
  final double averageBuildMs;
  final double averageRasterMs;
  final int jankyFrames;

  final String scenario;

  double get systemMemoryUsedPercent {
    if (systemTotalMemoryMb <= 0) return 0;

    return ((systemTotalMemoryMb - systemAvailableMemoryMb) /
            systemTotalMemoryMb) *
        100;
  }

  /// App's share of device RAM, based on Total PSS.
  double get appMemoryOfSystemPercent {
    if (systemTotalMemoryMb <= 0) return 0;

    return (totalPssMb / systemTotalMemoryMb) * 100;
  }

  PerformanceMetricsModel copyWith({
    double? currentRssMb,
    double? peakRssMb,
    double? receivedBytesPerSecond,
    double? transmittedBytesPerSecond,
    double? fps,
    double? averageBuildMs,
    double? averageRasterMs,
    int? jankyFrames,
    String? scenario,
  }) {
    return PerformanceMetricsModel(
      timestamp: timestamp,
      cpuPercentPerCore: cpuPercentPerCore,
      cpuPercentNormalized: cpuPercentNormalized,
      processorCount: processorCount,
      currentRssMb: currentRssMb ?? this.currentRssMb,
      peakRssMb: peakRssMb ?? this.peakRssMb,
      totalPssMb: totalPssMb,
      totalPrivateDirtyMb: totalPrivateDirtyMb,
      dalvikPssMb: dalvikPssMb,
      nativePssMb: nativePssMb,
      otherPssMb: otherPssMb,
      javaHeapUsedMb: javaHeapUsedMb,
      javaHeapAllocatedMb: javaHeapAllocatedMb,
      javaHeapMaxMb: javaHeapMaxMb,
      systemTotalMemoryMb: systemTotalMemoryMb,
      systemAvailableMemoryMb: systemAvailableMemoryMb,
      systemLowMemory: systemLowMemory,
      networkReceivedBytes: networkReceivedBytes,
      networkTransmittedBytes: networkTransmittedBytes,
      receivedBytesPerSecond:
          receivedBytesPerSecond ?? this.receivedBytesPerSecond,
      transmittedBytesPerSecond:
          transmittedBytesPerSecond ??
              this.transmittedBytesPerSecond,
      fps: fps ?? this.fps,
      averageBuildMs: averageBuildMs ?? this.averageBuildMs,
      averageRasterMs: averageRasterMs ?? this.averageRasterMs,
      jankyFrames: jankyFrames ?? this.jankyFrames,
      scenario: scenario ?? this.scenario,
    );
  }

  factory PerformanceMetricsModel.fromMap(
    Map<dynamic, dynamic> map,
  ) {
    double number(String key) {
      return (map[key] as num?)?.toDouble() ?? 0;
    }

    int integer(String key) {
      return (map[key] as num?)?.toInt() ?? 0;
    }

    return PerformanceMetricsModel(
      timestamp: integer('timestamp'),
      cpuPercentPerCore: number('cpuPercentPerCore'),
      cpuPercentNormalized: number('cpuPercentNormalized'),
      processorCount: integer('processorCount'),
      currentRssMb: 0,
      peakRssMb: 0,
      totalPssMb: number('totalPssMb'),
      totalPrivateDirtyMb: number('totalPrivateDirtyMb'),
      dalvikPssMb: number('dalvikPssMb'),
      nativePssMb: number('nativePssMb'),
      otherPssMb: number('otherPssMb'),
      javaHeapUsedMb: number('javaHeapUsedMb'),
      javaHeapAllocatedMb: number('javaHeapAllocatedMb'),
      javaHeapMaxMb: number('javaHeapMaxMb'),
      systemTotalMemoryMb: number('systemTotalMemoryMb'),
      systemAvailableMemoryMb:
          number('systemAvailableMemoryMb'),
      systemLowMemory: map['systemLowMemory'] == true,
      networkReceivedBytes: integer('networkReceivedBytes'),
      networkTransmittedBytes:
          integer('networkTransmittedBytes'),
      receivedBytesPerSecond: 0,
      transmittedBytesPerSecond: 0,
      fps: 0,
      averageBuildMs: 0,
      averageRasterMs: 0,
      jankyFrames: 0,
      scenario: PerformanceScenario.foregroundIdle.name,
    );
  }

  String toCsvRow() {
    return [
      timestamp,
      scenario,
      cpuPercentPerCore.toStringAsFixed(2),
      cpuPercentNormalized.toStringAsFixed(2),
      currentRssMb.toStringAsFixed(2),
      peakRssMb.toStringAsFixed(2),
      totalPssMb.toStringAsFixed(2),
      dalvikPssMb.toStringAsFixed(2),
      nativePssMb.toStringAsFixed(2),
      otherPssMb.toStringAsFixed(2),
      javaHeapUsedMb.toStringAsFixed(2),
      systemTotalMemoryMb.toStringAsFixed(2),
      systemAvailableMemoryMb.toStringAsFixed(2),
      fps.toStringAsFixed(2),
      averageBuildMs.toStringAsFixed(2),
      averageRasterMs.toStringAsFixed(2),
      jankyFrames,
      receivedBytesPerSecond.toStringAsFixed(2),
      transmittedBytesPerSecond.toStringAsFixed(2),
    ].join(',');
  }

  static const csvHeader =
      'timestamp,scenario,cpu_per_core_percent,'
      'cpu_normalized_percent,current_rss_mb,peak_rss_mb,'
      'total_pss_mb,dalvik_pss_mb,native_pss_mb,'
      'other_pss_mb,java_heap_used_mb,'
      'system_total_mb,system_available_mb,fps,'
      'average_build_ms,average_raster_ms,janky_frames,'
      'rx_bytes_per_second,tx_bytes_per_second';
}

enum PerformanceScenario {
  foregroundIdle,
  imageAd,
  videoAdInitializing,
  videoAdPlaying,
  videoAdDisposed,
  userInteraction,
  storeNavigation,
  cruiseMode,
  chargingNavigation,
  socketReconnect,
}
