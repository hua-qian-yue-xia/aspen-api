package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Table
import java.time.Instant

/** 保存部门邻接树、祖先路径和组织属性 */
@Entity
@Table(name = "upm_dept")
interface UpmDeptEntity : UpmMutableEntity {
    @Column(name = "parent_id")
    val parentId: Long?

    @Column(name = "dept_code")
    val deptCode: String

    @Column(name = "name")
    val name: String

    @Column(name = "short_name")
    val shortName: String?

    @Column(name = "full_name")
    val fullName: String?

    @Column(name = "dept_type")
    @Default("department")
    val deptType: String

    @Column(name = "ancestor_path")
    @Default("/")
    val ancestorPath: String

    @Column(name = "level")
    @Default("0")
    val level: Int

    @Column(name = "primary_leader_user_id")
    val primaryLeaderUserId: Long?

    @Column(name = "is_virtual")
    @Default("false")
    val isVirtual: Boolean

    @Column(name = "status")
    @Default("enabled")
    val status: String

    @Column(name = "sort_order")
    @Default("0")
    val sortOrder: Int

    @Column(name = "phone")
    val phone: String?

    @Column(name = "email")
    val email: String?

    @Column(name = "address")
    val address: String?

    @Column(name = "region_code")
    val regionCode: String?

    @Column(name = "valid_from")
    val validFrom: Instant?

    @Column(name = "valid_to")
    val validTo: Instant?

    @Column(name = "source")
    @Default("local")
    val source: String

    @Column(name = "external_key")
    val externalKey: String?

    @Column(name = "child_count")
    @Default("0")
    val childCount: Int

    @Column(name = "member_count")
    @Default("0")
    val memberCount: Int
}
