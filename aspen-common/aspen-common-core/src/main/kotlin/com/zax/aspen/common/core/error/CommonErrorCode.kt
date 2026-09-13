package com.zax.aspen.common.core.error

/**
 * 定义真正跨服务使用的公共错误, 业务错误仍归所属服务 API 模块
 */
enum class CommonErrorCode(
    /** 稳定的公共机器错误码 */
    override val code: String,
    /** 可以安全返回给调用方的默认消息 */
    override val defaultMessage: String,
) : ErrorCode {
    /** 请求参数不符合协议约束 */
    INVALID_ARGUMENT("COMMON.INVALID_ARGUMENT", "请求参数不正确"),

    /** 请求方法不被目标接口支持 */
    METHOD_NOT_ALLOWED("COMMON.METHOD_NOT_ALLOWED", "请求方法不支持"),

    /** 请求实体的媒体类型不被目标接口支持 */
    UNSUPPORTED_MEDIA_TYPE("COMMON.UNSUPPORTED_MEDIA_TYPE", "请求的媒体类型不支持"),

    /** 请求的资源不存在 */
    RESOURCE_NOT_FOUND("COMMON.RESOURCE_NOT_FOUND", "请求的资源不存在"),

    /** 未认证或凭据失效: 登录失败、令牌缺失/过期/被吊销的统一 401 语义, 不携带更多细节 */
    UNAUTHORIZED("COMMON.UNAUTHORIZED", "未认证或凭据已失效"),

    /** 已认证但无权限访问目标资源, 路由级与资源级越权的统一 403 语义 */
    FORBIDDEN("COMMON.FORBIDDEN", "无权访问目标资源"),

    /** 当前资源状态不允许执行目标操作 */
    STATE_CONFLICT("COMMON.STATE_CONFLICT", "资源状态不允许执行当前操作"),

    /** 完成请求所需的下游依赖不可用 */
    DEPENDENCY_UNAVAILABLE("COMMON.DEPENDENCY_UNAVAILABLE", "依赖服务暂时不可用"),

    /** 未分类的服务内部错误 */
    INTERNAL_ERROR("COMMON.INTERNAL_ERROR", "服务内部错误"),
}
