package com.zax.aspen.auth.biz.captcha

/**
 * 验证码闸门端口: 按令牌完成人机校验
 *
 * 实现按 CaptchaKind 分派 (NONE/IMAGE/SLIDER); 表值只是选择器, 协议实现归
 * 各实现类; 校验失败或未携带票据一律抛 BusinessException, 认证引擎只消费
 * 通过/拒绝两种结果
 */
interface CaptchaVerifier {
    /**
     * 校验前端提交的验证码票据
     *
     * @param captchaToken 前端票据, 闸门要求非 NONE 时为空即拒绝
     * @throws com.zax.aspen.common.core.error.BusinessException 票据缺失、校验不通过或闸门未接线时拒绝
     */
    fun verify(captchaToken: String?)
}
