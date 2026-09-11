import 'dart:convert';

import 'package:camera_person_detetction_poc/rtc/framework/controller/login_controller.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'poc_session.dart';
import 'poc_socket_service.dart';


class PocLoginScreen extends ConsumerStatefulWidget {
  const PocLoginScreen({super.key});

  @override
  ConsumerState<PocLoginScreen> createState() => _PocLoginScreenState();
}

class _PocLoginScreenState extends ConsumerState<PocLoginScreen> {



  @override
  Widget build(BuildContext context) {
    final loginwatch = ref.watch(loginController);

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Camera RTC POC',
        ),
      ),
      body: Center(
        child: SizedBox(
          width: 400,
          child: Padding(
            padding:
            const EdgeInsets.all(24),
            child: Column(
              mainAxisSize:
              MainAxisSize.min,
              children: [
                const Text(
                  'Credentials are configured '
                      'for the RTC POC.',
                  textAlign:
                  TextAlign.center,
                ),

                const SizedBox(
                  height: 24,
                ),

                SizedBox(
                  width: MediaQuery.sizeOf(context).height * 0.2,
                  child: ElevatedButton(
                    onPressed:
                    loginwatch.loading
                        ? null
                        : loginwatch.login,
                    child:
                    loginwatch.loading
                        ? const CircularProgressIndicator()
                        : const Text(
                      'LOGIN',
                    ),
                  ),
                ),

                const SizedBox(
                  height: 20,
                ),

                Text(
                  loginwatch.status,
                  textAlign:
                  TextAlign.center,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}