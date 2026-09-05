package com.zax.aspen.common.cache.support

import com.zax.aspen.common.core.error.BusinessException
import com.zax.aspen.common.core.error.CommonErrorCode

/** 表示 Redis 访问或缓存序列化失败, 调用方必须显式选择降级策略 */
class CacheAccessException(
    /** 可以安全记录和返回的缓存失败详情 */
    detail: String,
    /** 用于诊断的原始异常 */
    cause: Throwable? = null,
) : BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE, detail, cause)
