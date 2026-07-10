-- MagicOps 角色-权限分配
-- 切片 8：RBAC 权限控制
-- 为预置角色分配权限

-- PLATFORM_ADMIN: 全部权限
INSERT INTO role_permissions (role_id, permission_id, granted_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r, permissions p
WHERE r.name = 'PLATFORM_ADMIN';

-- PROJECT_ADMIN: 项目/脚本/数据源/HTTP 目标管理
INSERT INTO role_permissions (role_id, permission_id, granted_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r, permissions p
WHERE r.name = 'PROJECT_ADMIN'
AND p.code IN (
    'script:create', 'script:edit', 'script:debug', 'script:submit',
    'script:approve', 'script:publish',
    'datasource:manage', 'datasource:query',
    'http_target:manage',
    'audit:read', 'project:manage'
);

-- DEVELOPER: 脚本编辑/调试/提交 + 数据源查询
INSERT INTO role_permissions (role_id, permission_id, granted_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r, permissions p
WHERE r.name = 'DEVELOPER'
AND p.code IN (
    'script:create', 'script:edit', 'script:debug', 'script:submit',
    'datasource:query',
    'audit:read'
);

-- APPROVER: 审批 + 查看审计
INSERT INTO role_permissions (role_id, permission_id, granted_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r, permissions p
WHERE r.name = 'APPROVER'
AND p.code IN (
    'script:approve', 'script:publish', 'script:rollback',
    'audit:read', 'audit:export'
);

-- OPERATOR: 查看审计 + 数据源查询
INSERT INTO role_permissions (role_id, permission_id, granted_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r, permissions p
WHERE r.name = 'OPERATOR'
AND p.code IN (
    'audit:read', 'datasource:query'
);

-- AUDITOR: 审计读取/导出
INSERT INTO role_permissions (role_id, permission_id, granted_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r, permissions p
WHERE r.name = 'AUDITOR'
AND p.code IN (
    'audit:read', 'audit:export'
);

-- OBSERVER: 审计只读
INSERT INTO role_permissions (role_id, permission_id, granted_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r, permissions p
WHERE r.name = 'OBSERVER'
AND p.code IN (
    'audit:read'
);
