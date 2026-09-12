package com.zax.aspen.common.web.fixture.controller.admin.sys

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 管理端受众样例 Controller
 *
 * 包链含 controller.admin, 用于断言其映射在注册期统一携带 /admin-api 前缀;
 * 端点路径只写模块相对路径, 与真实契约的 PATH 常量写法一致
 */
@RestController
class FixtureAdminController {
    /** 样例端点, 携带受众前缀后的完整映射为 /admin-api/fixture */
    @GetMapping("/fixture")
    fun `admin fixture endpoint`(): String = "admin"
}
