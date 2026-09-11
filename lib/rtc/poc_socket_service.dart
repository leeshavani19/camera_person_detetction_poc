import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:socket_io_client/socket_io_client.dart'
as io;

import 'poc_rtc_broadcast_handler.dart';
import 'poc_session.dart';

class PocSocketService {
  PocSocketService._();

  static final PocSocketService instance =
  PocSocketService._();

  // ============================================================
  // SOCKET
  // ============================================================

  io.Socket? socket;

  static const String socketBaseUrl = 'https://socket.odigo-v3.kodyinfotech.com';

  static const String destinationToRobots = 'DESTINATION_TO_ROBOTS';

  static const String robotToDestination = 'ROBOT_TO_DESTINATION';

  static const String rtcMessage = 'message';

  // ============================================================
  // RTC
  // ============================================================

  PocRtcBroadcastHandler? _rtcHandler;

  Function(dynamic)? _rtcSignalCallback;

  // ============================================================
  // WATCHERS
  // ============================================================

  final List<Map<String, dynamic>>
  _connectedWatchers = [];

  String? _currentWatcherId;

  String? _watcherSentFrom;

  String? get currentWatcherId =>
      _currentWatcherId;

  bool get isConnected =>
      socket?.connected == true;

  // ============================================================
  // RTC CONFIG
  // ============================================================

  static const Map<String, dynamic> rtcConfig = {
    'iceServers': [
      {
        'urls': 'stun:stun.l.google.com:19302',
      },
    ],
  };

  // ============================================================
  // CONNECT
  // ============================================================

  Future<void> connect() async {
    final entityUuid =
        PocSession.entityUuid;

    final entityType =
        PocSession.entityType;

    final mapsUuid =
        PocSession.mapsUuid;

    final token =
        PocSession.token;

    if (entityUuid == null ||
        entityUuid.isEmpty ||
        entityType == null ||
        entityType.isEmpty ||
        token == null ||
        token.isEmpty) {
      throw StateError(
        'POC session information missing',
      );
    }

    const separator =
        '_com.kodytechnolabs.odigo_';

    final url =
        '$socketBaseUrl'
        '?userName=$entityUuid'
        '$separator'
        '$entityType'
        '&mapsUuid=${mapsUuid ?? ''}';

    debugPrint(
      '[POC SOCKET] URL → $url',
    );

    // ------------------------------------------------------------
    // Dispose old socket
    // ------------------------------------------------------------

    try {
      socket?.disconnect();
    } catch (_) {}

    try {
      socket?.dispose();
    } catch (_) {}

    socket = null;

    await Future.delayed(
      const Duration(
        milliseconds: 300,
      ),
    );

    // ------------------------------------------------------------
    // Create socket
    // ------------------------------------------------------------

    final newSocket =
    io.io(
      url,
      io.OptionBuilder()
          .setTransports([
        'websocket',
      ])
          .disableAutoConnect()
          .setExtraHeaders({
        'X-Auth-Token': token,
      })
          .build(),
    );

    socket =
        newSocket;

    debugPrint(
      '[POC SOCKET] '
          'Socket object created',
    );

    // ============================================================
    // ERROR
    // ============================================================

    newSocket.onConnectError(
          (data) {
        debugPrint(
          '[POC SOCKET] '
              'CONNECT ERROR → $data',
        );
      },
    );

    newSocket.onError(
          (data) {
        debugPrint(
          '[POC SOCKET] '
              'ERROR → $data',
        );
      },
    );

    // ============================================================
    // DISCONNECT
    // ============================================================

    newSocket.onDisconnect(
          (data) {
        debugPrint(
          '[POC SOCKET] '
              'DISCONNECTED → $data',
        );
      },
    );

    // ============================================================
    // CONNECTED
    // ============================================================

    newSocket.onConnect(
          (_) async {
        debugPrint(
          '[POC SOCKET] '
              'CONNECTED → ${newSocket.id}',
        );

        debugPrint(
          '[POC SOCKET] '
              'socketId → ${newSocket.id}',
        );

        listenToDestinationCommands();

        // ======================================================
        // START PERSISTENT CAMERA + AI
        //
        // This happens ONCE after socket/session is ready.
        //
        // OPEN_CAMERA is NOT required to start physical camera.
        // ======================================================

        try {
          await startPersistentCameraAndAi();
        } catch (e, stack) {
          debugPrint(
            '[POC CAMERA] '
                'Persistent camera start failed → $e',
          );

          debugPrint(
            '$stack',
          );
        }
      },
    );

    // ============================================================
    // ALL EVENTS DEBUG
    // ============================================================

    newSocket.onAny(
          (event, data) {
        debugPrint(
          '[POC SOCKET] '
              'EVENT → $event | DATA → $data',
        );
      },
    );

    // ============================================================
    // CONNECT
    // ============================================================

    debugPrint(
      '[POC SOCKET] '
          'Calling socket.connect()',
    );

    newSocket.connect();
  }

  // ============================================================
  // START PERSISTENT CAMERA + AI
  // ============================================================

  Future<void> startPersistentCameraAndAi() async {
    _rtcHandler ??=
        PocRtcBroadcastHandler();

    debugPrint(
      '[POC CAMERA] '
          'Starting persistent camera + AI',
    );

    await _rtcHandler!
        .startCameraAndAi();

    debugPrint(
      '[POC CAMERA] '
          'Persistent camera + AI started',
    );
  }

  // ============================================================
  // DESTINATION -> ROBOT COMMANDS
  // ============================================================

  void listenToDestinationCommands() {
    final currentSocket =
        socket;

    if (currentSocket == null) {
      debugPrint(
        '[POC SOCKET] '
            'Cannot register command listener: '
            'socket is null',
      );
      return;
    }

    currentSocket.off(
      destinationToRobots,
    );

    currentSocket.on(
      destinationToRobots,
          (raw) async {
        debugPrint(
          '[POC SOCKET] '
              'DESTINATION_TO_ROBOTS → $raw',
        );

        try {
          if (raw is! Map) {
            debugPrint(
              '[POC SOCKET] '
                  'Invalid destination payload',
            );
            return;
          }

          final message =
          raw['message'];

          dynamic decoded;

          if (message is String) {
            decoded =
                jsonDecode(message);
          } else {
            decoded =
                message;
          }

          if (decoded is! Map) {
            debugPrint(
              '[POC SOCKET] '
                  'Invalid message body',
            );
            return;
          }

          final method =
          decoded['method']
              ?.toString();

          final data =
          decoded['data'];

          debugPrint(
            '[POC SOCKET] '
                'METHOD → $method',
          );

          debugPrint(
            '[POC SOCKET] '
                'sentFrom → '
                '${decoded['sentFrom']}',
          );

          // ======================================================
          // COMMANDS
          // ======================================================

          switch (method) {

          // --------------------------------------------------
          // ADD WATCHER
          // --------------------------------------------------

            case 'addWatcher':
              _handleAddWatcher(
                data,
                decoded,
              );
              break;

          // --------------------------------------------------
          // OPEN CAMERA
          // --------------------------------------------------

            case 'OPEN_CAMERA':
              await _handleOpenCamera(
                data,
              );
              break;

          // --------------------------------------------------
          // CLOSE CAMERA
          // --------------------------------------------------

            case 'CLOSE_CAMERA':
              await _handleCloseCamera(
                data,
              );
              break;

          // --------------------------------------------------
          // REMOVE WATCHER
          // --------------------------------------------------

            case 'removeWatcher':
              _handleRemoveWatcher(
                data,
              );
              break;

            default:
              debugPrint(
                '[POC SOCKET] '
                    'Unhandled method → $method',
              );
              break;
          }
        } catch (e, stack) {
          debugPrint(
            '[POC SOCKET] '
                'Command error → $e',
          );

          debugPrint(
            '$stack',
          );
        }
      },
    );

    debugPrint(
      '[POC SOCKET] '
          'Destination command listener registered',
    );
  }

  // ============================================================
  // ADD WATCHER
  // ============================================================

  void _handleAddWatcher(
      dynamic data,
      dynamic decoded,
      ) {
    if (data is! Map) {
      debugPrint(
        '[POC RTC] '
            'addWatcher data invalid',
      );
      return;
    }

    final watcherId =
    (
        data['socketId'] ??
            data['socket_id'] ??
            data['id'] ??
            decoded['sentFrom']
    )?.toString();

    _currentWatcherId =
        watcherId;

    _watcherSentFrom =
        decoded['sentFrom']
            ?.toString();

    final watcher =
    <String, dynamic>{
      ...data.cast<String, dynamic>(),
      'socketId':
      watcherId,
      'sentFrom':
      _watcherSentFrom,
      'mapsUuid':
      PocSession.mapsUuid,
      'addedAt':
      DateTime.now()
          .toIso8601String(),
    };

    _connectedWatchers
      ..removeWhere(
            (existing) =>
        existing['socketId'] ==
            watcherId,
      )
      ..add(watcher);

    debugPrint(
      '[POC RTC] '
          'WATCHER REGISTERED → $watcherId',
    );

    debugPrint(
      '[POC RTC] '
          'WATCHER sentFrom → '
          '$_watcherSentFrom',
    );

    debugPrint(
      '[POC RTC] '
          'WATCHERS → '
          '$_connectedWatchers',
    );

    _sendWatchers();
  }

  // ============================================================
  // REMOVE WATCHER
  //
  // IMPORTANT:
  // Does NOT stop camera.
  // Does NOT stop AI.
  // ============================================================

  void _handleRemoveWatcher(
      dynamic data,
      ) {
    if (data is! Map) {
      return;
    }

    final watcherId =
    (
        data['socketId'] ??
            data['socket_id'] ??
            data['id']
    )?.toString();

    if (watcherId == null ||
        watcherId.isEmpty) {
      return;
    }

    _connectedWatchers
        .removeWhere(
          (watcher) =>
      watcher['socketId']
          ?.toString() ==
          watcherId,
    );

    if (_currentWatcherId ==
        watcherId) {
      _currentWatcherId = null;
    }

    debugPrint(
      '[POC RTC] '
          'WATCHER REMOVED → $watcherId',
    );

    _sendWatchers();

    debugPrint(
      '[POC RTC] '
          'Camera + AI remain running',
    );
  }

  // ============================================================
  // SEND WATCHERS
  // ============================================================

  void _sendWatchers() {
    final destination =
        _watcherSentFrom;

    if (destination == null ||
        destination.isEmpty) {
      debugPrint(
        '[POC SOCKET] '
            'No destination for getWatchers',
      );
      return;
    }

    final watchers =
    _connectedWatchers
        .map(
          (watcher) =>
      Map<String, dynamic>.from(
        watcher,
      ),
    )
        .toList();

    final compressed =
    jsonEncode({
      'watchers':
      watchers,
      'mapsUuid':
      PocSession.mapsUuid,
    });

    final payload = {
      'toData': [
        destination,
      ],
      'data': {
        'method':
        'getWatchers',
        'data':
        compressed,
      },
    };

    debugPrint(
      '[POC SOCKET] '
          'SEND ROBOT_TO_DESTINATION '
          '→ method=getWatchers',
    );

    debugPrint(
      '[POC SOCKET] '
          'TO → [$destination]',
    );

    _emitRobotToDestination(
      payload,
    );
  }

  // ============================================================
  // CAMERA READY
  // ============================================================

  void sendCameraReady({
    String? cameraId,
  }) {
    final destination =
        _watcherSentFrom;

    if (destination == null ||
        destination.isEmpty) {
      debugPrint(
        '[POC SOCKET] '
            'camera-ready skipped: '
            'no destination watcher',
      );
      return;
    }

    final payload = {
      'toData': [
        destination,
      ],
      'data': {
        'method':
        'camera-ready',
        'data':
        jsonEncode({
          'value':
          true,
          'cameraId':
          cameraId,
        }),
      },
    };

    debugPrint(
      '[POC SOCKET] '
          'SEND camera-ready '
          '→ cameraId=$cameraId',
    );

    _emitRobotToDestination(
      payload,
    );
  }

  // ============================================================
  // OPEN CAMERA
  // ============================================================

  Future<void> _handleOpenCamera(
      dynamic data,
      ) async {
    if (data is! Map) {
      debugPrint(
        '[POC RTC] '
            'OPEN_CAMERA data invalid',
      );
      return;
    }

    final cameraId =
    data['cameraId']
        ?.toString();

    if (cameraId == null ||
        cameraId.isEmpty) {
      debugPrint(
        '[POC RTC] '
            'OPEN_CAMERA missing cameraId',
      );
      return;
    }

    debugPrint(
      '[POC RTC] '
          'OPEN_CAMERA → cameraId=$cameraId',
    );

    // ==========================================================
    // DO NOT CREATE ANOTHER CAMERA
    //
    // _rtcHandler already owns the persistent camera.
    // ==========================================================

    _rtcHandler ??=
        PocRtcBroadcastHandler();

    // ==========================================================
    // Start RTC signaling against existing camera.
    // ==========================================================

    await _rtcHandler!
        .handleMessageForRTC(
      cameraId: cameraId,
    );

    debugPrint(
      '[POC RTC] '
          'OPEN_CAMERA processing complete',
    );
  }

  // ============================================================
  // CLOSE CAMERA
  //
  // IMPORTANT:
  // Only closes panel viewer connection.
  //
  // Persistent camera continues.
  // AI continues.
  // ============================================================

  Future<void> _handleCloseCamera(
      dynamic data,
      ) async {
    String? watcherId;

    if (data is Map) {
      watcherId =
          (
              data['watcherId'] ??
                  data['socketId'] ??
                  data['socket_id']
          )?.toString();
    }

    watcherId ??=
        _currentWatcherId;

    debugPrint(
      '[POC RTC] '
          'CLOSE_CAMERA → watcher=$watcherId',
    );

    final handler =
        _rtcHandler;

    if (handler == null) {
      debugPrint(
        '[POC RTC] '
            'No RTC handler',
      );
      return;
    }

    if (watcherId != null &&
        watcherId.isNotEmpty) {
      await handler
          .disconnectViewer(
        watcherId,
      );
    }

    // ==========================================================
    // DO NOT:
    //
    // handler.disposeAsync()
    // _rtcHandler = null
    // stop camera
    // stop AI
    //
    // ==========================================================

    debugPrint(
      '[POC RTC] '
          'CLOSE_CAMERA complete',
    );

    debugPrint(
      '[POC RTC] '
          'Camera remains running',
    );

    debugPrint(
      '[POC RTC] '
          'AI remains running',
    );
  }

  // ============================================================
  // RTC SIGNAL LISTENER
  // ============================================================

  void onRtcMessage(
      Future<void> Function(
          dynamic data,
          ) callback,
      ) {
    _rtcSignalCallback =
        callback;

    final currentSocket =
        socket;

    if (currentSocket == null) {
      debugPrint(
        '[POC SOCKET] '
            'Cannot register RTC listener: '
            'socket null',
      );
      return;
    }

    currentSocket.off(
      rtcMessage,
    );

    currentSocket.on(
      rtcMessage,
          (data) {
        debugPrint(
          '[POC SOCKET] '
              'RTC MESSAGE → $data',
        );

        if (data == null) {
          return;
        }

        final callback =
            _rtcSignalCallback;

        if (callback != null) {
          unawaited(
            callback(data),
          );
        }
      },
    );

    debugPrint(
      '[POC SOCKET] '
          'RTC message listener registered',
    );
  }

  // ============================================================
  // REMOVE RTC LISTENER
  // ============================================================

  void removeRtcMessageListener() {
    socket?.off(
      rtcMessage,
    );

    _rtcSignalCallback =
    null;

    debugPrint(
      '[POC SOCKET] '
          'RTC listener removed',
    );
  }

  // ============================================================
  // REGISTER BROADCASTER
  // ============================================================

  void registerBroadcaster() {
    send(
      'broadcaster',
      {
        'type':
        'broadcaster',
      },
    );
  }

  // ============================================================
  // ESTABLISH RTC
  // ============================================================

  void establishRtc({
    required String cameraId,
  }) {
    send(
      'establish-rtc',
      {
        'type':
        'establish-rtc',
        'cameraId':
        cameraId,
        'senderType':
        'broadcaster',
      },
    );
  }

  // ============================================================
  // OFFER
  // ============================================================

  void sendOffer({
    required String watcherId,
    required String sdp,
  }) {
    send(
      'offer',
      {
        'type':
        'offer',
        'id':
        watcherId,
        'data': {
          'type':
          'offer',
          'sdp':
          sdp,
        },
      },
    );
  }

  // ============================================================
  // ANSWER
  // ============================================================

  void sendAnswer({
    required String broadcasterId,
    required String sdp,
  }) {
    send(
      'answer',
      {
        'type':
        'answer',
        'id':
        broadcasterId,
        'data': {
          'type':
          'answer',
          'sdp':
          sdp,
        },
      },
    );
  }

  // ============================================================
  // ICE
  // ============================================================

  void sendCandidate({
    required String targetId,
    required String candidate,
    required String sdpMid,
    required int sdpMLineIndex,
  }) {
    send(
      'candidate',
      {
        'type':
        'candidate',
        'id':
        targetId,
        'data': {
          'candidate':
          candidate,
          'sdpMid':
          sdpMid,
          'sdpMLineIndex':
          sdpMLineIndex,
        },
      },
    );
  }

  // ============================================================
  // DISPOSE RTC SIGNALING
  // ============================================================

  void disposeRTCConnection() {
    send(
      'dispose',
      {
        'type':
        'dispose',
        'senderType':
        'broadcaster',
      },
    );
  }

  // ============================================================
  // SEND GENERIC SOCKET EVENT
  // ============================================================

  void send(
      String event,
      Map<String, dynamic> payload,
      ) {
    final currentSocket =
        socket;

    if (currentSocket == null) {
      debugPrint(
        '[POC SOCKET] '
            'Cannot send $event → socket null',
      );
      return;
    }

    if (!currentSocket.connected) {
      debugPrint(
        '[POC SOCKET] '
            'Cannot send $event → socket not connected',
      );
      return;
    }

    final encoded =
    jsonEncode(payload);

    debugPrint(
      '[POC SOCKET] '
          'SEND $event → $encoded',
    );

    currentSocket.emit(
      event,
      encoded,
    );
  }

  // ============================================================
  // ROBOT TO DESTINATION
  // ============================================================

  void _emitRobotToDestination(
      Map<String, dynamic> payload,
      ) {
    final currentSocket =
        socket;

    if (currentSocket == null ||
        !currentSocket.connected) {
      debugPrint(
        '[POC SOCKET] '
            'Cannot send ROBOT_TO_DESTINATION '
            '→ socket not connected',
      );
      return;
    }

    final encoded =
    jsonEncode(payload);

    debugPrint(
      '[POC SOCKET] '
          'SEND ROBOT_TO_DESTINATION '
          '→ $encoded',
    );

    currentSocket.emitWithAck(
      robotToDestination,
      encoded,
      ack: (data) {
        debugPrint(
          '[POC SOCKET] '
              'ACK ROBOT_TO_DESTINATION '
              '→ $data',
        );
      },
    );
  }

  // ============================================================
  // FULL DISPOSE
  //
  // This is the ONLY place where persistent camera is stopped.
  // ============================================================

  Future<void> dispose() async {
    debugPrint(
      '[POC SOCKET] '
          'Full socket service dispose',
    );

    try {
      await _rtcHandler
          ?.disposeAsync();
    } catch (e) {
      debugPrint(
        '[POC SOCKET] '
            'RTC dispose failed → $e',
      );
    }

    _rtcHandler = null;

    removeRtcMessageListener();

    try {
      socket?.disconnect();
    } catch (_) {}

    try {
      socket?.dispose();
    } catch (_) {}

    socket = null;

    _connectedWatchers.clear();

    _currentWatcherId =
    null;

    _watcherSentFrom =
    null;

    _rtcSignalCallback =
    null;

    debugPrint(
      '[POC SOCKET] '
          'Socket service disposed',
    );
  }
}