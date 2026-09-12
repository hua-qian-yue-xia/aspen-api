-- Aspen Task schema, 统一任务服务 (库: aspen_task)
-- 任务定义是平台级调度资产, 全体租户共享, 三张业务表均不做租户隔离;
-- 租户是执行维度: tenant_id 只作普通列与索引, 跨库无法外键 upm_tenant,
-- 租户合法性由投递前的租户解析与目标服务的 fail-closed 过滤器共同保证

CREATE TABLE `task_definition` (
    `definition_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `task_code` VARCHAR(64) NOT NULL,
    `task_name` VARCHAR(100) NOT NULL,
    `description` VARCHAR(500) NULL,
    `trigger_type` VARCHAR(32) NOT NULL,
    `cron_expression` VARCHAR(64) NULL,
    `interval_seconds` INT UNSIGNED NULL,
    `fire_at` DATETIME(3) NULL,
    `timezone_id` VARCHAR(64) NOT NULL,
    `http_method` VARCHAR(16) NOT NULL,
    `target_url` VARCHAR(500) NOT NULL,
    `headers` JSON NULL,
    `body` TEXT NULL,
    `timeout_seconds` INT UNSIGNED NOT NULL DEFAULT 30,
    `max_attempts` INT UNSIGNED NOT NULL DEFAULT 1,
    `backoff_seconds` INT UNSIGNED NOT NULL DEFAULT 60,
    `tenant_scope` VARCHAR(32) NOT NULL DEFAULT 'all',
    `misfire_policy` VARCHAR(32) NOT NULL DEFAULT 'fire_once',
    `concurrent_policy` VARCHAR(32) NOT NULL DEFAULT 'skip',
    `owner_account` VARCHAR(64) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'enabled',
    `version` INT UNSIGNED NOT NULL DEFAULT 1,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_by` VARCHAR(64) NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` VARCHAR(64) NULL,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` VARCHAR(64) NULL,
    PRIMARY KEY (`definition_id`),
    -- 编码永久占用 (对标 sys_route / storage_config), 唯一键不做 IFNULL 折算:
    -- 调用方与执行记录以编码为稳定契约, 逻辑删除行仍被执行记录快照引用
    CONSTRAINT `uk_task_definition_code` UNIQUE (`task_code`),
    INDEX `idx_task_definition_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 指定租户圈定清单: 不可变关系行, 清单变更由 Service 在任务保存事务内整体替换,
-- 任务逻辑删除时随任务物理清理; tenant_id 跨库只作普通列
CREATE TABLE `task_tenant` (
    `task_tenant_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `definition_id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_by` VARCHAR(64) NULL,
    PRIMARY KEY (`task_tenant_id`),
    CONSTRAINT `uk_task_tenant_definition_tenant` UNIQUE (`definition_id`, `tenant_id`),
    CONSTRAINT `fk_task_tenant_definition` FOREIGN KEY (`definition_id`) REFERENCES `task_definition` (`definition_id`) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 逐租户逻辑执行记录: execution_id 字符串主键承载幂等 (同逻辑执行重试不变),
-- 过程数据由保留期治理物理删除, 不声明删除审计防止行膨胀; task_code 为任务
-- 删除后仍可读的快照, 不与定义表建外键
CREATE TABLE `task_execution` (
    `execution_id` VARCHAR(128) NOT NULL,
    `definition_id` BIGINT UNSIGNED NOT NULL,
    `task_code` VARCHAR(64) NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL,
    `trigger_source` VARCHAR(16) NOT NULL,
    `request_id` VARCHAR(64) NULL,
    `fire_time` DATETIME(3) NOT NULL,
    `attempt` INT UNSIGNED NOT NULL DEFAULT 1,
    `status` VARCHAR(16) NOT NULL,
    `failure_kind` VARCHAR(32) NULL,
    `http_status` INT NULL,
    `response_snippet` VARCHAR(2000) NULL,
    `error_message` VARCHAR(1000) NULL,
    `started_at` DATETIME(3) NOT NULL,
    `finished_at` DATETIME(3) NULL,
    `duration_ms` BIGINT UNSIGNED NULL,
    `version` INT UNSIGNED NOT NULL DEFAULT 1,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_by` VARCHAR(64) NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` VARCHAR(64) NULL,
    PRIMARY KEY (`execution_id`),
    -- 管理端按任务翻执行记录的主路径: 按定义 + 计划触发时间倒序
    INDEX `idx_task_execution_definition_fire` (`definition_id`, `fire_time`),
    INDEX `idx_task_execution_tenant` (`tenant_id`),
    -- 按状态筛选的访问路径 (状态等值 + 时间范围); 保留期清理只按 created_at
    -- 单列扫描, 由下方单列索引承载, 该复合索引不作为清理路径 (2026-09-13 审核修正)
    INDEX `idx_task_execution_status_created` (`status`, `created_at`),
    -- 保留期清理的访问路径: 每日按 created_at 升序分批扫描过期行
    INDEX `idx_task_execution_created` (`created_at`),
    -- 人工触发幂等键的预查重路径
    INDEX `idx_task_execution_request` (`request_id`)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
