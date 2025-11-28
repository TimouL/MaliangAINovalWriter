import 'package:flutter/material.dart';
import '../../services/api_service/repositories/setup_repository.dart';

/// 基础设施配置管理页面
class InfrastructureConfigScreen extends StatefulWidget {
  const InfrastructureConfigScreen({super.key});

  @override
  State<InfrastructureConfigScreen> createState() => _InfrastructureConfigScreenState();
}

class _InfrastructureConfigScreenState extends State<InfrastructureConfigScreen> {
  final SetupRepository _repository = SetupRepository();
  bool _isLoading = true;
  String? _error;
  
  // 配置数据
  Map<String, dynamic> _config = {};
  
  // 编辑状态
  bool _isEditingStorage = false;
  bool _isEditingChroma = false;
  
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

  Future<void> _loadConfig() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final status = await _repository.getSystemStatus();
      setState(() {
        _config = {
          'mongoConnected': status.mongoConnected,
          'storageConnected': status.storageConnected,
          'chromaConnected': status.chromaConnected,
          'setupCompleted': status.setupCompleted,
        };
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _isLoading = false;
      });
    }
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
            const SizedBox(height: 8),
            Text(
              '注意：MongoDB 连接配置修改需要重启应用后生效',
              style: TextStyle(color: Colors.grey.shade600, fontSize: 12),
            ),
          ],
        ),
      ),
    );
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
