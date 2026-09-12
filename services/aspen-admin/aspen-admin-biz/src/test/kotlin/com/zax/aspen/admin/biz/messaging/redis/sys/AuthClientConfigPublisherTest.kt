package com.zax.aspen.admin.biz.messaging.redis.sys

import com.zax.aspen.admin.biz.entity.sys.SysAuthClientEntity
import com.zax.aspen.admin.biz.entity.sys.SysAuthLoginMethodEntity
import com.zax.aspen.admin.biz.repository.sys.SysAuthClientRepository
import com.zax.aspen.admin.biz.repository.sys.SysAuthLoginMethodRepository
import com.zax.aspen.auth.api.enums.auth.AuthClientKind
import com.zax.aspen.auth.api.enums.auth.AuthLoginMethodType
import com.zax.aspen.auth.api.enums.auth.CaptchaKind
import com.zax.aspen.common.security.publish.ClientConfigPublisher
import com.zax.aspen.common.security.snapshot.AuthClientSnapshot
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import kotlin.test.assertEquals

/** 覆盖认证客户端配置发布快照的端归类与方式行映射语义 */
class AuthClientConfigPublisherTest {
    private val sysAuthClientRepository: SysAuthClientRepository =
        Mockito.mock(SysAuthClientRepository::class.java)

    private val sysAuthLoginMethodRepository: SysAuthLoginMethodRepository =
        Mockito.mock(SysAuthLoginMethodRepository::class.java)

    private val clientConfigPublisher: ClientConfigPublisher =
        Mockito.mock(ClientConfigPublisher::class.java)

    private val publisher = AuthClientConfigPublisher(
        sysAuthClientRepository = sysAuthClientRepository,
        sysAuthLoginMethodRepository = sysAuthLoginMethodRepository,
        clientConfigPublisher = clientConfigPublisher,
    )

    /** 验证端行与其方式行按端归类发布, 枚举取 code 且 TTL 覆盖透传 */
    @Test
    fun `publishes clients with grouped methods mapped by code`() {
        val clientRow = client()
        val methodRow = method()
        Mockito.`when`(sysAuthClientRepository.findAllEnabled()).thenReturn(listOf(clientRow))
        Mockito.`when`(sysAuthLoginMethodRepository.findAllEnabled()).thenReturn(listOf(methodRow))
        Mockito.`when`(clientConfigPublisher.publishAll(anySnapshots())).thenReturn(3L)

        val version = publisher.publishAll()

        assertEquals(3L, version)
        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<AuthClientSnapshot>>
        Mockito.verify(clientConfigPublisher).publishAll(captureSnapshots(captor))
        val snapshots = captor.value
        val snapshot = snapshots.single()
        assertEquals("aspen-admin-web", snapshot.clientCode)
        assertEquals("admin", snapshot.clientKind)
        assertEquals(1800L, snapshot.accessTokenTtlSeconds)
        val publishedMethod = snapshot.methods.single()
        assertEquals("password", publishedMethod.method)
        assertEquals("slider", publishedMethod.captchaKind)
        assertEquals(true, publishedMethod.forceChangeOnFirstLogin)
        assertEquals(90, publishedMethod.passwordMaxAgeDays)
    }

    /** 验证无启用端时发布空快照, 清空全部端是合法的发布形态 */
    @Test
    fun `publishes empty snapshot when no clients remain`() {
        Mockito.`when`(sysAuthClientRepository.findAllEnabled()).thenReturn(emptyList())
        Mockito.`when`(sysAuthLoginMethodRepository.findAllEnabled()).thenReturn(emptyList())
        Mockito.`when`(clientConfigPublisher.publishAll(anySnapshots())).thenReturn(4L)

        assertEquals(4L, publisher.publishAll())
    }

    /**
     * anyList matcher 的非空包装: Kotlin 非空参数不接受 matcher 返回的 null
     *
     * @return matcher 登记结果, matcher 返回 null 时回退为空列表
     */
    private fun anySnapshots(): List<AuthClientSnapshot> =
        ArgumentMatchers.anyList<AuthClientSnapshot>() as? List<AuthClientSnapshot> ?: emptyList()

    /**
     * capture matcher 的非空包装: Kotlin 非空参数不接受 matcher 返回的 null
     *
     * @param captor 用于捕获实参的快照列表捕获器
     * @return matcher 登记结果, matcher 返回 null 时回退为空列表
     */
    private fun captureSnapshots(captor: ArgumentCaptor<List<AuthClientSnapshot>>): List<AuthClientSnapshot> =
        captor.capture() ?: emptyList()

    /**
     * 构造管理端实体替身
     *
     * @return 携带固定编码、类型与 TTL 覆盖的端实体 mock
     */
    private fun client(): SysAuthClientEntity {
        val client = Mockito.mock(SysAuthClientEntity::class.java)
        Mockito.`when`(client.authClientId).thenReturn(1L)
        Mockito.`when`(client.clientCode).thenReturn("aspen-admin-web")
        Mockito.`when`(client.clientKind).thenReturn(AuthClientKind.ADMIN)
        Mockito.`when`(client.accessTokenTtlSeconds).thenReturn(1800L)
        Mockito.`when`(client.refreshTokenTtlSeconds).thenReturn(null)
        return client
    }

    /**
     * 构造密码登录方式实体替身
     *
     * @return 携带行为验证码闸门与密码策略的方式行 mock
     */
    private fun method(): SysAuthLoginMethodEntity {
        val method = Mockito.mock(SysAuthLoginMethodEntity::class.java)
        Mockito.`when`(method.authLoginMethodId).thenReturn(2L)
        Mockito.`when`(method.authClientId).thenReturn(1L)
        Mockito.`when`(method.method).thenReturn(AuthLoginMethodType.PASSWORD)
        Mockito.`when`(method.captchaKind).thenReturn(CaptchaKind.SLIDER)
        Mockito.`when`(method.forceChangeOnFirstLogin).thenReturn(true)
        Mockito.`when`(method.passwordMaxAgeDays).thenReturn(90)
        Mockito.`when`(method.config).thenReturn(null)
        return method
    }
}
