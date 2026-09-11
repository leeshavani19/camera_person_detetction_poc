// import 'package:flutter/material.dart';
// import 'package:odigo_offline/framework/utils/performance/performance_monitor_controller.dart';
//
// class PerformanceOverlayWidget extends StatefulWidget {
//   const PerformanceOverlayWidget({super.key});
//
//   @override
//   State<PerformanceOverlayWidget> createState() =>
//       _PerformanceOverlayWidgetState();
// }
//
// class _PerformanceOverlayWidgetState
//     extends State<PerformanceOverlayWidget> {
//   final monitor = PerformanceMonitorController.instance;
//
//   static const _metricDescriptions = <(String, String, String)>[
//     (
//       'CPU',
//       'Central Processing Unit (per core)',
//       'App CPU usage relative to one core. 100% ≈ one full core; can exceed 100% when multiple cores are used.',
//     ),
//     (
//       'CPU normalized',
//       'Central Processing Unit (device-normalized)',
//       'CPU usage divided by total core count. On an 8-core device, one fully used core ≈ 12.5%.',
//     ),
//     (
//       'App RAM (PSS)',
//       'Proportional Set Size',
//       'Primary Android measure of RAM occupied by this app, including a fair share of shared pages. Use this as the main app memory value.',
//     ),
//     (
//       'App RAM %',
//       'App memory vs device RAM',
//       'Percentage of total device RAM currently occupied by this app (Total PSS ÷ System Total Memory).',
//     ),
//     (
//       'RSS',
//       'Resident Set Size',
//       'Physical RAM currently mapped into the process. Can include shared or multiply-mapped pages, so it may overstate unique app usage.',
//     ),
//     (
//       'Peak RSS',
//       'Peak Resident Set Size',
//       'Highest RSS observed since the process started.',
//     ),
//     (
//       'Native PSS',
//       'Native Proportional Set Size',
//       'RAM used by native (C/C++) allocations — often high during VideoPlayer / MediaCodec playback.',
//     ),
//     (
//       'Dalvik PSS',
//       'Dalvik / ART Proportional Set Size',
//       'RAM used by the Android Java/Kotlin runtime heap for this process.',
//     ),
//     (
//       'Other PSS',
//       'Other Proportional Set Size',
//       'Remaining PSS categories (graphics, ashmem, unknown mappings, etc.).',
//     ),
//     (
//       'Private Dirty',
//       'Total Private Dirty',
//       'Private dirty pages unique to this process that must be written to swap/disk if reclaimed.',
//     ),
//     (
//       'Java heap',
//       'Java / Dart VM heap used',
//       'Bytes currently used in the Java runtime heap (totalMemory − freeMemory).',
//     ),
//     (
//       'System RAM',
//       'Device memory used',
//       'Percent of whole-device RAM currently in use by all processes (not only this app).',
//     ),
//     (
//       'FPS',
//       'Frames Per Second',
//       'UI frames rendered in the last sample window.',
//     ),
//     (
//       'Build',
//       'UI build duration',
//       'Average time Flutter spent building the widget tree per frame (ms).',
//     ),
//     (
//       'Raster',
//       'UI raster duration',
//       'Average time spent rasterizing / GPU-uploading each frame (ms).',
//     ),
//     (
//       'Janky frames',
//       'Frames over budget',
//       'Frames whose total span exceeded ~16.67 ms (60 Hz budget) in the last sample window.',
//     ),
//     (
//       'Network RX',
//       'Network Received',
//       'Bytes received per second by this app’s UID.',
//     ),
//     (
//       'Network TX',
//       'Network Transmitted',
//       'Bytes sent per second by this app’s UID.',
//     ),
//   ];
//
//   @override
//   void initState() {
//     super.initState();
//     monitor.addListener(_update);
//
//     // start() is a no-op if already running from updatePerformanceOverlay(true).
//     if (!monitor.running) {
//       monitor.start();
//     }
//   }
//
//   void _update() {
//     if (mounted) setState(() {});
//   }
//
//   @override
//   void dispose() {
//     monitor.removeListener(_update);
//     // Do not stop here — collection is owned by
//     // DefaultSettingsController.updatePerformanceOverlay(false).
//     super.dispose();
//   }
//
//   void _showMetricsInfo() {
//     showDialog<void>(
//       context: context,
//       builder: (context) {
//         return AlertDialog(
//           backgroundColor: const Color(0xFF1A1A1A),
//           titlePadding: const EdgeInsets.fromLTRB(20, 16, 8, 0),
//           contentPadding: const EdgeInsets.fromLTRB(20, 12, 20, 8),
//           title: Row(
//             children: [
//               const Expanded(
//                 child: Text(
//                   'Performance metrics',
//                   style: TextStyle(
//                     color: Colors.white,
//                     fontSize: 16,
//                     fontWeight: FontWeight.w700,
//                   ),
//                 ),
//               ),
//               IconButton(
//                 onPressed: () => Navigator.of(context).pop(),
//                 icon: const Icon(Icons.close, color: Colors.white70, size: 20),
//               ),
//             ],
//           ),
//           content: SizedBox(
//             width: 340,
//             child: SingleChildScrollView(
//               child: Column(
//                 crossAxisAlignment: CrossAxisAlignment.start,
//                 children: [
//                   for (final item in _metricDescriptions) ...[
//                     Text(
//                       item.$1,
//                       style: const TextStyle(
//                         color: Colors.cyanAccent,
//                         fontSize: 12,
//                         fontWeight: FontWeight.w700,
//                       ),
//                     ),
//                     const SizedBox(height: 2),
//                     Text(
//                       item.$2,
//                       style: const TextStyle(
//                         color: Colors.white,
//                         fontSize: 11,
//                         fontWeight: FontWeight.w600,
//                       ),
//                     ),
//                     const SizedBox(height: 2),
//                     Text(
//                       item.$3,
//                       style: TextStyle(
//                         color: Colors.white.withValues(alpha: 0.75),
//                         fontSize: 11,
//                         height: 1.35,
//                       ),
//                     ),
//                     const Divider(color: Colors.white24, height: 18),
//                   ],
//                 ],
//               ),
//             ),
//           ),
//         );
//       },
//     );
//   }
//
//   @override
//   Widget build(BuildContext context) {
//     final data = monitor.latest;
//
//     if (data == null) {
//       return const SizedBox.shrink();
//     }
//
//     return SafeArea(
//       child: Align(
//         alignment: Alignment.topRight,
//         child: Container(
//           width: 270,
//           margin: const EdgeInsets.all(12),
//           padding: const EdgeInsets.all(12),
//           decoration: BoxDecoration(
//             color: Colors.black.withValues(alpha: 0.78),
//             borderRadius: BorderRadius.circular(12),
//           ),
//           child: DefaultTextStyle(
//             style: const TextStyle(
//               color: Colors.white,
//               fontSize: 11,
//             ),
//             child: Column(
//               mainAxisSize: MainAxisSize.min,
//               crossAxisAlignment: CrossAxisAlignment.start,
//               children: [
//                 Row(
//                   children: [
//                     Expanded(
//                       child: Text(
//                         'Scenario: ${data.scenario}',
//                         style: const TextStyle(
//                           fontWeight: FontWeight.bold,
//                           color: Colors.cyanAccent,
//                         ),
//                       ),
//                     ),
//                     InkWell(
//                       onTap: _showMetricsInfo,
//                       borderRadius: BorderRadius.circular(12),
//                       child: const Padding(
//                         padding: EdgeInsets.all(2),
//                         child: Icon(
//                           Icons.info_outline,
//                           color: Colors.cyanAccent,
//                           size: 18,
//                         ),
//                       ),
//                     ),
//                   ],
//                 ),
//                 const Divider(color: Colors.white24),
//                 _sectionTitle('App memory'),
//                 _row(
//                   'App RAM (PSS)',
//                   '${data.totalPssMb.toStringAsFixed(1)} MB',
//                   emphasize: true,
//                 ),
//                 _row(
//                   'App RAM %',
//                   '${data.appMemoryOfSystemPercent.toStringAsFixed(2)}% of device',
//                   emphasize: true,
//                 ),
//                 _row(
//                   'RSS',
//                   '${data.currentRssMb.toStringAsFixed(1)} MB',
//                 ),
//                 _row(
//                   'Peak RSS',
//                   '${data.peakRssMb.toStringAsFixed(1)} MB',
//                 ),
//                 _row(
//                   'Native PSS',
//                   '${data.nativePssMb.toStringAsFixed(1)} MB',
//                 ),
//                 _row(
//                   'Dalvik PSS',
//                   '${data.dalvikPssMb.toStringAsFixed(1)} MB',
//                 ),
//                 _row(
//                   'Other PSS',
//                   '${data.otherPssMb.toStringAsFixed(1)} MB',
//                 ),
//                 _row(
//                   'Private Dirty',
//                   '${data.totalPrivateDirtyMb.toStringAsFixed(1)} MB',
//                 ),
//                 _row(
//                   'Java heap',
//                   '${data.javaHeapUsedMb.toStringAsFixed(1)} MB',
//                 ),
//                 const Divider(color: Colors.white24),
//                 _sectionTitle('Device memory'),
//                 _row(
//                   'System total',
//                   '${data.systemTotalMemoryMb.toStringAsFixed(0)} MB',
//                 ),
//                 _row(
//                   'System available',
//                   '${data.systemAvailableMemoryMb.toStringAsFixed(0)} MB',
//                 ),
//                 _row(
//                   'System RAM used',
//                   '${data.systemMemoryUsedPercent.toStringAsFixed(1)}%',
//                 ),
//                 const Divider(color: Colors.white24),
//                 _sectionTitle('CPU & UI'),
//                 _row(
//                   'CPU',
//                   '${data.cpuPercentPerCore.toStringAsFixed(1)}%',
//                 ),
//                 _row(
//                   'CPU normalized',
//                   '${data.cpuPercentNormalized.toStringAsFixed(1)}%',
//                 ),
//                 _row(
//                   'FPS',
//                   data.fps.toStringAsFixed(1),
//                 ),
//                 _row(
//                   'Build',
//                   '${data.averageBuildMs.toStringAsFixed(2)} ms',
//                 ),
//                 _row(
//                   'Raster',
//                   '${data.averageRasterMs.toStringAsFixed(2)} ms',
//                 ),
//                 _row(
//                   'Janky frames',
//                   '${data.jankyFrames}',
//                 ),
//                 const Divider(color: Colors.white24),
//                 _row(
//                   'Network RX',
//                   _formatBytesPerSecond(
//                     data.receivedBytesPerSecond,
//                   ),
//                 ),
//                 _row(
//                   'Network TX',
//                   _formatBytesPerSecond(
//                     data.transmittedBytesPerSecond,
//                   ),
//                 ),
//               ],
//             ),
//           ),
//         ),
//       ),
//     );
//   }
//
//   Widget _sectionTitle(String title) {
//     return Padding(
//       padding: const EdgeInsets.only(bottom: 4),
//       child: Text(
//         title,
//         style: TextStyle(
//           color: Colors.white.withValues(alpha: 0.55),
//           fontSize: 10,
//           fontWeight: FontWeight.w600,
//           letterSpacing: 0.3,
//         ),
//       ),
//     );
//   }
//
//   Widget _row(String label, String value, {bool emphasize = false}) {
//     return Padding(
//       padding: const EdgeInsets.symmetric(vertical: 1.5),
//       child: Row(
//         children: [
//           Expanded(
//             child: Text(
//               label,
//               style: TextStyle(
//                 fontWeight: emphasize ? FontWeight.w700 : FontWeight.normal,
//                 color: emphasize ? Colors.lightGreenAccent : Colors.white,
//               ),
//             ),
//           ),
//           Text(
//             value,
//             style: TextStyle(
//               fontWeight: FontWeight.w600,
//               color: emphasize ? Colors.lightGreenAccent : Colors.white,
//             ),
//           ),
//         ],
//       ),
//     );
//   }
//
//   String _formatBytesPerSecond(double bytes) {
//     if (bytes >= 1024 * 1024) {
//       return '${(bytes / (1024 * 1024)).toStringAsFixed(2)} MB/s';
//     }
//
//     if (bytes >= 1024) {
//       return '${(bytes / 1024).toStringAsFixed(2)} KB/s';
//     }
//
//     return '${bytes.toStringAsFixed(0)} B/s';
//   }
// }
