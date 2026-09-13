-- Aspen Auth schema, cross-client refresh sessions and login audit
-- 会话与登录日志归 Auth 所有 (多主体域拆分后的统一权威, 见认证数据模型);
-- 刷新令牌轮换式: 摘要列覆盖旧值, 旧令牌立即作废; 登录日志只追加不修改不删除;
-- 行内主体即操作者, 无独立 by 审计列

CREATE TABLE `auth_session` (
    `session_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `client_kind` VARCHAR(32) NOT NULL,
    `client_code` VARCHAR(64) NOT NULL,
    `principal_id` BIGINT UNSIGNED NOT NULL,
    `refresh_token_hash` VARCHAR(100) NOT NULL,
    `device_id` VARCHAR(64) NULL,
    `ip` VARCHAR(45) NULL,
    `user_agent` VARCHAR(256) NULL,
    `expires_at` DATETIME(3) NOT NULL,
    `revoked_at` DATETIME(3) NULL,
    `revoked_reason` VARCHAR(32) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`session_id`),
    CONSTRAINT `uk_auth_session_refresh_hash` UNIQUE (`refresh_token_hash`),
    INDEX `idx_auth_session_principal` (`client_kind`, `principal_id`)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `auth_login_log` (
    `log_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `client_kind` VARCHAR(32) NOT NULL,
    `client_code` VARCHAR(64) NOT NULL,
    `principal_id` BIGINT UNSIGNED NULL,
    `account` VARCHAR(64) NULL,
    `method` VARCHAR(32) NOT NULL,
    `result` VARCHAR(32) NOT NULL,
    `failure_code` VARCHAR(64) NULL,
    `ip` VARCHAR(45) NULL,
    `user_agent` VARCHAR(256) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`log_id`),
    INDEX `idx_auth_login_log_created` (`created_at`),
    INDEX `idx_auth_login_log_principal` (`client_kind`, `principal_id`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
