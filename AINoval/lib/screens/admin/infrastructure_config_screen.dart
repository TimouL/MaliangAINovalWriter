import 'package:flutter/material.dart';
import '../../services/api_service/repositories/setup_repository.dart';
import '../../services/api_service/base/api_client.dart';
import '../../services/api_service/base/api_exception.dart';
import '../../services/admin_auth_service.dart';
import 'admin_login_screen.dart';

/// 基础设施配置管理页面
class InfrastructureConfigScreen extends StatefulWidget {
  const InfrastructureConfigScreen({super.key});

  @override
  State<InfrastructureConfigScreen> createState() => _InfrastructureConfigScreenState();
}

class _InfrastructureConfigScreenState extends State<InfrastructureConfigScreen> {
  final SetupRepository _repository = SetupRepository();
  final ApiClient _apiClient = ApiClient();
  bool _isLoading = true;
  String? _error;
  
  // 配置数据
  Map<String, dynamic> _config = {};
  
  // 编辑状态
  bool _isEditingStorage = false;
  bool _isEditingChroma = false;
  
  // Token 显示状态
  bool _showChromaToken = false;
  String _chromaTokenValue = '';
  
  // 表单控制器
  final _localPathController = TextEditingController();
  final _ossEndpointController = TextEditingController();
  final _ossAccessKeyIdController = TextEditingController();
  final _ossAccessKeySecretController = TextEditingController();
  final _ossBucketNameController = TextEditingController();
  final _chromaUrlController = TextEditingController();
  final _chromaTokenController = TextEditingController();
  
  String _storageProvider = 'local';
  bool _chromaEnabled = false;
  
  // 日志配置
  bool _isEditingLogging = false;
  String _rootLogLevel = 'INFO';
  String _appLogLevel = 'DEBUG';
  
  // MongoDB 连接池配置
  int _mongoPoolMaxSize = 100;
  int _mongoPoolMinSize = 10;
  int _mongoPoolMaxWaitTime = 30;
  int _mongoPoolMaxIdleTime = 60;
  
  // 任务系统配置
  String _taskTransport = 'local';
  int _taskLocalConcurrency = 2000;
  
  static const List<String> _logLevels = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'];

  @override
  void initState() {
    super.initState();
    _loadConfig();
  }

  @override
  void dispose() {
    _localPathController.dispose();
    _ossEndpointController.dispose();
    _ossAccessKeyIdController.dispose();
    _ossAccessKeySecretController.dispose();
    _ossBucketNameController.dispose();
    _chromaUrlController.dispose();
    _chromaTokenController.dispose();
    super.dispose();
  }

  Future<void> _loadConfig({bool isRetry = false}) async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      // 获取系统状态
      final status = await _repository.getSystemStatus();
      
      // 获取完整基础设施配置
      final configResponse = await _apiClient.get('/admin/config/infrastructure');
      final configData = configResponse is Map<String, dynamic> 
          ? (configResponse['data'] ?? configResponse) 
          : <String, dynamic>{};
      
      setState(() {
        _config = {
          'mongoConnected': status.mongoConnected,
          'storageConnected': status.storageConnected,
          'chromaConnected': status.chromaConnected,
          'setupCompleted': status.setupCompleted,
        };
        
        // 设置 Chroma 配置 - 支持扁平结构和嵌套结构
        final chromaConfig = configData['chroma'] as Map<String, dynamic>?;
        _chromaEnabled = chromaConfig?['enabled'] ?? configData['chromaEnabled'] ?? false;
        _chromaUrlController.text = chromaConfig?['url'] ?? configData['chromaUrl'] ?? '';
        _chromaTokenValue = chromaConfig?['authToken'] ?? configData['chromaAuthToken'] ?? '';
        
        // 设置 MongoDB 连接池配置
        _mongoPoolMaxSize = configData['mongoPoolMaxSize'] ?? 100;
        _mongoPoolMinSize = configData['mongoPoolMinSize'] ?? 10;
        _mongoPoolMaxWaitTime = configData['mongoPoolMaxWaitTime'] ?? 30;
        _mongoPoolMaxIdleTime = configData['mongoPoolMaxIdleTime'] ?? 60;
        
        // 设置任务系统配置
        _taskTransport = configData['taskTransport'] ?? 'local';
        _taskLocalConcurrency = configData['taskLocalConcurrency'] ?? 2000;
        
        _isLoading = false;
      });
    } catch (e) {
      // 处理 401 错误
      if (!isRetry && _is401Error(e)) {
        final refreshed = await AdminAuthService.instance.handle401Error();
        if (refreshed) {
          return _loadConfig(isRetry: true);
        } else {
          _redirectToLogin();
          return;
        }
      }
      setState(() {
        _error = e.toString();
        _isLoading = false;
      });
    }
  }

  bool _is401Error(dynamic e) {
    if (e is ApiException && e.statusCode == 401) return true;
    final msg = e.toString().toLowerCase();
    return msg.contains('401') || msg.contains('登录已过期') || msg.contains('token_expired');
  }

  void _redirectToLogin() {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('登录已过期，请重新登录'), backgroundColor: Colors.orange),
    );
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => const AdminLoginScreen()),
      (route) => false,
    );
  }

  /// 脱敏显示 Token
  String _maskToken(String token) {
    if (token.isEmpty) return '未配置';
    if (token.length <= 8) return '****';
    return '${token.substring(0, 4)}${'*' * (token.length - 8).clamp(4, 16)}${token.substring(token.length - 4)}';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('基础设施配置'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loadConfig,
            tooltip: '刷新',
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Text('加载失败: $_error'))
              : _buildContent(),
    );
  }

  Widget _buildContent() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildMongoSection(),
          const SizedBox(height: 24),
          _buildTaskSection(),
          const SizedBox(height: 24),
          _buildStorageSection(),
          const SizedBox(height: 24),
          _buildChromaSection(),
          const SizedBox(height: 24),
          _buildLoggingSection(),
        ],
      ),
    );
  }

  Widget _buildMongoSection() {
    final connected = _config['mongoConnected'] ?? false;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  connected ? Icons.check_circle : Icons.error,
                  color: connected ? Colors.green : Colors.red,
                ),
                const SizedBox(width: 8),
                const Text(
                  'MongoDB 数据库',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                ),
                const SizedBox(width: 8),
                Chip(
                  label: const Text('需重启生效'),
                  backgroundColor: Colors.orange.shade100,
                  labelStyle: const TextStyle(fontSize: 10),
                ),
              ],
            ),
            const Divider(),
            ListTile(
              leading: const Icon(Icons.storage),
              title: const Text('连接状态'),
              subtitle: Text(connected ? '已连接' : '未连接'),
              trailing: Chip(
                label: Text(connected ? '正常' : '异常'),
                backgroundColor: connected ? Colors.green.shade100 : Colors.red.shade100,
              ),
            ),
            // 连接池配置
            ExpansionTile(
              leading: const Icon(Icons.pool),
              title: const Text('连接池配置'),
              subtitle: Text('最大 $_mongoPoolMaxSize / 最小 $_mongoPoolMinSize'),
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  child: Column(
                    children: [
                      _buildPoolConfigRow('最大连接数', '$_mongoPoolMaxSize', 
                        '同时允许的最大数据库连接数'),
                      _buildPoolConfigRow('最小连接数', '$_mongoPoolMinSize', 
                        '连接池维护的最小空闲连接数'),
                      _buildPoolConfigRow('等待超时', '$_mongoPoolMaxWaitTime 秒', 
                        '获取连接的最大等待时间'),
                      _buildPoolConfigRow('空闲超时', '$_mongoPoolMaxIdleTime 秒', 
                        '空闲连接自动关闭时间'),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              '提示：连接池配置可通过环境变量修改，如 SPRING_DATA_MONGODB_POOL_MAX_SIZE',
              style: TextStyle(color: Colors.grey.shade600, fontSize: 12),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPoolConfigRow(String label, String value, String tooltip) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Row(
            children: [
              Text(label, style: const TextStyle(fontSize: 14)),
              const SizedBox(width: 4),
              Tooltip(
                message: tooltip,
                child: Icon(Icons.info_outline, size: 16, color: Colors.grey.shade500),
              ),
            ],
          ),
          Text(value, style: const TextStyle(fontWeight: FontWeight.bold)),
        ],
      ),
    );
  }

  Widget _buildTaskSection() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  Icons.settings_applications,
                  color: Colors.teal.shade700,
                ),
                const SizedBox(width: 8),
                const Text(
                  '任务系统',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                ),
                const SizedBox(width: 8),
                Chip(
                  label: const Text('需重启生效'),
                  backgroundColor: Colors.orange.shade100,
                  labelStyle: const TextStyle(fontSize: 10),
                ),
              ],
            ),
            const Divider(),
            ListTile(
              leading: const Icon(Icons.swap_horiz),
              title: const Text('传输模式'),
              subtitle: Text(_taskTransport == 'local' ? '本地模式' : 'RabbitMQ 分布式'),
              trailing: Chip(
                label: Text(_taskTransport == 'local' ? '单机' : '集群'),
                backgroundColor: _taskTransport == 'local' 
                    ? Colors.blue.shade100 
                    : Colors.purple.shade100,
              ),
            ),
            ListTile(
              leading: const Icon(Icons.speed),
              title: const Text('并发数'),
              subtitle: Text('最大 $_taskLocalConcurrency 个并发任务'),
              trailing: _getConcurrencyChip(),
            ),
            const SizedBox(height: 8),
            Text(
              '说明：任务系统用于处理拆书、知识提取等后台任务。\n'
              '并发数影响同时执行的任务数量，建议根据服务器配置调整。',
              style: TextStyle(color: Colors.grey.shade600, fontSize: 12),
            ),
            const SizedBox(height: 8),
            Text(
              '提示：可通过环境变量 TASK_LOCAL_CONCURRENCY 修改并发数',
              style: TextStyle(color: Colors.grey.shade600, fontSize: 12),
            ),
          ],
        ),
      ),
    );
  }

  Widget _getConcurrencyChip() {
    String label;
    Color color;
    if (_taskLocalConcurrency <= 100) {
      label = '低';
      color = Colors.green.shade100;
    } else if (_taskLocalConcurrency <= 500) {
      label = '中';
      color = Colors.blue.shade100;
    } else if (_taskLocalConcurrency <= 1000) {
      label = '高';
      color = Colors.orange.shade100;
    } else {
      label = '极高';
      color = Colors.red.shade100;
    }
    return Chip(label: Text(label), backgroundColor: color);
  }

  Widget _buildStorageSection() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Icon(
                      Icons.folder,
                      color: Colors.blue.shade700,
                    ),
                    const SizedBox(width: 8),
                    const Text(
                      '文件存储',
                      style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                    ),
                  ],
                ),
                TextButton.icon(
                  onPressed: () => setState(() => _isEditingStorage = !_isEditingStorage),
                  icon: Icon(_isEditingStorage ? Icons.close : Icons.edit),
                  label: Text(_isEditingStorage ? '取消' : '编辑'),
                ),
              ],
            ),
            const Divider(),
            if (!_isEditingStorage) ...[
              ListTile(
                leading: const Icon(Icons.cloud),
                title: const Text('存储类型'),
                subtitle: Text(_storageProvider == 'local' ? '本地存储' : '阿里云 OSS'),
              ),
            ] else ...[
              SegmentedButton<String>(
                segments: const [
                  ButtonSegment(value: 'local', label: Text('本地存储')),
                  ButtonSegment(value: 'oss', label: Text('阿里云 OSS')),
                ],
                selected: {_storageProvider},
                onSelectionChanged: (value) {
                  setState(() => _storageProvider = value.first);
                },
              ),
              const SizedBox(height: 16),
              if (_storageProvider == 'local')
                TextField(
                  controller: _localPathController,
                  decoration: const InputDecoration(
                    labelText: '存储路径',
                    border: OutlineInputBorder(),
                  ),
                )
              else ...[
                TextField(
                  controller: _ossEndpointController,
                  decoration: const InputDecoration(
                    labelText: 'OSS Endpoint',
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _ossAccessKeyIdController,
                  decoration: const InputDecoration(
                    labelText: 'Access Key ID',
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _ossAccessKeySecretController,
                  obscureText: true,
                  decoration: const InputDecoration(
                    labelText: 'Access Key Secret (留空则不修改)',
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _ossBucketNameController,
                  decoration: const InputDecoration(
                    labelText: 'Bucket Name',
                    border: OutlineInputBorder(),
                  ),
                ),
              ],
              const SizedBox(height: 16),
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  OutlinedButton(
                    onPressed: _testStorageConnection,
                    child: const Text('测试连接'),
                  ),
                  const SizedBox(width: 8),
                  ElevatedButton(
                    onPressed: _saveStorageConfig,
                    child: const Text('保存'),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildChromaSection() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Icon(
                      Icons.memory,
                      color: Colors.purple.shade700,
                    ),
                    const SizedBox(width: 8),
                    const Text(
                      'Chroma 向量库',
                      style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(width: 8),
                    Chip(
                      label: const Text('支持热更新'),
                      backgroundColor: Colors.green.shade100,
                      labelStyle: const TextStyle(fontSize: 10),
                    ),
                  ],
                ),
                TextButton.icon(
                  onPressed: () => setState(() => _isEditingChroma = !_isEditingChroma),
                  icon: Icon(_isEditingChroma ? Icons.close : Icons.edit),
                  label: Text(_isEditingChroma ? '取消' : '编辑'),
                ),
              ],
            ),
            const Divider(),
            if (!_isEditingChroma) ...[
              SwitchListTile(
                title: const Text('启用状态'),
                subtitle: Text(_chromaEnabled ? '已启用' : '已禁用'),
                value: _chromaEnabled,
                onChanged: null, // 只读
              ),
              ListTile(
                leading: const Icon(Icons.link),
                title: const Text('服务地址'),
                subtitle: Text(_chromaUrlController.text.isEmpty ? '未配置' : _chromaUrlController.text),
              ),
              ListTile(
                leading: const Icon(Icons.key),
                title: const Text('认证 Token'),
                subtitle: Text(_showChromaToken ? _chromaTokenValue : _maskToken(_chromaTokenValue)),
                trailing: _chromaTokenValue.isNotEmpty
                    ? IconButton(
                        icon: Icon(_showChromaToken ? Icons.visibility_off : Icons.visibility),
                        onPressed: () => setState(() => _showChromaToken = !_showChromaToken),
                        tooltip: _showChromaToken ? '隐藏' : '显示',
                      )
                    : null,
              ),
            ] else ...[
              SwitchListTile(
                title: const Text('启用 Chroma'),
                subtitle: const Text('用于知识库检索和拆书功能'),
                value: _chromaEnabled,
                onChanged: (value) => setState(() => _chromaEnabled = value),
              ),
              if (_chromaEnabled) ...[
                const SizedBox(height: 12),
                TextField(
                  controller: _chromaUrlController,
                  decoration: const InputDecoration(
                    labelText: 'Chroma 服务地址',
                    hintText: 'http://localhost:8000',
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _chromaTokenController,
                  obscureText: true,
                  decoration: const InputDecoration(
                    labelText: '认证 Token (留空则不修改)',
                    border: OutlineInputBorder(),
                  ),
                ),
              ],
              const SizedBox(height: 16),
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  if (_chromaEnabled)
                    OutlinedButton(
                      onPressed: _testChromaConnection,
                      child: const Text('测试连接'),
                    ),
                  const SizedBox(width: 8),
                  ElevatedButton(
                    onPressed: _saveChromaConfig,
                    child: const Text('保存并应用'),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _testStorageConnection() async {
    try {
      final result = await _repository.testStorage(StorageConfig(
        provider: _storageProvider,
        localPath: _localPathController.text,
        ossEndpoint: _ossEndpointController.text,
        ossAccessKeyId: _ossAccessKeyIdController.text,
        ossAccessKeySecret: _ossAccessKeySecretController.text,
        ossBucketName: _ossBucketNameController.text,
      ));
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(result.message),
            backgroundColor: result.success ? Colors.green : Colors.red,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('测试失败: $e'), backgroundColor: Colors.red),
        );
      }
    }
  }

  Future<void> _testChromaConnection() async {
    try {
      final result = await _repository.testChroma(
        _chromaUrlController.text,
        _chromaTokenController.text.isEmpty ? null : _chromaTokenController.text,
      );
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(result.message),
            backgroundColor: result.success ? Colors.green : Colors.red,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('测试失败: $e'), backgroundColor: Colors.red),
        );
      }
    }
  }

  Future<void> _saveStorageConfig() async {
    // TODO: 调用后端 API 保存存储配置
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('存储配置已保存，需要重启应用后生效')),
    );
    setState(() => _isEditingStorage = false);
  }

  Future<void> _saveChromaConfig() async {
    // TODO: 调用后端 API 保存 Chroma 配置
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Chroma 配置已保存并应用')),
    );
    setState(() => _isEditingChroma = false);
  }

  Widget _buildLoggingSection() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Icon(
                      Icons.article_outlined,
                      color: Colors.orange.shade700,
                    ),
                    const SizedBox(width: 8),
                    const Text(
                      '日志配置',
                      style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(width: 8),
                    Chip(
                      label: const Text('需重启生效'),
                      backgroundColor: Colors.orange.shade100,
                      labelStyle: const TextStyle(fontSize: 10),
                    ),
                  ],
                ),
                TextButton.icon(
                  onPressed: () => setState(() => _isEditingLogging = !_isEditingLogging),
                  icon: Icon(_isEditingLogging ? Icons.close : Icons.edit),
                  label: Text(_isEditingLogging ? '取消' : '编辑'),
                ),
              ],
            ),
            const Divider(),
            if (!_isEditingLogging) ...[
              ListTile(
                leading: const Icon(Icons.layers),
                title: const Text('根日志级别'),
                subtitle: Text(_rootLogLevel),
              ),
              ListTile(
                leading: const Icon(Icons.code),
                title: const Text('应用日志级别'),
                subtitle: Text(_appLogLevel),
              ),
            ] else ...[
              const Text(
                '日志级别说明：TRACE < DEBUG < INFO < WARN < ERROR',
                style: TextStyle(fontSize: 12, color: Colors.grey),
              ),
              const SizedBox(height: 16),
              Row(
                children: [
                  Expanded(
                    child: DropdownButtonFormField<String>(
                      value: _rootLogLevel,
                      decoration: const InputDecoration(
                        labelText: '根日志级别',
                        border: OutlineInputBorder(),
                        helperText: '控制所有日志输出',
                      ),
                      items: _logLevels.map((level) => DropdownMenuItem(
                        value: level,
                        child: Text(level),
                      )).toList(),
                      onChanged: (value) {
                        if (value != null) setState(() => _rootLogLevel = value);
                      },
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: DropdownButtonFormField<String>(
                      value: _appLogLevel,
                      decoration: const InputDecoration(
                        labelText: '应用日志级别',
                        border: OutlineInputBorder(),
                        helperText: '控制应用代码日志',
                      ),
                      items: _logLevels.map((level) => DropdownMenuItem(
                        value: level,
                        child: Text(level),
                      )).toList(),
                      onChanged: (value) {
                        if (value != null) setState(() => _appLogLevel = value);
                      },
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  ElevatedButton(
                    onPressed: _saveLoggingConfig,
                    child: const Text('保存'),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _saveLoggingConfig() async {
    // TODO: 调用后端 API 保存日志配置
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('日志配置已保存，需要重启应用后生效')),
    );
    setState(() => _isEditingLogging = false);
  }
}
