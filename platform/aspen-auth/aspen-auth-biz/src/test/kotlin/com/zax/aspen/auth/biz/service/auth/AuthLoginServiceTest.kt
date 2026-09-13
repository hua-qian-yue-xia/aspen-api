package com.zax.aspen.auth.biz.service.auth

import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.zax.aspen.auth.api.dto.auth.LoginRequest
import com.zax.aspen.auth.api.dto.auth.PasswordVerifyRequest
import com.zax.aspen.auth.api.dto.auth.PasswordVerifyResult
import com.zax.aspen.auth.api.dto.auth.PasswordVerifyStatus
import com.zax.aspen.auth.api.dto.auth.UserPrincipalDto
import com.zax.aspen.auth.api.enums.auth.AuthClientKind
import com.zax.aspen.auth.api.enums.auth.CaptchaKind
import com.zax.aspen.auth.api.enums.auth.PrincipalStatus
import com.zax.aspen.auth.biz.captcha.CaptchaGateways
import com.zax.aspen.auth.biz.captcha.NoopCaptchaVerifier
import com.zax.aspen.auth.biz.config.AspenAuthProperties
import com.zax.aspen.auth.biz.entity.auth.AuthClientEntity
import com.zax.aspen.auth.biz.entity.auth.AuthLoginMethodEntity
import com.zax.aspen.auth.biz.entity.auth.AuthSessionEntity
import com.zax.aspen.auth.biz.principal.PrincipalGateway
import com.zax.aspen.auth.biz.repository.auth.AuthClientRepository
import com.zax.aspen.auth.biz.repository.auth.AuthLoginLogRepository
import com.zax.aspen.auth.biz.repository.auth.AuthLoginMethodRepository
import com.zax.aspen.auth.biz.repository.auth.AuthSessionRepository
import com.zax.aspen.auth.biz.token.AuthTokenService
import com.zax.aspen.common.core.error.BusinessException
import com.zax.aspen.common.core.error.CommonErrorCode
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 覆盖统一认证引擎的登录、刷新轮换与登出语义 (端策略本库直读) */
class AuthLoginServiceTest {
    private val authClientRepository: AuthClientRepository = Mockito.mock(AuthClientRepository::class.java)

    private val authLoginMethodRepository: AuthLoginMethodRepository =
        Mockito.mock(AuthLoginMethodRepository::class.java)

    private val captchaGateways: CaptchaGateways = Mockito.mock(CaptchaGateways::class.java)

    private val principalGateway: PrincipalGateway = Mockito.mock(PrincipalGateway::class.java)

    private val authSessionRepository: AuthSessionRepository =
        Mockito.mock(AuthSessionRepository::class.java)

    private val authLoginLogRepository: AuthLoginLogRepository =
        Mockito.mock(AuthLoginLogRepository::class.java)

    private val service = AuthLoginService(
        authClientRepository = authClientRepository,
        authLoginMethodRepository = authLoginMethodRepository,
        captchaGateways = captchaGateways,
        principalGateway = principalGateway,
        authSessionRepository = authSessionRepository,
        authLoginLogRepository = authLoginLogRepository,
        authTokenService = tokenService(),
        properties = AspenAuthProperties(),
        clock = NOW_CLOCK,
    )

    /** 验证凭据正确时签发令牌对并落新会话 */
    @Test
    fun `issues tokens and persists session on successful login`() {
        stubAdminClient()
        Mockito.`when`(principalGateway.verifyPassword(anyVerifyRequest())).thenReturn(okResult())

        val response = service.login(command())

        assertTrue(response.accessToken.isNotEmpty())
        assertTrue(response.refreshToken.isNotEmpty())
        assertEquals(1800L, response.expiresIn)
        assertEquals(true, response.mustChangePassword)
        assertEquals("平台管理员", response.displayName)
        Mockito.verify(authSessionRepository).insert(
            clientKind = eqText("admin"),
            clientCode = eqText("aspen-admin-web"),
            principalId = ArgumentMatchers.anyLong(),
            refreshTokenHash = anyText(),
            deviceId = ArgumentMatchers.isNull(),
            ip = eqText("10.0.0.1"),
            userAgent = eqText("test-agent"),
            expiresAt = anyTime(),
            now = anyTime(),
        )
    }

    /** 验证凭据失败按 401 拒绝并落失败审计, 对外提示不区分账号存在性 */
    @Test
    fun `rejects bad credentials with audit trail`() {
        stubAdminClient()
        Mockito.`when`(principalGateway.verifyPassword(anyVerifyRequest()))
            .thenReturn(PasswordVerifyResult(PasswordVerifyStatus.BAD_CREDENTIALS, null))

        val exception = assertFailsWith<BusinessException> { service.login(command()) }

        assertEquals(CommonErrorCode.UNAUTHORIZED, exception.errorCode)
        Mockito.verify(authSessionRepository, Mockito.never()).insert(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyLong(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            anyTime(),
            anyTime(),
        )
        Mockito.verify(authLoginLogRepository).append(
            eqText("admin"),
            eqText("aspen-admin-web"),
            ArgumentMatchers.isNull(),
            eqText("admin-account"),
            eqText("password"),
            eqText("FAILED_CREDENTIALS"),
            eqText("BAD_CREDENTIALS"),
            eqText("10.0.0.1"),
            eqText("test-agent"),
            anyTime(),
        )
    }

    /** 验证端类型未注册时按未认证拒绝, 审计行不携带端编码 */
    @Test
    fun `rejects login when client kind not registered`() {
        Mockito.`when`(authClientRepository.findEnabledByKind(AuthClientKind.ADMIN)).thenReturn(null)

        val exception = assertFailsWith<BusinessException> { service.login(command()) }

        assertEquals(CommonErrorCode.UNAUTHORIZED, exception.errorCode)
        Mockito.verify(authLoginLogRepository).append(
            eqText("admin"),
            eqText(""),
            ArgumentMatchers.isNull(),
            eqText("admin-account"),
            eqText("password"),
            eqText("FAILED_METHOD"),
            eqText("client-missing"),
            eqText("10.0.0.1"),
            eqText("test-agent"),
            anyTime(),
        )
    }

    /** 验证刷新按摘要定位会话并轮换, 旧摘要立即作废 */
    @Test
    fun `rotates refresh token and reissues pair`() {
        val clientRow = clientRow()
        Mockito.`when`(authClientRepository.findByCode("aspen-admin-web")).thenReturn(clientRow)
        val session = session(expired = false, revoked = false)
        Mockito.`when`(authSessionRepository.findByRefreshTokenHash(ArgumentMatchers.anyString())).thenReturn(session)
        Mockito.`when`(principalGateway.getPrincipal(42L)).thenReturn(principal())

        val response = service.refresh("refresh-token-value")

        assertTrue(response.accessToken.isNotEmpty())
        Mockito.verify(authSessionRepository)
            .rotate(sameSession(session), anyText(), anyTime(), anyTime())
    }

    /** 验证已吊销会话的刷新按未认证拒绝, 不触发轮换 */
    @Test
    fun `rejects refresh for revoked session`() {
        val session = session(expired = false, revoked = true)
        Mockito.`when`(authSessionRepository.findByRefreshTokenHash(ArgumentMatchers.anyString())).thenReturn(session)

        val exception = assertFailsWith<BusinessException> { service.refresh("refresh-token-value") }

        assertEquals(CommonErrorCode.UNAUTHORIZED, exception.errorCode)
        Mockito.verify(authSessionRepository, Mockito.never())
            .rotate(anySession(), anyText(), anyTime(), anyTime())
    }

    /** 验证主体被禁用后刷新触发会话吊销并拒绝 */
    @Test
    fun `revokes session when principal becomes disabled`() {
        val session = session(expired = false, revoked = false)
        Mockito.`when`(authSessionRepository.findByRefreshTokenHash(ArgumentMatchers.anyString())).thenReturn(session)
        Mockito.`when`(principalGateway.getPrincipal(42L)).thenReturn(principal(status = PrincipalStatus.DISABLED))

        val exception = assertFailsWith<BusinessException> { service.refresh("refresh-token-value") }

        assertEquals(CommonErrorCode.UNAUTHORIZED, exception.errorCode)
        Mockito.verify(authSessionRepository).revoke(sameSession(session), eqText("PRINCIPAL_DISABLED"), anyTime())
    }

    /** 验证登出吊销会话, 未知刷新令牌幂等无副作用 */
    @Test
    fun `revokes session on logout and stays idempotent`() {
        val session = session(expired = false, revoked = false)
        Mockito.`when`(authSessionRepository.findByRefreshTokenHash(ArgumentMatchers.anyString())).thenReturn(session)

        service.logout("refresh-token-value")
        Mockito.verify(authSessionRepository).revoke(sameSession(session), eqText("LOGOUT"), anyTime())

        Mockito.`when`(authSessionRepository.findByRefreshTokenHash(ArgumentMatchers.anyString())).thenReturn(null)
        service.logout("refresh-token-value")
        Mockito.verify(authSessionRepository, Mockito.times(1))
            .revoke(anySession(), anyText(), anyTime())
    }

    /**
     * 打桩管理端实体与密码方式行; 替身先建再用, 避免嵌套打桩
     */
    private fun stubAdminClient() {
        val clientRow = clientRow()
        val methodRow = methodRow()
        Mockito.`when`(authClientRepository.findEnabledByKind(AuthClientKind.ADMIN)).thenReturn(clientRow)
        Mockito.`when`(authLoginMethodRepository.findEnabledByClient(1L)).thenReturn(listOf(methodRow))
        Mockito.`when`(captchaGateways.byKind(CaptchaKind.NONE)).thenReturn(NoopCaptchaVerifier())
    }

    /**
     * 构造管理端实体替身
     *
     * @return 携带固定编码与类型的端实体 mock
     */
    private fun clientRow(): AuthClientEntity {
        val client = Mockito.mock(AuthClientEntity::class.java)
        Mockito.`when`(client.clientId).thenReturn(1L)
        Mockito.`when`(client.clientCode).thenReturn("aspen-admin-web")
        Mockito.`when`(client.clientKind).thenReturn(AuthClientKind.ADMIN)
        Mockito.`when`(client.accessTokenTtlSeconds).thenReturn(null)
        Mockito.`when`(client.refreshTokenTtlSeconds).thenReturn(null)
        return client
    }

    /**
     * 构造密码登录方式实体替身
     *
     * @return 携带 NONE 闸门与密码策略的方式行 mock
     */
    private fun methodRow(): AuthLoginMethodEntity {
        val method = Mockito.mock(AuthLoginMethodEntity::class.java)
        Mockito.`when`(method.loginMethodId).thenReturn(2L)
        Mockito.`when`(method.clientId).thenReturn(1L)
        Mockito.`when`(method.method).thenReturn(com.zax.aspen.auth.api.enums.auth.AuthLoginMethodType.PASSWORD)
        Mockito.`when`(method.captchaKind).thenReturn(CaptchaKind.NONE)
        Mockito.`when`(method.forceChangeOnFirstLogin).thenReturn(false)
        Mockito.`when`(method.passwordMaxAgeDays).thenReturn(90)
        Mockito.`when`(method.config).thenReturn(null)
        return method
    }

    /**
     * 构造登录指令
     *
     * @return 管理端登录指令
     */
    private fun command(): LoginCommand =
        LoginCommand(
            clientKind = AuthClientKind.ADMIN,
            request = LoginRequest(account = "admin-account", password = "test-secret", captchaToken = null),
            ip = "10.0.0.1",
            userAgent = "test-agent",
        )

    /**
     * 构造校验成功结果
     *
     * @return OK 状态与主体视图
     */
    private fun okResult(): PasswordVerifyResult = PasswordVerifyResult(PasswordVerifyStatus.OK, principal())

    /**
     * 构造主体视图替身参数
     *
     * @param status 主体状态, 默认 ENABLED
     * @return 主体最小视图
     */
    private fun principal(status: PrincipalStatus = PrincipalStatus.ENABLED): UserPrincipalDto =
        UserPrincipalDto(
            principalId = 42L,
            displayName = "平台管理员",
            status = status,
            tenantId = 7L,
            mustChangePassword = true,
            passwordChangedAt = null,
        )

    /**
     * 构造会话实体替身
     *
     * @param expired 是否已过期
     * @param revoked 是否已吊销
     * @return 会话实体 mock
     */
    private fun session(expired: Boolean, revoked: Boolean): AuthSessionEntity {
        val session = Mockito.mock(AuthSessionEntity::class.java)
        Mockito.`when`(session.sessionId).thenReturn(1L)
        Mockito.`when`(session.clientKind).thenReturn("admin")
        Mockito.`when`(session.clientCode).thenReturn("aspen-admin-web")
        Mockito.`when`(session.principalId).thenReturn(42L)
        Mockito.`when`(session.expiresAt).thenReturn(if (expired) NOW.minusDays(1) else NOW.plusDays(1))
        Mockito.`when`(session.revokedAt).thenReturn(if (revoked) NOW else null)
        return session
    }

    /**
     * eq matcher 的非空字符串包装: Kotlin 非空参数不接受 matcher 返回的 null
     *
     * @param value 期望匹配的实参值
     * @return matcher 登记结果, matcher 返回 null 时回退为原值
     */
    private fun eqText(value: String): String = ArgumentMatchers.eq(value) ?: value

    /**
     * anyString matcher 的非空包装: Kotlin 非空参数不接受 matcher 返回的 null
     *
     * @return matcher 登记结果, matcher 返回 null 时回退为空字符串
     */
    private fun anyText(): String = ArgumentMatchers.anyString() ?: ""

    /**
     * any matcher 的非空时间包装: Kotlin 非空参数不接受 matcher 返回的 null
     *
     * @return matcher 登记结果, matcher 返回 null 时回退为固定时间
     */
    private fun anyTime(): LocalDateTime = ArgumentMatchers.any<LocalDateTime>() ?: NOW

    /**
     * same matcher 的非空会话包装: Kotlin 非空参数不接受 matcher 返回的 null
     *
     * @param session 期望同一引用的会话实体
     * @return matcher 登记结果, matcher 返回 null 时回退为原实体
     */
    private fun sameSession(session: AuthSessionEntity): AuthSessionEntity =
        ArgumentMatchers.same(session) ?: session

    /** anySession 的回退替身; 字段初始化期创建, 避免验证表达式内创建 mock 污染 matcher 栈 */
    private val anySessionFallback: AuthSessionEntity = Mockito.mock(AuthSessionEntity::class.java)

    /**
     * any matcher 的非空会话包装
     *
     * @return matcher 登记结果, matcher 返回 null 时回退为预建替身
     */
    private fun anySession(): AuthSessionEntity =
        ArgumentMatchers.any(AuthSessionEntity::class.java) ?: anySessionFallback

    /**
     * any matcher 的非空请求包装; 回退值为普通 DTO 构造, 无 Mockito 副作用
     *
     * @return matcher 登记结果, matcher 返回 null 时回退为占位请求
     */
    private fun anyVerifyRequest(): PasswordVerifyRequest =
        ArgumentMatchers.any(PasswordVerifyRequest::class.java) ?: PasswordVerifyRequest("account", "secret")

    private companion object {
        /** 固定时钟 */
        val NOW_CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-13T02:00:00Z"), ZoneId.of("UTC"))

        /** 固定时钟对应的本地时间 */
        val NOW: LocalDateTime = LocalDateTime.parse("2026-09-13T02:00:00")

        /**
         * 构造真实令牌服务 (运行期生成测试密钥, 不落字面量)
         *
         * @return 挂接固定时钟的令牌服务
         */
        fun tokenService(): AuthTokenService {
            val jwk = RSAKeyGenerator(4096).keyID("aspen-test-key").generate()
            val privateKey = Base64.getEncoder().encodeToString(jwk.toPrivateKey().encoded)
            return AuthTokenService(
                properties = AspenAuthProperties(
                    jwt = AspenAuthProperties.Jwt(privateKey = privateKey, keyId = "aspen-test-key"),
                ),
                clock = NOW_CLOCK,
            )
        }
    }
}
