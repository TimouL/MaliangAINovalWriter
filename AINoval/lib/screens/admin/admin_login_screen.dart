import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:get_it/get_it.dart';

import '../../blocs/admin/admin_bloc.dart';
import '../../config/app_config.dart';
import '../../services/api_service/repositories/impl/admin_repository_impl.dart';
import 'admin_dashboard_screen.dart';
import '../../services/permission_service.dart';
import '../../models/admin/admin_models.dart' show AdminUser;
import '../../utils/logger.dart';

class AdminLoginScreen extends StatefulWidget {
  const AdminLoginScreen({super.key});

  @override
  State<AdminLoginScreen> createState() => _AdminLoginScreenState();
}

class _AdminLoginScreenState extends State<AdminLoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _isLoading = false;
  bool _obscurePassword = true;

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _handleLogin() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }

    setState(() {
      _isLoading = true;
    });

    try {
      final username = _usernameController.text.trim();
      final password = _passwordController.text;

      // 使用真实的管理员API登录
      final adminRepository = GetIt.instance<AdminRepositoryImpl>();
      final authResponse = await adminRepository.adminLogin(username, password);

      // 设置管理员认证信息
      AppConfig.setAuthToken(authResponse.token);
      AppConfig.setUserId(authResponse.userId);
      AppConfig.setUsername(authResponse.username);

      // 保存管理员信息到本地，用于权限校验和会话持久化
      try {
        final permissionService = PermissionService();
        await permissionService.saveAdminInfo(
          AdminUser(
            id: authResponse.userId,
            username: authResponse.username,
            email: '', // 登录响应暂未提供邮箱信息，可按需调整
            displayName: authResponse.displayName,
            accountStatus: 'ACTIVE',
            credits: 0,
            roles: authResponse.roles.isNotEmpty ? authResponse.roles : ['ADMIN'],
            createdAt: DateTime.now(),
            updatedAt: null,
          ),
          authResponse.token,
          refreshToken: authResponse.refreshToken,
        );
      } catch (e) {
        // 若保存失败，仅记录日志，不阻断登录流程
        AppLogger.w('AdminLogin', '保存管理员信息失败', e);
      }

      // 跳转到管理后台
      if (mounted) {
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(
            builder: (context) => BlocProvider(
              create: (context) => GetIt.instance<AdminBloc>(),
              child: const AdminDashboardScreen(),
            ),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        String errorMessage = '登录失败';
        if (e.toString().contains('用户名或密码错误')) {
          errorMessage = '用户名或密码错误，或无管理员权限';
        } else if (e.toString().contains('connection')) {
          errorMessage = '无法连接到服务器，请检查网络连接';
        } else {
          errorMessage = '登录失败: ${e.toString()}';
        }
        
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(errorMessage),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 3),
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [
              Theme.of(context).colorScheme.primary.withOpacity(0.1),
              Theme.of(context).colorScheme.secondary.withOpacity(0.1),
            ],
          ),
        ),
        child: Center(
          child: SingleChildScrollView(
            child: Padding(
              padding: const EdgeInsets.all(32.0),
              child: Card(
                elevation: 8,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Container(
                  width: 400,
                  padding: const EdgeInsets.all(32.0),
                  child: Form(
                    key: _formKey,
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        // Logo和标题
                        Icon(
                          Icons.admin_panel_settings,
                          size: 64,
                          color: Theme.of(context).colorScheme.primary,
                        ),
                        const SizedBox(height: 16),
                        Text(
                          'AI小说助手',
                          style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          '管理后台',
                          style: Theme.of(context).textTheme.titleMedium?.copyWith(
                            color: Theme.of(context).colorScheme.primary,
                          ),
                        ),
                        const SizedBox(height: 32),

                        // 用户名输入框
                        TextFormField(
                          controller: _usernameController,
                          decoration: InputDecoration(
                            labelText: '用户名',
                            prefixIcon: const Icon(Icons.person),
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                          validator: (value) {
                            if (value == null || value.trim().isEmpty) {
                              return '请输入用户名';
                            }
                            return null;
                          },
                          enabled: !_isLoading,
                        ),
                        const SizedBox(height: 16),

                        // 密码输入框
                        TextFormField(
                          controller: _passwordController,
                          obscureText: _obscurePassword,
                          decoration: InputDecoration(
                            labelText: '密码',
                            prefixIcon: const Icon(Icons.lock),
                            suffixIcon: IconButton(
                              icon: Icon(
                                _obscurePassword ? Icons.visibility : Icons.visibility_off,
                              ),
                              onPressed: () {
                                setState(() {
                                  _obscurePassword = !_obscurePassword;
                                });
                              },
                            ),
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                          validator: (value) {
                            if (value == null || value.isEmpty) {
                              return '请输入密码';
                            }
                            return null;
                          },
                          enabled: !_isLoading,
                          onFieldSubmitted: (_) => _handleLogin(),
                        ),
                        const SizedBox(height: 24),

                        // 登录按钮
                        SizedBox(
                          width: double.infinity,
                          height: 48,
                          child: ElevatedButton(
                            onPressed: _isLoading ? null : _handleLogin,
                            style: ElevatedButton.styleFrom(
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                            ),
                            child: _isLoading
                                ? const SizedBox(
                                    width: 20,
                                    height: 20,
                                    child: CircularProgressIndicator(strokeWidth: 2),
                                  )
                                : const Text(
                                    '登录',
                                    style: TextStyle(fontSize: 16),
                                  ),
                          ),
                        ),
                        const SizedBox(height: 16),

                        // 提示信息
                        Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: Theme.of(context).colorScheme.primary.withOpacity(0.1),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Row(
                            children: [
                              Icon(
                                Icons.info_outline,
                                size: 16,
                                color: Theme.of(context).colorScheme.primary,
                              ),
                              const SizedBox(width: 8),
                              Expanded(
                                child: Text(
                                  '开发环境默认账号: admin / admin123',
                                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                                    color: Theme.of(context).colorScheme.primary,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
} 