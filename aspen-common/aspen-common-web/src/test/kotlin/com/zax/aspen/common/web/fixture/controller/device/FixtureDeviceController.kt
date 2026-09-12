package com.zax.aspen.common.web.fixture.controller.device

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 设备端受众样例 Controller
 *
 * 包链含 controller.device, 用于断言其映射在注册期统一携带 /device-api 前缀
 */
@RestController
class FixtureDeviceController {
    /** 样例端点, 携带受众前缀后的完整映射为 /device-api/fixture */
    @GetMapping("/fixture")
    fun `device fixture endpoint`(): String = "device"
}
