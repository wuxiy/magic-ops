-- 切片 37：脚本版本声明目标数据源。
-- 默认值 'default' 保持与既有脚本兼容；PackageBuildService 据此按脚本填充 datasourcePermissions，
-- Runtime 的 Query/Repair 按脚本声明的数据源路由，使灰度查询可指向真实业务库。
ALTER TABLE script_versions ADD COLUMN datasource VARCHAR(100) NOT NULL DEFAULT 'default';
