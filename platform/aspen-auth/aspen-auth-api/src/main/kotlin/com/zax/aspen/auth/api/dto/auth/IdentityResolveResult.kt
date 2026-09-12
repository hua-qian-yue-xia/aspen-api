package com.zax.aspen.auth.api.dto.auth

/**
 * 第三方身份解析结果
 *
 * Principal SPI resolveByIdentity 的返回体: FOUND 携带既有主体; CREATED 携带
 * 本次幂等建号的新主体 (重复请求返回既有主体, 不产生第二个账号); NOT_FOUND 表示
 * 身份不存在且策略不允许建号
 */
data class IdentityResolveResult(
    /** 解析结果状态 */
    val status: IdentityResolveStatus,
    /** 解析或建号得到的主体最小视图, NOT_FOUND 时为 null */
    val principal: UserPrincipalDto?,
)
