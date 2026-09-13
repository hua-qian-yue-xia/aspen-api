package com.zax.aspen.auth.biz.repository.auth

import com.zax.aspen.auth.biz.entity.auth.AuthLoginMethodEntity
import com.zax.aspen.auth.biz.entity.auth.clientId
import com.zax.aspen.auth.biz.entity.auth.sortOrder
import com.zax.aspen.auth.biz.entity.auth.status
import com.zax.aspen.common.core.enums.common.EnabledStatus
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.asc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.springframework.stereotype.Repository

/**
 * 访问端×登录方式策略表 auth_login_method
 *
 * 认证域常驻业务仓储, 服务登录链路的方式策略定位; 查询不关联端对象, 调用方按
 * 登录方式枚举自行挑选方式行
 */
@Repository
class AuthLoginMethodRepository(
    private val sqlClient: KSqlClient,
) {
    /**
     * 查询指定端的全部启用方式行, 按展示顺序排列
     *
     * @param clientId 端主键
     * @return 启用状态的方式行列表, 按 sortOrder 升序, 停用或已删除的行不在其中
     */
    fun findEnabledByClient(clientId: Long): List<AuthLoginMethodEntity> =
        sqlClient.createQuery(AuthLoginMethodEntity::class) {
            where(table.clientId eq clientId)
            where(table.status eq EnabledStatus.ENABLED)
            orderBy(table.sortOrder.asc())
            select(table)
        }.execute()
}
