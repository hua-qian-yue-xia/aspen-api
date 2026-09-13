package com.zax.aspen.auth.biz.entity.auth

import com.zax.aspen.auth.api.enums.auth.AuthClientKind
import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.database.model.MutableAuditEntity
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Table

/**
 * 保存统一认证的端注册, 认证引擎按端类型本库直读的唯一权威源
 *
 * 典型场景: 认证引擎登录时按端类型查启用端取令牌 TTL 与方式策略入口, 管理面
 * (后续批次) 经 controller/admin 的 /admin-api/auth-client 维护; 端是平台级配置,
 * 全体租户共用, 不做租户隔离; 机器端密钥只存不可逆摘要, 明文仅在创建或轮换时
 * 展示一次; 2026-09-13 归属修订: 自 Admin sys 迁入 Auth Schema, 快照分发链路退役
 */
@Entity
@Table(name = "auth_client")
interface AuthClientEntity : MutableAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val clientId: Long

    /** 端编码, 小写中划线格式, 全局唯一, 创建后不可修改 */
    val clientCode: String

    /** 端显示名, 用于管理界面展示与搜索 */
    val clientName: String

    /** 端类型, 签入令牌 client_kind claim, 网关据此做 claim 与路径前缀的双向校验 */
    val clientKind: AuthClientKind

    /** 机器端密钥摘要 (BCrypt); 仅 THIRD_PARTY 端必填, 第一方端不持密钥 */
    val secretHash: String?

    /** 摘要算法标识, 与 upm_user_credential 同约定, 供未来算法升级区分存量行 */
    val hashAlgorithm: String?

    /** 访问令牌 TTL 覆盖 (秒); null 表示使用全局默认值 */
    val accessTokenTtlSeconds: Long?

    /** 刷新令牌 TTL 覆盖 (秒); null 表示使用全局默认值 */
    val refreshTokenTtlSeconds: Long?

    /** 端启停状态; disabled 的端登录即时拒绝 */
    @Default("ENABLED")
    val status: EnabledStatus
}
