package com.zax.aspen.admin.biz.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Controller 受众包结构守护测试
 *
 * 《技术架构》7.5 的强制项: Controller 按受众分目录, controller/admin|app|device 下的
 * 映射经 aspen-common-web 统一携带 /admin-api、/app-api、/device-api 前缀,
 * internal 不加前缀, advice 承载本服务特有的协议异常处理; 受众目录之外不允许出现
 * 散置 Controller, 保证「包位置即受众」的判定规则成立
 */
class ControllerAudiencePackageTest {
    /** 验证全部 Controller 源文件位于受众目录的规范层级内 */
    @Test
    fun `controller files live under audience packages`() {
        val controllerDir =
            File("src/main/kotlin/com/zax/aspen/admin/biz/controller")
        assertTrue(controllerDir.isDirectory, "Controller 目录不存在: ${controllerDir.path}")

        val allowed =
            Regex("^(?:(?:admin|app|device|internal)/[a-z][a-z0-9]*/[^/]+\\.kt|advice/[^/]+\\.kt)$")
        val violations =
            controllerDir
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .map { it.relativeTo(controllerDir).invariantSeparatorsPath }
                .filterNot { allowed.matches(it) }
                .sorted()
                .toList()

        assertEquals(
            emptyList(),
            violations,
            "Controller 必须位于 controller/{admin|app|device|internal}/{module}/ 或 controller/advice/, 违规:\n${violations.joinToString("\n")}",
        )
    }
}
