package com.zax.aspen.admin.biz.service.upm

import com.zax.aspen.admin.biz.entity.upm.UpmUserCredentialEntity
import com.zax.aspen.admin.biz.entity.upm.UpmUserEntity
import com.zax.aspen.admin.biz.repository.upm.UpmUserCredentialRepository
import com.zax.aspen.admin.biz.repository.upm.UpmUserIdentityRepository
import com.zax.aspen.admin.biz.repository.upm.UpmUserRepository
import com.zax.aspen.auth.api.dto.auth.PasswordVerifyRequest
import com.zax.aspen.auth.api.dto.auth.PasswordVerifyStatus
import com.zax.aspen.auth.api.enums.auth.PrincipalStatus
import org.mockito.Mockito
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** 覆盖认证主体 SPI 的密码校验、锁定与禁用判定语义 */
class UpmPrincipalServiceTest {
    private val upmUserRepository: UpmUserRepository = Mockito.mock(UpmUserRepository::class.java)

    private val upmUserCredentialRepository: UpmUserCredentialRepository =
        Mockito.mock(UpmUserCredentialRepository::class.java)

    private val upmUserIdentityRepository: UpmUserIdentityRepository =
        Mockito.mock(UpmUserIdentityRepository::class.java)

    private val passwordEncoder = BCryptPasswordEncoder()

    private val service = UpmPrincipalService(
        upmUserRepository = upmUserRepository,
        upmUserCredentialRepository = upmUserCredentialRepository,
        upmUserIdentityRepository = upmUserIdentityRepository,
        passwordEncoder = passwordEncoder,
        clock = Clock.fixed(Instant.parse("2026-09-13T02:00:00Z"), ZoneId.of("UTC")),
    )

    /** 验证账号不存在返回 NOT_FOUND 且不触碰凭证行 */
    @Test
    fun `returns not found for unknown account`() {
        Mockito.`when`(upmUserRepository.findByAccount("ghost")).thenReturn(null)

        val result = service.verifyPassword(PasswordVerifyRequest("ghost", SECRET))

        assertEquals(PasswordVerifyStatus.NOT_FOUND, result.status)
        Mockito.verifyNoInteractions(upmUserCredentialRepository)
    }

    /** 验证用户状态非启用返回 DISABLED, 不计入失败计数 */
    @Test
    fun `returns disabled for non enabled user`() {
        val disabledUser = user(status = "disabled")
        Mockito.`when`(upmUserRepository.findByAccount("blocked")).thenReturn(disabledUser)

        val result = service.verifyPassword(PasswordVerifyRequest("blocked", SECRET))

        assertEquals(PasswordVerifyStatus.DISABLED, result.status)
        Mockito.verifyNoInteractions(upmUserCredentialRepository)
    }

    /** 验证用户锁定窗口未过返回 LOCKED, 不计入失败计数 */
    @Test
    fun `returns locked while user lock window is active`() {
        val lockedUser = user(lockedUntil = LocalDateTime.parse("2099-01-01T00:00:00"))
        Mockito.`when`(upmUserRepository.findByAccount("locked")).thenReturn(lockedUser)

        val result = service.verifyPassword(PasswordVerifyRequest("locked", SECRET))

        assertEquals(PasswordVerifyStatus.LOCKED, result.status)
        Mockito.verifyNoInteractions(upmUserCredentialRepository)
    }

    /** 验证正确密码返回 OK 并携带主体最小视图, 成功后清零失败计数 */
    @Test
    fun `returns ok with principal for correct password`() {
        val enabledUser = user()
        val credential = credentialOf(SECRET)
        Mockito.`when`(upmUserRepository.findByAccount("admin")).thenReturn(enabledUser)
        Mockito.`when`(upmUserCredentialRepository.findActivePassword(1L)).thenReturn(credential)

        val result = service.verifyPassword(PasswordVerifyRequest("admin", SECRET))

        assertEquals(PasswordVerifyStatus.OK, result.status)
        val principal = assertNotNull(result.principal)
        assertEquals(1L, principal.principalId)
        assertEquals("admin", principal.displayName)
        assertEquals(PrincipalStatus.ENABLED, principal.status)
        assertEquals(7L, principal.tenantId)
        assertEquals(true, principal.mustChangePassword)
        Mockito.verify(upmUserCredentialRepository).markSuccess(credential, NOW)
    }

    /** 验证错误密码返回 BAD_CREDENTIALS 并记录失败计数 */
    @Test
    fun `returns bad credentials and records failure for wrong password`() {
        val enabledUser = user()
        val credential = credentialOf("correct-horse")
        Mockito.`when`(upmUserRepository.findByAccount("admin")).thenReturn(enabledUser)
        Mockito.`when`(upmUserCredentialRepository.findActivePassword(1L)).thenReturn(credential)

        val result = service.verifyPassword(PasswordVerifyRequest("admin", SECRET))

        assertEquals(PasswordVerifyStatus.BAD_CREDENTIALS, result.status)
        Mockito.verify(upmUserCredentialRepository).markFailure(credential, 5, 15L, NOW)
    }

    /**
     * 构造用户实体替身; 必须在 Mockito.when 参数求值之外调用, 避免嵌套打桩
     *
     * @param status 用户状态字符串, 默认 enabled
     * @param lockedUntil 锁定截止时间, 默认 null (未锁定)
     * @return 携带固定标识与安全标记的用户实体 mock
     */
    private fun user(status: String = "enabled", lockedUntil: LocalDateTime? = null): UpmUserEntity {
        val user = Mockito.mock(UpmUserEntity::class.java)
        Mockito.`when`(user.userId).thenReturn(1L)
        Mockito.`when`(user.username).thenReturn("admin")
        Mockito.`when`(user.nickname).thenReturn(null)
        Mockito.`when`(user.realName).thenReturn(null)
        Mockito.`when`(user.status).thenReturn(status)
        Mockito.`when`(user.lockedUntil).thenReturn(lockedUntil)
        Mockito.`when`(user.tenantId).thenReturn(7L)
        Mockito.`when`(user.mustChangePassword).thenReturn(true)
        Mockito.`when`(user.passwordChangedAt).thenReturn(null)
        return user
    }

    /**
     * 构造密码凭证实体替身, 摘要由明文现场编码; 必须在 Mockito.when 参数求值之外调用
     *
     * @param rawSecret 密码明文, 编码失败时测试直接失败
     * @return 未锁定、零失败计数的凭证实体 mock
     */
    private fun credentialOf(rawSecret: String): UpmUserCredentialEntity {
        val secretHash = passwordEncoder.encode(rawSecret) ?: error("测试编码器返回空摘要")
        val credential = Mockito.mock(UpmUserCredentialEntity::class.java)
        Mockito.`when`(credential.userCredentialId).thenReturn(11L)
        Mockito.`when`(credential.version).thenReturn(1)
        Mockito.`when`(credential.secretHash).thenReturn(secretHash)
        Mockito.`when`(credential.lockedUntil).thenReturn(null)
        Mockito.`when`(credential.failedAttemptCount).thenReturn(0)
        return credential
    }

    private companion object {
        /** 测试用密码明文, 与任何真实凭据无关 */
        const val SECRET = "test-secret"

        /** 固定时钟对应的本地时间, 服务内所有时间取值都可精确预知 */
        val NOW: LocalDateTime = LocalDateTime.parse("2026-09-13T02:00:00")
    }
}
