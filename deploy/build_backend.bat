@echo off
chcp 65001 >nul
REM ========================================
REM AINoval 后端构建脚本
REM ========================================

title AINoval - 后端构建

REM 获取脚本所在目录
set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..
set BACKEND_DIR=%PROJECT_ROOT%\AINovalServer
REM 强制使用 JDK 21 环境
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"

REM 跳转到主程序
goto :start

:start
cls
echo.
echo ===========================================================
echo #                    AINoval 后端构建                    #
echo #                  Spring Boot 应用打包工具              #
echo ===========================================================
echo.

REM 检查Java环境
echo [信息] 检查Java环境...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] Java 未安装或未配置环境变量
    echo [建议] 请安装 JDK 11 或更高版本
    echo [信息] 按任意键继续或Ctrl+C退出...
    pause >nul
    exit /b 1
) else (
    echo [成功] Java 环境正常
)

echo [信息] 检查Maven环境...
where mvn >nul 2>&1
if %errorlevel% neq 0 (
    echo [警告] Maven 未安装或未配置环境变量
    echo [信息] 将使用项目自带的 Maven Wrapper
    set USE_WRAPPER=true
) else (
    echo [成功] Maven 环境正常
    set USE_WRAPPER=false
)

REM 显示项目信息
echo [信息] 项目路径: %BACKEND_DIR%
echo [信息] 构建环境: %PROFILE%
echo.

REM 检查项目目录
if not exist "%BACKEND_DIR%\pom.xml" (
    echo [错误] 未找到后端项目文件: %BACKEND_DIR%\pom.xml
    pause
    exit /b 1
)

echo ========================================
echo 构建选项：
echo ========================================
echo.
echo  1. [*] 完整构建 (清理+编译+测试+打包)
echo  2. [+] 快速构建 (跳过测试)
echo  3. [^] 仅打包 (不编译)
echo  4. [!] 清理项目
echo  5. [?] 查看项目信息
echo  0. [X] 退出
echo.
echo ========================================

set /p choice="请选择构建操作 (0-5): "

if "%choice%"=="1" goto :full_build
if "%choice%"=="2" goto :quick_build
if "%choice%"=="3" goto :package_only
if "%choice%"=="4" goto :clean_only
if "%choice%"=="5" goto :project_info
if "%choice%"=="0" goto :exit

echo [错误] 无效选择，请重新输入
pause
goto :start

:full_build
echo.
echo [*] 开始完整构建...
echo.
cd /d "%BACKEND_DIR%"

if "%USE_WRAPPER%"=="true" (
    echo [信息] 使用项目包装器...
    call mvnw.cmd clean compile test package -DskipTests=false
) else (
    echo [信息] 使用系统Maven...
    call mvn clean compile test package -DskipTests=false
)

if %errorlevel% neq 0 (
    echo [错误] 构建失败
    pause
    exit /b 1
)
goto :build_success

:quick_build
echo.
echo [+] 开始快速构建（跳过测试）...
echo.
cd /d "%BACKEND_DIR%"

if "%USE_WRAPPER%"=="true" (
    call mvnw.cmd clean package -DskipTests=true
) else (
    call mvn clean package -DskipTests=true
)

if %errorlevel% neq 0 (
    echo [错误] 构建失败
    pause
    exit /b 1
)
goto :build_success

:package_only
echo.
echo [^] 仅打包应用...
echo.
cd /d "%BACKEND_DIR%"

if "%USE_WRAPPER%"=="true" (
    call mvnw.cmd package -DskipTests=true
) else (
    call mvn package -DskipTests=true
)

if %errorlevel% neq 0 (
    echo [错误] 打包失败
    pause
    exit /b 1
)
goto :build_success

:clean_only
echo.
echo [!] 清理项目...
echo.
cd /d "%BACKEND_DIR%"

if "%USE_WRAPPER%"=="true" (
    call mvnw.cmd clean
) else (
    call mvn clean
)

echo [信息] 项目清理完成
pause
goto :start

:project_info
echo.
echo [?] 项目信息...
echo.
cd /d "%BACKEND_DIR%"

if "%USE_WRAPPER%"=="true" (
    call mvnw.cmd help:evaluate -Dexpression=project.version -q -DforceStdout
) else (
    call mvn help:evaluate -Dexpression=project.version -q -DforceStdout
)

echo.
echo [信息] 项目依赖信息:
if "%USE_WRAPPER%"=="true" (
    call mvnw.cmd dependency:tree | findstr -v "Download"
) else (
    call mvn dependency:tree | findstr -v "Download"
)

pause
goto :start

:build_success
echo.
echo ========================================
echo [成功] 构建完成！
echo ========================================
echo.

REM 查找生成的JAR文件
for /f "delims=" %%i in ('dir /b "%BACKEND_DIR%\target\*.jar" 2^>nul ^| findstr -v "original"') do (
    set JAR_FILE=%%i
)

if defined JAR_FILE (
    echo [信息] 生成的JAR文件: %JAR_FILE%
    echo [信息] 文件路径: %BACKEND_DIR%\target\%JAR_FILE%
    
    REM 显示文件大小
    for %%A in ("%BACKEND_DIR%\target\%JAR_FILE%") do (
        set FILE_SIZE=%%~zA
    )
    
    echo [信息] 文件大小: %FILE_SIZE% 字节
    
    REM 复制到部署目录
    if not exist "%SCRIPT_DIR%dist" (
        mkdir "%SCRIPT_DIR%dist"
    )
    
    copy "%BACKEND_DIR%\target\%JAR_FILE%" "%SCRIPT_DIR%dist\ainoval-server.jar" >nul
    if %errorlevel% equ 0 (
        echo [信息] JAR文件已复制到部署目录: %SCRIPT_DIR%dist\ainoval-server.jar
    )
) else (
    echo [警告] 未找到生成的JAR文件
)

echo.
set /p restart="是否返回主菜单？ (y/n): "
if /i "%restart%"=="y" (
    goto :start
)

:exit
echo.
echo 构建脚本执行完成！
echo.
pause
exit /b 0

 