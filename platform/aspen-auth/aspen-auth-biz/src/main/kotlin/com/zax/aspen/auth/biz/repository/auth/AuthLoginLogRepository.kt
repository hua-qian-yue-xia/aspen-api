package com.zax.aspen.auth.biz.repository.auth

import com.zax.aspen.auth.biz.entity.auth.AuthLoginLogEntity
import com.zax.aspen.auth.biz.entity.auth.AuthLoginLogEntityDraft
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * 访问登录审计表 auth_login_log
 *
 * 只追加: 每次登录尝试 (无论成败) 写一行, 不提供修改与删除入口
 */
@Repository
class AuthLoginLogRepository(
    private val sqlClient: KSqlClient,
) {
    /**
     * 追加一条登录审计记录
     *
     * @param clientKind 端类型 code
     * @param clientCode 具体端编码
     * @param principalId 用户域内主体标识, 凭据未对上时为 null
     * @param account 密码登录尝试的账号原文, 可为 null
     * @param method 认证方式 code
     * @param result 登录结果 (AuthLoginResult)
     * @param failureCode 细化失败码, 可为 null
     * @param ip 登录来源 IP, 可为 null
     * @param userAgent 登录 UA, 可为 null
     * @param now 记录时刻, 由服务层时钟统一提供
     */
    fun append(
        clientKind: String,
        clientCode: String,
        principalId: Long?,
        account: String?,
        method: String,
        result: String,
        failureCode: String?,
        ip: String?,
        userAgent: String?,
        now: LocalDateTime,
    ) {
        sqlClient.entities.save(
            AuthLoginLogEntityDraft.`$`.produce {
                this.clientKind = clientKind
                this.clientCode = clientCode
                this.principalId = principalId
                this.account = account
                this.method = method
                this.result = result
                this.failureCode = failureCode
                this.ip = ip
                this.userAgent = userAgent
                createdAt = now
            },
        ) {
            setMode(SaveMode.INSERT_ONLY)
        }
    }
}
