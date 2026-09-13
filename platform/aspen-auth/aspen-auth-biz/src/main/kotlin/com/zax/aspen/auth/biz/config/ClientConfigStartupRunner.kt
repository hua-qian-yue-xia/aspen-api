package com.zax.aspen.auth.biz.config

import com.zax.aspen.common.cache.support.AspenRedisOperations
import com.zax.aspen.common.security.AspenSecurityProperties
import com.zax.aspen.common.security.consume.ClientConfigSnapshotStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * 客户端配置快照的启动加载与变更订阅
 *
 * 启动时从 Redis 加载一次快照 (Admin 从未发布时保持空快照并告警), 并订阅
 * 刷新频道按版本号增量重载; Redis 只是分发介质, 加载失败不阻断启动, 认证
 * 引擎在快照就绪前对登录按「端未开放」拒绝
 */
@Component
class ClientConfigStartupRunner(
    private val clientConfigSnapshotStore: ObjectProvider<ClientConfigSnapshotStore>,
    private val aspenRedisOperations: ObjectProvider<AspenRedisOperations>,
    private val aspenSecurityProperties: AspenSecurityProperties,
) : ApplicationRunner {
    /**
     * 启动加载一次并注册订阅
     *
     * @param args 启动参数, 本运行器不消费
     */
    override fun run(args: ApplicationArguments) {
        val store = clientConfigSnapshotStore.ifAvailable ?: return
        val operations = aspenRedisOperations.ifAvailable
        if (operations == null) {
            log.warn("容器无 Redis 分发原语, 客户端配置快照保持空, 登录将被拒绝直到依赖恢复")
            return
        }
        store.refresh()
        val channel = com.zax.aspen.common.security.contract.AuthClientContract
            .refreshChannel(aspenSecurityProperties.environment)
        operations.subscribe(channel) { message ->
            val version = message.toLongOrNull()
            if (version != null && store.isNewer(version)) {
                store.refresh()
            }
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(ClientConfigStartupRunner::class.java)
    }
}
