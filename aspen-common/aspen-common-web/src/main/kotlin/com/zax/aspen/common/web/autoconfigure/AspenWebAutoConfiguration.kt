package com.zax.aspen.common.web.autoconfigure

import com.zax.aspen.common.web.error.AspenErrorCodeStatusMapper
import com.zax.aspen.common.web.error.AspenWebExceptionHandler
import com.zax.aspen.common.web.route.FixedWindowRateLimiter
import com.zax.aspen.common.web.route.OperationLogInterceptor
import com.zax.aspen.common.web.route.RateLimitInterceptor
import com.zax.aspen.common.web.route.RouteOperationCustomizer
import com.zax.aspen.common.web.route.RouteSpecResolver
import com.zax.aspen.common.web.route.RouteSubjectResolver
import com.zax.aspen.common.web.trace.AspenTraceIdFilter
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.util.AntPathMatcher
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 注册受众路径前缀、统一错误契约、Trace ID 与路由套件消费端的 MVC 运行约定
 *
 * 任何承载 MVC Controller 的 biz 引入本模块, 其 `controller/admin|app|device` 包下的
 * `@RestController` 映射即在注册期统一携带 `/admin-api`、`/app-api`、`/device-api`
 * 前缀 (规则经 [AspenWebProperties] 可配置), Controller 与 api 契约接口自身无感;
 * `internal` 及其余包不命中规则、不加前缀, internal 端点因此天然不经网关暴露。
 * 同时装配 Trace ID 过滤器 (请求级排查标识, 关联日志与响应头) 与统一异常渲染
 * (`BusinessException` 等 → RFC 9457 Problem Details, 见《Common 模块设计》§8),
 * 以及路由套件消费端 (§8.3): 路由声明解析器、操作日志与限流双拦截器 (common-security
 * 在类路径时附带给它请求级身份的主体解析器)、springdoc 文档定制。
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

    /**
     * 注册路由声明解析器
     *
     * 业务声明同类型 Bean 即覆盖
     *
     * @return HandlerMethod 到路由套件声明的带缓存解析器
     */
    @Bean("aspenRouteSpecResolver")
    @ConditionalOnMissingBean(RouteSpecResolver::class)
    fun aspenRouteSpecResolver(): RouteSpecResolver = RouteSpecResolver()

    /**
     * 注册进程内固定窗口限流器
     *
     * 业务声明同类型 Bean (如注入自定义 Clock) 即覆盖
     *
     * @return 本地限流计数器
     */
    @Bean("aspenFixedWindowRateLimiter")
    @ConditionalOnMissingBean(FixedWindowRateLimiter::class)
    fun aspenFixedWindowRateLimiter(): FixedWindowRateLimiter = FixedWindowRateLimiter()

    /**
     * 注册请求主体解析器
     *
     * 以 name 字符串声明条件, 避免 common-security 不在类路径时加载类字面量;
     * 无 security 的服务 (如 task-biz) 不装配本 Bean, 限流主体回退 XFF/IP
     *
     * @return 信任链身份优先的限流主体解析器
     */
    @Bean("aspenRouteSubjectResolver")
    @ConditionalOnMissingBean(RouteSubjectResolver::class)
    @ConditionalOnClass(name = ["com.zax.aspen.common.security.trust.RequestIdentityContext"])
    fun aspenRouteSubjectResolver(): RouteSubjectResolver = RouteSubjectResolver()

    /**
     * 注册操作日志拦截器
     *
     * 经 ObjectProvider 弱引用主体解析器, security 缺席时按 unknown 主体记录
     *
     * @param routeSpecResolver 路由声明解析器
     * @param subjectResolver 主体解析器提供者, 条件装配可能缺席
     * @return 输出 aspen.operation 日志行的拦截器
     */
    @Bean("aspenOperationLogInterceptor")
    @ConditionalOnMissingBean(OperationLogInterceptor::class)
    fun aspenOperationLogInterceptor(
        routeSpecResolver: RouteSpecResolver,
        subjectResolver: ObjectProvider<RouteSubjectResolver>,
    ): OperationLogInterceptor =
        OperationLogInterceptor(routeSpecResolver, subjectResolver.ifAvailable)

    /**
     * 注册限流拦截器
     *
     * 经 ObjectProvider 弱引用主体解析器, security 缺席时 USER 作用域按匿名共享桶
     *
     * @param routeSpecResolver 路由声明解析器
     * @param rateLimiter 进程内固定窗口限流器
     * @param subjectResolver 主体解析器提供者, 条件装配可能缺席
     * @return 超限抛 429 业务异常的限流拦截器
     */
    @Bean("aspenRateLimitInterceptor")
    @ConditionalOnMissingBean(RateLimitInterceptor::class)
    fun aspenRateLimitInterceptor(
        routeSpecResolver: RouteSpecResolver,
        rateLimiter: FixedWindowRateLimiter,
        subjectResolver: ObjectProvider<RouteSubjectResolver>,
    ): RateLimitInterceptor =
        RateLimitInterceptor(routeSpecResolver, rateLimiter, subjectResolver.ifAvailable)

    /**
     * 注册 springdoc 文档定制
     *
     * springdoc 被exclude 时静默退避, 以 name 字符串声明条件避免加载类字面量
     *
     * @param routeSpecResolver 路由声明解析器
     * @return 把路由注解 summary/description 写入 OpenAPI 的定制器
     */
    @Bean("aspenRouteOperationCustomizer")
    @ConditionalOnMissingBean(RouteOperationCustomizer::class)
    @ConditionalOnClass(name = ["org.springdoc.core.customizers.GlobalOperationCustomizer"])
    fun aspenRouteOperationCustomizer(routeSpecResolver: RouteSpecResolver): RouteOperationCustomizer =
        RouteOperationCustomizer(routeSpecResolver)

    /**
     * 注册路由套件双拦截器
     *
     * 操作日志在前 (其 afterCompletion 覆盖被限流拒绝的请求), 限流在后; 拦截器经
     * @Bean 方法参数注入而非自动配置类内引用, 规避 proxyBeanMethods=false 下
     * 方法互调产生游离实例导致限流计数器状态分裂
     *
     * @param operationLogInterceptor 操作日志拦截器
     * @param rateLimitInterceptor 限流拦截器
     * @return 注册双拦截器的 MVC 配置器
     */
    @Bean("aspenRouteInterceptorRegistration")
    fun aspenRouteInterceptorRegistration(
        operationLogInterceptor: OperationLogInterceptor,
        rateLimitInterceptor: RateLimitInterceptor,
    ): WebMvcConfigurer = object : WebMvcConfigurer {
        override fun addInterceptors(registry: InterceptorRegistry) {
            registry.addInterceptor(operationLogInterceptor)
            registry.addInterceptor(rateLimitInterceptor)
        }
    }
}
