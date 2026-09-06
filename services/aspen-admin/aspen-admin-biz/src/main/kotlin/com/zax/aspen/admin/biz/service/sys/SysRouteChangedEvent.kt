package com.zax.aspen.admin.biz.service.sys

/**
 * 路由表发生已提交变更的领域事件
 *
 * 在路由增删改事务内发布, 由发布器以 AFTER_COMMIT 阶段监听, 确保只有成功提交的
 * 变更才触发 Redis 快照发布, 回滚的变更不会进入分发介质; 无载荷, 语义是「应当重发布」
 */
object SysRouteChangedEvent
