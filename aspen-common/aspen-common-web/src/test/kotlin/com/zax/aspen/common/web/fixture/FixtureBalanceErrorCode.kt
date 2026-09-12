package com.zax.aspen.common.web.fixture

import com.zax.aspen.common.core.error.ErrorCode

/** 模拟业务方自定义错误码的样例, 未登记进公共状态映射, 用于断言默认 400 行为 */
object FixtureBalanceErrorCode : ErrorCode {
    /** 样例业务机器错误码 */
    override val code: String = "FIXTURE.INSUFFICIENT_BALANCE"

    /** 可安全返回给调用方的默认消息 */
    override val defaultMessage: String = "账户余额不足"
}
