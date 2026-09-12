package com.zax.aspen.admin.biz.entity.sys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 验证统一认证的端注册与登录方式策略迁移
 *
 * V006 建立端注册 (sys_auth_client) 与端×登录方式 (sys_auth_login_method) 两表:
 * 断言覆盖表集、端编码与 (端, 方式) 唯一约束、方式行级联删除、管理端种子行
 * 与「种子不携带凭据字面量」的密钥边界
 */
class SysAuthClientMigrationTest {
    /** 验证迁移只创建两张认证配置表并使用统一字符集 */
    @Test
    fun `creates the complete auth client schema`() {
        val tableNames = Regex("CREATE TABLE `([^`]+)`", RegexOption.IGNORE_CASE)
            .findAll(migrationSql)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(listOf("sys_auth_client", "sys_auth_login_method"), tableNames)
        assertEquals(2, Regex("COLLATE = utf8mb4_0900_ai_ci", RegexOption.IGNORE_CASE).findAll(migrationSql).count())
    }

    /** 验证端编码唯一、方式 (端, 方式) 唯一与方式行级联删除约束存在 */
    @Test
    fun `keeps uniqueness and cascade constraints`() {
        listOf(
            "uk_sys_auth_client_code",
            "uk_sys_auth_login_method_client_method",
            "idx_sys_auth_client_status",
        ).forEach { constraintName ->
            assertTrue(migrationSql.contains("`$constraintName`"), "缺少 $constraintName")
        }
        assertTrue(
            migrationSql.contains("ON DELETE CASCADE", ignoreCase = true),
            "方式行应随端级联删除",
        )
    }

    /** 验证管理端种子端与密码登录方式行 (行为验证码闸门、首登强制改密、90 天密码有效期) */
    @Test
    fun `seeds admin web client with password method`() {
        assertTrue(migrationSql.contains("'aspen-admin-web', '管理端 Web', 'admin'"), "缺少管理端种子行")
        assertTrue(migrationSql.contains("'password', 'slider', TRUE, 90"), "缺少密码登录方式种子行")
    }

    /** 验证种子不写入任何密钥摘要, secret_hash 只允许经管理面或 bootstrap 写入 */
    @Test
    fun `seeds carry no credential literals`() {
        val insertStatements = Regex("INSERT INTO[^;]+;", RegexOption.IGNORE_CASE)
            .findAll(migrationSql)
            .joinToString()
        assertTrue(!insertStatements.contains("secret_hash"), "种子不得写入 secret_hash 列")
        assertTrue(!insertStatements.contains(Regex("\\$2[aby]\\$")), "种子不得携带 BCrypt 字面量")
    }

    /** 从测试类路径读取 V006 认证配置迁移脚本 */
    private val migrationSql: String by lazy {
        val resource = assertNotNull(javaClass.getResourceAsStream(MIGRATION_RESOURCE))
        resource.bufferedReader().use { it.readText() }
    }

    /** 保存测试使用的资源常量 */
    private companion object {
        /** 认证端注册与登录方式策略的迁移版本 */
        const val MIGRATION_RESOURCE = "/db/migration/sys/V006__create_sys_auth_client.sql"
    }
}
