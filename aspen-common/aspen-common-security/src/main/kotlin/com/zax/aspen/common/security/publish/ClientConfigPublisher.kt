package com.zax.aspen.common.security.publish

import com.zax.aspen.common.cache.support.AspenRedisOperations
import com.zax.aspen.common.security.AspenSecurityProperties
import com.zax.aspen.common.security.contract.AuthClientContract
import com.zax.aspen.common.security.snapshot.AuthClientCatalogSnapshot
import com.zax.aspen.common.security.snapshot.AuthClientSnapshot
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.OffsetDateTime

/**
 * 把认证客户端配置快照发布到 Redis 分发介质
 *
 * 介质协议操作: 取号 (INCR 版本计数器) -> 组装信封 -> 版本守卫落盘
 * (setValueIfNewer, 仅当新于在途版本才 SET 并广播) -> Pub/Sub 携带版本号通知
 * 各消费实例; 本类不关心配置从哪来, sys_auth_client/sys_auth_login_method 的
 * 领域读取与行转换由 Admin 侧完成; 发布失败异常向上抛出, 由调用方决定吞并告警
 * 还是快速失败; 被守卫拒绝的旧版本只告警不视为失败, 待下次发布自愈
 *
 * @param aspenRedisOperations common-cache 提供的 Redis 分发原语
 * @param objectMapper Jackson 3 mapper, 用于信封序列化
 * @param properties 安全模块配置, environment 决定写入的 Redis Key
 * @param clock 发布时刻时钟, 便于测试固定时间
 */
class ClientConfigPublisher(
    private val aspenRedisOperations: AspenRedisOperations,
    private val objectMapper: ObjectMapper,
    private val properties: AspenSecurityProperties,
    private val clock: Clock,
) {
    /**
     * 全量构建并以版本守卫方式发布客户端配置信封
     *
     * @param clients 本次发布的全部启用端快照 (含各自启用方式行), 语义非法的行已由调用方过滤
     * @return 本次发布经 Redis 版本计数器 INCR 产生的版本号, 单调递增
     */
    fun publishAll(clients: List<AuthClientSnapshot>): Long {
        val environment = properties.environment
        val version = aspenRedisOperations.increment(AuthClientContract.versionKey(environment))
        val envelope = AuthClientCatalogSnapshot(
            version = version,
            publishedAt = OffsetDateTime.now(clock).toString(),
            clients = clients,
        )
        val adopted = aspenRedisOperations.setValueIfNewer(
            AuthClientContract.clientsKey(environment),
            objectMapper.writeValueAsString(envelope),
            AuthClientContract.refreshChannel(environment),
        )
        if (adopted) {
            log.info("客户端配置快照已发布, version={}, clients={}", version, clients.size)
        } else {
            log.warn(
                "客户端配置版本 {} 未采纳 (在途信封不旧于本次, 并发发布旧盖新被拒绝), 待下次发布自愈",
                version,
            )
        }
        return version
    }

    private companion object {
        private val log = LoggerFactory.getLogger(ClientConfigPublisher::class.java)
    }
}
