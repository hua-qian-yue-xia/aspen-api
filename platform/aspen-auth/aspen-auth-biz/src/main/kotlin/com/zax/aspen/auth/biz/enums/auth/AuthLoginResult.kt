package com.zax.aspen.auth.biz.enums.auth

/**
 * 登录结果分类
 *
 * auth_login_log 的 result 列取值: 成功与失败按原因分类, 失败也必须留痕
 * (账号不存在与密码错误对外同提示, 日志细分仅供内部排查)
 */
enum class AuthLoginResult {
    /** 登录成功, 令牌已签发 */
    SUCCESS,

    /** 账号不存在或密码错误 (对外同提示, 内部 failure_code 区分) */
    FAILED_CREDENTIALS,

    /** 主体处于锁定窗口 */
    FAILED_LOCKED,

    /** 主体被禁用 */
    FAILED_DISABLED,

    /** 验证码闸门未通过或未携带票据 */
    FAILED_CAPTCHA,

    /** 端或登录方式被停用 */
    FAILED_METHOD,

    /** 用户域 SPI 调用失败 */
    FAILED_DEPENDENCY
}
