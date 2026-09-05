package com.zax.aspen.admin.api.gen

import com.zax.aspen.common.core.gen.GenDictDescriptor
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

/**
 * 服务向 Admin 上报枚举字典目录的内部契约
 *
 * 各服务在 @GenDict 播种开启时把扫描出的目录上报本端点, Admin 幂等播种进 sys_dict 与
 * sys_dict_item; 属于 internal 契约, 不经网关暴露, v1 无鉴权, 依赖默认关闭与网络隔离兜底
 */
interface GenDictReportApi {
    /** 接收上报方的字典目录并按配置模式幂等播种 */
    @PostMapping(PATH)
    fun reportDicts(
        @RequestBody descriptors: List<GenDictDescriptor>,
    )

    companion object {
        /** 上报端点路径 */
        const val PATH = "/internal/gen/dict-report"
    }
}
