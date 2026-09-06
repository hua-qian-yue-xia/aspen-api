package com.zax.aspen

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cache Key 工厂边界测试
 *
 * 《Common 模块设计》第 5 节的强制项: CacheKey 只能由服务内 cache 包的 *CacheKeys
 * 工厂对象构造, 工厂之外的业务源码不得出现 CacheKey(...) 裸构造, 保证 group/domain
 * 字面量与业务 ID 的 Key 段转换不散落在调用点
 */
class CacheKeyFactoryBoundaryTest {
    /** 校验业务源码中的 CacheKey 构造只出现在键工厂文件里 */
    @Test
    fun `cache key construction only appears in key factories`() {
        val offenders = File(".")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.path.contains("src/main/kotlin/") }
            .filter { !it.path.contains("/build/") }
            .filter { !it.path.contains("aspen-common/aspen-common-cache/") }
            .filter { !it.name.endsWith("CacheKeys.kt") }
            .filter { it.readText().contains("CacheKey(") }
            .map { it.path }
            .toList()

        assertEquals(
            emptyList(),
            offenders,
            "以下源码绕过键工厂直接构造 CacheKey, 必须改为服务内 cache 包的 *CacheKeys 工厂函数: $offenders",
        )
    }

    /** 校验键工厂文件都位于服务内 cache 包, 保证命名与放置位置统一 */
    @Test
    fun `key factories live in service cache packages`() {
        val offenders = File(".")
            .walkTopDown()
            .filter { it.isFile && it.name.endsWith("CacheKeys.kt") }
            .filter { it.path.contains("src/main/kotlin/") }
            .filter { !it.path.contains("/build/") }
            .filter { !it.path.contains("/cache/") }
            .map { it.path }
            .toList()

        assertEquals(emptyList(), offenders, "键工厂必须放在服务内 cache 包: $offenders")
    }
}
