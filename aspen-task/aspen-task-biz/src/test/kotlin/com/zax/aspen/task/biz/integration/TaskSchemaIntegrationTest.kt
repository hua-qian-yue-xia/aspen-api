package com.zax.aspen.task.biz.integration

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.sql.Connection
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 验证 Task 初始迁移在真实 MySQL 上可执行且核心约束行为正确
 *
 * Docker 可用时启动 Testcontainers MySQL 依次执行 V001 (业务表)、V002 (Quartz
 * 运行时表) 与 V003 (执行日志表) 后验证: 四张业务表与 11 张 QRTZ 表齐备、
 * 圈定租户唯一键幂等拒绝、execution_id 字符串主键承载逻辑执行幂等、日志
 * (execution_id, seq) 唯一键承载回传幂等; Docker 不可用时整类禁用。
 * 所有语句都是编译期常量 SQL, 动态值一律占位符绑定, 各测试方法使用互不相同
 * 的种子主键与编码, 不依赖执行顺序
 */
@EnabledIf(value = "dockerAvailable", disabledReason = "Docker 不可用, 跳过 Task Schema 真实执行验证")
class TaskSchemaIntegrationTest {
    /** 验证迁移创建全部业务表与 Quartz 运行时表 */
    @Test
    fun `migration creates business and quartz tables`() {
        withConnection { connection ->
            // 上游 Quartz DDL 表名是大写, MySQL 在区分大小写文件系统上原样保留, 比较前统一小写
            val tables = tableNames(connection).map { it.lowercase() }
            assertEquals(
                (BUSINESS_TABLES + QUARTZ_TABLES).sorted(),
                tables,
                "应存在 3 张业务表与 11 张 QRTZ 运行时表",
            )
        }
    }

    /** 验证圈定租户唯一键拒绝同任务同租户的重复行 */
    @Test
    fun `tenant selection unique key rejects duplicates`() {
        withConnection { connection ->
            insertDefinitionRow(connection, definitionId = 1L, taskCode = "tenant-uk-local")
            insertTaskTenantRow(connection, definitionId = 1L, tenantId = 100L)

            assertFailsWith<java.sql.SQLIntegrityConstraintViolationException>(
                "同任务同租户必须被 uk_task_tenant_definition_tenant 拒绝",
            ) {
                insertTaskTenantRow(connection, definitionId = 1L, tenantId = 100L)
            }
        }
    }

    /** 验证执行日志按 (execution_id, seq) 幂等: 重复序号被唯一键拒绝 */
    @Test
    fun `execution log unique key rejects duplicate sequence`() {
        withConnection { connection ->
            insertDefinitionRow(connection, definitionId = 4L, taskCode = "log-uk-local")
            insertExecutionRow(connection, executionId = "task-4-f1700000000000-100", definitionId = 4L, tenantId = 100L)
            insertExecutionLogRow(connection, executionId = "task-4-f1700000000000-100", seq = 1, message = "开始同步")

            assertFailsWith<java.sql.SQLIntegrityConstraintViolationException>(
                "重复日志序号必须被 uk_task_execution_log_execution_seq 拒绝",
            ) {
                insertExecutionLogRow(connection, executionId = "task-4-f1700000000000-100", seq = 1, message = "重复上报")
            }
        }
    }

    /** 验证 execution_id 字符串主键拒绝同逻辑执行的重复创建 */
    @Test
    fun `execution primary key rejects duplicate logical executions`() {
        withConnection { connection ->
            insertDefinitionRow(connection, definitionId = 2L, taskCode = "execution-pk-local")
            insertExecutionRow(connection, executionId = "task-2-f1700000000000-100", definitionId = 2L, tenantId = 100L)

            assertFailsWith<java.sql.SQLIntegrityConstraintViolationException>(
                "同逻辑执行重复创建必须被 execution_id 主键拒绝",
            ) {
                insertExecutionRow(connection, executionId = "task-2-f1700000000000-100", definitionId = 2L, tenantId = 100L)
            }
        }
    }

    /**
     * 查询当前库全部表名
     *
     * @param connection 目标数据库连接
     * @return 按名称升序排列的表名列表
     */
    private fun tableNames(connection: Connection): List<String> =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() ORDER BY table_name")
                .use { resultSet ->
                    generateSequence { if (resultSet.next()) resultSet.getString(1) else null }.toList()
                }
        }

    /**
     * 插入一条任务定义种子行, 各测试以独立主键与编码互不干扰
     *
     * @param connection 目标数据库连接
     * @param definitionId 指定任务主键
     * @param taskCode 本测试专用的任务编码
     */
    private fun insertDefinitionRow(connection: Connection, definitionId: Long, taskCode: String) {
        connection.prepareStatement(
            "INSERT INTO task_definition (definition_id, task_code, task_name, trigger_type, cron_expression, timezone_id, http_method, target_url, owner_account, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        ).use { statement ->
            listOf(definitionId, taskCode, "演示任务", "cron", "0 0 2 * * ?", "Asia/Shanghai", "post", "https://example.com/hook", "ops", "enabled")
                .forEachIndexed { index, parameter -> statement.setObject(index + 1, parameter) }
            statement.executeUpdate()
        }
    }

    /**
     * 插入一条圈定租户行
     *
     * @param connection 目标数据库连接
     * @param definitionId 所属任务定义
     * @param tenantId 圈定的租户
     */
    private fun insertTaskTenantRow(connection: Connection, definitionId: Long, tenantId: Long) {
        connection.prepareStatement("INSERT INTO task_tenant (definition_id, tenant_id) VALUES (?, ?)").use { statement ->
            listOf(definitionId, tenantId).forEachIndexed { index, parameter -> statement.setObject(index + 1, parameter) }
            statement.executeUpdate()
        }
    }

    /**
     * 插入一条 RUNNING 执行记录
     *
     * @param connection 目标数据库连接
     * @param executionId 逻辑执行唯一标识
     * @param definitionId 所属任务定义
     * @param tenantId 归属租户
     */
    private fun insertExecutionRow(connection: Connection, executionId: String, definitionId: Long, tenantId: Long) {
        connection.prepareStatement(
            "INSERT INTO task_execution (execution_id, definition_id, task_code, tenant_id, trigger_source, fire_time, attempt, status, started_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        ).use { statement ->
            listOf(executionId, definitionId, "demo", tenantId, "scheduled", "2026-09-12 00:00:00", 1, "running", "2026-09-12 00:00:00")
                .forEachIndexed { index, parameter -> statement.setObject(index + 1, parameter) }
            statement.executeUpdate()
        }
    }

    /**
     * 插入一条执行过程日志
     *
     * @param connection 目标数据库连接
     * @param executionId 所属逻辑执行唯一标识
     * @param seq 执行内自增序号
     * @param message 日志消息文本
     */
    private fun insertExecutionLogRow(connection: Connection, executionId: String, seq: Int, message: String) {
        connection.prepareStatement(
            "INSERT INTO task_execution_log (execution_id, seq, level, message, logged_at) VALUES (?, ?, ?, ?, ?)",
        ).use { statement ->
            listOf(executionId, seq, "info", message, "2026-09-12 00:00:01")
                .forEachIndexed { index, parameter -> statement.setObject(index + 1, parameter) }
            statement.executeUpdate()
        }
    }

    /**
     * 借共享容器连接执行受检代码块
     *
     * @param block 消费连接的验证逻辑
     */
    private fun withConnection(block: (Connection) -> Unit) {
        requireNotNull(mysql).createConnection("").use(block)
    }

    private companion object {
        /** 业务表集合 */
        val BUSINESS_TABLES = setOf("task_definition", "task_execution", "task_execution_log", "task_tenant")

        /** Quartz 运行时表集合 (上游官方 11 张) */
        val QUARTZ_TABLES = setOf(
            "qrtz_blob_triggers",
            "qrtz_calendars",
            "qrtz_cron_triggers",
            "qrtz_fired_triggers",
            "qrtz_job_details",
            "qrtz_locks",
            "qrtz_paused_trigger_grps",
            "qrtz_scheduler_state",
            "qrtz_simple_triggers",
            "qrtz_simprop_triggers",
            "qrtz_triggers",
        )

        /**
         * 探测本机 Docker 守护进程是否可用
         *
         * @return 可建立连接时返回 true, 探测失败按 false 处理
         */
        @JvmStatic
        fun dockerAvailable(): Boolean =
            runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)

        private val mysql: MySQLContainer<*>? =
            if (dockerAvailable()) MySQLContainer<Nothing>("mysql:8.4").apply { start() } else null

        init {
            mysql?.let { container ->
                container.createConnection("").use { connection ->
                    ScriptUtils.executeSqlScript(connection, ClassPathResource("/db/migration/V001__create_task_schema.sql"))
                    ScriptUtils.executeSqlScript(connection, ClassPathResource("/db/migration/V002__create_quartz_schema.sql"))
                    ScriptUtils.executeSqlScript(connection, ClassPathResource("/db/migration/V003__create_task_execution_log.sql"))
                }
            }
        }
    }
}
