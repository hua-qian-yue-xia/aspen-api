package com.zax.aspen.auth.api.enums.auth

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.core.enums.EnumColor
import com.zax.aspen.common.core.gen.GenDict

/**
 * 登录验证码闸门类型
 *
 * sys_auth_login_method 的 captcha_kind 列取值, 粒度在端×登录方式: 密码登录需要
 * 人机校验而第三方登录不需要; 协议实现归 Auth 的 CaptchaVerifier 端口, 表值只是
 * 选择器; 本地开发无三方凭据时配置 NONE 降级跑通链路
 */
@GenDict(code = "auth_captcha_kind", name = "验证码闸门", group = "auth")
enum class CaptchaKind(
    override val code: String,
    override val description: String,
    override val color: String?,
) : AspenEnum {
    /** 无验证码闸门, 供本地开发降级或已由其他闸门保护的方式 */
    NONE("none", "无验证码", EnumColor.DEFAULT),

    /** 自研图形验证码, 答案哈希存 Redis 一次性令牌 (预留实现, 首版管理端使用 SLIDER) */
    IMAGE("image", "图形验证码", EnumColor.LIME),

    /** 三方行为验证码 (拖动或点选), 服务端向服务商二次校验票据, 凭据走环境变量 */
    SLIDER("slider", "行为验证码", EnumColor.BLUE),
}
