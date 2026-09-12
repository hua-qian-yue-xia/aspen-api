-- Aspen Admin SYS schema, align gateway seed route with audience path prefix
-- 服务侧已由 aspen-common-web 按 Controller 受众包 (controller/admin|app) 统一挂
-- /admin-api、/app-api 前缀, 网关改为原样转发、不再剥前段, 种子路由同步对齐;
-- 同时把目标服务名修正为 Nacos 注册名 (spring.application.name = aspen-admin-biz)

UPDATE `sys_route`
SET `uri`        = 'lb://aspen-admin-biz',
    `predicates` = '[{"name":"Path","args":{"_genkey_0":"/admin-api/**"}}]',
    `filters`    = '[]',
    `updated_at` = CURRENT_TIMESTAMP(3),
    `updated_by` = 'system:route-seed'
WHERE `route_code` = 'aspen-admin';
