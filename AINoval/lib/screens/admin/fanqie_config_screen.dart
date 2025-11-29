import 'package:flutter/material.dart';
import '../../services/api_service/base/api_client.dart';

/// 番茄小说服务配置页面
class FanqieConfigScreen extends StatefulWidget {
  const FanqieConfigScreen({super.key});

  @override
  State<FanqieConfigScreen> createState() => _FanqieConfigScreenState();
}

class _FanqieConfigScreenState extends State<FanqieConfigScreen> {
  final ApiClient _apiClient = ApiClient();
  bool _isLoading = true;
  String? _error;
  bool _isEditing = false;
  bool _isTesting = false;
  bool _isReloading = false;

  // 配置数据
  bool _enabled = true;
  String _remoteConfigUrl = '';
  String _apiBaseUrl = '';
  String _fallbackBaseUrl = '';
  Map<String, dynamic> _endpoints = {};
  int _timeout = 30;
  String? _lastLoadTime;
  bool _configLoaded = false;

  // 编辑表单
  final _remoteConfigUrlController = TextEditingController();
  final _fallbackBaseUrlController = TextEditingController();
  final _timeoutController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _loadConfig();
  }

  @override
  void dispose() {
    _remoteConfigUrlController.dispose();
    _fallbackBaseUrlController.dispose();
    _timeoutController.dispose();
    super.dispose();
  }

  Future<void> _loadConfig() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final response = await _apiClient.get('/api/v1/admin/config/fanqie');
      final data = response.data as Map<String, dynamic>;

      setState(() {
        _enabled = data['enabled'] ?? true;
        _remoteConfigUrl = data['remoteConfigUrl'] ?? '';
        _apiBaseUrl = data['apiBaseUrl'] ?? '';
        _fallbackBaseUrl = data['fallbackBaseUrl'] ?? '';
        _endpoints = Map<String, dynamic>.from(data['endpoints'] ?? {});
        _timeout = data['timeout'] ?? 30;
        _lastLoadTime = data['lastLoadTime'];
        _configLoaded = data['configLoaded'] ?? false;

        _remoteConfigUrlController.text = _remoteConfigUrl;
        _fallbackBaseUrlController.text = _fallbackBaseUrl;
        _timeoutController.text = _timeout.toString();

        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _isLoading = false;
      });
    }
  }

  Future<void> _testConnection() async {
    setState(() => _isTesting = true);

    try {
      final response = await _apiClient.post('/api/v1/admin/config/fanqie/test');
      final data = response.data as Map<String, dynamic>;

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(data['message'] ?? '测试完成'),
            backgroundColor: data['success'] == true ? Colors.green : Colors.red,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('测试失败: $e'), backgroundColor: Colors.red),
        );
      }
    } finally {
      setState(() => _isTesting = false);
    }
  }

  Future<void> _reloadRemoteConfig() async {
    setState(() => _isReloading = true);

    try {
      final response = await _apiClient.post('/api/v1/admin/config/fanqie/reload');
      final data = response.data as Map<String, dynamic>;

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(data['message'] ?? '重载完成'),
            backgroundColor: data['success'] == true ? Colors.green : Colors.red,
          ),
        );
        if (data['success'] == true) {
          _loadConfig();
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('重载失败: $e'), backgroundColor: Colors.red),
        );
      }
    } finally {
      setState(() => _isReloading = false);
    }
  }

  Future<void> _saveConfig() async {
    try {
      final response = await _apiClient.put(
        '/api/v1/admin/config/fanqie',
        data: {
          'enabled': _enabled,
          'remoteConfigUrl': _remoteConfigUrlController.text,
          'fallbackBaseUrl': _fallbackBaseUrlController.text,
          'timeout': int.tryParse(_timeoutController.text) ?? 30,
        },
      );
      final data = response.data as Map<String, dynamic>;

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(data['message'] ?? '保存完成'),
            backgroundColor: data['success'] == true ? Colors.green : Colors.red,
          ),
        );
        if (data['success'] == true) {
          setState(() => _isEditing = false);
          _loadConfig();
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('保存失败: $e'), backgroundColor: Colors.red),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('番茄小说服务配置'),
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
          _buildStatusCard(),
          const SizedBox(height: 24),
          _buildConfigCard(),
          const SizedBox(height: 24),
          _buildEndpointsCard(),
        ],
      ),
    );
  }

  Widget _buildStatusCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  _configLoaded ? Icons.check_circle : Icons.warning,
                  color: _configLoaded ? Colors.green : Colors.orange,
                ),
                const SizedBox(width: 8),
                const Text(
                  '服务状态',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                ),
                const Spacer(),
                Chip(
                  label: Text(_enabled ? '已启用' : '已禁用'),
                  backgroundColor: _enabled ? Colors.green.shade100 : Colors.grey.shade300,
                ),
              ],
            ),
            const Divider(),
            ListTile(
              leading: const Icon(Icons.cloud),
              title: const Text('当前 API 地址'),
              subtitle: Text(_apiBaseUrl.isNotEmpty ? _apiBaseUrl : '未加载'),
            ),
            ListTile(
              leading: const Icon(Icons.access_time),
              title: const Text('最后配置加载时间'),
              subtitle: Text(_lastLoadTime ?? '未知'),
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                ElevatedButton.icon(
                  onPressed: _isTesting ? null : _testConnection,
                  icon: _isTesting
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.network_check),
                  label: Text(_isTesting ? '测试中...' : '测试连接'),
                ),
                const SizedBox(width: 8),
                OutlinedButton.icon(
                  onPressed: _isReloading ? null : _reloadRemoteConfig,
                  icon: _isReloading
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.sync),
                  label: Text(_isReloading ? '重载中...' : '重载远程配置'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildConfigCard() {
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
                    Icon(Icons.settings, color: Colors.blue.shade700),
                    const SizedBox(width: 8),
                    const Text(
                      '配置参数',
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
                  onPressed: () => setState(() => _isEditing = !_isEditing),
                  icon: Icon(_isEditing ? Icons.close : Icons.edit),
                  label: Text(_isEditing ? '取消' : '编辑'),
                ),
              ],
            ),
            const Divider(),
            if (!_isEditing) ...[
              SwitchListTile(
                title: const Text('启用状态'),
                subtitle: Text(_enabled ? '服务已启用' : '服务已禁用'),
                value: _enabled,
                onChanged: null,
              ),
              ListTile(
                leading: const Icon(Icons.link),
                title: const Text('远程配置地址'),
                subtitle: Text(_remoteConfigUrl),
              ),
              ListTile(
                leading: const Icon(Icons.backup),
                title: const Text('备用 API 地址'),
                subtitle: Text(_fallbackBaseUrl),
              ),
              ListTile(
                leading: const Icon(Icons.timer),
                title: const Text('请求超时'),
                subtitle: Text('$_timeout 秒'),
              ),
            ] else ...[
              SwitchListTile(
                title: const Text('启用服务'),
                subtitle: const Text('控制番茄小说功能是否可用'),
                value: _enabled,
                onChanged: (value) => setState(() => _enabled = value),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _remoteConfigUrlController,
                decoration: const InputDecoration(
                  labelText: '远程配置地址',
                  hintText: 'https://qbin.me/r/fpoash/',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _fallbackBaseUrlController,
                decoration: const InputDecoration(
                  labelText: '备用 API 地址',
                  hintText: 'http://qkfqapi.vv9v.cn',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _timeoutController,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                  labelText: '请求超时（秒）',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 16),
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  ElevatedButton(
                    onPressed: _saveConfig,
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

  Widget _buildEndpointsCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.api, color: Colors.purple.shade700),
                const SizedBox(width: 8),
                const Text(
                  'API 端点',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                ),
                const SizedBox(width: 8),
                Chip(
                  label: const Text('只读'),
                  backgroundColor: Colors.grey.shade200,
                  labelStyle: const TextStyle(fontSize: 10),
                ),
              ],
            ),
            const Divider(),
            if (_endpoints.isEmpty)
              const ListTile(
                leading: Icon(Icons.info_outline),
                title: Text('暂无端点信息'),
                subtitle: Text('请先重载远程配置'),
              )
            else
              ..._endpoints.entries.map((e) => ListTile(
                    leading: const Icon(Icons.arrow_right),
                    title: Text(e.key),
                    subtitle: Text(e.value.toString()),
                    dense: true,
                  )),
          ],
        ),
      ),
    );
  }
}
