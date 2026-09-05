package com.zax.aspen.common.core.validation

/** 提供不依赖校验框架的纯值校验方法 */
object Validation {
    /** 校验文本非空, 长度受限且不包含控制字符 */
    fun requireSafeText(value: String, fieldName: String, maxLength: Int): String {
        require(fieldName.isNotBlank()) { "fieldName 不能为空" }
        require(maxLength >= 1) { "maxLength 必须为正数" }
        require(value.isNotBlank()) { "$fieldName 不能为空" }
        require(value.length <= maxLength) { "$fieldName 长度不能超过 $maxLength 个字符" }
        require(value.none(Char::isISOControl)) { "$fieldName 不能包含控制字符" }
        return value
    }
}
