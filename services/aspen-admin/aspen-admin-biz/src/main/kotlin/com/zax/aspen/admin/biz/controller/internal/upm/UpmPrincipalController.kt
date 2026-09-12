package com.zax.aspen.admin.biz.controller.internal.upm

import com.zax.aspen.admin.biz.service.upm.UpmPrincipalService
import com.zax.aspen.auth.api.contract.auth.AuthPrincipalApi
import com.zax.aspen.auth.api.dto.auth.IdentityResolveRequest
import com.zax.aspen.auth.api.dto.auth.IdentityResolveResult
import com.zax.aspen.auth.api.dto.auth.PasswordVerifyRequest
import com.zax.aspen.auth.api.dto.auth.PasswordVerifyResult
import com.zax.aspen.auth.api.dto.auth.UserPrincipalDto
import com.zax.aspen.common.core.error.BusinessException
import com.zax.aspen.common.core.error.CommonErrorCode
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * 认证主体 SPI 的管理端实现
 *
 * 属 internal 契约 (AuthPrincipalApi), 不经网关暴露, 供 aspen-auth-biz 在
 * 登录与刷新链路中调用; v1 依赖「内网可达 + /internal 路径不经网关」兜底,
 * 服务身份随批次 4 的网关信任链收紧; 密码明文仅在本端点内流转, 不落日志
 */
@RestController
class UpmPrincipalController(
    private val upmPrincipalService: UpmPrincipalService,
) : AuthPrincipalApi {
    /**
     * 校验账号密码并返回主体判定
     *
     * @param request 登录账号与密码明文
     * @return 校验状态与通过时的主体最小视图
     */
    override fun verifyPassword(@RequestBody request: PasswordVerifyRequest): PasswordVerifyResult =
        upmPrincipalService.verifyPassword(request)

    /**
     * 按第三方身份查找绑定主体, 管理端只认已绑定账号
     *
     * @param request 外部身份标识与建号策略
     * @return FOUND 携带绑定主体或 NOT_FOUND
     */
    override fun resolveByIdentity(@RequestBody request: IdentityResolveRequest): IdentityResolveResult =
        upmPrincipalService.resolveByIdentity(request)

    /**
     * 按主体标识复查主体状态
     *
     * @param principalId 用户域内主体标识
     * @return 主体最小视图
     */
    override fun getPrincipal(@PathVariable("principalId") principalId: Long): UserPrincipalDto =
        try {
            upmPrincipalService.getPrincipal(principalId)
        } catch (e: IllegalArgumentException) {
            throw BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "认证主体不存在: $principalId", e)
        }
}
