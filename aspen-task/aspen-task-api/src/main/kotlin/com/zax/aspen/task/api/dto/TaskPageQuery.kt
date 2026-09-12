package com.zax.aspen.task.api.dto

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.task.api.enums.TaskTriggerType
import jakarta.validation.constraints.Min

/**
 * 任务定义分页查询入参
 *
 * 查询条件全部可空, 空条件返回全量分页; 单页数量上限由服务端 DatabaseLimits 收敛
 */
data class TaskPageQuery(
    /** 从 1 开始的目标页码 */
    @field:Min(1)
    val pageNumber: Int = 1,
    /** 单页请求的数据条数 */
    @field:Min(1)
    val pageSize: Int = 20,
    /** 任务编码的模糊匹配片段 */
    val taskCode: String? = null,
    /** 任务显示名的模糊匹配片段 */
    val taskName: String? = null,
    /** 启停状态过滤 */
    val status: EnabledStatus? = null,
    /** 触发类型过滤 */
    val triggerType: TaskTriggerType? = null,
)
