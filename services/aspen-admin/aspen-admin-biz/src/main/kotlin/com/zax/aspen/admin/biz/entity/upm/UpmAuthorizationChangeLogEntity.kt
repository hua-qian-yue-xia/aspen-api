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
 * 保存不可变的授权主体变更前后快照
 *
 * 典型场景: 高风险权限操作的合规审计, 例如角色授权调整、用户权限收回;
 * 只追加不更新, 不提供删除流程
 */
@Entity
@Table(name = "upm_authorization_change_log")
interface UpmAuthorizationChangeLogEntity : TenantScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val authorizationChangeLogId: Long

    /** 执行变更的操作人主体标识; 系统自动变更时为服务身份字符串 */
    val operatorUserId: String?

    /** 变更主体类型, 约定取值为 user/role/menu/permission */
    val subjectType: String

    /** 变更主体主键 */
    val subjectId: Long

    /** 变更动作, 约定取值为 create/update/delete/grant/revoke */
    val action: String

    /** 变更前快照; 新建时为空; 只保留授权相关字段, 敏感字段不入快照 */
    @Serialized
    val beforeSnapshot: Map<String, Any?>?

    /** 变更后快照; 删除时为空 */
    @Serialized
    val afterSnapshot: Map<String, Any?>?

    /** 变更原因, 操作人填写或系统生成; 合规追溯使用 */
    val reason: String?

    /** 变更请求追踪 ID; 与网关日志关联 */
    val requestId: String?

    /** 操作来源 IP; 审计使用 */
    val ipAddress: String?

    /** 变更发生时间; 审计主时间轴 */
    @Default("now")
    val occurredAt: LocalDateTime
}
