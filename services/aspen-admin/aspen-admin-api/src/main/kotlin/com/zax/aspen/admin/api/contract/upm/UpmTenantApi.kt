package com.zax.aspen.admin.api.contract.upm

import com.zax.aspen.admin.api.dto.upm.TenantBriefDto
import org.springframework.web.bind.annotation.GetMapping

/**
 * 租户清单的内部契约
 *
 * 属于 internal 契约, 不经网关暴露, 供统一任务服务等平台设施按租户展开执行面;
 * "启用" 语义 = 状态启用且当前时间处于有效期内, 判定归 Admin, 消费方不做二次
 * 过滤; v1 无鉴权, 依赖「内网可达 + /internal 路径不经网关」兜底
 */
interface UpmTenantApi {
    /**
     * 查询全部启用租户的简要信息
     *
     * @return 启用且在有效期内的租户简要信息列表, 无启用租户时返回空列表
     */
    @GetMapping("$PATH/enabled")
    fun listEnabledTenants(): List<TenantBriefDto>

    companion object {
        /** 内部端点路径前缀 */
        const val PATH = "/internal/upm/tenant"
    }
}
