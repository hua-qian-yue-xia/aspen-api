-- Aspen Admin SYS schema, unified auth client registry
-- 端注册与端×登录方式策略是平台级配置 (全体租户共用, 不做租户隔离);
-- 权威源是这两张表, Redis 只是可随时全量重建的分发介质 (common-security 契约);
-- 密钥摘要只存不可逆哈希, 种子不携带任何可用凭据字面量

CREATE TABLE `sys_auth_client` (
    `auth_client_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `client_code` VARCHAR(64) NOT NULL,
    `client_name` VARCHAR(100) NOT NULL,
    `client_kind` VARCHAR(32) NOT NULL,
    `secret_hash` VARCHAR(100) NULL,
    `hash_algorithm` VARCHAR(32) NULL,
    `access_token_ttl_seconds` INT UNSIGNED NULL,
    `refresh_token_ttl_seconds` INT UNSIGNED NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'enabled',
    `version` INT UNSIGNED NOT NULL DEFAULT 1,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_by` VARCHAR(64) NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `updated_by` VARCHAR(64) NULL,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` VARCHAR(64) NULL,
    PRIMARY KEY (`auth_client_id`),
    CONSTRAINT `uk_sys_auth_client_code` UNIQUE (`client_code`),
    INDEX `idx_sys_auth_client_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `sys_auth_login_method` (
    `auth_login_method_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `auth_client_id` BIGINT UNSIGNED NOT NULL,
    `method` VARCHAR(32) NOT NULL,
    `captcha_kind` VARCHAR(32) NOT NULL DEFAULT 'none',
    `force_change_on_first_login` BOOLEAN NOT NULL DEFAULT FALSE,
    `password_max_age_days` INT UNSIGNED NULL,
    `config` JSON NULL,
    `sort_order` INT UNSIGNED NOT NULL DEFAULT 0,
    `status` VARCHAR(32) NOT NULL DEFAULT 'enabled',
    `version` INT UNSIGNED NOT NULL DEFAULT 1,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_by` VARCHAR(64) NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `updated_by` VARCHAR(64) NULL,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` VARCHAR(64) NULL,
    PRIMARY KEY (`auth_login_method_id`),
    CONSTRAINT `uk_sys_auth_login_method_client_method` UNIQUE (`auth_client_id`, `method`),
    CONSTRAINT `fk_sys_auth_login_method_client` FOREIGN KEY (`auth_client_id`) REFERENCES `sys_auth_client` (`auth_client_id`) ON DELETE CASCADE,
    INDEX `idx_sys_auth_login_method_status_sort` (`status`, `sort_order`)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 自举种子: 管理端 Web 端注册 + 账号密码登录方式 (三方行为验证码闸门, 首登强制改密, 密码 90 天有效期);
-- 只种 v1 真实现的行, 没有代码支撑的登录方式不预填
INSERT INTO `sys_auth_client`
    (`client_code`, `client_name`, `client_kind`, `status`, `created_by`, `updated_by`)
VALUES
    ('aspen-admin-web', '管理端 Web', 'admin', 'enabled', 'system:auth-seed', 'system:auth-seed');

INSERT INTO `sys_auth_login_method`
    (`auth_client_id`, `method`, `captcha_kind`, `force_change_on_first_login`, `password_max_age_days`, `sort_order`, `status`, `created_by`, `updated_by`)
SELECT `auth_client_id`, 'password', 'slider', TRUE, 90, 0, 'enabled', 'system:auth-seed', 'system:auth-seed'
FROM `sys_auth_client`
WHERE `client_code` = 'aspen-admin-web';
