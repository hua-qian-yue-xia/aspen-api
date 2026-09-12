package com.zax.aspen.admin.biz.integration

import com.zax.aspen.admin.api.dto.sys.SysRouteSaveDTO
import com.zax.aspen.admin.biz.bootstrap.AspenAdminApplication
import com.zax.aspen.admin.biz.service.sys.SysRouteService
import com.zax.aspen.common.cache.support.AspenRedisOperations
import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.gateway.contract.GatewayRouteContract
import com.zax.aspen.common.gateway.contract.RouteCatalogSnapshot
import com.zax.aspen.common.gateway.contract.RouteDefinitionPart
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
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.sql.DriverManager
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 验证路由管理到 Redis 分发的真实链路
 *
 * Docker 可用时启动 Testcontainers MySQL 与 Redis, 执行 V001 至 V004 迁移后验证:
 * 种子路由随上下文启动首发、增删改事务提交后增量发布、版本单调递增、
 * 停用与删除即从快照移除、已删除编码不可复用; Docker 不可用时整类禁用
 */
@EnabledIf(value = "dockerAvailable", disabledReason = "Docker 不可用, 跳过路由分发链路验证")
@TestMethodOrder(OrderAnnotation::class)
@SpringBootTest(
    classes = [AspenAdminApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        // 集成测试聚焦路由分发链路, 不依赖也不连接外部 Nacos
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
    ],
)
class SysRoutePublishIntegrationTest {
    /** 验证启动首发: 种子的 aspen-admin 路由已进入 Redis 版本信封 */
    @Test
    @Order(1)
    fun `startup publishes seeded admin route`() {
        val envelope = readEnvelope()

        assertEquals(1L, envelope.version)
        assertEquals("1", queryString("SELECT COUNT(*) FROM sys_route WHERE deleted_at IS NULL"))
        assertEquals(listOf("aspen-admin"), envelope.routes.map { it.routeCode })
        // V004 起: 断言对齐受众前缀 /admin-api/**、原样转发不带过滤器, 目标为注册名 aspen-admin-biz
        assertEquals("lb://aspen-admin-biz", envelope.routes.single().uri)
        assertEquals("/admin-api/**", envelope.routes.single().predicates.single().args["_genkey_0"])
        assertTrue(envelope.routes.single().filters.isEmpty())
    }

    /** 验证新增路由在事务提交后增量发布且版本递增 */
    @Test
    @Order(2)
    fun `create route publishes incrementally`() {
        val view = sysRouteService.createRoute(demoCommand())

        assertTrue(view.routeId > 0)
        val envelope = readEnvelope()
        assertEquals(2L, envelope.version)
        assertEquals(listOf("aspen-admin", "demo-service"), envelope.routes.map { it.routeCode })
        assertEquals("2", aspenRedisOperations.getValue(GatewayRouteContract.versionKey("local")))
    }

    /** 验证停用路由即从发布快照移除 */
    @Test
    @Order(3)
    fun `disable route removes it from snapshot`() {
        val demoRouteId = demoRouteId()
        sysRouteService.updateRoute(demoRouteId, demoCommand(status = EnabledStatus.DISABLED))

        val envelope = readEnvelope()
        assertEquals(3L, envelope.version)
        assertEquals(listOf("aspen-admin"), envelope.routes.map { it.routeCode })
    }

    /** 验证删除路由即从快照移除, 且已删除编码被唯一键永久保留 */
    @Test
    @Order(4)
    fun `delete route removes it and retired code cannot be reused`() {
        val demoRouteId = demoRouteId()
        sysRouteService.updateRoute(demoRouteId, demoCommand(status = EnabledStatus.ENABLED))
        sysRouteService.deleteRoute(demoRouteId)

        val envelope = readEnvelope()
        assertEquals(5L, envelope.version)
        assertEquals(listOf("aspen-admin"), envelope.routes.map { it.routeCode })
        assertEquals("1", queryString("SELECT COUNT(*) FROM sys_route WHERE deleted_at IS NOT NULL"))

        assertFailsWith<IllegalArgumentException> { sysRouteService.createRoute(demoCommand()) }
    }

    @Autowired
    private lateinit var sysRouteService: SysRouteService

    @Autowired
    private lateinit var aspenRedisOperations: AspenRedisOperations

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var dataSource: DataSource

    /**
     * 读取并解析 Redis 路由信封
     *
     * @return 反序列化后的路由目录快照
     * @throws IllegalArgumentException Redis 中尚未发布信封时抛出
     */
    private fun readEnvelope(): RouteCatalogSnapshot {
        val text = aspenRedisOperations.getValue(GatewayRouteContract.routesKey("local"))
        return objectMapper.readValue(requireNotNull(text) { "路由信封尚未发布" })
    }

    /**
     * 查询当前未删除的 demo-service 路由主键
     *
     * @return demo-service 路由的 route_id
     */
    private fun demoRouteId(): Long =
        requireNotNull(
            queryString("SELECT route_id FROM sys_route WHERE route_code = 'demo-service' AND deleted_at IS NULL"),
        ) { "demo-service 路由不存在" }.toLong()

    /**
     * 构造 demo-service 路由的写入命令夹具
     *
     * @param status 路由启停状态, 默认启用
     * @return 可直接提交创建或更新的写入命令
     */
    private fun demoCommand(status: EnabledStatus = EnabledStatus.ENABLED): SysRouteSaveDTO =
        SysRouteSaveDTO(
            routeCode = "demo-service",
            routeName = "演示服务",
            uri = "lb://demo-service",
            sortOrder = 10,
            status = status,
            predicates = listOf(RouteDefinitionPart("Path", mapOf("_genkey_0" to "/demo/**"))),
        )

    /**
     * 查询单值并以字符串返回, 布尔与整数按 MySQL 原样文本化
     *
     * @param sql 待执行的单值查询 SQL
     * @return 首行首列的文本值, 无结果行时返回 `null`
     */
    private fun queryString(sql: String): String? =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { resultSet ->
                    if (resultSet.next()) resultSet.getString(1) else null
                }
            }
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
            if (dockerAvailable()) MySQLContainer<Nothing>("mysql:8").apply { start() } else null

        private val redis: GenericContainer<*>? =
            if (dockerAvailable()) {
                GenericContainer<Nothing>(DockerImageName.parse("redis:7-alpine"))
                    .apply { withExposedPorts(6379); start() }
            } else {
                null
            }

        init {
            mysql?.let { container ->
                // 迁移清单为硬编码、需随新增迁移文件手工同步 (GenDictSeederIntegrationTest 同款):
                // 新迁移未登记时本类仍可启动, 但种子断言跑在旧库结构上, 失败原因会指向旧值
                listOf(
                    "/db/migration/upm/V001__create_upm_schema.sql",
                    "/db/migration/sys/V002__create_sys_schema.sql",
                    "/db/migration/sys/V003__create_sys_route.sql",
                    "/db/migration/sys/V004__align_admin_route_prefix.sql",
                ).forEach { resource -> executeMigration(container, resource) }
            }
        }

        /**
         * 把 Testcontainers MySQL 与 Redis 连接信息及路由环境标识注册为动态属性
         *
         * @param registry Spring 测试上下文的动态属性注册器
         */
        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            val mysqlContainer = requireNotNull(mysql) { "Docker 不可用时整类已被禁用, 不应注册属性" }
            val redisContainer = requireNotNull(redis) { "Docker 不可用时整类已被禁用, 不应注册属性" }
            registry.add("spring.datasource.url") { jdbcUrl(mysqlContainer) }
            registry.add("spring.datasource.username") { mysqlContainer.username }
            registry.add("spring.datasource.password") { mysqlContainer.password }
            registry.add("spring.data.redis.host") { redisContainer.host }
            registry.add("spring.data.redis.port") { redisContainer.getMappedPort(6379) }
            registry.add("aspen.routes.environment") { "local" }
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
