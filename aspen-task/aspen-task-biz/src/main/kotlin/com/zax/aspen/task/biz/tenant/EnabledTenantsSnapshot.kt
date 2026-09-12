package com.zax.aspen.task.biz.tenant

import com.zax.aspen.admin.api.dto.upm.TenantBriefDto

/**
 * 启用租户快照的缓存值包装
 *
 * List 泛型直存会因类型擦除退化为元素映射, 以具名类型承载保证反序列化保真
 */
data class EnabledTenantsSnapshot(
    /** 快照时刻的全部启用租户 */
    val tenants: List<TenantBriefDto>,
)
