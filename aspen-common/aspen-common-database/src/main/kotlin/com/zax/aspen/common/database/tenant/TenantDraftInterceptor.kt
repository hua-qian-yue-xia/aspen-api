package com.zax.aspen.common.database.tenant

import com.zax.aspen.common.database.model.TenantScopedEntity
import com.zax.aspen.common.database.model.TenantScopedEntityDraft
import org.babyfish.jimmer.sql.DraftInterceptor

/** 新增租户隔离数据时自动填充服务端上下文中的租户标识 */
class TenantDraftInterceptor(
    /** 提供当前请求的租户标识 */
    private val tenantContextSupplier: TenantContextSupplier,
) : DraftInterceptor<TenantScopedEntity, TenantScopedEntityDraft> {
    /** 新增数据强制使用服务端租户, 禁止客户端自选租户; 更新不重写租户列 */
    override fun beforeSave(draft: TenantScopedEntityDraft, original: TenantScopedEntity?) {
        if (original == null) {
            draft.tenantId = tenantContextSupplier.get()
                ?: throw IllegalStateException("缺少租户上下文, 拒绝保存租户数据")
        }
    }
}
