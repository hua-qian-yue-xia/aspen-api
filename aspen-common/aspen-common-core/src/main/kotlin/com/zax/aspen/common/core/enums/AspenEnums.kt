package com.zax.aspen.common.core.enums

/** AspenEnum 的通用查找工具, 所有枚举零样板复用, 不在各枚举重复编写 companion 反查方法 */
object AspenEnums {
    /** 按 code 反查枚举, 未命中返回 null, 适合可选值或宽松解析场景 */
    inline fun <reified T> codeOf(code: String): T? where T : Enum<T>, T : AspenEnum =
        enumValues<T>().firstOrNull { it.code == code }

    /** 按 code 反查枚举, 未命中抛出携带枚举名的非法参数异常, 适合必填值的快速失败解析 */
    inline fun <reified T> requireOf(code: String): T where T : Enum<T>, T : AspenEnum =
        codeOf<T>(code) ?: throw IllegalArgumentException("未知的 ${T::class.simpleName} 存储值: $code")
}
