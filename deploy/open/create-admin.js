// 创建管理员用户
db = db.getSiblingDB('ainovel');

// BCrypt hash for password: 123456
const hashedPassword = '$2a$10$N9qo8uLOickgx2ZMRZoMye1IYrDJWdNHmDgHmBGhiEHOqhGNBRSiG';

const adminUser = {
    username: 'admin',
    password: hashedPassword,
    email: 'admin@ainovel.com',
    roles: ['USER', 'ADMIN'],
    credits: 999999,
    totalCreditsUsed: 0,
    createdAt: new Date(),
    updatedAt: new Date(),
    emailVerified: true
};

// 检查用户是否已存在
const existing = db.users.findOne({username: 'admin'});
if (existing) {
    print('Admin user already exists');
} else {
    db.users.insertOne(adminUser);
    print('Admin user created successfully');
}
