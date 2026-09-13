package com.zax.aspen.auth.biz.captcha

import com.zax.aspen.auth.api.enums.auth.CaptchaKind
import com.zax.aspen.common.core.error.BusinessException
import com.zax.aspen.common.core.error.CommonErrorCode
import org.springframework.stereotype.Component

/**
 * 验证码闸门分派器: 按 CaptchaKind 选择实现
 *
 * 表值是选择器, 引擎经本组件取实现; 未落地的闸门 (IMAGE 预留) 命中即
 * fail-closed, 不允许静默放行
 */
@Component
class CaptchaGateways(
    private val noopCaptchaVerifier: NoopCaptchaVerifier,
    private val behaviorCaptchaVerifier: BehaviorCaptchaVerifier,
) {
    /**
     * 按闸门类型取验证器
     *
     * @param kind 端×登录方式策略声明的闸门类型
     * @return 对应实现; 未落地的闸门抛出拒绝
     * @throws BusinessException 闸门类型尚未实现时 fail-closed
     */
    fun byKind(kind: CaptchaKind): CaptchaVerifier = when (kind) {
        CaptchaKind.NONE -> noopCaptchaVerifier
        CaptchaKind.SLIDER -> behaviorCaptchaVerifier
        CaptchaKind.IMAGE -> throw BusinessException(CommonErrorCode.STATE_CONFLICT, "图形验证码闸门尚未实现")
    }
}
