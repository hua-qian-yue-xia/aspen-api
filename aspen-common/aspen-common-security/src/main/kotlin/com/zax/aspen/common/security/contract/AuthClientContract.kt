package com.zax.aspen.common.security.contract

/**
 * 认证客户端配置分发介质的 Redis Key 与通知频道约定
 *
 * Admin 写入 (sys_auth_client + sys_auth_login_method 的启用快照)、Auth 读取共用
 * 本约定; Key 不走 common-cache 的 CacheKeyBuilder (其强制 TTL 与 service 段命名
 * 不适配权威引用数据), 由双方以相同 environment 配置对齐; environment 取部署环境
 * 标识 (如 local、prod), 格式为小写字母数字与中划线; 仅 auth 与 admin 的 biz
 * 消费本模块, 无 api 引用需求, 因此契约内聚在 SDK 模块而不拆纯契约模块
 */
object AuthClientContract {
    /** 全量客户端配置信封 Key, value 为 AuthClientCatalogSnapshot 的 JSON, 无 TTL */
    const val CLIENTS_KEY_TEMPLATE = "aspen:%s:auth:clients"

    /** 版本计数器 Key, Admin 每次发布前 INCR 取号 */
    const val VERSION_KEY_TEMPLATE = "aspen:%s:auth:clients:version"

    /** 刷新通知频道, 消息体为最新版本号, Auth 比对后决定是否重载 */
    const val REFRESH_CHANNEL_TEMPLATE = "aspen:%s:auth:clients:refresh"

    /**
     * 构建全量客户端配置信封 Key
     *
     * @param environment 部署环境标识, 如 local、prod, 小写字母数字与中划线格式
     * @return 填充 environment 后的完整 Redis Key
     */
    fun clientsKey(environment: String): String = buildKey(CLIENTS_KEY_TEMPLATE, environment)

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
            "认证配置分发的 environment 必须为小写字母数字与中划线格式: $environment"
        }
        return template.format(environment)
    }

    private val ENVIRONMENT_PATTERN = Regex("[a-z][a-z0-9-]*")
}
