package com.zax.aspen.admin.api.dto.upm

/**
 * 租户的简要信息
 *
 * 供跨服务按租户展开的内部契约使用 (如统一任务服务的全租户投递), 只携带执行
 * 面需要的标识与展示字段, 不暴露租户配置与安全属性
 */
data class TenantBriefDto(
    /** 租户标识 */
    val tenantId: Long,
    /** 租户编码, 系统内全局唯一 */
    val tenantCode: String,
    /** 租户完整名称 */
    val name: String,
)
