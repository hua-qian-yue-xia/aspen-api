package com.zax.aspen.task.biz.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 验证 Task 迁移脚本覆盖完整表集和关键数据库约束
 */
class TaskMigrationTest {
    /** 验证业务迁移按依赖顺序创建 3 张核心 Task 表并使用统一字符集 */
    @Test
    fun `creates the complete task schema`() {
        val tableNames = Regex("CREATE TABLE `([^`]+)`", RegexOption.IGNORE_CASE)
            .findAll(taskSchemaSql)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(
            listOf(
                "task_definition",
                "task_tenant",
                "task_execution",
            ),
            tableNames,
        )
        assertEquals(3, Regex("COLLATE = utf8mb4_0900_ai_ci", RegexOption.IGNORE_CASE).findAll(taskSchemaSql).count())
    }

    /** 验证任务编码永久占用、圈定租户唯一键、清单外键与执行记录索引存在 */
    @Test
    fun `keeps code occupation tenant selection and execution constraints`() {
        listOf(
            "uk_task_definition_code",
            "uk_task_tenant_definition_tenant",
            "fk_task_tenant_definition",
            "idx_task_definition_status",
            "idx_task_execution_definition_fire",
            "idx_task_execution_tenant",
            "idx_task_execution_status_created",
            "idx_task_execution_request",
        ).forEach { constraintName ->
            assertTrue(taskSchemaSql.contains("`$constraintName`"), "缺少 $constraintName")
        }

        assertTrue(
            Regex("CONSTRAINT `uk_task_definition_code` UNIQUE \\(`task_code`\\)").containsMatchIn(taskSchemaSql),
            "任务编码永久占用, 唯一键不做 IFNULL 折算",
        )
    }

    /** 验证执行记录是过程数据表, 不声明删除审计, 保留期治理走物理删除 */
    @Test
    fun `execution table skips logical delete columns`() {
        val executionBody = tableBody("task_execution")

        assertFalse(Regex("^\\s*`deleted_at`", RegexOption.MULTILINE).containsMatchIn(executionBody), "执行记录表不应声明 deleted_at")
    }

    /** 验证 Quartz 运行时表为上游官方 DDL: 11 张 QRTZ 表、无破坏性语句、保留来源标注 */
    @Test
    fun `quartz schema ships upstream ddl without destructive statements`() {
        val quartzTables = Regex("CREATE TABLE QRTZ_([A-Z_]+)", RegexOption.IGNORE_CASE)
            .findAll(quartzSchemaSql)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(
            setOf(
                "JOB_DETAILS",
                "TRIGGERS",
                "SIMPLE_TRIGGERS",
                "CRON_TRIGGERS",
                "SIMPROP_TRIGGERS",
                "BLOB_TRIGGERS",
                "CALENDARS",
                "PAUSED_TRIGGER_GRPS",
                "FIRED_TRIGGERS",
                "SCHEDULER_STATE",
                "LOCKS",
            ),
            quartzTables.toSet(),
        )
        assertFalse(
            Regex("^DROP TABLE", RegexOption.MULTILINE).containsMatchIn(quartzSchemaSql),
            "版本化迁移禁止携带 DROP TABLE 语句",
        )
        assertTrue(quartzSchemaSql.contains("org/quartz/impl/jdbcjobstore/tables_mysql_innodb.sql"), "缺少上游来源标注")
    }

    /** 验证执行日志表承载回传幂等: (executionId, seq) 唯一且不声明删除审计 */
    @Test
    fun `execution log table keeps idempotent append semantics`() {
        assertTrue(executionLogSchemaSql.contains("`uk_task_execution_log_execution_seq`"), "缺少 (execution_id, seq) 唯一键")
        assertFalse(
            Regex("^\\s*`deleted_at`", RegexOption.MULTILINE).containsMatchIn(executionLogSchemaSql),
            "日志表是追加型过程数据, 不应声明 deleted_at",
        )
        assertEquals(1, Regex("COLLATE = utf8mb4_0900_ai_ci", RegexOption.IGNORE_CASE).findAll(executionLogSchemaSql).count())
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
            ).find(taskSchemaSql),
            "迁移中缺少 $tableName",
        ).groupValues[1]

    /** 从测试类路径读取 Task 业务表迁移脚本 */
    private val taskSchemaSql: String by lazy { migrationSql(TASK_MIGRATION_RESOURCE) }

    /** 从测试类路径读取 Quartz 运行时表迁移脚本 */
    private val quartzSchemaSql: String by lazy { migrationSql(QUARTZ_MIGRATION_RESOURCE) }

    /** 从测试类路径读取执行日志表迁移脚本 */
    private val executionLogSchemaSql: String by lazy { migrationSql(EXECUTION_LOG_MIGRATION_RESOURCE) }

    /**
     * 读取指定迁移资源全文
     *
     * @param resource 类路径资源位置
     * @return 迁移脚本文本
     */
    private fun migrationSql(resource: String): String =
        assertNotNull(javaClass.getResourceAsStream(resource)).bufferedReader().use { it.readText() }

    /** 保存测试使用的资源常量 */
    private companion object {
        /** Task 业务表的初始迁移版本 */
        const val TASK_MIGRATION_RESOURCE = "/db/migration/V001__create_task_schema.sql"

        /** Quartz 运行时表的初始迁移版本 */
        const val QUARTZ_MIGRATION_RESOURCE = "/db/migration/V002__create_quartz_schema.sql"

        /** 执行过程日志表的迁移版本 */
        const val EXECUTION_LOG_MIGRATION_RESOURCE = "/db/migration/V003__create_task_execution_log.sql"
    }
}
