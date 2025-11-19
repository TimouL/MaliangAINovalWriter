@echo off
chcp 65001 >nul
REM ========================================
REM Flutter Web 构建部署脚本 (Windows)
REM ========================================

echo [INFO] 开始Flutter Web构建和部署流程...
echo.

REM ========================================
REM 加载配置文件
REM ========================================
set SCRIPT_DIR=%~dp0
if exist "%SCRIPT_DIR%config.bat" (
    echo [INFO] 加载配置文件...
    call "%SCRIPT_DIR%config.bat"
) else (
    echo [ERROR] 配置文件不存在: %SCRIPT_DIR%config.bat
    echo [INFO] 请先创建配置文件或使用默认配置
    pause
    exit /b 1
)

REM 设置变量
set PROJECT_DIR=%FLUTTER_PROJECT_DIR%
set BUILD_DIR=%PROJECT_DIR%\build\web
set DEPLOY_DIR=%DEPLOY_SCRIPT_DIR%
if "%DEPLOY_DIR%"=="" set DEPLOY_DIR=%SCRIPT_DIR%
set DIST_DIR=%DEPLOY_DIR%\dist

REM 简化时间戳生成，避免特殊字符问题
for /f "tokens=1-4 delims=/ " %%a in ('date /t') do set mydate=%%a%%b%%c%%d
for /f "tokens=1-2 delims=: " %%a in ('time /t') do set mytime=%%a%%b
set TIMESTAMP=%mydate%_%mytime%

echo [INFO] 项目目录: %PROJECT_DIR%
echo [INFO] 构建目录: %BUILD_DIR%
echo [INFO] 发布目录: %DIST_DIR%
echo [INFO] 时间戳: %TIMESTAMP%
echo.

REM 检查Flutter是否安装
echo [INFO] 检查Flutter环境...
call flutter --version
if %errorlevel% neq 0 (
    echo [ERROR] Flutter未安装或未加入环境变量！
    pause
    exit /b 1
)

REM 进入项目目录
echo [INFO] 切换到项目目录...
cd /d "%PROJECT_DIR%"
if %errorlevel% neq 0 (
    echo [ERROR] 无法切换到项目目录: %PROJECT_DIR%
    pause
    exit /b 1
)

REM 清理之前的构建
echo [INFO] 清理之前的构建文件...
if exist "build\web" (
    rmdir /s /q "build\web"
    echo [INFO] 删除旧的构建文件完成
)

REM 获取依赖
echo [INFO] 获取Flutter依赖...
call flutter pub get
if %errorlevel% neq 0 (
    echo [ERROR] 获取依赖失败！
    pause
    exit /b 1
)

REM 运行代码生成（如果有）
echo [INFO] 运行代码生成...
where dart >nul 2>nul
if %errorlevel%==0 (
    call dart run build_runner build --delete-conflicting-outputs
) else (
    call flutter packages pub run build_runner build --delete-conflicting-outputs
)
if %errorlevel% neq 0 (
    echo [WARNING] 代码生成失败，继续构建...
)

REM 构建Web版本
echo [INFO] 开始构建Flutter Web...
echo [INFO] 构建模式: 生产环境优化
echo [INFO] Web渲染器: %WEB_RENDERER%
echo [INFO] 基础路径: %BASE_HREF%

REM 默认使用 HTML 渲染器（如未在 config.bat 中指定）
if "%WEB_RENDERER%"=="" set WEB_RENDERER=html
REM 启用默认图标摇树与源映射禁用以减小体积
if "%TREE_SHAKE_ICONS%"=="" set TREE_SHAKE_ICONS=--tree-shake-icons
if "%DART_DEFINE%"=="" set DART_DEFINE=--dart-define=LOG_LEVEL=WARN
REM 启用离线缓存策略（若支持）
if "%PWA_STRATEGY%"=="" set PWA_STRATEGY=offline-first
if "%BASE_HREF%"=="" set BASE_HREF=/

REM 使用指定渲染器进行构建，并启用可选图标摇树
REM 设置生产环境日志级别为WARN，减少控制台输出
echo [INFO] 生产环境日志级别设置为: WARN
echo [INFO] 检查 --web-renderer 支持...
flutter build web -h | findstr /C:"--web-renderer" >nul
if %errorlevel%==0 (
    set RENDERER_ARG=--web-renderer %WEB_RENDERER%
) else (
    set RENDERER_ARG=
    echo [WARNING] 当前 Flutter 版本不支持 --web-renderer，将使用默认渲染器
)
echo [INFO] 检查 --pwa-strategy 支持...
flutter build web -h | findstr /C:"--pwa-strategy" >nul
if %errorlevel%==0 (
    if "%PWA_STRATEGY%"=="" (
        set PWA_ARG=
    ) else (
        set PWA_ARG=--pwa-strategy %PWA_STRATEGY%
        echo [INFO] PWA策略: %PWA_STRATEGY%
    )
) else (
    set PWA_ARG=
)
call flutter build web --release %RENDERER_ARG% --base-href %BASE_HREF% %PWA_ARG% %TREE_SHAKE_ICONS% %DART_DEFINE%
if %errorlevel% neq 0 (
    echo [ERROR] Flutter Web构建失败！
    pause
    exit /b 1
)

REM 检查构建结果
if not exist "%BUILD_DIR%\index.html" (
    echo [ERROR] 构建失败：未找到index.html文件！
    pause
    exit /b 1
)

REM 创建发布目录
echo [INFO] 准备发布文件...
if not exist "%DIST_DIR%" (
    mkdir "%DIST_DIR%"
)

REM 创建带时间戳的备份目录
set BACKUP_DIR=%DIST_DIR%\backup\%TIMESTAMP%
if not exist "%BACKUP_DIR%" (
    mkdir "%BACKUP_DIR%"
)

REM 复制构建结果到发布目录
echo [INFO] 复制构建文件到发布目录...
xcopy "%BUILD_DIR%" "%DIST_DIR%\web" /E /I /Y /Q
if %errorlevel% neq 0 (
    echo [ERROR] 复制文件失败！
    pause
    exit /b 1
)

REM 复制 Nginx 配置和 SSL 文件到发布包（便于一次性上传）
echo [INFO] 复制部署配置到发布目录...
if exist "%DEPLOY_DIR%\nginx_ainoval_production.conf" (
    copy /Y "%DEPLOY_DIR%\nginx_ainoval_production.conf" "%DIST_DIR%\nginx_ainoval_production.conf" >nul
)
if not exist "%DIST_DIR%\ssl" mkdir "%DIST_DIR%\ssl"
if exist "%DEPLOY_DIR%\ssl\maliangwriter_origin.crt" (
    copy /Y "%DEPLOY_DIR%\ssl\maliangwriter_origin.crt" "%DIST_DIR%\ssl\maliangwriter_origin.crt" >nul
)
if exist "%DEPLOY_DIR%\ssl\maliangwriter_origin.key" (
    copy /Y "%DEPLOY_DIR%\ssl\maliangwriter_origin.key" "%DIST_DIR%\ssl\maliangwriter_origin.key" >nul
)

REM 创建备份
echo [INFO] 创建备份...
xcopy "%BUILD_DIR%" "%BACKUP_DIR%" /E /I /Y /Q

REM 创建部署信息文件
echo [INFO] 创建部署信息文件...
echo 构建时间: %date% %time% > "%DIST_DIR%\build_info.txt"
echo 构建版本: Flutter Web Release >> "%DIST_DIR%\build_info.txt"
echo 渲染器: %WEB_RENDERER% >> "%DIST_DIR%\build_info.txt"
echo 项目路径: %PROJECT_DIR% >> "%DIST_DIR%\build_info.txt"
echo 构建目录: %BUILD_DIR% >> "%DIST_DIR%\build_info.txt"

REM 显示构建统计
echo [INFO] 构建完成！
echo.
echo ========================================
echo 构建统计信息:
echo ========================================
for %%F in ("%DIST_DIR%\web\*.*") do (
    echo 文件: %%~nxF - 大小: %%~zF 字节
)
echo.

REM 计算总大小
set total_size=0
for /r "%DIST_DIR%\web" %%F in (*.*) do (
    set /a total_size+=%%~zF
)
echo 总大小: %total_size% 字节
echo.

echo [SUCCESS] Flutter Web构建完成！
echo [INFO] 构建文件位于: %DIST_DIR%\web
echo [INFO] 备份文件位于: %BACKUP_DIR%
echo [INFO] 现在可以运行部署脚本将文件上传到服务器
echo.

REM 询问是否立即部署
set /p deploy_now="是否立即部署到服务器？ (y/n): "
if /i "%deploy_now%"=="y" (
    echo [INFO] 开始部署到服务器...
    call "%DEPLOY_DIR%\deploy_to_server.bat"
) else (
    echo [INFO] 跳过自动部署，可以稍后手动运行 deploy_to_server.bat
)

pause
