package com.zax.aspen.admin.biz.messaging.redis.sys

import com.zax.aspen.admin.biz.entity.sys.SysAuthClientEntity
import com.zax.aspen.admin.biz.entity.sys.SysAuthLoginMethodEntity
import com.zax.aspen.admin.biz.repository.sys.SysAuthClientRepository
import com.zax.aspen.admin.biz.repository.sys.SysAuthLoginMethodRepository
import com.zax.aspen.common.security.publish.ClientConfigPublisher
import com.zax.aspen.common.security.snapshot.AuthClientSnapshot
import com.zax.aspen.common.security.snapshot.AuthLoginMethodSnapshot
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 把 sys_auth_client + sys_auth_login_method 全量发布为认证客户端配置快照
 *
 * 链路: 读取启用端与启用方式行 -> 按端归类并转契约快照 (语义非法跳过告警) ->
 * 交 common-security 的 ClientConfigPublisher 完成取号、版本守卫落盘与通知;
 * 认证管理面 CRUD 就绪前, 发布由启动首发驱动 (AuthClientPublishStartupRunner),
 * 变更事务提交后的重发布监听随管理面批次落地; Redis 只是分发介质, 发布失败
 * 只记录错误不回滚数据库, 由下次发布或重启自愈
 */
@Component
class AuthClientConfigPublisher(
    private val sysAuthClientRepository: SysAuthClientRepository,
    private val sysAuthLoginMethodRepository: SysAuthLoginMethodRepository,
    private val clientConfigPublisher: ClientConfigPublisher,
) {
    /**
     * 发布快照并吞掉 Redis 故障, 用于启动首发等不允许中断的调用点
     */
    fun publishAllSafely() {
        try {
            publishAll()
        } catch (e: Exception) {
            log.error("认证客户端配置快照发布失败, Redis 数据待下次发布或重启自愈", e)
        }
    }

    /**
     * 全量构建并发布认证客户端配置快照, 返回发布版本
     *
     * @return 本次发布经 Redis 版本计数器 INCR 产生的版本号, 单调递增
     */
    fun publishAll(): Long {
        val clients = sysAuthClientRepository.findAllEnabled()
        val methodsByClient = sysAuthLoginMethodRepository.findAllEnabled()
            .groupBy { it.authClientId }
        val snapshots = clients.mapNotNull { it.toSnapshotOrNull(methodsByClient[it.authClientId] ?: emptyList()) }
        return clientConfigPublisher.publishAll(snapshots)
    }

    /**
     * 把启用端行及其方式行转换为快照; 方式行语义非法 (未知枚举 code 等) 跳过该方式行, 端保留
     *
     * @param methods 该端全部启用方式行, 已按展示顺序排列
     * @return 合法的端发布快照, 端行语义非法无法构造时返回 `null` 并记录告警
     */
    private fun SysAuthClientEntity.toSnapshotOrNull(methods: List<SysAuthLoginMethodEntity>): AuthClientSnapshot? =
        try {
            AuthClientSnapshot(
                clientCode = clientCode,
                clientKind = clientKind.code,
                accessTokenTtlSeconds = accessTokenTtlSeconds,
                refreshTokenTtlSeconds = refreshTokenTtlSeconds,
                methods = methods.mapNotNull { it.toMethodSnapshotOrNull() },
            )
        } catch (e: Exception) {
            log.warn("端行语义非法, 已跳过发布: authClientId={}, clientCode={}", authClientId, clientCode, e)
            null
        }

    /**
     * 把方式行转换为快照, 未知枚举 code 跳过并告警
     *
     * @return 合法的方式发布快照, 行语义非法无法构造时返回 `null`
     */
    private fun SysAuthLoginMethodEntity.toMethodSnapshotOrNull(): AuthLoginMethodSnapshot? =
        try {
            AuthLoginMethodSnapshot(
                method = method.code,
                captchaKind = captchaKind.code,
                forceChangeOnFirstLogin = forceChangeOnFirstLogin,
                passwordMaxAgeDays = passwordMaxAgeDays,
                config = config,
            )
        } catch (e: Exception) {
            log.warn("方式行语义非法, 已跳过发布: authLoginMethodId={}", authLoginMethodId, e)
            null
        }

    private companion object {
        private val log = LoggerFactory.getLogger(AuthClientConfigPublisher::class.java)
    }
}
