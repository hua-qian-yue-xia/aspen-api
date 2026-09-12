package com.zax.aspen.common.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 安全模块公共配置
 *
 * 承载两类取值: 分发介质的 environment 段 (Admin 发布侧与 Auth 消费侧必须一致,
 * 否则读写不同 Key 静默失联) 与业务进程最小信任链的开关及期望信任凭据;
 * 信任凭据只从环境变量 ASPEN_GATEWAY_INTERNAL_SECRET 经部署注入, 不得写入
 * 配置中心或仓库 (技术架构 14.2 密钥边界)
 */
@ConfigurationProperties(prefix = "aspen.security")
class AspenSecurityProperties(
    /** 分发介质的环境标识, 与 Gateway 路由分发的 environment 同语义, 如 local、prod */
    var environment: String = "local",
    /** 最小信任链开关; 关闭时不注册 InternalTrustFilter 与 TenantContextSupplier, 供本地无网关联调降级 */
    var enabled: Boolean = true,
    /** 期望的网关信任凭据, null/空白时身份头请求一律拒绝 (fail-closed) */
    var trustToken: String? = null,
)
