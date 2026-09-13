package com.zax.aspen.admin.api.dto.sys

import com.zax.aspen.common.core.enums.common.EnabledStatus
import jakarta.validation.constraints.Min

/**
 * 字典分页查询入参
 *
 * 查询条件全部可空, 空条件返回全量分页; 单页数量上限由服务端 DatabaseLimits 收敛
 */
data class SysDictPageQuery(
    /** 从 1 开始的目标页码 */
    @field:Min(1)
    val pageNumber: Int = 1,
    /** 单页请求的数据条数 */
    @field:Min(1)
    val pageSize: Int = 20,
    /** 编码或显示名的模糊匹配片段 */
    val keyword: String? = null,
    /** 分组精确过滤 */
    val dictGroup: String? = null,
    /** 启停状态过滤 */
    val status: EnabledStatus? = null,
)
