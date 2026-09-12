package com.zax.aspen.admin.biz.repository.upm

import com.zax.aspen.admin.biz.entity.upm.UpmUserCredentialEntity
import com.zax.aspen.admin.biz.entity.upm.UpmUserCredentialEntityDraft
import com.zax.aspen.admin.biz.entity.upm.credentialType
import com.zax.aspen.admin.biz.entity.upm.status
import com.zax.aspen.admin.biz.entity.upm.userId
import com.zax.aspen.common.core.enums.common.EnabledStatus
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * 访问用户凭证表 upm_user_credential
 *
 * UPM 组常驻业务仓储, 服务认证主体 SPI 的密码比对、失败计数与锁定判定;
 * 查询是跨租户读, 调用方必须包裹在 TenantSystemContext 的显式系统上下文内
 */
@Repository
class UpmUserCredentialRepository(
    private val sqlClient: KSqlClient,
) {
    /**
     * 查找用户当前启用的密码凭证行
     *
     * @param userId 用户表主键 id
     * @return 启用状态的密码凭证, 不存在或已停用时返回 `null`
     */
    fun findActivePassword(userId: Long): UpmUserCredentialEntity? =
        sqlClient.createQuery(UpmUserCredentialEntity::class) {
            where(table.userId eq userId)
            where(table.credentialType eq CREDENTIAL_TYPE_PASSWORD)
            where(table.status eq EnabledStatus.ENABLED)
            select(table)
        }.fetchOneOrNull()

    /**
     * 记录一次成功校验: 清零失败计数、解锁并刷新最近使用时间
     *
     * @param credential 校验通过的凭证行, 提供主键与乐观锁 version
     * @param now 本次成功时刻, 由服务层时钟统一提供
     */
    fun markSuccess(credential: UpmUserCredentialEntity, now: LocalDateTime) {
        sqlClient.entities.save(
            UpmUserCredentialEntityDraft.`$`.produce {
                userCredentialId = credential.userCredentialId
                version = credential.version
                failedAttemptCount = 0
                lockedUntil = null
                lastUsedAt = now
            },
        ) {
            setMode(org.babyfish.jimmer.sql.ast.mutation.SaveMode.UPDATE_ONLY)
        }
    }

    /**
     * 记录一次失败校验: 递增失败计数, 达到阈值时写入锁定截止时间
     *
     * @param credential 校验失败的凭证行, 提供主键与乐观锁 version
     * @param maxAttempts 触发锁定的失败次数阈值
     * @param lockMinutes 锁定窗口时长 (分钟)
     * @param now 本次失败时刻, 锁定截止时间由此推算
     */
    fun markFailure(
        credential: UpmUserCredentialEntity,
        maxAttempts: Int,
        lockMinutes: Long,
        now: LocalDateTime,
    ) {
        val nextCount = credential.failedAttemptCount + 1
        sqlClient.entities.save(
            UpmUserCredentialEntityDraft.`$`.produce {
                userCredentialId = credential.userCredentialId
                version = credential.version
                failedAttemptCount = nextCount
                if (nextCount >= maxAttempts) {
                    lockedUntil = now.plusMinutes(lockMinutes)
                }
            },
        ) {
            setMode(org.babyfish.jimmer.sql.ast.mutation.SaveMode.UPDATE_ONLY)
        }
    }

    /**
     * 新增密码凭证行, 显式 INSERT_ONLY, 供超管 bootstrap 建号写入摘要
     *
     * 租户标识由 TenantDraftInterceptor 从显式系统上下文写入, 本方法不接收
     *
     * @param userId 凭证归属的用户主键
     * @param secretHash 密码摘要 (BCrypt), 明文不落库不落日志
     * @param hashAlgorithm 摘要算法标识
     * @param identity 操作人身份标识, 写入 createdBy 与 updatedBy 审计列
     * @return 落库后的凭证实体, 含数据库生成的 id
     */
    fun insert(userId: Long, secretHash: String, hashAlgorithm: String, identity: String): UpmUserCredentialEntity =
        sqlClient.entities.save(
            UpmUserCredentialEntityDraft.`$`.produce {
                this.userId = userId
                this.secretHash = secretHash
                this.hashAlgorithm = hashAlgorithm
                version = 1
                createdBy = identity
                updatedBy = identity
            },
        ) {
            setMode(org.babyfish.jimmer.sql.ast.mutation.SaveMode.INSERT_ONLY)
        }.modifiedEntity

    private companion object {
        /** 密码类型的凭证行标识, 与 V001 的 credential_type 默认值一致 */
        const val CREDENTIAL_TYPE_PASSWORD = "password"
    }
}
