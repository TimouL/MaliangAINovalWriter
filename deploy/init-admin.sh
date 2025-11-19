#!/bin/bash
# ========================================
# 初始化管理员账号
# ========================================

set -e

echo ""
echo "========================================"
echo "#         初始化管理员账号             #"
echo "========================================"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查 Docker 是否运行
if ! docker ps >/dev/null 2>&1; then
    echo -e "${RED}[错误]${NC} Docker 未运行或未安装"
    echo -e "${YELLOW}[提示]${NC} 请先启动 Docker"
    exit 1
fi

# 检查容器是否运行
if ! docker ps --filter "name=ainoval-server" --format "{{.Names}}" | grep -q "ainoval-server"; then
    echo -e "${RED}[错误]${NC} ainoval-server 容器未运行"
    echo -e "${YELLOW}[提示]${NC} 请先运行: cd deploy/open && docker-compose up -d"
    exit 1
fi

echo -e "${GREEN}[信息]${NC} 正在初始化管理员账号..."
echo ""

# 第一步：通过API创建用户
echo "[1/3] 创建 admin 用户..."
HTTP_CODE=$(curl -X POST http://localhost:18080/api/v1/auth/register/quick \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"123456","displayName":"系统管理员"}' \
    -s -o /dev/null -w "%{http_code}")

if [ "$HTTP_CODE" = "201" ]; then
    echo -e "${GREEN}✓${NC} 用户创建成功"
elif [ "$HTTP_CODE" = "400" ]; then
    echo -e "${YELLOW}⚠${NC} 用户可能已存在，继续执行..."
else
    echo -e "${YELLOW}⚠${NC} HTTP Status: $HTTP_CODE，继续执行..."
fi

sleep 2

# 第二步：创建管理员角色（如果不存在）
echo "[2/3] 创建管理员角色..."
docker exec ainoval-mongo mongosh -u admin -p admin123 --authenticationDatabase admin ainovel --eval "
var role = db.roles.findOne({roleName:'ROLE_ADMIN'});
if (!role) {
    db.roles.insertOne({
        roleName:'ROLE_ADMIN',
        displayName:'管理员',
        description:'系统管理员',
        permissions:[
            'ADMIN_MANAGE_USERS',
            'ADMIN_MANAGE_ROLES',
            'ADMIN_MANAGE_SUBSCRIPTIONS',
            'ADMIN_MANAGE_MODELS',
            'ADMIN_MANAGE_CONFIGS',
            'ADMIN_VIEW_ANALYTICS',
            'ADMIN_MANAGE_CREDITS'
        ],
        enabled:true,
        priority:100,
        createdAt:new Date(),
        updatedAt:new Date()
    });
    print('✓ 角色创建成功');
} else {
    print('✓ 角色已存在');
}
" --quiet

# 第三步：为用户分配管理员角色
echo "[3/3] 分配管理员权限..."
docker exec ainoval-mongo mongosh -u admin -p admin123 --authenticationDatabase admin ainovel --eval "
var role = db.roles.findOne({roleName:'ROLE_ADMIN'});
var roleId = role._id.toString();
db.users.updateOne(
    {username:'admin'},
    {\$set: {
        roleIds: [roleId],
        roles: ['ROLE_ADMIN', 'ROLE_USER'],
        credits: 999999
    }}
);
print('✓ 权限分配完成');
" --quiet

echo ""
echo "========================================"
echo -e "${GREEN}[成功] 管理员账号初始化完成！${NC}"
echo "========================================"
echo ""
echo "管理员登录信息："
echo "  - 管理后台地址: http://localhost:18080/admin"
echo "  - 用户名: admin"
echo "  - 密码: 123456"
echo "  - 初始积分: 999999"
echo ""
echo -e "${YELLOW}[重要提示] 请在首次登录后立即修改密码！${NC}"
echo ""
