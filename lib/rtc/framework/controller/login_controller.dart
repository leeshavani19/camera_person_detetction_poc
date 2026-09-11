import 'dart:convert';

import 'package:camera_person_detetction_poc/rtc/poc_session.dart';
import 'package:camera_person_detetction_poc/rtc/poc_socket_service.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:injectable/injectable.dart';

import '../dependency_injection/inject.dart';



final loginController = ChangeNotifierProvider<LoginController>(
      (ref) => getIt<LoginController>(),
);

@injectable
class LoginController extends ChangeNotifier {
  bool loading = false;

  String status = 'Not logged in';

  static const String baseUrl =
      'https://service.odigo-v3.kodyinfotech.com/odigo/v3';

  static const String contactNumber =
      '123456789_com.kodytechnolabs.odigo_1234567891';

  static const String password =
      '1234567891_com.kodytechnolabs.odigo_com.kody.odigo_display';

  static const String userType = 'MAIN_PANEL';

  Future<void> login() async {
    loading = true;
    status = 'Logging in...';
    notifyListeners();

    try {
      final dio = Dio(
        BaseOptions(
          baseUrl: baseUrl,
          connectTimeout: const Duration(seconds: 15),
          receiveTimeout: const Duration(seconds: 15),
          headers: {
            'Accept': '*/*',
            'Content-Type': 'application/json',
            'Accept-Language': 'en',
            'timeZone': 'Asia/Kolkata',
          },
        ),
      );

      final response = await dio.post(
        '/login',
        data: {
          'contactNumber': contactNumber,
          'password': password,
          'userType': userType,
        },
      );

      debugPrint(
        '[POC LOGIN] Response → ${response.data}',
      );

      final data = response.data['data'];

      if (response.statusCode == 200 && data != null) {
        PocSession.entityUuid = data['entityUuid']?.toString();

        PocSession.entityType = data['entityType']?.toString();

        PocSession.mapsUuid = data['mapsUuid']?.toString();

        PocSession.token = data['access_token']?.toString();

        debugPrint(
          '[POC LOGIN] entityUuid=${PocSession.entityUuid}',
        );

        debugPrint(
          '[POC LOGIN] entityType=${PocSession.entityType}',
        );

        debugPrint(
          '[POC LOGIN] mapsUuid=${PocSession.mapsUuid}',
        );

        debugPrint(
          '[POC LOGIN] token received=${PocSession.token != null}',
        );

        if (PocSession.isLoggedIn) {
          status = 'Login successful. Connecting socket...';
          notifyListeners();

          PocSocketService.instance.connect();

          return;
        }
      }

      status = 'Login failed: ${response.data}';
      notifyListeners();
    } catch (e, stack) {
      debugPrint('[POC LOGIN] ERROR → $e');
      debugPrint('$stack');

      status = 'Login error: $e';
      notifyListeners();
    } finally {
      loading = false;
      notifyListeners();
    }
  }
}
