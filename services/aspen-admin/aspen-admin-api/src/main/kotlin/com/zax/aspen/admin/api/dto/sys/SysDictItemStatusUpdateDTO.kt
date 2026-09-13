package com.zax.aspen.admin.api.dto.sys

import com.zax.aspen.common.core.enums.common.EnabledStatus
import jakarta.validation.constraints.NotNull

/**
 * 字典项启停状态修改入参
 */
data class SysDictItemStatusUpdateDTO(
    /** 目标启停状态 */
    @field:NotNull
    val status: EnabledStatus,
)
