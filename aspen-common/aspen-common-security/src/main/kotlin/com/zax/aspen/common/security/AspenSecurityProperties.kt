package com.zax.aspen.common.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 安全模块公共配置
 *
 * 业务进程最小信任链的开关与期望信任凭据; 信任凭据只从环境变量
 * ASPEN_GATEWAY_INTERNAL_SECRET 经部署注入, 不得写入配置中心或仓库 (技术架构
 * 14.2 密钥边界); 曾有的分发介质 environment 段已随端配置回归 Auth 本库直读
 * (2026-09-13 归属修订) 删除
 */
@ConfigurationProperties(prefix = "aspen.security")
class AspenSecurityProperties(
    /** 最小信任链开关; 关闭时不注册 InternalTrustFilter 与 TenantContextSupplier, 供本地无网关联调降级 */
    var enabled: Boolean = true,
    /** 期望的网关信任凭据, null/空白时身份头请求一律拒绝 (fail-closed) */
    var trustToken: String? = null,
)
