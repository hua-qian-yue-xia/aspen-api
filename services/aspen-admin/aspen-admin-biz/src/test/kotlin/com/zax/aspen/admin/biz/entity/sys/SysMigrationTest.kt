package com.zax.aspen.admin.biz.entity.sys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 验证 SYS 迁移脚本覆盖完整表集和关键数据库约束
 */
class SysMigrationTest {
    /** 验证迁移只创建 3 张 SYS 表并使用统一字符集 */
    @Test
    fun `creates the complete sys schema`() {
        val tableNames = Regex("CREATE TABLE `([^`]+)`", RegexOption.IGNORE_CASE)
            .findAll(migrationSql)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(listOf("sys_dict", "sys_dict_item", "sys_config"), tableNames)
        assertEquals(3, Regex("COLLATE = utf8mb4_0900_ai_ci", RegexOption.IGNORE_CASE).findAll(migrationSql).count())
    }

    /** 验证字典全局唯一约束、租户参数唯一约束、排序索引和外键删除策略存在 */
    @Test
    fun `keeps tenant and relationship constraints`() {
        listOf(
            "uk_sys_dict_code",
            "uk_sys_dict_item_dict_value",
            "uk_sys_config_tenant_key",
            "idx_sys_dict_item_dict_sort",
            "idx_sys_dict_item_parent",
        ).forEach { constraintName ->
            assertTrue(migrationSql.contains("`$constraintName`"), "缺少 $constraintName")
        }

        assertTrue(migrationSql.contains("ON DELETE RESTRICT", ignoreCase = true))
        assertTrue(migrationSql.contains("ON DELETE CASCADE", ignoreCase = true))
    }

    /** 从测试类路径读取版本化 SYS 迁移脚本 */
    private val migrationSql: String by lazy {
        val resource = assertNotNull(javaClass.getResourceAsStream(MIGRATION_RESOURCE))
        resource.bufferedReader().use { it.readText() }
    }

    /** 保存测试使用的资源常量 */
    private companion object {
        /** SYS 初始 Schema 的全局 Admin 迁移版本 */
        const val MIGRATION_RESOURCE = "/db/migration/sys/V002__create_sys_schema.sql"
    }
}
