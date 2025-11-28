import 'dart:convert';
import 'package:http/http.dart' as http;
import '../../../config/app_config.dart';

/// 配置向导 API 仓库
class SetupRepository {
  final String baseUrl = AppConfig.apiBaseUrl;

  /// 获取系统状态
  Future<SystemStatusResponse> getSystemStatus() async {
    final response = await http.get(
      Uri.parse('$baseUrl/api/system/status'),
      headers: {'Content-Type': 'application/json'},
    );

    if (response.statusCode == 200) {
      return SystemStatusResponse.fromJson(jsonDecode(response.body));
    } else {
      throw Exception('获取系统状态失败: ${response.statusCode}');
    }
  }

  /// 获取配置状态
  Future<SetupStatusResponse> getSetupStatus() async {
    final response = await http.get(
      Uri.parse('$baseUrl/api/setup/status'),
      headers: {'Content-Type': 'application/json'},
    );

    if (response.statusCode == 200) {
      return SetupStatusResponse.fromJson(jsonDecode(response.body));
    } else {
      throw Exception('获取配置状态失败: ${response.statusCode}');
    }
  }

  /// 测试 MongoDB 连接
  Future<MongoTestResponse> testMongoDB(String uri) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/setup/test-mongodb'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'uri': uri}),
    );

    if (response.statusCode == 200) {
      return MongoTestResponse.fromJson(jsonDecode(response.body));
    } else {
      throw Exception('测试 MongoDB 连接失败: ${response.statusCode}');
    }
  }

  /// 测试存储连接
  Future<TestResponse> testStorage(StorageConfig config) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/setup/test-storage'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode(config.toJson()),
    );

    if (response.statusCode == 200) {
      return TestResponse.fromJson(jsonDecode(response.body));
    } else {
      throw Exception('测试存储连接失败: ${response.statusCode}');
    }
  }

  /// 测试 Chroma 连接
  Future<TestResponse> testChroma(String url, String? authToken) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/setup/test-chroma'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'url': url, 'authToken': authToken ?? ''}),
    );

    if (response.statusCode == 200) {
      return TestResponse.fromJson(jsonDecode(response.body));
    } else {
      throw Exception('测试 Chroma 连接失败: ${response.statusCode}');
    }
  }

  /// 保存配置
  Future<bool> saveConfig(InfrastructureConfig config) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/setup/save-config'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode(config.toJson()),
    );

    if (response.statusCode == 200) {
      final data = jsonDecode(response.body);
      return data['success'] == true;
    } else {
      throw Exception('保存配置失败: ${response.statusCode}');
    }
  }

  /// 初始化管理员
  Future<AdminInitResponse> initAdmin(String username, String email, String password) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/setup/init-admin'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'username': username,
        'email': email,
        'password': password,
      }),
    );

    if (response.statusCode == 200) {
      return AdminInitResponse.fromJson(jsonDecode(response.body));
    } else {
      throw Exception('初始化管理员失败: ${response.statusCode}');
    }
  }

  /// 完成配置向导
  Future<bool> completeSetup() async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/setup/complete'),
      headers: {'Content-Type': 'application/json'},
    );

    if (response.statusCode == 200) {
      final data = jsonDecode(response.body);
      return data['success'] == true;
    } else {
      throw Exception('完成配置失败: ${response.statusCode}');
    }
  }
}

// DTO 类

class SystemStatusResponse {
  final bool operational;
  final bool restrictedMode;
  final bool setupCompleted;
  final bool mongoConnected;
  final bool storageConnected;
  final bool chromaConnected;
  final String? lastError;

  SystemStatusResponse({
    required this.operational,
    required this.restrictedMode,
    required this.setupCompleted,
    required this.mongoConnected,
    required this.storageConnected,
    required this.chromaConnected,
    this.lastError,
  });

  factory SystemStatusResponse.fromJson(Map<String, dynamic> json) {
    return SystemStatusResponse(
      operational: json['operational'] ?? false,
      restrictedMode: json['restrictedMode'] ?? true,
      setupCompleted: json['setupCompleted'] ?? false,
      mongoConnected: json['mongoConnected'] ?? false,
      storageConnected: json['storageConnected'] ?? false,
      chromaConnected: json['chromaConnected'] ?? false,
      lastError: json['lastError'],
    );
  }
}

class SetupStatusResponse {
  final bool configFileExists;
  final bool setupCompleted;
  final bool mongoConnected;
  final bool storageConfigured;
  final bool chromaConfigured;
  final bool adminExists;

  SetupStatusResponse({
    required this.configFileExists,
    required this.setupCompleted,
    required this.mongoConnected,
    required this.storageConfigured,
    required this.chromaConfigured,
    required this.adminExists,
  });

  factory SetupStatusResponse.fromJson(Map<String, dynamic> json) {
    return SetupStatusResponse(
      configFileExists: json['configFileExists'] ?? false,
      setupCompleted: json['setupCompleted'] ?? false,
      mongoConnected: json['mongoConnected'] ?? false,
      storageConfigured: json['storageConfigured'] ?? false,
      chromaConfigured: json['chromaConfigured'] ?? false,
      adminExists: json['adminExists'] ?? false,
    );
  }
}

class MongoTestResponse {
  final bool success;
  final String message;
  final String? databaseStatus;

  MongoTestResponse({
    required this.success,
    required this.message,
    this.databaseStatus,
  });

  factory MongoTestResponse.fromJson(Map<String, dynamic> json) {
    return MongoTestResponse(
      success: json['success'] ?? false,
      message: json['message'] ?? '',
      databaseStatus: json['databaseStatus'],
    );
  }
}

class TestResponse {
  final bool success;
  final String message;

  TestResponse({required this.success, required this.message});

  factory TestResponse.fromJson(Map<String, dynamic> json) {
    return TestResponse(
      success: json['success'] ?? false,
      message: json['message'] ?? '',
    );
  }
}

class StorageConfig {
  final String provider;
  final String? localPath;
  final String? ossEndpoint;
  final String? ossAccessKeyId;
  final String? ossAccessKeySecret;
  final String? ossBucketName;

  StorageConfig({
    required this.provider,
    this.localPath,
    this.ossEndpoint,
    this.ossAccessKeyId,
    this.ossAccessKeySecret,
    this.ossBucketName,
  });

  Map<String, dynamic> toJson() => {
    'provider': provider,
    'localPath': localPath,
    'ossEndpoint': ossEndpoint,
    'ossAccessKeyId': ossAccessKeyId,
    'ossAccessKeySecret': ossAccessKeySecret,
    'ossBucketName': ossBucketName,
  };
}

class InfrastructureConfig {
  final String mongoUri;
  final String storageProvider;
  final String? localStoragePath;
  final String? ossEndpoint;
  final String? ossAccessKeyId;
  final String? ossAccessKeySecret;
  final String? ossBucketName;
  final bool chromaEnabled;
  final String? chromaUrl;
  final String? chromaAuthToken;

  InfrastructureConfig({
    required this.mongoUri,
    required this.storageProvider,
    this.localStoragePath,
    this.ossEndpoint,
    this.ossAccessKeyId,
    this.ossAccessKeySecret,
    this.ossBucketName,
    required this.chromaEnabled,
    this.chromaUrl,
    this.chromaAuthToken,
  });

  Map<String, dynamic> toJson() => {
    'mongoUri': mongoUri,
    'storageProvider': storageProvider,
    'localStoragePath': localStoragePath,
    'ossEndpoint': ossEndpoint,
    'ossAccessKeyId': ossAccessKeyId,
    'ossAccessKeySecret': ossAccessKeySecret,
    'ossBucketName': ossBucketName,
    'chromaEnabled': chromaEnabled,
    'chromaUrl': chromaUrl,
    'chromaAuthToken': chromaAuthToken,
  };
}

class AdminInitResponse {
  final bool success;
  final String message;
  final String? userId;

  AdminInitResponse({
    required this.success,
    required this.message,
    this.userId,
  });

  factory AdminInitResponse.fromJson(Map<String, dynamic> json) {
    return AdminInitResponse(
      success: json['success'] ?? false,
      message: json['message'] ?? '',
      userId: json['userId'],
    );
  }
}
