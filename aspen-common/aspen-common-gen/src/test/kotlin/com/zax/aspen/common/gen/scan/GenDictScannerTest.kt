package com.zax.aspen.common.gen.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 验证枚举字典扫描器的过滤、映射与冲突拒绝 */
class GenDictScannerTest {
    /** 验证只收集标注 @GenDict 的枚举并按编码升序返回 */
    @Test
    fun `collects only annotated enums sorted by code`() {
        // dictfixture 包内还有未标注的 FixturePlainStatus, 不应出现
        val catalog = GenDictScanner(listOf(DICT_PACKAGE)).scan()

        assertEquals(listOf("fixture_order_status"), catalog.descriptors.map { it.dictCode })
        val order = catalog.descriptors.single()
        assertEquals("订单状态", order.dictName)
        assertEquals("fixture", order.dictGroup)
        assertEquals(listOf("paid", "shipped", "closed"), order.items.map { it.itemValue })
        assertEquals(listOf("已支付", "已发货", "已关闭"), order.items.map { it.itemLabel })
        assertEquals(listOf("success", null, "danger"), order.items.map { it.color })
        assertEquals(listOf(0, 1, 2), order.items.map { it.sortOrder })
    }

    /** 验证多包扫描合并 */
    @Test
    fun `merges multiple base packages`() {
        val catalog = GenDictScanner(listOf(DICT_PACKAGE, AUDIT_PACKAGE)).scan()

        assertEquals(
            listOf("fixture_audit_result", "fixture_order_status"),
            catalog.descriptors.map { it.dictCode },
        )
    }

    /** 验证跨包重复字典编码被整体拒绝 */
    @Test
    fun `rejects duplicated dict codes`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            GenDictScanner(listOf(DICT_PACKAGE, CONFLICT_PACKAGE)).scan()
        }
        assertTrue(exception.message!!.contains("fixture_order_status"))
    }

    /** 验证扫描范围之外的包不产生目录 */
    @Test
    fun `returns empty catalog for unrelated packages`() {
        val catalog = GenDictScanner(listOf("com.zax.aspen.nonexistent")).scan()

        assertTrue(catalog.descriptors.isEmpty())
    }

    private companion object {
        /** 主扫描夹具包 */
        const val DICT_PACKAGE = "com.zax.aspen.common.gen.scan.dictfixture"

        /** 独立分组的审计夹具包 */
        const val AUDIT_PACKAGE = "com.zax.aspen.common.gen.scan.auditfixture"

        /** 与主夹具同码的冲突夹具包 */
        const val CONFLICT_PACKAGE = "com.zax.aspen.common.gen.scan.conflictpackage"
    }
}
