package com.zax.aspen.common.security.consume

import com.zax.aspen.common.cache.support.AspenRedisOperations
import com.zax.aspen.common.security.AspenSecurityProperties
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 覆盖客户端配置快照的加载、两段式解析与版本比对语义 */
class ClientConfigSnapshotStoreTest {
    private val aspenRedisOperations: AspenRedisOperations =
        Mockito.mock(AspenRedisOperations::class.java)

    private val objectMapper: ObjectMapper =
        JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    private val store = ClientConfigSnapshotStore(
        aspenRedisOperations = aspenRedisOperations,
        objectMapper = objectMapper,
        properties = AspenSecurityProperties(),
    )

    /** 验证首次加载采纳合法信封, findByCode 能按端编码取到方式行 */
    @Test
    fun `adopts valid envelope and finds client by code`() {
        Mockito.`when`(aspenRedisOperations.getValue("aspen:local:auth:clients"))
            .thenReturn(envelope(version = 2, clientCode = "aspen-admin-web"))

        assertTrue(store.refresh())
        assertEquals(2L, store.version)
        val client = assertIs<com.zax.aspen.common.security.snapshot.AuthClientSnapshot>(
            store.findByCode("aspen-admin-web"),
        )
        assertEquals("admin", client.clientKind)
        assertEquals(1, client.methods.size)
        assertNull(store.findByCode("unknown-client"))
    }

    /** 验证信封 Key 缺失或未发布时保留旧快照并返回 false */
    @Test
    fun `keeps local snapshot when key is absent`() {
        Mockito.`when`(aspenRedisOperations.getValue("aspen:local:auth:clients")).thenReturn(null)

        assertFalse(store.refresh())
        assertNull(store.version)
        assertTrue(store.currentSnapshots().isEmpty())
    }

    /** 验证信封级损坏保留旧快照, 单端损坏跳过不阻塞整体刷新 */
    @Test
    fun `keeps old snapshot on broken envelope and skips broken client`() {
        Mockito.`when`(aspenRedisOperations.getValue("aspen:local:auth:clients"))
            .thenReturn(envelope(version = 2, clientCode = "aspen-admin-web"))
        assertTrue(store.refresh())

        Mockito.`when`(aspenRedisOperations.getValue("aspen:local:auth:clients"))
            .thenReturn("""{"version":3,"publishedAt":"x","clients":["broken"]}""")
        assertTrue(store.refresh())
        assertEquals(3L, store.version)
        assertTrue(store.currentSnapshots().isEmpty())

        Mockito.`when`(aspenRedisOperations.getValue("aspen:local:auth:clients"))
            .thenReturn("not-a-json")
        assertFalse(store.refresh())
        assertEquals(3L, store.version)
    }

    /** 验证旧版本信封不回退本地快照 */
    @Test
    fun `ignores stale version`() {
        Mockito.`when`(aspenRedisOperations.getValue("aspen:local:auth:clients"))
            .thenReturn(envelope(version = 5, clientCode = "aspen-admin-web"))
        assertTrue(store.refresh())

        Mockito.`when`(aspenRedisOperations.getValue("aspen:local:auth:clients"))
            .thenReturn(envelope(version = 4, clientCode = "aspen-admin-web"))
        assertFalse(store.refresh())
        assertEquals(5L, store.version)
    }

    /**
     * 构造合法信封 JSON fixture
     *
     * @param version 信封版本号
     * @param clientCode 端编码
     * @return 可被解析的 AuthClientCatalogSnapshot JSON 文本
     */
    private fun envelope(version: Long, clientCode: String): String =
        """
        {
          "version": $version,
          "publishedAt": "2026-09-13T10:00+08:00",
          "clients": [
            {
              "clientCode": "$clientCode",
              "clientKind": "admin",
              "accessTokenTtlSeconds": null,
              "refreshTokenTtlSeconds": null,
              "methods": [
                {
                  "method": "password",
                  "captchaKind": "slider",
                  "forceChangeOnFirstLogin": true,
                  "passwordMaxAgeDays": 90,
                  "config": null
                }
              ]
            }
          ]
        }
        """.trimIndent()
}
