package com.zax.aspen.storage.biz.entity

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.storage.api.enums.UploadTaskStatus
import org.babyfish.jimmer.meta.ImmutableType
import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.IdView
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.OneToMany
import org.babyfish.jimmer.sql.Table
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 验证 Storage Jimmer 模型的表所有权、命名和公共元数据语义
 */
class StorageEntityMappingTest {
    /** 验证 Storage 模型包含 5 个实体且全部映射到 storage_ 表 */
    @Test
    fun `maps all entities to storage tables`() {
        assertEquals(5, entityClasses.size)

        entityClasses.forEach { entityClass ->
            val tableName = assertNotNull(entityClass.getAnnotation(Table::class.java)).name
            assertTrue(tableName.startsWith("storage_"), "$tableName 不属于 Storage")
        }
    }

    /** 验证列名与属性名蛇形一致时不声明 Column 注解, 由 Jimmer 自动解析 */
    @Test
    fun `resolves column names without explicit annotations`() {
        entityClasses.forEach { entityClass ->
            val type = ImmutableType.get(entityClass)
            type.props.values.forEach { prop ->
                assertNull(prop.getAnnotation(Column::class.java), "${entityClass.simpleName}.${prop.name} 的列名与属性蛇形一致, 不应声明 @Column")
            }
        }
    }

    /** 验证实体按数据语义组合审计原子: 文件/配置/分类全量治理, 任务可更新无删除审计, 分片只建审计 */
    @Test
    fun `maps audit atoms by data semantics`() {
        val configType = ImmutableType.get(StorageConfigEntity::class.java)
        val fileType = ImmutableType.get(StorageFileEntity::class.java)
        val categoryType = ImmutableType.get(StorageCategoryEntity::class.java)

        listOf(configType, fileType, categoryType).forEach { type ->
            val versionProp = assertNotNull(type.getProp("version"), "配置、文件与分类实体必须声明乐观锁列")
            assertTrue(versionProp.isVersion, "version 必须是乐观锁列")
            assertSame(versionProp, type.versionProp)
            assertEquals("deletedAt", assertNotNull(type.logicalDeletedInfo).prop.name)
        }

        val taskType = ImmutableType.get(StorageUploadTaskEntity::class.java)
        assertNotNull(taskType.versionProp, "任务表必须声明乐观锁保护状态机迁移")
        assertNull(taskType.logicalDeletedInfo, "任务表是过程数据, 不应声明逻辑删除")

        val chunkType = ImmutableType.get(StorageUploadChunkEntity::class.java)
        assertNull(chunkType.versionProp, "分片是不可变行, 不应声明乐观锁")
        assertNull(chunkType.logicalDeletedInfo, "分片随任务级联物理删除, 不应声明逻辑删除")
    }

    /** 验证配置、分类与任务的业务默认值已声明到 Jimmer 元数据 */
    @Test
    fun `maps source defaults into metadata`() {
        val configType = ImmutableType.get(StorageConfigEntity::class.java)
        val categoryType = ImmutableType.get(StorageCategoryEntity::class.java)
        val taskType = ImmutableType.get(StorageUploadTaskEntity::class.java)

        assertEquals(EnabledStatus.ENABLED, configType.getProp("status").defaultValueRef.value)
        assertEquals(false, configType.getProp("isDefault").defaultValueRef.value)
        assertEquals(EnabledStatus.ENABLED, categoryType.getProp("status").defaultValueRef.value)
        assertEquals(0, categoryType.getProp("sortOrder").defaultValueRef.value)
        assertEquals(UploadTaskStatus.UPLOADING, taskType.getProp("taskStatus").defaultValueRef.value)
    }

    /** 验证每个 Jimmer 实体属性蛇形后的列与迁移表字段一一对应, 关联与集合属性不映射列, 不参与对比 */
    @Test
    fun `keeps entity columns aligned with the migration`() {
        entityClasses.forEach { entityClass ->
            val type = ImmutableType.get(entityClass)
            val tableName = assertNotNull(entityClass.getAnnotation(Table::class.java)).name
            val tableBody = assertNotNull(
                Regex(
                    "CREATE TABLE `$tableName` \\((.*?)\\) ENGINE",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                ).find(migrationSql),
                "迁移中缺少 $tableName",
            ).groupValues[1]
            val migrationColumns = Regex("^\\s*`([^`]+)`\\s+", RegexOption.MULTILINE)
                .findAll(tableBody)
                .map { it.groupValues[1] }
                .toSet()
            val entityColumns = type.props.values
                .filter { prop ->
                    prop.getAnnotation(ManyToOne::class.java) == null &&
                        prop.getAnnotation(OneToMany::class.java) == null
                }
                .map { prop -> prop.name.toSnakeCase() }
                .toSet()

            assertEquals(migrationColumns, entityColumns, "$tableName 的 Entity 与迁移字段不一致")
        }
    }

    /** 验证关联声明规则: 大量表只声明引用侧, 分类树按 sys_dict_item 惯例加有界 children 集合 */
    @Test
    fun `declares associations by data volume semantics`() {
        val expectedShapes = mapOf(
            StorageConfigEntity::class.java to (0 to 0),
            StorageCategoryEntity::class.java to (1 to 1),
            StorageFileEntity::class.java to (2 to 0),
            StorageUploadTaskEntity::class.java to (1 to 0),
            StorageUploadChunkEntity::class.java to (1 to 0),
        )

        entityClasses.forEach { entityClass ->
            val type = ImmutableType.get(entityClass)
            val manyToOneProps = type.props.values.filter { it.getAnnotation(ManyToOne::class.java) != null }
            val oneToManyProps = type.props.values.filter { it.getAnnotation(OneToMany::class.java) != null }
            val (expectedReferences, expectedCollections) = assertNotNull(expectedShapes[entityClass])

            assertEquals(expectedReferences, manyToOneProps.size, "${entityClass.simpleName} 的 ManyToOne 数量不符")
            assertEquals(
                expectedCollections,
                oneToManyProps.size,
                "${entityClass.simpleName} 的 OneToMany 数量不符 (只有有界小集合允许反向集合)",
            )

            type.props.values.forEach { prop ->
                val idView = prop.getAnnotation(IdView::class.java)
                if (idView != null) {
                    val target = assertNotNull(type.props[idView.value], "${entityClass.simpleName}.${prop.name} 的 IdView 指向不存在的关联 ${idView.value}")
                    assertNotNull(target.getAnnotation(ManyToOne::class.java), "${entityClass.simpleName}.${idView.value} 必须是 ManyToOne")
                }
            }
        }
    }

    /**
     * 把属性名按 Jimmer 默认策略转为蛇形列名
     *
     * @return 小写并以下划线分隔的列名
     */
    private fun String.toSnakeCase(): String = replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()

    /** 从测试类路径读取版本化 Storage 迁移脚本 */
    private val migrationSql: String by lazy {
        val resource = assertNotNull(javaClass.getResourceAsStream(MIGRATION_RESOURCE))
        resource.bufferedReader().use { it.readText() }
    }

    /** Storage 首版全部实体类型, 用于防止表模型静默遗漏 */
    private val entityClasses = listOf(
        StorageConfigEntity::class.java,
        StorageCategoryEntity::class.java,
        StorageFileEntity::class.java,
        StorageUploadTaskEntity::class.java,
        StorageUploadChunkEntity::class.java,
    )

    /** 保存测试使用的资源常量 */
    private companion object {
        /** Storage 初始 Schema 的迁移版本 */
        const val MIGRATION_RESOURCE = "/db/migration/V001__create_storage_schema.sql"
    }
}
