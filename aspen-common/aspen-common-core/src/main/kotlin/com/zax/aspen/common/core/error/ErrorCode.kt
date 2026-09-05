package com.zax.aspen.common.core.error

/**
 * 定义服务 API 模块共享的稳定机器错误契约
 *
 * HTTP 状态映射由 Web 公共模块负责
 */
interface ErrorCode {
    /** 稳定且不可复用的机器错误码 */
    val code: String

    /** 可以安全返回给调用方的默认消息 */
    val defaultMessage: String
}
