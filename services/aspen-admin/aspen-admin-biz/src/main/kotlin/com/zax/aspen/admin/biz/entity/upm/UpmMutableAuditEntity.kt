package com.zax.aspen.admin.biz.entity.upm

import com.zax.aspen.common.database.model.AuditableEntity
import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.LogicalDeleted
import org.babyfish.jimmer.sql.MappedSuperclass
import org.babyfish.jimmer.sql.Serialized
import org.babyfish.jimmer.sql.Version
import java.time.Instant

/** 为可更新 UPM 数据统一版本、审计、扩展信息和时间戳逻辑删除语义 */
@MappedSuperclass
interface UpmMutableAuditEntity : AuditableEntity {
    /** UPM 来源模型的乐观锁版本从一开始 */
    @Version
    @Default("1")
    @Column(name = "version")
    val version: Int

    @Column(name = "remark")
    val remark: String?

    @Serialized
    @Column(name = "extension")
    val extension: Map<String, Any?>?

    @Column(name = "created_by")
    val createdBy: Long?

    @Column(name = "updated_by")
    val updatedBy: Long?

    /** 使用删除时间作为逻辑删除标记, 保留来源模型的删除审计语义 */
    @LogicalDeleted("now")
    @Column(name = "deleted_at")
    val deletedAt: Instant?

    @Column(name = "deleted_by")
    val deletedBy: Long?
}
