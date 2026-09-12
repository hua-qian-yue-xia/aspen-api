package com.zax.aspen.task.api.enums

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.core.enums.EnumColor
import com.zax.aspen.common.core.gen.GenDict

/**
 * 任务租户圈定方式
 *
 * 决定每次触发要执行的租户集合: 全部租户在触发时实时解析 Admin 启用租户
 * (新开租户自动纳入下一次触发), 指定租户按管理端多选清单执行
 */
@GenDict(code = "task_tenant_scope", name = "任务租户圈定", group = "task")
enum class TaskTenantScope(
    override val code: String,
    override val description: String,
    override val color: String?,
) : AspenEnum {
    /** 全部租户: 每次触发实时拉取启用且在有效期内的租户清单 */
    ALL_TENANTS("all", "全部租户", EnumColor.PRIMARY),

    /** 指定租户: 按 task_tenant 关系表维护的多选清单执行, 变更即时生效 */
    SELECTED_TENANTS("selected", "指定租户", EnumColor.CYAN),
}
