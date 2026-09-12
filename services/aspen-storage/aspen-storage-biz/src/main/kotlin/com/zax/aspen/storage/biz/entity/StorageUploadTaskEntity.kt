package com.zax.aspen.storage.biz.entity

import com.zax.aspen.common.database.model.AuditableEntity
import com.zax.aspen.common.database.model.TenantScopedEntity
import com.zax.aspen.common.database.model.VersionedEntity
import com.zax.aspen.storage.api.enums.UploadTaskStatus
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
 * 保存分片上传任务, 是断点续传与过期清理的过程数据载体
 *
 * 典型场景: 客户端初始化任务 (固化总量/分片规格/整文件哈希并顺带秒传检查) →
 * 逐片上传并以 (任务, 分片号) 唯一键幂等补传 → 全部到齐后合并校验落库文件记录并置
 * 完成态; 任务可重建 (重试、续传超期重建都是新行); 过期未完成任务连同分片由清理
 * 任务物理删除, 因此不声明删除审计, 防止行膨胀
 */
@Entity
@Table(name = "storage_upload_task")
interface StorageUploadTaskEntity : TenantScopedEntity, AuditableEntity, VersionedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val uploadTaskId: Long

    /** 落库目标配置; 初始化时随调用方 configCode 或默认配置解析固化, 之后不随配置变更迁移 */
    @ManyToOne
    @JoinColumn(name = "config_id", referencedColumnName = "config_id")
    val config: StorageConfigEntity

    /** config 的同名列标量视图, 过期清理与续传查询直接用 id, 不触发对象关联加载 */
    @IdView("config")
    val configId: Long

    /** 客户端原始文件名, 合并落库时透传给文件记录的 originalName */
    val originalName: String

    /** 客户端声明的 MIME 类型, 可空; 权威值由合并时的内容探测结果覆盖进文件记录 */
    val mimeType: String?

    /** 整文件字节数, 初始化时声明; 与分片规格一起校验「除末片外每片等于 chunkSize、末片不大于 chunkSize」 */
    val totalSize: Long

    /** 分片字节数, 初始化时固化; 全程不变, 续传客户端据此切割 */
    val chunkSize: Int

    /** 分片总数, 由 totalSize 与 chunkSize 推导固化; 分片号从 1 起连续编号到此值 */
    val totalChunks: Int

    /** 整文件 SHA-256 十六进制值; 初始化时作秒传预检键, 合并后作拼接完整性校验值 */
    val fileSha256: String

    /** 对象存储原生分片上传的远端 uploadId, 二期 S3 直传启用; V1 服务端中转方案恒为 NULL */
    val remoteUploadId: String?

    /** 任务状态机; uploading → merging → completed 主线, 任意阶段可 failed/canceled, 迁移受乐观锁保护 */
    @Default("UPLOADING")
    val taskStatus: UploadTaskStatus

    /** 任务过期时间, 初始化时按策略写入 (默认未完成保留 24 小时); 合并完成置 NULL 表示不再过期 */
    val expiresAt: LocalDateTime?
}
