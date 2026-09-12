package com.zax.aspen.admin.biz.repository.upm

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.admin.biz.entity.upm.UpmTenantEntity
import com.zax.aspen.admin.biz.entity.upm.UpmTenantEntityDraft
import com.zax.aspen.admin.biz.entity.upm.status
import com.zax.aspen.admin.biz.entity.upm.tenantCode
import com.zax.aspen.admin.biz.entity.upm.tenantId
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
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

    /**
     * 按租户编码查找未删除租户
     *
     * @param tenantCode 租户编码, 全局唯一
     * @return 匹配的租户实体, 不存在或已逻辑删除时返回 `null`
     */
    fun findByCode(tenantCode: String): UpmTenantEntity? =
        sqlClient.createQuery(UpmTenantEntity::class) {
            where(table.tenantCode eq tenantCode)
            select(table)
        }.fetchOneOrNull()

    /**
     * 新增租户, 显式 INSERT_ONLY, 供超管 bootstrap 建立平台租户
     *
     * @param tenantCode 租户编码, 全局唯一
     * @param name 租户完整名称
     * @param identity 操作人身份标识, 写入 createdBy 与 updatedBy 审计列
     * @return 落库后的租户实体, 含数据库生成的 id
     */
    fun insert(tenantCode: String, name: String, identity: String): UpmTenantEntity =
        sqlClient.entities.save(
            UpmTenantEntityDraft.`$`.produce {
                this.tenantCode = tenantCode
                this.name = name
                version = 1
                createdBy = identity
                updatedBy = identity
            },
        ) {
            setMode(SaveMode.INSERT_ONLY)
        }.modifiedEntity}
