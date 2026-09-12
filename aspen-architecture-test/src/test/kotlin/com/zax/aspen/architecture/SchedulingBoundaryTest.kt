package com.zax.aspen.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 调度边界守护测试
 *
 * 《技术架构》17.3 与 21 的强制项: 全仓只有 aspen-task-biz 可以引入 Quartz;
 * 任何模块禁止 @Scheduled; 普通业务 biz 禁止 task/job/scheduler/security 包,
 * 统一调度一律交给 aspen-task-biz; api 契约目录 (如 api/task) 不受包名规则约束
 */
class SchedulingBoundaryTest {
    /** 校验调度设施只出现在 aspen-task-biz, 业务 biz 无调度包名 */
    @Test
    fun `scheduling facilities stay inside aspen-task-biz`() {
        val violations = sourceFiles()
            .flatMap(::checkFile)
            .sorted()

        assertEquals(
            emptyList(),
            violations,
            "调度边界违规 (统一调度只允许 Quartz 且仅 aspen-task-biz):\n${violations.joinToString("\n")}",
        )
    }

    /**
     * 单文件检查, 提取全部调度边界违规
     *
     * @param file 待检查的 Kotlin 源文件
     * @return 「文件 相对路径: 违规说明」列表
     */
    private fun checkFile(file: File): List<String> {
        val relative = file.relativeTo(repositoryRoot()).invariantSeparatorsPath
        // 本守卫自身的源码以字符串与注释引用违规标识, 排除测试模块自身
        if (relative.startsWith("aspen-architecture-test/")) {
            return emptyList()
        }
        val text = file.readText()
        val violations = mutableListOf<String>()
        if (Regex("@Scheduled\\b").containsMatchIn(text)) {
            violations.add("$relative: 禁止 @Scheduled, 周期任务统一交给 aspen-task-biz")
        }
        if (text.contains("org.quartz") && !relative.startsWith("platform/aspen-task/aspen-task-biz/")) {
            violations.add("$relative: Quartz 只允许 aspen-task-biz 引入")
        }
        if (relative.contains("/biz/") && !relative.startsWith("platform/aspen-task/")) {
            val packageSegments = relative.substringAfter("/biz/").split('/')
            packageSegments.dropLast(1).forEach { segment ->
                if (segment in FORBIDDEN_BIZ_PACKAGES) {
                    violations.add("$relative: 普通业务 biz 禁止 $segment 包")
                }
            }
        }
        return violations
    }

    /**
     * 收集全部模块的 main 与 test 源码文件, 排除构建产物
     *
     * @return 仓库内全部 Kotlin 源文件列表
     */
    private fun sourceFiles(): List<File> =
        repositoryRoot()
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.path.contains("src/main/kotlin/") || it.path.contains("src/test/kotlin/") }
            .filter { !it.path.contains("/build/") }
            .toList()

    /**
     * 定位仓库根目录
     *
     * 架构测试模块位于仓库子目录, 仓库根由本模块构建脚本经系统属性注入绝对路径
     *
     * @return 仓库根目录, 系统属性缺失时抛出 IllegalStateException
     */
    private fun repositoryRoot(): File =
        File(
            checkNotNull(System.getProperty("aspen.repositoryRoot")) {
                "缺少系统属性 aspen.repositoryRoot, 请经 aspen-architecture-test 的 Gradle test 任务运行"
            },
        )

    /** 保存测试使用的常量 */
    private companion object {
        /** 普通业务 biz 禁止的调度与安全包名 */
        val FORBIDDEN_BIZ_PACKAGES = setOf("task", "job", "scheduler", "security")
    }
}
