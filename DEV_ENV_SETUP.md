WSL 开发环境安装指南（系统级 sudo）

假设：WSL Ubuntu 22.04，具备 sudo 权限，使用宿主机 Docker Desktop（可选）。以下步骤在 WSL 内执行。

0) 基础准备
```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg apt-transport-https unzip zip git build-essential
```

1) 安装 JDK 21 与 Maven
```bash
sudo apt-get install -y openjdk-21-jdk maven
java -version
mvn -v
```

2) 安装 Dart（官方 APT 源）
```bash
sudo install -d -m 0755 /etc/apt/keyrings 或 sudo chmod 0755 /etc/apt/keyrings
# 先删掉之前可能生成的空文件
sudo rm -f /etc/apt/keyrings/dart.gpg
curl -fsSL https://dl.google.com/linux/linux_signing_key.pub | sudo gpg --dearmor -o /etc/apt/keyrings/dart.gpg
echo "deb [signed-by=/etc/apt/keyrings/dart.gpg] https://storage.googleapis.com/download.dartlang.org/linux/debian stable main" | sudo tee /etc/apt/sources.list.d/dart_stable.list
sudo apt-get update
sudo apt-get install -y dart
dart --version
```

3) 安装 Flutter SDK（系统级路径）
```bash
cd /tmp
#官方下载
curl -LO https://storage.googleapis.com/flutter_infra_release/releases/stable/linux/flutter_linux_3.24.5-stable.tar.xz
#阿里云OSS 镜像加速
curl -LO https://storage.flutter-io.cn/flutter_infra_release/releases/stable/linux/flutter_linux_3.24.5-stable.tar.xz
sudo mkdir -p /opt/flutter
sudo tar -xf flutter_linux_3.24.5-stable.tar.xz -C /opt/flutter --strip-components=1
```

4) 配置 PATH（全局生效）
```bash
echo 'export PATH="/opt/flutter/bin:$PATH"' | sudo tee /etc/profile.d/flutter.sh
source /etc/profile.d/flutter.sh
flutter doctor  # 检查安装
```

5) 安装 Flutter 构建依赖并启用 Web
```bash
sudo apt-get install -y clang cmake ninja-build pkg-config libgtk-3-dev liblzma-dev
flutter config --enable-web
flutter doctor # 检查安装
```
6) 如果是用root用户安装的Flutter则使用下面命令处理
Flutter 未能运行：/opt/flutter 由 root 拥有，flutter 需要写 version 文件且触发 git safe.directory 检查，导致权限/安全错误。使用 sudo 修复 Flutter：
```bash
sudo chown -R $USER:$USER /opt/flutter
git config --global --add safe.directory /opt/flutter
source /etc/profile.d/flutter.sh && flutter --version && flutter doctor
```

完成后即可在当前项目使用：后端构建 `mvn clean package`，前端 `flutter pub get && flutter build web`（或 `flutter run -d chrome` 需要宿主浏览器/WSL GUI 支持）。
