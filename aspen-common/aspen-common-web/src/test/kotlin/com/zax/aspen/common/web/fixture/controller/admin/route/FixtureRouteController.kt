package com.zax.aspen.common.web.fixture.controller.admin.route

import org.springframework.web.bind.annotation.RestController

/**
 * 路由套件测试的零注解实现 Controller
 *
 * 全部映射声明来自 [FixtureRouteApi] 契约接口, 自身不携带任何路由注解,
 * 验证解析器按接口层查找路由声明的平台路径
 */
@RestController
class FixtureRouteController : FixtureRouteApi {
    override fun echo(): String = "ok"

    override fun limited(): String = "limited"

    override fun detail(): String = "detail"
}
