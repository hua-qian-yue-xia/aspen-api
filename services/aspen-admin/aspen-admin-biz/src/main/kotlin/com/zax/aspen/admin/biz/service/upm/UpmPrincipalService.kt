package com.zax.aspen.admin.biz.service.upm

import com.zax.aspen.admin.biz.repository.upm.UpmUserCredentialRepository
import com.zax.aspen.admin.biz.repository.upm.UpmUserIdentityRepository
import com.zax.aspen.admin.biz.repository.upm.UpmUserRepository
import com.zax.aspen.admin.biz.entity.upm.UpmUserEntity
import com.zax.aspen.auth.api.dto.auth.IdentityResolveRequest
import com.zax.aspen.auth.api.dto.auth.IdentityResolveResult
import com.zax.aspen.auth.api.dto.auth.IdentityResolveStatus
import com.zax.aspen.auth.api.dto.auth.PasswordVerifyRequest
import com.zax.aspen.auth.api.dto.auth.PasswordVerifyResult
import com.zax.aspen.auth.api.dto.auth.PasswordVerifyStatus
import com.zax.aspen.auth.api.dto.auth.UserPrincipalDto
import com.zax.aspen.auth.api.enums.auth.PrincipalStatus
import com.zax.aspen.common.database.tenant.TenantSystemContext
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

/**
 * 认证主体 SPI 的 UPM 实现: 密码校验、主体复查与第三方身份解析
 *
 * Auth 登录链路的用户域判定入口 (技术架构 14.2): 摘要比对、失败计数与锁定窗口
 * 全部在本服务完成, Auth 只消费判定结果; upm_user/upm_user_credential 是租户
 * 隔离实体而登录先于租户上下文存在, 全部数据访问包裹在 TenantSystemContext
 * 的显式系统上下文内 (审计说明 auth-principal-spi); NOT_FOUND 与
 * BAD_CREDENTIALS 的差异只用于失败计数, 调用方必须对外同提示; 登录成功的
 * upm_user 冗余计数 (last_login_at/login_count) 留待管理面批次随批量查询更新,
 * 本版只维护凭证行状态
 */
@Service
class UpmPrincipalService(
    private val upmUserRepository: UpmUserRepository,
    private val upmUserCredentialRepository: UpmUserCredentialRepository,
    private val upmUserIdentityRepository: UpmUserIdentityRepository,
    private val passwordEncoder: PasswordEncoder,
    private val clock: Clock,
) {
    /**
     * 校验账号密码并返回主体判定
     *
     * @param request 登录账号与密码明文
     * @return 校验状态与通过时的主体最小视图; 账号不存在、密码错误、锁定与禁用各自的判定见 PasswordVerifyStatus
     */
    @Transactional
    fun verifyPassword(request: PasswordVerifyRequest): PasswordVerifyResult =
        TenantSystemContext.runAsSystem(SYSTEM_CONTEXT_REASON) {
            val user = upmUserRepository.findByAccount(request.account)
                ?: return@runAsSystem PasswordVerifyResult(PasswordVerifyStatus.NOT_FOUND, null)
            if (user.status != USER_STATUS_ENABLED) {
                return@runAsSystem PasswordVerifyResult(PasswordVerifyStatus.DISABLED, null)
            }
            val now = LocalDateTime.now(clock)
            val userLockedUntil = user.lockedUntil
            if (userLockedUntil != null && userLockedUntil.isAfter(now)) {
                return@runAsSystem PasswordVerifyResult(PasswordVerifyStatus.LOCKED, null)
            }
            val credential = upmUserCredentialRepository.findActivePassword(user.userId)
                ?: return@runAsSystem PasswordVerifyResult(PasswordVerifyStatus.BAD_CREDENTIALS, null)
            val credentialLockedUntil = credential.lockedUntil
            if (credentialLockedUntil != null && credentialLockedUntil.isAfter(now)) {
                return@runAsSystem PasswordVerifyResult(PasswordVerifyStatus.LOCKED, null)
            }
            if (!passwordEncoder.matches(request.secret, credential.secretHash)) {
                upmUserCredentialRepository.markFailure(credential, MAX_FAILED_ATTEMPTS, LOCK_MINUTES, now)
                return@runAsSystem PasswordVerifyResult(PasswordVerifyStatus.BAD_CREDENTIALS, null)
            }
            upmUserCredentialRepository.markSuccess(credential, now)
            PasswordVerifyResult(PasswordVerifyStatus.OK, user.toPrincipalDto())
        }

    /**
     * 按主体标识复查主体状态, 供 Auth 刷新令牌时拦截被禁用或锁定的主体
     *
     * @param principalId 用户域内主体标识 (upm_user.user_id)
     * @return 主体最小视图
     * @throws IllegalArgumentException 主体不存在或已删除时拒绝
     */
    fun getPrincipal(principalId: Long): UserPrincipalDto =
        TenantSystemContext.runAsSystem(SYSTEM_CONTEXT_REASON) {
            val user = upmUserRepository.findById(principalId)
                ?: throw IllegalArgumentException("认证主体不存在: $principalId")
            user.toPrincipalDto()
        }

    /**
     * 按第三方身份查找绑定主体
     *
     * 管理端用户域只认已绑定账号, 绝不自动建号: allowCreate=true 且回应为
     * NOT_FOUND 是管理端的固定形态, Auth 依此对管理端第三方登录给出去
     * 「账号未绑定」的对外提示
     *
     * @param request 外部身份标识与建号策略
     * @return FOUND 携带绑定主体或 NOT_FOUND, 管理端不产生 CREATED
     */
    fun resolveByIdentity(request: IdentityResolveRequest): IdentityResolveResult =
        TenantSystemContext.runAsSystem(SYSTEM_CONTEXT_REASON) {
            val identity = upmUserIdentityRepository.findEnabled(request.identityType, request.identityId)
                ?: return@runAsSystem IdentityResolveResult(IdentityResolveStatus.NOT_FOUND, null)
            val user = upmUserRepository.findById(identity.userId)
                ?: return@runAsSystem IdentityResolveResult(IdentityResolveStatus.NOT_FOUND, null)
            IdentityResolveResult(IdentityResolveStatus.FOUND, user.toPrincipalDto())
        }

    /**
     * 用户行转主体最小视图
     *
     * @return 携带标识、状态、租户与密码安全标记的主体视图
     */
    private fun UpmUserEntity.toPrincipalDto(): UserPrincipalDto =
        UserPrincipalDto(
            principalId = userId,
            displayName = nickname ?: realName ?: username,
            status = principalStatus(),
            tenantId = tenantId,
            mustChangePassword = mustChangePassword,
            passwordChangedAt = passwordChangedAt,
        )

    /**
     * 推导主体状态: 用户行状态非启用为 DISABLED, 锁定窗口未过为 LOCKED, 否则 ENABLED
     *
     * @return 映射后的主体状态
     */
    private fun UpmUserEntity.principalStatus(): PrincipalStatus {
        if (status != USER_STATUS_ENABLED) {
            return PrincipalStatus.DISABLED
        }
        val lockedUntil = lockedUntil
        if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now(clock))) {
            return PrincipalStatus.LOCKED
        }
        return PrincipalStatus.ENABLED
    }

    private companion object {
        /** SPI 跨租户访问的系统上下文审计说明 */
        const val SYSTEM_CONTEXT_REASON = "auth-principal-spi"

        /** upm_user.status 的启用取值, 该列按字符串存储 */
        const val USER_STATUS_ENABLED = "enabled"

        /** 连续密码错误达到该次数后锁定 */
        const val MAX_FAILED_ATTEMPTS = 5

        /** 锁定窗口时长 (分钟) */
        const val LOCK_MINUTES = 15L
    }
}
