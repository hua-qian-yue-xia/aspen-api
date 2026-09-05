package com.zax.aspen.common.core.gen

/**
 * 声明枚举的内置字典镜像, 由 common-gen 扫描并交由 Admin 播种进 sys_dict 与 sys_dict_item
 *
 * 典型场景: 前端下拉渲染与值翻译需要枚举的展示数据时, 在 `AspenEnum` 枚举上声明本注解,
 * 免去人工在管理界面逐条录入; 播种只建立展示镜像, 枚举仍是唯一权威取值来源,
 * 字典项禁止运营增删值, 只允许调整展示属性
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class GenDict(
    /** 字典编码, 对应 sys_dict.dict_code; 全局唯一且创建后不可修改, 小写下划线格式 */
    val code: String,
    /** 字典显示名, 对应 sys_dict.dict_name, 用于管理界面展示与搜索, 例如「用户状态」 */
    val name: String,
    /** 字典分组, 对应 sys_dict.dict_group, 供管理界面按域筛选, 例如 common/upm, 小写下划线格式 */
    val group: String,
)
