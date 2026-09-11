// import 'dart:async';
// import 'dart:io';
//
// import 'package:flutter/foundation.dart';
// import 'package:flutter/scheduler.dart';
// import 'package:flutter/services.dart';
// import 'package:odigo_offline/framework/utils/performance/performance_metrics_model.dart';
// import 'package:odigo_offline/ui/utils/const/method_channel_const.dart';
// import 'package:path_provider/path_provider.dart';
//
// class PerformanceMonitorController extends ChangeNotifier {
//   PerformanceMonitorController._();
//
//   static final PerformanceMonitorController instance =
//       PerformanceMonitorController._();
//
//   static const MethodChannel _channel =
//       MethodChannel(MethodChannelConstant.odigoAndroidChannel);
//
//   final List<PerformanceMetricsModel> _samples = [];
//
//   Timer? _timer;
//   TimingsCallback? _timingsCallback;
//
//   PerformanceScenario _scenario =
//       PerformanceScenario.foregroundIdle;
//
//   bool _running = false;
//   bool _sampling = false;
//
//   int _frameCount = 0;
//   int _buildMicroseconds = 0;
//   int _rasterMicroseconds = 0;
//   int _jankyFrames = 0;
//
//   DateTime _frameWindowStartedAt = DateTime.now();
//
//   int? _previousReceivedBytes;
//   int? _previousTransmittedBytes;
//   DateTime? _previousNetworkSampleTime;
//
//   PerformanceMetricsModel? _latest;
//
//   PerformanceMetricsModel? get latest => _latest;
//
//   List<PerformanceMetricsModel> get samples =>
//       List.unmodifiable(_samples);
//
//   PerformanceScenario get scenario => _scenario;
//
//   bool get running => _running;
//
//   Future<void> start({
//     Duration interval = const Duration(seconds: 1),
//   }) async {
//     if (_running) return;
//
//     _running = true;
//     _resetFrameWindow();
//
//     _timingsCallback = _handleFrameTimings;
//
//     SchedulerBinding.instance.addTimingsCallback(
//       _timingsCallback!,
//     );
//
//     await _sample();
//
//     _timer = Timer.periodic(interval, (_) {
//       unawaited(_sample());
//     });
//
//     notifyListeners();
//   }
//
//   Future<void> stop() async {
//     _timer?.cancel();
//     _timer = null;
//
//     final callback = _timingsCallback;
//
//     if (callback != null) {
//       SchedulerBinding.instance.removeTimingsCallback(callback);
//     }
//
//     _timingsCallback = null;
//     _running = false;
//
//     notifyListeners();
//   }
//
//   void setScenario(PerformanceScenario value) {
//     if (!_running) return;
//     if (_scenario == value) return;
//
//     _scenario = value;
//     showPerformanceLog('Scenario changed to ${value.name}');
//     notifyListeners();
//   }
//
//   void _handleFrameTimings(List<FrameTiming> timings) {
//     for (final timing in timings) {
//       _frameCount++;
//
//       _buildMicroseconds +=
//           timing.buildDuration.inMicroseconds;
//
//       _rasterMicroseconds +=
//           timing.rasterDuration.inMicroseconds;
//
//       /*
//        * At 60Hz, a frame budget is approximately 16.67 ms.
//        * Count the frame as janky when total processing exceeds it.
//        */
//       if (timing.totalSpan.inMicroseconds > 16667) {
//         _jankyFrames++;
//       }
//     }
//   }
//
//   Future<void> _sample() async {
//     if (_sampling || !_running) return;
//
//     _sampling = true;
//
//     try {
//       final result =
//           await _channel.invokeMapMethod<dynamic, dynamic>(
//         'getPerformanceMetrics',
//       );
//
//       if (result == null) return;
//
//       var metrics =
//           PerformanceMetricsModel.fromMap(result);
//
//       final now = DateTime.now();
//
//       final elapsedSeconds = now
//               .difference(_frameWindowStartedAt)
//               .inMicroseconds /
//           Duration.microsecondsPerSecond;
//
//       final fps = elapsedSeconds > 0
//           ? _frameCount / elapsedSeconds
//           : 0.0;
//
//       final averageBuildMs = _frameCount > 0
//           ? (_buildMicroseconds / _frameCount) / 1000.0
//           : 0.0;
//
//       final averageRasterMs = _frameCount > 0
//           ? (_rasterMicroseconds / _frameCount) / 1000.0
//           : 0.0;
//
//       final currentRssMb =
//           ProcessInfo.currentRss / (1024 * 1024);
//
//       final peakRssMb =
//           ProcessInfo.maxRss / (1024 * 1024);
//
//       final networkRates = _calculateNetworkRates(
//         receivedBytes: metrics.networkReceivedBytes,
//         transmittedBytes: metrics.networkTransmittedBytes,
//         sampledAt: now,
//       );
//
//       metrics = metrics.copyWith(
//         currentRssMb: currentRssMb,
//         peakRssMb: peakRssMb,
//         receivedBytesPerSecond: networkRates.$1,
//         transmittedBytesPerSecond: networkRates.$2,
//         fps: fps.clamp(0, 240),
//         averageBuildMs: averageBuildMs,
//         averageRasterMs: averageRasterMs,
//         jankyFrames: _jankyFrames,
//         scenario: _scenario.name,
//       );
//
//       _latest = metrics;
//       _samples.add(metrics);
//
//       // Keep one hour when sampling every second.
//       if (_samples.length > 3600) {
//         _samples.removeAt(0);
//       }
//
//       _resetFrameWindow();
//
//       notifyListeners();
//     } on PlatformException catch (error, stackTrace) {
//       debugPrint(
//         'Performance metrics platform error: '
//         '${error.code} ${error.message}\n$stackTrace',
//       );
//     } catch (error, stackTrace) {
//       debugPrint(
//         'Performance metrics error: $error\n$stackTrace',
//       );
//     } finally {
//       _sampling = false;
//     }
//   }
//
//   (double, double) _calculateNetworkRates({
//     required int receivedBytes,
//     required int transmittedBytes,
//     required DateTime sampledAt,
//   }) {
//     final previousTime = _previousNetworkSampleTime;
//
//     if (previousTime == null ||
//         _previousReceivedBytes == null ||
//         _previousTransmittedBytes == null) {
//       _previousReceivedBytes = receivedBytes;
//       _previousTransmittedBytes = transmittedBytes;
//       _previousNetworkSampleTime = sampledAt;
//
//       return (0, 0);
//     }
//
//     final elapsedSeconds = sampledAt
//             .difference(previousTime)
//             .inMicroseconds /
//         Duration.microsecondsPerSecond;
//
//     if (elapsedSeconds <= 0) return (0, 0);
//
//     final receivedDifference =
//         receivedBytes - _previousReceivedBytes!;
//
//     final transmittedDifference =
//         transmittedBytes - _previousTransmittedBytes!;
//
//     _previousReceivedBytes = receivedBytes;
//     _previousTransmittedBytes = transmittedBytes;
//     _previousNetworkSampleTime = sampledAt;
//
//     return (
//       receivedDifference > 0
//           ? receivedDifference / elapsedSeconds
//           : 0,
//       transmittedDifference > 0
//           ? transmittedDifference / elapsedSeconds
//           : 0,
//     );
//   }
//
//   void _resetFrameWindow() {
//     _frameCount = 0;
//     _buildMicroseconds = 0;
//     _rasterMicroseconds = 0;
//     _jankyFrames = 0;
//     _frameWindowStartedAt = DateTime.now();
//   }
//
//   Future<File?> exportCsv() async {
//     if (_samples.isEmpty) return null;
//
//     final directory =
//         await getApplicationDocumentsDirectory();
//
//     final timestamp =
//         DateTime.now().millisecondsSinceEpoch;
//
//     final file = File(
//       '${directory.path}/performance_metrics_$timestamp.csv',
//     );
//
//     final content = StringBuffer()
//       ..writeln(PerformanceMetricsModel.csvHeader);
//
//     for (final sample in _samples) {
//       content.writeln(sample.toCsvRow());
//     }
//
//     await file.writeAsString(
//       content.toString(),
//       flush: true,
//     );
//
//     return file;
//   }
//
//   void clear() {
//     _samples.clear();
//     _latest = null;
//     notifyListeners();
//   }
//
//   void showPerformanceLog(String message) {
//     debugPrint('[PERFORMANCE] $message');
//   }
//
//   @override
//   void dispose() {
//     _timer?.cancel();
//
//     final callback = _timingsCallback;
//
//     if (callback != null) {
//       SchedulerBinding.instance.removeTimingsCallback(callback);
//     }
//
//     super.dispose();
//   }
// }
