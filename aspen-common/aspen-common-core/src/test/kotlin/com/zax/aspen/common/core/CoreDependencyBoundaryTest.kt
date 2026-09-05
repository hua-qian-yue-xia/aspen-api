package com.zax.aspen.common.core

import kotlin.test.Test
import kotlin.test.assertFailsWith

/** 验证 core 运行时类路径保持技术中立 */
class CoreDependencyBoundaryTest {
    /** 验证 Spring, Jimmer 和 Redis 类不会通过依赖传入 core */
    @Test
    fun `main runtime remains free of infrastructure frameworks`() {
        listOf(
            "org.springframework.boot.SpringApplication",
            "org.babyfish.jimmer.sql.kt.KSqlClient",
            "org.springframework.data.redis.core.RedisTemplate",
        ).forEach { className ->
            assertFailsWith<ClassNotFoundException>(className) { Class.forName(className) }
        }
    }
}
