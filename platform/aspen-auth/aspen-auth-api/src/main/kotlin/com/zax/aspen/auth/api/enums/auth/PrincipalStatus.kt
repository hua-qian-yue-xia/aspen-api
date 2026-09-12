package com.zax.aspen.auth.api.enums.auth

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.core.enums.EnumColor
import com.zax.aspen.common.core.gen.GenDict

/**
 * 认证主体状态
 *
 * UserPrincipalDto 携带的用户域主体状态快照: 登录与刷新时都复查, DISABLED 或
 * LOCKED 主体在刷新入口被拒绝并吊销会话; 语义由各用户域定义 (锁定如
 * upm_user_credential.locked_until 生效), Auth 只消费判定结果
 */
@GenDict(code = "auth_principal_status", name = "认证主体状态", group = "auth")
enum class PrincipalStatus(
    override val code: String,
    override val description: String,
    override val color: String?,
) : AspenEnum {
    /** 主体正常, 允许登录与刷新 */
    ENABLED("enabled", "正常", EnumColor.SUCCESS),

    /** 主体被禁用, 拒绝登录与刷新; 属管理员处置, 与临时锁定区分 */
    DISABLED("disabled", "已禁用", EnumColor.DANGER),

    /** 主体被临时锁定 (如密码失败次数超限), 锁定窗口内拒绝, 解锁后自动恢复 */
    LOCKED("locked", "已锁定", EnumColor.WARNING),
}
