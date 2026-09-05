package com.zax.aspen.common.database.enums

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.core.enums.common.Gender
import org.babyfish.jimmer.sql.runtime.ScalarProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 验证 AspenEnum 桥接转换器的双向映射与失败语义 */
class AspenEnumProvidersTest {
    /** 验证枚举按 code 转为数据库存储值 */
    @Test
    fun `converts enums to code strings`() {
        val genderProvider = provider(Gender::class.java)
        val statusProvider = provider(EnabledStatus::class.java)

        assertEquals("male", genderProvider.toSql(Gender.MALE))
        assertEquals("not_applicable", genderProvider.toSql(Gender.NOT_APPLICABLE))
        assertEquals("enabled", statusProvider.toSql(EnabledStatus.ENABLED))
        assertEquals("disabled", statusProvider.toSql(EnabledStatus.DISABLED))
    }

    /** 验证数据库存储值按 code 反查为枚举 */
    @Test
    fun `converts code strings back to enums`() {
        val genderProvider = provider(Gender::class.java)
        val statusProvider = provider(EnabledStatus::class.java)

        assertEquals(Gender.FEMALE, genderProvider.toScalar("female"))
        assertEquals(EnabledStatus.DISABLED, statusProvider.toScalar("disabled"))
    }

    /** 验证未知存储值被拒绝, 不静默回退默认枚举 */
    @Test
    fun `rejects unknown code strings`() {
        val genderProvider = provider(Gender::class.java)

        assertFailsWith<Exception> { genderProvider.toScalar("other") }
    }

    /** 创建按 code 映射的转换器 */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Enum<T>> provider(enumType: Class<T>): ScalarProvider<T, String> =
        AspenEnumProviders.create(enumType) as ScalarProvider<T, String>
}
