package com.zax.aspen.admin.biz.service.upm

import com.zax.aspen.admin.biz.repository.upm.UpmTenantRepository
import com.zax.aspen.admin.biz.repository.upm.UpmUserCredentialRepository
import com.zax.aspen.admin.biz.repository.upm.UpmUserRepository
import com.zax.aspen.common.database.tenant.TenantSystemContext
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.env.Environment
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 超管 bootstrap: 平台租户 + 超管账号 + 密码凭证的一次性幂等建号
 *
 * 首个登录主体的来源 (Auth 不建用户, 管理面用户 CRUD 未建, 超管必须自举):
 * 启动时若平台租户或超管账号缺失则补建, 已存在即跳过, 重复执行无副作用;
 * 初始密码只从环境变量 ASPEN_UPM_BOOTSTRAP_ADMIN_PASSWORD 读取 (密钥边界:
 * 不进配置中心与仓库), 未配置时跳过 bootstrap 并提示, 不阻断启动; 建号写入
 * 包裹在显式系统上下文内 (审计说明 upm-bootstrap), 租户由声明值写入
 */
@Component
class UpmBootstrapRunner(
    private val upmTenantRepository: UpmTenantRepository,
    private val upmUserRepository: UpmUserRepository,
    private val upmUserCredentialRepository: UpmUserCredentialRepository,
    private val passwordEncoder: PasswordEncoder,
    private val environment: Environment,
) : ApplicationRunner {
    /**
     * 启动时执行幂等建号; 密码未配置时记录提示并跳过
     *
     * @param args 启动参数, 本运行器不消费
     */
    @Transactional
    override fun run(args: ApplicationArguments) {
        val password: String = environment.getProperty(BOOTSTRAP_PASSWORD_PROPERTY) ?: run {
            log.info("未配置 {} , 跳过超管 bootstrap", BOOTSTRAP_PASSWORD_PROPERTY)
            return
        }
        if (password.isBlank()) {
            log.info("未配置 {} , 跳过超管 bootstrap", BOOTSTRAP_PASSWORD_PROPERTY)
            return
        }
        val created = TenantSystemContext.runAsSystem(SYSTEM_CONTEXT_REASON, tenantId = null) { bootstrap(password) }
        if (created) {
            log.info("超管 bootstrap 完成: 平台租户与超管账号 {} 已就绪", BOOTSTRAP_ADMIN_USERNAME)
        }
    }

    /**
     * 幂等补建平台租户、超管账号与密码凭证, 全部已存在时跳过
     *
     * @param bootstrapPassword 初始密码明文, 仅编码为摘要, 不落日志
     * @return 本次执行实际建号时为 `true`, 全部已存在时为 `false`
     */
    private fun bootstrap(bootstrapPassword: String): Boolean {
        val tenant = upmTenantRepository.findByCode(PLATFORM_TENANT_CODE)
            ?: upmTenantRepository.insert(PLATFORM_TENANT_CODE, PLATFORM_TENANT_NAME, BOOTSTRAP_IDENTITY)
        if (upmUserRepository.findByTenantUsername(tenant.tenantId, BOOTSTRAP_ADMIN_USERNAME) != null) {
            return false
        }
        val user = TenantSystemContext.runAsSystem(SYSTEM_CONTEXT_REASON, tenantId = tenant.tenantId) {
            upmUserRepository.insert(BOOTSTRAP_ADMIN_USERNAME, BOOTSTRAP_ADMIN_USER_TYPE, BOOTSTRAP_IDENTITY)
        }
        // spring-security-crypto 7.x 的 JSpecify 空标注把 encode 返回视为可空, 空摘要属编码器故障, 快速失败
        val bootstrapSecretHash = passwordEncoder.encode(bootstrapPassword)
            ?: error("密码编码器返回空摘要, 拒绝 bootstrap 建号")
        TenantSystemContext.runAsSystem(SYSTEM_CONTEXT_REASON, tenantId = tenant.tenantId) {
            upmUserCredentialRepository.insert(
                userId = user.userId,
                secretHash = bootstrapSecretHash,
                hashAlgorithm = BOOTSTRAP_HASH_ALGORITHM,
                identity = BOOTSTRAP_IDENTITY,
            )
        }
        return true
    }

    private companion object {
        private val log = LoggerFactory.getLogger(UpmBootstrapRunner::class.java)

        /** 初始密码的环境变量键, 只经部署注入 */
        const val BOOTSTRAP_PASSWORD_PROPERTY = "ASPEN_UPM_BOOTSTRAP_ADMIN_PASSWORD"

        /** bootstrap 跨租户写入的系统上下文审计说明 */
        const val SYSTEM_CONTEXT_REASON = "upm-bootstrap"

        /** 平台租户编码, 超管与平台级资产归属该租户 */
        const val PLATFORM_TENANT_CODE = "platform"

        /** 平台租户显示名 */
        const val PLATFORM_TENANT_NAME = "平台租户"

        /** 超管登录用户名 */
        const val BOOTSTRAP_ADMIN_USERNAME = "admin"

        /** 超管的用户类型, upm_user.user_type 取值 */
        const val BOOTSTRAP_ADMIN_USER_TYPE = "admin"

        /** 凭证摘要算法标识 */
        const val BOOTSTRAP_HASH_ALGORITHM = "bcrypt"

        /** bootstrap 写入的审计操作人 */
        const val BOOTSTRAP_IDENTITY = "system:upm-bootstrap"
    }
}
