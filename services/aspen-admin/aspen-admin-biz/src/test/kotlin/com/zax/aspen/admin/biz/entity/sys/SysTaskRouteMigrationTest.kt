package com.zax.aspen.admin.biz.entity.sys

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 验证统一任务服务的路由种子迁移
 *
 * V005 以「具体前缀靠前、兜底靠后」的排序实现 /admin-api/task 路径前缀优先命中
 * aspen-task-biz, 其余 /admin-api 前缀流量落回 aspen-admin-biz; 断言覆盖排序抬升、
 * 任务路由的路径断言、目标 uri 与原样转发过滤器
 */
class SysTaskRouteMigrationTest {
    /** 验证兜底路由排序抬升且任务路由以更小排序插入 */
    @Test
    fun `raises admin fallback order and seeds task route ahead`() {
        assertTrue(
            Regex("SET `sort_order` = 100").containsMatchIn(migrationSql),
            "admin 兜底路由应抬升至 sort_order 100",
        )
        assertTrue(
            Regex("10, 'enabled', 'system:route-seed'").containsMatchIn(migrationSql),
            "任务路由应以 sort_order 10 插入, 优先于兜底路由",
        )
    }

    /** 验证任务路由指向 /admin-api/task 路径前缀、lb 目标与空过滤器原样转发 */
    @Test
    fun `task route targets admin-api task prefix without strip prefix`() {
        assertTrue(migrationSql.contains("'aspen-task', '统一任务服务'"), "缺少 aspen-task 路由编码")
        assertTrue(migrationSql.contains("'lb://aspen-task-biz'"), "任务路由应指向 Nacos 注册名 aspen-task-biz")
        assertTrue(migrationSql.contains("/admin-api/task/**"), "任务路由路径断言应为 /admin-api/task 前缀")
        assertTrue(migrationSql.contains("'\\[\\]'") || migrationSql.contains("'[]'"), "任务路由不应携带 StripPrefix, 过滤器为空数组")
    }

    /** 从测试类路径读取 V005 路由种子迁移脚本 */
    private val migrationSql: String by lazy {
        val resource = assertNotNull(javaClass.getResourceAsStream(MIGRATION_RESOURCE))
        resource.bufferedReader().use { it.readText() }
    }

    /** 保存测试使用的资源常量 */
    private companion object {
        /** 统一任务服务路由种子的迁移版本 */
        const val MIGRATION_RESOURCE = "/db/migration/sys/V005__create_task_route.sql"
    }
}
