import 'package:flutter/material.dart';

/// 即将上线页面
/// 当系统处于受限模式时，普通用户看到此页面
class ComingSoonScreen extends StatelessWidget {
  const ComingSoonScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [
              Colors.blue.shade900,
              Colors.purple.shade900,
            ],
          ),
        ),
        child: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              // Logo 或图标
              Container(
                width: 120,
                height: 120,
                decoration: BoxDecoration(
                  color: Colors.white.withOpacity(0.1),
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.auto_stories,
                  size: 64,
                  color: Colors.white,
                ),
              ),
              const SizedBox(height: 32),
              // 标题
              const Text(
                'AI 小说助手',
                style: TextStyle(
                  fontSize: 36,
                  fontWeight: FontWeight.bold,
                  color: Colors.white,
                ),
              ),
              const SizedBox(height: 16),
              // 副标题
              Text(
                '即将上线',
                style: TextStyle(
                  fontSize: 24,
                  color: Colors.white.withOpacity(0.8),
                ),
              ),
              const SizedBox(height: 32),
              // 描述
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 48),
                child: Text(
                  '系统正在配置中，请稍后再来...',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 16,
                    color: Colors.white.withOpacity(0.6),
                  ),
                ),
              ),
              const SizedBox(height: 48),
              // 加载动画
              SizedBox(
                width: 200,
                child: LinearProgressIndicator(
                  backgroundColor: Colors.white.withOpacity(0.2),
                  valueColor: AlwaysStoppedAnimation<Color>(
                    Colors.white.withOpacity(0.8),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
