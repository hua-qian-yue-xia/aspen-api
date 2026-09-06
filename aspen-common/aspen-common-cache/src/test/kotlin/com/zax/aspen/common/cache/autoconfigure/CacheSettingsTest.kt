package com.zax.aspen.common.cache.autoconfigure

import com.zax.aspen.common.cache.key.CacheKey
import org.springframework.util.unit.DataSize
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 验证可变外部配置到不可变缓存设置的转换规则
 */
class CacheSettingsTest {
    /** 验证缓存定义被冻结且服务名可以回退到应用名称 */
    @Test
    fun `builds immutable definitions and uses application name fallback`() {
        val properties = validProperties()

        val settings = CacheSettings.from(properties, "aspen-admin-biz")

        assertEquals(Duration.ofMinutes(30), settings.definition("upm-user").ttl)
        assertEquals(
            "aspen:prod:aspen-admin-biz:upm:user:42",
            settings.keyBuilder.build(CacheKey("upm", "user", "42")),
        )
    }

    /** 验证缺少 TTL, 命名空间重复和 Key 不匹配时拒绝启动 */
    @Test
    fun `rejects missing ttl duplicate namespaces and mismatched keys`() {
        val missingTtl = validProperties().apply { definitions.getValue("upm-user").ttl = null }
        assertFailsWith<IllegalArgumentException> { CacheSettings.from(missingTtl, "admin") }

        val duplicate = validProperties().apply {
            definitions["upm-user-copy"] = AspenCacheProperties.Definition().also {
                it.group = "upm"
                it.domain = "user"
                it.ttl = Duration.ofMinutes(5)
            }
        }
        assertFailsWith<IllegalArgumentException> { CacheSettings.from(duplicate, "admin") }

        val settings = CacheSettings.from(validProperties(), "admin")
        assertFailsWith<IllegalArgumentException> {
            settings.definition("upm-user", CacheKey("sys", "user", "42"))
        }
    }

    /** 验证允许未声明 Cache 时必须配置正数默认 TTL */
    @Test
    fun `requires positive default ttl when undeclared caches are enabled`() {
        val properties = validProperties().apply { allowUndeclaredCaches = true }

        assertFailsWith<IllegalArgumentException> { CacheSettings.from(properties, "admin") }

        properties.defaultTtl = Duration.ofMinutes(10)
        val settings = CacheSettings.from(properties, "admin")
        assertEquals(
            Duration.ofMinutes(10),
            settings.definition("temporary", CacheKey("sys", "setting", "theme")).ttl,
        )
    }

    /** 验证未声明 Cache 在严格模式下不能动态创建 */
    @Test
    fun `rejects undeclared caches in strict mode`() {
        val settings = CacheSettings.from(validProperties(), "admin")

        assertFailsWith<IllegalArgumentException> {
            settings.definition("undeclared", CacheKey("upm", "user", "42"))
        }
    }

    /** 验证空部署标识, 非正数容量和非法 TTL 会阻止设置创建 */
    @Test
    fun `rejects invalid deployment size and ttl settings`() {
        val blankEnvironment = validProperties().apply { environment = " " }
        assertFailsWith<IllegalArgumentException> { CacheSettings.from(blankEnvironment, "admin") }

        val blankService = validProperties()
        assertFailsWith<IllegalArgumentException> { CacheSettings.from(blankService, " ") }

        val emptyEntrySize = validProperties().apply { maxEntrySize = DataSize.ofBytes(0) }
        assertFailsWith<IllegalArgumentException> { CacheSettings.from(emptyEntrySize, "admin") }

        val zeroTtl = validProperties().apply {
            definitions.getValue("upm-user").ttl = Duration.ZERO
        }
        assertFailsWith<IllegalArgumentException> { CacheSettings.from(zeroTtl, "admin") }

        val invalidAllowedGroup = validProperties().apply { allowedGroups += "not:valid" }
        assertFailsWith<IllegalArgumentException> { CacheSettings.from(invalidAllowedGroup, "admin") }
    }

    /**
     * 创建包含一个合法显式 Cache 定义的基础配置
     *
     * @return 含 prod 部署标识, upm 与 sys 白名单分组和 upm-user 显式定义的 AspenCacheProperties
     */
    private fun validProperties(): AspenCacheProperties = AspenCacheProperties().apply {
        environment = "prod"
        allowedGroups += setOf("upm", "sys")
        maxEntrySize = DataSize.ofMegabytes(1)
        definitions["upm-user"] = AspenCacheProperties.Definition().also {
            it.group = "upm"
            it.domain = "user"
            it.ttl = Duration.ofMinutes(30)
        }
    }
}
