package com.zax.aspen.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 网关安全链配置
 *
 * 公开路径是白名单语义: 登录/刷新与健康检查无需令牌直进, 其余全部要求认证
 * (JWT 验签); mode 提供 permit-all 逃生口仅供本地无 Auth 联调, 生产禁止
 */
@ConfigurationProperties(prefix = "aspen.gateway.security")
class GatewaySecurityProperties(
    /** 安全模式: enforce 全量校验 (默认), permit-all 仅限本地联调逃生 */
    var mode: Mode = Mode.ENFORCE,
    /** 公开路径白名单 (Ant 语义), 登录/刷新与健康检查 */
    var publicPaths: List<String> = DEFAULT_PUBLIC_PATHS,
) {
    /** 安全模式取值 */
    enum class Mode {
        /** 全量校验: 公开路径放行, 其余要求 JWT 认证 */
        ENFORCE,

        /** 全部放行: 本地无 Auth 联调逃生口, 生产禁用 */
        PERMIT_ALL,
    }

    private companion object {
        /** 默认公开路径: 管理端/app 端登录与刷新, 健康检查 */
        val DEFAULT_PUBLIC_PATHS = listOf(
            "/admin-api/auth/login",
            "/admin-api/auth/refresh",
            "/app-api/auth/login",
            "/app-api/auth/refresh",
            "/actuator/health",
        )
    }
}
