package com.zax.aspen.admin.biz.integration

import com.jayway.jsonpath.JsonPath
import com.zax.aspen.admin.biz.bootstrap.AspenAdminApplication
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 字典管理 API 的端到端验证
 *
 * Docker 可用时启动 Testcontainers MySQL 并随上下文播种内置字典, 经 MockMvc 走完整
 * MVC 链路 (路由套件映射、受众前缀、校验、错误契约) 验证内置保护、唯一性与默认项
 * 唯一规则; Docker 不可用时整类禁用
 */
@EnabledIf(value = "dockerAvailable", disabledReason = "Docker 不可用, 跳过字典管理端到端验证")
@TestMethodOrder(OrderAnnotation::class)
@SpringBootTest(
    classes = [AspenAdminApplication::class],
    properties = [
        // 集成测试聚焦数据库与 MVC 链路, 不依赖也不连接外部 Nacos
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
    ],
)
@AutoConfigureMockMvc
class SysDictAdminApiIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    /** 验证分页查询命中播种的内置字典, 路由套件映射与受众前缀生效 */
    @Test
    @Order(1)
    fun `page finds seeded built in dict`() {
        mockMvc.get("/admin-api/sys/dict/page") {
            param("keyword", "gender")
        }.andExpect {
            status { isOk() }
            jsonPath("$.items[0].dictCode") { value("gender") }
            jsonPath("$.items[0].isBuiltIn") { value(true) }
            jsonPath("$.totalElements") { value(1) }
        }
    }

    /** 验证内置字典受保护: 禁止停用、禁止删除、禁止新增字典项 */
    @Test
    @Order(2)
    fun `built in dict is protected`() {
        val genderDictId = dictIdOfCode("gender")

        mockMvc.put("/admin-api/sys/dict/$genderDictId/status") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"status": "DISABLED"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON.INVALID_ARGUMENT") }
            jsonPath("$.detail") { value("内置字典不允许停用: gender") }
        }

        mockMvc.delete("/admin-api/sys/dict/$genderDictId").andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("内置字典禁止删除: gender") }
        }

        mockMvc.post("/admin-api/sys/dict/item") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"dictId": $genderDictId, "itemLabel": "其他", "itemValue": "other"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("内置字典的字典项由枚举播种维护, 禁止新增: gender") }
        }
    }

    /** 验证内置字典允许调整显示属性 (只改显示名, 校验值由播种维护) */
    @Test
    @Order(3)
    fun `built in dict display is editable`() {
        val genderDictId = dictIdOfCode("gender")

        mockMvc.put("/admin-api/sys/dict/$genderDictId") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"dictName": "性别", "dictGroup": "common"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.dictName") { value("性别") }
        }
    }

    /** 验证手工字典全生命周期: 新增、重复编码拒绝、展示属性修改、启停与删除 */
    @Test
    @Order(4)
    fun `custom dict lifecycle honors uniqueness and display rules`() {
        val created = mockMvc.post("/admin-api/sys/dict") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"dictCode": "demo_area", "dictName": "演示区域", "dictGroup": "common"}"""
        }.andReturn().response.contentAsString
        val dictId = JsonPath.read<Int>(created, "$.dictId").toLong()
        assertEquals(false, JsonPath.read(created, "$.isBuiltIn"))

        mockMvc.post("/admin-api/sys/dict") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"dictCode": "demo_area", "dictName": "演示区域", "dictGroup": "common"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("字典编码已存在: demo_area") }
        }

        mockMvc.post("/admin-api/sys/dict") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"dictCode": "demo_blank", "dictName": " ", "dictGroup": "common"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON.INVALID_ARGUMENT") }
        }

        mockMvc.put("/admin-api/sys/dict/$dictId") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"dictName": "演示区域改", "dictGroup": "demo"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.dictName") { value("演示区域改") }
        }

        mockMvc.put("/admin-api/sys/dict/$dictId/status") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"status": "DISABLED"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("DISABLED") }
        }

        mockMvc.delete("/admin-api/sys/dict/$dictId").andExpect { status { isOk() } }

        mockMvc.get("/admin-api/sys/dict/$dictId").andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("字典不存在: $dictId") }
        }
    }

    /** 验证字典项规则: 值唯一、默认项先清后置、父子删除保护与删除级联 */
    @Test
    @Order(5)
    fun `custom dict items honor value default and tree rules`() {
        val dictId = createDict("demo_stage")

        val firstBody = mockMvc.post("/admin-api/sys/dict/item") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"dictId": $dictId, "itemLabel": "草稿", "itemValue": "draft", "isDefault": true}"""
        }.andReturn().response.contentAsString
        val parentId = JsonPath.read<Int>(firstBody, "$.dictItemId").toLong()
        assertEquals(true, JsonPath.read(firstBody, "$.isDefault"))

        mockMvc.post("/admin-api/sys/dict/item") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"dictId": $dictId, "itemLabel": "草稿", "itemValue": "draft"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("字典项值已存在: draft") }
        }

        val secondBody = mockMvc.post("/admin-api/sys/dict/item") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"dictId": $dictId, "itemLabel": "发布", "itemValue": "published", "isDefault": true}"""
        }.andReturn().response.contentAsString
        val secondId = JsonPath.read<Int>(secondBody, "$.dictItemId").toLong()

        mockMvc.get("/admin-api/sys/dict/item/list") {
            param("dictId", dictId.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
        }
        assertTrue(firstItemNoLongerDefault(dictId, secondId), "置位新默认项后旧默认项应被清除")

        val childBody = mockMvc.post("/admin-api/sys/dict/item") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"dictId": $dictId, "parentId": $parentId, "itemLabel": "草稿改", "itemValue": "draft_v2"}"""
        }.andReturn().response.contentAsString
        val childId = JsonPath.read<Int>(childBody, "$.dictItemId").toLong()

        mockMvc.delete("/admin-api/sys/dict/item/$parentId").andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("存在子字典项, 请先删除子项: $parentId") }
        }

        mockMvc.put("/admin-api/sys/dict/item/$childId/status") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"status": "DISABLED"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("DISABLED") }
        }

        mockMvc.delete("/admin-api/sys/dict/item/$childId").andExpect { status { isOk() } }
        mockMvc.delete("/admin-api/sys/dict/item/$parentId").andExpect { status { isOk() } }

        mockMvc.delete("/admin-api/sys/dict/$dictId").andExpect { status { isOk() } }
        mockMvc.get("/admin-api/sys/dict/item/list") {
            param("dictId", dictId.toString())
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("字典不存在: $dictId") }
        }
    }

    /**
     * 按编码查询字典主键
     *
     * @param dictCode 字典编码
     * @return 匹配字典的主键 id
     */
    private fun dictIdOfCode(dictCode: String): Long {
        val body = mockMvc.get("/admin-api/sys/dict/page") {
            param("keyword", dictCode)
        }.andReturn().response.contentAsString
        return JsonPath.read<Int>(body, "$.items[0].dictId").toLong()
    }

    /**
     * 新建手工字典并返回主键
     *
     * @param dictCode 字典编码
     * @return 新建字典的主键 id
     */
    private fun createDict(dictCode: String): Long {
        val body = mockMvc.post("/admin-api/sys/dict") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"dictCode": "$dictCode", "dictName": "$dictCode", "dictGroup": "demo"}"""
        }.andReturn().response.contentAsString
        return JsonPath.read<Int>(body, "$.dictId").toLong()
    }

    /**
     * 校验同字典只剩唯一默认项
     *
     * @param dictId 所属字典主键 id
     * @param expectedDefaultId 应为默认项的字典项主键 id
     * @return 默认项唯一且为 expectedDefaultId 时返回 `true`
     */
    private fun firstItemNoLongerDefault(dictId: Long, expectedDefaultId: Long): Boolean {
        val body = mockMvc.get("/admin-api/sys/dict/item/list") {
            param("dictId", dictId.toString())
        }.andReturn().response.contentAsString
        val defaults = JsonPath.read<List<Map<String, Any>>>(body, "$[?(@.isDefault == true)]")
        return defaults.size == 1 && (defaults[0]["dictItemId"] as Number).toLong() == expectedDefaultId
    }

    private companion object {
        /**
         * 探测本机 Docker 守护进程是否可用
         *
         * @return 可建立连接时返回 true, 探测失败按 false 处理
         */
        @JvmStatic
        fun dockerAvailable(): Boolean =
            runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)

        private val mysql: MySQLContainer<*>? =
            if (dockerAvailable()) {
                MySQLContainer<Nothing>("mysql:8").apply { start() }
            } else {
                null
            }

        init {
            mysql?.let { container ->
                // 迁移清单为硬编码、需随新增迁移文件手工同步 (GenDictSeederIntegrationTest 同款)
                listOf(
                    "/db/migration/upm/V001__create_upm_schema.sql",
                    "/db/migration/sys/V002__create_sys_schema.sql",
                ).forEach { resource -> executeMigration(container, resource) }
            }
        }

        /**
         * 把 Testcontainers MySQL 连接信息与字典播种开关注册为动态属性
         *
         * @param registry Spring 测试上下文的动态属性注册器
         */
        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            val container = requireNotNull(mysql) { "Docker 不可用时整类已被禁用, 不应注册属性" }
            registry.add("spring.datasource.url") { jdbcUrl(container) }
            registry.add("spring.datasource.username") { container.username }
            registry.add("spring.datasource.password") { container.password }
            registry.add("aspen.gen.dict.enabled") { "true" }
        }

        /**
         * 构造可执行多语句脚本的 JDBC 连接地址
         *
         * @param container 已启动的 MySQL 测试容器
         * @return 追加 allowMultiQueries=true、关闭 SSL 并允许本机取公钥后的连接地址
         */
        private fun jdbcUrl(container: MySQLContainer<*>): String =
            container.jdbcUrl + "?allowMultiQueries=true&useSSL=false&allowPublicKeyRetrieval=true"

        /**
         * 在容器上执行类路径中的迁移脚本
         *
         * @param container 已启动的 MySQL 测试容器
         * @param resource 类路径下的迁移脚本路径
         */
        private fun executeMigration(container: MySQLContainer<*>, resource: String) {
            val sql = javaClass.getResourceAsStream(resource)!!.bufferedReader().readText()
            DriverManager.getConnection(jdbcUrl(container), container.username, container.password).use { connection ->
                connection.createStatement().use { statement -> statement.execute(sql) }
            }
        }
    }
}
