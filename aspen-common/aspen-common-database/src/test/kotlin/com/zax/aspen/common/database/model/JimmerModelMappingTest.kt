package com.zax.aspen.common.database.model

import org.babyfish.jimmer.meta.ImmutableType
import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 验证公共映射接口进入业务实体后的 Jimmer 元数据语义 */
class JimmerModelMappingTest {
    /** 验证审计字段和 KSP 生成属性完整进入测试实体 */
    @Test
    fun `composes optional mapped superclasses through ksp`() {
        val immutableType = ImmutableType.get(TestEntity::class.java)

        assertEquals("createdAt", immutableType.getProp("createdAt").name)
        assertEquals("updatedAt", immutableType.getProp("updatedAt").name)
        assertEquals("version", TestEntityProps.VERSION.unwrap().name)
        assertEquals("deleted", TestEntityProps.DELETED.unwrap().name)
    }

    /** 验证 version 字段被识别为实体唯一的乐观锁属性 */
    @Test
    fun `maps the version property as optimistic lock metadata`() {
        val immutableType = ImmutableType.get(TestEntity::class.java)
        val versionProp = immutableType.getProp("version")

        assertTrue(versionProp.isVersion)
        assertSame(versionProp, immutableType.versionProp)
    }

    /** 验证 deleted 字段使用布尔逻辑删除语义 */
    @Test
    fun `maps the deleted property as logical deletion metadata`() {
        val immutableType = ImmutableType.get(TestEntity::class.java)
        val deletedProp = immutableType.getProp("deleted")
        val logicalDeletedInfo = requireNotNull(immutableType.logicalDeletedInfo)

        assertTrue(deletedProp.isLogicalDeleted)
        assertSame(deletedProp, logicalDeletedInfo.prop)
        assertNull(deletedProp.getAnnotation(Column::class.java))
        assertTrue(logicalDeletedInfo.isDeleted(true))
        assertFalse(logicalDeletedInfo.isDeleted(false))
    }

    /** 验证自增主键和可更新审计组合完整进入测试实体, 列名由 Jimmer 按属性蛇形自动解析 */
    @Test
    fun `composes auto increment and mutable audit superclasses through ksp`() {
        val immutableType = ImmutableType.get(MutableTestEntity::class.java)

        listOf("id", "createdAt", "createdBy").forEach { propName ->
            assertTrue(propName in immutableType.props)
            assertNull(
                immutableType.getProp(propName).getAnnotation(Column::class.java),
                "$propName 列名与属性蛇形一致, 不应声明 @Column",
            )
        }
    }

    /** 验证 MutableAuditEntity 的乐观锁从 1 开始并使用 deleted_at 时间戳逻辑删除 */
    @Test
    fun `maps mutable audit version and timestamp deletion metadata`() {
        val immutableType = ImmutableType.get(MutableTestEntity::class.java)
        val versionProp = immutableType.getProp("version")
        val deletedAtProp = immutableType.getProp("deletedAt")
        val logicalDeletedInfo = requireNotNull(immutableType.logicalDeletedInfo)

        assertTrue(versionProp.isVersion)
        assertSame(versionProp, immutableType.versionProp)
        assertEquals("1", requireNotNull(versionProp.getAnnotation(Default::class.java)).value)
        assertTrue(deletedAtProp.isLogicalDeleted)
        assertSame(deletedAtProp, logicalDeletedInfo.prop)
        assertNull(deletedAtProp.getAnnotation(Column::class.java))
    }
}
