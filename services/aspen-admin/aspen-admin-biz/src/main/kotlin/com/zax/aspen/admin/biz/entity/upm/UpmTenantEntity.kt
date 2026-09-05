package com.zax.aspen.admin.biz.entity.upm

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.database.model.MutableAuditEntity
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Serialized
import org.babyfish.jimmer.sql.Table
import java.time.LocalDateTime

/**
 * 保存租户、默认区域、有效期和租户级配置
 *
 * 典型场景: 租户开通、续期与停用管理; 全部租户从属表以 tenantId 引用本表,
 * 租户删除被 RESTRICT 保护, 必须先清理业务数据
 */
@Entity
@Table(name = "upm_tenant")
interface UpmTenantEntity : MutableAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val tenantId: Long

    /** 租户编码, 系统内全局唯一, 出现在日志与运维命令中; 创建后不可修改 */
    val tenantCode: String

    /** 租户完整名称, 用于管理界面展示与搜索 */
    val name: String

    /** 租户简称, 用于空间受限的界面位置, 例如顶部栏徽标 */
    val shortName: String?

    /** 租户独立访问域名; 为空表示走统一域名; 全局唯一 */
    val domain: String?

    /** 租户 Logo 地址, 登录页与顶栏展示 */
    val logoUrl: String?

    /** 租户默认语言, 新用户未自选语言时的界面语言兜底 */
    @Default("zh-CN")
    val locale: String

    /** 租户默认时区, 用于跨时区租户的展示转换; 存储时间统一按部署域时区 */
    @Default("Asia/Shanghai")
    val timezone: String

    /** 租户状态; disabled 后该租户全部用户登录被拒绝, 已登录会话由 Auth 按策略失效 */
    @Default("ENABLED")
    val status: EnabledStatus

    /** 租户有效期起点; 为空表示开通即生效; 登录与授权时校验 */
    val validFrom: LocalDateTime?

    /** 租户有效期终点; 过期后登录被拒绝并提示续期; 为空表示长期有效 */
    val validTo: LocalDateTime?

    /** 租户用户数上限; 新用户创建与邀请注册时校验; 为空表示不限制 */
    val maxUsers: Int?

    /** 租户级配置覆盖, 例如默认密码策略开关; 键值含义由 UPM 定义, 与 sys_config 的运行参数互补 */
    @Serialized
    val configuration: Map<String, Any?>?
}
