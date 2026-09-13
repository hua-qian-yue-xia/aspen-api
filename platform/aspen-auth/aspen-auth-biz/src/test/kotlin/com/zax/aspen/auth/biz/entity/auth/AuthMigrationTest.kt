package com.zax.aspen.auth.biz.entity.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 验证 Auth Schema 迁移覆盖完整表集和关键数据库约束
 */
class AuthMigrationTest {
    /** 验证迁移只创建会话与登录日志两张表并使用统一字符集 */
    @Test
    fun `creates the complete auth schema`() {
        val tableNames = Regex("CREATE TABLE `([^`]+)`", RegexOption.IGNORE_CASE)
            .findAll(migrationSql)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(listOf("auth_session", "auth_login_log"), tableNames)
        assertEquals(2, Regex("COLLATE = utf8mb4_0900_ai_ci", RegexOption.IGNORE_CASE).findAll(migrationSql).count())
    }

    /** 验证刷新摘要唯一约束与主体维度的查询索引存在 */
    @Test
    fun `keeps uniqueness and lookup constraints`() {
        listOf(
            "uk_auth_session_refresh_hash",
            "idx_auth_session_principal",
            "idx_auth_login_log_created",
            "idx_auth_login_log_principal",
        ).forEach { constraintName ->
            assertTrue(migrationSql.contains("`$constraintName`"), "缺少 $constraintName")
        }
    }

    /** 验证会话表不含租户列与凭据明文列, 登录日志表只追加不修改 */
    @Test
    fun `keeps session and log column boundaries`() {
        val sessionTable = migrationSql.substringAfter("CREATE TABLE `auth_session`").substringBefore("ENGINE")
        assertTrue(!sessionTable.contains("tenant_id"), "会话表不应携带租户列")
        assertTrue(!sessionTable.contains("refresh_token`"), "会话表只存摘要, 不存刷新令牌明文")
        val logTable = migrationSql.substringAfter("CREATE TABLE `auth_login_log`").substringBefore("ENGINE")
        assertTrue(logTable.contains("`created_at`"), "登录日志必须携带只增时间列")
    }

    /** 从测试类路径读取 V001 Auth Schema 迁移脚本 */
    private val migrationSql: String by lazy {
        val resource = assertNotNull(javaClass.getResourceAsStream(MIGRATION_RESOURCE))
        resource.bufferedReader().use { it.readText() }
    }

    /** 保存测试使用的资源常量 */
    private companion object {
        /** Auth 初始 Schema 的迁移版本 */
        const val MIGRATION_RESOURCE = "/db/migration/V001__create_auth_schema.sql"
    }
}
