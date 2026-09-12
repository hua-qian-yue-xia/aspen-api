-- Aspen Admin SYS schema, align gateway seed route with audience path prefix
-- 服务侧已由 aspen-common-web 按 Controller 受众包 (controller/admin|app) 统一挂
-- /admin-api、/app-api 前缀, 网关改为原样转发、不再剥前段, 种子路由同步对齐;
-- 同时把目标服务名修正为 Nacos 注册名 (spring.application.name = aspen-admin-biz)
-- 注意: 首个 admin 受众 Controller 落地前, /admin-api/** 是空壳锚点路由——网关可
-- 转发但服务侧暂无可命中映射 (外部调用 404), 属预期而非路由失效; 原经 /admin/sys/route
-- 暴露的 SysRouteController 已迁 controller/internal/sys, 按内端定位不再经网关暴露

UPDATE `sys_route`
SET `uri`        = 'lb://aspen-admin-biz',
    `predicates` = '[{"name":"Path","args":{"_genkey_0":"/admin-api/**"}}]',
    `filters`    = '[]',
    `updated_at` = CURRENT_TIMESTAMP(3),
    `updated_by` = 'system:route-seed'
WHERE `route_code` = 'aspen-admin';
