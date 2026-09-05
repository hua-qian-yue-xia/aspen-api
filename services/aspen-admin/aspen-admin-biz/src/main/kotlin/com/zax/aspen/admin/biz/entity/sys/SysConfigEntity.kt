package com.zax.aspen.admin.biz.entity.sys

import com.zax.aspen.common.database.model.MutableAuditEntity
import com.zax.aspen.common.database.model.TenantScopedEntity
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Table

/**
 * 保存租户级运行参数键值
 *
 * 典型场景: 业务开关、阈值和功能配置的运行期读取, 例如注册开关、密码有效期天数;
 * 高频读取的参数必须走缓存, 本表是权威数据源; 中间件与运行时配置仍归 Nacos, 不入本表
 */
@Entity
@Table(name = "sys_config")
interface SysConfigEntity : TenantScopedEntity, MutableAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val configId: Long

    /** 参数键, 业务代码以其读取参数, 例如 security.password.max-retry; 租户内唯一, 创建后不可修改 */
    val configKey: String

    /** 参数显示名, 用于管理界面展示与搜索, 例如「密码最大重试次数」 */
    val configName: String

    /** 参数值, 统一以字符串保存; 读取时由 Service 按 valueType 解析为对应类型并校验 */
    val configValue: String

    /** 参数值类型, 约定取值为 string/number/boolean/json; 写入时拒绝与声明类型不匹配的值 */
    @Default("string")
    val valueType: String

    /** 标记系统内置参数; 内置参数禁止业务删除, 只允许调整值, 防止系统运行依赖的配置丢失 */
    @Default("false")
    val isBuiltIn: Boolean

    /** 标记敏感参数; 其值不得进入日志、导出文件和未脱敏的接口响应, 读取接口必须按此标记脱敏 */
    @Default("false")
    val isSensitive: Boolean

    /** 参数启停状态; disabled 后读取按默认值兜底, 不返回已禁用参数值 */
    @Default("enabled")
    val status: String
}
