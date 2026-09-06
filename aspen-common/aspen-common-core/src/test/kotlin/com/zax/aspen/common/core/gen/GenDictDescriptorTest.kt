package com.zax.aspen.common.core.gen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 验证枚举字典目录模型在构造期拒绝非法取值
 */
class GenDictDescriptorTest {
    /** 验证合法目录按原值保存并保留项顺序 */
    @Test
    fun `accepts a valid descriptor`() {
        val descriptor = descriptor()

        assertEquals("user_status", descriptor.dictCode)
        assertEquals("用户状态", descriptor.dictName)
        assertEquals("common", descriptor.dictGroup)
        assertEquals(listOf("enabled", "disabled"), descriptor.items.map { it.itemValue })
        assertEquals(listOf(0, 1), descriptor.items.map { it.sortOrder })
    }

    /** 验证编码与分组只接受小写下划线格式 */
    @Test
    fun `rejects malformed code and group`() {
        listOf("User", "user-status", "1user", "").forEach { bad ->
            assertFailsWith<IllegalArgumentException> { descriptor(dictCode = bad) }
            assertFailsWith<IllegalArgumentException> { descriptor(dictGroup = bad) }
        }
    }

    /** 验证空显示名与空字典项被拒绝 */
    @Test
    fun `rejects blank name and empty items`() {
        assertFailsWith<IllegalArgumentException> { descriptor(dictName = " ") }
        assertFailsWith<IllegalArgumentException> { descriptor(items = emptyList()) }
    }

    /** 验证同字典内存储值重复与非法颜色被拒绝 */
    @Test
    fun `rejects duplicated values and invalid color`() {
        assertFailsWith<IllegalArgumentException> {
            descriptor(
                items = listOf(
                    GenDictItemDescriptor("enabled", "启用", "success", 0),
                    GenDictItemDescriptor("enabled", "启用", null, 1),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GenDictItemDescriptor("enabled", "启用", "红色", 0)
        }
        // EnumColor 令牌与 #RRGGBB 是合法颜色
        assertTrue(GenDictItemDescriptor("enabled", "启用", "danger", 0).color == "danger")
        assertTrue(GenDictItemDescriptor("enabled", "启用", "#FF5733", 0).color == "#FF5733")
    }

    /**
     * 构造默认合法目录
     *
     * @param dictCode 字典编码, 默认 user_status, 仅接受小写下划线格式
     * @param dictName 字典显示名, 默认 用户状态, 不允许空白
     * @param dictGroup 字典分组, 默认 common, 仅接受小写下划线格式
     * @param items 字典项列表, 默认启用与禁用两项, 项值不允许重复
     * @return 由入参组装并通过构造期校验的 GenDictDescriptor
     */
    private fun descriptor(
        dictCode: String = "user_status",
        dictName: String = "用户状态",
        dictGroup: String = "common",
        items: List<GenDictItemDescriptor> = listOf(
            GenDictItemDescriptor("enabled", "启用", "success", 0),
            GenDictItemDescriptor("disabled", "禁用", "danger", 1),
        ),
    ): GenDictDescriptor = GenDictDescriptor(dictCode, dictName, dictGroup, items)
}
