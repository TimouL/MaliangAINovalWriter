@echo off
chcp 65001 >nul

REM ========================================
REM Server Configuration
REM ========================================
set SERVER_IP=localhost
set SERVER_USER=root
set SERVER_PORT=22
set SERVER_PASSWORD=11111111
set SSH_KEY_PATH=

REM ========================================
REM Path Configuration
REM ========================================
set PROJECT_ROOT=h:\GitHub\MaliangAINovalWriter
set FLUTTER_PROJECT_DIR=%PROJECT_ROOT%\AINoval
set DEPLOY_SCRIPT_DIR=%PROJECT_ROOT%\deploy
set LOCAL_BUILD_DIR=%DEPLOY_SCRIPT_DIR%\dist\web

REM ========================================
REM Remote Server Paths
REM ========================================
set REMOTE_BASE_DIR=/work
set REMOTE_WEB_DIR=%REMOTE_BASE_DIR%/ainoval/web
set REMOTE_BACKEND_DIR=%REMOTE_BASE_DIR%/ainoval/backend
set REMOTE_CONFIG_DIR=%REMOTE_BASE_DIR%/ainoval/config
set REMOTE_LOG_DIR=%REMOTE_BASE_DIR%/ainoval/logs
set REMOTE_BACKUP_DIR=%REMOTE_BASE_DIR%/ainoval/backup

REM ========================================
REM Nginx Configuration
REM ========================================
set LOCAL_NGINX_CONFIG=%DEPLOY_SCRIPT_DIR%\nginx_ainoval_production.conf
set REMOTE_NGINX_CONFIG=/etc/nginx/sites-available/ainoval
set NGINX_SITES_ENABLED=/etc/nginx/sites-enabled/ainoval

REM ========================================
REM Flutter Build Configuration
REM ========================================
set WEB_RENDERER=html
set BASE_HREF=/
set TREE_SHAKE_ICONS=--no-tree-shake-icons

REM ========================================
REM Deployment Options
REM ========================================
set CREATE_BACKUP=true
set AUTO_RESTART_NGINX=true
set VERBOSE_LOGGING=true
set SSH_TIMEOUT=30
set SCP_TIMEOUT=300

REM ========================================
REM Advanced Configuration
REM ========================================
set API_BACKEND_PORT=18080
set CLIENT_MAX_BODY_SIZE=100
set GZIP_COMPRESSION_LEVEL=6
set STATIC_CACHE_TIME=1y

REM ========================================
REM Development Mode  频繁发布期间PWA_STRATEGY=none
REM ========================================
set DEVELOPMENT_MODE=false
set DEBUG_MODE=false
set LOCAL_TEST_PORT=8080
set PWA_STRATEGY=none
GOTO :EOF 