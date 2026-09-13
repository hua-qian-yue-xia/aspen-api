package com.zax.aspen.common.route

/**
 * 操作日志的业务分类标签
 *
 * 由 common-web 的操作日志拦截器消费, 作为 `aspen.operation` 日志行的 tag 字段;
 * 它是操作审计的检索维度, 与数据库实体的审计原子 (CreateAudit 等) 正交
 */
enum class OperationTag {
    /** 常规读写之外无法归类的操作 */
    OTHER,

    /** 新增类操作 */
    INSERT,

    /** 修改类操作 */
    UPDATE,

    /** 删除类操作 */
    DELETE,

    /** 授权类操作 (角色、权限分配与回收) */
    GRANT,

    /** 导出类操作 */
    EXPORT,

    /** 导入类操作 */
    IMPORT,

    /** 生成类操作 (代码生成、报告生成等) */
    GENERATE,

    /** 管理面高危操作 (配置变更、租户管理等) */
    ADMIN,

    /** 登录与认证相关操作 */
    LOGIN,
}
