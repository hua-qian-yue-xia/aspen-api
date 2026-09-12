package com.zax.aspen.auth.api.dto.auth

/**
 * 第三方身份解析的状态分类
 *
 * 用户域 resolveByIdentity 的判定结果: 建号以身份唯一键幂等, 并发重复请求
 * 解析到同一主体, 不产生重复账号
 */
enum class IdentityResolveStatus {
    /** 身份已绑定本域主体, principal 携带该主体 */
    FOUND,

    /** 身份此前不存在, 本次按策略幂等建号, principal 携带新主体 */
    CREATED,

    /** 身份不存在且策略不允许建号 (allowCreate=false), 管理端第三方登录的固定形态 */
    NOT_FOUND
}
