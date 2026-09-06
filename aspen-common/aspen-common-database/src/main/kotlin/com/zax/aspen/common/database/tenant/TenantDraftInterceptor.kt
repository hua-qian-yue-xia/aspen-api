package com.zax.aspen.common.database.tenant

import com.zax.aspen.common.database.model.TenantScopedEntity
import com.zax.aspen.common.database.model.TenantScopedEntityDraft
import org.babyfish.jimmer.sql.DraftInterceptor

/** 新增租户隔离数据时自动填充服务端上下文中的租户标识 */
class TenantDraftInterceptor(
    /** 提供当前请求的租户标识 */
    private val tenantContextSupplier: TenantContextSupplier,
) : DraftInterceptor<TenantScopedEntity, TenantScopedEntityDraft> {
    /**
     * 新增数据强制使用服务端租户标识, 禁止客户端自选租户, 更新不重写租户列
     *
     * 与接口通用的保存前回调相比, 本实现对新增且缺少租户上下文的场景选择快速失败
     *
     * @param draft 待保存实体的草稿对象, 新增时租户标识写入该草稿
     * @param original 保存前的原始实体, 新增时为 `null`
     * @throws IllegalStateException 新增租户数据但租户上下文缺失时拒绝保存
     */
    override fun beforeSave(draft: TenantScopedEntityDraft, original: TenantScopedEntity?) {
        if (original == null) {
            draft.tenantId = tenantContextSupplier.get()
                ?: throw IllegalStateException("缺少租户上下文, 拒绝保存租户数据")
        }
    }
}
