package com.zax.aspen.storage.api.enums

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.core.enums.EnumColor

/**
 * 存储后端类型
 *
 * 标识一条 storage_config 背后的存储实现族; minio/aliyun_oss/qiniu/s3 四类在协议层
 * 共享同一套 S3 兼容客户端 (endpoint + bucket + ak/sk), 枚举分开声明只为配置表单
 * 差异 (如七牛强制自定义 domain、MinIO 默认 path-style) 与后续按厂商扩展;
 * 只增不改, 新增后端类型以新枚举项接入
 */
enum class StorageType(
    override val code: String,
    override val description: String,
    override val color: String?,
) : AspenEnum {
    /** 本地磁盘存储, 分片临时目录与最终文件都落服务端文件系统 */
    LOCAL("local", "本地磁盘", EnumColor.DEFAULT),

    /** MinIO 或自建 MinIO 集群, S3 协议, 默认 path-style 访问 */
    MINIO("minio", "MinIO", EnumColor.CYAN),

    /** 阿里云 OSS, S3 协议, 默认虚拟主机风格访问 */
    ALIYUN_OSS("aliyun_oss", "阿里云 OSS", EnumColor.ORANGE),

    /** 七牛云 Kodo, S3 协议, 必须配置自定义 domain */
    QINIU("qiniu", "七牛云", EnumColor.GREEN),

    /** 其他 S3 兼容端点 (腾讯云 COS、华为云 OBS、火山云 TOS 等) */
    S3("s3", "S3 兼容存储", EnumColor.BLUE),
}
