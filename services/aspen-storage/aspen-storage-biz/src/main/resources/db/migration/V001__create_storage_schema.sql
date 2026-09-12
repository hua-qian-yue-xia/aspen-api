-- Aspen Storage schema, 微服务文件存储 (库: aspen_storage)
-- storage_config 是平台基础设施配置, 全体租户共用; 其余表租户隔离;
-- Storage 与 Admin 分库, tenant_id 无法跨库外键 upm_tenant, 只作普通列与索引前缀

CREATE TABLE `storage_config` (
    `config_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `config_code` VARCHAR(64) NOT NULL,
    `config_name` VARCHAR(100) NOT NULL,
    `storage_type` VARCHAR(32) NOT NULL,
    `params` JSON NOT NULL,
    `is_default` BOOLEAN NOT NULL DEFAULT FALSE,
    `status` VARCHAR(32) NOT NULL DEFAULT 'enabled',
    `version` INT UNSIGNED NOT NULL DEFAULT 1,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_by` VARCHAR(64) NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` VARCHAR(64) NULL,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` VARCHAR(64) NULL,
    PRIMARY KEY (`config_id`),
    -- 编码永久占用 (对标 sys_route), 唯一键不做 IFNULL 折算: 调用方以编码为稳定契约,
    -- 逻辑删除行仍被 storage_file.config_id 引用, 删除后同码重建会造成删除行与活跃行同码二义
    CONSTRAINT `uk_storage_config_code` UNIQUE (`config_code`)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 文件业务分类树: 邻接表 parent_id 自引用, 不设数据库外键 (照 sys_dict_item 惯例,
-- 成环与跨租户挂父由 Service 维护); 子树查询走 (tenant_id, parent_id) 索引加递归 CTE
CREATE TABLE `storage_category` (
    `category_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT UNSIGNED NOT NULL,
    `parent_id` BIGINT UNSIGNED NULL,
    `category_code` VARCHAR(64) NOT NULL,
    `category_name` VARCHAR(100) NOT NULL,
    `sort_order` INT UNSIGNED NOT NULL DEFAULT 0,
    `status` VARCHAR(32) NOT NULL DEFAULT 'enabled',
    `version` INT UNSIGNED NOT NULL DEFAULT 1,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_by` VARCHAR(64) NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` VARCHAR(64) NULL,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` VARCHAR(64) NULL,
    PRIMARY KEY (`category_id`),
    INDEX `idx_storage_category_tenant_parent` (`tenant_id`, `parent_id`),
    -- 分类建删重建是常态, 唯一键语义与 storage_file 同款: IFNULL 把活跃行折算哨兵值
    -- 后真正受数据库约束, 已删除行按毫秒时间戳参与键值不阻塞同码重建
    UNIQUE KEY `uk_storage_category_tenant_code` (`tenant_id`, `category_code`, (IFNULL(`deleted_at`, '1970-01-01 00:00:00.000')))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `storage_file` (
    `file_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT UNSIGNED NOT NULL,
    `config_id` BIGINT UNSIGNED NOT NULL,
    `category_id` BIGINT UNSIGNED NULL,
    `original_name` VARCHAR(512) NOT NULL,
    `storage_key` VARCHAR(512) NOT NULL,
    `url` VARCHAR(1024) NOT NULL,
    `mime_type` VARCHAR(128) NOT NULL,
    `file_size` BIGINT UNSIGNED NOT NULL,
    `sha256` CHAR(64) NOT NULL,
    `file_type` VARCHAR(32) NOT NULL,
    `version` INT UNSIGNED NOT NULL DEFAULT 1,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_by` VARCHAR(64) NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` VARCHAR(64) NULL,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` VARCHAR(64) NULL,
    PRIMARY KEY (`file_id`),
    CONSTRAINT `fk_storage_file_config` FOREIGN KEY (`config_id`) REFERENCES `storage_config` (`config_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_storage_file_category` FOREIGN KEY (`category_id`) REFERENCES `storage_category` (`category_id`) ON DELETE RESTRICT,
    INDEX `idx_storage_file_config` (`config_id`),
    INDEX `idx_storage_file_tenant_category` (`tenant_id`, `category_id`),
    -- 物理对象回收的访问路径: storage_key 跨租户共享物理对象, 回收前的「同 key 活跃记录
    -- 计数」按系统任务跨全租户查询 WHERE storage_key = ? AND deleted_at IS NULL, 单列索引支撑
    INDEX `idx_storage_file_storage_key` (`storage_key`),
    -- MySQL 唯一索引视 NULL 为互不相等, 活跃行 (deleted_at IS NULL) 直接入键不受约束;
    -- 以 IFNULL 折算哨兵值后活跃行之间真正受数据库保护, 并发同传同内容被拒转秒传命中,
    -- 已删除行按毫秒时间戳参与键值不阻塞重传 (表达式索引要求 MySQL >= 8.0.13)
    UNIQUE KEY `uk_storage_file_dedup` (`tenant_id`, `sha256`, `config_id`, (IFNULL(`deleted_at`, '1970-01-01 00:00:00.000')))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 任务是过程数据: 只建不删审计列, 过期未完成任务由清理任务物理删除 (连同分片)
CREATE TABLE `storage_upload_task` (
    `upload_task_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT UNSIGNED NOT NULL,
    `config_id` BIGINT UNSIGNED NOT NULL,
    `original_name` VARCHAR(512) NOT NULL,
    `mime_type` VARCHAR(128) NULL,
    `total_size` BIGINT UNSIGNED NOT NULL,
    `chunk_size` INT UNSIGNED NOT NULL,
    `total_chunks` INT UNSIGNED NOT NULL,
    `file_sha256` CHAR(64) NOT NULL,
    `remote_upload_id` VARCHAR(255) NULL,
    `task_status` VARCHAR(32) NOT NULL DEFAULT 'uploading',
    `expires_at` DATETIME(3) NULL,
    `version` INT UNSIGNED NOT NULL DEFAULT 1,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_by` VARCHAR(64) NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` VARCHAR(64) NULL,
    PRIMARY KEY (`upload_task_id`),
    CONSTRAINT `fk_storage_upload_task_config` FOREIGN KEY (`config_id`) REFERENCES `storage_config` (`config_id`) ON DELETE RESTRICT,
    -- 清理是跨租户系统级补偿扫描 (架构 14.1.2), 命令不携带租户等值条件, 按系统上下文
    -- 分页扫描 task_status IN (...) AND expires_at < now, 索引以状态与过期时间打头不含租户前缀
    INDEX `idx_storage_upload_task_cleanup` (`task_status`, `expires_at`)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `storage_upload_chunk` (
    `upload_chunk_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT UNSIGNED NOT NULL,
    `upload_task_id` BIGINT UNSIGNED NOT NULL,
    `chunk_number` INT UNSIGNED NOT NULL,
    `chunk_size` INT UNSIGNED NOT NULL,
    `chunk_sha256` CHAR(64) NULL,
    `remote_part_tag` VARCHAR(255) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_by` VARCHAR(64) NULL,
    PRIMARY KEY (`upload_chunk_id`),
    CONSTRAINT `fk_storage_upload_chunk_task` FOREIGN KEY (`upload_task_id`) REFERENCES `storage_upload_task` (`upload_task_id`) ON DELETE CASCADE,
    CONSTRAINT `uk_storage_upload_chunk_task_number` UNIQUE (`upload_task_id`, `chunk_number`)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
