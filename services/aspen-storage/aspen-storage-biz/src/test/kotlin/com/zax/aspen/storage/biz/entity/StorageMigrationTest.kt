package com.zax.aspen.storage.biz.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 验证 Storage 迁移脚本覆盖完整表集和关键数据库约束
 */
class StorageMigrationTest {
    /** 验证迁移按外键依赖顺序创建 5 张 Storage 表并使用统一字符集 */
    @Test
    fun `creates the complete storage schema`() {
        val tableNames = Regex("CREATE TABLE `([^`]+)`", RegexOption.IGNORE_CASE)
            .findAll(migrationSql)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(
            listOf(
                "storage_config",
                "storage_category",
                "storage_file",
                "storage_upload_task",
                "storage_upload_chunk",
            ),
            tableNames,
        )
        assertEquals(5, Regex("COLLATE = utf8mb4_0900_ai_ci", RegexOption.IGNORE_CASE).findAll(migrationSql).count())
    }

    /** 验证秒传唯一键、分类编码键、续传唯一键、外键删除策略与治理索引存在 */
    @Test
    fun `keeps dedup category resume and relationship constraints`() {
        listOf(
            "uk_storage_config_code",
            "uk_storage_category_tenant_code",
            "uk_storage_file_dedup",
            "uk_storage_upload_chunk_task_number",
            "idx_storage_category_tenant_parent",
            "idx_storage_file_config",
            "idx_storage_file_tenant_category",
            "idx_storage_file_storage_key",
            "idx_storage_upload_task_cleanup",
        ).forEach { constraintName ->
            assertTrue(migrationSql.contains("`$constraintName`"), "缺少 $constraintName")
        }

        assertEquals(3, Regex("ON DELETE RESTRICT", RegexOption.IGNORE_CASE).findAll(migrationSql).count())
        assertEquals(1, Regex("ON DELETE CASCADE", RegexOption.IGNORE_CASE).findAll(migrationSql).count())
    }

    /** 验证治理索引与编码占用语义: 清理索引面向跨租户系统扫描, 回收计数有 storage_key 索引, 配置编码永久占用 */
    @Test
    fun `governance indexes and config code occupation stay as designed`() {
        assertTrue(
            Regex("INDEX `idx_storage_upload_task_cleanup` \\(`task_status`, `expires_at`\\)").containsMatchIn(migrationSql),
            "清理是跨租户系统级补偿扫描, 索引应以 task_status, expires_at 打头且不含租户前缀",
        )
        assertTrue(
            Regex("INDEX `idx_storage_file_storage_key` \\(`storage_key`\\)").containsMatchIn(migrationSql),
            "同 key 活跃记录计数的回收查询跨全租户, 需要 storage_key 单列索引",
        )
        assertTrue(
            Regex("CONSTRAINT `uk_storage_config_code` UNIQUE \\(`config_code`\\)").containsMatchIn(migrationSql),
            "配置编码永久占用 (对标 sys_route), 唯一键不做 IFNULL 折算",
        )
    }

    /** 验证秒传与分类编码唯一键均以 IFNULL 哨兵表达式把活跃行纳入约束, 而非裸 deleted_at 入键 */
    @Test
    fun `functional unique keys fold null deleted rows into a sentinel`() {
        listOf("uk_storage_file_dedup", "uk_storage_category_tenant_code").forEach { keyName ->
            val keyDefinition = Regex("UNIQUE KEY `$keyName` \\(([^)]*\\([^)]*\\)[^)]*)\\)", RegexOption.DOT_MATCHES_ALL)
                .find(migrationSql)
                ?: throw AssertionError("$keyName 缺少表达式索引定义")

            assertTrue(
                keyDefinition.groupValues[1].contains("IFNULL(`deleted_at`", ignoreCase = true),
                "$keyName 未使用 IFNULL 折算活跃行",
            )
            assertTrue(keyDefinition.groupValues[1].contains("1970-01-01", ignoreCase = true), "$keyName 缺少哨兵值")
        }
    }

    /** 验证过程数据表不声明删除审计列, 过期治理走物理删除防止行膨胀 */
    @Test
    fun `process tables skip logical delete columns`() {
        val taskBody = tableBody("storage_upload_task")
        val chunkBody = tableBody("storage_upload_chunk")

        assertFalse(Regex("^\\s*`deleted_at`", RegexOption.MULTILINE).containsMatchIn(taskBody), "任务表不应声明 deleted_at")
        assertFalse(Regex("^\\s*`deleted_at`", RegexOption.MULTILINE).containsMatchIn(chunkBody), "分片表不应声明 deleted_at")
    }

    /** 验证分类树邻接表不设数据库外键, 树一致性由 Service 维护 */
    @Test
    fun `category parent stays free of database foreign key`() {
        val categoryBody = tableBody("storage_category")

        assertFalse(
            Regex("FOREIGN KEY", RegexOption.IGNORE_CASE).containsMatchIn(categoryBody),
            "storage_category 不应声明外键, 成环与移动一致性由 Service 维护",
        )
    }

    /**
     * 提取指定表的建表语句体
     *
     * @param tableName 目标表名
     * @return 建表语句中列与约束定义的文本
     */
    private fun tableBody(tableName: String): String =
        assertNotNull(
            Regex(
                "CREATE TABLE `$tableName` \\((.*?)\\) ENGINE",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ).find(migrationSql),
            "迁移中缺少 $tableName",
        ).groupValues[1]

    /** 从测试类路径读取版本化 Storage 迁移脚本 */
    private val migrationSql: String by lazy {
        val resource = assertNotNull(javaClass.getResourceAsStream(MIGRATION_RESOURCE))
        resource.bufferedReader().use { it.readText() }
    }

    /** 保存测试使用的资源常量 */
    private companion object {
        /** Storage 初始 Schema 的迁移版本 */
        const val MIGRATION_RESOURCE = "/db/migration/V001__create_storage_schema.sql"
    }
}
