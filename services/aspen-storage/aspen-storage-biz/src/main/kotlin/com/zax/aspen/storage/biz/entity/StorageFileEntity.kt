package com.zax.aspen.storage.biz.entity

import com.zax.aspen.common.database.model.MutableAuditEntity
import com.zax.aspen.common.database.model.TenantScopedEntity
import com.zax.aspen.storage.api.enums.FileType
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.IdView
import org.babyfish.jimmer.sql.JoinColumn
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.Table

/**
 * 保存已落库的文件记录, 是文件元数据与秒传命中的权威源
 *
 * 典型场景: 直传或分片合并完成后写入一行; 客户端上传前以 (租户, sha256, 配置)
 * 查活跃行, 命中即秒传返回既有 fileId, 零字节传输; 唯一键把活跃行 (deleted_at
 * 为 NULL 经 IFNULL 折算哨兵) 纳入数据库约束, 并发同传同内容由后写方转秒传命中;
 * 记录逻辑删除只隐藏元数据, 物理对象按同 key 活跃记录计数由后续清理任务回收
 */
@Entity
@Table(name = "storage_file")
interface StorageFileEntity : TenantScopedEntity, MutableAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val fileId: Long

    /** 落库目标配置; 文件归属决定 URL 生成与后端读取实现 */
    @ManyToOne
    @JoinColumn(name = "config_id", referencedColumnName = "config_id")
    val config: StorageConfigEntity

    /** config 的同名列标量视图, 秒传命中与列表查询直接用 id, 不触发对象关联加载 */
    @IdView("config")
    val configId: Long

    /** 业务分类, 可空表示未分类; 挂载到分类树任意层级节点, 删除分类需先确认无活跃文件挂载 */
    @ManyToOne
    @JoinColumn(name = "category_id", referencedColumnName = "category_id")
    val category: StorageCategoryEntity?

    /** category 的同名列标量视图, 分类筛选与统计直接用 id, 不触发对象关联加载 */
    @IdView("category")
    val categoryId: Long?

    /** 客户端原始文件名, 仅作展示与下载回填 Content-Disposition, 不参与存储定位 */
    val originalName: String

    /** 对象 key, 内容寻址为 {sha256}.{原始扩展名}; 同内容在同一配置下覆盖同 key, 跨租户共享物理对象 */
    val storageKey: String

    /** 访问 URL 冗余缓存; 正典引用是 fileId 与 storageKey, domain 变更后按配置批量重算, 不因配置改动断链 */
    val url: String

    /** MIME 类型, 服务端经内容探测 (Tika) 识别, 不信任客户端声明 */
    val mimeType: String

    /** 文件字节数, 分片合并场景与 totalSize 一致, 直传场景以实测为准 */
    val fileSize: Long

    /** 整文件 SHA-256 十六进制值 (64 字符); 与 configId、tenantId 共同构成秒传命中键 */
    val sha256: String

    /** 技术形态, 服务端按 MIME 推导 (图片/安装包/文档等), 与运营自定义的业务分类正交; 供管理端筛选与统计, 不参与存储路由判断 */
    val fileType: FileType
}
