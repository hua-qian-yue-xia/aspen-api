package com.zax.aspen.storage.api.enums

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.core.enums.EnumColor

/**
 * 文件技术形态
 *
 * 由服务端按 MIME 类型推导并落 storage_file.file_type, 描述文件的技术形态
 * (图片、安装包、文档等), 与运营自定义的业务分类 (storage_category, 可选挂载)
 * 是两个正交概念; 只影响展示与检索, 不参与存储路由判断;
 * 取值不足时允许新增枚举项, 不允许改名
 */
enum class FileType(
    override val code: String,
    override val description: String,
    override val color: String?,
) : AspenEnum {
    /** 图片, image 主类型与常见位图、矢量格式 */
    IMAGE("image", "图片", EnumColor.BLUE),

    /** 视频, video 主类型与常见容器格式 */
    VIDEO("video", "视频", EnumColor.VOLCANO),

    /** 音频, audio 主类型与常见音频编码格式 */
    AUDIO("audio", "音频", EnumColor.CYAN),

    /** 文档, office、PDF、文本等文档格式 */
    DOCUMENT("document", "文档", EnumColor.GEEKBLUE),

    /** 安装包, Android apk 与后续移动端安装包格式 */
    APK("apk", "安装包", EnumColor.GREEN),

    /** 压缩包, zip、tar、7z 等归档格式 */
    ARCHIVE("archive", "压缩包", EnumColor.ORANGE),

    /** 其他, 无法归入以上类别的文件 */
    OTHER("other", "其他", EnumColor.DEFAULT),
}
