import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';

import 'poc_socket_service.dart';

class PocRtcBroadcastHandler {
  PocRtcBroadcastHandler();

  // ============================================================
  // AI CHANNEL
  // ============================================================

  static const MethodChannel _aiChannel =
  MethodChannel(
    'camera_person_detetction_poc/ai',
  );

  // ============================================================
  // PERSISTENT CAMERA
  // ============================================================

  MediaStream? _localStream;

  Future<void>? _cameraInitFuture;

  bool _cameraStarted = false;

  // ============================================================
  // AI FRAME CAPTURE
  // ============================================================

  Timer? _aiCaptureTimer;

  bool _aiCaptureRunning = false;

  bool _aiFrameProcessing = false;

  // ============================================================
  // RTC SIGNALING
  // ============================================================

  bool _rtcStarted = false;

  String? _signalingCameraId;

  // ============================================================
  // PEER CONNECTIONS
  // ============================================================

  final Map<String, RTCPeerConnection>
  _peerConnections = {};

  // ============================================================
  // ICE
  // ============================================================

  final Map<String, List<RTCIceCandidate>>
  _localIceBuffer = {};

  final Map<String, List<RTCIceCandidate>>
  _remoteIceBuffer = {};

  final Map<String, bool>
  _remoteDescriptionSet = {};

  // ============================================================
  // WATCHER PROTECTION
  // ============================================================

  final Set<String> _pendingWatchers = {};

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
  // START CAMERA + AI
  //
  // Called when application/socket is ready.
  //
  // IMPORTANT:
  // This does NOT start RTC signaling.
  // It only opens the camera and starts AI.
  // ============================================================

  Future<void> startCameraAndAi() async {
    debugPrint(
      '[POC CAMERA] '
          'startCameraAndAi()',
    );

    await _ensureCameraRunning();

    debugPrint(
      '[POC CAMERA] '
          'Camera + AI are running',
    );
  }

  // ============================================================
  // START RTC FOR PANEL
  //
  // Called ONLY when panel sends OPEN_CAMERA.
  //
  // Camera is already running.
  // ============================================================

  void listenToSignalingServer() {
    PocSocketService.instance.onRtcMessage(_onSignal);
    debugPrint('[POC RTC] Signaling listener registered at startup');
  }

  Future<void> handleMessageForRTC({
    required String cameraId,
  }) async {
    _signalingCameraId =
        cameraId;

    if (_rtcStarted) {
      debugPrint(
        '[POC RTC] '
            'RTC already started '
            'cameraId=$cameraId',
      );
      return;
    }

    _rtcStarted = true;

    debugPrint(
      '[POC RTC] '
          'Starting RTC signaling '
          'cameraId=$cameraId',
    );

    // Listener is already up (registered at startup via listenToSignalingServer).
    // Just announce ourselves so the server knows our broadcaster session.
    debugPrint(
      '[POC RTC] '
          'Sending establish-rtc',
    );

    PocSocketService.instance.establishRtc(
      cameraId: cameraId,
    );
  }

  // ============================================================
  // RTC SIGNAL
  // ============================================================

  Future<void> _onSignal(
      dynamic data,
      ) async {
    if (data is! Map) {
      debugPrint(
        '[POC RTC] '
            'Invalid RTC signal → $data',
      );
      return;
    }

    final type =
    data['type']?.toString();

    debugPrint(
      '[POC RTC] '
          'SIGNAL → type=$type data=$data',
    );

    switch (type) {

    // ========================================================
    // ESTABLISH RTC
    // ========================================================

      case 'establish-rtc':
        debugPrint(
          '[POC RTC] '
              'ESTABLISH-RTC received',
        );

        // Extract cameraId from the incoming message so we have it even
        // when handleMessageForRTC has not been called yet (startup listener path).
        _signalingCameraId =
            data['cameraId']?.toString() ?? _signalingCameraId;

        PocSocketService.instance
            .registerBroadcaster();

        // Make absolutely sure camera still exists.
        await _ensureCameraRunning();

        // Inform panel that camera is ready.
        // sendCameraReady will buffer the message if _watcherSentFrom is not
        // set yet (addWatcher hasn't arrived) and flush it as soon as it does.
        PocSocketService.instance
            .sendCameraReady(
          cameraId:
          _signalingCameraId,
        );

        break;

    // ========================================================
    // WATCHER
    // ========================================================

      case 'watcher':
        await _handleWatcher(
          data,
        );
        break;

    // ========================================================
    // ANSWER
    // ========================================================

      case 'answer':
        await _handleAnswer(
          data,
        );
        break;

    // ========================================================
    // ICE
    // ========================================================

      case 'candidate':
        await _handleCandidate(
          data,
        );
        break;

    // ========================================================
    // WATCHER DISCONNECTED
    // ========================================================

      case 'watcher-left':
      case 'watcher_left':
      case 'unwatch':
      case 'unwatcher':
      case 'stop-watching':
      case 'stop_watching':
      case 'disconnectPeer':
      case 'disconnect-peer':
      case 'peer-disconnected':
      case 'peer_disconnected':
      case 'watcher-disposed':
        {
          final watcherId =
          (
              data['id'] ??
                  data['watcherId']
          )?.toString();

          if (watcherId != null &&
              watcherId.isNotEmpty) {
            await disconnectViewer(
              watcherId,
            );
          }

          break;
        }

    // ========================================================
    // REFRESH
    //
    // Restart signaling ONLY.
    // Do NOT stop camera.
    // Do NOT stop AI.
    // ========================================================

      case 'refresh':
      case 'reload-broadcast':
        debugPrint(
          '[POC RTC] '
              'RTC refresh requested',
        );

        await _disposeRtcConnectionsOnly();

        _rtcStarted = false;

        final cameraId =
            _signalingCameraId;

        if (cameraId != null &&
            cameraId.isNotEmpty) {
          await handleMessageForRTC(
            cameraId: cameraId,
          );
        }

        break;

    // ========================================================
    // BROADCASTER ACK
    // ========================================================

      case 'broadcaster':
        debugPrint(
          '[POC RTC] '
              'Broadcaster registered',
        );
        break;

    // ========================================================
    // UNKNOWN
    // ========================================================

      default:
        debugPrint(
          '[POC RTC] '
              'Unhandled RTC signal → $type',
        );
        break;
    }
  }

  // ============================================================
  // WATCHER
  // ============================================================

  Future<void> _handleWatcher(
      Map data,
      ) async {
    final watcherId =
    (
        data['id'] ??
            data['watcherId']
    )?.toString();

    if (watcherId == null ||
        watcherId.isEmpty) {
      debugPrint(
        '[POC RTC] '
            'Watcher ID missing',
      );
      return;
    }

    debugPrint(
      '[POC RTC] '
          'WATCHER SIGNAL → $watcherId',
    );

    if (_peerConnections.containsKey(
      watcherId,
    )) {
      debugPrint(
        '[POC RTC] '
            'Watcher already connected → '
            '$watcherId',
      );
      return;
    }

    if (_pendingWatchers.contains(
      watcherId,
    )) {
      debugPrint(
        '[POC RTC] '
            'Watcher offer already pending → '
            '$watcherId',
      );
      return;
    }

    _pendingWatchers.add(
      watcherId,
    );

    try {
      await _createOffer(
        watcherId,
      );
    } catch (e, stack) {
      debugPrint(
        '[POC RTC] '
            'Create offer failed → $e',
      );

      debugPrint(
        '$stack',
      );
    } finally {
      _pendingWatchers.remove(
        watcherId,
      );
    }
  }

  // ============================================================
  // CAMERA
  // ============================================================

  Future<void> _ensureCameraRunning() async {
    if (_localStream != null &&
        _cameraStarted) {
      debugPrint(
        '[POC CAMERA] '
            'Camera already running',
      );
      return;
    }

    final running =
        _cameraInitFuture;

    if (running != null) {
      debugPrint(
        '[POC CAMERA] '
            'Waiting for camera initialization',
      );

      await running;
      return;
    }

    _cameraInitFuture =
        _initializeCamera();

    try {
      await _cameraInitFuture;
    } finally {
      _cameraInitFuture = null;
    }
  }

  Future<void> _initializeCamera() async {
    debugPrint(
      '[POC CAMERA] '
          'OPENING CAMERA',
    );

    try {
      final stream =
      await navigator.mediaDevices
          .getUserMedia({
        'video': {
          'width': 640,
          'height': 480,
          'frameRate': 15,
          'facingMode': 'environment',
        },
        'audio': false,
      });

      _localStream =
          stream;

      _cameraStarted =
      true;

      debugPrint(
        '[POC CAMERA] '
            'CAMERA READY',
      );

      debugPrint(
        '[POC CAMERA] '
            'Stream ID → ${stream.id}',
      );

      final tracks =
      stream.getVideoTracks();

      debugPrint(
        '[POC CAMERA] '
            'Video tracks → ${tracks.length}',
      );

      for (final track in tracks) {
        debugPrint(
          '[POC CAMERA] '
              'Video track → ${track.id}',
        );
      }

      // ========================================================
      // START AI FROM SAME VIDEO TRACK
      // ========================================================

      if (tracks.isNotEmpty) {
        await _startAiFrameCapture(
          tracks.first,
        );
      }
    } catch (e, stack) {
      _localStream = null;
      _cameraStarted = false;

      debugPrint(
        '[POC CAMERA] '
            'Camera initialization failed → $e',
      );

      debugPrint(
        '$stack',
      );

      rethrow;
    }
  }

  // ============================================================
  // AI FRAME CAPTURE
  // ============================================================

  Future<void> _startAiFrameCapture(
      MediaStreamTrack videoTrack,
      ) async {
    if (_aiCaptureRunning) {
      debugPrint(
        '[POC AI] '
            'AI capture already running',
      );
      return;
    }

    _aiCaptureRunning =
    true;

    debugPrint(
      '[POC AI] '
          'Starting AI frame capture',
    );

    try {
      await _aiChannel.invokeMethod(
        'startAi',
      );
    } catch (e) {
      debugPrint(
        '[POC AI] '
            'startAi failed → $e',
      );
    }

    _aiCaptureTimer?.cancel();

    // ==========================================================
    // 2 AI FRAMES / SECOND
    //
    // Camera itself remains 15 FPS.
    // ==========================================================

    _aiCaptureTimer =
        Timer.periodic(
          const Duration(
            milliseconds: 500,
          ),
              (_) async {
            if (!_aiCaptureRunning) {
              return;
            }

            if (_aiFrameProcessing) {
              return;
            }

            _aiFrameProcessing =
            true;

            try {
              final ByteBuffer buffer =
              await videoTrack
                  .captureFrame();

              final Uint8List bytes =
              buffer.asUint8List();

              if (bytes.isEmpty) {
                return;
              }

              debugPrint(
                '[POC AI] '
                    'Captured frame → '
                    '${bytes.length} bytes',
              );

              await _aiChannel.invokeMethod(
                'processCameraFrame',
                {
                  'bytes': bytes,
                },
              );
            } catch (e) {
              debugPrint(
                '[POC AI] '
                    'Frame capture failed → $e',
              );
            } finally {
              _aiFrameProcessing =
              false;
            }
          },
        );

    debugPrint(
      '[POC AI] '
          'AI capture started',
    );
  }

  // ============================================================
  // STOP AI CAPTURE
  // ============================================================

  Future<void> _stopAiFrameCapture() async {
    if (!_aiCaptureRunning) {
      return;
    }

    _aiCaptureRunning =
    false;

    _aiCaptureTimer?.cancel();
    _aiCaptureTimer = null;

    _aiFrameProcessing =
    false;

    try {
      await _aiChannel.invokeMethod(
        'stopAi',
      );
    } catch (_) {}

    debugPrint(
      '[POC AI] '
          'AI capture stopped',
    );
  }

  // ============================================================
  // CREATE OFFER
  // ============================================================

  Future<void> _createOffer(
      String watcherId,
      ) async {
    debugPrint(
      '[POC RTC] '
          'Creating offer → watcher=$watcherId',
    );

    // Camera should already be running.
    // This is only a safety check.
    await _ensureCameraRunning();

    final stream =
        _localStream;

    if (stream == null) {
      throw StateError(
        'Local WebRTC stream is null',
      );
    }

    final pc =
    await createPeerConnection(
      rtcConfig,
    );

    _peerConnections[
    watcherId] =
        pc;

    _remoteDescriptionSet[
    watcherId] =
    false;

    _localIceBuffer[
    watcherId] =
    <RTCIceCandidate>[];

    _remoteIceBuffer[
    watcherId] =
    <RTCIceCandidate>[];

    // ==========================================================
    // ADD EXISTING CAMERA TRACK
    // ==========================================================

    final tracks =
    stream.getTracks();

    debugPrint(
      '[POC RTC] '
          'Adding ${tracks.length} track(s)',
    );

    for (final track in tracks) {
      await pc.addTrack(
        track,
        stream,
      );

      debugPrint(
        '[POC RTC] '
            'Track added → '
            '${track.kind} ${track.id}',
      );
    }

    // ==========================================================
    // LOCAL ICE
    // ==========================================================

    pc.onIceCandidate =
        (RTCIceCandidate candidate) {
      if (candidate.candidate ==
          null) {
        return;
      }

      final answered =
          _remoteDescriptionSet[
          watcherId] ??
              false;

      if (!answered) {
        _localIceBuffer
            .putIfAbsent(
          watcherId,
              () => [],
        )
            .add(candidate);

        debugPrint(
          '[POC RTC] '
              'Local ICE buffered → '
              '$watcherId',
        );

        return;
      }

      _sendCandidate(
        watcherId,
        candidate,
      );
    };

    // ==========================================================
    // CONNECTION STATE
    // ==========================================================

    pc.onConnectionState =
        (
        RTCPeerConnectionState state,
        ) {
      debugPrint(
        '[POC RTC] '
            'PC STATE [$watcherId] → $state',
      );
    };

    // ==========================================================
    // ICE STATE
    // ==========================================================

    pc.onIceConnectionState =
        (
        RTCIceConnectionState state,
        ) {
      debugPrint(
        '[POC RTC] '
            'ICE STATE [$watcherId] → $state',
      );
    };

    // ==========================================================
    // ICE GATHERING
    // ==========================================================

    pc.onIceGatheringState =
        (
        RTCIceGatheringState state,
        ) {
      debugPrint(
        '[POC RTC] '
            'ICE GATHERING [$watcherId] → $state',
      );
    };

    // ==========================================================
    // OFFER
    // ==========================================================

    final offer =
    await pc.createOffer({
      'offerToReceiveAudio': false,
      'offerToReceiveVideo': false,
    });

    await pc.setLocalDescription(
      offer,
    );

    debugPrint(
      '[POC RTC] '
          'OFFER CREATED → watcher=$watcherId',
    );

    debugPrint(
      '[POC RTC] '
          'SDP length=${offer.sdp?.length ?? 0}',
    );

    PocSocketService.instance
        .sendOffer(
      watcherId: watcherId,
      sdp: offer.sdp!,
    );

    debugPrint(
      '[POC RTC] '
          'OFFER SENT → watcher=$watcherId',
    );
  }

  // ============================================================
  // ANSWER
  // ============================================================

  Future<void> _handleAnswer(
      Map data,
      ) async {
    final watcherId =
    (
        data['id'] ??
            data['watcherId']
    )?.toString();

    if (watcherId == null ||
        watcherId.isEmpty) {
      debugPrint(
        '[POC RTC] '
            'Answer missing watcher ID',
      );
      return;
    }

    final pc =
    _peerConnections[
    watcherId];

    if (pc == null) {
      debugPrint(
        '[POC RTC] '
            'Answer received but PC does not exist → '
            '$watcherId',
      );
      return;
    }

    final sdp =
    _extractSdp(
      data,
    );

    if (sdp == null ||
        sdp.trim().isEmpty) {
      debugPrint(
        '[POC RTC] '
            'Answer has no SDP',
      );
      return;
    }

    await pc.setRemoteDescription(
      RTCSessionDescription(
        sdp,
        'answer',
      ),
    );

    _remoteDescriptionSet[
    watcherId] =
    true;

    debugPrint(
      '[POC RTC] '
          'ANSWER SET → watcher=$watcherId',
    );

    // ==========================================================
    // SEND LOCAL ICE
    // ==========================================================

    final localCandidates =
    _localIceBuffer
        .remove(
      watcherId,
    );

    if (localCandidates != null) {
      debugPrint(
        '[POC RTC] '
            'Flushing '
            '${localCandidates.length} '
            'local ICE',
      );

      for (final candidate
      in localCandidates) {
        _sendCandidate(
          watcherId,
          candidate,
        );
      }
    }

    // ==========================================================
    // ADD REMOTE ICE
    // ==========================================================

    final remoteCandidates =
    _remoteIceBuffer
        .remove(
      watcherId,
    );

    if (remoteCandidates != null) {
      debugPrint(
        '[POC RTC] '
            'Adding '
            '${remoteCandidates.length} '
            'remote ICE',
      );

      for (final candidate
      in remoteCandidates) {
        try {
          await pc.addCandidate(
            candidate,
          );
        } catch (e) {
          debugPrint(
            '[POC RTC] '
                'Buffered ICE failed → $e',
          );
        }
      }
    }
  }

  // ============================================================
  // SDP
  // ============================================================

  String? _extractSdp(
      Map data,
      ) {
    final data1 =
    data['data'];

    if (data1 is Map) {
      final direct =
      data1['sdp'];

      if (direct != null &&
          direct.toString().isNotEmpty) {
        return direct.toString();
      }

      final nested =
      data1['data'];

      if (nested is Map) {
        final nestedSdp =
        nested['sdp'];

        if (nestedSdp != null &&
            nestedSdp
                .toString()
                .isNotEmpty) {
          return nestedSdp.toString();
        }
      }
    }

    final rootSdp =
    data['sdp'];

    if (rootSdp != null &&
        rootSdp.toString().isNotEmpty) {
      return rootSdp.toString();
    }

    return null;
  }

  // ============================================================
  // REMOTE ICE
  // ============================================================

  Future<void> _handleCandidate(
      Map data,
      ) async {
    final watcherId =
    (
        data['watcherId'] ??
            data['id']
    )?.toString();

    if (watcherId == null ||
        watcherId.isEmpty) {
      return;
    }

    final data1 =
    data['data'];

    Map? candidateData;

    if (data1 is Map &&
        data1['candidate'] != null) {
      candidateData =
          data1;
    } else if (
    data1 is Map &&
        data1['data'] is Map
    ) {
      final nested =
      data1['data'];

      if (nested['candidate'] !=
          null) {
        candidateData =
            nested;
      }
    }

    if (candidateData == null) {
      debugPrint(
        '[POC RTC] '
            'Invalid ICE candidate → $data',
      );
      return;
    }

    final candidate =
    RTCIceCandidate(
      candidateData['candidate']
          ?.toString(),
      candidateData['sdpMid']
          ?.toString(),
      _parseIceMLineIndex(
        candidateData[
        'sdpMLineIndex'],
      ),
    );

    final pc =
    _peerConnections[
    watcherId];

    if (pc == null) {
      _remoteIceBuffer
          .putIfAbsent(
        watcherId,
            () => [],
      )
          .add(candidate);

      debugPrint(
        '[POC RTC] '
            'PC not ready → remote ICE buffered',
      );

      return;
    }

    final answered =
        _remoteDescriptionSet[
        watcherId] ??
            false;

    if (!answered) {
      _remoteIceBuffer
          .putIfAbsent(
        watcherId,
            () => [],
      )
          .add(candidate);

      return;
    }

    try {
      await pc.addCandidate(
        candidate,
      );

      debugPrint(
        '[POC RTC] '
            'Remote ICE added → $watcherId',
      );
    } catch (e) {
      debugPrint(
        '[POC RTC] '
            'Remote ICE failed → $e',
      );
    }
  }

  // ============================================================
  // SEND ICE
  // ============================================================

  void _sendCandidate(
      String watcherId,
      RTCIceCandidate candidate,
      ) {
    if (candidate.candidate ==
        null) {
      return;
    }

    PocSocketService.instance
        .sendCandidate(
      targetId: watcherId,
      candidate:
      candidate.candidate!,
      sdpMid:
      candidate.sdpMid ?? '',
      sdpMLineIndex:
      candidate.sdpMLineIndex ?? 0,
    );

    debugPrint(
      '[POC RTC] '
          'LOCAL ICE SENT → $watcherId',
    );
  }

  // ============================================================
  // PARSE ICE INDEX
  // ============================================================

  int _parseIceMLineIndex(
      dynamic value,
      ) {
    if (value is int) {
      return value;
    }

    if (value is num) {
      return value.toInt();
    }

    if (value is String) {
      return int.tryParse(
        value,
      ) ??
          0;
    }

    return 0;
  }

  // ============================================================
  // DISCONNECT ONE VIEWER
  //
  // IMPORTANT:
  // Camera remains alive.
  // AI remains alive.
  // ============================================================

  Future<void> disconnectViewer(
      String watcherId,
      ) async {
    if (watcherId.trim().isEmpty) {
      return;
    }

    debugPrint(
      '[POC RTC] Closing viewer → $watcherId',
    );

    _pendingWatchers.remove(watcherId);

    final pc = _peerConnections.remove(watcherId);

    _localIceBuffer.remove(watcherId);
    _remoteIceBuffer.remove(watcherId);
    _remoteDescriptionSet.remove(watcherId);

    if (pc != null) {
      try {
        await pc.close();
      } catch (e) {
        debugPrint(
          '[POC RTC] PC close failed → $e',
        );
      }
    }

    debugPrint(
      '[POC RTC] Viewer disconnected → $watcherId',
    );

    debugPrint(
      '[POC RTC] Remaining viewers → '
          '${_peerConnections.length}',
    );

    // ==========================================================
    // CONNECTION RESET
    // ==========================================================

    if (_peerConnections.isEmpty) {
      debugPrint(
        '[POC RTC] No viewers remain',
      );

      // Reset so the next viewer gets a fresh signaling session.
      _rtcStarted = false;

      // Keep the 'message' listener ACTIVE — the next viewer's establish-rtc
      // must be received immediately without waiting for OPEN_CAMERA.
      // (Do NOT call removeRtcMessageListener here.)

      // Tell the RTC server to dispose the previous broadcaster session.
      // The listener re-registers the broadcaster on the next establish-rtc.
      PocSocketService.instance
          .disposeRTCConnection();

      debugPrint(
        '[POC RTC] RTC signaling reset — listener kept active',
      );

      // Camera must remain alive.
      debugPrint(
        '[POC RTC] Camera remains running',
      );

      // AI must remain alive.
      debugPrint(
        '[POC RTC] AI remains running',
      );

      return;
    }

    // At least one other viewer is still connected.
    debugPrint(
      '[POC RTC] Other viewer(s) still connected',
    );
  }

  // ============================================================
  // DISPOSE RTC CONNECTIONS ONLY
  // ============================================================

  Future<void> _disposeRtcConnectionsOnly() async {
    final connections =
    List<RTCPeerConnection>.from(
      _peerConnections.values,
    );

    _peerConnections.clear();

    for (final pc
    in connections) {
      try {
        await pc.close();
      } catch (_) {}
    }

    _localIceBuffer.clear();
    _remoteIceBuffer.clear();
    _remoteDescriptionSet.clear();
    _pendingWatchers.clear();

    debugPrint(
      '[POC RTC] '
          'RTC peer connections disposed',
    );
  }

  // ============================================================
  // STOP CAMERA
  //
  // ONLY application shutdown should call this.
  // ============================================================

  Future<void> _stopCamera() async {
    debugPrint(
      '[POC CAMERA] '
          'Stopping persistent camera',
    );

    await _stopAiFrameCapture();

    final stream =
        _localStream;

    if (stream != null) {
      for (final track
      in stream.getTracks()) {
        try {
          track.stop();
        } catch (_) {}
      }

      try {
        await stream.dispose();
      } catch (_) {}
    }

    _localStream = null;
    _cameraStarted = false;

    debugPrint(
      '[POC CAMERA] '
          'CAMERA STOPPED',
    );
  }

  // ============================================================
  // FULL DISPOSE
  //
  // Application shutdown.
  // ============================================================

  Future<void> disposeAsync() async {
    debugPrint(
      '[POC RTC] '
          'Full dispose',
    );

    await _disposeRtcConnectionsOnly();

    await _stopCamera();

    _rtcStarted = false;
    _signalingCameraId = null;

    _cameraInitFuture = null;

    debugPrint(
      '[POC RTC] '
          'Full dispose complete',
    );
  }

  // ============================================================
  // DISPOSE
  // ============================================================

  void dispose() {
    unawaited(
      disposeAsync(),
    );
  }
}