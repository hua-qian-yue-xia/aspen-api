package com.zax.aspen.auth.biz.repository.auth

import com.zax.aspen.auth.api.enums.auth.AuthClientKind
import com.zax.aspen.auth.biz.entity.auth.AuthClientEntity
import com.zax.aspen.auth.biz.entity.auth.clientCode
import com.zax.aspen.auth.biz.entity.auth.clientKind
import com.zax.aspen.auth.biz.entity.auth.status
import com.zax.aspen.common.core.enums.common.EnabledStatus
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.asc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.springframework.stereotype.Repository

/**
 * 访问端注册表 auth_client
 *
 * 认证域常驻业务仓储, 服务登录链路的端策略定位与刷新时的端复核; 逻辑删除由
 * Jimmer 按 deleted_at 自动过滤, 查询无需手工排除已删除行
 */
@Repository
class AuthClientRepository(
    private val sqlClient: KSqlClient,
) {
    /**
     * 按端类型查找首个启用端, 登录链路按路径前缀对应的端类型定位策略
     *
     * @param kind 端类型 (由登录路径前缀钉死)
     * @return 匹配的启用端实体, 同类型多端时取编码序最小者, 不存在时返回 `null`
     */
    fun findEnabledByKind(kind: AuthClientKind): AuthClientEntity? =
        sqlClient.createQuery(AuthClientEntity::class) {
            where(table.clientKind eq kind)
            where(table.status eq EnabledStatus.ENABLED)
            orderBy(table.clientCode.asc())
            select(table)
        }.execute().firstOrNull()

    /**
     * 按端编码查找未删除端, 刷新链路按会话记录的端编码复核策略
     *
     * @param clientCode 端编码, 小写中划线格式, 全局唯一
     * @return 匹配的端实体, 不存在或已逻辑删除时返回 `null`
     */
    fun findByCode(clientCode: String): AuthClientEntity? =
        sqlClient.createQuery(AuthClientEntity::class) {
            where(table.clientCode eq clientCode)
            select(table)
        }.fetchOneOrNull()
}
