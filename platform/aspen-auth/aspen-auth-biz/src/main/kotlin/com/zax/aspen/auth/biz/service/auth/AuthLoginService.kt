package com.zax.aspen.auth.biz.service.auth

import com.zax.aspen.auth.api.dto.auth.LoginResponse
import com.zax.aspen.auth.api.dto.auth.PasswordVerifyStatus
import com.zax.aspen.auth.api.enums.auth.AuthClientKind
import com.zax.aspen.auth.api.enums.auth.AuthLoginMethodType
import com.zax.aspen.auth.api.enums.auth.CaptchaKind
import com.zax.aspen.auth.biz.captcha.CaptchaGateways
import com.zax.aspen.auth.biz.config.AspenAuthProperties
import com.zax.aspen.auth.biz.enums.auth.AuthLoginResult
import com.zax.aspen.auth.biz.principal.PrincipalGateway
import com.zax.aspen.auth.biz.repository.auth.AuthLoginLogRepository
import com.zax.aspen.auth.biz.repository.auth.AuthSessionRepository
import com.zax.aspen.auth.biz.token.AuthTokenService
import com.zax.aspen.common.core.error.BusinessException
import com.zax.aspen.common.core.error.CommonErrorCode
import com.zax.aspen.common.security.consume.ClientConfigSnapshotStore
import com.zax.aspen.common.security.snapshot.AuthClientSnapshot
import com.zax.aspen.common.security.snapshot.AuthLoginMethodSnapshot
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.LocalDateTime
import java.util.Base64

/**
 * 统一认证引擎: 登录、刷新与登出的编排主干
 *
 * 引擎只有一条主干「找主体 -> 验凭据 -> 发令牌」: 按端类型从客户端配置快照取
 * 端与登录方式策略, 验证码闸门与主体 SPI 按策略分派; 失败按 AuthLoginResult
 * 分类落登录审计后抛 401 语义 (账号不存在与密码错误对外同提示); 刷新为轮换式
 * (旧摘要覆盖即作废), 刷新前复查主体状态拦截被禁用主体; 登出吊销会话, 访问
 * 令牌不建黑名单, 靠短 TTL 自然过期 (技术架构 14.2)
 */
@Service
class AuthLoginService(
    private val clientConfigSnapshotStore: ClientConfigSnapshotStore,
    private val captchaGateways: CaptchaGateways,
    private val principalGateway: PrincipalGateway,
    private val authSessionRepository: AuthSessionRepository,
    private val authLoginLogRepository: AuthLoginLogRepository,
    private val authTokenService: AuthTokenService,
    private val properties: AspenAuthProperties,
    private val clock: Clock,
) {
    /**
     * 账号密码登录
     *
     * @param command 登录指令 (端、请求体与来源)
     * @return 令牌对与主体回执; 触发强制改密时仍签发令牌并置位标记
     * @throws BusinessException 端/方式停用、验证码未过、凭据失败、主体锁定或禁用时按对应语义拒绝
     */
    @Transactional
    fun login(command: LoginCommand): LoginResponse {
        val client = clientConfigSnapshotStore.currentSnapshots()
            .firstOrNull { it.clientKind == command.clientKind.code }
        if (client == null) {
            audit(command, null, AuthLoginResult.FAILED_METHOD, "client-missing")
            throw BusinessException(CommonErrorCode.UNAUTHORIZED, "当前端未开放登录")
        }
        val method = client.methods.firstOrNull { it.method == AuthLoginMethodType.PASSWORD.code }
        if (method == null) {
            audit(command, null, AuthLoginResult.FAILED_METHOD, "password-missing")
            throw BusinessException(CommonErrorCode.UNAUTHORIZED, "当前端未开放账号密码登录")
        }
        try {
            captchaGateways.byKind(captchaKindOf(method)).verify(command.request.captchaToken)
        } catch (e: BusinessException) {
            audit(command, null, AuthLoginResult.FAILED_CAPTCHA, e.errorCode.code)
            throw e
        }
        val verification = principalGateway.verifyPassword(
            com.zax.aspen.auth.api.dto.auth.PasswordVerifyRequest(
                account = command.request.account,
                secret = command.request.password,
            ),
        )
        when (verification.status) {
            PasswordVerifyStatus.OK -> {
                val principal = requireNotNull(verification.principal)
                val response = issue(command, client, method, principal)
                audit(command, principal.principalId, AuthLoginResult.SUCCESS, null)
                return response
            }
            PasswordVerifyStatus.LOCKED -> {
                audit(command, verification.principal?.principalId, AuthLoginResult.FAILED_LOCKED, null)
                throw BusinessException(CommonErrorCode.UNAUTHORIZED, LOCKED_MESSAGE)
            }
            PasswordVerifyStatus.DISABLED -> {
                audit(command, verification.principal?.principalId, AuthLoginResult.FAILED_DISABLED, null)
                throw BusinessException(CommonErrorCode.UNAUTHORIZED, DISABLED_MESSAGE)
            }
            else -> {
                audit(command, verification.principal?.principalId, AuthLoginResult.FAILED_CREDENTIALS, verification.status.name)
                throw BusinessException(CommonErrorCode.UNAUTHORIZED, BAD_CREDENTIALS_MESSAGE)
            }
        }
    }

    /**
     * 刷新访问令牌 (轮换式): 定位会话、复查主体、覆盖摘要并发新令牌对
     *
     * @param refreshToken 登录或上次刷新下发的刷新令牌
     * @return 新的令牌对
     * @throws BusinessException 会话缺失/过期/已吊销或主体被禁用锁定时按未认证拒绝
     */
    @Transactional
    fun refresh(refreshToken: String): LoginResponse {
        val now = LocalDateTime.now(clock)
        val session = authSessionRepository.findByRefreshTokenHash(refreshToken.sha256Hex())
            ?: throw BusinessException(CommonErrorCode.UNAUTHORIZED, SESSION_INVALID_MESSAGE)
        if (session.revokedAt != null) {
            throw BusinessException(CommonErrorCode.UNAUTHORIZED, SESSION_INVALID_MESSAGE)
        }
        if (session.expiresAt.isBefore(now)) {
            throw BusinessException(CommonErrorCode.UNAUTHORIZED, SESSION_INVALID_MESSAGE)
        }
        val principal = principalGateway.getPrincipal(session.principalId)
        if (principal.status != com.zax.aspen.auth.api.enums.auth.PrincipalStatus.ENABLED) {
            authSessionRepository.revoke(session, REVOKED_REASON_PRINCIPAL_DISABLED, now)
            throw BusinessException(CommonErrorCode.UNAUTHORIZED, DISABLED_MESSAGE)
        }
        val client = requireNotNull(
            clientConfigSnapshotStore.findByCode(session.clientCode),
        ) { "会话端已不存在于快照: ${session.clientCode}" }
        return rotateAndIssue(client, session, principal, now)
    }

    /**
     * 登出: 按刷新令牌吊销会话, 幂等
     *
     * @param refreshToken 待吊销会话的刷新令牌
     */
    @Transactional
    fun logout(refreshToken: String) {
        val now = LocalDateTime.now(clock)
        val session = authSessionRepository.findByRefreshTokenHash(refreshToken.sha256Hex()) ?: return
        authSessionRepository.revoke(session, REVOKED_REASON_LOGOUT, now)
    }

    /**
     * 签发令牌对并落新会话
     *
     * @param command 登录指令
     * @param client 端快照
     * @param method 登录方式行快照
     * @param principal 主体最小视图
     * @return 令牌回执
     */
    private fun issue(
        command: LoginCommand,
        client: AuthClientSnapshot,
        method: AuthLoginMethodSnapshot,
        principal: com.zax.aspen.auth.api.dto.auth.UserPrincipalDto,
    ): LoginResponse {
        val now = LocalDateTime.now(clock)
        val refreshToken = generateRefreshToken()
        val refreshTtl = client.refreshTokenTtlSeconds ?: properties.jwt.refreshTokenTtlSeconds
        authSessionRepository.insert(
            clientKind = command.clientKind.code,
            clientCode = client.clientCode,
            principalId = principal.principalId,
            refreshTokenHash = refreshToken.sha256Hex(),
            deviceId = null,
            ip = command.ip,
            userAgent = command.userAgent,
            expiresAt = now.plusSeconds(refreshTtl),
            now = now,
        )
        return buildResponse(client, principal, refreshToken, mustChange(command, method, principal))
    }

    /**
     * 轮换会话摘要并发新令牌对
     *
     * @param client 端快照
     * @param session 既有会话
     * @param principal 复查后的主体视图
     * @param now 刷新时刻
     * @return 令牌回执
     */
    private fun rotateAndIssue(
        client: AuthClientSnapshot,
        session: com.zax.aspen.auth.biz.entity.auth.AuthSessionEntity,
        principal: com.zax.aspen.auth.api.dto.auth.UserPrincipalDto,
        now: LocalDateTime,
    ): LoginResponse {
        val refreshToken = generateRefreshToken()
        val refreshTtl = client.refreshTokenTtlSeconds ?: properties.jwt.refreshTokenTtlSeconds
        authSessionRepository.rotate(session, refreshToken.sha256Hex(), now.plusSeconds(refreshTtl), now)
        return buildResponse(client, principal, refreshToken, principal.mustChangePassword)
    }

    /**
     * 组装令牌回执: 签发访问令牌并携带强制改密标记
     *
     * @param client 端快照
     * @param principal 主体视图
     * @param refreshToken 新刷新令牌明文 (摘要已落库)
     * @param mustChangePassword 是否触发强制改密
     * @return 登录/刷新回执
     */
    private fun buildResponse(
        client: AuthClientSnapshot,
        principal: com.zax.aspen.auth.api.dto.auth.UserPrincipalDto,
        refreshToken: String,
        mustChangePassword: Boolean,
    ): LoginResponse {
        val accessTtl = client.accessTokenTtlSeconds ?: properties.jwt.accessTokenTtlSeconds
        val accessToken = authTokenService.issueAccessToken(
            principalId = principal.principalId,
            clientKind = AuthClientKind.entries.first { it.code == client.clientKind },
            clientCode = client.clientCode,
            tenantId = principal.tenantId,
            ttlSeconds = accessTtl,
        )
        return LoginResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = accessTtl,
            mustChangePassword = mustChangePassword,
            displayName = principal.displayName,
        )
    }

    /**
     * 判定是否触发强制改密: 用户域标记或密码有效期策略 (从未改密按最严格处理)
     *
     * @param command 登录指令
     * @param method 登录方式行快照
     * @param principal 主体视图
     * @return 需要强制改密时为 `true`
     */
    private fun mustChange(
        command: LoginCommand,
        method: AuthLoginMethodSnapshot,
        principal: com.zax.aspen.auth.api.dto.auth.UserPrincipalDto,
    ): Boolean {
        if (principal.mustChangePassword) {
            return true
        }
        val maxAgeDays = method.passwordMaxAgeDays ?: return false
        val changedAt = principal.passwordChangedAt ?: return true
        return changedAt.plusDays(maxAgeDays.toLong()).isBefore(LocalDateTime.now(clock))
    }

    /**
     * 解析方式行的验证码闸门; 快照行携带未知 code 时按最严格的 SLIDER 处理
     *
     * @param method 登录方式行快照
     * @return 闸门类型枚举
     */
    private fun captchaKindOf(method: AuthLoginMethodSnapshot): CaptchaKind =
        CaptchaKind.entries.firstOrNull { it.code == method.captchaKind } ?: CaptchaKind.SLIDER

    /**
     * 追加登录审计行; 失败路径不阻断原始异常
     *
     * @param command 登录指令
     * @param principalId 已解析出的主体标识, 未解析出为 null
     * @param result 登录结果分类
     * @param failureCode 细化失败码, 可为 null
     */
    private fun audit(command: LoginCommand, principalId: Long?, result: AuthLoginResult, failureCode: String?) {
        val clientCode = clientConfigSnapshotStore.currentSnapshots()
            .firstOrNull { it.clientKind == command.clientKind.code }?.clientCode
        try {
            authLoginLogRepository.append(
                clientKind = command.clientKind.code,
                clientCode = clientCode ?: "",
                principalId = principalId,
                account = command.request.account,
                method = AuthLoginMethodType.PASSWORD.code,
                result = result.name,
                failureCode = failureCode,
                ip = command.ip,
                userAgent = command.userAgent,
                now = LocalDateTime.now(clock),
            )
        } catch (e: Exception) {
            log.warn("登录审计写入失败, 不阻断登录链路", e)
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(AuthLoginService::class.java)

        /** 刷新令牌随机源, 构造一次复用 */
        val SECURE_RANDOM = SecureRandom()

        /** 凭据失败的对外统一提示, 不区分账号存在性 */
        const val BAD_CREDENTIALS_MESSAGE = "账号或密码不正确"

        /** 主体锁定的对外提示 */
        const val LOCKED_MESSAGE = "账号已锁定, 请稍后重试"

        /** 主体禁用的对外提示 */
        const val DISABLED_MESSAGE = "账号已停用"

        /** 会话失效的对外提示 */
        const val SESSION_INVALID_MESSAGE = "登录已失效, 请重新登录"

        /** 登出吊销原因 */
        const val REVOKED_REASON_LOGOUT = "LOGOUT"

        /** 主体失效吊销原因 */
        const val REVOKED_REASON_PRINCIPAL_DISABLED = "PRINCIPAL_DISABLED"

        /**
         * 生成不透明刷新令牌: 256 位随机数的 base64url 文本
         *
         * @return 刷新令牌明文, 摘要入库, 明文只回执一次
         */
        fun generateRefreshToken(): String {
            val bytes = ByteArray(32)
            SECURE_RANDOM.nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        /**
         * 计算刷新令牌的 SHA-256 十六进制摘要
         *
         * @receiver 刷新令牌明文
         * @return 小写十六进制摘要
         */
        fun String.sha256Hex(): String =
            MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
