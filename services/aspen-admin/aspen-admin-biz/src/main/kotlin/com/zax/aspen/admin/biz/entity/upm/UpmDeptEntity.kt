package com.zax.aspen.admin.biz.entity.upm

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.database.model.MutableAuditEntity
import com.zax.aspen.common.database.model.TenantScopedEntity
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.IdView
import org.babyfish.jimmer.sql.JoinColumn
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.OneToMany
import org.babyfish.jimmer.sql.Table
import java.time.LocalDateTime

/**
 * 保存部门邻接树、祖先路径和组织属性
 *
 * 典型场景: 组织树展示与数据权限范围计算; 直接父子用 parentId 维护,
 * 祖先后代查询走闭包表 upm_dept_closure, 移动部门时两者在一个事务内同步重建
 */
@Entity
@Table(name = "upm_dept")
interface UpmDeptEntity : TenantScopedEntity, MutableAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val deptId: Long

    /** 父部门; 为空表示根部门; 移动部门时由 Service 同步重建 ancestorPath 与闭包表; 自引用不设数据库外键, 由 Service 校验环 */
    @ManyToOne
    @JoinColumn(name = "parent_id", referencedColumnName = "dept_id")
    val parent: UpmDeptEntity?

    /** 父部门主键; parent 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("parent")
    val parentId: Long?

    /** 部门编码, 租户内唯一; 业务系统与报表的部门引用键 */
    val deptCode: String

    /** 部门名称, 组织树与通讯录展示 */
    val name: String

    /** 部门简称, 空间受限的界面位置使用 */
    val shortName: String?

    /** 部门全称, 带层级语义的完整名称, 例如「总公司/华东大区/上海分公司」 */
    val fullName: String?

    /** 部门类型, 约定取值为 department/group 等, 区分实体部门与虚拟分组 */
    @Default("department")
    val deptType: String

    /** 祖先路径, 例如 /1/5/12/; 冗余加速前缀查询, 与闭包表配合使用 */
    @Default("/")
    val ancestorPath: String

    /** 层级深度, 根为 0; 组织树缩进与层级校验使用 */
    @Default("0")
    val level: Int

    /** 部门第一负责人; 审批与通讯录展示; 多类型负责人见 upm_dept_leader; 引用列不设数据库外键, 由 Service 保证指向在职用户 */
    @ManyToOne
    @JoinColumn(name = "primary_leader_user_id", referencedColumnName = "user_id")
    val primaryLeaderUser: UpmUserEntity?

    /** 第一负责人主键; primaryLeaderUser 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("primaryLeaderUser")
    val primaryLeaderUserId: Long?

    /** 虚拟部门标记; 虚拟部门不参与成本与编制统计, 仅用于权限分组 */
    @Default("false")
    val isVirtual: Boolean

    /** 部门状态; disabled 后不参与授权与通讯录, 子部门不受影响 */
    @Default("ENABLED")
    val status: EnabledStatus

    /** 同级部门展示顺序 */
    @Default("0")
    val sortOrder: Int

    /** 部门联系电话 */
    val phone: String?

    /** 部门邮箱 */
    val email: String?

    /** 部门办公地址 */
    val address: String?

    /** 行政区划代码, 与省市区字典联动 */
    val regionCode: String?

    /** 部门有效期起点; 为空表示立即生效 */
    val validFrom: LocalDateTime?

    /** 部门有效期终点; 过期后不参与授权; 为空表示长期有效 */
    val validTo: LocalDateTime?

    /** 数据来源, 约定取值为 local/hr-sync 等, 标记是否外部系统同步 */
    @Default("local")
    val source: String

    /** 外部系统部门键, HR 同步对账使用; 与 source 组合唯一 */
    val externalKey: String?

    /** 直接子部门数冗余; 组织树懒加载展示使用, 由 Service 维护 */
    @Default("0")
    val childCount: Int

    /** 在职成员数冗余; 通讯录统计使用, 由 Service 维护 */
    @Default("0")
    val memberCount: Int

    /** 直接子部门; 组织树逐级渲染使用, 全量查询走闭包表; 引用列不设数据库外键 */
    @OneToMany(mappedBy = "parent")
    val children: List<UpmDeptEntity>

    /** 部门全部任职关系; 通讯录成员列表与数据权限展开使用 */
    @OneToMany(mappedBy = "dept")
    val userDepts: List<UpmUserDeptEntity>

    /** 本部门作为祖先的闭包行; 「本部门及下级」数据范围展开使用 */
    @OneToMany(mappedBy = "ancestor")
    val ancestorClosures: List<UpmDeptClosureEntity>

    /** 本部门作为后代的闭包行; 查询祖先链与层级校验使用 */
    @OneToMany(mappedBy = "descendant")
    val descendantClosures: List<UpmDeptClosureEntity>

    /** 部门全部负责人; 第一负责人之外的类型以此为准 */
    @OneToMany(mappedBy = "dept")
    val deptLeaders: List<UpmDeptLeaderEntity>

    /** 以本部门为自定义数据范围的角色; 角色数据范围管理使用 */
    @OneToMany(mappedBy = "dept")
    val roleDepts: List<UpmRoleDeptEntity>
}
