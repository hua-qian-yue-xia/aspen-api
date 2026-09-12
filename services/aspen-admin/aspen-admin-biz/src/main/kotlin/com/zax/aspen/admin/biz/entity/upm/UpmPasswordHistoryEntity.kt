package com.zax.aspen.admin.biz.entity.upm

import com.zax.aspen.common.database.model.CreateAuditEntity
import com.zax.aspen.common.database.model.TenantScopedEntity
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.IdView
import org.babyfish.jimmer.sql.JoinColumn
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.Table

/**
 * 保存历史密码摘要以阻止近期密码复用
 *
 * 典型场景: 修改密码时校验新密码是否与近 N 次历史重复; 超出保留窗口的记录由清理任务删除
 */
@Entity
@Table(name = "upm_password_history")
interface UpmPasswordHistoryEntity : TenantScopedEntity, CreateAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val passwordHistoryId: Long

    /** 历史密码归属用户 */
    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "user_id")
    val user: UpmUserEntity

    /** 历史密码归属用户主键; user 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("user")
    val userId: Long

    /** 历史密码摘要, 只允许保存不可逆摘要, 禁止写入明文; 校验时以相同摘要算法比对 */
    val passwordHash: String

    /** 摘要算法标识, 与凭证表语义一致; 算法升级期间按算法分组校验 */
    val hashAlgorithm: String
}
