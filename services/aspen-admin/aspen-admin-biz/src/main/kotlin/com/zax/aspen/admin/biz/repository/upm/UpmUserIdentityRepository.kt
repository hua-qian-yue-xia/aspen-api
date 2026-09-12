package com.zax.aspen.admin.biz.repository.upm

import com.zax.aspen.admin.biz.entity.upm.UpmUserIdentityEntity
import com.zax.aspen.admin.biz.entity.upm.provider
import com.zax.aspen.admin.biz.entity.upm.status
import com.zax.aspen.admin.biz.entity.upm.subject
import com.zax.aspen.common.core.enums.common.EnabledStatus
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.springframework.stereotype.Repository

/**
 * 访问用户外部身份表 upm_user_identity
 *
 * UPM 组常驻业务仓储, 服务认证主体 SPI 的第三方身份查找 (管理端第三方登录
 * 只认已绑定账号); 查询是跨租户读, 调用方必须包裹在 TenantSystemContext 的
 * 显式系统上下文内
 */
@Repository
class UpmUserIdentityRepository(
    private val sqlClient: KSqlClient,
) {
    /**
     * 按身份提供方与外部主体标识查找启用的绑定关系
     *
     * @param identityType 外部身份类型, 对应 provider 列
     * @param identityId 外部身份标识, 对应 subject 列
     * @return 启用状态的绑定关系, 不存在或已停用时返回 `null`
     */
    fun findEnabled(identityType: String, identityId: String): UpmUserIdentityEntity? =
        sqlClient.createQuery(UpmUserIdentityEntity::class) {
            where(table.provider eq identityType)
            where(table.subject eq identityId)
            where(table.status eq EnabledStatus.ENABLED)
            select(table)
        }.fetchOneOrNull()
}
