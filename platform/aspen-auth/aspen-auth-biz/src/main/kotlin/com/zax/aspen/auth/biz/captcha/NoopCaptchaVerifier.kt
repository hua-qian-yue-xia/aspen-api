package com.zax.aspen.auth.biz.captcha

import org.springframework.stereotype.Component

/**
 * 无验证码闸门 (CaptchaKind.NONE)
 *
 * 供本地开发降级与已由其他闸门保护的登录方式使用; 对任何票据 (含 null) 放行
 */
@Component
class NoopCaptchaVerifier : CaptchaVerifier {
    /**
     * 直接放行, 不消费票据
     *
     * @param captchaToken 前端票据, 本闸门忽略
     */
    override fun verify(captchaToken: String?) = Unit
}
