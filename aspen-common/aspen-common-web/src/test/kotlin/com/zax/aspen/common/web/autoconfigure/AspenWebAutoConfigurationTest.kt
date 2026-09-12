package com.zax.aspen.common.web.autoconfigure

import com.zax.aspen.common.web.fixture.FixtureApplication
import com.zax.aspen.common.web.fixture.controller.admin.sys.FixtureAdminController
import com.zax.aspen.common.web.fixture.controller.app.FixtureAppController
import com.zax.aspen.common.web.fixture.controller.device.FixtureDeviceController
import com.zax.aspen.common.web.fixture.controller.internal.FixtureInternalController
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证受众路径前缀的默认装配行为
 *
 * 零配置启动 fixture 上下文, 断言 controller/admin、controller/app、controller/device
 * 包下的 `@RestController` 映射分别携带默认前缀 /admin-api、/app-api、/device-api,
 * 而 controller/internal 及其余包不加前缀; 同时覆盖默认配置绑定 (未提供任何 aspen.web 配置)
 */
@SpringBootTest(classes = [FixtureApplication::class])
class AspenWebAutoConfigurationTest {
    @Autowired
    private lateinit var handlerMapping: RequestMappingHandlerMapping

    /** 验证四个受众包的样例 Controller 分别获得默认前缀或保持相对路径 */
    @Test
    fun `audience controllers gain matching default prefix`() {
        assertEquals(setOf("/admin-api/fixture"), patternsOf(FixtureAdminController::class.java))
        assertEquals(setOf("/app-api/fixture"), patternsOf(FixtureAppController::class.java))
        assertEquals(setOf("/device-api/fixture"), patternsOf(FixtureDeviceController::class.java))
        assertEquals(setOf("/fixture"), patternsOf(FixtureInternalController::class.java))
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
