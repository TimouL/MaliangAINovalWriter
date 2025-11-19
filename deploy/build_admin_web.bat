@echo off
chcp 65001 >nul
REM ========================================
REM Admin Flutter Web 构建部署脚本 (Windows)
REM 与应用页面相同逻辑，但入口为 lib\admin_main.dart
REM ========================================

echo [INFO] 开始 Admin Flutter Web 构建流程...
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
set TIMESTAMP=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%

echo [INFO] 项目目录: %PROJECT_DIR%
echo [INFO] 构建目录: %BUILD_DIR%
echo [INFO] 发布目录: %DIST_DIR%\admin_web
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

REM 构建Web版本（Admin 入口）
echo [INFO] 开始构建 Admin Flutter Web...
echo [INFO] 构建模式: 生产环境优化

REM 渲染器与 BaseHref（可在 config.bat 中覆盖 ADMIN_WEB_RENDERER / ADMIN_BASE_HREF）
if "%WEB_RENDERER%"=="" set WEB_RENDERER=html
if "%ADMIN_WEB_RENDERER%"=="" set ADMIN_WEB_RENDERER=%WEB_RENDERER%
if "%ADMIN_BASE_HREF%"=="" set ADMIN_BASE_HREF=/

echo [INFO] Web渲染器: %ADMIN_WEB_RENDERER%
echo [INFO] 基础路径: %ADMIN_BASE_HREF%

echo [INFO] 检查 --web-renderer 支持...
flutter build web -h | findstr /C:"--web-renderer" >nul
if %errorlevel%==0 (
    set RENDERER_ARG=--web-renderer %ADMIN_WEB_RENDERER%
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

call flutter build web --release %RENDERER_ARG% --base-href %ADMIN_BASE_HREF% %PWA_ARG% %TREE_SHAKE_ICONS% -t lib/admin_main.dart
if %errorlevel% neq 0 (
    echo [ERROR] Admin Flutter Web 构建失败！
    pause
    exit /b 1
)

REM 检查构建结果
if not exist "%BUILD_DIR%\index.html" (
    echo [ERROR] 构建失败：未找到 index.html 文件！
    pause
    exit /b 1
)

REM 创建发布目录
echo [INFO] 准备发布文件...
if not exist "%DIST_DIR%" (
    mkdir "%DIST_DIR%"
)
if not exist "%DIST_DIR%\admin_web" (
    mkdir "%DIST_DIR%\admin_web"
)

REM 创建带时间戳的备份目录
set BACKUP_DIR=%DIST_DIR%\backup\admin_%TIMESTAMP%
if not exist "%BACKUP_DIR%" (
    mkdir "%BACKUP_DIR%"
)

REM 复制构建结果到发布目录（admin_web）
echo [INFO] 复制构建文件到发布目录(admin_web)...
xcopy "%BUILD_DIR%" "%DIST_DIR%\admin_web" /E /I /Y /Q
if %errorlevel% neq 0 (
    echo [ERROR] 复制文件失败！
    pause
    exit /b 1
)

REM 复制 admin Nginx 配置和 SSL 示例文件到发布包（便于一次性上传）
echo [INFO] 复制部署配置到发布目录...
REM 不再复制 admin Nginx/SSL 文件（统一维护）

REM 创建备份
echo [INFO] 创建备份...
xcopy "%BUILD_DIR%" "%BACKUP_DIR%" /E /I /Y /Q

REM 创建部署信息文件
echo [INFO] 创建部署信息文件...
echo 构建时间: %date% %time% > "%DIST_DIR%\build_info_admin.txt"
echo 构建版本: Admin Flutter Web Release >> "%DIST_DIR%\build_info_admin.txt"
echo 渲染器: %ADMIN_WEB_RENDERER% >> "%DIST_DIR%\build_info_admin.txt"
echo 项目路径: %PROJECT_DIR% >> "%DIST_DIR%\build_info_admin.txt"
echo 构建目录: %BUILD_DIR% >> "%DIST_DIR%\build_info_admin.txt"

echo [SUCCESS] Admin Flutter Web 构建完成！
echo [INFO] 构建文件位于: %DIST_DIR%\admin_web
echo [INFO] 备份文件位于: %BACKUP_DIR%
echo.

REM 询问是否立即部署
set /p deploy_now="是否立即部署到服务器（admin）？ (y/n): "
if /i "%deploy_now%"=="y" (
    echo [INFO] 开始部署到服务器（admin）...
    call "%DEPLOY_DIR%\deploy_admin_to_server.bat"
) else (
    echo [INFO] 跳过自动部署，可以稍后手动运行 deploy_admin_to_server.bat
)

pause


