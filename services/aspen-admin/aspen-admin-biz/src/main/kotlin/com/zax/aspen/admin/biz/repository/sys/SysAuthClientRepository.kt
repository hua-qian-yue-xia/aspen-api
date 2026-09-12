package com.zax.aspen.admin.biz.repository.sys

import com.zax.aspen.admin.biz.entity.sys.SysAuthClientEntity
import com.zax.aspen.admin.biz.entity.sys.clientCode
import com.zax.aspen.admin.biz.entity.sys.status
import com.zax.aspen.common.core.enums.common.EnabledStatus
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.asc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.springframework.stereotype.Repository

/**
 * 访问端注册表 sys_auth_client
 *
 * SYS 组常驻业务仓储, 服务认证配置发布快照构建与未来的认证管理面; 逻辑删除由
 * Jimmer 按 deleted_at 自动过滤, 查询无需手工排除已删除行
 */
@Repository
class SysAuthClientRepository(
    private val sqlClient: KSqlClient,
) {
    /**
     * 查询全部启用端, 按端编码排列, 供发布快照构建使用
     *
     * @return 启用状态的端实体列表, 按 clientCode 升序, 停用或已删除的端不在其中
     */
    fun findAllEnabled(): List<SysAuthClientEntity> =
        sqlClient.createQuery(SysAuthClientEntity::class) {
            where(table.status eq EnabledStatus.ENABLED)
            orderBy(table.clientCode.asc())
            select(table)
        }.execute()

    /**
     * 按端编码查找未删除端
     *
     * @param clientCode 端编码, 小写中划线格式, 全局唯一
     * @return 匹配的端实体, 不存在或已逻辑删除时返回 `null`
     */
    fun findByCode(clientCode: String): SysAuthClientEntity? =
        sqlClient.createQuery(SysAuthClientEntity::class) {
            where(table.clientCode eq clientCode)
            select(table)
        }.fetchOneOrNull()
}
