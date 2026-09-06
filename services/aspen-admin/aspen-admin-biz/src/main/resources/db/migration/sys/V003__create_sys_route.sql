-- Aspen Admin SYS schema, gateway dynamic routes
-- 路由是平台基础设施配置, 全体租户共用, 不做租户隔离; 权威源是本表, Redis 只是分发介质

CREATE TABLE `sys_route` (
    `route_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `route_code` VARCHAR(64) NOT NULL,
    `route_name` VARCHAR(100) NOT NULL,
    `uri` VARCHAR(255) NOT NULL,
    `predicates` JSON NOT NULL,
    `filters` JSON NOT NULL,
    `metadata` JSON NULL,
    `sort_order` INT UNSIGNED NOT NULL DEFAULT 0,
    `status` VARCHAR(32) NOT NULL DEFAULT 'enabled',
    `version` INT UNSIGNED NOT NULL DEFAULT 1,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_by` VARCHAR(64) NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` VARCHAR(64) NULL,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` VARCHAR(64) NULL,
    PRIMARY KEY (`route_id`),
    CONSTRAINT `uk_sys_route_code` UNIQUE (`route_code`),
    INDEX `idx_sys_route_status_sort` (`status`, `sort_order`)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 自举种子: Gateway 到 Admin 的首条路由无法经路由表自身发布, 由种子数据保证;
-- /admin/** 为 Admin 未来对外管理 API 的路径前缀, 网关剥离前段后转发服务本体
INSERT INTO `sys_route`
    (`route_code`, `route_name`, `uri`, `predicates`, `filters`, `metadata`, `sort_order`, `status`, `created_by`, `updated_by`)
VALUES
    ('aspen-admin', 'Admin 管理服务', 'lb://aspen-admin',
     '[{"name":"Path","args":{"_genkey_0":"/admin/**"}}]',
     '[{"name":"StripPrefix","args":{"_genkey_0":"1"}}]',
     NULL,
     0, 'enabled', 'system:route-seed', 'system:route-seed');
