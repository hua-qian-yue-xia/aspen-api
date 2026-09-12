package com.zax.aspen.common.web.fixture.controller.app

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 用户端受众样例 Controller
 *
 * 包链含 controller.app, 用于断言其映射在注册期统一携带 /app-api 前缀
 */
@RestController
class FixtureAppController {
    /** 样例端点, 携带受众前缀后的完整映射为 /app-api/fixture */
    @GetMapping("/fixture")
    fun `app fixture endpoint`(): String = "app"
}
