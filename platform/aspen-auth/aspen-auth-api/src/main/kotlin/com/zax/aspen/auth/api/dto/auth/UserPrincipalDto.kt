package com.zax.aspen.auth.api.dto.auth

import com.zax.aspen.auth.api.enums.auth.PrincipalStatus

/**
 * 认证主体的最小视图
 *
 * Principal SPI 的统一返回体: 只携带认证引擎判定所需的标识、状态与安全标记,
 * 不暴露用户资料、角色权限等用户域内部数据; tenantId 可空——管理端主体必填,
 * C 端主体 (未来 app 用户域) 无租户语义
 */
data class UserPrincipalDto(
    /** 用户域内主体标识, 跨域唯一性由端类型限定 (管理端为 upm_user.user_id) */
    val principalId: Long,
    /** 展示名, 用于登录成功回执与审计日志的可读性 */
    val displayName: String,
    /** 主体状态, DISABLED/LOCKED 时认证引擎拒绝发令牌并按策略吊销既有会话 */
    val status: PrincipalStatus,
    /** 所属租户标识, 管理端主体必填; C 端主体为 null */
    val tenantId: Long?,
    /** 是否需要强制修改密码: 首登策略或密码过期触发, 登录回执携带改密动作 */
    val mustChangePassword: Boolean,
)
