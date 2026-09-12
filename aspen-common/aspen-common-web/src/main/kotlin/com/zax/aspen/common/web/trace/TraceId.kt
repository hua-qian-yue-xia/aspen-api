package com.zax.aspen.common.web.trace

import java.security.SecureRandom
import java.util.HexFormat

/**
 * Trace ID 的常量约定与生成器
 *
 * 排查闭环: 每请求一个 traceId, 由 [AspenTraceIdFilter] 写入 MDC 与响应头,
 * 日志模式经 `%X{traceId}` 取值使该请求期间每一行日志携带同一标识;
 * 用户报出 traceId 即可按日志检索还原完整请求链路。
 * 网关最终负责跨服务透传 (请求头 [HEADER]); 服务侧只做「有 header 用 header、
 * 无则自生成」的兜底, 因此单服务直连开发同样可用
 */
object TraceId {
    /** 跨服务透传与响应回写的请求/响应头名称 */
    const val HEADER = "X-Trace-Id"

    /** 写入 MDC 供日志模式取值的 key, 同时是 Problem Details 扩展字段名 */
    const val MDC_KEY = "traceId"

    /** 外来 traceId 的合法字符与长度约束, 拒绝空白/控制字符/超长值, 防止日志注入 */
    private val incomingPattern = Regex("^[A-Za-z0-9_-]{1,64}$")

    private val random = SecureRandom()

    /**
     * 生成新的 traceId
     *
     * @return 8 字节 SecureRandom 随机数的 16 位小写十六进制表示, 短且可抄写
     */
    fun generate(): String {
        val bytes = ByteArray(8)
        random.nextBytes(bytes)
        return HexFormat.of().formatHex(bytes)
    }

    /**
     * 校验外来 traceId 是否可安全进入日志与响应
     *
     * @param candidate 请求头携带的原始值
     * @return 合法时返回原值; 非法 (空白/控制字符/超长) 时返回 null, 由调用方改为生成新值
     */
    fun sanitize(candidate: String?): String? = candidate?.takeIf { incomingPattern.matches(it) }
}
