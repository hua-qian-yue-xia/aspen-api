package com.zax.aspen.admin.biz.entity.upm

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.database.model.MutableAuditEntity
import com.zax.aspen.common.database.model.TenantScopedEntity
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Table
import java.time.LocalDateTime

/**
 * 保存用户 TOTP、WebAuthn 等多因素认证方式
 *
 * 典型场景: 登录第二步验证与 MFA 设备管理; 一个用户可绑定多种认证方式, 其中一条为主方式
 */
@Entity
@Table(name = "upm_user_mfa")
interface UpmUserMfaEntity : TenantScopedEntity, MutableAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val userMfaId: Long

    /** MFA 归属用户主键 */
    val userId: Long

    /** 认证方式类型, 约定取值为 totp/webauthn/email; 同用户同类型唯一 */
    val mfaType: String

    /** MFA 密钥密文, 必须在应用层使用受控密钥加密后写入, 禁止保存 TOTP 明文密钥 */
    val encryptedSecret: String?

    /** WebAuthn 凭证 ID; webauthn 类型使用, totp 类型为空 */
    val credentialId: String?

    /** 主认证方式标记; 登录第二步默认使用主方式; 每用户只有一条 */
    @Default("false")
    val isPrimary: Boolean

    /** 验证通过时间; 绑定流程完成后写入; 为空表示绑定未完成不可用于登录 */
    val verifiedAt: LocalDateTime?

    /** 最后使用时间; 统计与清理未使用认证方式 */
    val lastUsedAt: LocalDateTime?

    /** 认证方式状态; disabled 后登录不再要求或接受该方式 */
    @Default("ENABLED")
    val status: EnabledStatus
}
