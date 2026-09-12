package com.zax.aspen.admin.biz.entity.sys

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
 * 保存统一认证的端注册, 是认证客户端配置分发的唯一权威源
 *
 * 典型场景: 管理端经认证管理面维护端定义, Admin 在配置变更后与启动时把全部启用端
 * 构建为版本化快照发布到 Redis, Auth 只读消费; 端是平台级配置, 全体租户共用,
 * 不做租户隔离; 机器端密钥只存不可逆摘要, 明文仅在创建或轮换时展示一次
 */
@Entity
@Table(name = "sys_auth_client")
interface SysAuthClientEntity : MutableAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val authClientId: Long

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

    /** 访问令牌 TTL 覆盖 (秒); null 表示 Auth 使用全局默认值 */
    val accessTokenTtlSeconds: Long?

    /** 刷新令牌 TTL 覆盖 (秒); null 表示 Auth 使用全局默认值 */
    val refreshTokenTtlSeconds: Long?

    /** 端启停状态; disabled 的端不进入发布快照, 该端全部登录方式即时不可用 */
    @Default("ENABLED")
    val status: EnabledStatus
}
