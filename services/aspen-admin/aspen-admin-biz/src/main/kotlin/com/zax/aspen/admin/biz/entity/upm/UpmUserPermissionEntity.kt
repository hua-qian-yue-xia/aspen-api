package com.zax.aspen.admin.biz.entity.upm

import com.zax.aspen.common.database.model.CreateAuditEntity
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
 * 保存用户临时、例外或显式拒绝的直接权限
 *
 * 典型场景: 不经过角色的临时授权与显式拒绝; 拒绝项优先级最高,
 * 用于收回角色权限体系内的单项权限
 */
@Entity
@Table(name = "upm_user_permission")
interface UpmUserPermissionEntity : TenantScopedEntity, CreateAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val userPermissionId: Long

    /** 目标用户 */
    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "user_id")
    val user: UpmUserEntity

    /** 目标用户主键; user 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("user")
    val userId: Long

    /** 直接授权的权限 */
    @ManyToOne
    @JoinColumn(name = "permission_id", referencedColumnName = "permission_id")
    val permission: UpmPermissionEntity

    /** 权限主键; permission 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("permission")
    val permissionId: Long

    /** 授权效果, 约定取值为 allow/deny; deny 在权限合并时优先于角色授予的 allow */
    @Default("allow")
    val effect: String

    /** 生效起点; 临时授权常用未来时间点; 为空表示立即生效 */
    val validFrom: LocalDateTime?

    /** 失效终点; 到期自动失效; 为空表示长期有效 */
    val validTo: LocalDateTime?

    /** 授权原因, 例如「项目临时支持」「审计整改收回」; 到期清理与审计使用 */
    val reason: String?

    /** 执行授权的用户; 系统自动授权时为空 */
    @ManyToOne
    @JoinColumn(name = "granted_by", referencedColumnName = "user_id")
    val grantedByUser: UpmUserEntity?

    /** 授权用户主键; grantedByUser 关联的标量视图, 审计追溯与保存时使用 */
    @IdView("grantedByUser")
    val grantedBy: Long?
}
