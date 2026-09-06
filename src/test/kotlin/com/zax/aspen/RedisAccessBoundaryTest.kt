package com.zax.aspen

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 中央 Redis 访问边界测试
 *
 * 《技术架构》14.4 与 17.3 的强制项: Redis 客户端只能由 common-cache 声明并经其
 * 受控操作类暴露, common-cache 之外的业务源码不得出现 RedisTemplate 直连, 模块
 * 构建脚本不得直接声明 data-redis starter; 根模块是路线图第 12 步待移除的迁移期
 * 骨架, 暂时豁免
 */
class RedisAccessBoundaryTest {
    /** 校验 common-cache 之外的业务源码不出现 RedisTemplate 直连 */
    @Test
    fun `business sources outside common cache never touch redis template`() {
        val offenders = mainSourceFiles()
            .filter { !it.path.contains("aspen-common/aspen-common-cache/") }
            .filter { it.readText().contains("RedisTemplate") }
            .map { it.path }

        assertEquals(emptyList(), offenders, "以下业务源码直接使用了 RedisTemplate, 必须改为 common-cache 受控操作类: $offenders")
    }

    /** 校验模块构建脚本不直接声明 data-redis starter */
    @Test
    fun `module build scripts declare data redis only through common cache`() {
        val offenders = File(".")
            .walkTopDown()
            .filter { it.isFile && it.name == "build.gradle.kts" }
            .filter { it.parentFile != File(".") } // 根模块迁移期骨架豁免
            .filter { !it.path.contains("aspen-common/aspen-common-cache/") }
            .filter { it.readText().contains("spring-boot-starter-data-redis") }
            .map { it.path }
            .toList()

        assertEquals(emptyList(), offenders, "以下模块直接声明了 data-redis starter, 必须改为依赖 :aspen-common-cache: $offenders")
    }

    /** 校验受控操作类确实存在于 common-cache, 保证边界测试自身不过期 */
    @Test
    fun `controlled redis operations exist in common cache`() {
        val controlledOperations = File("aspen-common/aspen-common-cache/src/main")
            .walkTopDown()
            .filter { it.isFile && it.name == "AspenRedisOperations.kt" }
            .toList()

        assertTrue(controlledOperations.isNotEmpty(), "common-cache 必须提供 AspenRedisOperations 受控分发原语")
    }

    /**
     * 收集全部模块的 main 源码文件, 排除构建产物目录
     *
     * @return 仓库内全部 main 源码 Kotlin 文件列表
     */
    private fun mainSourceFiles(): List<File> =
        File(".")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.path.contains("src/main/kotlin/") }
            .filter { !it.path.contains("/build/") }
            .toList()
}
