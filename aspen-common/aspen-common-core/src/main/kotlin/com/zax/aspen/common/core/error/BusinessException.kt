package com.zax.aspen.common.core.error

/**
 * 表示可以映射为 Problem Details 响应的服务层失败
 *
 * [detail] 必须可以安全返回给调用方, 敏感诊断信息只能写入日志或保存在 [cause] 中
 *
 * @property errorCode 稳定的机器错误契约
 * @property detail 可以安全返回给调用方的错误详情
 */
open class BusinessException(
    val errorCode: ErrorCode,
    val detail: String = errorCode.defaultMessage,
    /** 保留给日志和诊断链路的原始异常 */
    cause: Throwable? = null,
) : RuntimeException(detail, cause)
