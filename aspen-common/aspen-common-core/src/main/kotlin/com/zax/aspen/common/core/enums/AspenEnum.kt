package com.zax.aspen.common.core.enums

/** 所有业务枚举的统一契约 */
interface AspenEnum {
    /**
     * 持久化存储值
     *
     * 约定: 小写字符串, 与数据库既有列值一致; 发布后属于稳定契约,
     * 禁止修改既有值或删除枚举项, 只能新增; 同一枚举内不允许重复
     */
    val code: String

    /**
     * 默认中文描述
     *
     * 直接用于界面展示、日志与错误消息, 随代码发布; 需要运营定制文案时由 sys_dict 覆盖展示,
     * 字典只能覆盖描述, 不改变 code 语义; 同一个值域只能以枚举或字典之一作为权威来源
     */
    val description: String

    /**
     * 前端标签颜色
     *
     * 取值为 EnumColor 调色板令牌(如 success、blue、magenta)或 #RRGGBB 十六进制色值,
     * 与 sys_dict_item.color 共用同一套约定; 无着色需求时为 null; 只影响展示, 不参与业务判断
     */
    val color: String?
}
