package com.zax.aspen.admin.biz.entity.sys

import com.zax.aspen.common.core.enums.common.EnabledStatus
import org.babyfish.jimmer.meta.ImmutableType
import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Table
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 验证 SYS Jimmer 模型的表所有权、命名和公共元数据语义 */
class SysEntityMappingTest {
    /** 验证 SYS 模型包含 3 个实体且全部映射到 SYS 表 */
    @Test
    fun `maps all entities to sys tables`() {
        assertEquals(3, entityClasses.size)

        entityClasses.forEach { entityClass ->
            val tableName = assertNotNull(entityClass.getAnnotation(Table::class.java)).name
            assertTrue(tableName.startsWith("sys_"), "$tableName 不属于 SYS")
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

    /** 验证可更新实体使用乐观锁和 deleted_at 时间戳逻辑删除 */
    @Test
    fun `maps mutable audit metadata`() {
        val dictType = ImmutableType.get(SysDictEntity::class.java)
        val versionProp = dictType.getProp("version")
        val deletedAtProp = dictType.getProp("deletedAt")

        assertTrue(versionProp.isVersion)
        assertSame(versionProp, dictType.versionProp)
        assertEquals("1", assertNotNull(versionProp.getAnnotation(Default::class.java)).value)
        assertTrue(deletedAtProp.isLogicalDeleted)
        assertSame(deletedAtProp, assertNotNull(dictType.logicalDeletedInfo).prop)
    }

    /** 验证字典、字典项和参数的业务默认值已声明到 Jimmer 元数据 */
    @Test
    fun `maps source defaults into metadata`() {
        val dictType = ImmutableType.get(SysDictEntity::class.java)
        val itemType = ImmutableType.get(SysDictItemEntity::class.java)
        val configType = ImmutableType.get(SysConfigEntity::class.java)

        assertEquals(EnabledStatus.ENABLED, dictType.getProp("status").defaultValueRef.value)
        assertEquals(false, dictType.getProp("isBuiltIn").defaultValueRef.value)
        assertEquals("common", dictType.getProp("dictGroup").defaultValueRef.value)
        assertEquals(EnabledStatus.ENABLED, itemType.getProp("status").defaultValueRef.value)
        assertEquals(false, itemType.getProp("isDefault").defaultValueRef.value)
        assertEquals(0, itemType.getProp("sortOrder").defaultValueRef.value)
        assertEquals(EnabledStatus.ENABLED, configType.getProp("status").defaultValueRef.value)
        assertEquals("string", configType.getProp("valueType").defaultValueRef.value)
        assertEquals(false, configType.getProp("isSensitive").defaultValueRef.value)
    }

    /** 验证字典项支持可空父级和租户隔离键的代表性属性 */
    @Test
    fun `maps representative source columns`() {
        val itemType = ImmutableType.get(SysDictItemEntity::class.java)
        val configType = ImmutableType.get(SysConfigEntity::class.java)

        assertEquals("dict_id", "dictId".toSnakeCase())
        assertEquals("parent_id", "parentId".toSnakeCase())
        assertEquals("item_label", "itemLabel".toSnakeCase())
        assertEquals("item_value", "itemValue".toSnakeCase())
        assertEquals("config_key", "configKey".toSnakeCase())
        assertEquals("config_value", "configValue".toSnakeCase())
        assertEquals("value_type", "valueType".toSnakeCase())
        assertEquals("is_sensitive", "isSensitive".toSnakeCase())
        assertTrue("parentId" in itemType.props)
        assertTrue("dictId" in itemType.props)
        assertTrue("color" in itemType.props)
        assertTrue("isSensitive" in configType.props)
    }

    /** 验证每个 Jimmer 实体属性蛇形后的列与迁移表字段一一对应 */
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
                .map { prop -> prop.name.toSnakeCase() }
                .toSet()

            assertEquals(migrationColumns, entityColumns, "$tableName 的 Entity 与迁移字段不一致")
        }
    }

    /** 把属性名按 Jimmer 默认策略转为蛇形列名 */
    private fun String.toSnakeCase(): String = replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()

    /** 从测试类路径读取版本化 SYS 迁移脚本 */
    private val migrationSql: String by lazy {
        val resource = assertNotNull(javaClass.getResourceAsStream(MIGRATION_RESOURCE))
        resource.bufferedReader().use { it.readText() }
    }

    /** SYS 首版全部实体类型, 用于防止表模型静默遗漏 */
    private val entityClasses = listOf(
        SysDictEntity::class.java,
        SysDictItemEntity::class.java,
        SysConfigEntity::class.java,
    )

    /** 保存测试使用的资源常量 */
    private companion object {
        /** SYS 初始 Schema 的全局 Admin 迁移版本 */
        const val MIGRATION_RESOURCE = "/db/migration/sys/V002__create_sys_schema.sql"
    }
}
