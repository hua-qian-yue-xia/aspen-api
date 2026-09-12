package com.zax.aspen.common.web.autoconfigure

import com.zax.aspen.common.web.fixture.FixtureApplication
import com.zax.aspen.common.web.fixture.controller.admin.sys.FixtureAdminController
import com.zax.aspen.common.web.fixture.controller.app.FixtureAppController
import com.zax.aspen.common.web.fixture.controller.device.FixtureDeviceController
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证受众路径前缀可经配置整体改写
 *
 * 经测试属性覆盖三组受众前缀, 断言映射使用配置值而非默认值——
 * 部署侧可整体改写前缀而不改代码, 是 aspen.web 配置的既定语义
 */
@SpringBootTest(
    classes = [FixtureApplication::class],
    properties = [
        "aspen.web.admin-api.prefix=/management",
        "aspen.web.app-api.prefix=/client",
        "aspen.web.device-api.prefix=/hardware",
    ],
)
class AspenWebPrefixOverrideTest {
    @Autowired
    private lateinit var handlerMapping: RequestMappingHandlerMapping

    /** 验证三个受众样例 Controller 的映射携带配置覆盖后的前缀 */
    @Test
    fun `audience prefixes honor configured values`() {
        assertEquals(
            setOf("/management/fixture"),
            patternsOf(FixtureAdminController::class.java),
        )
        assertEquals(
            setOf("/client/fixture"),
            patternsOf(FixtureAppController::class.java),
        )
        assertEquals(
            setOf("/hardware/fixture"),
            patternsOf(FixtureDeviceController::class.java),
        )
    }

    /**
     * 提取指定 Controller 类的全部映射路径
     *
     * @param controllerClass 样例 Controller 的 Bean 类型
     * @return 该 Controller 全部 handler 方法的映射路径集合
     */
    private fun patternsOf(controllerClass: Class<*>): Set<String> =
        handlerMapping.handlerMethods.entries
            .filter { it.value.beanType == controllerClass }
            .flatMap { it.key.patternValues }
            .toSet()
}
