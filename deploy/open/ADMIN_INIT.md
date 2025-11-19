# 管理员账号初始化文档

## 概述

系统在首次启动时会自动创建默认管理员账号，用于管理系统和用户。

## 默认管理员账号

- **用户名**: `admin`
- **密码**: `123456`
- **邮箱**: `admin@example.com`
- **角色**: `USER`, `ADMIN`
- **积分**: `999999`
- **状态**: `ACTIVE`

## 自动初始化机制

### 1. 初始化脚本

管理员账号通过MongoDB初始化脚本自动创建：
- **脚本文件**: `deploy/open/mongo-init-admin.js`
- **执行时机**: MongoDB容器首次启动时（数据库为空时）
- **执行方式**: 通过Docker Compose自动挂载到 `/docker-entrypoint-initdb.d/`

### 2. Docker Compose配置

```yaml
volumes:
  - ./mongo-init-admin.js:/docker-entrypoint-initdb.d/02-mongo-init-admin.js:ro
```

**注意**: 文件名前缀 `02-` 确保在副本集初始化（01-）之后执行。

### 3. 密码加密

密码使用BCrypt算法加密，强度为10：
```javascript
const passwordHash = "$2a$10$N9qo8uLOickgx2ZMRZoMye1IYrDJWdNHmDgHmBGhiEHOqhGNBRSiG";
// 对应明文密码: 123456
```

## 手动初始化（如需要）

如果自动初始化失败，可以手动执行：

```bash
# 方法1: 通过Docker执行
docker cp deploy/open/mongo-init-admin.js ainoval-mongo:/tmp/mongo-init-admin.js
docker exec ainoval-mongo mongosh -u admin -p admin123 --authenticationDatabase admin /tmp/mongo-init-admin.js

# 方法2: 通过mongosh直接连接
mongosh mongodb://admin:admin123@localhost:27017/ainovel?authSource=admin < deploy/open/mongo-init-admin.js
```

## 验证管理员账号

### 检查用户是否存在

```bash
docker exec ainoval-mongo mongosh -u admin -p admin123 --authenticationDatabase admin \
  --eval "db.getSiblingDB('ainovel').users.findOne({username: 'admin'}, {password: 0})"
```

### 测试登录

使用管理员账号登录应用：

**主应用登录**：
1. 访问: http://localhost:18080
2. 输入用户名: `admin`
3. 输入密码: `123456`
4. 登录后具有管理员权限

**管理员面板**（独立 Flutter Web 应用）：
- **后端端口**: 18080（与主应用共用同一后端）
- **静态资源位置**: `/app/admin_web/`
- **API连接**: `/api/v1`（相对路径，自动指向 18080 端口）
- **访问方式**: 
  - 方式1: 通过主应用登录后访问管理功能（推荐）
  - 方式2: 直接访问 admin_web 静态文件（需要额外配置路由）

**注意**：主应用和管理员面板是两个独立的 Flutter Web 应用，但它们都连接到同一个后端 API服务（18080端口）。

## 管理员权限

管理员账号拥有以下角色：

- **USER**: 普通用户权限（创建小说、使用AI等）
- **ADMIN**: 管理员权限（用户管理、系统配置等）

### 管理员可访问的功能

- 用户管理
- 内容审核
- 系统配置
- 模型配置
- 积分管理
- 订阅管理
- 数据分析
- 日志查看

## 安全建议

⚠️ **重要安全提示**

1. **立即修改密码**: 首次登录后立即修改默认密码
2. **使用强密码**: 密码应包含大小写字母、数字和特殊字符
3. **限制访问**: 不要在公网暴露管理员账号
4. **定期审计**: 定期检查管理员操作日志
5. **备份账号**: 建议创建备用管理员账号

### 修改密码

#### 方法1: 通过UI修改
1. 登录管理员账号
2. 进入"个人设置" -> "安全设置"
3. 修改密码

#### 方法2: 通过数据库修改

```javascript
// 生成新密码的BCrypt哈希（使用bcryptjs）
const bcrypt = require('bcryptjs');
const newPasswordHash = bcrypt.hashSync("your_new_password", 10);

// 更新数据库
db.getSiblingDB('ainovel').users.updateOne(
  { username: "admin" },
  { $set: { password: newPasswordHash, updatedAt: new Date() } }
);
```

## 故障排查

### 问题1: 管理员账号未创建

**原因**: 
- MongoDB数据卷已存在，初始化脚本不会执行
- 初始化脚本执行失败

**解决方案**:
```bash
# 删除数据卷重新初始化
docker compose -f deploy/open/docker-compose.yml down -v
docker compose -f deploy/open/docker-compose.yml up -d

# 或手动执行初始化脚本（见上文）
```

### 问题2: 无法登录

**可能原因**:
- 密码输入错误
- 账号状态为非ACTIVE
- 数据库连接问题

**检查账号状态**:
```bash
docker exec ainoval-mongo mongosh -u admin -p admin123 --authenticationDatabase admin \
  --eval "db.getSiblingDB('ainovel').users.findOne({username: 'admin'}, {password: 0})"
```

### 问题3: 权限不足

**检查角色配置**:
```bash
docker exec ainoval-mongo mongosh -u admin -p admin123 --authenticationDatabase admin \
  --eval "db.getSiblingDB('ainovel').users.findOne({username: 'admin'}).roles"
```

应该返回: `[ 'USER', 'ADMIN' ]`

## 相关文件

- `deploy/open/mongo-init-admin.js` - 管理员初始化脚本
- `deploy/open/docker-compose.yml` - Docker Compose配置
- `deploy/open/README.md` - 部署说明
- `scripts/seed-admin.js` - Node.js版本的初始化脚本（参考）

## 更新记录

- 2025-11-19: 初始版本，添加自动管理员账号初始化功能
