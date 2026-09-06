package com.zax.aspen.admin.biz.entity.upm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 验证 UPM 迁移脚本覆盖完整表集和关键数据库约束
 */
class UpmMigrationTest {
    /** 验证迁移只创建 24 张 UPM 表并使用统一字符集 */
    @Test
    fun `creates the complete upm schema`() {
        val tableNames = Regex("CREATE TABLE `([^`]+)`", RegexOption.IGNORE_CASE)
            .findAll(migrationSql)
            .map { it.groupValues[1] }
            .toList()
        val columnNames = Regex("^\\s*`([^`]+)`\\s+", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
            .findAll(migrationSql)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(24, tableNames.size)
        assertTrue(tableNames.all { it.startsWith("upm_") })
        assertTrue((tableNames + columnNames).none { it.contains("department", ignoreCase = true) })
        assertFalse(columnNames.any { it.equals("display_name", ignoreCase = true) })
        assertEquals(24, Regex("COLLATE = utf8mb4_0900_ai_ci", RegexOption.IGNORE_CASE).findAll(migrationSql).count())
    }

    /** 验证租户唯一约束、闭包查询索引和外键删除策略存在 */
    @Test
    fun `keeps tenant and relationship constraints`() {
        listOf(
            "uk_upm_user_tenant_username",
            "uk_upm_dept_tenant_code",
            "uk_upm_role_tenant_code",
            "uk_upm_menu_tenant_platform_code",
            "uk_upm_permission_code",
            "idx_upm_dept_closure_desc_depth",
            "idx_upm_role_inheritance_child",
        ).forEach { constraintName ->
            assertTrue(migrationSql.contains("`$constraintName`"), "缺少 $constraintName")
        }

        assertTrue(migrationSql.contains("ON DELETE RESTRICT", ignoreCase = true))
        assertTrue(migrationSql.contains("ON DELETE CASCADE", ignoreCase = true))
        assertTrue(migrationSql.contains("ON DELETE SET NULL", ignoreCase = true))
    }

    /** 从测试类路径读取版本化 UPM 迁移脚本 */
    private val migrationSql: String by lazy {
        val resource = assertNotNull(javaClass.getResourceAsStream(MIGRATION_RESOURCE))
        resource.bufferedReader().use { it.readText() }
    }

    /** 保存测试使用的资源常量 */
    private companion object {
        /** UPM 初始 Schema 的全局 Admin 迁移版本 */
        const val MIGRATION_RESOURCE = "/db/migration/upm/V001__create_upm_schema.sql"
    }
}
