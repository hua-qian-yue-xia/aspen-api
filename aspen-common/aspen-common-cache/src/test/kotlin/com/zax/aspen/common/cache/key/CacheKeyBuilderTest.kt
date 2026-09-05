package com.zax.aspen.common.cache.key

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 验证 Redis Key 的命名空间格式和分段安全规则 */
class CacheKeyBuilderTest {
    /** 使用固定生产命名空间执行 Key 构建测试 */
    private val builder = CacheKeyBuilder("aspen", "prod", "aspen-admin-biz")

    /** 验证部署信息和业务信息都进入最终 Redis Key */
    @Test
    fun `builds a deployment and business scoped key`() {
        val key = CacheKey("upm", "user", "42")

        assertEquals("aspen:prod:aspen-admin-biz:upm:user:42", builder.build(key))
        assertEquals("aspen:prod:aspen-admin-biz:upm:user:", builder.prefix("upm", "user"))
    }

    /** 验证可能产生歧义或碰撞的 Key 分段会被拒绝 */
    @Test
    fun `rejects ambiguous or unsafe key segments`() {
        assertFailsWith<IllegalArgumentException> { CacheKey("upm", "user", "") }
        assertFailsWith<IllegalArgumentException> { CacheKey("upm", "user", "a:b") }
        assertFailsWith<IllegalArgumentException> { CacheKey("upm", "user name", "42") }
        assertFailsWith<IllegalArgumentException> { CacheKeyBuilder("aspen", "prod", "") }
    }

    /** 验证 Admin 可以通过配置将业务组严格限制为 upm 和 sys */
    @Test
    fun `restricts admin keys to configured business groups`() {
        val allowedGroups = linkedSetOf("upm", "sys")
        val adminBuilder = CacheKeyBuilder("aspen", "prod", "aspen-admin-biz", allowedGroups)

        assertEquals(
            "aspen:prod:aspen-admin-biz:sys:setting:theme",
            adminBuilder.build(CacheKey("sys", "setting", "theme")),
        )
        assertFailsWith<IllegalArgumentException> {
            adminBuilder.build(CacheKey("other", "setting", "theme"))
        }

        // 构建器复制外部集合, 防止启动后修改配置对象绕过分组限制
        allowedGroups += "other"
        assertFailsWith<IllegalArgumentException> {
            adminBuilder.build(CacheKey("other", "setting", "theme"))
        }
    }
}
