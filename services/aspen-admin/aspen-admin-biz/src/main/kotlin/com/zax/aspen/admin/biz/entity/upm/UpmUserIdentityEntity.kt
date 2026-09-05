package com.zax.aspen.admin.biz.entity.upm

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.database.model.MutableAuditEntity
import com.zax.aspen.common.database.model.TenantScopedEntity
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Serialized
import org.babyfish.jimmer.sql.Table
import java.time.LocalDateTime

/**
 * 保存 OIDC、SAML、LDAP 等外部身份绑定
 *
 * 典型场景: 外部 IdP 登录时以 provider + subject 定位本地用户并完成联合登录
 */
@Entity
@Table(name = "upm_user_identity")
interface UpmUserIdentityEntity : TenantScopedEntity, MutableAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val userIdentityId: Long

    /** 所属用户主键; 解绑后保留用户本体 */
    val userId: Long

    /** 身份提供商标识, 例如 oidc-google、ldap-corp; 与 subject 组成租户内唯一外部身份 */
    val provider: String

    /** 身份提供商的租户或目录名; 同一 provider 多目录时区分, 例如多套 LDAP */
    @Default("default")
    val providerTenant: String

    /** 外部身份唯一标识, IdP 的 sub 或 LDAP DN; 与 provider 组合唯一, 联合登录的查找键 */
    val subject: String

    /** 外部系统用户名快照, 排查外部登录问题时对照; 不参与唯一约束 */
    val externalUsername: String?

    /** 外部 Profile 快照, IdP 返回的原始声明; 只读对照使用, 权威属性仍以用户表为准 */
    @Serialized
    val profile: Map<String, Any?>?

    /** 绑定完成时间; 解绑再绑定后更新 */
    @Default("now")
    val boundAt: LocalDateTime

    /** 该身份最后登录时间; 统计与清理僵尸绑定使用 */
    val lastLoginAt: LocalDateTime?

    /** 绑定状态; disabled 后该外部身份登录被拒绝, 保留绑定关系用于审计 */
    @Default("ENABLED")
    val status: EnabledStatus
}
