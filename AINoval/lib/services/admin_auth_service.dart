import 'dart:convert';
import '../config/app_config.dart';
import '../utils/logger.dart';
import 'permission_service.dart';
import 'api_service/repositories/impl/admin_repository_impl.dart';

/// 管理后台认证服务
/// 处理管理员 token 刷新和会话管理
class AdminAuthService {
  static const String _tag = 'AdminAuthService';
  static AdminAuthService? _instance;
  
  final PermissionService _permissionService;
  final AdminRepositoryImpl _adminRepository;
  
  bool _isRefreshing = false;
  
  AdminAuthService._({
    PermissionService? permissionService,
    AdminRepositoryImpl? adminRepository,
  }) : _permissionService = permissionService ?? PermissionService(),
       _adminRepository = adminRepository ?? AdminRepositoryImpl();
  
  static AdminAuthService get instance {
    _instance ??= AdminAuthService._();
    return _instance!;
  }
  
  /// 尝试刷新 token
  /// 返回 true 表示刷新成功，false 表示需要重新登录
  Future<bool> refreshToken() async {
    if (_isRefreshing) {
      AppLogger.d(_tag, 'Token刷新正在进行中，跳过重复请求');
      return false;
    }
    
    _isRefreshing = true;
    try {
      final refreshToken = await _permissionService.getAdminRefreshToken();
      if (refreshToken == null || refreshToken.isEmpty) {
        AppLogger.w(_tag, '没有可用的refreshToken，需要重新登录');
        return false;
      }
      
      final result = await _adminRepository.refreshAdminToken(refreshToken);
      if (result != null) {
        final newToken = result['token']!;
        final newRefreshToken = result['refreshToken']!;
        
        // 更新本地存储
        await _permissionService.updateAdminTokens(newToken, newRefreshToken);
        
        // 更新全局配置
        AppConfig.setAuthToken(newToken);
        
        AppLogger.info(_tag, '管理员token刷新成功');
        return true;
      } else {
        AppLogger.w(_tag, 'Token刷新失败，需要重新登录');
        return false;
      }
    } catch (e) {
      AppLogger.e(_tag, 'Token刷新异常', e);
      return false;
    } finally {
      _isRefreshing = false;
    }
  }
  
  /// 检查 token 是否即将过期（提前刷新）
  Future<bool> ensureValidToken() async {
    final token = await _permissionService.getAdminToken();
    if (token == null || token.isEmpty) {
      return false;
    }
    
    // 解析 JWT 检查过期时间
    try {
      final parts = token.split('.');
      if (parts.length != 3) {
        return false;
      }
      
      final payload = parts[1];
      final normalized = base64Url.normalize(payload);
      final decoded = utf8.decode(base64Url.decode(normalized));
      final data = json.decode(decoded) as Map<String, dynamic>;
      
      final exp = data['exp'] as int?;
      if (exp == null) {
        return true; // 无过期时间，假设有效
      }
      
      final expTime = DateTime.fromMillisecondsSinceEpoch(exp * 1000);
      final now = DateTime.now();
      final remaining = expTime.difference(now);
      
      // 如果剩余时间少于5分钟，尝试刷新
      if (remaining.inMinutes < 5) {
        AppLogger.d(_tag, 'Token即将过期（剩余${remaining.inMinutes}分钟），尝试刷新');
        return await refreshToken();
      }
      
      return true;
    } catch (e) {
      AppLogger.e(_tag, '解析token失败', e);
      // 解析失败时尝试刷新
      return await refreshToken();
    }
  }
  
  /// 处理 401 错误，尝试刷新 token
  Future<bool> handle401Error() async {
    AppLogger.w(_tag, '收到401错误，尝试刷新token');
    return await refreshToken();
  }
  
  /// 清除认证信息（登出）
  Future<void> logout() async {
    await _permissionService.clearAdminInfo();
    AppConfig.setAuthToken(null);
    AppConfig.setUserId(null);
    AppConfig.setUsername(null);
    AppLogger.info(_tag, '管理员已登出');
  }
}
