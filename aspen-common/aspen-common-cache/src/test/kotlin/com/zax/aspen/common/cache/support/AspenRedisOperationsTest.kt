package com.zax.aspen.common.cache.support

import com.zax.aspen.common.core.error.CommonErrorCode
import org.mockito.Mockito
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** 验证分发原语的委托行为、订阅回调与故障包装 */
class AspenRedisOperationsTest {
    private val stringRedisTemplate: StringRedisTemplate = Mockito.mock(StringRedisTemplate::class.java)

    @Suppress("UNCHECKED_CAST")
    private val valueOperations: ValueOperations<String, String> =
        Mockito.mock(ValueOperations::class.java) as ValueOperations<String, String>

    private val listenerContainer: RedisMessageListenerContainer =
        Mockito.mock(RedisMessageListenerContainer::class.java)

    private val operations = AspenRedisOperations(stringRedisTemplate, listenerContainer)

    @Test
    fun `increment delegates and returns new value`() {
        Mockito.`when`(stringRedisTemplate.opsForValue()).thenReturn(valueOperations)
        Mockito.`when`(valueOperations.increment("aspen:local:gateway:routes:version")).thenReturn(7L)

        assertEquals(7L, operations.increment("aspen:local:gateway:routes:version"))
    }

    @Test
    fun `set and get delegate to value operations`() {
        Mockito.`when`(stringRedisTemplate.opsForValue()).thenReturn(valueOperations)
        Mockito.`when`(valueOperations.get("aspen:local:gateway:routes")).thenReturn("""{"version":1}""")

        operations.setValue("aspen:local:gateway:routes", """{"version":1}""")

        Mockito.verify(valueOperations).set("aspen:local:gateway:routes", """{"version":1}""")
        assertEquals("""{"version":1}""", operations.getValue("aspen:local:gateway:routes"))
    }

    @Test
    fun `publish delegates to template`() {
        operations.publish("aspen:local:gateway:routes:refresh", "7")

        Mockito.verify(stringRedisTemplate).convertAndSend("aspen:local:gateway:routes:refresh", "7")
    }

    @Test
    fun `subscribe handler receives message body as string`() {
        operations.subscribe("aspen:local:gateway:routes:refresh") { message -> received.add(message) }

        val captor = org.mockito.ArgumentCaptor.forClass(org.springframework.data.redis.connection.MessageListener::class.java)
        Mockito.verify(listenerContainer)
            .addMessageListener(captor.capture(), Mockito.any(ChannelTopic::class.java))
        val body = "9".toByteArray(StandardCharsets.UTF_8)
        captor.value.onMessage(
            object : org.springframework.data.redis.connection.Message {
                override fun getBody(): ByteArray = body

                override fun getChannel(): ByteArray = ByteArray(0)
            },
            null,
        )

        assertEquals(listOf("9"), received)
    }

    @Test
    fun `redis failures are wrapped without losing cause`() {
        Mockito.`when`(stringRedisTemplate.opsForValue()).thenReturn(valueOperations)
        Mockito.`when`(valueOperations.get("aspen:local:gateway:routes"))
            .thenThrow(RedisConnectionFailureException("redis unavailable"))

        val exception = assertFailsWith<CacheAccessException> { operations.getValue("aspen:local:gateway:routes") }

        assertEquals(CommonErrorCode.DEPENDENCY_UNAVAILABLE, exception.errorCode)
        assertIs<RedisConnectionFailureException>(exception.cause)
    }

    @Test
    fun `illegal key and channel names are rejected`() {
        assertFailsWith<IllegalArgumentException> { operations.getValue("bad key with space") }
        assertFailsWith<IllegalArgumentException> { operations.publish("bad channel", "1") }
    }

    private val received = mutableListOf<String>()
}
