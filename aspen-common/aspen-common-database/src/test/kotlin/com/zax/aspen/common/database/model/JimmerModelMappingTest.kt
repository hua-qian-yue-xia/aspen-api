package com.zax.aspen.common.database.model

import org.babyfish.jimmer.meta.ImmutableType
import org.babyfish.jimmer.sql.Column
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertEquals("deleted", requireNotNull(deletedProp.getAnnotation(Column::class.java)).name)
        assertTrue(logicalDeletedInfo.isDeleted(true))
        assertFalse(logicalDeletedInfo.isDeleted(false))
    }
}
