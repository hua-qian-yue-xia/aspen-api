package com.zax.aspen.gateway.route

import com.zax.aspen.admin.api.event.sys.RouteDefinitionPart
import com.zax.aspen.admin.api.event.sys.RouteDefinitionSnapshot
import jakarta.annotation.Resource
import org.springframework.cloud.gateway.filter.FilterDefinition
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition
import org.springframework.cloud.gateway.route.RouteDefinition
import org.springframework.cloud.gateway.route.RouteDefinitionRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.URI

/**
 * 基于 Redis 路由快照的只读路由定义仓库
 *
 * Spring Cloud Gateway 自动把本仓库纳入 CompositeRouteDefinitionLocator, 与配置文件
 * 静态路由合并生效; 快照只在启动与刷新通知时重载, 匹配期零 IO; 写入端点一律拒绝,
 * 路由变更只允许经 Admin sys_route 落库后发布, 保证数据库是唯一写入通道
 */
@Component
class RedisRouteDefinitionRepository : RouteDefinitionRepository {
    @Resource
    private lateinit var routeSnapshotStore: RouteSnapshotStore
    /**
     * 返回内存快照映射的网关路由定义
     *
     * 与仓库通用契约的差异: 数据源是 RouteSnapshotStore 持有的内存快照而非每次查询存储,
     * 匹配期零 IO, 快照仅在启动与刷新通知时重载
     *
     * @return 当前内存快照映射出的 RouteDefinition 流, 未加载过快照时为空流
     */
    override fun getRouteDefinitions(): Flux<RouteDefinition> =
        Flux.fromIterable(routeSnapshotStore.currentSnapshots().map { it.toGatewayDefinition() })

    /**
     * 拒绝写入: 动态路由唯一写入通道是 Admin 的 sys_route 发布链路
     *
     * 与仓库通用契约的差异: 不落库任何路由, 一律以错误流拒绝
     *
     * @param route 待保存的路由定义, 本实现不消费其内容
     * @return 携带 UnsupportedOperationException 的错误 Mono
     */
    override fun save(route: Mono<RouteDefinition>): Mono<Void> =
        Mono.error(UnsupportedOperationException(WRITE_REJECTED_MESSAGE))

    /**
     * 拒绝删除: 动态路由唯一写入通道是 Admin 的 sys_route 发布链路
     *
     * 与仓库通用契约的差异: 不删除任何路由, 一律以错误流拒绝
     *
     * @param routeId 待删除的路由 id, 本实现不消费其内容
     * @return 携带 UnsupportedOperationException 的错误 Mono
     */
    override fun delete(routeId: Mono<String>): Mono<Void> =
        Mono.error(UnsupportedOperationException(WRITE_REJECTED_MESSAGE))

    /**
     * 把契约快照映射为 Spring Cloud Gateway 路由定义
     *
     * @return 可直接进入 CompositeRouteDefinitionLocator 的路由定义, routeCode 作为 id, sortOrder 作为 order
     */
    private fun RouteDefinitionSnapshot.toGatewayDefinition(): RouteDefinition {
        val definition = RouteDefinition()
        definition.setId(routeCode)
        definition.setUri(URI.create(uri))
        definition.setOrder(order)
        definition.setPredicates(predicates.map { it.toPredicateDefinition() })
        definition.setFilters(filters.map { it.toFilterDefinition() })
        definition.getMetadata().putAll(metadata)
        return definition
    }

    /**
     * 把契约断言结构映射为网关断言定义
     *
     * @return 同名同参的 Spring Cloud Gateway PredicateDefinition
     */
    private fun RouteDefinitionPart.toPredicateDefinition(): PredicateDefinition {
        val definition = PredicateDefinition()
        definition.setName(name)
        definition.getArgs().putAll(args)
        return definition
    }

    /**
     * 把契约过滤器结构映射为网关过滤器定义
     *
     * @return 同名同参的 Spring Cloud Gateway FilterDefinition
     */
    private fun RouteDefinitionPart.toFilterDefinition(): FilterDefinition {
        val definition = FilterDefinition()
        definition.setName(name)
        definition.getArgs().putAll(args)
        return definition
    }

    private companion object {
        const val WRITE_REJECTED_MESSAGE = "动态路由禁止直接写入网关, 请经 Admin sys_route 发布"
    }
}
