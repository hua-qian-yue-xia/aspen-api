package com.zax.aspen.admin.biz.repository.sys

import com.zax.aspen.admin.biz.entity.sys.SysAuthLoginMethodEntity
import com.zax.aspen.admin.biz.entity.sys.sortOrder
import com.zax.aspen.admin.biz.entity.sys.status
import com.zax.aspen.common.core.enums.common.EnabledStatus
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.asc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.springframework.stereotype.Repository

/**
 * 访问端×登录方式策略表 sys_auth_login_method
 *
 * SYS 组常驻业务仓储, 服务认证配置发布快照构建与未来的认证管理面; 查询不关联
 * 端对象, 调用方按 authClientId 分组归类
 */
@Repository
class SysAuthLoginMethodRepository(
    private val sqlClient: KSqlClient,
) {
    /**
     * 查询全部启用方式行, 按展示顺序排列, 供发布快照构建使用
     *
     * @return 启用状态的方式行列表, 按 sortOrder 升序, 停用或已删除的行不在其中
     */
    fun findAllEnabled(): List<SysAuthLoginMethodEntity> =
        sqlClient.createQuery(SysAuthLoginMethodEntity::class) {
            where(table.status eq EnabledStatus.ENABLED)
            orderBy(table.sortOrder.asc())
            select(table)
        }.execute()
}
