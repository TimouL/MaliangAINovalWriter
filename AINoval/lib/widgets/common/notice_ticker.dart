import 'dart:async';

import 'package:flutter/material.dart';
import 'package:ainoval/utils/web_theme.dart';

/// 公告轮播组件：
/// - 支持自动循环播放
/// - 鼠标悬停暂停
/// - 文本可选择复制
/// - 可手动添加消息
class NoticeTicker extends StatefulWidget {
  final List<String>? initialMessages;
  final Duration interval;
  final TextStyle? textStyle;
  final bool allowAdd;

  const NoticeTicker({
    super.key,
    this.initialMessages,
    this.interval = const Duration(seconds: 4),
    this.textStyle,
    this.allowAdd = false,
  });

  @override
  State<NoticeTicker> createState() => _NoticeTickerState();
}

class _NoticeTickerState extends State<NoticeTicker> {
  late List<String> _messages;
  int _currentIndex = 0;
  Timer? _timer;
  bool _isHovering = false;

  @override
  void initState() {
    super.initState();
    _messages = (widget.initialMessages == null || widget.initialMessages!.isEmpty)
        ? <String>[
            '当前小说网站属于测试状态，欢迎大家加入qq群',
            '如果有报错和bug或者改进建议，欢迎大家在群里反馈'
          ]
        : List<String>.from(widget.initialMessages!);
    _startTimer();
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  void _startTimer() {
    _timer?.cancel();
    if (_messages.length <= 1) return;
    _timer = Timer.periodic(widget.interval, (_) {
      if (!_isHovering && mounted) {
        setState(() {
          _currentIndex = (_currentIndex + 1) % _messages.length;
        });
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final style = widget.textStyle ?? TextStyle(
      fontSize: 20,
      fontWeight: FontWeight.w700,
      color: WebTheme.getPrimaryColor(context),
    );

    final current = _messages.isNotEmpty ? _messages[_currentIndex] : '';

    return Container(
      constraints: const BoxConstraints(minHeight: 40),
      alignment: Alignment.centerLeft,
      child: Row(
        children: [
          // 文本区域：悬停暂停 + 可复制
          Expanded(
            child: MouseRegion(
              onEnter: (_) {
                setState(() => _isHovering = true);
              },
              onExit: (_) {
                setState(() => _isHovering = false);
              },
              child: AnimatedSwitcher(
                duration: const Duration(milliseconds: 350),
                transitionBuilder: (child, animation) {
                  // 轻微滑动+淡入
                  final offset = Tween<Offset>(begin: const Offset(0.1, 0), end: Offset.zero).animate(animation);
                  return ClipRect(
                    child: SlideTransition(position: offset, child: FadeTransition(opacity: animation, child: child)),
                  );
                },
                child: SelectableText(
                  current,
                  key: ValueKey<int>(_currentIndex),
                  style: style,
                  maxLines: 1,
                  textAlign: TextAlign.left,
                  toolbarOptions: const ToolbarOptions(copy: true, selectAll: true),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}


