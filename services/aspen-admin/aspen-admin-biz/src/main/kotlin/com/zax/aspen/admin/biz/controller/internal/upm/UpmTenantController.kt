package com.zax.aspen.admin.biz.controller.internal.upm

import com.zax.aspen.admin.api.contract.upm.UpmTenantApi
import com.zax.aspen.admin.api.dto.upm.TenantBriefDto
import com.zax.aspen.admin.biz.repository.upm.UpmTenantRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.RestController

/**
 * 租户清单内部端点
 *
 * 路径与映射继承 admin-api 的 UpmTenantApi; 属于 internal 契约, 不经网关暴露,
 * 位于 controller/internal 受众包, 不命中 aspen-common-web 的前缀规则;
 * 与 SysRouteApi 的默认关闭不同, 本端点有真实消费方 (统一任务服务的全租户投递),
 * 默认开启, 依赖「/internal 路径不经网关 + 服务端口仅内网可达」兜底
 */
@RestController
@ConditionalOnProperty(prefix = "aspen.admin.tenant-api", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class UpmTenantController(
    private val upmTenantRepository: UpmTenantRepository,
) : UpmTenantApi {
    override fun listEnabledTenants(): List<TenantBriefDto> =
        upmTenantRepository.findAllEnabled().map { tenant ->
            TenantBriefDto(
                tenantId = tenant.tenantId,
                tenantCode = tenant.tenantCode,
                name = tenant.name,
            )
        }
}
