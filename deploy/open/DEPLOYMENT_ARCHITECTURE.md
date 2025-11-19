# AINoval 部署架构说明

## 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│                    Docker 容器环境                            │
│                                                              │
│  ┌────────────────────────────────────────────────────┐     │
│  │  ainoval-server 容器 (端口 18080)                   │     │
│  │                                                     │     │
│  │  ┌──────────────────────────────────────────┐      │     │
│  │  │  Spring Boot 后端                         │      │     │
│  │  │  - REST API: /api/v1/*                   │      │     │
│  │  │  - 用户管理                               │      │     │
│  │  │  - AI 服务                                │      │     │
│  │  │  - 静态资源服务                           │      │     │
│  │  └──────────────────────────────────────────┘      │     │
│  │                                                     │     │
│  │  ┌──────────────────────────────────────────┐      │     │
│  │  │  静态资源                                  │      │     │
│  │  │                                           │      │     │
│  │  │  /app/web/         (主应用前端)           │      │     │
│  │  │  ├─ index.html                            │      │     │
│  │  │  ├─ main.dart.js                          │      │     │
│  │  │  ├─ fonts/ (64个字体文件)                 │      │     │
│  │  │  └─ ... (其他资源)                        │      │     │
│  │  │                                           │      │     │
│  │  │  /app/admin_web/   (管理员面板)           │      │     │
│  │  │  ├─ index.html                            │      │     │
│  │  │  ├─ main.dart.js                          │      │     │
│  │  │  └─ ... (完整管理界面)                    │      │     │
│  │  └──────────────────────────────────────────┘      │     │
│  └────────────────────────────────────────────────────┘     │
│                                                              │
│  ┌────────────────────────────────────────────────────┐     │
│  │  ainoval-mongo 容器 (端口 27017)                    │     │
│  │  - MongoDB 8.0 副本集                               │     │
│  │  - 数据库: ainovel                                  │     │
│  │  - 管理员: admin/admin123                           │     │
│  └────────────────────────────────────────────────────┘     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## 端口说明

### 对外暴露端口
- **18080**: 主服务端口
  - REST API 服务
  - 主应用前端
  - 管理员面板前端

- **27017**: MongoDB 数据库（开发环境）
  - 生产环境建议不对外暴露

### 内部端口
- 容器间通信通过 Docker 网络

## 应用架构

### 1. 后端服务 (Spring Boot)
- **端口**: 18080
- **API 基础路径**: `/api/v1`
- **静态资源配置**: 
  ```
  spring.web.resources.static-locations=file:/app/web/,file:/app/admin_web/
  ```
- **功能**:
  - RESTful API 服务
  - JWT 认证
  - MongoDB 数据访问
  - 文件上传/下载
  - AI 模型调用

### 2. 主应用前端 (Flutter Web)
- **技术栈**: Flutter Web + Dart
- **构建输出**: `/app/web/`
- **入口文件**: `index.html`
- **API 连接**: 
  - 开发环境: `http://127.0.0.1:18080/api/v1`
  - 生产环境: `/api/v1` (相对路径)
- **功能**:
  - 用户注册/登录
  - 小说创作
  - AI 辅助写作
  - 设定管理
  - 个人中心

### 3. 管理员面板 (Flutter Web)
- **技术栈**: Flutter Web + Dart
- **构建输出**: `/app/admin_web/`
- **入口文件**: `index.html`
- **API 连接**: 
  - 开发环境: `http://127.0.0.1:18080/api/v1`
  - 生产环境: `/api/v1` (相对路径)
- **功能**:
  - 用户管理
  - 内容审核
  - 系统配置
  - 模型管理
  - 数据分析
  - 日志查看

**注意**: 主应用和管理员面板是两个独立编译的 Flutter Web 应用，但它们共享同一个后端 API。

### 4. 数据库 (MongoDB)
- **版本**: MongoDB 8.0
- **模式**: 副本集 (rs0)
- **数据库**: ainovel
- **认证**: 
  - MongoDB 管理员: admin/admin123
  - 应用数据库: ainovel
- **持久化**: Docker Volume
  - mongo-data: 数据文件
  - mongo-config: 配置文件

## 用户账号体系

### 默认管理员账号
- **用户名**: admin
- **密码**: 123456
- **邮箱**: admin@example.com
- **角色**: USER, ADMIN
- **积分**: 999999
- **创建方式**: MongoDB 初始化脚本自动创建

### 用户角色
- **USER**: 普通用户权限
  - 创建和管理自己的小说
  - 使用 AI 功能
  - 查看个人数据

- **ADMIN**: 管理员权限
  - 所有 USER 权限
  - 用户管理
  - 内容审核
  - 系统配置
  - 数据查看

## 访问方式

### 主应用访问
```
URL: http://localhost:18080
登录: 任何注册用户或 admin/123456
```

### 管理员功能访问
**方式一：通过主应用（推荐）**
1. 使用管理员账号登录主应用
2. 在用户菜单中选择"管理后台"或相关选项
3. 进入管理功能模块

**方式二：直接访问管理员面板（需配置）**
- 当前配置下，admin_web 静态文件已部署但未配置独立路由
- 如需独立访问，需要配置 Nginx 反向代理或修改 Spring 路由配置
- 例如: `http://localhost:18080/admin/` -> `/app/admin_web/index.html`

## API 端点示例

### 认证相关
```
POST /api/v1/auth/login          # 用户登录
POST /api/v1/auth/register       # 用户注册
POST /api/v1/auth/refresh        # 刷新token
```

### 管理员相关
```
GET  /api/v1/admin/users         # 用户列表
GET  /api/v1/admin/dashboard     # 管理仪表板
POST /api/v1/admin/users/{id}    # 更新用户
```

### 小说相关
```
GET  /api/v1/novels              # 小说列表
POST /api/v1/novels              # 创建小说
GET  /api/v1/novels/{id}         # 小说详情
```

## 数据持久化

### Docker Volumes
```yaml
volumes:
  mongo-data:       # MongoDB 数据
  mongo-config:     # MongoDB 配置
```

### 数据备份
```bash
# 备份 MongoDB
docker exec ainoval-mongo mongodump \
  -u admin -p admin123 --authenticationDatabase admin \
  --db ainovel --out /backup

# 复制备份文件
docker cp ainoval-mongo:/backup ./mongodb-backup
```

## 环境变量

### 关键环境变量
```bash
# 应用配置
SPRING_PROFILES_ACTIVE=open-dev
JVM_XMS=512m
JVM_XMX=512m

# MongoDB
SPRING_DATA_MONGODB_URI=mongodb://admin:admin123@mongo:27017/ainovel?replicaSet=rs0&authSource=admin

# 番茄小说 API
FANQIE_API_BASE_URL=http://127.0.0.1:5000
FANQIE_API_PUBLIC_URL=http://127.0.0.1:5000

# 静态资源
SPRING_WEB_RESOURCES_STATIC_LOCATIONS=file:/app/web/,file:/app/admin_web/

# 存储
STORAGE_PROVIDER=local

# 代理（可选）
PROXY_ENABLED=false
```

## 安全建议

### 生产环境配置
1. **修改默认密码**
   - MongoDB: admin/admin123 -> 强密码
   - 应用管理员: admin/123456 -> 强密码

2. **使用环境变量**
   - 不要在配置文件中硬编码密码
   - 使用 Docker secrets 或环境变量

3. **网络隔离**
   - MongoDB 不对外暴露（移除 ports 配置）
   - 使用 Docker 内部网络通信

4. **HTTPS 配置**
   - 在生产环境前置 Nginx
   - 配置 SSL/TLS 证书

5. **备份策略**
   - 定期备份 MongoDB 数据
   - 备份到远程存储

## 扩展配置

### Nginx 反向代理示例
```nginx
server {
    listen 80;
    server_name example.com;

    # 主应用
    location / {
        proxy_pass http://localhost:18080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # 管理员面板（独立路由）
    location /admin/ {
        alias /app/admin_web/;
        try_files $uri $uri/ /admin/index.html;
    }

    # API 代理
    location /api/ {
        proxy_pass http://localhost:18080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## 故障排查

### 检查服务状态
```bash
# 查看容器状态
docker ps

# 查看应用日志
docker logs ainoval-server -f

# 查看 MongoDB 日志
docker logs ainoval-mongo -f

# 检查 MongoDB 副本集状态
docker exec ainoval-mongo mongosh -u admin -p admin123 \
  --authenticationDatabase admin --eval "rs.status()"
```

### 常见问题

1. **应用无法连接 MongoDB**
   - 检查副本集状态是否为 PRIMARY
   - 检查 MongoDB 连接字符串
   - 查看防火墙规则

2. **静态资源 404**
   - 检查文件是否存在于容器中
   - 验证 SPRING_WEB_RESOURCES_STATIC_LOCATIONS 配置
   - 查看应用日志

3. **管理员登录失败**
   - 验证管理员账号是否创建
   - 检查密码是否正确
   - 查看用户角色配置

## 相关文档

- `README.md` - 快速开始指南
- `ADMIN_INIT.md` - 管理员账号文档
- `STATIC_RESOURCES_FIX.md` - 静态资源修复文档
- `docker-compose.yml` - Docker Compose 配置
- `Dockerfile` - Docker 镜像构建配置

## 更新记录

- 2025-11-19: 初始版本，完整部署架构说明
