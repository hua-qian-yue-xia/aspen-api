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
 * 保存密码等可轮换凭证的摘要和锁定状态
 *
 * 典型场景: Auth 登录时按凭证类型取用与校验; 支持一用户多凭证并行与轮换过渡
 */
@Entity
@Table(name = "upm_user_credential")
interface UpmUserCredentialEntity : TenantScopedEntity, MutableAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val userCredentialId: Long

    /** 所属用户主键; 用户物理删除时级联删除凭证 */
    val userId: Long

    /** 凭证类型, 约定取值为 password/passkey 等; 同用户同类型唯一, 新类型先并存再切换 */
    @Default("password")
    val credentialType: String

    /** 凭证摘要, 只允许保存不可逆摘要, 禁止写入明文; 日志与接口响应不得输出 */
    val secretHash: String

    /** 摘要算法标识, 例如 bcrypt/argon2id; 升级算法时与 hashVersion 配合判断是否需要重摘要 */
    val hashAlgorithm: String

    /** 摘要算法版本; 同算法参数升级时递增, 登录成功后按需重摘要 */
    val hashVersion: String?

    /** 凭证状态; disabled 后该凭证登录被拒绝, 保留用于历史审计 */
    @Default("ENABLED")
    val status: EnabledStatus

    /** 凭证过期时间; 为空表示长期有效; 过期后登录要求先更新凭证 */
    val expiresAt: LocalDateTime?

    /** 强制轮换标记; 密码到期策略命中时置位, 下次登录必须修改 */
    @Default("false")
    val mustRotate: Boolean

    /** 该凭证连续失败计数; 成功后清零, 与用户级 failedLoginCount 分别计数 */
    @Default("0")
    val failedAttemptCount: Int

    /** 凭证级锁定截止时间; 防止单凭证暴力破解, 独立于用户级锁定 */
    val lockedUntil: LocalDateTime?

    /** 最后使用时间; 凭证活跃度统计与清理长期未用凭证使用 */
    val lastUsedAt: LocalDateTime?

    /** 轮换完成时间; 新旧凭证并行期间判断轮换是否完成 */
    val rotatedAt: LocalDateTime?
}
