package com.zax.aspen.common.gateway.publish

import com.zax.aspen.common.cache.support.AspenRedisOperations
import com.zax.aspen.common.gateway.GatewayRouteProperties
import com.zax.aspen.common.gateway.contract.GatewayRouteContract
import com.zax.aspen.common.gateway.contract.RouteCatalogSnapshot
import com.zax.aspen.common.gateway.contract.RouteDefinitionSnapshot
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.OffsetDateTime

/**
 * 把路由快照发布到 Redis 分发介质
 *
 * 介质协议操作: 取号 (INCR 版本计数器) -> 组装信封 -> 一条 SET 原子替换路由 Key
 * (无删除窗口) -> Pub/Sub 携带版本号通知各消费实例; 本类不关心路由从哪来,
 * sys_route 的领域读取与行转换由 Admin 侧完成; 发布失败异常向上抛出,
 * 由调用方决定吞并告警还是快速失败
 *
 * 与仓库通用契约的差异: 本类是 common-gateway 的工厂装配基础设施, 协作依赖经
 * 构造参数注入, 由 AspenGatewayAutoConfiguration 统一装配
 *
 * @param aspenRedisOperations common-cache 提供的 Redis 分发原语
 * @param objectMapper Jackson 3 mapper, 用于信封序列化
 * @param properties 路由分发配置, environment 决定写入的 Redis Key
 * @param clock 发布时刻时钟, 便于测试固定时间
 */
class RouteEnvelopePublisher(
    private val aspenRedisOperations: AspenRedisOperations,
    private val objectMapper: ObjectMapper,
    private val properties: GatewayRouteProperties,
    private val clock: Clock,
) {
    /**
     * 全量构建并以版本守卫方式发布路由信封
     *
     * @param routes 本次发布的全部启用路由快照, 语义非法的行已由调用方过滤
     * @return 本次发布经 Redis 版本计数器 INCR 产生的版本号, 单调递增
     */
    fun publishAll(routes: List<RouteDefinitionSnapshot>): Long {
        val environment = properties.environment
        val version = aspenRedisOperations.increment(GatewayRouteContract.versionKey(environment))
        val envelope = RouteCatalogSnapshot(
            version = version,
            publishedAt = OffsetDateTime.now(clock).toString(),
            routes = routes,
        )
        val adopted = aspenRedisOperations.setValueIfNewer(
            GatewayRouteContract.routesKey(environment),
            objectMapper.writeValueAsString(envelope),
            GatewayRouteContract.refreshChannel(environment),
        )
        if (adopted) {
            log.info("路由快照已发布, version={}, routes={}", version, routes.size)
        } else {
            log.warn(
                "路由快照版本 {} 未采纳 (在途信封不旧于本次, 并发发布旧盖新被拒绝), 待下次发布自愈",
                version,
            )
        }
        return version
    }

    private companion object {
        private val log = LoggerFactory.getLogger(RouteEnvelopePublisher::class.java)
    }
}
