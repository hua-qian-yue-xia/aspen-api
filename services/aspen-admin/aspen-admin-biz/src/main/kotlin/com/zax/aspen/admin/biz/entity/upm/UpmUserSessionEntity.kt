package com.zax.aspen.admin.biz.entity.upm

import com.zax.aspen.common.database.model.MutableAuditEntity
import com.zax.aspen.common.database.model.TenantScopedEntity
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.IdView
import org.babyfish.jimmer.sql.JoinColumn
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.Table
import java.time.LocalDateTime

/**
 * 保存刷新令牌摘要、设备信息和会话生命周期
 *
 * 典型场景: Auth 刷新令牌时的会话校验与设备管理页的在线设备列表;
 * 本表只存摘要不存原始令牌, 会话的唯一标识为 sessionId
 */
@Entity
@Table(name = "upm_user_session")
interface UpmUserSessionEntity : TenantScopedEntity, MutableAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val userSessionId: Long

    /** 会话归属用户; 用户物理删除时级联删除会话 */
    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "user_id")
    val user: UpmUserEntity

    /** 会话归属用户主键; user 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("user")
    val userId: Long

    /** 会话唯一标识, 由 Auth 签发并随刷新令牌携带; 刷新时以此定位会话 */
    val sessionId: String

    /** 刷新令牌摘要, 只允许保存不可逆摘要, 禁止写入原始令牌; 校验时以相同摘要算法比对 */
    val refreshTokenHash: String

    /** 设备标识, 客户端生成的稳定设备 ID; 设备管理与同设备互斥登录策略使用 */
    val deviceId: String?

    /** 客户端名称, 例如 Chrome 120 / iOS App; 在线设备列表展示 */
    val clientName: String?

    /** 客户端版本; 兼容性问题排查使用 */
    val clientVersion: String?

    /** 登录来源 IP; 安全审计与异地登录提醒使用 */
    val ipAddress: String?

    /** 登录 User-Agent 原文; 设备识别与审计使用 */
    val userAgent: String?

    /** 最后活跃时间; 活跃会话统计与长期未活跃清理使用 */
    val lastActiveAt: LocalDateTime?

    /** 会话过期时间; 过期后刷新被拒绝, 用户必须重新登录 */
    val expiresAt: LocalDateTime

    /** 会话撤销时间; 管理员强制下线或用户注销设备时设置; 早于过期时间即失效 */
    val revokedAt: LocalDateTime?

    /** 撤销会话的操作用户; 管理员强制下线时为执行管理员, 用户自行注销设备时为本人; 系统自动撤销时为空 */
    @ManyToOne
    @JoinColumn(name = "revoked_by", referencedColumnName = "user_id")
    val revokedByUser: UpmUserEntity?

    /** 撤销操作用户主键; revokedByUser 关联的标量视图, 审计追溯与保存时使用 */
    @IdView("revokedByUser")
    val revokedBy: Long?

    /** 撤销原因, 例如「管理员强制下线」「安全策略」; 审计使用 */
    val revokeReason: String?

    /** 会话状态; 与撤销时间分工: revokedAt 记录动作, status 是可查询的当前状态 */
    @Default("enabled")
    val status: String
}
