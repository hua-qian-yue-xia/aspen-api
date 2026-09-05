package com.zax.aspen.common.core.enums

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.core.enums.common.Gender
import com.zax.aspen.common.core.enums.common.RiskLevel
import com.zax.aspen.common.core.enums.common.SortDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 验证公共枚举遵守 AspenEnum 契约与取值约定 */
class AspenEnumTest {
    /** enums.common 下的全部通用枚举, 新增通用枚举时必须登记以接受契约检查 */
    private val enumClasses = listOf(Gender::class, EnabledStatus::class, SortDirection::class, RiskLevel::class)

    /** 验证通用枚举全部位于 enums.common 子包, 与契约和工具类型分层 */
    @Test
    fun `places common enums in the common sub package`() {
        enumClasses.forEach { enumClass ->
            assertEquals("com.zax.aspen.common.core.enums.common", enumClass.java.packageName, "${enumClass.simpleName} 不在 enums.common 包")
        }
    }

    /** 验证公共枚举的存储值唯一、小写且与数据库既有列值一致 */
    @Test
    fun `keeps codes unique and lowercase`() {
        enumClasses.forEach { enumClass ->
            val codes = enumClass.java.enumConstants.map { (it as AspenEnum).code }

            assertEquals(codes.size, codes.toSet().size, "${enumClass.simpleName} 存在重复 code")
            codes.forEach { code ->
                assertTrue(code.matches(Regex("[a-z][a-z0-9_]*")), "${enumClass.simpleName} 的 code 必须为小写下划线格式: $code")
            }
        }
    }

    /** 验证公共枚举的描述非空且为中文, 颜色为合法调色板令牌或十六进制色值 */
    @Test
    fun `keeps descriptions chinese and colors valid`() {
        enumClasses.forEach { enumClass ->
            enumClass.java.enumConstants.map { it as AspenEnum }.forEach { item ->
                assertTrue(item.description.isNotBlank(), "${enumClass.simpleName}.${item.code} 缺少描述")
                assertTrue(
                    item.description.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN },
                    "${enumClass.simpleName}.${item.code} 的描述必须为中文: ${item.description}",
                )
                item.color?.let { color ->
                    assertTrue(EnumColor.isValid(color), "${enumClass.simpleName}.${item.code} 的颜色不合法: $color")
                }
            }
        }
    }

    /** 验证性别取值语义对齐 ISO/IEC 5218 且不着色 */
    @Test
    fun `aligns gender values with iso 5218`() {
        assertEquals("unknown", Gender.UNKNOWN.code)
        assertEquals("male", Gender.MALE.code)
        assertEquals("female", Gender.FEMALE.code)
        assertEquals("not_applicable", Gender.NOT_APPLICABLE.code)
        assertEquals("男", Gender.MALE.description)
        assertNull(Gender.MALE.color)
    }

    /** 验证通用启停状态取值与语义色 */
    @Test
    fun `defines enabled status values`() {
        assertEquals("enabled", EnabledStatus.ENABLED.code)
        assertEquals("disabled", EnabledStatus.DISABLED.code)
        assertEquals(EnumColor.SUCCESS, EnabledStatus.ENABLED.color)
        assertEquals(EnumColor.DANGER, EnabledStatus.DISABLED.color)
    }

    /** 验证排序方向对齐 SQL 关键字且不着色 */
    @Test
    fun `aligns sort direction with sql keywords`() {
        assertEquals("asc", SortDirection.ASC.code)
        assertEquals("desc", SortDirection.DESC.code)
        assertNull(SortDirection.ASC.color)
    }

    /** 验证风险等级按严重度递增排列并使用递增的语义色 */
    @Test
    fun `orders risk levels by severity`() {
        assertEquals(listOf("low", "normal", "high", "critical"), RiskLevel.entries.map { it.code })
        assertTrue(RiskLevel.HIGH.ordinal > RiskLevel.NORMAL.ordinal)
        assertEquals(EnumColor.DANGER, RiskLevel.CRITICAL.color)
    }

    /** 验证按 code 反查在命中、未命中和必填三种场景下的行为 */
    @Test
    fun `looks up enums by code`() {
        assertEquals(Gender.FEMALE, AspenEnums.codeOf<Gender>("female"))
        assertNull(AspenEnums.codeOf<Gender>("other"))
        assertEquals(EnabledStatus.DISABLED, AspenEnums.requireOf<EnabledStatus>("disabled"))

        val error = assertFailsWith<IllegalArgumentException> { AspenEnums.requireOf<EnabledStatus>("paused") }
        assertTrue(error.message!!.contains("EnabledStatus"))
        assertTrue(error.message!!.contains("paused"))
    }

    /** 验证调色板令牌校验接受令牌与十六进制色值, 拒绝其他自由文本 */
    @Test
    fun `validates palette tokens and hex colors`() {
        assertTrue(EnumColor.isValid(EnumColor.MAGENTA))
        assertTrue(EnumColor.isValid("#1F6FEB"))
        assertTrue(EnumColor.TOKENS.size == 16)
        listOf("Red", "#FFF", "rgb(1,2,3)", "").forEach { invalid ->
            assertTrue(!EnumColor.isValid(invalid), "不应接受: $invalid")
        }
    }
}
