package com.zax.aspen.common.web.autoconfigure

import com.zax.aspen.common.web.error.AspenErrorCodeStatusMapper
import com.zax.aspen.common.web.error.AspenWebExceptionHandler
import com.zax.aspen.common.web.trace.AspenTraceIdFilter
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.util.AntPathMatcher
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 注册受众路径前缀、统一错误契约与 Trace ID 的 MVC 运行约定
 *
 * 任何承载 MVC Controller 的 biz 引入本模块, 其 `controller/admin|app|device` 包下的
 * `@RestController` 映射即在注册期统一携带 `/admin-api`、`/app-api`、`/device-api`
 * 前缀 (规则经 [AspenWebProperties] 可配置), Controller 与 api 契约接口自身无感;
 * `internal` 及其余包不命中规则、不加前缀, internal 端点因此天然不经网关暴露。
 * 同时装配 Trace ID 过滤器 (请求级排查标识, 关联日志与响应头) 与统一异常渲染
 * (`BusinessException` 等 → RFC 9457 Problem Details, 见《Common 模块设计》§8)。
 * 装配只在 Spring MVC 类路径存在时生效, WebFlux-only 服务引入本模块静默退避
 */
@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer::class)
@EnableConfigurationProperties(AspenWebProperties::class)
class AspenWebAutoConfiguration(
    private val properties: AspenWebProperties,
) : WebMvcConfigurer {

    /**
     * 按受众注册路径前缀
     *
     * 匹配对象是 Controller 类的包名 (Ant 规则、点号分隔) 而非 URL;
     * 三组受众规则互不相交, 注册顺序不影响结果, 固定按管理端在前保持确定性
     *
     * @param configurer MVC 路径匹配配置, 注册的前缀在映射注册期拼接到全部命中映射之前
     */
    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        // AntPathMatcher 以点号为分隔符, 用于匹配包名字符串而不是 URL 路径
        val packageMatcher = AntPathMatcher(".")
        for (api in listOf(properties.adminApi, properties.appApi, properties.deviceApi)) {
            configurer.addPathPrefix(api.prefix) { controller ->
                // package 是 Kotlin 硬关键字, 经 getPackageName() 的属性语法取包名
                controller.isAnnotationPresent(RestController::class.java) &&
                    packageMatcher.match(api.controller, controller.packageName)
            }
        }
    }

    /**
     * 注册 Trace ID 过滤器
     *
     * 注册序取最高优先级, 保证 MDC 先于一切业务 Filter 建立并覆盖完整请求周期;
     * 业务声明同名 Bean 即覆盖默认注册
     *
     * @return Trace ID 过滤器的注册件
     */
    @Bean("aspenTraceIdFilter")
    @ConditionalOnMissingBean(name = ["aspenTraceIdFilter"])
    fun aspenTraceIdFilter(): FilterRegistrationBean<AspenTraceIdFilter> =
        FilterRegistrationBean(AspenTraceIdFilter()).apply { order = Ordered.HIGHEST_PRECEDENCE }

    /**
     * 注册错误码到 HTTP 状态的映射
     *
     * 业务声明同类型 Bean 即覆盖默认映射
     *
     * @return 公共错误码精确映射、业务错误码默认 400 的映射器
     */
    @Bean("aspenErrorCodeStatusMapper")
    @ConditionalOnMissingBean(AspenErrorCodeStatusMapper::class)
    fun aspenErrorCodeStatusMapper(): AspenErrorCodeStatusMapper = AspenErrorCodeStatusMapper()

    /**
     * 注册统一异常渲染
     *
     * 业务声明同类型 Bean (或组件扫描直接拾取带 @RestControllerAdvice 的本类) 即覆盖,
     * 条件注解同时防止双注册
     *
     * @param statusMapper 错误码状态映射器
     * @return 渲染 RFC 9457 Problem Details 的全局异常处理器
     */
    @Bean("aspenWebExceptionHandler")
    @ConditionalOnMissingBean(AspenWebExceptionHandler::class)
    fun aspenWebExceptionHandler(statusMapper: AspenErrorCodeStatusMapper): AspenWebExceptionHandler =
        AspenWebExceptionHandler(statusMapper)
}
