package com.zax.aspen.storage.api.dto

/**
 * 存储后端连接参数的扁平契约
 *
 * 作为 storage_config.params JSON 列的类型化映射, 字段集合是全部存储类型参数的并集,
 * JSON 内不携带任何类型判别字段, 解释权归 storage_config.storage_type 列——
 * 这是相对芋道 @class 多态 TypeHandler 的刻意简化: 反序列化面收敛为纯数据结构,
 * 必填组合由 Service 按 StorageType 校验 (local 必填 pathBase;
 * 对象存储必填 endpoint/bucket/accessKey/accessSecret, 七牛另强制 domain);
 * accessSecret 是敏感字段, 出参 VO 必须脱敏, 修改配置时未传则不改
 */
data class StorageConfigParams(
    /** 本地存储根目录绝对路径, 仅 storage_type=local 使用, 对象存储忽略 */
    val pathBase: String? = null,

    /** 对象存储服务端点, 如 https://oss-cn-hangzhou.aliyuncs.com, 仅对象存储使用 */
    val endpoint: String? = null,

    /** 对象存储地域, 部分厂商 (如阿里云) 虚拟主机寻址需要, 可空时由 SDK 自行推断 */
    val region: String? = null,

    /** 对象存储桶名, 仅对象存储使用 */
    val bucket: String? = null,

    /** 访问密钥标识, 仅对象存储使用; 出参可明文, 但修改时未传则不改 */
    val accessKey: String? = null,

    /** 访问密钥私钥, 仅对象存储使用; 敏感字段, 出参必须脱敏, 禁止进入日志 */
    val accessSecret: String? = null,

    /** 自定义访问域名 (含协议), 生成文件 URL 时优先于 endpoint 拼接; 七牛必填 */
    val domain: String? = null,

    /** 是否 path-style 寻址 (https://endpoint/bucket/key); MinIO 默认 true, 云厂商默认 false */
    val pathStyleAccess: Boolean? = null,
)
