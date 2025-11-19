@echo off
chcp 65001 >nul
REM ========================================
REM 初始化管理员账号
REM ========================================

title 初始化管理员账号

echo.
echo ========================================
echo #         初始化管理员账号             #
echo ========================================
echo.

REM 检查 Docker 是否运行
docker ps >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] Docker 未运行或未安装
    echo [提示] 请先启动 Docker Desktop
    pause
    exit /b 1
)

REM 检查容器是否运行
docker ps --filter "name=ainoval-server" --format "{{.Names}}" | findstr "ainoval-server" >nul
if %errorlevel% neq 0 (
    echo [错误] ainoval-server 容器未运行
    echo [提示] 请先运行: cd deploy/open ^&^& docker-compose up -d
    pause
    exit /b 1
)

echo [信息] 正在初始化管理员账号...
echo.

REM 第一步：通过API创建用户
echo [1/3] 创建 admin 用户...
curl -X POST http://localhost:18080/api/v1/auth/register/quick ^
    -H "Content-Type: application/json" ^
    -d "{\"username\":\"admin\",\"password\":\"123456\",\"displayName\":\"系统管理员\"}" ^
    -s -o nul -w "HTTP Status: %%{http_code}\n"

if %errorlevel% neq 0 (
    echo [警告] 用户可能已存在，继续执行...
)

timeout /t 2 /nobreak >nul

REM 第二步：创建管理员角色（如果不存在）
echo [2/3] 创建管理员角色...
docker exec ainoval-mongo mongosh -u admin -p admin123 --authenticationDatabase admin ainovel --eval "var role = db.roles.findOne({roleName:'ROLE_ADMIN'}); if (!role) { db.roles.insertOne({roleName:'ROLE_ADMIN',displayName:'管理员',description:'系统管理员',permissions:['ADMIN_MANAGE_USERS','ADMIN_MANAGE_ROLES','ADMIN_MANAGE_SUBSCRIPTIONS','ADMIN_MANAGE_MODELS','ADMIN_MANAGE_CONFIGS','ADMIN_VIEW_ANALYTICS','ADMIN_MANAGE_CREDITS'],enabled:true,priority:100,createdAt:new Date(),updatedAt:new Date()}); print('角色创建成功'); } else { print('角色已存在'); }" --quiet

REM 第三步：为用户分配管理员角色
echo [3/3] 分配管理员权限...
docker exec ainoval-mongo mongosh -u admin -p admin123 --authenticationDatabase admin ainovel --eval "var role = db.roles.findOne({roleName:'ROLE_ADMIN'}); var roleId = role._id.toString(); db.users.updateOne({username:'admin'}, {$set: {roleIds: [roleId], roles: ['ROLE_ADMIN', 'ROLE_USER'], credits: 999999}}); print('权限分配完成');" --quiet

echo.
echo ========================================
echo [成功] 管理员账号初始化完成！
echo ========================================
echo.
echo 管理员登录信息：
echo   - 管理后台地址: http://localhost:18080/admin
echo   - 用户名: admin
echo   - 密码: 123456
echo   - 初始积分: 999999
echo.
echo [重要提示] 请在首次登录后立即修改密码！
echo.
pause
