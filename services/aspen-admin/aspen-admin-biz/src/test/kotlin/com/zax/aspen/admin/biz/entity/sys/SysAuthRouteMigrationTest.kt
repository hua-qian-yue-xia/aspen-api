package com.zax.aspen.admin.biz.entity.sys

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 验证认证服务的路由种子迁移
 *
 * V007 为 aspen-auth-biz 建立 /admin-api/auth 与 /app-api/auth 两条受众前缀路由:
 * 排序 5 先于 admin 兜底路由 (100) 与任务路由 (10), 空过滤器原样转发 (无 StripPrefix,
 * common-web 已在认证服务侧挂好前缀)
 */
class SysAuthRouteMigrationTest {
    /** 验证两条认证路由以优先排序插入且指向 aspen-auth-biz */
    @Test
    fun `seeds auth routes ahead of fallback with lb target`() {
        assertTrue(migrationSql.contains("'aspen-auth-admin', '认证服务管理端'"), "缺少认证管理端路由")
        assertTrue(migrationSql.contains("'aspen-auth-app', '认证服务应用端'"), "缺少认证应用端路由")
        assertTrue(migrationSql.contains("'lb://aspen-auth-biz'"), "认证路由应指向 Nacos 注册名 aspen-auth-biz")
        assertTrue(
            Regex("5, 'enabled', 'system:route-seed'").containsMatchIn(migrationSql),
            "认证路由应以 sort_order 5 插入, 优先于兜底与任务路由",
        )
    }

    /** 验证认证路由路径断言覆盖两个受众前缀且不带 StripPrefix */
    @Test
    fun `auth routes cover audience prefixes without strip prefix`() {
        assertTrue(migrationSql.contains("/admin-api/auth/**"), "缺少管理端认证前缀断言")
        assertTrue(migrationSql.contains("/app-api/auth/**"), "缺少应用端认证前缀断言")
        assertTrue(
            !migrationSql.contains("StripPrefix"),
            "认证路由不应携带 StripPrefix, common-web 前缀在服务侧已就位",
        )
    }

    /** 从测试类路径读取 V007 认证路由种子迁移脚本 */
    private val migrationSql: String by lazy {
        val resource = assertNotNull(javaClass.getResourceAsStream(MIGRATION_RESOURCE))
        resource.bufferedReader().use { it.readText() }
    }

    /** 保存测试使用的资源常量 */
    private companion object {
        /** 认证路由种子的迁移版本 */
        const val MIGRATION_RESOURCE = "/db/migration/sys/V007__create_auth_route.sql"
    }
}
