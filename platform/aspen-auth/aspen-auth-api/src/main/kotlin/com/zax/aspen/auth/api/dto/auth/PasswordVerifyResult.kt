package com.zax.aspen.auth.api.dto.auth

import com.zax.aspen.auth.api.enums.auth.PrincipalStatus

/**
 * 密码校验结果
 *
 * Principal SPI verifyPassword 的返回体: 状态归用户域判定, principal 仅在 OK 时
 * 携带; Auth 对 NOT_FOUND 与 BAD_CREDENTIALS 采用相同的对外提示 (不暴露账号存在性),
 * 差异只用于用户域内部的失败计数
 */
data class PasswordVerifyResult(
    /** 校验结果状态 */
    val status: PasswordVerifyStatus,
    /** 校验通过时的主体最小视图, 非 OK 时为 null */
    val principal: UserPrincipalDto?,
)
