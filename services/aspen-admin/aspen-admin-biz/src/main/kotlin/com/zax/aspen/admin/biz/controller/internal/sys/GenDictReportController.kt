package com.zax.aspen.admin.biz.controller.internal.sys

import com.zax.aspen.admin.api.contract.sys.GenDictReportApi
import com.zax.aspen.admin.biz.service.sys.GenDictSeeder
import com.zax.aspen.common.core.gen.GenDictDescriptor
import com.zax.aspen.common.gen.autoconfigure.AspenGenProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.RestController

/**
 * 接收其他服务上报的枚举字典目录并转发播种
 *
 * 路径与映射继承 admin-api 的 GenDictReportApi; 属于 internal 契约, 不经网关暴露,
 * 位于 controller/internal 受众包, 不命中 aspen-common-web 的前缀规则、不加受众前缀;
 * v1 无鉴权, 依赖「播种默认关闭 + 网络不暴露」兜底, auth 服务就绪后补齐
 */
@RestController
@ConditionalOnProperty(prefix = "aspen.gen.dict", name = ["enabled"], havingValue = "true")
class GenDictReportController(
    private val genDictSeeder: GenDictSeeder,
    private val aspenGenProperties: AspenGenProperties,
) : GenDictReportApi {
    override fun reportDicts(descriptors: List<GenDictDescriptor>) {
        genDictSeeder.seed(descriptors, aspenGenProperties.dict.mode)
    }
}
