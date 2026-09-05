package com.zax.aspen.common.core.enums.common

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.core.enums.EnumColor

/**
 * 通用启停状态
 *
 * 适用于语义真正同构的启停字段: 启用参与正常业务, 禁用保留数据但不参与;
 * 域内有额外生命周期(如用户锁定、会话撤销)时必须定义域枚举, 禁止在本枚举追加值
 */
enum class EnabledStatus(
    override val code: String,
    override val description: String,
    override val color: String?,
) : AspenEnum {
    /** 启用, 参与正常业务流程 */
    ENABLED("enabled", "启用", EnumColor.SUCCESS),

    /** 禁用, 不参与业务流程但数据保留; 具体影响由各使用方定义并写入字段注释 */
    DISABLED("disabled", "禁用", EnumColor.DANGER),
}
