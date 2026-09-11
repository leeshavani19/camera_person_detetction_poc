class PocSession {
  static String? entityUuid;
  static String? entityType;
  static String? mapsUuid;
  static String? token;

  static bool get isLoggedIn =>
      token != null &&
          token!.isNotEmpty &&
          entityUuid != null &&
          entityUuid!.isNotEmpty;

  static void clear() {
    entityUuid = null;
    entityType = null;
    mapsUuid = null;
    token = null;
  }
}