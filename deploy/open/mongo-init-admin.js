// MongoDB管理员用户初始化脚本
// 在副本集初始化后自动执行，创建默认管理员账号

print("开始初始化管理员用户...");

// 切换到ainovel数据库
db = db.getSiblingDB('ainovel');

// BCrypt哈希密码 "123456"，强度10
// 生成方式: bcrypt.hashSync("123456", 10)
const passwordHash = "$2a$10$N9qo8uLOickgx2ZMRZoMye1IYrDJWdNHmDgHmBGhiEHOqhGNBRSiG";

const now = new Date();
const adminUserId = "691c991e5e021f43c6274f00";  // 固定管理员ID

// 管理员用户文档
const adminUser = {
    _id: adminUserId,
    username: "admin",
    password: passwordHash,  // Spring Security使用 'password' 字段
    email: "admin@example.com",
    emailVerified: true,
    phoneVerified: false,
    phone: null,
    displayName: "系统管理员",
    avatar: null,
    roleIds: [],
    roles: ["USER", "ADMIN"],  // Spring Security角色
    credits: 999999,
    totalCreditsUsed: 0,
    currentSubscriptionId: null,
    accountStatus: "ACTIVE",
    preferences: {},
    createdAt: now,
    updatedAt: now,
    lastLoginAt: null,
    _class: "com.ainovel.server.domain.model.User"
};

try {
    // 检查管理员是否已存在
    const existing = db.users.findOne({ username: "admin" });
    
    if (existing) {
        print("✓ 管理员用户已存在，跳过创建");
    } else {
        // 插入管理员用户
        db.users.insertOne(adminUser);
        print("✓ 管理员用户创建成功");
        print("  用户名: admin");
        print("  密码: 123456");
        print("  角色: USER, ADMIN");
        print("  积分: 999999");
    }
} catch (e) {
    print("✗ 管理员用户创建失败:", e);
}

print("管理员用户初始化完成");
