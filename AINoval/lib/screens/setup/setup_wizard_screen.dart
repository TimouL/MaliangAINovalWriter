import 'package:flutter/material.dart';
import '../../services/api_service/repositories/setup_repository.dart';
import '../admin/admin_login_screen.dart';

/// 配置向导主屏幕
class SetupWizardScreen extends StatefulWidget {
  const SetupWizardScreen({super.key});

  @override
  State<SetupWizardScreen> createState() => _SetupWizardScreenState();
}

class _SetupWizardScreenState extends State<SetupWizardScreen> {
  final SetupRepository _repository = SetupRepository();
  int _currentStep = 0;
  bool _isLoading = false;
  String? _errorMessage;

  // 表单控制器
  final _mongoUriController = TextEditingController();
  final _localPathController = TextEditingController(text: '/data/storage');
  final _ossEndpointController = TextEditingController();
  final _ossAccessKeyIdController = TextEditingController();
  final _ossAccessKeySecretController = TextEditingController();
  final _ossBucketNameController = TextEditingController();
  final _chromaUrlController = TextEditingController();
  final _chromaTokenController = TextEditingController();
  final _adminUsernameController = TextEditingController(text: 'admin');
  final _adminEmailController = TextEditingController();
  final _adminPasswordController = TextEditingController();
  final _adminPasswordConfirmController = TextEditingController();

  // 配置状态
  String _storageProvider = 'local';
  bool _chromaEnabled = false;
  bool _mongoTestPassed = false;
  String? _databaseStatus;
  bool _skipAdminStep = false;

  @override
  void dispose() {
    _mongoUriController.dispose();
    _localPathController.dispose();
    _ossEndpointController.dispose();
    _ossAccessKeyIdController.dispose();
    _ossAccessKeySecretController.dispose();
    _ossBucketNameController.dispose();
    _chromaUrlController.dispose();
    _chromaTokenController.dispose();
    _adminUsernameController.dispose();
    _adminEmailController.dispose();
    _adminPasswordController.dispose();
    _adminPasswordConfirmController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('系统配置向导'),
        centerTitle: true,
      ),
      body: Column(
        children: [
          // 步骤指示器
          _buildStepIndicator(),
          // 步骤内容
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : _buildStepContent(),
          ),
          // 错误信息
          if (_errorMessage != null)
            Container(
              padding: const EdgeInsets.all(16),
              color: Colors.red.shade50,
              child: Row(
                children: [
                  const Icon(Icons.error, color: Colors.red),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      _errorMessage!,
                      style: const TextStyle(color: Colors.red),
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () => setState(() => _errorMessage = null),
                  ),
                ],
              ),
            ),
          // 导航按钮
          _buildNavigationButtons(),
        ],
      ),
    );
  }

  Widget _buildStepIndicator() {
    final steps = ['数据库', '存储', '向量库', '管理员', '确认'];
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: List.generate(steps.length, (index) {
          final isActive = index == _currentStep;
          final isCompleted = index < _currentStep;
          return Row(
            children: [
              if (index > 0)
                Container(
                  width: 40,
                  height: 2,
                  color: isCompleted ? Colors.blue : Colors.grey.shade300,
                ),
              Container(
                width: 32,
                height: 32,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: isActive
                      ? Colors.blue
                      : isCompleted
                          ? Colors.green
                          : Colors.grey.shade300,
                ),
                child: Center(
                  child: isCompleted
                      ? const Icon(Icons.check, color: Colors.white, size: 16)
                      : Text(
                          '${index + 1}',
                          style: TextStyle(
                            color: isActive ? Colors.white : Colors.grey,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                ),
              ),
            ],
          );
        }),
      ),
    );
  }

  Widget _buildStepContent() {
    switch (_currentStep) {
      case 0:
        return _buildDatabaseStep();
      case 1:
        return _buildStorageStep();
      case 2:
        return _buildChromaStep();
      case 3:
        return _buildAdminStep();
      case 4:
        return _buildConfirmStep();
      default:
        return const SizedBox();
    }
  }

  Widget _buildDatabaseStep() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '步骤 1: 数据库配置',
            style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          const Text(
            '请输入 MongoDB 连接字符串。支持 MongoDB Atlas 或自建实例。',
            style: TextStyle(color: Colors.grey),
          ),
          const SizedBox(height: 24),
          TextField(
            controller: _mongoUriController,
            decoration: InputDecoration(
              labelText: 'MongoDB URI',
              hintText: 'mongodb://user:password@host:27017/database',
              border: const OutlineInputBorder(),
              suffixIcon: _mongoTestPassed
                  ? const Icon(Icons.check_circle, color: Colors.green)
                  : null,
            ),
          ),
          const SizedBox(height: 16),
          ElevatedButton.icon(
            onPressed: _testMongoConnection,
            icon: const Icon(Icons.play_arrow),
            label: const Text('测试连接'),
          ),
          if (_databaseStatus != null) ...[
            const SizedBox(height: 16),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    Icon(
                      _databaseStatus == 'complete'
                          ? Icons.check_circle
                          : Icons.info,
                      color: _databaseStatus == 'complete'
                          ? Colors.green
                          : Colors.orange,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(_getDatabaseStatusText()),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  String _getDatabaseStatusText() {
    switch (_databaseStatus) {
      case 'empty':
        return '数据库为空，将创建新的数据结构和管理员账户';
      case 'has_data_no_admin':
        return '数据库已有数据但无管理员，将跳过部分步骤直接创建管理员';
      case 'complete':
        return '数据库已完整配置，可直接完成向导';
      default:
        return '数据库状态未知';
    }
  }

  Widget _buildStorageStep() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '步骤 2: 存储配置',
            style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          const Text(
            '选择文件存储方式。本地存储适合单机部署，OSS 适合云端部署。',
            style: TextStyle(color: Colors.grey),
          ),
          const SizedBox(height: 24),
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
          const SizedBox(height: 24),
          if (_storageProvider == 'local')
            TextField(
              controller: _localPathController,
              decoration: const InputDecoration(
                labelText: '存储路径',
                hintText: '/data/storage',
                border: OutlineInputBorder(),
              ),
            )
          else ...[
            TextField(
              controller: _ossEndpointController,
              decoration: const InputDecoration(
                labelText: 'OSS Endpoint',
                hintText: 'https://oss-cn-hangzhou.aliyuncs.com',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _ossAccessKeyIdController,
              decoration: const InputDecoration(
                labelText: 'Access Key ID',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _ossAccessKeySecretController,
              obscureText: true,
              decoration: const InputDecoration(
                labelText: 'Access Key Secret',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _ossBucketNameController,
              decoration: const InputDecoration(
                labelText: 'Bucket Name',
                border: OutlineInputBorder(),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildChromaStep() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '步骤 3: 向量库配置',
            style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          const Text(
            'Chroma 向量库用于 RAG 知识检索功能。如不需要可跳过。',
            style: TextStyle(color: Colors.grey),
          ),
          const SizedBox(height: 24),
          SwitchListTile(
            title: const Text('启用 Chroma 向量库'),
            subtitle: const Text('用于知识库检索和拆书功能'),
            value: _chromaEnabled,
            onChanged: (value) => setState(() => _chromaEnabled = value),
          ),
          if (_chromaEnabled) ...[
            const SizedBox(height: 16),
            TextField(
              controller: _chromaUrlController,
              decoration: const InputDecoration(
                labelText: 'Chroma 服务地址',
                hintText: 'http://localhost:8000',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _chromaTokenController,
              obscureText: true,
              decoration: const InputDecoration(
                labelText: '认证 Token (可选)',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 16),
            ElevatedButton.icon(
              onPressed: _testChromaConnection,
              icon: const Icon(Icons.play_arrow),
              label: const Text('测试连接'),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildAdminStep() {
    if (_skipAdminStep) {
      return const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.check_circle, size: 64, color: Colors.green),
            SizedBox(height: 16),
            Text('管理员账户已存在，跳过此步骤'),
          ],
        ),
      );
    }

    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '步骤 4: 创建管理员',
            style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          const Text(
            '创建系统管理员账户，用于管理后台登录。',
            style: TextStyle(color: Colors.grey),
          ),
          const SizedBox(height: 24),
          TextField(
            controller: _adminUsernameController,
            decoration: const InputDecoration(
              labelText: '用户名',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _adminEmailController,
            decoration: const InputDecoration(
              labelText: '邮箱',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _adminPasswordController,
            obscureText: true,
            decoration: const InputDecoration(
              labelText: '密码',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _adminPasswordConfirmController,
            obscureText: true,
            decoration: const InputDecoration(
              labelText: '确认密码',
              border: OutlineInputBorder(),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildConfirmStep() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '步骤 5: 确认配置',
            style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          const Text(
            '请确认以下配置信息，点击完成后将保存配置并启动系统。',
            style: TextStyle(color: Colors.grey),
          ),
          const SizedBox(height: 24),
          _buildConfigSummaryCard('数据库', [
            '连接地址: ${_maskUri(_mongoUriController.text)}',
          ]),
          const SizedBox(height: 16),
          _buildConfigSummaryCard('存储', [
            '类型: ${_storageProvider == 'local' ? '本地存储' : '阿里云 OSS'}',
            if (_storageProvider == 'local')
              '路径: ${_localPathController.text}'
            else
              'Bucket: ${_ossBucketNameController.text}',
          ]),
          const SizedBox(height: 16),
          _buildConfigSummaryCard('向量库', [
            '状态: ${_chromaEnabled ? '已启用' : '已禁用'}',
            if (_chromaEnabled) '地址: ${_chromaUrlController.text}',
          ]),
          const SizedBox(height: 16),
          _buildConfigSummaryCard('管理员', [
            '用户名: ${_adminUsernameController.text}',
            '邮箱: ${_adminEmailController.text}',
          ]),
        ],
      ),
    );
  }

  Widget _buildConfigSummaryCard(String title, List<String> items) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
            const Divider(),
            ...items.map((item) => Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Text(item),
                )),
          ],
        ),
      ),
    );
  }

  Widget _buildNavigationButtons() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Theme.of(context).cardColor,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.1),
            blurRadius: 4,
            offset: const Offset(0, -2),
          ),
        ],
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          if (_currentStep > 0)
            TextButton.icon(
              onPressed: () => setState(() => _currentStep--),
              icon: const Icon(Icons.arrow_back),
              label: const Text('上一步'),
            )
          else
            const SizedBox(),
          ElevatedButton.icon(
            onPressed: _handleNext,
            icon: Icon(_currentStep == 4 ? Icons.check : Icons.arrow_forward),
            label: Text(_currentStep == 4 ? '完成配置' : '下一步'),
          ),
        ],
      ),
    );
  }

  String _maskUri(String uri) {
    return uri.replaceAll(RegExp(r'://[^:]+:[^@]+@'), '://***:***@');
  }

  Future<void> _testMongoConnection() async {
    if (_mongoUriController.text.isEmpty) {
      setState(() => _errorMessage = '请输入 MongoDB URI');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final result = await _repository.testMongoDB(_mongoUriController.text);
      setState(() {
        _mongoTestPassed = result.success;
        _databaseStatus = result.databaseStatus;
        if (!result.success) {
          _errorMessage = result.message;
        }
        // 根据数据库状态决定是否跳过管理员步骤
        _skipAdminStep = result.databaseStatus == 'complete';
      });
    } catch (e) {
      setState(() => _errorMessage = e.toString());
    } finally {
      setState(() => _isLoading = false);
    }
  }

  Future<void> _testChromaConnection() async {
    if (_chromaUrlController.text.isEmpty) {
      setState(() => _errorMessage = '请输入 Chroma 服务地址');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final result = await _repository.testChroma(
        _chromaUrlController.text,
        _chromaTokenController.text.isEmpty ? null : _chromaTokenController.text,
      );
      if (!result.success) {
        setState(() => _errorMessage = result.message);
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Chroma 连接成功')),
        );
      }
    } catch (e) {
      setState(() => _errorMessage = e.toString());
    } finally {
      setState(() => _isLoading = false);
    }
  }

  Future<void> _handleNext() async {
    setState(() => _errorMessage = null);

    // 验证当前步骤
    switch (_currentStep) {
      case 0: // 数据库
        if (!_mongoTestPassed) {
          setState(() => _errorMessage = '请先测试并确认数据库连接成功');
          return;
        }
        break;
      case 3: // 管理员
        if (!_skipAdminStep) {
          if (_adminPasswordController.text != _adminPasswordConfirmController.text) {
            setState(() => _errorMessage = '两次输入的密码不一致');
            return;
          }
          if (_adminPasswordController.text.length < 6) {
            setState(() => _errorMessage = '密码长度至少 6 位');
            return;
          }
        }
        break;
      case 4: // 完成
        await _completeSetup();
        return;
    }

    // 进入下一步
    setState(() => _currentStep++);
  }

  Future<void> _completeSetup() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      // 1. 保存配置
      final config = InfrastructureConfig(
        mongoUri: _mongoUriController.text,
        storageProvider: _storageProvider,
        localStoragePath: _storageProvider == 'local' ? _localPathController.text : null,
        ossEndpoint: _storageProvider == 'oss' ? _ossEndpointController.text : null,
        ossAccessKeyId: _storageProvider == 'oss' ? _ossAccessKeyIdController.text : null,
        ossAccessKeySecret: _storageProvider == 'oss' ? _ossAccessKeySecretController.text : null,
        ossBucketName: _storageProvider == 'oss' ? _ossBucketNameController.text : null,
        chromaEnabled: _chromaEnabled,
        chromaUrl: _chromaEnabled ? _chromaUrlController.text : null,
        chromaAuthToken: _chromaEnabled ? _chromaTokenController.text : null,
      );

      final saveResult = await _repository.saveConfig(config);
      if (!saveResult) {
        setState(() => _errorMessage = '保存配置失败');
        return;
      }

      // 2. 初始化管理员（如果需要）
      if (!_skipAdminStep) {
        final adminResult = await _repository.initAdmin(
          _adminUsernameController.text,
          _adminEmailController.text,
          _adminPasswordController.text,
        );
        if (!adminResult.success) {
          setState(() => _errorMessage = adminResult.message);
          return;
        }
      }

      // 3. 完成向导
      final completeResult = await _repository.completeSetup();
      if (!completeResult) {
        setState(() => _errorMessage = '完成配置失败');
        return;
      }

      // 4. 跳转到登录页
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('配置完成！请重新启动应用以使配置生效。')),
        );
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(builder: (_) => const AdminLoginScreen()),
        );
      }
    } catch (e) {
      setState(() => _errorMessage = e.toString());
    } finally {
      setState(() => _isLoading = false);
    }
  }
}
