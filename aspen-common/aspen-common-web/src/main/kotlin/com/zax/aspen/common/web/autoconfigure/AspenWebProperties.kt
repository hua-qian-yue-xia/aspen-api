package com.zax.aspen.common.web.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 绑定 Aspen Web 受众路径前缀公共配置
 *
 * 管理端、用户端与设备端各一组「前缀 + Controller 包匹配规则」, 由
 * [AspenWebAutoConfiguration] 在 MVC 路径映射注册期按 Controller 类的包名追加前缀;
 * 前缀同时是网关路由断言与 RBAC `upm_permission_api.application` 的受众标识锚点,
 * 部署侧可整体改写前缀而不改代码; 默认值即平台约定, 零配置接入即生效
 */
@ConfigurationProperties("aspen.web")
class AspenWebProperties {
    /** 管理端受众配置, 默认前缀 /admin-api 匹配 controller.admin 包下的 Controller */
    var adminApi: Api = Api()

    /** 用户端受众配置, 默认前缀 /app-api 匹配 controller.app 包下的 Controller */
    var appApi: Api = Api().apply {
        prefix = "/app-api"
        controller = "**.controller.app.**"
    }

    /** 设备端受众配置, 默认前缀 /device-api 匹配 controller.device 包下的 Controller */
    var deviceApi: Api = Api().apply {
        prefix = "/device-api"
        controller = "**.controller.device.**"
    }

    /**
     * 单个受众的前缀与包匹配规则
     *
     * 三组规则的匹配对象是 Controller 类的包名而非 URL, 必须互不相交;
     * 命中受众规则的 `@RestController` 的全部映射携带该受众前缀,
     * 其余包 (如 internal) 不命中任何规则、不加前缀
     */
    class Api {
        /** 该受众全部接口统一携带的路径前缀, 以 / 开头 */
        var prefix: String = "/admin-api"

        /** Controller 类所在包的 Ant 匹配规则, 分隔符为点号, 如 `**.controller.admin.**` */
        var controller: String = "**.controller.admin.**"
    }
}
