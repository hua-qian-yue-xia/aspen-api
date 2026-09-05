package com.zax.aspen.admin.biz.entity.upm

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
 * 保存不可变的登录成功、失败、阻断和挑战审计记录
 *
 * 典型场景: 登录安全审计、暴力破解分析和管理端登录日志查询;
 * 只追加不更新, 不提供删除流程
 */
@Entity
@Table(name = "upm_login_log")
interface UpmLoginLogEntity : TenantScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val loginLogId: Long

    /** 登录用户主键; 输入了不存在用户名时为空, 保留记录用于暴力破解分析 */
    val userId: Long?

    /** 登录时输入的用户名快照; 用户改名后审计仍可读 */
    val usernameSnapshot: String?

    /** 登录使用的身份提供商标识, 例如 local/oidc-google; 联合登录审计使用 */
    val identityProvider: String?

    /** 登录结果, 约定取值为 success/failure/blocked/challenge; challenge 表示进入二次验证 */
    val result: String

    /** 失败错误码, 例如 PASSWORD_INCORRECT/ACCOUNT_LOCKED; 成功时为空 */
    val failureCode: String?

    /** 面向运维的失败描述; 对外展示时需按错误码映射, 不直接透出本字段 */
    val failureMessage: String?

    /** 登录来源 IP; 异地提醒与风控使用 */
    val ipAddress: String?

    /** 登录 User-Agent; 设备指纹分析使用 */
    val userAgent: String?

    /** 客户端设备 ID; 与会话表 deviceId 对应 */
    val deviceId: String?

    /** 登录请求追踪 ID; 与网关日志关联 */
    val requestId: String?

    /** 登录产生的会话标识; success 记录与会话表对应, 其余为空 */
    val sessionId: String?

    /** 登录发生时间; 审计主时间轴 */
    @Default("now")
    val occurredAt: LocalDateTime

    /** 登录附加上下文, 例如风控命中规则; 排查复杂登录问题时使用 */
    @Serialized
    val context: Map<String, Any?>?
}
