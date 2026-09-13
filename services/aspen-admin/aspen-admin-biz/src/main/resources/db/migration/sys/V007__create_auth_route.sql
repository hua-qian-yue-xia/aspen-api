-- Aspen Admin SYS schema, auth service gateway routes
-- 认证服务的受众前缀路由: /admin-api/auth 与 /app-api/auth 均指向 aspen-auth-biz,
-- 排序先于 admin 兜底路由 (sort_order 5 < 100), 空过滤器原样转发 (前缀由 common-web 在服务侧挂载)

INSERT INTO `sys_route`
    (`route_code`, `route_name`, `uri`, `predicates`, `filters`, `metadata`, `sort_order`, `status`, `created_by`, `updated_by`)
VALUES
    ('aspen-auth-admin', '认证服务管理端', 'lb://aspen-auth-biz',
     '[{"name":"Path","args":{"_genkey_0":"/admin-api/auth/**"}}]',
     '[]',
     NULL,
     5, 'enabled', 'system:route-seed', 'system:route-seed'),
    ('aspen-auth-app', '认证服务应用端', 'lb://aspen-auth-biz',
     '[{"name":"Path","args":{"_genkey_0":"/app-api/auth/**"}}]',
     '[]',
     NULL,
     5, 'enabled', 'system:route-seed', 'system:route-seed');
