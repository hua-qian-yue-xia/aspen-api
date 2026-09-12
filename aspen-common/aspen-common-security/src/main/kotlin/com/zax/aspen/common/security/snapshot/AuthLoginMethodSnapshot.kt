package com.zax.aspen.common.security.snapshot

/**
 * 登录方式行的分发快照
 *
 * sys_auth_login_method 单行到快照的映射: 枚举取值以 code 字符串承载
 * (AuthClientKind/AuthLoginMethodType/CaptchaKind 定义在 aspen-auth-api, common
 * 模块不依赖服务 api, 由 Auth 消费侧经枚举查表还原), config 只携带引用别名,
 * 密钥本体一律不进入快照
 */
data class AuthLoginMethodSnapshot(
    /** 登录方式 code, 对应 AuthLoginMethodType; 未知 code 由消费方按损坏行跳过 */
    val method: String,
    /** 验证码闸门 code, 对应 CaptchaKind */
    val captchaKind: String,
    /** 首登是否强制改密, 仅密码系方式有语义, 其余方式恒 false */
    val forceChangeOnFirstLogin: Boolean,
    /** 密码有效期天数, 仅密码系方式有语义, 未配置为 null (按不过期处理) */
    val passwordMaxAgeDays: Int?,
    /** 方式专属配置 (如第三方身份提供方别名), 无配置时为 null */
    val config: Map<String, String>?,
) {
    init {
        require(method.isNotBlank()) { "登录方式快照的 method 不能为空" }
        require(captchaKind.isNotBlank()) { "登录方式快照的 captchaKind 不能为空" }
        if (passwordMaxAgeDays != null) {
            require(passwordMaxAgeDays > 0) { "密码有效期必须为正数天数: $passwordMaxAgeDays" }
        }
    }
}
