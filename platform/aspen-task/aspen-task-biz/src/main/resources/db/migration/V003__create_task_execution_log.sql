-- Aspen Task schema, 执行过程日志回传
-- 目标服务按步骤经内部契约 POST /internal/task/execution-log 上报;
-- (execution_id, seq) 唯一约束承载回传幂等: 重复上报跳过, 乱序到达不破坏排序;
-- 不与 task_execution 建外键, 两条保留期扫描独立推进, 避免删除顺序耦合

CREATE TABLE `task_execution_log` (
    `log_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `execution_id` VARCHAR(128) NOT NULL,
    `seq` INT UNSIGNED NOT NULL,
    `level` VARCHAR(16) NOT NULL DEFAULT 'info',
    `message` VARCHAR(2000) NOT NULL,
    `logged_at` DATETIME(3) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_by` VARCHAR(64) NULL,
    PRIMARY KEY (`log_id`),
    CONSTRAINT `uk_task_execution_log_execution_seq` UNIQUE (`execution_id`, `seq`),
    -- 保留期清理的访问路径: 每日按 created_at 升序分批扫描过期行 (2026-09-13 审核补充)
    INDEX `idx_task_execution_log_created` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
