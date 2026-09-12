package com.zax.aspen.common.security.publish

import com.zax.aspen.common.cache.support.AspenRedisOperations
import com.zax.aspen.common.security.AspenSecurityProperties
import com.zax.aspen.common.security.snapshot.AuthClientCatalogSnapshot
import com.zax.aspen.common.security.snapshot.AuthClientSnapshot
import com.zax.aspen.common.security.snapshot.AuthLoginMethodSnapshot
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import tools.jackson.module.kotlin.readValue
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 覆盖客户端配置信封发布的取号、版本守卫落盘与通知的介质操作语义 */
class ClientConfigPublisherTest {
    private val aspenRedisOperations: AspenRedisOperations =
        Mockito.mock(AspenRedisOperations::class.java)

    private val objectMapper: ObjectMapper =
        JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    private val publisher = ClientConfigPublisher(
        aspenRedisOperations = aspenRedisOperations,
        objectMapper = objectMapper,
        properties = AspenSecurityProperties(),
        clock = Clock.fixed(Instant.parse("2026-09-13T02:00:00Z"), ZoneId.of("Asia/Shanghai")),
    )

    /** 验证一次发布完成取号、版本守卫落盘与携带版本号的广播, 且信封内容完整 */
    @Test
    fun `publishes versioned envelope through guarded set`() {
        Mockito.`when`(aspenRedisOperations.increment("aspen:local:auth:clients:version")).thenReturn(3L)
        Mockito.`when`(
            aspenRedisOperations.setValueIfNewer(
                eqText("aspen:local:auth:clients"),
                anyText(),
                eqText("aspen:local:auth:clients:refresh"),
            ),
        ).thenReturn(true)

        val version = publisher.publishAll(listOf(client("aspen-admin-web")))

        assertEquals(3L, version)
        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(aspenRedisOperations).setValueIfNewer(
            eqText("aspen:local:auth:clients"),
            captureText(captor),
            eqText("aspen:local:auth:clients:refresh"),
        )
        val envelope = objectMapper.readValue<AuthClientCatalogSnapshot>(captor.value)
        assertEquals(3L, envelope.version)
        assertEquals("2026-09-13T10:00+08:00", envelope.publishedAt)
        assertEquals(listOf("aspen-admin-web"), envelope.clients.map { it.clientCode })
    }

    /** 验证守卫拒绝旧版本 (并发发布旧盖新) 不视为失败, 返回取号并等待下次发布自愈 */
    @Test
    fun `keeps version and does not fail when guard rejects stale envelope`() {
        Mockito.`when`(aspenRedisOperations.increment("aspen:local:auth:clients:version")).thenReturn(4L)
        Mockito.`when`(
            aspenRedisOperations.setValueIfNewer(
                eqText("aspen:local:auth:clients"),
                anyText(),
                eqText("aspen:local:auth:clients:refresh"),
            ),
        ).thenReturn(false)

        val version = publisher.publishAll(emptyList())

        assertEquals(4L, version)
    }

    /** 验证取号失败时快速失败, 不产生信封写入与通知 */
    @Test
    fun `fails fast when version increment fails`() {
        Mockito.`when`(aspenRedisOperations.increment("aspen:local:auth:clients:version"))
            .thenThrow(IllegalArgumentException("版本计数器取号失败"))

        assertFailsWith<IllegalArgumentException> { publisher.publishAll(emptyList()) }

        Mockito.verify(aspenRedisOperations, Mockito.never())
            .setValueIfNewer(Mockito.anyString(), Mockito.anyString(), Mockito.anyString())
    }

    /**
     * eq matcher 的非空包装: Kotlin 非空参数不接受 matcher 返回的 null
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
     * capture matcher 的非空包装: Kotlin 非空参数不接受 matcher 返回的 null
     *
     * @param captor 用于捕获实参的字符串捕获器
     * @return matcher 登记结果, matcher 返回 null 时回退为空字符串
     */
    private fun captureText(captor: ArgumentCaptor<String>): String = captor.capture() ?: ""

    /**
     * 构造可进入信封的合法端快照 fixture
     *
     * @param clientCode 端编码
     * @return 携带密码登录方式行、可正常序列化的端快照
     */
    private fun client(clientCode: String): AuthClientSnapshot =
        AuthClientSnapshot(
            clientCode = clientCode,
            clientKind = "admin",
            accessTokenTtlSeconds = null,
            refreshTokenTtlSeconds = null,
            methods = listOf(
                AuthLoginMethodSnapshot(
                    method = "password",
                    captchaKind = "slider",
                    forceChangeOnFirstLogin = true,
                    passwordMaxAgeDays = 90,
                    config = null,
                ),
            ),
        )
}
