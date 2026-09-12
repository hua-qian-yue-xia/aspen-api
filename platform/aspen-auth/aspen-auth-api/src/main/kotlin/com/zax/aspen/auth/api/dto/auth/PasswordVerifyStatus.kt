package com.zax.aspen.auth.api.dto.auth

/**
 * 密码校验的状态分类
 *
 * 用户域 verifyPassword 的判定结果: 失败计数与锁定窗口状态机由用户域持有,
 * Auth 消费状态并决定登录回执, 不做二次推断
 */
enum class PasswordVerifyStatus {
    /** 账号存在且密码正确, principal 携带主体 */
    OK,

    /** 账号不存在, Auth 侧与 BAD_CREDENTIALS 同提示, 不暴露账号存在性 */
    NOT_FOUND,

    /** 密码错误, 失败计数已由用户域记录 */
    BAD_CREDENTIALS,

    /** 主体处于锁定窗口 (如失败次数超限), 拒绝且不计入新一轮失败 */
    LOCKED
}
