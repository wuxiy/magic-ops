-- MagicOps 诊断中心权限
-- Arthas 线上诊断属于受控高危能力，权限按角色最小化授予

-- 诊断权限定义
INSERT INTO permissions (code, display_name, description, resource_type, category) VALUES
    ('diagnosis:session:create', '诊断会话', '创建、查看、关闭诊断会话并执行诊断命令', 'instance', 'diagnosis'),
    ('diagnosis:template:manage', '诊断模板管理', '注册和管理诊断命令模板', 'global', 'diagnosis');

-- PLATFORM_ADMIN: 全部诊断权限
INSERT INTO role_permissions (role_id, permission_id, granted_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r, permissions p
WHERE r.name = 'PLATFORM_ADMIN'
AND p.code IN ('diagnosis:session:create', 'diagnosis:template:manage');

-- OPERATOR: 诊断会话（执行受控诊断任务），不含模板管理
INSERT INTO role_permissions (role_id, permission_id, granted_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r, permissions p
WHERE r.name = 'OPERATOR'
AND p.code IN ('diagnosis:session:create');
