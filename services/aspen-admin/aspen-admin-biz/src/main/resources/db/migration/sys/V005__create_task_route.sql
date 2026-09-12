-- Aspen Admin SYS schema, seed unified task service route & raise admin fallback order
-- Task 管理面挂在 /admin-api/task/** (common-web 受众前缀 + 网关原样转发, 对齐 V004 惯例);
-- sys_route.sort_order 为 INT UNSIGNED 无法取负, 采用「具体前缀路由靠前 (10)、
-- 兜底路由靠后 (100)」实现更具体路径优先匹配: /admin-api/task/** 命中 aspen-task,
-- 其余 /admin-api/** 落回 aspen-admin-biz

UPDATE `sys_route`
SET `sort_order` = 100,
    `updated_at` = CURRENT_TIMESTAMP(3),
    `updated_by` = 'system:route-seed'
WHERE `route_code` = 'aspen-admin';

INSERT INTO `sys_route`
    (`route_code`, `route_name`, `uri`, `predicates`, `filters`, `metadata`, `sort_order`, `status`, `created_by`, `updated_by`)
VALUES
    ('aspen-task', '统一任务服务', 'lb://aspen-task-biz',
     '[{"name":"Path","args":{"_genkey_0":"/admin-api/task/**"}}]',
     '[]',
     NULL,
     10, 'enabled', 'system:route-seed', 'system:route-seed');
