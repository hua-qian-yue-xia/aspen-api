package com.zax.aspen.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 路由套件边界守护测试
 *
 * 《Common 模块设计》§8.3 的强制项: aspen-common-route 是纯注解契约模块, 源码 import
 * 只允许 Spring Web 注解与 spring-core 的 @AliasFor; 五个 verb 注解的属性面固定为
 * path/summary/description/rateLimit/log, 且除 path (经 @AliasFor 由 MVC 消费) 外
 * 每个属性必须在与模块分离的 aspen-common-web 消费端源码中真实存在读取点——
 * 声明面不得先于消费面膨胀 (pjcloud/NestJS 版的 rateLimit.key、limitType 与
 * RepeatSubmit 均为无消费者的死配置, 引以为鉴)
 */
class RouteSuiteBoundaryTest {
    /** 校验 route 模块的 import 白名单与注解属性消费者 */
    @Test
    fun `route module stays thin and every attribute has a consumer`() {
        val violations = mutableListOf<String>()
        val routeSources = routeMainSources()
        val webMainSource = webMainSource()

        routeSources.forEach { file ->
            file.readLines().forEach { line ->
                val import = IMPORT_PATTERN.find(line)?.groupValues?.get(1)
                if (import != null && ALLOWED_IMPORT_PREFIXES.none { import.startsWith(it) }) {
                    violations.add("${file.name}: 非法依赖 $import, 纯注解模块只允许 Spring Web 注解与 @AliasFor")
                }
            }
        }

        VERB_ANNOTATION_FILES.forEach { fileName ->
            val file = routeSources.firstOrNull { it.name == fileName }
            if (file == null) {
                violations.add("缺少 verb 注解文件 $fileName")
                return@forEach
            }
            val text = file.readText()
            if (!text.contains("@get:AliasFor")) {
                violations.add("$fileName: path 属性必须经 @get:AliasFor 转发给 RequestMapping")
            }
            val declaredAttributes = text.substringAfter("annotation class")
                .let { body -> ATTRIBUTE_PATTERN.findAll(body).map { match -> match.groupValues[1] }.toSet() }
            if (declaredAttributes != EXPECTED_ATTRIBUTES) {
                violations.add("$fileName: 属性面漂移为 $declaredAttributes, 期望 $EXPECTED_ATTRIBUTES, 新增属性必须同步消费端与本守卫")
            }
        }

        REQUIRED_CONSUMER_REFERENCES.forEach { reference ->
            if (!webMainSource.contains(reference)) {
                violations.add("aspen-common-web 消费端缺少路由属性读取点: $reference")
            }
        }

        assertEquals(emptyList(), violations, "路由套件边界违规:\n${violations.joinToString("\n")}")
    }

    /**
     * 收集 aspen-common-route 的 main 源码文件
     *
     * @return route 模块全部 Kotlin 源文件, 模块目录缺失时测试直接失败
     */
    private fun routeMainSources(): List<File> {
        val directory = File(repositoryRoot(), "aspen-common/aspen-common-route/src/main/kotlin")
        assertTrue(directory.isDirectory, "缺少 route 模块源码目录 ${directory.path}")
        return directory.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /**
     * 聚合 aspen-common-web 的 main 源码文本
     *
     * @return 全部 main 源码拼接文本, 供消费者引用检查
     */
    private fun webMainSource(): String {
        val directory = File(repositoryRoot(), "aspen-common/aspen-common-web/src/main/kotlin")
        assertTrue(directory.isDirectory, "缺少 common-web 源码目录 ${directory.path}")
        return directory.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
    }

    /**
     * 定位仓库根目录
     *
     * 架构测试模块位于仓库子目录, 仓库根由本模块构建脚本经系统属性注入绝对路径,
     * 测试内不做目录回溯, 也不依赖运行工作目录
     *
     * @return 仓库根目录, 系统属性缺失时抛出 IllegalStateException
     */
    private fun repositoryRoot(): File =
        File(
            checkNotNull(System.getProperty("aspen.repositoryRoot")) {
                "缺少系统属性 aspen.repositoryRoot, 请经 aspen-architecture-test 的 Gradle test 任务运行"
            },
        )

    private companion object {
        /** import 语句提取模式 */
        private val IMPORT_PATTERN = Regex("^import\\s+([\\w.]+)", RegexOption.MULTILINE)

        /** 注解属性声明提取模式 */
        private val ATTRIBUTE_PATTERN = Regex("val\\s+(\\w+)")

        /** 五个 verb 注解文件名 */
        private val VERB_ANNOTATION_FILES =
            listOf("GetRoute.kt", "PostRoute.kt", "PutRoute.kt", "DeleteRoute.kt", "PatchRoute.kt")

        /** 纯注解模块允许的 import 前缀: Spring Web 注解与 spring-core 的 @AliasFor */
        private val ALLOWED_IMPORT_PREFIXES =
            listOf("org.springframework.web.bind.annotation.", "org.springframework.core.annotation.")

        /** verb 注解的固定属性面 */
        private val EXPECTED_ATTRIBUTES = setOf("path", "summary", "description", "rateLimit", "log")

        /** 消费端必须存在的属性读取点, 与 common-web route 包实现一一对应 */
        private val REQUIRED_CONSUMER_REFERENCES = listOf(
            "spec.summary",
            "spec.description",
            "spec.log",
            ".rateLimit",
            "rateLimit.windowSeconds",
            "rateLimit.limit",
            "rateLimit.scope",
        )
    }
}
