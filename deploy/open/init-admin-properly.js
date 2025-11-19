// 初始化管理员角色和用户
db = db.getSiblingDB('ainovel');

// 1. 创建管理员角色
const adminRole = db.roles.findOne({roleName: 'ROLE_ADMIN'});
let adminRoleId;

if (!adminRole) {
    const result = db.roles.insertOne({
        roleName: 'ROLE_ADMIN',
        displayName: '管理员',
        description: '系统管理员，拥有所有权限',
        permissions: [
            'ADMIN_MANAGE_USERS',
            'ADMIN_MANAGE_ROLES',
            'ADMIN_MANAGE_SUBSCRIPTIONS',
            'ADMIN_MANAGE_MODELS',
            'ADMIN_MANAGE_CONFIGS',
            'ADMIN_VIEW_ANALYTICS',
            'ADMIN_MANAGE_CREDITS'
        ],
        enabled: true,
        priority: 100,
        createdAt: new Date(),
        updatedAt: new Date()
    });
    adminRoleId = result.insertedId.toString();
    print('Created ROLE_ADMIN with ID: ' + adminRoleId);
} else {
    adminRoleId = adminRole._id.toString();
    print('ROLE_ADMIN already exists with ID: ' + adminRoleId);
}

// 2. 创建普通用户角色
const userRole = db.roles.findOne({roleName: 'ROLE_USER'});
let userRoleId;

if (!userRole) {
    const result = db.roles.insertOne({
        roleName: 'ROLE_USER',
        displayName: '普通用户',
        description: '标准用户权限',
        permissions: [
            'USER_READ_PROFILE',
            'USER_UPDATE_PROFILE',
            'NOVEL_CREATE',
            'NOVEL_READ',
            'NOVEL_UPDATE',
            'NOVEL_DELETE',
            'SCENE_CREATE',
            'SCENE_READ',
            'SCENE_UPDATE',
            'SCENE_DELETE'
        ],
        enabled: true,
        priority: 1,
        createdAt: new Date(),
        updatedAt: new Date()
    });
    userRoleId = result.insertedId.toString();
    print('Created ROLE_USER with ID: ' + userRoleId);
} else {
    userRoleId = userRole._id.toString();
    print('ROLE_USER already exists with ID: ' + userRoleId);
}

// 3. 创建管理员用户
const existingAdmin = db.users.findOne({username: 'admin'});

if (existingAdmin) {
    print('Admin user already exists');
} else {
    // BCrypt hash for password: 123456
    const hashedPassword = '$2a$10$N9qo8uLOickgx2ZMRZoMye1IYrDJWdNHmDgHmBGhiEHOqhGNBRSiG';
    
    db.users.insertOne({
        username: 'admin',
        password: hashedPassword,
        email: 'admin@ainovel.com',
        displayName: '系统管理员',
        roleIds: [adminRoleId, userRoleId],
        roles: ['ROLE_ADMIN', 'ROLE_USER'],  // 兼容性字段
        credits: 999999,
        totalCreditsUsed: 0,
        accountStatus: 'ACTIVE',
        emailVerified: true,
        phoneVerified: false,
        preferences: {},
        tokenVersion: 1,
        createdAt: new Date(),
        updatedAt: new Date()
    });
    
    print('Admin user created successfully');
    print('Username: admin');
    print('Password: 123456');
    print('Roles: ROLE_ADMIN, ROLE_USER');
    print('Role IDs: ' + adminRoleId + ', ' + userRoleId);
}
