package com.zax.aspen.common.database.enums

import com.zax.aspen.common.core.enums.AspenEnum
import org.babyfish.jimmer.sql.runtime.EnumProviderBuilder
import org.babyfish.jimmer.sql.runtime.ScalarProvider

/** 为 AspenEnum 枚举构建按 code 映射的 Jimmer 标量转换器 */
object AspenEnumProviders {
    /**
     * 按枚举 code 与数据库小写字符串互转
     *
     * 枚举定义保持纯净(不标注 Jimmer 注解, 可被 api 契约引用),
     * 持久化映射统一由本工厂在 common-database 桥接
     */
    @Suppress("UNCHECKED_CAST")
    fun create(enumType: Class<*>): ScalarProvider<*, *> =
        createInternal(enumType as Class<Nothing>)

    /** 通过具体泛型构造按 code 的双向映射 */
    private fun <T> createInternal(enumType: Class<T>): ScalarProvider<T, String> where T : Enum<T>, T : AspenEnum =
        EnumProviderBuilder.of(enumType, String::class.java) { it.code }.build()
}
