package com.zax.aspen.storage.api.enums

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.core.enums.EnumColor

/**
 * 分片上传任务状态
 *
 * 描述 storage_upload_task 的生命周期: uploading → merging → completed 主线,
 * 任意阶段可转 failed/canceled; 状态迁移由任务表乐观锁保护, 客户端轮询任务状态
 * 与已收分片号列表实现断点续传
 */
enum class UploadTaskStatus(
    override val code: String,
    override val description: String,
    override val color: String?,
) : AspenEnum {
    /** 上传中, 分片未收齐, 接受新分片写入与续传查询 */
    UPLOADING("uploading", "上传中", EnumColor.PRIMARY),

    /** 合并中, 分片已收齐, 服务端正在拼接并写入目标后端, 禁止重复触发合并 */
    MERGING("merging", "合并中", EnumColor.WARNING),

    /** 已完成, 文件记录已生成, 任务过期时间已清除, 不再接受任何分片 */
    COMPLETED("completed", "已完成", EnumColor.SUCCESS),

    /** 已失败, 合并校验或后端写入失败后的终态, 客户端可重建任务重试 */
    FAILED("failed", "已失败", EnumColor.DANGER),

    /** 已取消, 客户端主动放弃后的终态, 任务等待过期清理回收 */
    CANCELED("canceled", "已取消", EnumColor.DEFAULT),
}
