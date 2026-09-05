package com.zax.aspen.admin.biz.entity.upm

import com.zax.aspen.common.core.enums.common.Gender
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
 * 保存用户稳定身份属性、联系方式和登录状态冗余
 *
 * 典型场景: 用户管理、组织通讯录、登录审计的主体来源;
 * 登录频控与锁定状态冗余在本表, 由 Auth 登录流程更新, 避免每次登录多表写入
 */
@Entity
@Table(name = "upm_user")
interface UpmUserEntity : TenantScopedEntity, MutableAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val userId: Long

    /** 登录用户名, 租户内唯一; 全系统展示与审计的默认标识, 创建后不可修改 */
    val username: String

    /** 昵称, 界面优先展示的名称; 与真实姓名分离以支持匿名社交场景 */
    val nickname: String?

    /** 真实姓名, 用于审批、通讯录等实名场景; 与昵称分开管理 */
    val realName: String?

    /** 用户类型, 约定取值为 member/staff/admin, 决定默认权限与界面入口 */
    @Default("member")
    val userType: String

    /** 用户状态; disabled 后登录被拒绝且已有会话失效; locked 状态配合 lockedUntil 使用 */
    @Default("enabled")
    val status: String

    /** 工号, 企业内唯一标识; 租户内唯一约束, 与外部 HR 系统对账使用 */
    val jobNumber: String?

    /** 主部门主键; 用户多部门任职时的默认数据范围锚点; 引用 upm_user_dept 关系 */
    val primaryDeptId: Long?

    /** 职位头衔, 通讯录展示; 部门内的任职头衔以 upm_user_dept 为准 */
    val positionTitle: String?

    /** 头像地址, 界面展示; 上传后由对象存储生成 */
    val avatarUrl: String?

    /** 原始邮箱, 用户输入保留; 唯一性以规范化邮箱为准 */
    val email: String?

    /** 规范化邮箱, 小写去点号等标准化后用于租户内唯一约束与登录查找 */
    val emailNormalized: String?

    /** 邮箱验证时间; 为空表示未验证; 影响邮箱找回密码等高敏感操作 */
    val emailVerifiedAt: LocalDateTime?

    /** 手机区号, 例如 86; 与 mobile 组合成完整号码 */
    val mobileCountryCode: String?

    /** 原始手机号, 展示保留; 唯一性与查找以规范化手机号为准 */
    val mobile: String?

    /** 规范化手机号, 去分隔符标准化后用于租户内唯一约束与登录查找 */
    val mobileNormalized: String?

    /** 手机验证时间; 为空表示未验证; 影响短信找回密码等高敏感操作 */
    val mobileVerifiedAt: LocalDateTime?

    /** 性别; 默认 UNKNOWN 避免强制采集, 取值与语义由 Gender 枚举定义 */
    @Default("UNKNOWN")
    val gender: Gender

    /** 用户选择的语言; 为空时回退租户 locale */
    val locale: String?

    /** 用户选择的时区; 为空时回退租户 timezone */
    val timezone: String?

    /** 用户来源, 约定取值为 local/ldap/oidc 等, 标记首次创建渠道 */
    @Default("local")
    val source: String

    /** 外部系统唯一键, 与 source 组合做租户内唯一约束, 用于外部数据同步对账 */
    val externalKey: String?

    /** 连续登录失败计数; 成功登录后清零, 达到阈值触发锁定 */
    @Default("0")
    val failedLoginCount: Int

    /** 锁定截止时间; 在此之前登录被拒绝; 由 Auth 按失败策略写入 */
    val lockedUntil: LocalDateTime?

    /** 最后登录时间; 审计与活跃度统计使用, 非权威会话状态 */
    val lastLoginAt: LocalDateTime?

    /** 最后登录 IP; 安全审计使用; 展示时按合规要求脱敏 */
    val lastLoginIp: String?

    /** 累计登录成功次数; 活跃度统计使用 */
    @Default("0")
    val loginCount: Int

    /** 权限缓存版本; 角色或权限变更时递增, 用于失效该用户的权限缓存 */
    @Default("1")
    val authorizationVersion: Int

    /** 最后密码修改时间; 密码过期策略以此计算 */
    val passwordChangedAt: LocalDateTime?

    /** 强制改密标记; 下次登录必须修改密码, 常用于管理员重置后首次登录 */
    @Default("false")
    val mustChangePassword: Boolean

    /** 用户有效期起点; 为空表示立即生效; 登录时校验 */
    val validFrom: LocalDateTime?

    /** 用户有效期终点; 过期后登录被拒绝; 为空表示长期有效 */
    val validTo: LocalDateTime?
}
