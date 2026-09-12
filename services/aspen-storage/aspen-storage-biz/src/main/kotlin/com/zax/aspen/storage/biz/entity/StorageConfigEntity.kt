package com.zax.aspen.storage.biz.entity

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.database.model.MutableAuditEntity
import com.zax.aspen.storage.api.dto.StorageConfigParams
import com.zax.aspen.storage.api.enums.StorageType
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Serialized
import org.babyfish.jimmer.sql.Table

/**
 * 保存存储后端配置, 是多后端共存与路由的唯一权威源
 *
 * 典型场景: 本地目录、MinIO、阿里云 OSS、七牛云等配置各行共存, 调用方以 configCode
 * 指定目标后端, 未指定时落唯一启用中的默认配置; 配置是平台基础设施, 全体租户共用,
 * 不做租户隔离; 误删配置可凭逻辑删除行恢复, 恢复时由 Service 处理默认位唯一性
 */
@Entity
@Table(name = "storage_config")
interface StorageConfigEntity : MutableAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val configId: Long

    /** 配置编码, 调用方以其稳定引用目标后端 (如 apk 固定传 main-local); 小写中划线格式, 全局唯一, 创建后不可修改 */
    val configCode: String

    /** 配置显示名, 用于管理界面展示与搜索 */
    val configName: String

    /** 存储后端类型, 决定 params 的解释方式与客户端实现族; 值集合见 api 契约 StorageType */
    val storageType: StorageType

    /** 连接参数, 字段集合是全部存储类型的并集且不含类型判别字段, 必填组合由 Service 按 storageType 校验; accessSecret 敏感, 出参必须脱敏; 存取经 Jimmer @Serialized 与 JSON 列互转 */
    @Serialized
    val params: StorageConfigParams

    /** 是否默认后端; 同一时刻只应有一个启用中的默认配置, 由 Service 在事务内校验 (MySQL 无法以普通唯一约束表达条件唯一) */
    @Default("false")
    val isDefault: Boolean

    /** 配置启停状态; disabled 的配置不参与新上传, 既有文件读取不受影响 */
    @Default("ENABLED")
    val status: EnabledStatus
}
