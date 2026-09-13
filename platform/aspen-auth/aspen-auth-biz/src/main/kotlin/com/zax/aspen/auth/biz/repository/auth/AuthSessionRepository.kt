package com.zax.aspen.auth.biz.repository.auth

import com.zax.aspen.auth.biz.entity.auth.AuthSessionEntity
import com.zax.aspen.auth.biz.entity.auth.AuthSessionEntityDraft
import com.zax.aspen.auth.biz.entity.auth.refreshTokenHash
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * 访问刷新会话表 auth_session
 *
 * 刷新链路的唯一权威: 登录写入、按摘要定位、轮换覆盖摘要、吊销打标;
 * 会话表无乐观锁列, 轮换按主键覆盖写, 摘要唯一约束兜底并发轮换的重复写入
 */
@Repository
class AuthSessionRepository(
    private val sqlClient: KSqlClient,
) {
    /**
     * 按刷新令牌摘要查找会话
     *
     * @param refreshTokenHash 刷新令牌的 SHA-256 十六进制摘要
     * @return 匹配的会话实体, 摘要已被轮换覆盖 (旧令牌) 或不存在时返回 `null`
     */
    fun findByRefreshTokenHash(refreshTokenHash: String): AuthSessionEntity? =
        sqlClient.createQuery(AuthSessionEntity::class) {
            where(table.refreshTokenHash eq refreshTokenHash)
            select(table)
        }.fetchOneOrNull()

    /**
     * 新增刷新会话, 显式 INSERT_ONLY
     *
     * @param clientKind 端类型 code
     * @param clientCode 具体端编码
     * @param principalId 用户域内主体标识
     * @param refreshTokenHash 刷新令牌摘要
     * @param deviceId 设备标识, 可为 null
     * @param ip 登录来源 IP, 可为 null
     * @param userAgent 登录 UA, 可为 null
     * @param expiresAt 会话过期时间
     * @param now 会话建立时刻, 由服务层时钟统一提供
     * @return 落库后的会话实体, 含数据库生成的 id
     */
    fun insert(
        clientKind: String,
        clientCode: String,
        principalId: Long,
        refreshTokenHash: String,
        deviceId: String?,
        ip: String?,
        userAgent: String?,
        expiresAt: LocalDateTime,
        now: LocalDateTime,
    ): AuthSessionEntity =
        sqlClient.entities.save(
            AuthSessionEntityDraft.`$`.produce {
                this.clientKind = clientKind
                this.clientCode = clientCode
                this.principalId = principalId
                this.refreshTokenHash = refreshTokenHash
                this.deviceId = deviceId
                this.ip = ip
                this.userAgent = userAgent
                this.expiresAt = expiresAt
                createdAt = now
                updatedAt = now
            },
        ) {
            setMode(SaveMode.INSERT_ONLY)
        }.modifiedEntity

    /**
     * 轮换刷新令牌: 覆盖摘要并顺延过期时间
     *
     * @param session 待轮换的会话实体, 仅取其 sessionId 定位行
     * @param newRefreshTokenHash 新刷新令牌的摘要
     * @param newExpiresAt 新的会话过期时间
     * @param now 轮换时刻, 由服务层时钟统一提供
     */
    fun rotate(session: AuthSessionEntity, newRefreshTokenHash: String, newExpiresAt: LocalDateTime, now: LocalDateTime) {
        sqlClient.entities.save(
            AuthSessionEntityDraft.`$`.produce {
                sessionId = session.sessionId
                refreshTokenHash = newRefreshTokenHash
                expiresAt = newExpiresAt
                updatedAt = now
            },
        ) {
            setMode(SaveMode.UPDATE_ONLY)
        }
    }

    /**
     * 吊销会话, 幂等: 已吊销的会话再次吊销只刷新原因
     *
     * @param session 待吊销的会话实体, 仅取其 sessionId 定位行
     * @param reason 吊销原因 (LOGOUT/PRINCIPAL_DISABLED 等)
     * @param now 吊销时刻, 由服务层时钟统一提供
     */
    fun revoke(session: AuthSessionEntity, reason: String, now: LocalDateTime) {
        sqlClient.entities.save(
            AuthSessionEntityDraft.`$`.produce {
                sessionId = session.sessionId
                revokedAt = now
                revokedReason = reason
                updatedAt = now
            },
        ) {
            setMode(SaveMode.UPDATE_ONLY)
        }
    }
}
