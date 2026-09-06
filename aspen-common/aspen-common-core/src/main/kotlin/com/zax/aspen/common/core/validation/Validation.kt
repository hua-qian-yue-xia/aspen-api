package com.zax.aspen.common.core.validation

/**
 * 提供不依赖校验框架的纯值校验方法
 */
object Validation {
    /**
     * 校验文本非空, 长度受限且不包含控制字符
     *
     * @param value 待校验的原始文本
     * @param fieldName 字段名, 用于拼装校验失败消息, 不能为空白
     * @param maxLength 允许的最大字符数, 必须大于等于 1
     * @return 校验通过的原始文本, 恒不为 `null`
     * @throws IllegalArgumentException fieldName 为空白、maxLength 小于 1, 或 value 为空白、长度超过 maxLength、包含控制字符时抛出
     */
    fun requireSafeText(value: String, fieldName: String, maxLength: Int): String {
        require(fieldName.isNotBlank()) { "fieldName 不能为空" }
        require(maxLength >= 1) { "maxLength 必须为正数" }
        require(value.isNotBlank()) { "$fieldName 不能为空" }
        require(value.length <= maxLength) { "$fieldName 长度不能超过 $maxLength 个字符" }
        require(value.none(Char::isISOControl)) { "$fieldName 不能包含控制字符" }
        return value
    }
}
