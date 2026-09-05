package example.cache

/** 模拟 Aspen 类型白名单之外的反序列化载荷 */
data class UntrustedPayload(
    /** 保存用于触发白名单校验的测试内容 */
    val value: String,
)
