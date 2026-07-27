-- magic-api 脚本资源存储表
-- magic-api 编辑器以 database 模式存储 API 脚本、函数、数据源等资源

CREATE TABLE magic_api_file (
    file_path    VARCHAR(512)  NOT NULL,
    file_content TEXT,
    create_time  BIGINT        DEFAULT 0,
    modify_time  BIGINT        DEFAULT 0,
    PRIMARY KEY (file_path)
);

-- magic-api 备份表（可选，用于脚本历史备份）
CREATE TABLE magic_api_file_backup (
    file_path    VARCHAR(512)  NOT NULL,
    file_content TEXT,
    create_time  BIGINT        DEFAULT 0,
    modify_time  BIGINT        DEFAULT 0,
    PRIMARY KEY (file_path)
);
