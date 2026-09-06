package com.zax.aspen.common.cache.key

/** 定义不包含部署前缀, 环境和服务名的业务缓存 Key */
data class CacheKey(
    /** 所属的服务内业务组 */
    val group: String,
    /** 所属的业务领域 */
    val domain: String,
    /** 唯一定位缓存对象的一个或多个标识 */
    val identifiers: List<String>,
) {
    // 构造业务 Key 时立即拒绝会导致歧义或碰撞的分段
    init {
        CacheKeyRules.requireSegment(group, "group")
        CacheKeyRules.requireSegment(domain, "domain")
        require(identifiers.isNotEmpty()) { "Cache Key 必须包含至少一个业务标识" }
        identifiers.forEachIndexed { index, identifier ->
            CacheKeyRules.requireSegment(identifier, "identifiers[$index]", MAX_IDENTIFIER_LENGTH)
        }
    }

    /**
     * 支持使用可变参数快速创建缓存 Key
     *
     * @param group 所属的服务内业务组
     * @param domain 所属的业务领域
     * @param identifiers 唯一定位缓存对象的一个或多个标识
     */
    constructor(group: String, domain: String, vararg identifiers: String) :
        this(group, domain, identifiers.toList())

    /** 保存缓存标识长度上限 */
    companion object {
        /** 单个业务标识允许的最大长度 */
        private const val MAX_IDENTIFIER_LENGTH = 256
    }
}

/** 集中维护 Redis Key 段的语法规则 */
internal object CacheKeyRules {
    /** 允许在 Key 段中使用的字符集合 */
    private val segmentPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

    /**
     * 校验单个 Key 段并返回原值
     *
     * @param value 待校验的 Key 段内容
     * @param fieldName 校验失败时用于错误提示的字段名
     * @param maxLength 该段允许的最大字符长度
     * @return 校验通过的原始值
     */
    fun requireSegment(value: String, fieldName: String, maxLength: Int = 64): String {
        require(value.length <= maxLength) { "$fieldName 长度不能超过 $maxLength 个字符" }
        require(segmentPattern.matches(value)) {
            "$fieldName 必须以 ASCII 字母或数字开头, 且只能包含字母、数字、'.'、'_' 或 '-'"
        }
        return value
    }
}
