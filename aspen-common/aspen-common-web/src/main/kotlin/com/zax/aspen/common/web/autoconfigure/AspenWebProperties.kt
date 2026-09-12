package com.zax.aspen.common.web.autoconfigure

import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 绑定 Aspen Web 受众路径前缀公共配置
 *
 * 管理端、用户端与设备端各一组「前缀 + Controller 包匹配规则」, 由
 * [AspenWebAutoConfiguration] 在 MVC 路径映射注册期按 Controller 类的包名追加前缀;
 * 前缀同时是网关路由断言与 RBAC `upm_permission_api.application` 的受众标识锚点,
 * 部署侧可整体改写前缀而不改代码; 默认值即平台约定, 零配置接入即生效;
 * 非法配置 (前缀格式错误、三组受众前缀或包规则重复) 在绑定完成后启动失败
 */
@ConfigurationProperties("aspen.web")
class AspenWebProperties : InitializingBean {
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
     * 绑定完成后校验配置合法性
     *
     * 前缀必须以 `/` 开头、不以 `/` 结尾且不含空白 (它是网关断言与 RBAC 的锚点,
     * 配错即整条链路 404, 必须启动失败而非静默错配); 三组受众的前缀与包规则
     * 各自互不重复——Ant 规则的完全不相交无法静态证明, 重复检查兜住复制粘贴
     * 错配, 完全不相交由默认约定与部署侧自查保证
     */
    override fun afterPropertiesSet() {
        val audiences = listOf(
            "aspen.web.admin-api" to adminApi,
            "aspen.web.app-api" to appApi,
            "aspen.web.device-api" to deviceApi,
        )
        for ((name, api) in audiences) {
            require(api.prefix.length > 1 && api.prefix.startsWith("/") && !api.prefix.endsWith("/")) {
                "$name.prefix 必须以 / 开头且不以 / 结尾: '${api.prefix}'"
            }
            require(api.prefix.none { it.isWhitespace() }) {
                "$name.prefix 不能包含空白字符: '${api.prefix}'"
            }
        }
        val duplicatePrefixes = audiences
            .groupBy { it.second.prefix }
            .filterValues { it.size > 1 }
            .keys
        require(duplicatePrefixes.isEmpty()) {
            "三组受众的前缀必须互不相同: $duplicatePrefixes"
        }
        val duplicateControllers = audiences
            .groupBy { it.second.controller }
            .filterValues { it.size > 1 }
            .keys
        require(duplicateControllers.isEmpty()) {
            "三组受众的 Controller 包规则必须互不相同: $duplicateControllers"
        }
    }

    /**
     * 单个受众的前缀与包匹配规则
     *
     * 三组规则的匹配对象是 Controller 类的包名而非 URL, 必须互不相交;
     * 命中受众规则的 `@RestController` 的全部映射携带该受众前缀,
     * 其余包 (如 internal) 不命中任何规则、不加前缀
     */
    class Api {
        /** 该受众全部接口统一携带的路径前缀, 以 / 开头且不以 / 结尾 */
        var prefix: String = "/admin-api"

        /** Controller 类所在包的 Ant 匹配规则, 分隔符为点号, 如 `**.controller.admin.**` */
        var controller: String = "**.controller.admin.**"
    }
}
