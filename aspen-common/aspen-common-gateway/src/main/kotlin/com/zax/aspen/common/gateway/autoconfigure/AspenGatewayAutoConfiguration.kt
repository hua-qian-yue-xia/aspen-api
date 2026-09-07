package com.zax.aspen.common.gateway.autoconfigure

import com.zax.aspen.common.cache.support.AspenRedisOperations
import com.zax.aspen.common.gateway.GatewayRouteProperties
import com.zax.aspen.common.gateway.consume.RouteSnapshotStore
import com.zax.aspen.common.gateway.publish.RouteEnvelopePublisher
import org.springframework.boot.autoconfigure.AutoConfiguration
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
 * Key 约定与发布/消费原语; ObjectMapper 与 AspenRedisOperations 由服务容器提供
 */
@AutoConfiguration
@ConditionalOnClass(AspenRedisOperations::class)
@EnableConfigurationProperties(GatewayRouteProperties::class)
class AspenGatewayAutoConfiguration {
    /**
     * 提供默认部署域时钟, 供发布时刻使用, 业务服务可以声明 Clock Bean 覆盖
     *
     * @return 基于系统默认时区的时钟
     */
    @Bean("aspenGatewayClock")
    @ConditionalOnMissingBean(Clock::class)
    fun aspenGatewayClock(): Clock = Clock.systemDefaultZone()

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
     * @param aspenRedisOperations common-cache 提供的 Redis 分发原语
     * @param objectMapper 服务容器的 Jackson 3 mapper
     * @param properties 路由分发配置
     * @param clock 发布时刻时钟
     * @return 把快照列表发布到 Redis 介质的发布器
     */
    @Bean("aspenRouteEnvelopePublisher")
    @ConditionalOnMissingBean(RouteEnvelopePublisher::class)
    fun aspenRouteEnvelopePublisher(
        aspenRedisOperations: AspenRedisOperations,
        objectMapper: ObjectMapper,
        properties: GatewayRouteProperties,
        clock: Clock,
    ): RouteEnvelopePublisher = RouteEnvelopePublisher(aspenRedisOperations, objectMapper, properties, clock)
}
