package com.zax.aspen.admin.biz.cache

/**
 * Admin 业务缓存 Key 的唯一合法构造入口
 *
 * 《Common 模块设计》第 5 节的强制项: 调用点禁止手写 CacheKey(...) 与 group/domain
 * 字面量, 必须调用本工厂函数; 业务 ID 到 Key 段的转换 (toString 等) 也收在这里。
 *
 * 落地规则: 新工厂函数与配置中心对应 Cache 声明 (group/domain/ttl) 以及第一个真实
 * 消费方同时出现, 不预置没有消费方的条目; group/domain 与声明的一致性由
 * CacheSettings 运行时校验兜底, 构造入口唯一性由根模块中央边界测试固化。
 */
object AdminCacheKeys
