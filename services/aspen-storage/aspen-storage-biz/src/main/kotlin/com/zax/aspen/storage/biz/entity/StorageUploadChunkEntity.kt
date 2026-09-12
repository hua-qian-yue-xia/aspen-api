package com.zax.aspen.storage.biz.entity

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
 * 保存已接收的分片记录, (任务, 分片号) 唯一键即断点续传的已收集合
 *
 * 典型场景: 客户端续传前按任务查询已收分片号列表, 只补缺失分片; 重复上传同号分片
 * 被唯一键拒绝后由 Service 按幂等覆盖处理; 任务清理或重建时随外键级联物理删除,
 * 分片是不可变行, 只建不更
 */
@Entity
@Table(name = "storage_upload_chunk")
interface StorageUploadChunkEntity : TenantScopedEntity, CreateAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val uploadChunkId: Long

    /** 所属上传任务; 任务物理删除时级联删除全部分片 */
    @ManyToOne
    @JoinColumn(name = "upload_task_id", referencedColumnName = "upload_task_id")
    val uploadTask: StorageUploadTaskEntity

    /** uploadTask 的同名列标量视图, 已收分片号查询与合并排序直接用 id, 不触发对象关联加载 */
    @IdView("uploadTask")
    val uploadTaskId: Long

    /** 分片序号, 从 1 起连续编号到任务 totalChunks; 与任务构成唯一键 */
    val chunkNumber: Int

    /** 分片实际字节数; 末片允许小于任务 chunkSize, 其余分片必须等于 chunkSize, 由 Service 校验 */
    val chunkSize: Int

    /** 分片 SHA-256 十六进制值, 客户端可选提供; 提供时服务端校验单片完整性, 不匹配拒绝该分片 */
    val chunkSha256: String?

    /** 对象存储原生分片上传返回的 part ETag, 合并时按分片号回填提交; V1 服务端中转方案恒为 NULL */
    val remotePartTag: String?
}
