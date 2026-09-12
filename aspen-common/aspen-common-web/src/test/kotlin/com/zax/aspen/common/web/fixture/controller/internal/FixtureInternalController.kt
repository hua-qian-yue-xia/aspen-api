package com.zax.aspen.common.web.fixture.controller.internal

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 内部端点样例 Controller
 *
 * 包链含 controller.internal, 不命中任何受众规则, 用于断言映射保持模块相对路径、
 * 不被追加前缀; 对应 internal 契约「不经网关暴露」的定位
 */
@RestController
class FixtureInternalController {
    /** 样例端点, 完整映射保持 /fixture, 不携带任何受众前缀 */
    @GetMapping("/fixture")
    fun `internal fixture endpoint`(): String = "internal"
}
