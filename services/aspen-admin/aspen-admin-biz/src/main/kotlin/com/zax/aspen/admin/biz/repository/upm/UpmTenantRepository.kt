package com.zax.aspen.admin.biz.repository.upm

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.admin.biz.entity.upm.UpmTenantEntity
import com.zax.aspen.admin.biz.entity.upm.status
import com.zax.aspen.admin.biz.entity.upm.tenantId
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.asc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * 访问租户表 upm_tenant
 *
 * 逻辑删除由 Jimmer 按 deleted_at 自动过滤; "启用" 判定 = 状态启用且当前时间
 * 处于有效期内, 有效期窗口过滤在内存完成 (租户表量级小, 避免为低频内部契约
 * 引入可空条件组合)
 */
@Repository
class UpmTenantRepository(
    private val sqlClient: KSqlClient,
) {
    /**
     * 查询全部启用租户 (状态启用且在有效期内), 按租户 id 升序
     *
     * @param now 有效期判定基准时间
     * @return 启用租户实体列表, 无启用租户时返回空列表
     */
    fun findAllEnabled(now: LocalDateTime = LocalDateTime.now()): List<UpmTenantEntity> =
        sqlClient.createQuery(UpmTenantEntity::class) {
            where(table.status eq EnabledStatus.ENABLED)
            orderBy(table.tenantId.asc())
            select(table)
        }.execute().filter { tenant ->
            val validFrom = tenant.validFrom
            val validTo = tenant.validTo
            (validFrom == null || !validFrom.isAfter(now)) && (validTo == null || validTo.isAfter(now))
        }
}
