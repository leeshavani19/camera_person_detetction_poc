import 'package:get_it/get_it.dart';
import 'package:injectable/injectable.dart';


import 'inject.config.dart';

final getIt = GetIt.instance;

@InjectableInit()
Future<void> configureMainDependencies({required String environment}) async => GetItInjectableX(getIt).init(environment: environment);

abstract class Env {
  //static const production = 'production';
  static const development = 'development';
  static const kodyinfotech = 'kodyinfotech';
  static const kodyrobots = 'kodyrobots';
  static const falcontechrobotics = 'falcontechrobotics';
  // static const List<String> environments = [Env.production, Env.development,Env.falcontechrobotics];
  static const List<String> environments = [ Env.development,Env.kodyinfotech,Env.kodyrobots,Env.falcontechrobotics];
}