package com.zax.aspen.admin.biz.bootstrap

import com.zax.aspen.admin.biz.service.sys.GenDictSeeder
import com.zax.aspen.common.gen.autoconfigure.AspenGenProperties
import com.zax.aspen.common.gen.scan.GenDictCatalog
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.sql.DriverManager
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 验证 @GenDict 枚举字典播种的真实写路径
 *
 * Docker 可用时启动 Testcontainers MySQL, 执行 V001/V002 迁移后随上下文启动播种并验证
 * 幂等与两种模式; Docker 不可用时整类禁用, 不启动 Spring 上下文, 保证其余测试可运行
 */
@EnabledIf(value = "dockerAvailable", disabledReason = "Docker 不可用, 跳过真实写路径验证")
@TestMethodOrder(OrderAnnotation::class)
@SpringBootTest(classes = [AspenAdminApplication::class], webEnvironment = SpringBootTest.WebEnvironment.NONE)
class GenDictSeederIntegrationTest {
    /** 验证上下文启动播种已写入三个通用字典及其项 */
    @Test
    @Order(1)
    fun `seeds common dicts on startup`() {
        assertEquals(
            listOf("enabled_status", "gender", "risk_level"),
            queryStrings("SELECT dict_code FROM sys_dict ORDER BY dict_code"),
        )
        assertEquals(
            listOf("common", "common", "common"),
            queryStrings("SELECT dict_group FROM sys_dict ORDER BY dict_code"),
        )
        assertEquals(
            listOf("1", "1", "1"),
            queryStrings("SELECT is_built_in FROM sys_dict ORDER BY dict_code"),
        )
        assertEquals(
            listOf("启停状态", "性别", "风险等级"),
            queryStrings("SELECT dict_name FROM sys_dict ORDER BY dict_code"),
        )

        // 字典项总数: enabled_status 2 + gender 4 + risk_level 4
        assertEquals("10", queryString("SELECT COUNT(*) FROM sys_dict_item"))
        assertEquals(
            listOf("未知", "男", "女", "不适用"),
            queryStrings(
                """
                SELECT i.item_label FROM sys_dict_item i
                JOIN sys_dict d ON i.dict_id = d.dict_id
                WHERE d.dict_code = 'gender' ORDER BY i.sort_order
                """.trimIndent(),
            ),
        )
        assertEquals(
            listOf("0", "1", "2", "3"),
            queryStrings(
                """
                SELECT i.sort_order FROM sys_dict_item i
                JOIN sys_dict d ON i.dict_id = d.dict_id
                WHERE d.dict_code = 'gender' ORDER BY i.dict_id
                """.trimIndent(),
            ),
        )
        assertEquals(
            listOf("success", "danger"),
            queryStrings(
                """
                SELECT i.color FROM sys_dict_item i
                JOIN sys_dict d ON i.dict_id = d.dict_id
                WHERE d.dict_code = 'enabled_status' ORDER BY i.sort_order
                """.trimIndent(),
            ),
        )
        assertNull(
            queryString(
                """
                SELECT i.color FROM sys_dict_item i
                JOIN sys_dict d ON i.dict_id = d.dict_id
                WHERE d.dict_code = 'gender' AND i.item_value = 'male'
                """.trimIndent(),
            ),
        )
        assertEquals(
            "system:gen-dict",
            queryString("SELECT MIN(created_by) FROM sys_dict"),
        )
        assertEquals(
            "1",
            queryString("SELECT MIN(version) FROM sys_dict_item"),
        )
    }

    /** 验证重复播种不产生重复数据 */
    @Test
    @Order(2)
    fun `reseeding create missing keeps counts stable`() {
        genDictSeeder.seed(genDictCatalog.descriptors, AspenGenProperties.Dict.Mode.CREATE_MISSING)

        assertEquals("3", queryString("SELECT COUNT(*) FROM sys_dict"))
        assertEquals("10", queryString("SELECT COUNT(*) FROM sys_dict_item"))
    }

    /** 验证 create-missing 不覆盖运营对展示属性的修改 */
    @Test
    @Order(3)
    fun `create missing preserves manual display edits`() {
        execute(
            """
            UPDATE sys_dict_item SET item_label = '男士', color = 'red'
            WHERE item_value = 'male' AND dict_id = (SELECT dict_id FROM sys_dict WHERE dict_code = 'gender')
            """.trimIndent(),
        )

        genDictSeeder.seed(genDictCatalog.descriptors, AspenGenProperties.Dict.Mode.CREATE_MISSING)

        assertEquals(
            "男士",
            queryString(
                """
                SELECT i.item_label FROM sys_dict_item i
                JOIN sys_dict d ON i.dict_id = d.dict_id
                WHERE d.dict_code = 'gender' AND i.item_value = 'male'
                """.trimIndent(),
            ),
        )
    }

    /** 验证 resync 把展示属性强制回写为枚举声明值 */
    @Test
    @Order(4)
    fun `resync restores enum display values`() {
        genDictSeeder.seed(genDictCatalog.descriptors, AspenGenProperties.Dict.Mode.RESYNC)

        assertEquals(
            "男",
            queryString(
                """
                SELECT i.item_label FROM sys_dict_item i
                JOIN sys_dict d ON i.dict_id = d.dict_id
                WHERE d.dict_code = 'gender' AND i.item_value = 'male'
                """.trimIndent(),
            ),
        )
        assertNull(
            queryString(
                """
                SELECT i.color FROM sys_dict_item i
                JOIN sys_dict d ON i.dict_id = d.dict_id
                WHERE d.dict_code = 'gender' AND i.item_value = 'male'
                """.trimIndent(),
            ),
        )
        assertEquals(
            "system:gen-dict",
            queryString(
                """
                SELECT i.updated_by FROM sys_dict_item i
                JOIN sys_dict d ON i.dict_id = d.dict_id
                WHERE d.dict_code = 'gender' AND i.item_value = 'male'
                """.trimIndent(),
            ),
        )
    }

    @Autowired
    private lateinit var genDictSeeder: GenDictSeeder

    @Autowired
    private lateinit var genDictCatalog: GenDictCatalog

    @Autowired
    private lateinit var dataSource: DataSource

    /** 查询单值并以字符串返回, 布尔与整数按 MySQL 原样文本化 */
    private fun queryString(sql: String): String? =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { resultSet ->
                    if (resultSet.next()) resultSet.getString(1) else null
                }
            }
        }

    /** 查询整列并以字符串列表返回 */
    private fun queryStrings(sql: String): List<String> =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { resultSet ->
                    generateSequence { if (resultSet.next()) resultSet.getString(1) else null }.toList()
                }
            }
        }

    /** 直接执行维护用 SQL */
    private fun execute(sql: String) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement -> statement.execute(sql) }
        }
    }

    private companion object {
        @JvmStatic
        fun dockerAvailable(): Boolean =
            runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)

        private val mysql: MySQLContainer<*>? =
            if (dockerAvailable()) {
                MySQLContainer<Nothing>("mysql:8.4").apply { start() }
            } else {
                null
            }

        init {
            mysql?.let { container ->
                listOf(
                    "/db/migration/upm/V001__create_upm_schema.sql",
                    "/db/migration/sys/V002__create_sys_schema.sql",
                ).forEach { resource -> executeMigration(container, resource) }
            }
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            val container = requireNotNull(mysql) { "Docker 不可用时整类已被禁用, 不应注册属性" }
            registry.add("spring.datasource.url") { jdbcUrl(container) }
            registry.add("spring.datasource.username") { container.username }
            registry.add("spring.datasource.password") { container.password }
            registry.add("aspen.gen.dict.enabled") { "true" }
        }

        /** 多语句脚本需要 allowMultiQueries, 关闭 SSL 并允许本机取公钥 */
        private fun jdbcUrl(container: MySQLContainer<*>): String =
            container.jdbcUrl + "?allowMultiQueries=true&useSSL=false&allowPublicKeyRetrieval=true"

        private fun executeMigration(container: MySQLContainer<*>, resource: String) {
            val sql = javaClass.getResourceAsStream(resource)!!.bufferedReader().readText()
            DriverManager.getConnection(jdbcUrl(container), container.username, container.password).use { connection ->
                connection.createStatement().use { statement -> statement.execute(sql) }
            }
        }
    }
}
