-- Aspen Admin SYS schema, dictionary, dictionary items and runtime parameters
-- 字典是平台引用数据, 不做租户隔离; 参数是租户业务数据, 保持租户列

CREATE TABLE `sys_dict` (
    `dict_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `dict_code` VARCHAR(64) NOT NULL,
    `dict_name` VARCHAR(100) NOT NULL,
    `dict_group` VARCHAR(32) NOT NULL DEFAULT 'common',
    `is_built_in` BOOLEAN NOT NULL DEFAULT FALSE,
    `status` VARCHAR(32) NOT NULL DEFAULT 'enabled',
    `version` INT UNSIGNED NOT NULL DEFAULT 1,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_by` VARCHAR(64) NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` VARCHAR(64) NULL,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` VARCHAR(64) NULL,
    PRIMARY KEY (`dict_id`),
    CONSTRAINT `uk_sys_dict_code` UNIQUE (`dict_code`)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `sys_dict_item` (
    `dict_item_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `dict_id` BIGINT UNSIGNED NOT NULL,
    `parent_id` BIGINT UNSIGNED NULL,
    `item_label` VARCHAR(200) NOT NULL,
    `item_value` VARCHAR(200) NOT NULL,
    `is_default` BOOLEAN NOT NULL DEFAULT FALSE,
    `color` VARCHAR(32) NULL,
    `css_class` VARCHAR(100) NULL,
    `sort_order` INT UNSIGNED NOT NULL DEFAULT 0,
    `status` VARCHAR(32) NOT NULL DEFAULT 'enabled',
    `version` INT UNSIGNED NOT NULL DEFAULT 1,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_by` VARCHAR(64) NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` VARCHAR(64) NULL,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` VARCHAR(64) NULL,
    PRIMARY KEY (`dict_item_id`),
    CONSTRAINT `fk_sys_dict_item_dict` FOREIGN KEY (`dict_id`) REFERENCES `sys_dict` (`dict_id`) ON DELETE CASCADE,
    CONSTRAINT `uk_sys_dict_item_dict_value` UNIQUE (`dict_id`, `item_value`),
    INDEX `idx_sys_dict_item_dict_sort` (`dict_id`, `sort_order`),
    INDEX `idx_sys_dict_item_parent` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `sys_config` (
    `config_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT UNSIGNED NOT NULL,
    `config_key` VARCHAR(100) NOT NULL,
    `config_name` VARCHAR(200) NOT NULL,
    `config_value` VARCHAR(2000) NOT NULL,
    `value_type` VARCHAR(32) NOT NULL DEFAULT 'string',
    `is_built_in` BOOLEAN NOT NULL DEFAULT FALSE,
    `is_sensitive` BOOLEAN NOT NULL DEFAULT FALSE,
    `status` VARCHAR(32) NOT NULL DEFAULT 'enabled',
    `version` INT UNSIGNED NOT NULL DEFAULT 1,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_by` VARCHAR(64) NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` VARCHAR(64) NULL,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` VARCHAR(64) NULL,
    PRIMARY KEY (`config_id`),
    CONSTRAINT `fk_sys_config_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `upm_tenant` (`tenant_id`) ON DELETE RESTRICT,
    CONSTRAINT `uk_sys_config_tenant_key` UNIQUE (`tenant_id`, `config_key`),
    INDEX `idx_sys_config_tenant_status` (`tenant_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
