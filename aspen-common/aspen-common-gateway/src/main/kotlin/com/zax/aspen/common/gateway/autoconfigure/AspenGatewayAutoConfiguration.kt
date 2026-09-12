package com.zax.aspen.common.gateway.autoconfigure

import com.zax.aspen.common.cache.autoconfigure.AspenCacheAutoConfiguration
import com.zax.aspen.common.cache.support.AspenRedisOperations
import com.zax.aspen.common.gateway.GatewayRouteProperties
import com.zax.aspen.common.gateway.consume.RouteSnapshotStore
import com.zax.aspen.common.gateway.publish.RouteEnvelopePublisher
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import tools.jackson.databind.ObjectMapper
import java.time.Clock

/**
 * 注册路由分发的共享配置与收发设施
 *
 * Admin 与 Gateway 各自引入本模块即获得同一套 environment 配置、同一组介质
 * Key 约定与发布/消费原语; ObjectMapper 与 AspenRedisOperations 由服务容器提供。
 * 装配条件对齐 common-cache 模式: after 固定在 AspenCacheAutoConfiguration 之后
 * 求值, 且只有容器真实产出 AspenRedisOperations (Redis 连接可用且
 * aspen.cache.enabled 未关闭) 时才注册任何 Bean; 无 Redis 的服务静默退避,
 * 不因类路径存在而以 Bean 缺失异常崩溃
 */
@AutoConfiguration(after = [AspenCacheAutoConfiguration::class])
@ConditionalOnClass(AspenRedisOperations::class)
@ConditionalOnBean(AspenRedisOperations::class)
@EnableConfigurationProperties(GatewayRouteProperties::class)
class AspenGatewayAutoConfiguration {
    /**
     * 装配路由快照加载与持有的消费端设施
     *
     * @param aspenRedisOperations common-cache 提供的 Redis 分发原语
     * @param objectMapper 服务容器的 Jackson 3 mapper
     * @param properties 路由分发配置
     * @return 从 Redis 加载并持有内存快照的存储
     */
    @Bean("aspenRouteSnapshotStore")
    @ConditionalOnMissingBean(RouteSnapshotStore::class)
    fun aspenRouteSnapshotStore(
        aspenRedisOperations: AspenRedisOperations,
        objectMapper: ObjectMapper,
        properties: GatewayRouteProperties,
    ): RouteSnapshotStore = RouteSnapshotStore(aspenRedisOperations, objectMapper, properties)

    /**
     * 装配路由信封发布原语
     *
     * 发布时刻优先复用容器既有 Clock (如 common-database 的审计时钟或业务自定义
     * 时钟), 容器未声明时回退部署域默认时区; 本模块不再声明兜底 Clock Bean,
     * 避免与 common-database 的 @ConditionalOnMissingBean 时钟在自动配置排序
     * 变化时静默竞争导致时区语义翻转
     *
     * @param aspenRedisOperations common-cache 提供的 Redis 分发原语
     * @param objectMapper 服务容器的 Jackson 3 mapper
     * @param properties 路由分发配置
     * @param clockProvider 容器 Clock 的惰性解析入口, 缺省时回退系统默认时区
     * @return 把快照列表发布到 Redis 介质的发布器
     */
    @Bean("aspenRouteEnvelopePublisher")
    @ConditionalOnMissingBean(RouteEnvelopePublisher::class)
    fun aspenRouteEnvelopePublisher(
        aspenRedisOperations: AspenRedisOperations,
        objectMapper: ObjectMapper,
        properties: GatewayRouteProperties,
        clockProvider: ObjectProvider<Clock>,
    ): RouteEnvelopePublisher = RouteEnvelopePublisher(
        aspenRedisOperations = aspenRedisOperations,
        objectMapper = objectMapper,
        properties = properties,
        clock = clockProvider.getIfAvailable(Clock::systemDefaultZone),
    )
}
