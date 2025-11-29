import 'dart:convert';
import '../models/admin/admin_models.dart';
import '../utils/logger.dart';
import 'local_storage_service.dart';

/// 权限管理服务
class PermissionService {
  static const String _tag = 'PermissionService';
  static const String _adminTokenKey = 'admin_token';
  static const String _adminRefreshTokenKey = 'admin_refresh_token';
  static const String _adminUserKey = 'admin_user';
  
  final LocalStorageService _localStorage;
  
  PermissionService({LocalStorageService? localStorage}) 
      : _localStorage = localStorage ?? LocalStorageService();

  /// 权限常量
  static const String SYSTEM_ADMIN = 'SYSTEM_ADMIN';
  static const String USER_MANAGEMENT = 'USER_MANAGEMENT';
  static const String MODEL_MANAGEMENT = 'MODEL_MANAGEMENT';
  static const String PRESET_MANAGEMENT = 'PRESET_MANAGEMENT';
  static const String TEMPLATE_MANAGEMENT = 'TEMPLATE_MANAGEMENT';
  static const String SYSTEM_CONFIG = 'SYSTEM_CONFIG';
  static const String SUBSCRIPTION_MANAGEMENT = 'SUBSCRIPTION_MANAGEMENT';
  static const String STATISTICS_VIEW = 'STATISTICS_VIEW';

  /// 功能权限映射
  static const Map<String, List<String>> _featurePermissions = {
    'dashboard': [STATISTICS_VIEW],
    'user_management': [USER_MANAGEMENT],
    'role_management': [USER_MANAGEMENT],
    'subscription_management': [SUBSCRIPTION_MANAGEMENT],
    'model_management': [MODEL_MANAGEMENT],
    'system_presets': [PRESET_MANAGEMENT],
    'public_templates': [TEMPLATE_MANAGEMENT],
    'system_config': [SYSTEM_CONFIG],
  };

  /// 获取当前管理员信息
  Future<AdminUser?> getCurrentAdmin() async {
    try {
      final adminDataJson = await _localStorage.getString(_adminUserKey);
      if (adminDataJson != null) {
        final adminData = Map<String, dynamic>.from(json.decode(adminDataJson));
        return AdminUser.fromJson(adminData);
      }
      return null;
    } catch (e) {
      AppLogger.e(_tag, '获取当前管理员信息失败', e);
      return null;
    }
  }

  /// 保存管理员信息
  Future<void> saveAdminInfo(AdminUser admin, String token, {String? refreshToken}) async {
    try {
      await _localStorage.setString(_adminUserKey, json.encode(admin.toJson()));
      await _localStorage.setString(_adminTokenKey, token);
      if (refreshToken != null) {
        await _localStorage.setString(_adminRefreshTokenKey, refreshToken);
      }
      AppLogger.info(_tag, '管理员信息保存成功: ${admin.username}');
    } catch (e) {
      AppLogger.e(_tag, '保存管理员信息失败', e);
      rethrow;
    }
  }

  /// 清除管理员信息
  Future<void> clearAdminInfo() async {
    try {
      await _localStorage.remove(_adminUserKey);
      await _localStorage.remove(_adminTokenKey);
      await _localStorage.remove(_adminRefreshTokenKey);
      AppLogger.info(_tag, '管理员信息清除成功');
    } catch (e) {
      AppLogger.e(_tag, '清除管理员信息失败', e);
    }
  }

  /// 获取管理员token
  Future<String?> getAdminToken() async {
    try {
      return await _localStorage.getString(_adminTokenKey);
    } catch (e) {
      AppLogger.e(_tag, '获取管理员token失败', e);
      return null;
    }
  }

  /// 获取管理员refreshToken
  Future<String?> getAdminRefreshToken() async {
    try {
      return await _localStorage.getString(_adminRefreshTokenKey);
    } catch (e) {
      AppLogger.e(_tag, '获取管理员refreshToken失败', e);
      return null;
    }
  }

  /// 更新管理员token（刷新后使用）
  Future<void> updateAdminTokens(String token, String refreshToken) async {
    try {
      await _localStorage.setString(_adminTokenKey, token);
      await _localStorage.setString(_adminRefreshTokenKey, refreshToken);
      AppLogger.info(_tag, '管理员token更新成功');
    } catch (e) {
      AppLogger.e(_tag, '更新管理员token失败', e);
      rethrow;
    }
  }

  /// 检查是否是管理员
  Future<bool> isAdmin() async {
    final admin = await getCurrentAdmin();
    return admin != null;
  }

  /// 检查是否是超级管理员
  Future<bool> isSuperAdmin() async {
    final admin = await getCurrentAdmin();
    return admin?.roles?.any((role) => 
      role.contains('SUPER_ADMIN') || role == 'SUPER_ADMIN'
    ) ?? false;
  }

  /// 检查特定权限
  Future<bool> hasPermission(String permission) async {
    final admin = await getCurrentAdmin();
    if (admin == null) return false;

    // 超级管理员拥有所有权限
    if (await isSuperAdmin()) return true;

    // 检查用户角色中是否包含指定权限
    final userRoles = admin.roles ?? [];
    
    // 基于角色的权限映射
    if (userRoles.contains('ADMIN') || userRoles.contains('SUPER_ADMIN')) {
      // 管理员和超级管理员拥有所有权限
      return true;
    }
    
    // 具体权限映射可以在这里扩展
    // 目前简化处理：所有登录的管理员都有基本权限
    return userRoles.isNotEmpty;
  }

  /// 检查多个权限（需要全部拥有）
  Future<bool> hasAllPermissions(List<String> permissions) async {
    for (final permission in permissions) {
      if (!await hasPermission(permission)) {
        return false;
      }
    }
    return true;
  }

  /// 检查多个权限（拥有其中任一即可）
  Future<bool> hasAnyPermission(List<String> permissions) async {
    for (final permission in permissions) {
      if (await hasPermission(permission)) {
        return true;
      }
    }
    return false;
  }

  /// 检查功能访问权限
  Future<bool> canAccessFeature(String feature) async {
    final requiredPermissions = _featurePermissions[feature];
    if (requiredPermissions == null || requiredPermissions.isEmpty) {
      return await isAdmin(); // 默认需要管理员权限
    }

    return await hasAnyPermission(requiredPermissions);
  }

  /// 检查是否可以管理用户
  Future<bool> canManageUsers() async {
    return await hasPermission(USER_MANAGEMENT);
  }

  /// 检查是否可以管理模型
  Future<bool> canManageModels() async {
    return await hasPermission(MODEL_MANAGEMENT);
  }

  /// 检查是否可以管理预设
  Future<bool> canManagePresets() async {
    return await hasPermission(PRESET_MANAGEMENT);
  }

  /// 检查是否可以管理模板
  Future<bool> canManageTemplates() async {
    return await hasPermission(TEMPLATE_MANAGEMENT);
  }

  /// 检查是否可以管理系统配置
  Future<bool> canManageSystemConfig() async {
    return await hasPermission(SYSTEM_CONFIG);
  }

  /// 检查是否可以管理订阅
  Future<bool> canManageSubscriptions() async {
    return await hasPermission(SUBSCRIPTION_MANAGEMENT);
  }

  /// 检查是否可以查看统计数据
  Future<bool> canViewStatistics() async {
    return await hasPermission(STATISTICS_VIEW);
  }

  /// 验证操作权限（用于敏感操作）
  Future<bool> validateOperation(String operation, {Map<String, dynamic>? context}) async {
    if (!await isAdmin()) {
      AppLogger.w(_tag, '非管理员尝试执行操作: $operation');
      return false;
    }

    switch (operation) {
      case 'delete_user':
        return await canManageUsers();
      case 'delete_model':
        return await canManageModels();
      case 'create_system_preset':
      case 'update_system_preset':
      case 'delete_system_preset':
        return await canManagePresets();
      case 'review_template':
      case 'publish_template':
      case 'verify_template':
      case 'delete_template':
        return await canManageTemplates();
      case 'update_system_config':
        return await canManageSystemConfig();
      case 'create_subscription_plan':
      case 'update_subscription_plan':
        return await canManageSubscriptions();
      default:
        AppLogger.w(_tag, '未知操作权限检查: $operation');
        return await isSuperAdmin(); // 未知操作需要超级管理员权限
    }
  }

  /// 获取用户可访问的管理功能列表
  Future<List<String>> getAccessibleFeatures() async {
    final accessibleFeatures = <String>[];
    
    for (final feature in _featurePermissions.keys) {
      if (await canAccessFeature(feature)) {
        accessibleFeatures.add(feature);
      }
    }
    
    return accessibleFeatures;
  }

  /// 权限检查装饰器（用于业务方法）
  Future<T?> withPermissionCheck<T>(
    String permission,
    Future<T> Function() operation, {
    String? operationName,
  }) async {
    if (!await hasPermission(permission)) {
      final admin = await getCurrentAdmin();
      AppLogger.w(_tag, '权限不足: ${admin?.username ?? 'unknown'} 尝试执行 ${operationName ?? 'unknown operation'}，需要权限: $permission');
      throw PermissionDeniedException('权限不足，需要 $permission 权限');
    }

    try {
      return await operation();
    } catch (e) {
      AppLogger.e(_tag, '执行操作失败: ${operationName ?? 'unknown'}', e);
      rethrow;
    }
  }

  /// 多权限检查装饰器
  Future<T?> withMultiPermissionCheck<T>(
    List<String> permissions,
    Future<T> Function() operation, {
    String? operationName,
    bool requireAll = false,
  }) async {
    final hasAccess = requireAll 
        ? await hasAllPermissions(permissions)
        : await hasAnyPermission(permissions);

    if (!hasAccess) {
      final admin = await getCurrentAdmin();
      final permissionStr = requireAll ? permissions.join(' AND ') : permissions.join(' OR ');
      AppLogger.w(_tag, '权限不足: ${admin?.username ?? 'unknown'} 尝试执行 ${operationName ?? 'unknown operation'}，需要权限: $permissionStr');
      throw PermissionDeniedException('权限不足，需要 $permissionStr 权限');
    }

    try {
      return await operation();
    } catch (e) {
      AppLogger.e(_tag, '执行操作失败: ${operationName ?? 'unknown'}', e);
      rethrow;
    }
  }

  /// 管理员会话验证
  Future<bool> validateAdminSession() async {
    try {
      final token = await getAdminToken();
      final admin = await getCurrentAdmin();
      
      if (token == null || admin == null) {
        return false;
      }

      // TODO: 可以添加token过期检查和服务器端验证
      return true;
    } catch (e) {
      AppLogger.e(_tag, '管理员会话验证失败', e);
      return false;
    }
  }

  /// 刷新管理员信息
  Future<void> refreshAdminInfo(AdminUser updatedAdmin) async {
    try {
      final token = await getAdminToken();
      if (token != null) {
        await saveAdminInfo(updatedAdmin, token);
      }
    } catch (e) {
      AppLogger.error(_tag, '刷新管理员信息失败', e);
    }
  }
}

/// 权限拒绝异常
class PermissionDeniedException implements Exception {
  final String message;
  
  const PermissionDeniedException(this.message);
  
  @override
  String toString() => 'PermissionDeniedException: $message';
}