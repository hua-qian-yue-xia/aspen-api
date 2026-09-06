package com.zax.aspen.admin.api.constant

/**
 * 路由分发介质的 Redis Key 与通知频道约定
 *
 * Admin 写入、Gateway 读取共用本约定; Key 不走 common-cache (其强制 TTL 与
 * service 段命名不适配权威引用数据), 由双方以相同 environment 配置对齐;
 * environment 取部署环境标识 (如 local、prod), 格式为小写字母数字与中划线
 */
object GatewayRouteContract {
    /** 全量路由信封 Key, value 为 RouteCatalogSnapshot 的 JSON, 无 TTL */
    const val ROUTES_KEY_TEMPLATE = "aspen:%s:gateway:routes"

    /** 版本计数器 Key, Admin 每次发布前 INCR 取号 */
    const val VERSION_KEY_TEMPLATE = "aspen:%s:gateway:routes:version"

    /** 刷新通知频道, 消息体为最新版本号, Gateway 比对后决定是否重载 */
    const val REFRESH_CHANNEL_TEMPLATE = "aspen:%s:gateway:routes:refresh"

    /**
     * 构建全量路由信封 Key
     *
     * @param environment 部署环境标识, 如 local、prod, 小写字母数字与中划线格式
     * @return 填充 environment 后的完整 Redis Key
     */
    fun routesKey(environment: String): String = buildKey(ROUTES_KEY_TEMPLATE, environment)

    /**
     * 构建版本计数器 Key
     *
     * @param environment 部署环境标识, 如 local、prod, 小写字母数字与中划线格式
     * @return 填充 environment 后的完整 Redis Key
     */
    fun versionKey(environment: String): String = buildKey(VERSION_KEY_TEMPLATE, environment)

    /**
     * 构建刷新通知频道名
     *
     * @param environment 部署环境标识, 如 local、prod, 小写字母数字与中划线格式
     * @return 填充 environment 后的完整频道名
     */
    fun refreshChannel(environment: String): String = buildKey(REFRESH_CHANNEL_TEMPLATE, environment)

    /**
     * 校验 environment 格式并填充到模板占位符
     *
     * @param template 含单个 %s 占位符的 Key 或频道名模板
     * @param environment 部署环境标识, 必须为小写字母数字与中划线格式且以小写字母开头
     * @return 填充 environment 后的完整 Key 或频道名
     */
    private fun buildKey(template: String, environment: String): String {
        require(environment.matches(ENVIRONMENT_PATTERN)) {
            "路由分发的 environment 必须为小写字母数字与中划线格式: $environment"
        }
        return template.format(environment)
    }

    private val ENVIRONMENT_PATTERN = Regex("[a-z][a-z0-9-]*")
}
