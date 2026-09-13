package com.zax.aspen.common.web.route

import com.zax.aspen.common.route.DeleteRoute
import com.zax.aspen.common.route.GetRoute
import com.zax.aspen.common.route.PatchRoute
import com.zax.aspen.common.route.PostRoute
import com.zax.aspen.common.route.PutRoute
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.web.method.HandlerMethod
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * HandlerMethod 到路由套件声明的解析器
 *
 * 路由注解约定声明在 api 契约接口方法上 (实现 Controller 零注解), 因此解析顺序是
 * 先查 Handler 方法自身, 未命中再按「方法名 + 参数签名」向 bean 类型的全部接口递归查找;
 * 解析结果按 HandlerMethod 缓存 (MVC 映射注册表对同一映射复用同一 HandlerMethod 实例,
 * 缓存容量以映射数为上界), 请求期零反射合并开销, 未注解端点同样缓存负结果避免重复反射
 */
class RouteSpecResolver {
    /** verb 组合注解的读取器, 每个读取器持有具体注解类型, 属性访问全程类型安全 */
    private val specReaders: List<(Method) -> RouteSpec?> = listOf(
        { method ->
            AnnotatedElementUtils.findMergedAnnotation(method, GetRoute::class.java)
                ?.let { RouteSpec(GetRoute::class, it.summary, it.description, it.rateLimit, it.log) }
        },
        { method ->
            AnnotatedElementUtils.findMergedAnnotation(method, PostRoute::class.java)
                ?.let { RouteSpec(PostRoute::class, it.summary, it.description, it.rateLimit, it.log) }
        },
        { method ->
            AnnotatedElementUtils.findMergedAnnotation(method, PutRoute::class.java)
                ?.let { RouteSpec(PutRoute::class, it.summary, it.description, it.rateLimit, it.log) }
        },
        { method ->
            AnnotatedElementUtils.findMergedAnnotation(method, DeleteRoute::class.java)
                ?.let { RouteSpec(DeleteRoute::class, it.summary, it.description, it.rateLimit, it.log) }
        },
        { method ->
            AnnotatedElementUtils.findMergedAnnotation(method, PatchRoute::class.java)
                ?.let { RouteSpec(PatchRoute::class, it.summary, it.description, it.rateLimit, it.log) }
        },
    )

    /** 解析结果缓存, 值包装允许以 null 缓存未注解端点 */
    private val cache = ConcurrentHashMap<HandlerMethod, CachedRouteSpec>()

    /**
     * 解析 Handler 方法的路由声明
     *
     * @param handlerMethod 当前请求的 handler 方法
     * @return 命中的路由声明; 方法与接口层均无路由注解时返回 `null`
     */
    fun resolve(handlerMethod: HandlerMethod): RouteSpec? =
        cache[handlerMethod]?.spec ?: resolveUncached(handlerMethod).also { resolved ->
            cache[handlerMethod] = CachedRouteSpec(resolved)
        }

    /**
     * 执行一次真实解析并写入缓存
     *
     * 并发下可能重复解析同一方法, 结果幂等, 竞态无害
     *
     * @param handlerMethod 当前请求的 handler 方法
     * @return 路由声明, 未注解端点返回 `null`
     */
    private fun resolveUncached(handlerMethod: HandlerMethod): RouteSpec? {
        val method = handlerMethod.method
        readSpecOn(method)?.let { return it }
        // Controller 实现零注解时, 路由注解在 api 契约接口方法上, 按签名向接口层查找
        for (interfaceType in allInterfaces(handlerMethod.beanType)) {
            val interfaceMethod = interfaceType.methods.firstOrNull { candidate ->
                candidate.name == method.name && candidate.parameterTypes.contentEquals(method.parameterTypes)
            }
            if (interfaceMethod != null) {
                readSpecOn(interfaceMethod)?.let { return it }
            }
        }
        return null
    }

    /**
     * 按读取器顺序读取方法上的首个 verb 组合注解
     *
     * @param method 候选方法 (Handler 实现方法或契约接口方法)
     * @return 首个命中注解的归一结果, 五个注解均未命中时返回 `null`
     */
    private fun readSpecOn(method: Method): RouteSpec? {
        for (readSpec in specReaders) {
            readSpec(method)?.let { return it }
        }
        return null
    }

    /**
     * 收集类型的全部接口 (含父接口), 广度优先去重
     *
     * @param type Controller bean 类型
     * @return 该类型实现的全部接口, 无接口时返回空序列
     */
    private fun allInterfaces(type: Class<*>): Sequence<Class<*>> = sequence {
        val visited = mutableSetOf<Class<*>>()
        val queue = ArrayDeque<Class<*>>()
        queue += type.interfaces
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (visited.add(current)) {
                yield(current)
                queue += current.interfaces
            }
        }
    }

    /** 缓存值包装, spec 为 null 表示已确认未注解 */
    private data class CachedRouteSpec(val spec: RouteSpec?)
}
