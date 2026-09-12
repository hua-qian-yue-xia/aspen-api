package com.zax.aspen.storage.biz.integration

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
 * 验证 Storage 初始迁移在真实 MySQL 上可执行且核心约束行为正确
 *
 * Docker 可用时启动 Testcontainers MySQL 执行 V001 迁移后验证:
 * 秒传唯一键与分类编码键以 IFNULL 哨兵把活跃行纳入约束 (并发写入被拒、
 * 逻辑删除后同键可重建)、分类父子挂载与文件分类外键可用、
 * 分片唯一键幂等拒绝与任务删除级联清理分片;
 * Docker 不可用时整类禁用。所有语句都是编译期常量 SQL, 动态值一律占位符绑定;
 * 各测试方法使用互不相同的种子主键与编码, 不依赖执行顺序
 */
@EnabledIf(value = "dockerAvailable", disabledReason = "Docker 不可用, 跳过 Storage Schema 真实执行验证")
class StorageSchemaIntegrationTest {
    /** 验证迁移创建 5 张表且表达式唯一键在真实 MySQL 上成功建索引 */
    @Test
    fun `migration creates all storage tables with functional unique keys`() {
        withConnection { connection ->
            assertEquals(
                listOf(
                    "storage_category",
                    "storage_config",
                    "storage_file",
                    "storage_upload_chunk",
                    "storage_upload_task",
                ),
                tableNames(connection),
            )
            assertEquals("4", dedupIndexColumnCount(connection), "秒传唯一键应为 4 部 (含 IFNULL 表达式部)")
            assertEquals("3", categoryCodeIndexColumnCount(connection), "分类编码键应为 3 部 (含 IFNULL 表达式部)")
        }
    }

    /** 验证并发同传同内容的活跃行被唯一键拒绝, 逻辑删除后同内容可重传 */
    @Test
    fun `dedup unique key rejects live duplicates and releases after logical delete`() {
        withConnection { connection ->
            insertConfigRow(connection, 1L, "dedup-local")
            insertFileRow(connection, tenantId = 1L, configId = 1L, sha256 = CONTENT_SHA256)

            assertFailsWith<java.sql.SQLIntegrityConstraintViolationException>(
                "同租户同内容活跃行必须被 uk_storage_file_dedup 拒绝",
            ) {
                insertFileRow(connection, tenantId = 1L, configId = 1L, sha256 = CONTENT_SHA256)
            }

            logicalDeleteFileBySha256(connection, 1L, CONTENT_SHA256)
            insertFileRow(connection, tenantId = 1L, configId = 1L, sha256 = CONTENT_SHA256)
            assertEquals("1", countLiveFilesBySha256(connection, CONTENT_SHA256))
        }
    }

    /** 验证分类树父子挂载、文件挂分类外键、同码拒绝与逻辑删除后同码重建 */
    @Test
    fun `category tree links and code unique key releases after logical delete`() {
        withConnection { connection ->
            insertConfigRow(connection, 3L, "category-local")
            insertCategoryRow(connection, categoryId = 3L, tenantId = 1L, parentId = null, categoryCode = "marketing")
            insertCategoryRow(connection, categoryId = 4L, tenantId = 1L, parentId = 3L, categoryCode = "product-image")

            assertFailsWith<java.sql.SQLIntegrityConstraintViolationException>(
                "同租户同码活跃分类必须被 uk_storage_category_tenant_code 拒绝",
            ) {
                insertCategoryRow(connection, categoryId = 5L, tenantId = 1L, parentId = null, categoryCode = "marketing")
            }

            insertCategorizedFileRow(connection, tenantId = 1L, configId = 3L, categoryId = 4L, sha256 = CATEGORY_FILE_SHA256)

            logicalDeleteCategoryByCode(connection, 1L, "marketing")
            insertCategoryRow(connection, categoryId = 6L, tenantId = 1L, parentId = null, categoryCode = "marketing")
            assertEquals("2", countLiveCategories(connection), "同码重建后活跃分类应为 2 行 (子分类 + 重建行)")
        }
    }

    /** 验证分片唯一键拒绝重复分片号, 任务物理删除级联清理分片 */
    @Test
    fun `chunk unique key is idempotent and task deletion cascades chunks`() {
        withConnection { connection ->
            insertConfigRow(connection, 2L, "chunk-local")
            insertTaskRow(connection, taskId = 2L, tenantId = 1L, configId = 2L)
            insertChunkRow(connection, tenantId = 1L, taskId = 2L, chunkNumber = 1)

            assertFailsWith<java.sql.SQLIntegrityConstraintViolationException>(
                "重复分片号必须被 uk_storage_upload_chunk_task_number 拒绝",
            ) {
                insertChunkRow(connection, tenantId = 1L, taskId = 2L, chunkNumber = 1)
            }

            deleteTaskById(connection, 2L)
            assertEquals("0", countChunks(connection))
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
     * 查询秒传唯一键的索引部数量
     *
     * @param connection 目标数据库连接
     * @return uk_storage_file_dedup 的索引部数量文本
     */
    private fun dedupIndexColumnCount(connection: Connection): String =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'storage_file' AND index_name = 'uk_storage_file_dedup'")
                .use { resultSet ->
                    resultSet.next()
                    resultSet.getString(1)
                }
        }

    /**
     * 查询分类编码唯一键的索引部数量
     *
     * @param connection 目标数据库连接
     * @return uk_storage_category_tenant_code 的索引部数量文本
     */
    private fun categoryCodeIndexColumnCount(connection: Connection): String =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'storage_category' AND index_name = 'uk_storage_category_tenant_code'")
                .use { resultSet ->
                    resultSet.next()
                    resultSet.getString(1)
                }
        }

    /**
     * 统计指定内容的活跃文件行数
     *
     * @param connection 目标数据库连接
     * @param sha256 整文件哈希
     * @return 该内容未删除的行数文本
     */
    private fun countLiveFilesBySha256(connection: Connection, sha256: String): String =
        connection.prepareStatement("SELECT COUNT(*) FROM storage_file WHERE deleted_at IS NULL AND sha256 = ?").use { statement ->
            statement.setObject(1, sha256)
            statement.executeQuery().use { resultSet ->
                resultSet.next()
                resultSet.getString(1)
            }
        }

    /**
     * 统计活跃分类行数
     *
     * @param connection 目标数据库连接
     * @return deleted_at 为空的分类行数文本
     */
    private fun countLiveCategories(connection: Connection): String =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM storage_category WHERE deleted_at IS NULL").use { resultSet ->
                resultSet.next()
                resultSet.getString(1)
            }
        }

    /**
     * 统计分片行数
     *
     * @param connection 目标数据库连接
     * @return 分片总行数文本
     */
    private fun countChunks(connection: Connection): String =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM storage_upload_chunk").use { resultSet ->
                resultSet.next()
                resultSet.getString(1)
            }
        }

    /**
     * 插入一条存储配置种子行, 各测试以独立主键与编码互不干扰
     *
     * @param connection 目标数据库连接
     * @param configId 指定配置主键, 保证文件与任务外键可解析
     * @param configCode 本测试专用的配置编码, 避免跨方法撞唯一键
     */
    private fun insertConfigRow(connection: Connection, configId: Long, configCode: String) {
        connection.prepareStatement("INSERT INTO storage_config (config_id, config_code, config_name, storage_type, params, is_default, status) VALUES (?, ?, ?, ?, ?, ?, ?)")
            .use { statement ->
                listOf(configId, configCode, "本地主存储", "local", "{}", false, "enabled")
                    .forEachIndexed { index, parameter -> statement.setObject(index + 1, parameter) }
                statement.executeUpdate()
            }
    }

    /**
     * 插入一条未挂分类的文件记录, 主键由自增生成
     *
     * @param connection 目标数据库连接
     * @param tenantId 租户标识
     * @param configId 归属配置
     * @param sha256 整文件哈希
     */
    private fun insertFileRow(connection: Connection, tenantId: Long, configId: Long, sha256: String) {
        connection.prepareStatement("INSERT INTO storage_file (tenant_id, config_id, original_name, storage_key, url, mime_type, file_size, sha256, file_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
            .use { statement ->
                listOf(tenantId, configId, "demo.png", "$sha256.png", "http://localhost/demo.png", "image/png", 3L, sha256, "image")
                    .forEachIndexed { index, parameter -> statement.setObject(index + 1, parameter) }
                statement.executeUpdate()
            }
    }

    /**
     * 插入一条挂载业务分类的文件记录, 验证分类外键可用
     *
     * @param connection 目标数据库连接
     * @param tenantId 租户标识
     * @param configId 归属配置
     * @param categoryId 挂载的分类
     * @param sha256 整文件哈希
     */
    private fun insertCategorizedFileRow(
        connection: Connection,
        tenantId: Long,
        configId: Long,
        categoryId: Long,
        sha256: String,
    ) {
        connection.prepareStatement("INSERT INTO storage_file (tenant_id, config_id, category_id, original_name, storage_key, url, mime_type, file_size, sha256, file_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            .use { statement ->
                listOf(tenantId, configId, categoryId, "contract.pdf", "$sha256.pdf", "http://localhost/contract.pdf", "application/pdf", 5L, sha256, "document")
                    .forEachIndexed { index, parameter -> statement.setObject(index + 1, parameter) }
                statement.executeUpdate()
            }
    }

    /**
     * 插入一条分类记录, parentId 为空即根节点
     *
     * @param connection 目标数据库连接
     * @param categoryId 指定分类主键
     * @param tenantId 租户标识
     * @param parentId 父分类主键, 根节点传 null
     * @param categoryCode 分类编码
     */
    private fun insertCategoryRow(
        connection: Connection,
        categoryId: Long,
        tenantId: Long,
        parentId: Long?,
        categoryCode: String,
    ) {
        connection.prepareStatement("INSERT INTO storage_category (category_id, tenant_id, parent_id, category_code, category_name, sort_order, status) VALUES (?, ?, ?, ?, ?, ?, ?)")
            .use { statement ->
                listOf(categoryId, tenantId, parentId, categoryCode, "演示分类", 0, "enabled")
                    .forEachIndexed { index, parameter -> statement.setObject(index + 1, parameter) }
                statement.executeUpdate()
            }
    }

    /**
     * 插入一条上传任务
     *
     * @param connection 目标数据库连接
     * @param taskId 指定任务主键
     * @param tenantId 租户标识
     * @param configId 归属配置
     */
    private fun insertTaskRow(connection: Connection, taskId: Long, tenantId: Long, configId: Long) {
        connection.prepareStatement("INSERT INTO storage_upload_task (upload_task_id, tenant_id, config_id, original_name, mime_type, total_size, chunk_size, total_chunks, file_sha256, task_status, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            .use { statement ->
                listOf(taskId, tenantId, configId, "demo.png", "image/png", 10L, 5, 2, CONTENT_SHA256, "uploading", null)
                    .forEachIndexed { index, parameter -> statement.setObject(index + 1, parameter) }
                statement.executeUpdate()
            }
    }

    /**
     * 插入一条分片记录
     *
     * @param connection 目标数据库连接
     * @param tenantId 租户标识
     * @param taskId 所属任务
     * @param chunkNumber 分片序号
     */
    private fun insertChunkRow(connection: Connection, tenantId: Long, taskId: Long, chunkNumber: Int) {
        connection.prepareStatement("INSERT INTO storage_upload_chunk (tenant_id, upload_task_id, chunk_number, chunk_size) VALUES (?, ?, ?, ?)")
            .use { statement ->
                listOf(tenantId, taskId, chunkNumber, 5)
                    .forEachIndexed { index, parameter -> statement.setObject(index + 1, parameter) }
                statement.executeUpdate()
            }
    }

    /**
     * 按租户与内容哈希逻辑删除文件行, 释放秒传唯一键
     *
     * @param connection 目标数据库连接
     * @param tenantId 租户标识
     * @param sha256 整文件哈希
     */
    private fun logicalDeleteFileBySha256(connection: Connection, tenantId: Long, sha256: String) {
        connection.prepareStatement("UPDATE storage_file SET deleted_at = NOW(3) WHERE tenant_id = ? AND sha256 = ?")
            .use { statement ->
                statement.setObject(1, tenantId)
                statement.setObject(2, sha256)
                statement.executeUpdate()
            }
    }

    /**
     * 按租户与编码逻辑删除分类行, 释放分类编码唯一键
     *
     * @param connection 目标数据库连接
     * @param tenantId 租户标识
     * @param categoryCode 分类编码
     */
    private fun logicalDeleteCategoryByCode(connection: Connection, tenantId: Long, categoryCode: String) {
        connection.prepareStatement("UPDATE storage_category SET deleted_at = NOW(3) WHERE tenant_id = ? AND category_code = ?")
            .use { statement ->
                statement.setObject(1, tenantId)
                statement.setObject(2, categoryCode)
                statement.executeUpdate()
            }
    }

    /**
     * 按主键物理删除任务行, 触发分片级联清理
     *
     * @param connection 目标数据库连接
     * @param taskId 任务主键
     */
    private fun deleteTaskById(connection: Connection, taskId: Long) {
        connection.prepareStatement("DELETE FROM storage_upload_task WHERE upload_task_id = ?").use { statement ->
            statement.setObject(1, taskId)
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
        /** 演示用整文件 SHA-256 十六进制值 (64 字符) */
        val CONTENT_SHA256 = "a".repeat(64)

        /** 分类测试专用整文件 SHA-256 十六进制值, 与秒传测试内容隔离 */
        val CATEGORY_FILE_SHA256 = "b".repeat(64)

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
                    ScriptUtils.executeSqlScript(connection, ClassPathResource(MIGRATION_RESOURCE))
                }
            }
        }

        /** Storage 初始 Schema 的迁移版本 */
        const val MIGRATION_RESOURCE = "/db/migration/V001__create_storage_schema.sql"
    }
}
