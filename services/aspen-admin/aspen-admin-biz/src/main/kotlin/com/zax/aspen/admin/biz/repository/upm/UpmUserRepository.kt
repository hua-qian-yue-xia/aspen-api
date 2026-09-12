package com.zax.aspen.admin.biz.repository.upm

import com.zax.aspen.admin.biz.entity.upm.UpmUserEntity
import com.zax.aspen.admin.biz.entity.upm.UpmUserEntityDraft
import com.zax.aspen.admin.biz.entity.upm.emailNormalized
import com.zax.aspen.admin.biz.entity.upm.mobileNormalized
import com.zax.aspen.admin.biz.entity.upm.tenantId
import com.zax.aspen.admin.biz.entity.upm.userId
import com.zax.aspen.admin.biz.entity.upm.username
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.or
import org.springframework.stereotype.Repository

/**
 * 访问用户表 upm_user
 *
 * UPM 组常驻业务仓储, 服务认证主体 SPI 的账号定位与主体复查; 本仓储的查询
 * 是跨租户读, 调用方 (UpmPrincipalService) 必须包裹在 TenantSystemContext
 * 的显式系统上下文内, 否则 fail-closed 租户过滤器拒绝执行
 */
@Repository
class UpmUserRepository(
    private val sqlClient: KSqlClient,
) {
    /**
     * 按登录账号跨租户查找唯一用户
     *
     * 账号按用户域可定位形态解释: 用户名精确匹配, 邮箱按小写规范化匹配,
     * 手机号按纯数字规范化匹配; 多租户下同一账号可能命中多行, 命中不止
     * 一行时无法确定主体, 返回 null 交由调用方按账号不存在处理 (租户感知
     * 登录形态上线后本契约升级为携带租户限定)
     *
     * @param account 登录账号原文
     * @return 唯一命中的用户实体, 未命中或命中多行时返回 `null`
     */
    fun findByAccount(account: String): UpmUserEntity? {
        val matched = sqlClient.createQuery(UpmUserEntity::class) {
            val predicates = mutableListOf(table.username eq account)
            val normalizedEmail = account.lowercase()
            if (normalizedEmail.isNotBlank()) {
                predicates += table.emailNormalized eq normalizedEmail
            }
            val normalizedMobile = account.filter { it.isDigit() }
            if (normalizedMobile.isNotEmpty()) {
                predicates += table.mobileNormalized eq normalizedMobile
            }
            where(or(*predicates.toTypedArray()))
            select(table)
        }.execute()
        return matched.singleOrNull()
    }

    /**
     * 按主键查找用户
     *
     * @param userId 用户表主键 id
     * @return 匹配的用户实体, 不存在或已逻辑删除时返回 `null`
     */
    fun findById(userId: Long): UpmUserEntity? =
        sqlClient.createQuery(UpmUserEntity::class) {
            where(table.userId eq userId)
            select(table)
        }.fetchOneOrNull()

    /**
     * 按租户与用户名查找用户, 供超管 bootstrap 幂等判定
     *
     * @param tenantId 租户标识
     * @param username 登录用户名, 租户内唯一
     * @return 匹配的用户实体, 不存在或已逻辑删除时返回 `null`
     */
    fun findByTenantUsername(tenantId: Long, username: String): UpmUserEntity? =
        sqlClient.createQuery(UpmUserEntity::class) {
            where(table.tenantId eq tenantId)
            where(table.username eq username)
            select(table)
        }.fetchOneOrNull()

    /**
     * 新增用户, 显式 INSERT_ONLY, 供超管 bootstrap 建号
     *
     * 租户标识由 TenantDraftInterceptor 从显式系统上下文写入, 本方法不接收;
     * 其余列走实体默认值, 只覆盖用户名与用户类型
     *
     * @param username 登录用户名, 租户内唯一
     * @param userType 用户类型, 超管固定 admin
     * @param identity 操作人身份标识, 写入 createdBy 与 updatedBy 审计列
     * @return 落库后的用户实体, 含数据库生成的 id
     */
    fun insert(username: String, userType: String, identity: String): UpmUserEntity =
        sqlClient.entities.save(
            UpmUserEntityDraft.`$`.produce {
                this.username = username
                this.userType = userType
                version = 1
                createdBy = identity
                updatedBy = identity
            },
        ) {
            setMode(SaveMode.INSERT_ONLY)
        }.modifiedEntity
}
