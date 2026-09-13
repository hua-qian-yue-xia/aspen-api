package com.zax.aspen.auth.biz.entity.auth

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Table
import java.time.LocalDateTime

/**
 * 保存跨端刷新会话, 是刷新链路的唯一权威
 *
 * 典型场景: 登录成功写入, 刷新时按摘要定位并轮换 (摘要覆盖旧值, 旧令牌立即
 * 作废), 登出或主体失效时吊销; 多主体域拆分后 (管理端 UPM / 未来 app 用户域 /
 * 设备注册表) 会话统一归 Auth, 跨端吊销与设备管理一张表看全; principal_id 的
 * 归属域由 client_kind 判定, 跨库不做外键; 行内主体即操作者, 无独立 by 审计列
 */
@Entity
@Table(name = "auth_session")
interface AuthSessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val sessionId: Long

    /** 端类型 code (AuthClientKind), 决定 principal_id 的归属域与网关前缀校验 */
    val clientKind: String

    /** 具体端编码 (如 aspen-admin-web), 与令牌 client_code claim 一致 */
    val clientCode: String

    /** 用户域内主体标识, 跨域唯一性由 client_kind 限定 */
    val principalId: Long

    /** 当前有效刷新令牌的 SHA-256 摘要; 轮换即覆盖, 旧令牌随即失效 */
    val refreshTokenHash: String

    /** 设备标识, 首版取客户端请求头或 UA 摘要, 供会话管理面展示 */
    val deviceId: String?

    /** 登录来源 IP (IPv6 长度), 仅审计用途 */
    val ip: String?

    /** 登录 User-Agent, 仅审计用途 */
    val userAgent: String?

    /** 会话过期时间 = 登录/最近刷新时刻 + 刷新令牌 TTL */
    val expiresAt: LocalDateTime

    /** 吊销时间; 非空即不可刷新 */
    val revokedAt: LocalDateTime?

    /** 吊销原因 (LOGOUT/ROTATED/PRINCIPAL_DISABLED 等), 审计归类 */
    val revokedReason: String?

    /** 会话建立时间 */
    val createdAt: LocalDateTime

    /** 最近轮换或吊销时间 */
    val updatedAt: LocalDateTime
}
